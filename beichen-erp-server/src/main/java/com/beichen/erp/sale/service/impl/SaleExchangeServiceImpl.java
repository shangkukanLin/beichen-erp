package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
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
 * 销售换货单服务实现（同品换货，强关联销售单）
 * <p>
 * 审核时双向联动库存：
 * <ol>
 *   <li>退回：入「换入仓」（售后仓），品质记 {@code PENDING}(待分类)，后续走退货整理流程；</li>
 *   <li>换出：从「换出仓」（成品仓）按明细 {@code qualityType} 扣减。</li>
 * </ol>
 * 可换数量 = 已售 − 已退 − 已换，支持同一销售明细多次部分换货。
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
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SaleExchange e : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("code", e.getCode());
            m.put("saleOrderId", e.getSaleOrderId());
            m.put("saleOrderCode", e.getSaleOrderCode());
            m.put("customerId", e.getCustomerId());
            m.put("warehouseInId", e.getWarehouseInId());
            m.put("warehouseInName", warehouseName(e.getWarehouseInId()));
            m.put("warehouseOutId", e.getWarehouseOutId());
            m.put("warehouseOutName", warehouseName(e.getWarehouseOutId()));
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
        Map<Long, BigDecimal> exchangedMap = alreadyExchangedBatch(oiIds);
        List<Map<String, Object>> res = new ArrayList<>();
        for (SaleOrderItem oi : oiList) {
            Product p = pMap.get(oi.getProductId());
            BigDecimal sold = nz(oi.getQuantity());
            BigDecimal returned = returnedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            BigDecimal exchanged = exchangedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            BigDecimal canExchange = sold.subtract(returned).subtract(exchanged);
            if (canExchange.compareTo(BigDecimal.ZERO) < 0) canExchange = BigDecimal.ZERO;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("saleOrderItemId", oi.getId());
            m.put("productId", oi.getProductId());
            m.put("productName", p != null ? p.getName() : "");
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

    /** 批量取已退量（按销售单明细ID聚合）：已审核销售退单中该销售明细的累计数量 */
    private Map<Long, BigDecimal> alreadyReturnedBatch(Set<Long> saleOrderItemIds) {
        Map<Long, BigDecimal> res = new HashMap<>();
        if (saleOrderItemIds == null || saleOrderItemIds.isEmpty()) return res;
        List<SaleReturn> audited = saleReturnMapper.selectList(new LambdaQueryWrapper<SaleReturn>()
                .eq(SaleReturn::getStatus, DocStatus.AUDITED.getCode()));
        if (audited.isEmpty()) return res;
        List<Long> returnIds = audited.stream().map(SaleReturn::getId).collect(Collectors.toList());
        List<SaleReturnItem> items = saleReturnItemMapper.selectList(new LambdaQueryWrapper<SaleReturnItem>()
                .in(SaleReturnItem::getReturnId, returnIds)
                .in(SaleReturnItem::getSaleOrderItemId, saleOrderItemIds));
        for (SaleReturnItem it : items) {
            if (it.getSaleOrderItemId() == null) continue;
            res.merge(it.getSaleOrderItemId(), nz(it.getQuantity()), BigDecimal::add);
        }
        return res;
    }

    /** 批量取已换量（按销售单明细ID聚合）：已审核换货单中该销售明细的累计数量 */
    private Map<Long, BigDecimal> alreadyExchangedBatch(Set<Long> saleOrderItemIds) {
        Map<Long, BigDecimal> res = new HashMap<>();
        if (saleOrderItemIds == null || saleOrderItemIds.isEmpty()) return res;
        List<SaleExchange> audited = exchangeMapper.selectList(new LambdaQueryWrapper<SaleExchange>()
                .eq(SaleExchange::getStatus, DocStatus.AUDITED.getCode()));
        if (audited.isEmpty()) return res;
        List<Long> exIds = audited.stream().map(SaleExchange::getId).collect(Collectors.toList());
        List<SaleExchangeItem> items = exchangeItemMapper.selectList(new LambdaQueryWrapper<SaleExchangeItem>()
                .in(SaleExchangeItem::getExchangeId, exIds)
                .in(SaleExchangeItem::getSaleOrderItemId, saleOrderItemIds));
        for (SaleExchangeItem it : items) {
            if (it.getSaleOrderItemId() == null) continue;
            res.merge(it.getSaleOrderItemId(), nz(it.getQuantity()), BigDecimal::add);
        }
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
        if (cid != null) exchange.setCompanyId(cid);
        validate(exchange, itemMaps);
        normalizeCharge(exchange);
        exchangeMapper.insert(exchange);
        saveItems(exchange.getId(), itemMaps);
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
        normalizeCharge(exchange);
        exchange.setCode(old.getCode());
        exchange.setStatus(old.getStatus());
        exchangeMapper.updateById(exchange);
        saveItems(exchange.getId(), itemMaps);
        return exchangeMapper.selectById(exchange.getId());
    }

    /**
     * 保存明细：先删后插。
     * <p>明细拆「退回侧 + 换出侧」：只支持同品换货，换出产品固定为退回产品；
     * 换出数量未指定时默认等于退回数量，换出单价默认取原销售单价。</p>
     */
    private void saveItems(Long exchangeId, List<Map<String, Object>> itemMaps) {
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
            if (!ProductQualityType.isValid(qt)) throw new BusinessException("非法的换出品质等级：" + qt);
            it.setOutQualityType(qt);

            if (m.get("remark") != null) it.setRemark(m.get("remark").toString());
            it.setCompanyId(cid);
            exchangeItemMapper.insert(it);
        }
    }

    /** 回填来源销售单信息（客户、单号） */
    private void fillSaleOrderInfo(SaleExchange e) {
        if (e.getSaleOrderId() == null) throw new BusinessException("换货单必须选择来源销售单");
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
        // 换入=退回品（品质 PENDING 待分类）、换出=良品（A 等），同一仓内按品质分行 → **允许同仓**（用户确认）
        assertWarehouseType(e.getWarehouseInId(), WarehouseType.FINISHED, "换入仓");
        assertWarehouseType(e.getWarehouseOutId(), WarehouseType.FINISHED, "换出仓");
        validateQuantity(e, items);
        // 批量取产品（退回侧与换出侧同品），避免循环内逐条查库（N+1）
        Map<Long, Product> pMap = productMap(items);

        for (SaleExchangeItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            // ① 退回：入售后仓，品质待分类（后续走退货整理）
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
        // 追溯联动：退回的待分类品登记到统一待整理池，供退货整理单消费（与销售退单同一入口）
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
        // 已被退货整理的货物不允许反审核：整理单会把售后仓待分类库存转走，反审核将扣不动或造成跨单据不一致
        assertNotSorted(AfterSaleSourceType.SALE_EXCHANGE, id, "销售换货单");
        List<SaleExchangeItem> items = getItems(id);
        Map<Long, Product> pMap = productMap(items);
        for (SaleExchangeItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            // 回滚：售后仓扣回退回的待分类品
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
        SaleExchange u = new SaleExchange();
        u.setId(id);
        u.setStatus(DocStatus.DRAFT.getCode());
        exchangeMapper.updateById(u);
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

    /** 主表校验：必关联销售单、仓库必填且类型正确、明细非空 */
    private void validate(SaleExchange e, List<Map<String, Object>> itemMaps) {
        if (e.getSaleOrderId() == null) throw new BusinessException("换货单必须选择来源销售单");
        if (e.getWarehouseInId() == null) throw new BusinessException("换入仓(成品仓)不能为空");
        if (e.getWarehouseOutId() == null) throw new BusinessException("换出仓(成品仓)不能为空");
        // 2026-09-16 方案 A：换入/换出都只能是自有成品仓 → **不再限制"两者不能相同"**（同仓内按品质分行）
        assertWarehouseType(e.getWarehouseInId(), WarehouseType.FINISHED, "换入仓");
        assertWarehouseType(e.getWarehouseOutId(), WarehouseType.FINISHED, "换出仓");
        if (itemMaps == null || itemMaps.isEmpty()) throw new BusinessException("换货明细不能为空");

        // 明细校验 + 可换量校验（草稿明细，按明细行聚合；可换量只约束退回数量）
        Map<Long, BigDecimal> qtyMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        for (Map<String, Object> m : itemMaps) {
            Long pid = m.get("productId") != null && !m.get("productId").toString().isBlank()
                    ? Long.valueOf(m.get("productId").toString()) : null;
            if (pid == null) throw new BusinessException("退回产品不能为空");
            // 换出侧只支持同品：换出产品固定为退回产品，无需单独校验产品
            // 换出品质：未指定默认 A 规，指定则必须合法
            String qt = m.get("outQualityType") != null && !m.get("outQualityType").toString().isBlank()
                    ? m.get("outQualityType").toString() : ProductQualityType.A.getCode();
            if (!ProductQualityType.isValid(qt)) throw new BusinessException("非法的换出品质等级：" + qt);

            Object soiObj = m.get("saleOrderItemId");
            if (soiObj == null || soiObj.toString().isBlank()) continue;
            Long soiId = Long.valueOf(soiObj.toString());
            qtyMap.merge(soiId, toBig(m.get("quantity")), BigDecimal::add);
            Product p = productMapper.selectById(pid);
            if (p != null) nameMap.put(soiId, p.getName());
        }
        // 编辑草稿时排除自身（草稿不计入"已换"，此处仅为口径统一，F1-1）
        checkCanExchange(qtyMap, nameMap, e.getId());
    }

    /** 审核时复核可换量（用已落库的明细） */
    private void validateQuantity(SaleExchange e, List<SaleExchangeItem> items) {
        Map<Long, BigDecimal> qtyMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        for (SaleExchangeItem it : items) {
            if (it.getSaleOrderItemId() == null) continue;
            qtyMap.merge(it.getSaleOrderItemId(), nz(it.getQuantity()), BigDecimal::add);
            if (it.getProductId() != null) {
                Product p = productMapper.selectById(it.getProductId());
                if (p != null) nameMap.put(it.getSaleOrderItemId(), p.getName());
            }
        }
        // ⚠️ F1-1（2026-09-18 审核修复）：审核时本单已被 claim 置为 AUDITED，必须排除自身，
        // 否则"已换量"会包含本单退回量（实测：已售5/已退0，退回 3 时误报"已换 3、可换 2"）
        checkCanExchange(qtyMap, nameMap, e.getId());
    }

    /**
     * 可换量 = 已售 − 已退 − 已换。
     *
     * @param excludeExchangeId 需排除的单据 id（**审核中的本单**，见 F1-1：claim 已把本单置为 AUDITED，
     *                          不排除就会把本单退回量算进"已换"，导致"退回量 > 余量一半"被误拒）；
     *                          创建/编辑草稿传当前 id（无则 null）
     */
    private void checkCanExchange(Map<Long, BigDecimal> qtyMap, Map<Long, String> nameMap, Long excludeExchangeId) {
        if (qtyMap.isEmpty()) return;
        List<SaleOrderItem> oiList = saleOrderItemMapper.selectBatchIds(qtyMap.keySet());
        for (SaleOrderItem oi : oiList) {
            BigDecimal sold = nz(oi.getQuantity());
            BigDecimal returned = alreadyReturned(oi.getId());
            BigDecimal exchanged = alreadyExchanged(oi.getId(), excludeExchangeId);
            BigDecimal canEx = sold.subtract(returned).subtract(exchanged);
            BigDecimal thisQty = qtyMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            if (thisQty.compareTo(canEx) > 0) {
                String name = nameMap.getOrDefault(oi.getId(), String.valueOf(oi.getProductId()));
                throw new BusinessException("产品[" + name + "]换货数量超过可换数量（已售" + fmt(sold)
                        + "，已退" + fmt(returned) + "，已换" + fmt(exchanged) + "，可换" + fmt(canEx) + "）");
            }
        }
    }

    /** 已退量：已审核销售退货单中该销售明细的累计数量 */
    private BigDecimal alreadyReturned(Long saleOrderItemId) {
        List<SaleReturn> audited = saleReturnMapper.selectList(new LambdaQueryWrapper<SaleReturn>()
                .eq(SaleReturn::getStatus, DocStatus.AUDITED.getCode()));
        if (audited.isEmpty()) return BigDecimal.ZERO;
        List<Long> ids = audited.stream().map(SaleReturn::getId).collect(Collectors.toList());
        List<SaleReturnItem> items = saleReturnItemMapper.selectList(new LambdaQueryWrapper<SaleReturnItem>()
                .in(SaleReturnItem::getReturnId, ids)
                .eq(SaleReturnItem::getSaleOrderItemId, saleOrderItemId));
        BigDecimal sum = BigDecimal.ZERO;
        for (SaleReturnItem it : items) sum = sum.add(nz(it.getQuantity()));
        return sum;
    }

    /** 已换量：已审核换货单中该销售明细的累计数量（**排除 excludeExchangeId 指定的本单**，见 F1-1） */
    private BigDecimal alreadyExchanged(Long saleOrderItemId, Long excludeExchangeId) {
        LambdaQueryWrapper<SaleExchange> w = new LambdaQueryWrapper<SaleExchange>()
                .eq(SaleExchange::getStatus, DocStatus.AUDITED.getCode());
        if (excludeExchangeId != null) w.ne(SaleExchange::getId, excludeExchangeId);
        List<SaleExchange> audited = exchangeMapper.selectList(w);
        if (audited.isEmpty()) return BigDecimal.ZERO;
        List<Long> ids = audited.stream().map(SaleExchange::getId).collect(Collectors.toList());
        List<SaleExchangeItem> items = exchangeItemMapper.selectList(new LambdaQueryWrapper<SaleExchangeItem>()
                .in(SaleExchangeItem::getExchangeId, ids)
                .eq(SaleExchangeItem::getSaleOrderItemId, saleOrderItemId));
        BigDecimal sum = BigDecimal.ZERO;
        for (SaleExchangeItem it : items) sum = sum.add(nz(it.getQuantity()));
        return sum;
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

    // ==================== 售后待整理批次（与销售退单共用统一追溯池） ====================

    /**
     * 登记售后待整理批次：换货退回的待分类品进入统一待整理池，供退货整理单消费。
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

    /** 收费字段归一化：不收费则金额归零、类型清空；收费则类型必须合法且金额必须 &gt; 0 */
    private void normalizeCharge(SaleExchange e) {
        boolean charged = e.getChargeFlag() != null && e.getChargeFlag() == 1;
        if (!charged) {
            e.setChargeFlag(0);
            e.setChargeType(null);
            e.setChargeAmount(BigDecimal.ZERO);
            e.setChargeReason(null);
            return;
        }
        if (e.getChargeType() == null || e.getChargeType().isBlank())
            throw new BusinessException("已选择收费，请选择收费类型");
        if (!ExchangeChargeType.isValid(e.getChargeType()))
            throw new BusinessException("非法的收费类型：" + e.getChargeType());
        if (nz(e.getChargeAmount()).compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("已选择收费，收费金额必须大于 0");
    }

    /** 审核时生成换货收费应收：单号 -FEE 后缀，与换货单本体区分，便于反审核精确冲销 */
    private void saveChargeReceivable(SaleExchange e) {
        boolean charged = e.getChargeFlag() != null && e.getChargeFlag() == 1;
        if (!charged || nz(e.getChargeAmount()).compareTo(BigDecimal.ZERO) <= 0) return;
        FinanceReceivable fr = new FinanceReceivable();
        fr.setBillNo(e.getCode() + "-FEE");
        fr.setCustomerId(e.getCustomerId());
        fr.setCustomerName(customerName(e.getCustomerId()));
        fr.setSourceBillType(SourceBillType.SALE_EXCHANGE_CHARGE.getCode());
        fr.setSourceBillNo(e.getCode());
        fr.setSourceId(e.getId());
        fr.setAmount(e.getChargeAmount());
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(e.getChargeAmount());
        fr.setDueDate(e.getExchangeDate());
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        fr.setRemark("销售换货收费"
                + (e.getChargeReason() != null && !e.getChargeReason().isBlank() ? "：" + e.getChargeReason() : ""));
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
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; }
            catch (Exception ex) { seq = 1; }
        }
        return pat + String.format("%03d", seq);
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
