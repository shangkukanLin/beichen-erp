package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.BillNoSeq;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.finance.service.ReceivableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.service.ProductService;
import com.beichen.erp.sale.common.AfterSaleSourceType;
import com.beichen.erp.sale.common.ExchangeChargeType;
import com.beichen.erp.sale.entity.AfterSalePending;
import com.beichen.erp.sale.entity.SaleExchange;
import com.beichen.erp.sale.entity.SaleExchangeItem;
import com.beichen.erp.sale.entity.SaleReturn;
import com.beichen.erp.sale.entity.SaleReturnItem;
import com.beichen.erp.sale.entity.SaleOrderItem;
import com.beichen.erp.sale.mapper.AfterSalePendingMapper;
import com.beichen.erp.sale.mapper.SaleExchangeItemMapper;
import com.beichen.erp.sale.mapper.SaleExchangeMapper;
import com.beichen.erp.sale.mapper.SaleOrderItemMapper;
import com.beichen.erp.sale.mapper.SaleReturnItemMapper;
import com.beichen.erp.sale.mapper.SaleReturnMapper;
import com.beichen.erp.sale.service.SaleExchangeService;
import com.beichen.erp.warehouse.common.WarehouseType;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 销售换货单服务实现（同品换货；**来源销售单可选** = 为空即"无单换货"）
 * <p>
 * 审核时双向联动库存：
 * <ol>
 *   <li>退回：入「换入仓」（成品仓），品质记 {@code PENDING}(待整理)，后续走退货整理流程；</li>
 *   <li>换出：从「换出仓」（成品仓）按明细 {@code qualityType} 扣减。</li>
 * </ol>
 * 有来源时：可换数量 = 已售 − 已退 − 已换，支持同一销售明细多次部分换货；
 * 无来源时（2026-10-01 第 2 步放开）：不作可换量校验，退回/换出能否成立以审核时的库存校验为准。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SaleExchangeServiceImpl implements SaleExchangeService {

    private final SaleExchangeMapper exchangeMapper;
    private final SaleExchangeItemMapper exchangeItemMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final SaleReturnMapper saleReturnMapper;
    private final SaleReturnItemMapper saleReturnItemMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockService stockService;
    private final AfterSalePendingMapper afterSalePendingMapper;
    private final CustomerMapper customerMapper;
    private final FinanceReceivableMapper financeReceivableMapper;
    private final ReceivableHelper receivableHelper;
    /**
     * F7-113（2026-09-20）：已退/已换量改用**一条 JOIN 聚合**（原实现先"查出全部已审核单据"
     * 再 {@code in(ids)} 查明细，且在校验循环里按明细逐条调用 ⇒ 全表扫描 + N+1）。
     */
    private final JdbcTemplate jdbcTemplate;

    // ==================== 查询 ====================

    @Override
    public IPage<Map<String, Object>> page(long current, long size, Map<String, Object> q) {
        Page<SaleExchange> pg = new Page<>(current, size);
        String kw = q == null ? null : str(q.get("kw"));
        String status = q == null ? null : str(q.get("status"));
        Long customerId = q == null ? null : toLong(q.get("customerId"));
        Long saleOrderId = q == null ? null : toLong(q.get("saleOrderId"));
        LambdaQueryWrapper<SaleExchange> w = new LambdaQueryWrapper<SaleExchange>()
                .and(kw != null && !kw.isBlank(),
                        x -> x.like(SaleExchange::getCode, kw).or().like(SaleExchange::getSaleOrderCode, kw))
                .eq(status != null && !status.isBlank(), SaleExchange::getStatus, status)
                .eq(customerId != null, SaleExchange::getCustomerId, customerId)
                .eq(saleOrderId != null, SaleExchange::getSaleOrderId, saleOrderId)
                .orderByDesc(SaleExchange::getId);
        IPage<SaleExchange> p = exchangeMapper.selectPage(pg, w);
        // 换货概况（退回侧 → 换出侧）为非表字段，按本页单据批量查明细后拼接，避免逐条查库
        Map<Long, List<SaleExchangeItem>> itemsMap = new HashMap<>();
        if (!p.getRecords().isEmpty()) {
            List<Long> ids = p.getRecords().stream().map(SaleExchange::getId).collect(Collectors.toList());
            itemsMap = exchangeItemMapper.selectList(
                            new LambdaQueryWrapper<SaleExchangeItem>().in(SaleExchangeItem::getExchangeId, ids))
                    .stream().collect(Collectors.groupingBy(SaleExchangeItem::getExchangeId));
        }
        Map<Long, List<SaleExchangeItem>> finalItemsMap = itemsMap;
        // F7-116（2026-09-20）：仓库名一次批量查 —— 原先在下面的循环里逐条 `selectById`（N+1），
        // 而同方法上方刚注释"避免逐条查库"，属自相矛盾。
        Set<Long> whIds = new HashSet<>();
        for (SaleExchange e : p.getRecords()) {
            if (e.getWarehouseInId() != null) whIds.add(e.getWarehouseInId());
            if (e.getWarehouseOutId() != null) whIds.add(e.getWarehouseOutId());
        }
        Map<Long, String> whNameMap = whIds.isEmpty() ? new HashMap<>()
                : warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(Warehouse::getId,
                                wh -> wh.getWarehouseName() != null ? wh.getWarehouseName() : "", (a, b) -> a));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SaleExchange e : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("code", e.getCode());
            m.put("saleOrderId", e.getSaleOrderId());
            m.put("saleOrderCode", e.getSaleOrderCode());
            m.put("customerId", e.getCustomerId());
            m.put("warehouseInId", e.getWarehouseInId());
            m.put("warehouseInName", whNameMap.getOrDefault(e.getWarehouseInId(), ""));
            m.put("warehouseOutId", e.getWarehouseOutId());
            m.put("warehouseOutName", whNameMap.getOrDefault(e.getWarehouseOutId(), ""));
            m.put("exchangeDate", e.getExchangeDate() != null ? e.getExchangeDate().toString() : "");
            m.put("status", e.getStatus());
            // 换货概况：产品名 退N → 换M(品质)，多条明细用「；」连接
            List<SaleExchangeItem> exItems = finalItemsMap.getOrDefault(e.getId(), Collections.emptyList());
            String summary = exItems.stream()
                    .map(it -> String.format("%s 退%s → 换%s(%s)",
                            it.getProductName() != null ? it.getProductName() : "",
                            it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "0",
                            it.getOutQuantity() != null ? it.getOutQuantity().stripTrailingZeros().toPlainString() : "0",
                            it.getOutQualityType() != null ? it.getOutQualityType() : ""))
                    .collect(Collectors.joining("；"));
            m.put("exchangeSummary", summary);
            m.put("chargeFlag", e.getChargeFlag());
            m.put("chargeType", e.getChargeType());
            m.put("chargeAmount", e.getChargeAmount());
            m.put("chargeReason", e.getChargeReason());
            m.put("remark", e.getRemark());
            m.put("auditorName", e.getAuditorName());
            m.put("auditTime", e.getAuditTime() != null ? e.getAuditTime().toString() : "");
            rows.add(m);
        }
        IPage<Map<String, Object>> res = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        res.setRecords(rows);
        return res;
    }

    @Override
    public SaleExchange getById(Long id) {
        return exchangeMapper.selectById(id);
    }

    @Override
    public List<SaleExchangeItem> getItems(Long id) {
        List<SaleExchangeItem> items = exchangeItemMapper.selectList(
                new LambdaQueryWrapper<SaleExchangeItem>().eq(SaleExchangeItem::getExchangeId, id));
        // 回填 SKU（非表字段），前端免查库即可展示
        productService.fillSku(items, SaleExchangeItem::getProductId, SaleExchangeItem::setSku);
        return items;
    }

    @Override
    public List<Map<String, Object>> saleOrderItems(Long saleOrderId) {
        if (saleOrderId == null) return List.of();
        List<SaleOrderItem> oiList = saleOrderItemMapper.selectList(
                new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, saleOrderId));
        if (oiList.isEmpty()) return List.of();
        Map<Long, Product> pMap = new HashMap<>();
        Set<Long> pids = oiList.stream().map(SaleOrderItem::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!pids.isEmpty()) productMapper.selectBatchIds(pids).forEach(p -> pMap.put(p.getId(), p));
        Set<Long> oiIds = oiList.stream().map(SaleOrderItem::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // 批量取已退/已换累计，避免逐条查库（N+1）
        Map<Long, BigDecimal> returnedMap = alreadyReturnedBatch(oiIds);
        // F7-113（2026-09-20）：此处是"查可换量"页签，不排除任何本单 ⇒ excludeExchangeId = null
        Map<Long, BigDecimal> exchangedMap = alreadyExchangedBatch(oiIds, null);
        List<Map<String, Object>> res = new ArrayList<>();
        for (SaleOrderItem oi : oiList) {
            Product p = pMap.get(oi.getProductId());
            BigDecimal sold = nz(oi.getQuantity());
            BigDecimal returned = returnedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            BigDecimal exchanged = exchangedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            // 2026-10-01（用户口径「已换也可以再换：每一次不超过销售总数」）：**回显的"可换数量"必须与
            // checkCanExchange 同口径** —— = 该明细的销售数量（不再扣历史累计），否则前端会显示 0
            // 而提交却能通过（改造前这里正是「已售 − 已退 − 已换」）。
            // returned / exchanged 仍回传前端作展示参考（returnedQuantity / exchangedQuantity）。
            BigDecimal canExchange = sold;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("saleOrderItemId", oi.getId());
            m.put("productId", oi.getProductId());
            m.put("productName", p != null ? p.getName() : "");
            // 2026-09-21（UI）：换货新增页明细表把「SKU | 名称」并到一列（原 SKU 独占一列 ⇒ 表格横向滚动），
            // pMap 已批量取好产品，这里顺带回 SKU，零额外查询
            m.put("sku", p != null ? p.getSku() : "");
            m.put("unit", p != null ? p.getUnit() : "");
            m.put("qualityType", oi.getQualityType());
            m.put("quantity", sold);
            m.put("unitPrice", oi.getUnitPrice());
            m.put("returnedQuantity", returned);
            m.put("exchangedQuantity", exchanged);
            m.put("canExchange", canExchange);
            res.add(m);
        }
        return res;
    }

    /**
     * 批量取已退量（按销售单明细ID聚合）：已审核销售退货单中该销售明细的累计数量。
     * <p><b>F7-113（2026-09-20）</b>：改为一条 JOIN 聚合 SQL（原实现"查全部已审核退货单 → in(ids) 查明细"，
     * 随单据增长是平方级 + 受 {@code max_allowed_packet} 限制；与 {@code SaleReturnServiceImpl} 的写法统一）。</p>
     */
    private Map<Long, BigDecimal> alreadyReturnedBatch(Set<Long> saleOrderItemIds) {
        return sumsBySaleOrderItemIds(
                "SELECT ri.sale_order_item_id AS k, COALESCE(SUM(ri.quantity), 0) AS v "
                        + "FROM sale_return_item ri JOIN sale_return sr ON sr.id = ri.return_id "
                        + "WHERE sr.status = 'AUDITED' AND ri.sale_order_item_id IN (%s) "
                        + "GROUP BY ri.sale_order_item_id",
                saleOrderItemIds);
    }

    /**
     * 批量取已换量（按销售单明细ID聚合）：已审核换货单中该销售明细的累计数量。
     *
     * @param excludeExchangeId 需要排除的单据 id（**审核中的本单**，F1-1：claim 已把本单置 AUDITED，
     *                          不排除就会把本单退回量算进"已换"）—— 仅 {@link #checkCanExchange} 会传。
     */
    private Map<Long, BigDecimal> alreadyExchangedBatch(Set<Long> saleOrderItemIds, Long excludeExchangeId) {
        return sumsBySaleOrderItemIds(
                "SELECT i.sale_order_item_id AS k, COALESCE(SUM(i.quantity), 0) AS v "
                        + "FROM sale_exchange_item i JOIN sale_exchange e ON e.id = i.exchange_id "
                        + "WHERE e.status = 'AUDITED' AND i.sale_order_item_id IN (%s)"
                        + (excludeExchangeId == null ? "" : " AND e.id <> " + excludeExchangeId)
                        + " GROUP BY i.sale_order_item_id",
                saleOrderItemIds);
    }

    /**
     * 执行"按销售单明细ID求和"的聚合 SQL（{@code %s} 处填入 IN 列表）。
     *
     * <p>⚠️ 拼入 IN 列表的 id 均来自**数据库主键**（{@code SaleOrderItem.id}）或已由
     * {@code Long} 承载的入参 ⇒ 只可能是数字 ⇒ 无注入面。若将来改为传字符串形参，必须改成参数化绑定。</p>
     */
    private Map<Long, BigDecimal> sumsBySaleOrderItemIds(String sqlTemplate, Set<Long> saleOrderItemIds) {
        Map<Long, BigDecimal> res = new HashMap<>();
        if (saleOrderItemIds == null || saleOrderItemIds.isEmpty()) return res;
        List<Long> clean = saleOrderItemIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (clean.isEmpty()) return res;
        String in = clean.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.query(String.format(sqlTemplate, in), rs -> {
            while (rs.next()) {
                res.put(rs.getLong(1), rs.getBigDecimal(2));
            }
            return null;
        });
        return res;
    }

    // ==================== 保存 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleExchange create(SaleExchange exchange, List<Map<String, Object>> itemMaps) {
        fillSaleOrderInfo(exchange);
        exchange.setCode(gen());
        if (exchange.getStatus() == null) exchange.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        // F7-114（2026-09-20）：必须同时判 `> 0` —— 超管模式下 CompanyContext 为 0，
        // 显式 set(0) 会落 company_id = 0 ⇒ 租户条件 `company_id = N` 永不命中 ⇒ 该行对所有公司不可见（孤儿单）。
        // 自动填充器（strictInsertFill）本身也只在 cid > 0 时才兜底，故这里不能只判 null。
        if (cid != null && cid > 0) exchange.setCompanyId(cid);
        validate(exchange, itemMaps);
        normalizeCharge(exchange, itemMaps);
        exchangeMapper.insert(exchange);
        saveItems(exchange.getId(), itemMaps, exchange.getChargeReason());
        // 主表收费 = Σ(明细)（2026-09-21 逐产品口径）
        recalcDocCharge(exchange.getId());
        return exchangeMapper.selectById(exchange.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleExchange update(SaleExchange exchange, List<Map<String, Object>> itemMaps) {
        if (exchange.getId() == null) throw new BusinessException("换货单ID不能为空");
        SaleExchange old = exchangeMapper.selectById(exchange.getId());
        if (old == null) throw new BusinessException("换货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可修改");
        fillSaleOrderInfo(exchange);
        validate(exchange, itemMaps);
        normalizeCharge(exchange, itemMaps);
        exchange.setCode(old.getCode());
        exchange.setStatus(old.getStatus());
        exchangeMapper.updateById(exchange);
        saveItems(exchange.getId(), itemMaps, exchange.getChargeReason());
        // 主表收费 = Σ(明细)；返回体重取，保证 charge_* 是回写后的值
        recalcDocCharge(exchange.getId());
        return exchangeMapper.selectById(exchange.getId());
    }

    /**
     * 保存明细：先删后插。
     * <p>明细拆「退回侧 + 换出侧」：只支持同品换货，换出产品固定为退回产品；
     * 换出数量未指定时默认等于退回数量，换出单价默认取原销售单价。</p>
     */
    private void saveItems(Long exchangeId, List<Map<String, Object>> itemMaps, String docChargeReason) {
        exchangeItemMapper.delete(
                new LambdaQueryWrapper<SaleExchangeItem>().eq(SaleExchangeItem::getExchangeId, exchangeId));
        if (itemMaps == null || itemMaps.isEmpty()) return;
        Long cid = CompanyContext.get();
        for (Map<String, Object> m : itemMaps) {
            SaleExchangeItem it = new SaleExchangeItem();
            it.setExchangeId(exchangeId);
            if (m.get("saleOrderItemId") != null && !m.get("saleOrderItemId").toString().isBlank())
                it.setSaleOrderItemId(Long.valueOf(m.get("saleOrderItemId").toString()));
            // ===== 退回侧 =====
            if (m.get("productId") == null || m.get("productId").toString().isBlank())
                throw new BusinessException("退回产品不能为空");
            it.setProductId(Long.valueOf(m.get("productId").toString()));
            Product p = productMapper.selectById(it.getProductId());
            it.setProductName(p != null ? p.getName()
                    : (m.get("productName") != null ? m.get("productName").toString() : ""));
            BigDecimal qty = toBig(m.get("quantity"));
            if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("退回数量必须大于0");
            it.setQuantity(qty);
            it.setUnitPrice(toBig(m.get("unitPrice")));
            it.setAmount(qty.multiply(it.getUnitPrice()));

            // ===== 换出侧（只支持同品：换出产品固定为退回产品；数量可不等如退2换1，品质可换）=====
            BigDecimal outQty = m.get("outQuantity") != null && !m.get("outQuantity").toString().isBlank()
                    ? toBig(m.get("outQuantity")) : qty;
            if (outQty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("换出数量必须大于0");
            it.setOutQuantity(outQty);
            // 换出单价：默认取原销售单价，前端可手工改（仅用于展示与差价参考）
            it.setOutUnitPrice(m.get("outUnitPrice") != null && !m.get("outUnitPrice").toString().isBlank()
                    ? toBig(m.get("outUnitPrice")) : it.getUnitPrice());
            it.setOutAmount(outQty.multiply(it.getOutUnitPrice()));
            String qt = m.get("outQualityType") != null && !m.get("outQualityType").toString().isBlank()
                    ? m.get("outQualityType").toString() : ProductQualityType.A.getCode();
            assertOutQuality(qt);   // F7-27：换出品质仅允许 A/B/C
            it.setOutQualityType(qt);

            // 逐产品收费（2026-09-21 用户口径）：金额 > 0 即收费；类型必须合法；说明缺省取单据级（批量默认）
            BigDecimal chargeAmt = m.get("chargeAmount") == null || m.get("chargeAmount").toString().isBlank()
                    ? BigDecimal.ZERO : toBig(m.get("chargeAmount"));
            if (chargeAmt.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("产品收费金额不能为负数");
            String chargeType = m.get("chargeType") != null && !m.get("chargeType").toString().isBlank()
                    ? m.get("chargeType").toString() : null;
            if (chargeAmt.compareTo(BigDecimal.ZERO) > 0) {
                if (chargeType == null) throw new BusinessException("产品[" + it.getProductName() + "]已填收费金额，请选择收费类型");
                if (!ExchangeChargeType.isValid(chargeType)) throw new BusinessException("非法的收费类型：" + chargeType);
            } else {
                chargeType = null;
            }
            String chargeReason = m.get("chargeReason") != null && !m.get("chargeReason").toString().isBlank()
                    ? m.get("chargeReason").toString()
                    : (docChargeReason != null && !docChargeReason.isBlank() ? docChargeReason : null);
            it.setChargeFlag(chargeAmt.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
            it.setChargeType(chargeType);
            it.setChargeAmount(chargeAmt);
            it.setChargeReason(chargeAmt.compareTo(BigDecimal.ZERO) > 0 ? chargeReason : null);
            if (m.get("remark") != null) it.setRemark(m.get("remark").toString());
            // F7-114（2026-09-20）：同 create —— 只在 cid > 0 时赋值（超管模式不落 company_id = 0）
            if (cid != null && cid > 0) it.setCompanyId(cid);
            exchangeItemMapper.insert(it);
        }
    }

    /**
     * F7-27（2026-09-19）：换出品质只允许 A / B / C。
     *
     * <p>"换给客户的新品"不应是不良品或待整理；原先用 {@link ProductQualityType#isValid} 收全集
     * （A/B/C/DEFECT/PENDING），与前端品质下拉（仅 A/B/C）出现"前端不可选、后端可收"的宽窄不一致。</p>
     */
    private void assertOutQuality(String qt) {
        if (!ProductQualityType.A.getCode().equals(qt)
                && !ProductQualityType.B.getCode().equals(qt)
                && !ProductQualityType.C.getCode().equals(qt)) {
            throw new BusinessException("非法的换出品质等级：" + qt);
        }
    }

    /**
     * 回填来源销售单信息（客户、单号）。
     *
     * <p>2026-10-01（F8 进货/销售退换货改造·第 2 步，用户口径「子菜单裸进可以不关联」）：
     * {@code saleOrderId} 为空 = **无单换货**（线下/历史/其他渠道补录），直接返回不再抛错；
     * 与进货侧 {@code PurchaseExchangeServiceImpl#fillPurchaseOrderInfo} 的分支**完全对齐**。</p>
     *
     * <p>无来源时的把关改为：客户 / 换入仓 / 换出仓 / 明细产品必填（见 {@link #validate}），
     * 退回能否出库由审核时的库存校验决定；「可换量」校验自然跳过
     * （{@link #validateQuantity} 对 {@code saleOrderItemId} 为空的行直接 continue）。</p>
     */
    private void fillSaleOrderInfo(SaleExchange e) {
        if (e.getSaleOrderId() == null) {
            e.setSaleOrderCode(null);
            return;
        }
        // 单号冗余由前端传入或此处补查
    }

    // ==================== 审核 / 反审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleExchange e = exchangeMapper.selectById(id);
        if (e == null) throw new BusinessException("换货单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免双向库存重复变动
        if (!DocStatusGuard.claim(exchangeMapper, SaleExchange::getId, id, SaleExchange::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<SaleExchangeItem> items = getItems(id);
        if (items.isEmpty()) throw new BusinessException("换货单明细不能为空");
        // 换入/换出仓（2026-09-16 方案 A）：仓型收敛后**两者都必须是自有成品仓**；
        // 换入=退回品（品质 PENDING 待整理）、换出=良品（A 等），同一仓内按品质分行 → **允许同仓**（用户确认）
        assertWarehouseType(e.getWarehouseInId(), WarehouseType.FINISHED, "换入仓");
        assertWarehouseType(e.getWarehouseOutId(), WarehouseType.FINISHED, "换出仓");
        validateQuantity(e, items);
        // 批量取产品（退回侧与换出侧同品），避免循环内逐条查库（N+1）
        Map<Long, Product> pMap = productMap(items);

        // F7-248（2026-09-29 批 E 修复）：**非 1:1 换货必须收费**。
        // 换货**本体不写台账**（等价换货无需挂账，见下方 saveChargeReceivable 的分工），但 outQtyOf 允许
        // "退 2 换 1 / 退 1 换 2"（:624-627）⇒ 差额货物价值在系统里**无处挂账**：销售退货会写负数应收、
        // 换货不走那条路；品质折损由后续「退货整理单」的 -LOSS 覆盖，而**数量差不会进整理单**。
        // 因此：换出数量 ≠ 退回数量的明细必须已填"收费"金额，否则拒绝审核（提示改走退货单或补收费）。
        for (SaleExchangeItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal needOut = outQtyOf(it);
            if (needOut.compareTo(BigDecimal.ZERO) > 0 && needOut.compareTo(it.getQuantity()) != 0
                    && toBig(it.getChargeAmount()).compareTo(BigDecimal.ZERO) <= 0) {
                String nm = it.getProductName() != null && !it.getProductName().isBlank()
                        ? it.getProductName() : ("产品" + it.getProductId());
                throw new BusinessException("换货明细「" + nm + "」退回 " + it.getQuantity().stripTrailingZeros().toPlainString()
                        + " / 换出 " + needOut.stripTrailingZeros().toPlainString()
                        + "（非 1:1）：差额货物价值无处挂账，请为该明细填写「收费」金额后再审核，或在销售退货单中处理");
            }
        }

        for (SaleExchangeItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            // ① 退回：入售后仓，品质待整理（后续走退货整理）
            stockService.changeStock(e.getWarehouseInId(), it.getProductId(), it.getQuantity(),
                    StockChangeType.EXCHANGE_IN, e.getCode(), RelatedBillType.SALE_EXCHANGE,
                    "", e.getId(), ProductQualityType.PENDING.getCode());
            // ② 换出：从成品仓按「退回产品/换出数量/换出品质」扣减（同品换货，产品与退回一致）
            BigDecimal outQty = outQtyOf(it);
            if (outQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            Long outPid = it.getProductId();
            String qt = outQualityTypeOf(it);
            stockService.changeStock(e.getWarehouseOutId(), outPid, outQty.negate(),
                    StockChangeType.EXCHANGE_OUT, e.getCode(), RelatedBillType.SALE_EXCHANGE,
                    "", e.getId(), qt);
        }
        // 追溯联动：退回的待整理品登记到统一待整理池，供退货整理单消费（与销售退货单同一入口）
        createPendingBatches(e, items, pMap);
        // 财务联动：选择收费时生成一条独立正向应收（单号 -FEE 后缀，与换货单本体区分）
        saveChargeReceivable(e);
        SaleExchange u = new SaleExchange();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditTime(LocalDateTime.now());
        exchangeMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        SaleExchange e = exchangeMapper.selectById(id);
        if (e == null) throw new BusinessException("换货单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免双向库存重复冲回
        if (!DocStatusGuard.claim(exchangeMapper, SaleExchange::getId, id, SaleExchange::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核状态可反审核");
        // 已被退货整理的货物不允许反审核：整理单会把售后仓待整理库存转走，反审核将扣不动或造成跨单据不一致
        assertNotSorted(AfterSaleSourceType.SALE_EXCHANGE, id, "销售换货单");
        List<SaleExchangeItem> items = getItems(id);
        Map<Long, Product> pMap = productMap(items);
        for (SaleExchangeItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            // 回滚：售后仓扣回退回的待整理品
            stockService.changeStock(e.getWarehouseInId(), it.getProductId(), it.getQuantity().negate(),
                    StockChangeType.EXCHANGE_UN_AUDIT, e.getCode(), RelatedBillType.SALE_EXCHANGE,
                    "", e.getId(), ProductQualityType.PENDING.getCode());
            // 回滚：成品仓加回换出库存（按退回产品/换出数量/品质对称回补，同品换货产品一致）
            BigDecimal outQty = outQtyOf(it);
            if (outQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            Long outPid = it.getProductId();
            stockService.changeStock(e.getWarehouseOutId(), outPid, outQty,
                    StockChangeType.EXCHANGE_UN_AUDIT, e.getCode(), RelatedBillType.SALE_EXCHANGE,
                    "", e.getId(), outQualityTypeOf(it));
        }
        // 追溯联动：撤销本单登记的待整理批次（护栏已确保未被整理，可安全删除）
        deletePendingBatches(AfterSaleSourceType.SALE_EXCHANGE, id);
        // 财务联动：冲销换货收费台账（未收费时台账不存在，跳过）
        reverseReceivableIfExists(e.getCode() + "-FEE");
        // F7-48（2026-09-19）：审核信息必须用 UpdateWrapper **显式置 null** —— updateById 忽略 null 字段，
        // 反审核后 audit_time/审核人仍残留，单据看着"像已审核"（审计信息失真）。
        exchangeMapper.update(null, new LambdaUpdateWrapper<SaleExchange>()
                .eq(SaleExchange::getId, id)
                .set(SaleExchange::getStatus, DocStatus.DRAFT.getCode())
                .set(SaleExchange::getAuditorId, null)
                .set(SaleExchange::getAuditorName, null)
                .set(SaleExchange::getAuditTime, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SaleExchange e = exchangeMapper.selectById(id);
        if (e == null) throw new BusinessException("换货单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(exchangeMapper, SaleExchange::getId, id, SaleExchange::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
        SaleExchange u = new SaleExchange();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        exchangeMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 换货单不做物理删除（避免留下孤儿明细/关联数据），语义等同于作废：单据留痕
        cancel(id);
    }

    // ==================== 校验 ====================

    /** 主表校验：仓库必填且类型正确、明细非空（来源销售单可空 = 无单换货） */
    private void validate(SaleExchange e, List<Map<String, Object>> itemMaps) {
        // 2026-10-01（第 2 步）：来源销售单由「必填」改为「选填」—— 无来源时不再拦截，
        // 可换量校验随后按 saleOrderItemId 为空的行跳过（与进货侧同一口径）。
        if (e.getWarehouseInId() == null) throw new BusinessException("换入仓(成品仓)不能为空");
        if (e.getWarehouseOutId() == null) throw new BusinessException("换出仓(成品仓)不能为空");
        // 2026-09-16 方案 A：换入/换出都只能是自有成品仓 → **不再限制"两者不能相同"**（同仓内按品质分行）
        assertWarehouseType(e.getWarehouseInId(), WarehouseType.FINISHED, "换入仓");
        assertWarehouseType(e.getWarehouseOutId(), WarehouseType.FINISHED, "换出仓");
        if (itemMaps == null || itemMaps.isEmpty()) throw new BusinessException("换货明细不能为空");

        // 2026-10-01（用户口径）：退回数量上限 = min(来源销售明细数量, 换出仓 + 产品 + 换出品质的库存)；
        // **无来源（无单换货）时上限 = 该仓该品质库存**。故按「有锚点 / 无锚点」两维度聚合。
        Map<Long, BigDecimal> qtyMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        Map<Long, String> qualityMap = new HashMap<>();
        Map<String, BigDecimal> qtyByProduct = new HashMap<>();
        Map<String, Long> productByKey = new HashMap<>();
        Map<String, String> qualityByKey = new HashMap<>();
        for (Map<String, Object> m : itemMaps) {
            Long pid = m.get("productId") != null && !m.get("productId").toString().isBlank()
                    ? Long.valueOf(m.get("productId").toString()) : null;
            if (pid == null) throw new BusinessException("退回产品不能为空");
            // 换出侧只支持同品：换出产品固定为退回产品，无需单独校验产品
            // 换出品质：未指定默认 A 规，指定则必须合法
            String qt = m.get("outQualityType") != null && !m.get("outQualityType").toString().isBlank()
                    ? m.get("outQualityType").toString() : ProductQualityType.A.getCode();
            assertOutQuality(qt);   // F7-27：换出品质仅允许 A/B/C

            Object soiObj = m.get("saleOrderItemId");
            if (soiObj == null || soiObj.toString().isBlank()) {
                // 无单换货：按「产品 + 换出品质」聚合，稍后与库存比较（原来这里是 continue ⇒ 完全不校验）
                String key = pid + "|" + qt;
                qtyByProduct.merge(key, toBig(m.get("quantity")), BigDecimal::add);
                productByKey.put(key, pid);
                qualityByKey.put(key, qt);
                continue;
            }
            Long soiId = Long.valueOf(soiObj.toString());
            qtyMap.merge(soiId, toBig(m.get("quantity")), BigDecimal::add);
            qualityMap.putIfAbsent(soiId, qt);
            Product p = productMapper.selectById(pid);
            if (p != null) nameMap.put(soiId, p.getName());
        }
        // 编辑草稿时排除自身（草稿不计入"已换"，此处仅为口径统一，F1-1）
        checkCanExchange(qtyMap, nameMap, qualityMap, e.getId(), e.getWarehouseOutId());
        // 无锚点（无单换货）：上限 = 换出仓该品质库存
        assertOutStockWithin(e.getWarehouseOutId(), qtyByProduct, productByKey, qualityByKey);
    }

    /**
     * 无锚点明细（无单换货）的退回数量校验：上限 = 换出仓 + 产品 + 换出品质的库存数量。
     * 与 {@link #checkCanExchange} 的库存取法一致（{@code getQuantity} 缺省取 stockForm=MATERIAL）。
     */
    private void assertOutStockWithin(Long warehouseOutId, Map<String, BigDecimal> qtyByProduct,
                                      Map<String, Long> productByKey, Map<String, String> qualityByKey) {
        for (Map.Entry<String, BigDecimal> en : qtyByProduct.entrySet()) {
            Long pid = productByKey.get(en.getKey());
            BigDecimal stock = stockService.getQuantity(warehouseOutId, pid, qualityByKey.get(en.getKey()));
            if (en.getValue().compareTo(stock) > 0) {
                Product p = productMapper.selectById(pid);
                throw new BusinessException("产品[" + (p != null ? p.getName() : pid) + "]本次退回数量"
                        + fmt(en.getValue()) + "超过该仓库该品质的库存数量" + fmt(stock) + "（未关联销售单）");
            }
        }
    }

    /** 审核时复核退回数量上限（用已落库的明细） */
    private void validateQuantity(SaleExchange e, List<SaleExchangeItem> items) {
        Map<Long, BigDecimal> qtyMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        Map<Long, String> qualityMap = new HashMap<>();
        Map<String, BigDecimal> qtyByProduct = new HashMap<>();
        Map<String, Long> productByKey = new HashMap<>();
        Map<String, String> qualityByKey = new HashMap<>();
        for (SaleExchangeItem it : items) {
            if (it.getProductId() == null) continue;
            String qt = outQualityTypeOf(it);
            if (it.getSaleOrderItemId() == null) {
                // 无单换货：按「产品 + 换出品质」聚合（原来这里 continue ⇒ 完全不校验）
                String key = it.getProductId() + "|" + qt;
                qtyByProduct.merge(key, nz(it.getQuantity()), BigDecimal::add);
                productByKey.put(key, it.getProductId());
                qualityByKey.put(key, qt);
                continue;
            }
            qtyMap.merge(it.getSaleOrderItemId(), nz(it.getQuantity()), BigDecimal::add);
            qualityMap.putIfAbsent(it.getSaleOrderItemId(), qt);
            Product p = productMapper.selectById(it.getProductId());
            if (p != null) nameMap.put(it.getSaleOrderItemId(), p.getName());
        }
        // ⚠️ F1-1（2026-09-18 审核修复）：审核时本单已被 claim 置为 AUDITED，必须排除自身，
        // 否则"已换量"会包含本单退回量（实测：已售5/已退0，退回 3 时误报"已换 3、可换 2"）
        checkCanExchange(qtyMap, nameMap, qualityMap, e.getId(), e.getWarehouseOutId());
        assertOutStockWithin(e.getWarehouseOutId(), qtyByProduct, productByKey, qualityByKey);
    }

    /**
     * 退回数量上限校验（只约束**退回数量**，换出属正常出库不受限）。
     *
     * <p><b>2026-10-01 口径变更（用户口径）</b>：上限 = <b>min(该销售明细的销售数量,
     * 换出仓 + 该产品 + 该换出品质的库存数量)</b>；**不再扣历史累计**（已退/已换）。
     * 用户口径是「已换也可以再换：只要有库存就可以一直换，但每一次不能超过销售总数」。</p>
     *
     * @param qualityMap     锚点 → 换出品质（取库存用；缺省按 A 规）
     * @param warehouseOutId 换出仓（取库存用）
     * @param excludeExchangeId 需排除的单据 id（**审核中的本单**，见 F1-1：claim 已把本单置为 AUDITED，
     *                          不排除就会把本单退回量算进"已换"）；当前口径下该参数只影响报错信息里的参考值。
     */
    private void checkCanExchange(Map<Long, BigDecimal> qtyMap, Map<Long, String> nameMap,
                                  Map<Long, String> qualityMap, Long excludeExchangeId, Long warehouseOutId) {
        if (qtyMap.isEmpty()) return;
        List<SaleOrderItem> oiList = saleOrderItemMapper.selectBatchIds(qtyMap.keySet());
        // F7-113（2026-09-20）：已退/已换量**一次批量聚合**（原先在下面的循环里按明细逐条查
        // ⇒ 每条明细 2 次"全表已审核单 + in"扫描）。
        Set<Long> oiIds = oiList.stream().map(SaleOrderItem::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, BigDecimal> returnedMap = alreadyReturnedBatch(oiIds);
        Map<Long, BigDecimal> exchangedMap = alreadyExchangedBatch(oiIds, excludeExchangeId);
        for (SaleOrderItem oi : oiList) {
            BigDecimal sold = nz(oi.getQuantity());
            BigDecimal returned = returnedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            BigDecimal exchanged = exchangedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            BigDecimal thisQty = qtyMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            // 2026-10-01（用户口径）：上限 = min(销售数量, 换出仓 + 该产品 + 该换出品质的库存数量)；
            // 库存按 stockForm=MATERIAL 取（getQuantity 的缺省重载），与审核换出扣减口径一致。
            String qt = qualityMap.getOrDefault(oi.getId(), ProductQualityType.A.getCode());
            BigDecimal stock = stockService.getQuantity(warehouseOutId, oi.getProductId(), qt);
            BigDecimal limit = sold.min(stock);
            if (thisQty.compareTo(limit) > 0) {
                String name = nameMap.getOrDefault(oi.getId(), String.valueOf(oi.getProductId()));
                throw new BusinessException("产品[" + name + "]退回数量" + fmt(thisQty)
                        + "超过上限" + fmt(limit) + "（销售数量" + fmt(sold)
                        + "，换出仓该品质库存" + fmt(stock) + "，取较小值；历史已退" + fmt(returned)
                        + "、已换" + fmt(exchanged) + "）");
            }
        }
    }

    private void assertWarehouseType(Long warehouseId, WarehouseType expect, String label) {
        Warehouse wh = warehouseId == null ? null : warehouseMapper.selectById(warehouseId);
        if (wh == null) throw new BusinessException(label + "不存在");
        if (!expect.getCode().equals(wh.getWarehouseType()))
            throw new BusinessException(label + "必须是" + expect.getCode() + "，当前仓库类型为：" + wh.getWarehouseType());
    }

    // ==================== 明细字段取值（退回侧 / 换出侧） ====================

    /** 批量取「退回侧 + 换出侧」产品映射（同品换货，两侧产品一致），避免循环内 selectById（N+1） */
    private Map<Long, Product> productMap(List<SaleExchangeItem> items) {
        Set<Long> pids = new HashSet<>();
        for (SaleExchangeItem it : items) {
            if (it.getProductId() != null) pids.add(it.getProductId());
        }
        Map<Long, Product> map = new HashMap<>();
        if (!pids.isEmpty()) productMapper.selectBatchIds(pids).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    /** 换出数量：未指定时回退为退回数量（默认 1:1，可手工改成退 2 换 1） */
    private BigDecimal outQtyOf(SaleExchangeItem it) {
        return it.getOutQuantity() != null ? it.getOutQuantity() : nz(it.getQuantity());
    }

    /** 换出品质：未指定时默认 A 规，并校验合法性 */
    private String outQualityTypeOf(SaleExchangeItem it) {
        String qt = it.getOutQualityType() != null && !it.getOutQualityType().isBlank()
                ? it.getOutQualityType() : ProductQualityType.A.getCode();
        if (!ProductQualityType.isValid(qt)) throw new BusinessException("非法的换出品质等级：" + qt);
        return qt;
    }

    // ==================== 售后待整理批次（与销售退货单共用统一追溯池） ====================

    /**
     * 登记售后待整理批次：换货退回的待整理品进入统一待整理池，供退货整理单消费。
     * <p>先按 (source_type, source_item_id) 清掉残留再插入，保证「反审核 → 重新审核」不重复登记。</p>
     */
    private void createPendingBatches(SaleExchange e, List<SaleExchangeItem> items, Map<Long, Product> pMap) {
        Long cid = CompanyContext.get();
        for (SaleExchangeItem it : items) {
            if (it.getId() == null) continue;
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product p = pMap.get(it.getProductId());
            afterSalePendingMapper.delete(new LambdaQueryWrapper<AfterSalePending>()
                    .eq(AfterSalePending::getSourceType, AfterSaleSourceType.SALE_EXCHANGE.getCode())
                    .eq(AfterSalePending::getSourceItemId, it.getId()));
            AfterSalePending pending = new AfterSalePending();
            pending.setSourceType(AfterSaleSourceType.SALE_EXCHANGE.getCode());
            pending.setSourceId(e.getId());
            pending.setSourceItemId(it.getId());
            pending.setSourceCode(e.getCode());
            pending.setSourceDate(e.getExchangeDate());
            pending.setWarehouseId(e.getWarehouseInId());
            pending.setCustomerId(e.getCustomerId());
            pending.setProductId(it.getProductId());
            pending.setProductName(it.getProductName() != null && !it.getProductName().isBlank()
                    ? it.getProductName() : (p != null ? p.getName() : ""));
            pending.setUnit(p != null ? p.getUnit() : "");
            pending.setQuantity(it.getQuantity());
            pending.setSortedQuantity(BigDecimal.ZERO);
            pending.setUnitPrice(nz(it.getUnitPrice()));
            if (cid != null && cid > 0) pending.setCompanyId(cid);
            afterSalePendingMapper.insert(pending);
        }
    }

    /** 撤销待整理批次：护栏已确保未被整理，可安全删除 */
    private void deletePendingBatches(AfterSaleSourceType type, Long sourceId) {
        afterSalePendingMapper.delete(new LambdaQueryWrapper<AfterSalePending>()
                .eq(AfterSalePending::getSourceType, type.getCode())
                .eq(AfterSalePending::getSourceId, sourceId));
    }

    /**
     * 反审核护栏：来源单据已有货物被退货整理（待整理批次已产生已整理数量）则禁止反审核，
     * 否则售后仓库存已被整理单转走，反审核将扣不动或造成跨单据不一致。
     */
    private void assertNotSorted(AfterSaleSourceType type, Long sourceId, String billLabel) {
        List<AfterSalePending> batches = afterSalePendingMapper.selectList(new LambdaQueryWrapper<AfterSalePending>()
                .eq(AfterSalePending::getSourceType, type.getCode())
                .eq(AfterSalePending::getSourceId, sourceId));
        BigDecimal sorted = BigDecimal.ZERO;
        for (AfterSalePending b : batches) {
            if (b.getSortedQuantity() != null) sorted = sorted.add(b.getSortedQuantity());
        }
        if (sorted.compareTo(BigDecimal.ZERO) > 0)
            throw new BusinessException("该" + billLabel + "已有货物被退货整理（已整理 " + fmt(sorted)
                    + "），无法反审核，请先反审核对应的退货整理单");
    }

    // ==================== 换货收费 ====================

    /**
     * 收费归一化（2026-09-21 逐产品口径）：**明细级为准，单据级只作"批量默认"**。
     * <p>用户口径：「销售退货单和销售换货单应该都有付费，而且付费需要精确到产品上」⇒ 金额挂在明细行
     * （一行 = 一个产品），单据级 charge_amount 由 {@link #recalcDocCharge} 按 Σ 明细回写。</p>
     */
    private void normalizeCharge(SaleExchange e, List<Map<String, Object>> itemMaps) {
        if (e.getChargeType() != null && !e.getChargeType().isBlank() && !ExchangeChargeType.isValid(e.getChargeType()))
            throw new BusinessException("非法的收费类型：" + e.getChargeType());
        boolean anyItem = hasItemCharge(itemMaps);
        // 兼容旧前端（只填单据级金额、没逐行填）：落到**第一条明细**（保证「Σ明细 = 单据金额」恒等）
        if (!anyItem && e.getChargeFlag() != null && e.getChargeFlag() == 1
                && toBig(e.getChargeAmount()).compareTo(BigDecimal.ZERO) > 0) {
            applyDocChargeToFirstItem(itemMaps, e.getChargeType(), e.getChargeAmount(), e.getChargeReason());
            anyItem = true;
        }
        if (!anyItem && e.getChargeFlag() != null && e.getChargeFlag() == 1)
            throw new BusinessException("已选择收费，请为具体产品填写收费金额（收费精确到产品）");
        e.setChargeFlag(anyItem ? 1 : 0);
        e.setChargeAmount(BigDecimal.ZERO);
        if (!anyItem) {
            e.setChargeType(null);
            e.setChargeReason(null);
        }
    }

    /** 兼容旧前端：把"单据级收费"落到第一条明细（类型缺省 OTHER） */
    private void applyDocChargeToFirstItem(List<Map<String, Object>> itemMaps, String type, BigDecimal amount, String reason) {
        if (itemMaps == null || itemMaps.isEmpty())
            throw new BusinessException("已选择收费，请先添加明细（收费精确到产品）");
        Map<String, Object> first = itemMaps.get(0);
        first.put("chargeAmount", amount);
        first.put("chargeType", type != null && !type.isBlank() ? type : ExchangeChargeType.OTHER.getCode());
        if (reason != null) first.put("chargeReason", reason);
    }

    /** 本次提交里是否有任意一行填了收费金额（> 0） */
    private boolean hasItemCharge(List<Map<String, Object>> itemMaps) {
        if (itemMaps == null) return false;
        for (Map<String, Object> m : itemMaps) {
            Object amt = m.get("chargeAmount");
            if (amt != null && !amt.toString().isBlank() && toBig(amt).compareTo(BigDecimal.ZERO) > 0) return true;
        }
        return false;
    }

    /** 主表收费 = Σ(明细)：charge_flag=任一行收费；charge_amount=Σ；charge_type 仅当各收费行**类型一致**时回填 */
    private void recalcDocCharge(Long exchangeId) {
        List<SaleExchangeItem> items = exchangeItemMapper.selectList(
                new LambdaQueryWrapper<SaleExchangeItem>().eq(SaleExchangeItem::getExchangeId, exchangeId));
        BigDecimal sum = BigDecimal.ZERO;
        Set<String> types = new java.util.LinkedHashSet<>();
        for (SaleExchangeItem it : items) {
            if (toBig(it.getChargeAmount()).compareTo(BigDecimal.ZERO) > 0) {
                sum = sum.add(it.getChargeAmount());
                if (it.getChargeType() != null && !it.getChargeType().isBlank()) types.add(it.getChargeType());
            }
        }
        exchangeMapper.update(null, new LambdaUpdateWrapper<SaleExchange>()
                .eq(SaleExchange::getId, exchangeId)
                .set(SaleExchange::getChargeFlag, sum.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0)
                .set(SaleExchange::getChargeAmount, sum)
                .set(SaleExchange::getChargeType, types.size() == 1 ? types.iterator().next() : null));
    }

    /**
     * 审核时生成换货收费应收：单号 -FEE 后缀（与换货单本体区分，便于反审核精确冲销）。
     * <p>金额 = <b>Σ 明细行收费</b>（口径 A：一张单据一条台账）；remark 逐产品列出，
     * 财务列表能直接看到"哪个产品收了多少"，客户付款仍可一笔核销整单。</p>
     */
    private void saveChargeReceivable(SaleExchange e) {
        List<SaleExchangeItem> items = getItems(e.getId()); // 已回填 SKU/产品名
        BigDecimal total = BigDecimal.ZERO;
        List<String> parts = new ArrayList<>();
        for (SaleExchangeItem it : items) {
            BigDecimal amt = toBig(it.getChargeAmount());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;
            total = total.add(amt);
            String name = it.getProductName() != null && !it.getProductName().isBlank()
                    ? it.getProductName() : "产品" + it.getProductId();
            parts.add(name + " " + amt.stripTrailingZeros().toPlainString()
                    + (it.getChargeType() != null && !it.getChargeType().isBlank() ? "（" + it.getChargeType() + "）" : ""));
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) return;
        FinanceReceivable fr = new FinanceReceivable();
        fr.setBillNo(e.getCode() + "-FEE");
        fr.setCustomerId(e.getCustomerId());
        fr.setCustomerName(customerName(e.getCustomerId()));
        fr.setSourceBillType(SourceBillType.SALE_EXCHANGE_CHARGE.getCode());
        fr.setSourceBillNo(e.getCode());
        fr.setSourceId(e.getId());
        fr.setAmount(total);
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(total);
        fr.setDueDate(e.getExchangeDate());
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        fr.setRemark("销售换货收费（逐产品）：" + String.join("；", parts)
                + (e.getChargeReason() != null && !e.getChargeReason().isBlank() ? "；说明：" + e.getChargeReason() : ""));
        saveReceivable(fr);
    }

    /**
     * 保存应收台账：按 billNo 复用已存在记录后再写入。
     * <p>反审核是「冲销」（仅置 CANCELLED，记录保留），再次审核直接 insert 同 billNo 会撞 uk_bill_no。</p>
     */
    private void saveReceivable(FinanceReceivable fr) {
        FinanceReceivable exist = financeReceivableMapper.selectOne(
                new LambdaQueryWrapper<FinanceReceivable>().eq(FinanceReceivable::getBillNo, fr.getBillNo()));
        if (exist == null) {
            financeReceivableMapper.insert(fr);
            return;
        }
        financeReceivableMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FinanceReceivable>()
                        .eq(FinanceReceivable::getId, exist.getId())
                        .set(FinanceReceivable::getCustomerId, fr.getCustomerId())
                        .set(FinanceReceivable::getCustomerName, fr.getCustomerName())
                        .set(FinanceReceivable::getSourceBillType, fr.getSourceBillType())
                        .set(FinanceReceivable::getSourceBillNo, fr.getSourceBillNo())
                        .set(FinanceReceivable::getSourceId, fr.getSourceId())
                        .set(FinanceReceivable::getAmount, fr.getAmount())
                        .set(FinanceReceivable::getPaidAmount, fr.getPaidAmount())
                        .set(FinanceReceivable::getUnpaidAmount, fr.getUnpaidAmount())
                        .set(FinanceReceivable::getDueDate, fr.getDueDate())
                        .set(FinanceReceivable::getStatus, fr.getStatus())
                        .set(FinanceReceivable::getRemark, fr.getRemark()));
    }

    /** 冲销应收台账：不存在则跳过（未收费时本就没有台账） */
    private void reverseReceivableIfExists(String billNo) {
        if (billNo == null || billNo.isBlank()) return;
        FinanceReceivable fr = financeReceivableMapper.selectOne(
                new LambdaQueryWrapper<FinanceReceivable>().eq(FinanceReceivable::getBillNo, billNo));
        if (fr == null) return;
        receivableHelper.reverseReceivable(billNo);
    }

    private String customerName(Long customerId) {
        if (customerId == null) return "";
        Customer c = customerMapper.selectById(customerId);
        return c != null && c.getName() != null ? c.getName() : "";
    }

    // ==================== 工具 ====================

    /** 单号：前缀 + yyyyMMdd + 三位序号 */
    private String gen() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.SALE_EXCHANGE + d;
        LambdaQueryWrapper<SaleExchange> w = new LambdaQueryWrapper<SaleExchange>()
                .likeRight(SaleExchange::getCode, pat).orderByDesc(SaleExchange::getCode).last("LIMIT 1");
        SaleExchange last = exchangeMapper.selectOne(w);
        // F7-109（2026-09-20）：统一走 BillNoSeq（详见该类 javadoc）。
        int seq = BillNoSeq.lastSeq(last == null ? null : last.getCode(), pat) + 1;
        return BillNoSeq.formatUnique(pat, seq, cand -> exchangeMapper.selectCount(new LambdaQueryWrapper<SaleExchange>().eq(SaleExchange::getCode, cand)) > 0) /* F7-261 冲突检测+重试 */;
    }

    private String warehouseName(Long id) {
        if (id == null) return "";
        Warehouse w = warehouseMapper.selectById(id);
        return w != null && w.getWarehouseName() != null ? w.getWarehouseName() : "";
    }

    private String str(Object v) { return v == null ? null : v.toString(); }

    private Long toLong(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try { return Long.valueOf(v.toString()); } catch (Exception e) { return null; }
    }

    private BigDecimal toBig(Object v) {
        if (v == null || v.toString().isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** 去掉无意义的末尾 0，便于提示语展示 */
    private String fmt(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
