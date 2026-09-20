package com.beichen.erp.purchase.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.common.WarehouseType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.entity.PurchaseOrderItem;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.purchase.mapper.PurchaseOrderMapper;
import com.beichen.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.beichen.erp.purchase.service.PurchaseOrderService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final FinancePayableMapper payableMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final com.beichen.erp.finance.service.PayableHelper payableHelper;
    private final WarehouseStockService stockService;
    private final com.beichen.erp.warehouse.service.CostService costService;
    private final WarehouseMapper warehouseMapper;

    @Override
    public Page<Map<String, Object>> page(Integer status, Long supplierId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<PurchaseOrder> w = new LambdaQueryWrapper<PurchaseOrder>()
                .eq(status != null, PurchaseOrder::getStatus, status)
                .eq(supplierId != null, PurchaseOrder::getSupplierId, supplierId)
                .like(code != null && !code.isBlank(), PurchaseOrder::getCode, code)
                .orderByDesc(PurchaseOrder::getId);
        Page<PurchaseOrder> raw = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量查询所有订单的明细
        List<Long> orderIds = raw.getRecords().stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        Map<Long, List<PurchaseOrderItem>> itemsMap = Collections.emptyMap();
        if (!orderIds.isEmpty()) {
            List<PurchaseOrderItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<PurchaseOrderItem>().in(PurchaseOrderItem::getOrderId, orderIds));
            itemsMap = allItems.stream().collect(Collectors.groupingBy(PurchaseOrderItem::getOrderId));
        }
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        Map<Long, List<PurchaseOrderItem>> finalItemsMap = itemsMap;
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("supplierId", o.getSupplierId());
            m.put("warehouseId", o.getWarehouseId());
            m.put("orderDate", o.getOrderDate());
            m.put("status", o.getStatus());
            m.put("taxIncluded", o.getTaxIncluded());
            m.put("taxRate", o.getTaxRate());
            m.put("taxAmount", o.getTaxAmount());
            m.put("totalAmount", o.getTotalAmount());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            if (o.getSupplierId() != null) {
                Supplier s = supplierMapper.selectById(o.getSupplierId());
                m.put("supplierName", s != null ? s.getName() : "");
            }
            // 物品明细摘要：成品A*100，成品B*100
            List<PurchaseOrderItem> orderItems = finalItemsMap.getOrDefault(o.getId(), Collections.emptyList());
            String itemsSummary = orderItems.stream()
                    .map(it -> {
                        String name = "";
                        if (it.getProductId() != null) {
                            Product prod = productMapper.selectById(it.getProductId());
                            if (prod != null) name = prod.getName();
                        }
                        return name + "*" + (it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "0");
                    })
                    .collect(Collectors.joining("，"));
            m.put("itemsSummary", itemsSummary);
            return m;
        }).toList());
        return res;
    }

    @Override
    public PurchaseOrder getById(Long id) {
        return orderMapper.selectById(id);
    }

    @Override
    public List<PurchaseOrderItem> getItems(Long orderId) {
        List<PurchaseOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, orderId));
        // 批量查询产品名称，补到 productName 展示字段
        if (!items.isEmpty()) {
            Set<Long> productIds = items.stream()
                    .map(PurchaseOrderItem::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!productIds.isEmpty()) {
                List<Product> products = productMapper.selectBatchIds(productIds);
                Map<Long, String> nameMap = products.stream()
                        .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
                Map<Long, String> skuMap = products.stream()
                        .collect(Collectors.toMap(Product::getId, p -> p.getSku() != null ? p.getSku() : "", (a, b) -> a));
                items.forEach(it -> {
                    if (it.getProductId() != null) {
                        it.setProductName(nameMap.getOrDefault(it.getProductId(), ""));
                        it.setSku(skuMap.getOrDefault(it.getProductId(), ""));
                    }
                });
            }
        }
        return items;
    }

    /**
     * 2026-09-20（F7-149）：采购单的入库仓必须是**自有成品仓**（INVENTORY + FINISHED）。
     * <p>前端下拉已按此口径收窄，但接口可被直接调用 ⇒ 服务端补一次校验，
     * 避免成品被采进**委外仓**（会污染委外物料账）或**辅料仓**（物料与成品混账）。</p>
     */
    private void assertFinishedWarehouse(Long warehouseId) {
        if (warehouseId == null) throw new BusinessException("采购入库仓不能为空");
        Warehouse w = warehouseMapper.selectById(warehouseId);
        if (w == null) throw new BusinessException("采购入库仓不存在");
        boolean ok = WarehouseCategory.INVENTORY.getCode().equals(w.getWarehouseCategory())
                && WarehouseType.FINISHED.getCode().equals(w.getWarehouseType());
        if (!ok) {
            throw new BusinessException("采购入库仓只能是自有成品仓（当前选择：" + w.getWarehouseName() + "）");
        }
    }

    /** 税额拆分（单价含税口径）：打开含税时从含税总额中按税率拆出税额 = total × rate/(100+rate) */
    private BigDecimal calcTaxAmount(BigDecimal total, Integer taxIncluded, BigDecimal taxRate) {
        if (!Integer.valueOf(1).equals(taxIncluded) || taxRate == null || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = taxRate.divide(new BigDecimal("100"), 6, BigDecimal.ROUND_HALF_UP);
        return total.multiply(rate).divide(BigDecimal.ONE.add(rate), 2, BigDecimal.ROUND_HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(PurchaseOrder order, List<PurchaseOrderItem> items) {
        if (order.getSupplierId() == null) throw new BusinessException("供应商不能为空");
        assertFinishedWarehouse(order.getWarehouseId()); // F7-149：入库仓必须是自有成品仓
        order.setCode(generateCode());
        order.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        BigDecimal total = BigDecimal.ZERO;
        orderMapper.insert(order);
        for (PurchaseOrderItem it : items) {
            it.setId(null);
            it.setOrderId(order.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        PurchaseOrder u = new PurchaseOrder();
        u.setId(order.getId());
        u.setTotalAmount(total);
        u.setTaxAmount(calcTaxAmount(total, order.getTaxIncluded(), order.getTaxRate()));
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(PurchaseOrder order, List<PurchaseOrderItem> items) {
        PurchaseOrder old = orderMapper.selectById(order.getId());
        if (old == null) throw new BusinessException("采购单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        assertFinishedWarehouse(order.getWarehouseId()); // F7-149：入库仓必须是自有成品仓
        order.setCode(old.getCode());
        orderMapper.updateById(order);
        itemMapper.delete(new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, order.getId()));
        BigDecimal total = BigDecimal.ZERO;
        Long cid = CompanyContext.get();
        for (PurchaseOrderItem it : items) {
            it.setId(null);
            it.setOrderId(order.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        PurchaseOrder u = new PurchaseOrder();
        u.setId(order.getId());
        u.setTotalAmount(total);
        u.setTaxAmount(calcTaxAmount(total, order.getTaxIncluded(), order.getTaxRate()));
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PurchaseOrder old = orderMapper.selectById(id);
        if (old == null) throw new BusinessException("采购单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(orderMapper, PurchaseOrder::getId, id, PurchaseOrder::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
        PurchaseOrder u = new PurchaseOrder();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("采购单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存/应付/成本重复写
        if (!DocStatusGuard.claim(orderMapper, PurchaseOrder::getId, id, PurchaseOrder::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<PurchaseOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, id));
        if (items.isEmpty()) throw new BusinessException("采购单明细不能为空");
        // P2-33：数量必须为正（负数会被当作"出库"扣库存、0 会生成空台账）；引用主数据必须存在
        for (PurchaseOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("采购数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        if (order.getSupplierId() == null || supplierMapper.selectById(order.getSupplierId()) == null)
            throw new BusinessException("供应商不存在：ID=" + order.getSupplierId());
        // 1) 生成应付台账（D1 口径 2026-09-12：台账号一律 YF- 流水号，来源单号仍写 source_bill_no）
        FinancePayable fp = new FinancePayable();
        fp.setBillNo(payableHelper.newBillNo());
        fp.setSupplierId(order.getSupplierId());
        Supplier s = order.getSupplierId() != null ? supplierMapper.selectById(order.getSupplierId()) : null;
        fp.setSupplierName(s != null ? s.getName() : "");
        fp.setSourceBillType(SourceBillType.PURCHASE_ORDER.getCode());
        fp.setSourceBillNo(order.getCode());
        fp.setSourceId(order.getId());
        fp.setAmount(order.getTotalAmount());
        fp.setPaidAmount(BigDecimal.ZERO);
        fp.setUnpaidAmount(order.getTotalAmount());
        fp.setDueDate(order.getOrderDate() != null ? order.getOrderDate().plusMonths(1) : null);
        fp.setStatus(SettlementStatus.UNSETTLED.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fp.setCompanyId(cid);
        // 按 bill_no 保存：反审核后该单号台账已存在（仅置 CANCELLED 留痕），必须复用重置，
        // 否则再次审核会撞 finance_payable.uk_bill_no（2026-09-10 审核发现，P1-01）
        payableHelper.saveByBillNo(fp);
        // 2) 审核直接入库，按品质等级分别增加库存
        for (PurchaseOrderItem it : items) {
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getId() : it.getProductId(),
                    it.getQuantity(),
                    StockChangeType.PURCHASE_IN, order.getCode(), RelatedBillType.PURCHASE_ORDER,
                    "",
                    order.getId(), it.getQualityType());
            // 3) 移动加权成本：按明细单价入库加权
            costService.applyProduct(it.getProductId(), it.getQuantity(), it.getUnitPrice(),
                    StockChangeType.PURCHASE_IN.getCode(), order.getId(), order.getCode());
        }
        // 3) 更新订单状态为"已完成"，记录审核人
        PurchaseOrder u = new PurchaseOrder();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("采购单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存/成本重复冲销
        if (!DocStatusGuard.claim(orderMapper, PurchaseOrder::getId, id, PurchaseOrder::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已完成状态可反审核");

        // 1) 检查应付台账状态
        LambdaQueryWrapper<FinancePayable> payableW = new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getSourceBillType, SourceBillType.PURCHASE_ORDER.getCode())
                .eq(FinancePayable::getSourceBillNo, order.getCode());
        List<FinancePayable> payables = payableMapper.selectList(payableW);
        for (FinancePayable fp : payables) {
            // 只拦「真正核销过」的（已结清/部分结清/有付款额）；CANCELLED 是反审核自身的冲销留痕，不算核销
            if (SettlementStatus.isSettled(fp.getStatus(), fp.getPaidAmount())) {
                throw new BusinessException("该采购单对应的应付账款已核销，无法反审核。请先处理应付账款。");
            }
        }

        // 2) 冲回库存前校验：该批库存是否已被后续单据（销售出库/移仓/重分类等）消耗
        List<PurchaseOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, id));
        for (PurchaseOrderItem it : items) {
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            BigDecimal currentQty = stockService.getQuantity(order.getWarehouseId(),
                    product != null ? product.getId() : it.getProductId(), it.getQualityType());
            if (currentQty.compareTo(it.getQuantity()) < 0) {
                throw new BusinessException("该采购单对应库存已被后续单据消耗，无法反审核。当前库存 "
                        + currentQty + " 小于入库数量 " + it.getQuantity());
            }
        }
        // 3) 冲回库存：审核时加了库存，反审核需按品质等级逐条冲回
        for (PurchaseOrderItem it : items) {
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getId() : it.getProductId(),
                    it.getQuantity().negate(), // 负数冲回
                    // 反审核流水用专用 code（清单 C1）：原先复用 PURCHASE_IN，只能靠符号判断正反
                    StockChangeType.PURCHASE_UN_AUDIT, order.getCode(), RelatedBillType.PURCHASE_ORDER,
                    "",
                    order.getId(), it.getQualityType());
        }

        // 4) 冲销应付台账：置「已作废」并保留审计，不再物理删除（与委外交货反审核一致），
        //    避免账务无留痕、以及反审核后再审核时 bill_no 重复生成
        //    I29 口径（2026-09-18）：作废行**金额一并清零**（原金额记入备注留痕），
        //    使「amount = paid + unpaid」在所有行上恒成立，行级对账不再误报作废行
        for (FinancePayable fp : payables) {
            payableHelper.cancelLedger(fp);
        }

        // 5) 成本冲销：删除本单入库批次并反加权
        costService.reverseByBill(StockChangeType.PURCHASE_IN.getCode(), id);

        // 6) 回退状态到草稿，清除审核信息
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 忽略 null 字段，反审核后仍显示审核人/时间
        orderMapper.update(null, new LambdaUpdateWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, id)
                .set(PurchaseOrder::getStatus, DocStatus.DRAFT.getCode())
                .set(PurchaseOrder::getAuditorId, null)
                .set(PurchaseOrder::getAuditorName, null)
                .set(PurchaseOrder::getAuditTime, null));
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.PURCHASE + d;
        LambdaQueryWrapper<PurchaseOrder> w = new LambdaQueryWrapper<PurchaseOrder>()
                .likeRight(PurchaseOrder::getCode, pat).orderByDesc(PurchaseOrder::getCode).last("LIMIT 1");
        PurchaseOrder last = orderMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.PURCHASE + d + String.format("%03d", seq);
    }

    private Long getCurrentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrentUserName() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userMapper.selectById(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
