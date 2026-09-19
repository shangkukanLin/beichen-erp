package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.entity.InventoryWarehouseMove;
import com.beichen.erp.inventory.entity.InventoryWarehouseMoveItem;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMoveMapper;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMoveItemMapper;
import com.beichen.erp.warehouse.service.CostService;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.inventory.service.WarehouseMoveService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseMoveServiceImpl implements WarehouseMoveService {

    private final InventoryWarehouseMoveMapper moveMapper;
    private final InventoryWarehouseMoveItemMapper itemMapper;
    private final WarehouseStockService stockService;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final CostService costService;

    @Override
    public Page<Map<String, Object>> page(String status, Long fromWarehouseId, Long toWarehouseId, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryWarehouseMove> w = new LambdaQueryWrapper<InventoryWarehouseMove>()
                .eq(status != null && !status.isBlank(), InventoryWarehouseMove::getStatus, status)
                .eq(fromWarehouseId != null, InventoryWarehouseMove::getFromWarehouseId, fromWarehouseId)
                .eq(toWarehouseId != null, InventoryWarehouseMove::getToWarehouseId, toWarehouseId)
                .orderByDesc(InventoryWarehouseMove::getId);
        Page<InventoryWarehouseMove> raw = moveMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量加载明细
        List<Long> moveIds = raw.getRecords().stream().map(InventoryWarehouseMove::getId).collect(Collectors.toList());
        final Map<Long, List<InventoryWarehouseMoveItem>> itemsMap;
        if (!moveIds.isEmpty()) {
            List<InventoryWarehouseMoveItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<InventoryWarehouseMoveItem>().in(InventoryWarehouseMoveItem::getMoveId, moveIds));
            itemsMap = allItems.stream().collect(Collectors.groupingBy(InventoryWarehouseMoveItem::getMoveId));
        } else {
            itemsMap = Collections.emptyMap();
        }
        // 批量查询产品名称，避免循环内逐条 selectById 产生 N+1
        List<Long> productIds = new ArrayList<>();
        itemsMap.values().forEach(its -> its.forEach(it -> {
            if (it.getProductId() != null) productIds.add(it.getProductId());
        }));
        final Map<Long, Product> productMap;
        if (!productIds.isEmpty()) {
            productMap = productMapper.selectBatchIds(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        } else {
            productMap = Collections.emptyMap();
        }

        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("fromWarehouseId", o.getFromWarehouseId()); m.put("toWarehouseId", o.getToWarehouseId());
            m.put("moveDate", o.getMoveDate()); m.put("status", o.getStatus()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            // 产品明细摘要
            List<InventoryWarehouseMoveItem> its = itemsMap.getOrDefault(o.getId(), Collections.emptyList());
            String summary = its.stream().map(it -> {
                String name = "";
                if (it.getProductId() != null) {
                    Product prod = productMap.get(it.getProductId());
                    if (prod != null) name = prod.getName();
                }
                return name + "*" + (it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "0");
            }).collect(Collectors.joining("，"));
            m.put("itemsSummary", summary);
            return m;
        }).toList());
        return res;
    }

    @Override
    public InventoryWarehouseMove getById(Long id) { return moveMapper.selectById(id); }

    @Override
    public List<InventoryWarehouseMoveItem> getItems(Long moveId) {
        List<InventoryWarehouseMoveItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryWarehouseMoveItem>().eq(InventoryWarehouseMoveItem::getMoveId, moveId));
        // 回填 SKU（非表字段），前端免查库即可展示
        productService.fillSku(items, InventoryWarehouseMoveItem::getProductId, InventoryWarehouseMoveItem::setSku);
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryWarehouseMove move, List<InventoryWarehouseMoveItem> items) {
        if (move.getFromWarehouseId() == null || move.getToWarehouseId() == null)
            throw new BusinessException("移出/移入仓库不能为空");
        if (move.getFromWarehouseId().equals(move.getToWarehouseId()))
            throw new BusinessException("移出与移入仓库不能相同");
        assertItems(items);
        move.setCode(gen(BillPrefix.WAREHOUSE_MOVE));
        move.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) move.setCompanyId(cid);
        moveMapper.insert(move);
        for (InventoryWarehouseMoveItem it : items) {
            it.setId(null);
            it.setMoveId(move.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InventoryWarehouseMove move, List<InventoryWarehouseMoveItem> items) {
        InventoryWarehouseMove old = moveMapper.selectById(move.getId());
        if (old == null) throw new BusinessException("移仓单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        // F7-15：必须在删旧明细之前校验，否则校验失败时草稿明细已被清空
        assertItems(items);
        move.setCode(old.getCode());
        moveMapper.updateById(move);
        itemMapper.delete(new LambdaQueryWrapper<InventoryWarehouseMoveItem>().eq(InventoryWarehouseMoveItem::getMoveId, move.getId()));
        Long cid = CompanyContext.get();
        for (InventoryWarehouseMoveItem it : items) {
            it.setId(null);
            it.setMoveId(move.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryWarehouseMove old = moveMapper.selectById(id);
        if (old == null) throw new BusinessException("移仓单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        InventoryWarehouseMove u = new InventoryWarehouseMove(); u.setId(id); u.setStatus(DocStatus.CANCELLED.getCode());
        moveMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryWarehouseMove move = moveMapper.selectById(id);
        if (move == null) throw new BusinessException("移仓单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED（原"先查后判再更新"非原子，并发/双击会重复扣加库存）
        if (!DocStatusGuard.claim(moveMapper, InventoryWarehouseMove::getId, id,
                InventoryWarehouseMove::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }
        List<InventoryWarehouseMoveItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryWarehouseMoveItem>().eq(InventoryWarehouseMoveItem::getMoveId, id));
        // F7-15 兜底（覆盖历史草稿与直连库的脏数据）：放在 claim 之后，抛错会连同 claim 一起回滚
        assertItems(items);
        for (InventoryWarehouseMoveItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            // 查询产品名称用于库存流水
            String productName = "";
            if (it.getProductId() != null) {
                Product product = productMapper.selectById(it.getProductId());
                productName = product != null ? product.getName() : "";
            }
            stockService.changeStock(move.getFromWarehouseId(), productName, q.negate(),
                    StockChangeType.MOVE_OUT, move.getCode(), RelatedBillType.WAREHOUSE_MOVE, it.getProductId(), "", move.getId(), it.getQualityType());
            stockService.changeStock(move.getToWarehouseId(), productName, q,
                    StockChangeType.MOVE_IN, move.getCode(), RelatedBillType.WAREHOUSE_MOVE, it.getProductId(), "", move.getId(), it.getQualityType());
            // 移仓不改变加权价（总量不变），但目标仓新出现的库存若产品无成本，用最近进价兜底
            costService.fillProductCostIfEmpty(it.getProductId());
        }
        // 状态已由 DocStatusGuard 在该方法开头原子置为 AUDITED，此处无需再更新
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        InventoryWarehouseMove move = moveMapper.selectById(id);
        if (move == null) throw new BusinessException("移仓单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回滚库存
        if (!DocStatusGuard.claim(moveMapper, InventoryWarehouseMove::getId, id,
                InventoryWarehouseMove::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有已审核状态可反审核");
        }
        List<InventoryWarehouseMoveItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryWarehouseMoveItem>().eq(InventoryWarehouseMoveItem::getMoveId, id));
        for (InventoryWarehouseMoveItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            String productName = "";
            if (it.getProductId() != null) {
                Product product = productMapper.selectById(it.getProductId());
                productName = product != null ? product.getName() : "";
            }
            // 反审核：退回移出仓、从移入仓扣回
            // 【C2 口径 · 2026-09-12 定稿：刻意复用，不再改动】MOVE_IN/MOVE_OUT 表意的是"某仓库存进/出"（方向），
            // 不是"审核/反审核"（动作）；动作由 related_bill_type 区分：审核=WAREHOUSE_MOVE、反审核=WAREHOUSE_MOVE_UN_AUDIT，
            // 故四行流水两两可辨。不新增 MOVE_UN_AUDIT_* 的原因：历史流水无法回填新 code，新老并存反而更难核对。
            stockService.changeStock(move.getFromWarehouseId(), productName, q,
                    StockChangeType.MOVE_IN, move.getCode(), RelatedBillType.WAREHOUSE_MOVE_UN_AUDIT, it.getProductId(), "", move.getId(), it.getQualityType());
            stockService.changeStock(move.getToWarehouseId(), productName, q.negate(),
                    StockChangeType.MOVE_OUT, move.getCode(), RelatedBillType.WAREHOUSE_MOVE_UN_AUDIT, it.getProductId(), "", move.getId(), it.getQualityType());
        }
        // 状态已由 DocStatusGuard 在该方法开头原子置为 DRAFT
    }

    /**
     * 明细行校验（F7-15 · 2026-09-19）：明细不能为空，且每行必须选择产品、数量必须大于 0。
     *
     * <p><b>修复的缺陷</b>：修复前 create / update / audit 三处都不校验明细数量，数量为负数时
     * {@link #audit(Long)} 会给移出仓 {@code q.negate()}（变正）、给移入仓 {@code q}（变负），
     * 等价于"按单据的反方向搬运"（实测：单据说 A→B 移 2 件，账上 B 仓 −2、A 仓 +2），
     * 静默错账、反审核也不会自愈。</p>
     *
     * <p><b>为什么直接抛错而不是静默过滤</b>：过滤会悄悄改变单据含义（用户填 −2 意图搬 2 件，
     * 丢行后单据内容就与提交内容不一致）；抛错能立刻定位到具体行。</p>
     */
    private void assertItems(List<InventoryWarehouseMoveItem> items) {
        if (items == null || items.isEmpty()) throw new BusinessException("请添加移仓明细");
        for (int i = 0; i < items.size(); i++) {
            InventoryWarehouseMoveItem it = items.get(i);
            if (it == null || it.getProductId() == null)
                throw new BusinessException("第 " + (i + 1) + " 行未选择产品");
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("第 " + (i + 1) + " 行数量必须大于 0");
        }
    }

    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<InventoryWarehouseMove> w = new LambdaQueryWrapper<InventoryWarehouseMove>()
                .likeRight(InventoryWarehouseMove::getCode, pat).orderByDesc(InventoryWarehouseMove::getCode).last("LIMIT 1");
        InventoryWarehouseMove last = moveMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
