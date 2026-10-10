package com.beichen.erp.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.warehouse.entity.CostInboundLog;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.CostInboundLogMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 移动加权平均成本服务（一期）。
 * <p>
 * 口径：入库时 {@code 新成本 = (现存数量×现成本 + 入库数量×入库单价) ÷ (现存数量+入库数量)}，
 * 现存数量为 0 时直接采用入库单价；出库/销售不回改成本（加权平均法标准处理）。
 * 每笔有明确单价的入库写一行 {@link CostInboundLog} 批次，反审核按单据冲销批次并反加权。
 * cost_manual=1（手工锁定）时只记批次不更新主数据成本。
 * 退货类入库按"当前成本"数学上不改变加权价，无需单独接入。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostService {

    public static final String TYPE_PRODUCT = "PRODUCT";
    public static final String TYPE_MATERIAL = "MATERIAL";

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 4;

    private final CostInboundLogMapper costLogMapper;
    private final WarehouseStockMapper stockMapper;
    private final ProductMapper productMapper;
    private final OutsourceMaterialMapper materialMapper;
    /** 2026-10-09：物料「子物料组成」（{@link #materialFullCost} 递归展开用） */
    private final OutsourceMaterialComponentMapper componentMapper;

    /** 产品入库加权（采购入库/委外交货等） */
    @Transactional(rollbackFor = Exception.class)
    public void applyProduct(Long productId, BigDecimal qty, BigDecimal unitPrice,
                             String changeType, Long relatedBillId, String relatedBillNo) {
        apply(TYPE_PRODUCT, productId, qty, unitPrice, changeType, relatedBillId, relatedBillNo);
    }

    /** 委外物料入库加权（其他出入库 IN/物料订单收货/委外收发单等） */
    @Transactional(rollbackFor = Exception.class)
    public void applyMaterial(Long materialId, BigDecimal qty, BigDecimal unitPrice,
                              String changeType, Long relatedBillId, String relatedBillNo) {
        apply(TYPE_MATERIAL, materialId, qty, unitPrice, changeType, relatedBillId, relatedBillNo);
    }

    /**
     * 成本兜底：无单价入库（移仓/其他入库/盘盈）后调用。
     * <p>
     * 这些途径本身没有采购单价，若对象此前从未有过带单价入库，成本价会一直为 NULL，
     * 造成"有库存、无成本"，毛利与库存金额失真。此处用最近参考价补齐：
     * 产品取 {@code last_in_price}；物料取 {@code last_in_price}，再退回主数据 {@code price}。
     * </p>
     * <p>
     * 只写主数据成本、<b>不写入库批次</b>：本类途径无来源单价，写批次会让反审核按单据冲销时算错。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void fillCostIfEmpty(String type, Long id) {
        if (id == null) return;
        if (!isBlank(currentCost(type, id))) return; // 已有成本不动（0 视为未定价）
        if (isManual(type, id)) return;              // 手工锁定不动
        BigDecimal ref = lastKnownPrice(type, id);
        if (ref == null || ref.compareTo(ZERO) <= 0) return;
        updateCost(type, id, ref, null);
        log.info("成本兜底 {}#{} 无单价入库，按参考价 {} 补齐", type, id, ref);
    }

    /** 产品成本兜底（移仓/其他入库/盘盈入库后） */
    public void fillProductCostIfEmpty(Long productId) {
        fillCostIfEmpty(TYPE_PRODUCT, productId);
    }

    /** 委外物料成本兜底 */
    public void fillMaterialCostIfEmpty(Long materialId) {
        fillCostIfEmpty(TYPE_MATERIAL, materialId);
    }

    // ==================== 物料「完全成本」（含子物料，2026-10-09 新增） ====================

    /**
     * 委外物料的<b>完全成本</b>：
     * <pre>
     *   完全成本 = 自身成本 + Σ( 子物料完全成本 × 用量 × (1 + 损耗率/100) )
     * </pre>
     *
     * <p><b>为什么需要它</b>：委外物料可以是<b>装配件</b>（见 {@code outsource_material_component}，
     * 如「排线 X60Pro」含子物料「触摸IC S3909」）。原实现各处计价只取 {@code cost_price}（自身加权成本），
     * <b>子物料成本从未计入</b> —— 实测排线自身 7.0000 + 子物料 4.1724 应为 11.1724，被低估 37%，
     * 并沿「物料 → 加工单 → 产品成本」一路放大。</p>
     *
     * <p><b>为什么是"算"出来、而不是把子物料写进 {@code cost_price}</b>：加权平均的入参必须是
     * <b>自身</b>成本（见 {@link #apply}）。若把子物料成本并进 {@code cost_price}，下一批入库加权时
     * 会把它当成"自身价"一起加权 ⇒ 子物料成本被反复放大，越滚越离谱。
     * 故 {@code cost_price} 语义保持不变（自身成本），完全成本按需计算。</p>
     *
     * <p><b>递归与防环</b>：按 {@code parent_outsource_material_id} 递归展开，支持多层装配；
     * 用 {@code visiting} 做<b>环检测</b>（A→B→A 时该分支记 0 并告警，绝不死循环）；
     * 用 {@code memo} 记忆化，同一物料一次调用只查一次库。</p>
     *
     * <p><b>损耗率口径</b>：按<b>百分数</b>理解（与界面「损耗率%」一致，2 = 2%）⇒ 系数 = 1 + 损耗率/100。</p>
     *
     * <p><b>调用点</b>（凡"把物料成本计入金额"之处）：产品成本（加工单收货扣料）、
     * 退货/维修计价（{@code fifoPriceWithFallback}）、结单报告物料单价、物料报损默认价。</p>
     *
     * @param materialId 委外物料ID（null ⇒ 0）
     * @return 完全成本，保留 4 位小数（与 {@code cost_price} 同精度）
     */
    public BigDecimal materialFullCost(Long materialId) {
        return materialFullCost(materialId, new HashSet<>(), new HashMap<>());
    }

    private BigDecimal materialFullCost(Long materialId, Set<Long> visiting, Map<Long, BigDecimal> memo) {
        if (materialId == null) {
            return ZERO;
        }
        BigDecimal cached = memo.get(materialId);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(materialId)) {
            // 环：该分支记 0 并告警（不缓存 —— 这不是该物料的真实成本）
            log.warn("[物料完全成本] 子物料组成存在环：物料 {} 重复出现，该分支按 0 计（请检查 outsource_material_component）",
                    materialId);
            return ZERO;
        }
        BigDecimal total = ownCostOf(materialId);
        List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialComponent>()
                        .eq(OutsourceMaterialComponent::getParentMaterialId, materialId));
        for (OutsourceMaterialComponent c : comps) {
            if (c.getChildMaterialId() == null) continue;
            BigDecimal qty = c.getQuantity() == null ? ZERO : c.getQuantity();
            if (qty.compareTo(ZERO) <= 0) continue;
            BigDecimal loss = c.getLossRate() == null ? ZERO : c.getLossRate();
            BigDecimal factor = BigDecimal.ONE.add(loss.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            total = total.add(materialFullCost(c.getChildMaterialId(), visiting, memo)
                    .multiply(qty).multiply(factor));
        }
        visiting.remove(materialId);
        BigDecimal result = total.setScale(SCALE, RoundingMode.HALF_UP);
        memo.put(materialId, result);
        return result;
    }

    /** 物料"自身"成本：移动加权 {@code cost_price}；未建立（NULL/0）时退回主数据参考价 {@code price} */
    private BigDecimal ownCostOf(Long materialId) {
        OutsourceMaterial m = materialMapper.selectById(materialId);
        if (m == null) return ZERO;
        if (m.getCostPrice() != null && m.getCostPrice().compareTo(ZERO) > 0) return m.getCostPrice();
        return m.getPrice() != null ? m.getPrice() : ZERO;
    }

    /** 反审核冲销：按 变动类型+单据ID 删除批次并反加权（同单多产品/多明细全部处理） */
    @Transactional(rollbackFor = Exception.class)
    public void reverseByBill(String changeType, Long relatedBillId) {
        if (relatedBillId == null) return;
        List<CostInboundLog> batches = costLogMapper.selectList(new LambdaQueryWrapper<CostInboundLog>()
                .eq(CostInboundLog::getChangeType, changeType)
                .eq(CostInboundLog::getRelatedBillId, relatedBillId));
        if (batches.isEmpty()) return;
        // 按 (targetType, targetId) 分组，逐个反加权
        batches.stream().collect(java.util.stream.Collectors.groupingBy(b -> b.getTargetType() + "|" + b.getTargetId()))
                .values().forEach(group -> reverseGroup(group));
        costLogMapper.deleteBatchIds(batches.stream().map(CostInboundLog::getId).toList());
    }

    // ==================== 内部实现 ====================

    private void apply(String type, Long id, BigDecimal qty, BigDecimal unitPrice,
                       String changeType, Long relatedBillId, String relatedBillNo) {
        if (id == null || qty == null || qty.compareTo(ZERO) <= 0
                || unitPrice == null || unitPrice.compareTo(ZERO) < 0) return;
        Long companyId = CompanyContext.get();

        BigDecimal stockQty = sumStock(type, id, companyId);
        BigDecimal curCost = currentCost(type, id);
        boolean manual = isManual(type, id);

        BigDecimal costAfter = isBlank(curCost) ? ZERO : curCost;
        if (!manual) {
            // 注意：apply 在库存变更之后调用，stockQty 已包含本批；入库前数量 = stockQty - qty
            BigDecimal beforeQty = stockQty.subtract(qty);
            // 历史成本为 0/NULL 视为"未定价"（如反审核冲销后归零），不参与加权，直接采用本次入库单价，
            // 否则 0 会拉低加权结果（例：库存 80 成本 0 + 入库 20 @9 → 1.8，严重低估）
            if (beforeQty.compareTo(ZERO) <= 0 || isBlank(curCost)) {
                costAfter = unitPrice;
            } else {
                BigDecimal total = curCost.multiply(beforeQty).add(unitPrice.multiply(qty));
                costAfter = total.divide(stockQty, SCALE, RoundingMode.HALF_UP);
            }
            updateCost(type, id, costAfter, unitPrice);
        }

        CostInboundLog logRow = new CostInboundLog();
        logRow.setTargetType(type);
        logRow.setTargetId(id);
        logRow.setChangeType(changeType);
        logRow.setRelatedBillId(relatedBillId);
        logRow.setRelatedBillNo(relatedBillNo);
        logRow.setQuantity(qty);
        logRow.setUnitCost(unitPrice);
        logRow.setTotalCost(qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
        logRow.setCostAfter(costAfter);
        if (companyId != null && companyId > 0) logRow.setCompanyId(companyId);
        costLogMapper.insert(logRow);
    }

    private void reverseGroup(List<CostInboundLog> group) {
        CostInboundLog first = group.get(0);
        String type = first.getTargetType();
        Long id = first.getTargetId();
        Long companyId = first.getCompanyId();
        if (isManual(type, id)) return; // 手工锁定：只删批次，不动成本

        BigDecimal stockQty = sumStock(type, id, companyId);
        BigDecimal curCost = currentCost(type, id);
        if (curCost == null) curCost = ZERO;
        BigDecimal batchQty = ZERO, batchTotal = ZERO;
        for (CostInboundLog b : group) {
            batchQty = batchQty.add(b.getQuantity() == null ? ZERO : b.getQuantity());
            batchTotal = batchTotal.add(b.getTotalCost() == null ? ZERO : b.getTotalCost());
        }
        // 注意：reverse 在库存冲回之后调用，stockQty 已不含批次；冲销前库存 = stockQty + batchQty
        BigDecimal beforeQty = stockQty.add(batchQty);
        BigDecimal newCost;
        if (stockQty.compareTo(ZERO) <= 0) {
            newCost = ZERO; // 冲销后无库存，成本归零（下次入库重置）
        } else {
            BigDecimal total = curCost.multiply(beforeQty).subtract(batchTotal);
            newCost = total.divide(stockQty, SCALE, RoundingMode.HALF_UP).max(ZERO);
        }
        updateCost(type, id, newCost, null);
        log.info("成本冲销 {}#{} 批次数量={} 金额={} 成本 {} -> {}", type, id, batchQty, batchTotal, curCost, newCost);
    }

    /** 该对象全部库存行的数量合计（product 维度含各品质等级，material 维度 GOOD/DEFECT） */
    private BigDecimal sumStock(String type, Long id, Long companyId) {
        LambdaQueryWrapper<WarehouseStock> w = new LambdaQueryWrapper<>();
        if (TYPE_PRODUCT.equals(type)) w.eq(WarehouseStock::getProductId, id);
        else w.eq(WarehouseStock::getMaterialId, id);
        w.eq(companyId != null && companyId > 0, WarehouseStock::getCompanyId, companyId);
        List<WarehouseStock> rows = stockMapper.selectList(w);
        BigDecimal sum = ZERO;
        for (WarehouseStock r : rows) {
            if (r.getQuantity() != null) sum = sum.add(r.getQuantity());
        }
        return sum;
    }

    /** 成本是否视为"未定价"：NULL 或 0（反审核冲销归零、历史库列默认 0 都属此类） */
    private boolean isBlank(BigDecimal cost) {
        return cost == null || cost.compareTo(ZERO) == 0;
    }

    /** 参考价：物料 last_in_price → 主数据 price；产品 last_in_price */
    private BigDecimal lastKnownPrice(String type, Long id) {
        if (TYPE_PRODUCT.equals(type)) {
            Product p = productMapper.selectById(id);
            return p != null ? p.getLastInPrice() : null;
        }
        OutsourceMaterial m = materialMapper.selectById(id);
        if (m == null) return null;
        if (m.getLastInPrice() != null && m.getLastInPrice().compareTo(ZERO) > 0) return m.getLastInPrice();
        return m.getPrice();
    }

    private BigDecimal currentCost(String type, Long id) {
        if (TYPE_PRODUCT.equals(type)) {
            Product p = productMapper.selectById(id);
            return p != null ? p.getCostPrice() : null;
        }
        OutsourceMaterial m = materialMapper.selectById(id);
        return m != null ? m.getCostPrice() : null;
    }

    private boolean isManual(String type, Long id) {
        Integer manual;
        if (TYPE_PRODUCT.equals(type)) {
            Product p = productMapper.selectById(id);
            manual = p != null ? p.getCostManual() : null;
        } else {
            OutsourceMaterial m = materialMapper.selectById(id);
            manual = m != null ? m.getCostManual() : null;
        }
        return manual != null && manual == 1;
    }

    /** 更新主数据成本价与最近进价（局部更新，不动其他字段） */
    private void updateCost(String type, Long id, BigDecimal costPrice, BigDecimal lastInPrice) {
        if (TYPE_PRODUCT.equals(type)) {
            Product u = new Product();
            u.setId(id);
            u.setCostPrice(costPrice);
            if (lastInPrice != null) u.setLastInPrice(lastInPrice);
            productMapper.updateById(u);
        } else {
            OutsourceMaterial u = new OutsourceMaterial();
            u.setId(id);
            u.setCostPrice(costPrice);
            if (lastInPrice != null) u.setLastInPrice(lastInPrice);
            materialMapper.updateById(u);
        }
    }
}
