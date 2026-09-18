package com.beichen.erp.purchase.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.service.ProductService;
import com.beichen.erp.purchase.entity.PurchaseExchange;
import com.beichen.erp.purchase.entity.PurchaseExchangeItem;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.entity.PurchaseOrderItem;
import com.beichen.erp.purchase.entity.PurchaseReturn;
import com.beichen.erp.purchase.entity.PurchaseReturnItem;
import com.beichen.erp.purchase.mapper.PurchaseExchangeItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseExchangeMapper;
import com.beichen.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseOrderMapper;
import com.beichen.erp.purchase.mapper.PurchaseReturnItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseReturnMapper;
import com.beichen.erp.purchase.service.PurchaseExchangeService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.warehouse.common.WarehouseType;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购换货单服务实现（进货业务，**同品换货**，强关联采购单；2026-09-18 新增）
 * <p>
 * 与销售换货单（{@code SaleExchangeServiceImpl}）**结构对称、方向相反**：
 * <ul>
 *   <li>退回侧：我方把（不良）成品**退回供货商** ⇒ 从我方仓**出库**扣减 + 生成**负向**应付（冲减）；</li>
 *   <li>换入侧：供货商把良品**换回我方** ⇒ 入我方仓**入库**增加 + 生成**正向**应付。</li>
 * </ul>
 * 两条台账净额即差价：等价换货净 0（账实相符：旧货已退、新货已收），加价换新则净额为正。
 * </p>
 * <p>
 * 可换量 = 已购（采购单明细数量） − 已退（已审 TH-） − 已换（已审 CH-），只约束**退回数量**，
 * 支持同一采购明细多次部分换货。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PurchaseExchangeServiceImpl implements PurchaseExchangeService {

    private final PurchaseExchangeMapper exchangeMapper;
    private final PurchaseExchangeItemMapper itemMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseReturnItemMapper purchaseReturnItemMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final SupplierMapper supplierMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockService stockService;
    private final PayableHelper payableHelper;
    private final UserMapper userMapper;

    // ==================== 查询 ====================

    @Override
    public IPage<Map<String, Object>> page(long current, long size, Map<String, Object> q) {
        String kw = q == null ? null : str(q.get("kw"));
        String status = q == null ? null : str(q.get("status"));
        Long supplierId = q == null ? null : toLong(q.get("supplierId"));
        Long purchaseOrderId = q == null ? null : toLong(q.get("purchaseOrderId"));
        LambdaQueryWrapper<PurchaseExchange> w = new LambdaQueryWrapper<PurchaseExchange>()
                .and(kw != null && !kw.isBlank(),
                        x -> x.like(PurchaseExchange::getCode, kw).or().like(PurchaseExchange::getPurchaseOrderCode, kw))
                .eq(status != null && !status.isBlank(), PurchaseExchange::getStatus, status)
                .eq(supplierId != null, PurchaseExchange::getSupplierId, supplierId)
                .eq(purchaseOrderId != null, PurchaseExchange::getPurchaseOrderId, purchaseOrderId)
                .orderByDesc(PurchaseExchange::getId);
        Page<PurchaseExchange> p = exchangeMapper.selectPage(new Page<>(current, size), w);
        // 换货概况（退回侧 → 换入侧）为非表字段，按本页单据批量查明细后拼接，避免逐条查库
        Map<Long, List<PurchaseExchangeItem>> itemsMap = new HashMap<>();
        if (!p.getRecords().isEmpty()) {
            List<Long> ids = p.getRecords().stream().map(PurchaseExchange::getId).collect(Collectors.toList());
            itemsMap = itemMapper.selectList(
                            new LambdaQueryWrapper<PurchaseExchangeItem>().in(PurchaseExchangeItem::getExchangeId, ids))
                    .stream().collect(Collectors.groupingBy(PurchaseExchangeItem::getExchangeId));
        }
        Map<Long, List<PurchaseExchangeItem>> finalItemsMap = itemsMap;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PurchaseExchange e : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("code", e.getCode());
            m.put("supplierId", e.getSupplierId());
            m.put("supplierName", supplierName(e.getSupplierId()));
            m.put("purchaseOrderId", e.getPurchaseOrderId());
            m.put("purchaseOrderCode", e.getPurchaseOrderCode());
            m.put("warehouseOutId", e.getWarehouseOutId());
            m.put("warehouseOutName", warehouseName(e.getWarehouseOutId()));
            m.put("warehouseInId", e.getWarehouseInId());
            m.put("warehouseInName", warehouseName(e.getWarehouseInId()));
            m.put("exchangeDate", e.getExchangeDate() != null ? e.getExchangeDate().toString() : "");
            m.put("status", e.getStatus());
            m.put("totalReturnAmount", e.getTotalReturnAmount());
            m.put("totalInAmount", e.getTotalInAmount());
            // 换货概况：产品名 退N(DEFECT) → 换M(A)，多条明细用「；」连接
            List<PurchaseExchangeItem> exItems = finalItemsMap.getOrDefault(e.getId(), Collections.emptyList());
            String summary = exItems.stream()
                    .map(it -> String.format("%s 退%s(%s) → 换%s(%s)",
                            it.getProductName() != null ? it.getProductName() : "",
                            qtyText(it.getQuantity()), orDash(it.getQualityType()),
                            qtyText(inQtyOf(it)), orDash(inQualityOf(it))))
                    .collect(Collectors.joining("；"));
            m.put("exchangeSummary", summary);
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
    public PurchaseExchange getById(Long id) {
        PurchaseExchange e = exchangeMapper.selectById(id);
        if (e == null) throw new BusinessException("换货单不存在");
        e.setSupplierName(supplierName(e.getSupplierId()));
        return e;
    }

    @Override
    public List<PurchaseExchangeItem> getItems(Long id) {
        List<PurchaseExchangeItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseExchangeItem>().eq(PurchaseExchangeItem::getExchangeId, id));
        productService.fillSku(items, PurchaseExchangeItem::getProductId, PurchaseExchangeItem::setSku);
        return items;
    }

    @Override
    public List<Map<String, Object>> purchaseOrderItems(Long purchaseOrderId) {
        if (purchaseOrderId == null) return List.of();
        List<PurchaseOrderItem> oiList = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, purchaseOrderId));
        if (oiList.isEmpty()) return List.of();
        Map<Long, Product> pMap = new HashMap<>();
        Set<Long> pids = oiList.stream().map(PurchaseOrderItem::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!pids.isEmpty()) productMapper.selectBatchIds(pids).forEach(p -> pMap.put(p.getId(), p));
        Set<Long> oiIds = oiList.stream().map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // 批量取已退/已换累计，避免逐条查库（N+1）
        Map<Long, BigDecimal> returnedMap = alreadyReturnedBatch(oiIds);
        Map<Long, BigDecimal> exchangedMap = alreadyExchangedBatch(oiIds);
        List<Map<String, Object>> res = new ArrayList<>();
        for (PurchaseOrderItem oi : oiList) {
            Product p = pMap.get(oi.getProductId());
            BigDecimal purchased = nz(oi.getQuantity());
            BigDecimal returned = returnedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            BigDecimal exchanged = exchangedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            BigDecimal canExchange = purchased.subtract(returned).subtract(exchanged);
            if (canExchange.compareTo(BigDecimal.ZERO) < 0) canExchange = BigDecimal.ZERO;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("purchaseOrderItemId", oi.getId());
            m.put("productId", oi.getProductId());
            m.put("productName", p != null ? p.getName() : "");
            m.put("unit", p != null ? p.getUnit() : "");
            m.put("qualityType", oi.getQualityType());
            m.put("quantity", purchased);
            m.put("unitPrice", oi.getUnitPrice());
            m.put("returnedQuantity", returned);
            m.put("exchangedQuantity", exchanged);
            m.put("canExchange", canExchange);
            res.add(m);
        }
        return res;
    }

    @Override
    public List<Map<String, Object>> purchaseOrders(Long supplierId, String kw) {
        List<PurchaseOrder> list = purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getStatus, DocStatus.AUDITED.getCode())
                .eq(supplierId != null, PurchaseOrder::getSupplierId, supplierId)
                .like(kw != null && !kw.isBlank(), PurchaseOrder::getCode, kw)
                .orderByDesc(PurchaseOrder::getId)
                .last("LIMIT 200"));
        List<Map<String, Object>> res = new ArrayList<>();
        for (PurchaseOrder po : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", po.getId());
            m.put("code", po.getCode());
            m.put("supplierId", po.getSupplierId());
            m.put("supplierName", supplierName(po.getSupplierId()));
            m.put("warehouseId", po.getWarehouseId());
            m.put("warehouseName", warehouseName(po.getWarehouseId()));
            m.put("orderDate", po.getOrderDate() != null ? po.getOrderDate().toString() : "");
            res.add(m);
        }
        return res;
    }

    // ==================== 保存 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseExchange create(PurchaseExchange exchange, List<Map<String, Object>> itemMaps) {
        exchange.setId(null);
        exchange.setStatus(DocStatus.DRAFT.getCode());
        exchange.setCode(gen());
        fillPurchaseOrderInfo(exchange);
        validate(exchange, itemMaps);
        if (exchange.getTotalReturnAmount() == null) exchange.setTotalReturnAmount(BigDecimal.ZERO);
        if (exchange.getTotalInAmount() == null) exchange.setTotalInAmount(BigDecimal.ZERO);
        exchangeMapper.insert(exchange);
        saveItems(exchange.getId(), itemMaps);
        recalcTotals(exchange.getId());
        return getById(exchange.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseExchange update(Long id, PurchaseExchange exchange, List<Map<String, Object>> itemMaps) {
        PurchaseExchange old = exchangeMapper.selectById(id);
        if (old == null) throw new BusinessException("换货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        exchange.setId(id);
        exchange.setCode(null);   // 单号不可修改
        exchange.setStatus(null); // 状态不可修改
        fillPurchaseOrderInfo(exchange);
        validate(exchange, itemMaps);
        if (exchange.getTotalReturnAmount() == null) exchange.setTotalReturnAmount(BigDecimal.ZERO);
        if (exchange.getTotalInAmount() == null) exchange.setTotalInAmount(BigDecimal.ZERO);
        exchangeMapper.updateById(exchange);
        itemMapper.delete(new LambdaQueryWrapper<PurchaseExchangeItem>().eq(PurchaseExchangeItem::getExchangeId, id));
        saveItems(id, itemMaps);
        recalcTotals(id);
        return getById(id);
    }

    /**
     * 保存明细：先删后插。
     * <p>明细拆「退回侧 + 换入侧」：同品换货，换入产品默认与退回产品相同；
     * 换入数量未指定时默认等于退回数量，换入单价默认取采购原价（改高即表示加价换新）。</p>
     */
    private void saveItems(Long exchangeId, List<Map<String, Object>> itemMaps) {
        if (itemMaps == null || itemMaps.isEmpty()) return;
        Long cid = CompanyContext.get();
        for (Map<String, Object> m : itemMaps) {
            PurchaseExchangeItem it = new PurchaseExchangeItem();
            it.setExchangeId(exchangeId);
            if (m.get("purchaseOrderItemId") != null && !m.get("purchaseOrderItemId").toString().isBlank())
                it.setPurchaseOrderItemId(Long.valueOf(m.get("purchaseOrderItemId").toString()));

            // ===== 退回侧 =====
            if (m.get("productId") == null || m.get("productId").toString().isBlank())
                throw new BusinessException("退回产品不能为空");
            it.setProductId(Long.valueOf(m.get("productId").toString()));
            Product p = productMapper.selectById(it.getProductId());
            it.setProductName(p != null ? p.getName()
                    : (m.get("productName") != null ? m.get("productName").toString() : ""));
            String qt = m.get("qualityType") != null && !m.get("qualityType").toString().isBlank()
                    ? m.get("qualityType").toString() : ProductQualityType.DEFECT.getCode();
            if (!ProductQualityType.isValid(qt)) throw new BusinessException("非法的退回品质等级：" + qt);
            it.setQualityType(qt);
            BigDecimal qty = toBig(m.get("quantity"));
            if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("退回数量必须大于 0");
            it.setQuantity(qty);
            it.setUnitPrice(toBig(m.get("unitPrice")));
            it.setAmount(qty.multiply(it.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));

            // ===== 换入侧（同品换货：换入产品默认=退回产品，数量可不等如退2换1，品质可不同）=====
            Long inPid = m.get("inProductId") != null && !m.get("inProductId").toString().isBlank()
                    ? Long.valueOf(m.get("inProductId").toString()) : it.getProductId();
            it.setInProductId(inPid);
            BigDecimal inQty = m.get("inQuantity") != null && !m.get("inQuantity").toString().isBlank()
                    ? toBig(m.get("inQuantity")) : qty;
            if (inQty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("换入数量必须大于 0");
            it.setInQuantity(inQty);
            String inQt = m.get("inQualityType") != null && !m.get("inQualityType").toString().isBlank()
                    ? m.get("inQualityType").toString() : ProductQualityType.A.getCode();
            if (!ProductQualityType.isValid(inQt)) throw new BusinessException("非法的换入品质等级：" + inQt);
            it.setInQualityType(inQt);
            it.setInUnitPrice(m.get("inUnitPrice") != null && !m.get("inUnitPrice").toString().isBlank()
                    ? toBig(m.get("inUnitPrice")) : it.getUnitPrice());
            it.setInAmount(inQty.multiply(it.getInUnitPrice()).setScale(2, RoundingMode.HALF_UP));

            if (m.get("remark") != null) it.setRemark(m.get("remark").toString());
            it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    /** 回填来源采购单信息（单号、供货商、默认仓） */
    private void fillPurchaseOrderInfo(PurchaseExchange e) {
        if (e.getPurchaseOrderId() == null) throw new BusinessException("换货单必须选择来源采购单");
        PurchaseOrder po = purchaseOrderMapper.selectById(e.getPurchaseOrderId());
        if (po == null) throw new BusinessException("来源采购单不存在");
        e.setPurchaseOrderCode(po.getCode());
        if (e.getSupplierId() == null) e.setSupplierId(po.getSupplierId());
        // 默认仓取采购单的入库仓（我方成品仓）；退回出库与换入入库都允许用户另选
        if (e.getWarehouseOutId() == null) e.setWarehouseOutId(po.getWarehouseId());
        if (e.getWarehouseInId() == null) e.setWarehouseInId(po.getWarehouseId());
    }

    /**
     * 按明细重算两侧金额：退回侧 amount = 数量 × 单价，换入侧 inAmount = 换入数量 × 换入单价，
     * 分别汇总到主表 totalReturnAmount / totalInAmount（审核生成两条应付台账时使用）。
     */
    private void recalcTotals(Long exchangeId) {
        List<PurchaseExchangeItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseExchangeItem>().eq(PurchaseExchangeItem::getExchangeId, exchangeId));
        BigDecimal totalReturn = BigDecimal.ZERO;
        BigDecimal totalIn = BigDecimal.ZERO;
        for (PurchaseExchangeItem it : items) {
            BigDecimal amount = nz(it.getQuantity()).multiply(nz(it.getUnitPrice())).setScale(2, RoundingMode.HALF_UP);
            if (it.getAmount() == null || it.getAmount().compareTo(amount) != 0) {
                PurchaseExchangeItem u = new PurchaseExchangeItem();
                u.setId(it.getId());
                u.setAmount(amount);
                itemMapper.updateById(u);
            }
            BigDecimal inAmount = inQtyOf(it).multiply(inPriceOf(it)).setScale(2, RoundingMode.HALF_UP);
            if (it.getInAmount() == null || it.getInAmount().compareTo(inAmount) != 0) {
                PurchaseExchangeItem u = new PurchaseExchangeItem();
                u.setId(it.getId());
                u.setInAmount(inAmount);
                itemMapper.updateById(u);
            }
            totalReturn = totalReturn.add(amount);
            totalIn = totalIn.add(inAmount);
        }
        PurchaseExchange u = new PurchaseExchange();
        u.setId(exchangeId);
        u.setTotalReturnAmount(totalReturn);
        u.setTotalInAmount(totalIn);
        exchangeMapper.updateById(u);
    }

    // ==================== 审核 / 反审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        PurchaseExchange e = exchangeMapper.selectById(id);
        if (e == null) throw new BusinessException("换货单不存在");
        // 原子抢占状态（同采购退货 P2-29）：并发/双击只有一个请求能抢到，避免双向库存/台账重复写
        if (!DocStatusGuard.claim(exchangeMapper, PurchaseExchange::getId, id, PurchaseExchange::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<PurchaseExchangeItem> items = getItems(id);
        if (items.isEmpty()) throw new BusinessException("换货单明细不能为空");
        // 仓型收敛：退回出库仓与换入入库仓**都必须是自有成品仓**（同仓允许，仓内按品质分行）
        assertWarehouseType(e.getWarehouseOutId(), "退回出库仓");
        assertWarehouseType(e.getWarehouseInId(), "换入入库仓");
        for (PurchaseExchangeItem it : items) {
            if (outQtyOf(it).compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("退回数量必须大于 0（明细行ID=" + it.getId() + "）");
            if (inQtyOf(it).compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("换入数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        // 可换量复核（口径 = 已购 − 已退 − 已换）
        checkCanExchange(items);
        // 退回出库前**一次性列清**库存缺口（changeStock 只会报"产品ID=xx"，用户看不出差多少）
        assertReturnStockEnough(e, items);
        // 1) 库存联动：退回出库扣减 + 换入入库增加
        for (PurchaseExchangeItem it : items) {
            stockService.changeStock(e.getWarehouseOutId(), it.getProductId(), outQtyOf(it).negate(),
                    StockChangeType.PURCHASE_EXCHANGE_OUT, e.getCode(), RelatedBillType.PURCHASE_EXCHANGE,
                    "", e.getId(), outQualityOf(it));
            stockService.changeStock(e.getWarehouseInId(), inProductOf(it), inQtyOf(it),
                    StockChangeType.PURCHASE_EXCHANGE_IN, e.getCode(), RelatedBillType.PURCHASE_EXCHANGE,
                    "", e.getId(), inQualityOf(it));
        }
        // 2) 重算两侧金额（兜底前端未传金额）
        recalcTotals(id);
        PurchaseExchange cur = exchangeMapper.selectById(id);
        BigDecimal totalReturn = nz(cur.getTotalReturnAmount());
        BigDecimal totalIn = nz(cur.getTotalInAmount());
        // 3) 财务联动：两条对称台账 —— 退回侧负向（冲减应付）、换入侧正向（新增应付）⇒ 净额即差价
        //    台账号固定为「单据号 + 后缀」，按 billNo 复用（反审核后重审不撞 finance_payable.uk_bill_no）
        savePayable(e, SourceBillType.PURCHASE_EXCHANGE_RETURN, e.getCode() + "-RET",
                totalReturn.negate(), "采购换货退回（冲减应付）：" + e.getCode());
        savePayable(e, SourceBillType.PURCHASE_EXCHANGE_IN, e.getCode() + "-IN",
                totalIn, "采购换货入库（新增应付）：" + e.getCode());
        // 4) 更新状态与审核人（状态已由 DocStatusGuard 抢占置为 AUDITED）
        PurchaseExchange u = new PurchaseExchange();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        exchangeMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        PurchaseExchange e = exchangeMapper.selectById(id);
        if (e == null) throw new BusinessException("换货单不存在");
        if (!DocStatusGuard.claim(exchangeMapper, PurchaseExchange::getId, id, PurchaseExchange::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核状态可反审核");
        // 1) 应付护栏：已核销（有付款/已结清）或已转应收的两条台账都不允许作废（reversePayable 内部抛错）
        payableHelper.reversePayable(id,
                SourceBillType.PURCHASE_EXCHANGE_RETURN.getCode(), SourceBillType.PURCHASE_EXCHANGE_IN.getCode());
        List<PurchaseExchangeItem> items = getItems(id);
        // 2) 换入的良品可能已被后续单据（销售/委外）消耗 ⇒ 先列清缺口，
        //    否则 changeStock 只会报"库存不足，无法出库：产品ID=xx"，用户无法定位
        assertInStockReversible(e, items);
        // 3) 库存对称回滚：恢复退回库存 + 扣回换入库存
        for (PurchaseExchangeItem it : items) {
            stockService.changeStock(e.getWarehouseOutId(), it.getProductId(), outQtyOf(it),
                    StockChangeType.PURCHASE_EXCHANGE_UN_AUDIT, e.getCode(), RelatedBillType.PURCHASE_EXCHANGE,
                    "", e.getId(), outQualityOf(it));
            stockService.changeStock(e.getWarehouseInId(), inProductOf(it), inQtyOf(it).negate(),
                    StockChangeType.PURCHASE_EXCHANGE_UN_AUDIT, e.getCode(), RelatedBillType.PURCHASE_EXCHANGE,
                    "", e.getId(), inQualityOf(it));
        }
        // 4) 回退到草稿；审核信息必须用 UpdateWrapper 显式置 null（updateById 忽略 null 字段）
        exchangeMapper.update(null, new LambdaUpdateWrapper<PurchaseExchange>()
                .eq(PurchaseExchange::getId, id)
                .set(PurchaseExchange::getStatus, DocStatus.DRAFT.getCode())
                .set(PurchaseExchange::getAuditorId, null)
                .set(PurchaseExchange::getAuditorName, null)
                .set(PurchaseExchange::getAuditTime, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PurchaseExchange old = exchangeMapper.selectById(id);
        if (old == null) throw new BusinessException("换货单不存在");
        if (!DocStatusGuard.claim(exchangeMapper, PurchaseExchange::getId, id, PurchaseExchange::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
        PurchaseExchange u = new PurchaseExchange();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        exchangeMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 换货单不做物理删除（避免留下孤儿明细/台账），语义等同作废：单据留痕
        cancel(id);
    }

    // ==================== 校验 ====================

    /** 主表校验：必关联采购单与供货商、仓库必填且为成品仓、明细非空、可换量不超 */
    private void validate(PurchaseExchange e, List<Map<String, Object>> itemMaps) {
        if (e.getPurchaseOrderId() == null) throw new BusinessException("换货单必须选择来源采购单");
        if (e.getSupplierId() == null) throw new BusinessException("请选择供货商（可由采购单带出）");
        if (e.getWarehouseOutId() == null) throw new BusinessException("退回出库仓不能为空");
        if (e.getWarehouseInId() == null) throw new BusinessException("换入入库仓不能为空");
        assertWarehouseType(e.getWarehouseOutId(), "退回出库仓");
        assertWarehouseType(e.getWarehouseInId(), "换入入库仓");
        if (itemMaps == null || itemMaps.isEmpty()) throw new BusinessException("换货明细不能为空");

        Map<Long, BigDecimal> qtyMap = new LinkedHashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        for (Map<String, Object> m : itemMaps) {
            Object pidObj = m.get("productId");
            if (pidObj == null || pidObj.toString().isBlank()) throw new BusinessException("退回产品不能为空");
            BigDecimal qty = toBig(m.get("quantity"));
            if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("退回数量必须大于 0");
            String qt = m.get("qualityType") != null && !m.get("qualityType").toString().isBlank()
                    ? m.get("qualityType").toString() : ProductQualityType.DEFECT.getCode();
            if (!ProductQualityType.isValid(qt)) throw new BusinessException("非法的退回品质等级：" + qt);
            BigDecimal inQty = m.get("inQuantity") != null && !m.get("inQuantity").toString().isBlank()
                    ? toBig(m.get("inQuantity")) : qty;
            if (inQty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("换入数量必须大于 0");
            String inQt = m.get("inQualityType") != null && !m.get("inQualityType").toString().isBlank()
                    ? m.get("inQualityType").toString() : ProductQualityType.A.getCode();
            if (!ProductQualityType.isValid(inQt)) throw new BusinessException("非法的换入品质等级：" + inQt);

            Object poiObj = m.get("purchaseOrderItemId");
            // 换货单**强关联采购单**：每行必须带采购单明细锚点，否则可换量校验会被静默跳过（超量换货漏网）
            if (poiObj == null || poiObj.toString().isBlank())
                throw new BusinessException("明细缺少关联采购单明细（purchaseOrderItemId），无法校验可换数量");
            Long poiId = Long.valueOf(poiObj.toString());
            qtyMap.merge(poiId, qty, BigDecimal::add);
            if (m.get("productName") != null) nameMap.put(poiId, m.get("productName").toString());
        }
        checkCanExchangeMap(qtyMap, nameMap);
    }

    /** 审核时复核可换量（用已落库的明细） */
    private void checkCanExchange(List<PurchaseExchangeItem> items) {
        Map<Long, BigDecimal> qtyMap = new LinkedHashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        for (PurchaseExchangeItem it : items) {
            if (it.getPurchaseOrderItemId() == null) continue;
            qtyMap.merge(it.getPurchaseOrderItemId(), outQtyOf(it), BigDecimal::add);
            if (it.getProductName() != null && !it.getProductName().isBlank())
                nameMap.put(it.getPurchaseOrderItemId(), it.getProductName());
        }
        checkCanExchangeMap(qtyMap, nameMap);
    }

    /** 可换量 = 已购 − 已退(TH-) − 已换(CH-)（只约束退回数量，换入属正常入库不受限） */
    private void checkCanExchangeMap(Map<Long, BigDecimal> qtyMap, Map<Long, String> nameMap) {
        if (qtyMap.isEmpty()) return;
        List<PurchaseOrderItem> oiList = purchaseOrderItemMapper.selectBatchIds(qtyMap.keySet());
        Map<Long, BigDecimal> returnedMap = alreadyReturnedBatch(qtyMap.keySet());
        Map<Long, BigDecimal> exchangedMap = alreadyExchangedBatch(qtyMap.keySet());
        for (PurchaseOrderItem oi : oiList) {
            // 提示语要能定位到具体产品：调用方可能没传 productName，这里按产品ID兜底查名
            if (!nameMap.containsKey(oi.getId()) && oi.getProductId() != null) {
                Product p = productMapper.selectById(oi.getProductId());
                if (p != null) nameMap.put(oi.getId(), p.getName());
            }
            BigDecimal purchased = nz(oi.getQuantity());
            BigDecimal returned = returnedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            BigDecimal exchanged = exchangedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            BigDecimal canExchange = purchased.subtract(returned).subtract(exchanged);
            BigDecimal thisQty = qtyMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            if (thisQty.compareTo(canExchange) > 0) {
                String name = nameMap.getOrDefault(oi.getId(), String.valueOf(oi.getProductId()));
                throw new BusinessException("产品[" + name + "]退回数量超过可换数量（已购" + fmt(purchased)
                        + "，已退" + fmt(returned) + "，已换" + fmt(exchanged) + "，可换" + fmt(canExchange) + "）");
            }
        }
    }

    /** 批量取已退量（按采购单明细ID聚合）：已审核采购退货单中该采购明细的累计数量 */
    private Map<Long, BigDecimal> alreadyReturnedBatch(Collection<Long> purchaseOrderItemIds) {
        Map<Long, BigDecimal> res = new HashMap<>();
        if (purchaseOrderItemIds == null || purchaseOrderItemIds.isEmpty()) return res;
        List<PurchaseReturn> audited = purchaseReturnMapper.selectList(new LambdaQueryWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getStatus, DocStatus.AUDITED.getCode()));
        if (audited.isEmpty()) return res;
        List<Long> returnIds = audited.stream().map(PurchaseReturn::getId).collect(Collectors.toList());
        List<PurchaseReturnItem> items = purchaseReturnItemMapper.selectList(new LambdaQueryWrapper<PurchaseReturnItem>()
                .in(PurchaseReturnItem::getReturnId, returnIds)
                .in(PurchaseReturnItem::getPurchaseOrderItemId, purchaseOrderItemIds));
        for (PurchaseReturnItem it : items) {
            if (it.getPurchaseOrderItemId() == null) continue;
            res.merge(it.getPurchaseOrderItemId(), nz(it.getQuantity()), BigDecimal::add);
        }
        return res;
    }

    /** 批量取已换退回量（按采购单明细ID聚合）：已审核采购换货单中该采购明细的累计退回数量 */
    private Map<Long, BigDecimal> alreadyExchangedBatch(Collection<Long> purchaseOrderItemIds) {
        Map<Long, BigDecimal> res = new HashMap<>();
        if (purchaseOrderItemIds == null || purchaseOrderItemIds.isEmpty()) return res;
        List<PurchaseExchange> audited = exchangeMapper.selectList(new LambdaQueryWrapper<PurchaseExchange>()
                .eq(PurchaseExchange::getStatus, DocStatus.AUDITED.getCode()));
        if (audited.isEmpty()) return res;
        List<Long> exIds = audited.stream().map(PurchaseExchange::getId).collect(Collectors.toList());
        List<PurchaseExchangeItem> items = itemMapper.selectList(new LambdaQueryWrapper<PurchaseExchangeItem>()
                .in(PurchaseExchangeItem::getExchangeId, exIds)
                .in(PurchaseExchangeItem::getPurchaseOrderItemId, purchaseOrderItemIds));
        for (PurchaseExchangeItem it : items) {
            if (it.getPurchaseOrderItemId() == null) continue;
            res.merge(it.getPurchaseOrderItemId(), outQtyOf(it), BigDecimal::add);
        }
        return res;
    }

    /** 退回出库前库存校验：一次列清所有缺口（产品/品质/需量/可用/缺口） */
    private void assertReturnStockEnough(PurchaseExchange e, List<PurchaseExchangeItem> items) {
        List<String> shortage = new ArrayList<>();
        for (PurchaseExchangeItem it : items) {
            BigDecimal need = outQtyOf(it);
            if (need.compareTo(BigDecimal.ZERO) <= 0) continue;
            String qt = outQualityOf(it);
            BigDecimal avail = stockService.getQuantity(e.getWarehouseOutId(), it.getProductId(), qt);
            if (avail.compareTo(need) < 0) {
                shortage.add(desc(it) + "（" + qt + "）需 " + fmt(need) + "，可用 " + fmt(avail)
                        + "，缺 " + fmt(need.subtract(avail)));
            }
        }
        if (!shortage.isEmpty())
            throw new BusinessException("退回出库库存不足，无法审核：" + String.join("；", shortage)
                    + (shortage.size() > 5 ? " 等 " + shortage.size() + " 项" : ""));
    }

    /** 反审核前校验：换入的良品是否已被后续单据消耗（扣回后不得为负） */
    private void assertInStockReversible(PurchaseExchange e, List<PurchaseExchangeItem> items) {
        List<String> shortage = new ArrayList<>();
        for (PurchaseExchangeItem it : items) {
            BigDecimal need = inQtyOf(it);
            if (need.compareTo(BigDecimal.ZERO) <= 0) continue;
            String qt = inQualityOf(it);
            BigDecimal avail = stockService.getQuantity(e.getWarehouseInId(), inProductOf(it), qt);
            if (avail.compareTo(need) < 0) {
                shortage.add(desc(it) + "（" + qt + "）需扣回 " + fmt(need) + "，当前可用 " + fmt(avail)
                        + "，缺 " + fmt(need.subtract(avail)));
            }
        }
        if (!shortage.isEmpty())
            throw new BusinessException("换入的库存已被后续单据消耗，无法反审核：" + String.join("；", shortage)
                    + "。请先反审核占用该库存的单据（如销售单/委外单）");
    }

    /** 应付台账入库（按 billNo 复用：反审核后重审不撞 uk_bill_no） */
    private void savePayable(PurchaseExchange e, SourceBillType type, String billNo,
                             BigDecimal amount, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return; // 0 元不建台账，避免空行
        FinancePayable fp = new FinancePayable();
        fp.setBillNo(billNo);
        fp.setSupplierId(e.getSupplierId());
        fp.setSupplierName(supplierName(e.getSupplierId()));
        fp.setSourceBillType(type.getCode());
        fp.setSourceBillNo(e.getCode());
        fp.setSourceId(e.getId());
        fp.setAmount(amount);
        fp.setPaidAmount(BigDecimal.ZERO);
        fp.setUnpaidAmount(amount);
        fp.setDueDate(e.getExchangeDate());
        fp.setStatus(SettlementStatus.UNSETTLED.getCode());
        fp.setRemark(remark);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fp.setCompanyId(cid);
        payableHelper.saveByBillNo(fp);
    }

    // ==================== 明细字段取值（退回侧 / 换入侧，空值兜底默认） ====================

    /** 退回数量 */
    private BigDecimal outQtyOf(PurchaseExchangeItem it) {
        return nz(it.getQuantity());
    }

    /** 退回品质：未指定默认不良品 DEFECT（换货多因质量问题） */
    private String outQualityOf(PurchaseExchangeItem it) {
        String qt = it.getQualityType() != null && !it.getQualityType().isBlank()
                ? it.getQualityType() : ProductQualityType.DEFECT.getCode();
        if (!ProductQualityType.isValid(qt)) throw new BusinessException("非法的退回品质等级：" + qt);
        return qt;
    }

    /** 换入数量：未指定时回退为退回数量（默认 1:1，可手工改成退 2 换 1） */
    private BigDecimal inQtyOf(PurchaseExchangeItem it) {
        return it.getInQuantity() != null ? it.getInQuantity() : outQtyOf(it);
    }

    /** 换入产品：同品换货为空时取退回产品（字段为"换不同型号"预留） */
    private Long inProductOf(PurchaseExchangeItem it) {
        return it.getInProductId() != null ? it.getInProductId() : it.getProductId();
    }

    /** 换入品质：未指定默认 A 规 */
    private String inQualityOf(PurchaseExchangeItem it) {
        String qt = it.getInQualityType() != null && !it.getInQualityType().isBlank()
                ? it.getInQualityType() : ProductQualityType.A.getCode();
        if (!ProductQualityType.isValid(qt)) throw new BusinessException("非法的换入品质等级：" + qt);
        return qt;
    }

    /** 换入单价：未指定时默认取退回单价（改高即表示加价换新） */
    private BigDecimal inPriceOf(PurchaseExchangeItem it) {
        return it.getInUnitPrice() != null ? it.getInUnitPrice() : nz(it.getUnitPrice());
    }

    // ==================== 工具 ====================

    private void assertWarehouseType(Long warehouseId, String label) {
        Warehouse wh = warehouseId == null ? null : warehouseMapper.selectById(warehouseId);
        if (wh == null) throw new BusinessException(label + "不存在");
        if (!WarehouseType.FINISHED.getCode().equals(wh.getWarehouseType()))
            throw new BusinessException(label + "必须是成品仓（" + WarehouseType.FINISHED.getCode()
                    + "），当前仓库类型为：" + wh.getWarehouseType());
    }

    /** 单号：前缀 + yyyyMMdd + 三位序号 */
    private String gen() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.PURCHASE_EXCHANGE + d;
        LambdaQueryWrapper<PurchaseExchange> w = new LambdaQueryWrapper<PurchaseExchange>()
                .likeRight(PurchaseExchange::getCode, pat).orderByDesc(PurchaseExchange::getCode).last("LIMIT 1");
        PurchaseExchange last = exchangeMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; }
            catch (Exception ex) { seq = 1; }
        }
        return pat + String.format("%03d", seq);
    }

    private String supplierName(Long supplierId) {
        if (supplierId == null) return "";
        Supplier s = supplierMapper.selectById(supplierId);
        return s != null && s.getName() != null ? s.getName() : "";
    }

    private String warehouseName(Long id) {
        if (id == null) return "";
        Warehouse w = warehouseMapper.selectById(id);
        return w != null && w.getWarehouseName() != null ? w.getWarehouseName() : "";
    }

    /** 明细行的产品描述（提示语用产品名，缺失则用ID） */
    private String desc(PurchaseExchangeItem it) {
        String name = it.getProductName();
        if (name == null || name.isBlank()) {
            if (it.getProductId() != null) {
                Product p = productMapper.selectById(it.getProductId());
                name = p != null ? p.getName() : String.valueOf(it.getProductId());
            } else {
                name = "—";
            }
        }
        return "产品[" + name + "]";
    }

    private String qtyText(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private String orDash(String v) {
        return v == null || v.isBlank() ? "—" : v;
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

    private Long getCurrentUserId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    private String getCurrentUserName() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userMapper.selectById(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception e) { return null; }
    }
}
