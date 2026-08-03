package com.beichen.erp.purchase.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.entity.PurchaseOrderItem;
import com.beichen.erp.purchase.entity.PurchaseOrderStatus;
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
    private final InventoryWarehouseStockService stockService;
    private final FinancePayableMapper payableMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;

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
        return itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, orderId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(PurchaseOrder order, List<PurchaseOrderItem> items) {
        if (order.getSupplierId() == null) throw new BusinessException("供应商不能为空");
        if (order.getSupplierId() != null) {
            Supplier s = supplierMapper.selectById(order.getSupplierId());
            order.setSupplierName(s != null ? s.getName() : "");
        }
        order.setCode(generateCode());
        order.setStatus(PurchaseOrderStatus.DRAFT.getCode());
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
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(PurchaseOrder order, List<PurchaseOrderItem> items) {
        PurchaseOrder old = orderMapper.selectById(order.getId());
        if (old == null) throw new BusinessException("采购单不存在");
        if (!PurchaseOrderStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        if (order.getSupplierId() != null) {
            Supplier s = supplierMapper.selectById(order.getSupplierId());
            order.setSupplierName(s != null ? s.getName() : "");
        }
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
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PurchaseOrder old = orderMapper.selectById(id);
        if (old == null) throw new BusinessException("采购单不存在");
        if (!PurchaseOrderStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        PurchaseOrder u = new PurchaseOrder();
        u.setId(id);
        u.setStatus(PurchaseOrderStatus.CANCELLED.getCode());
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("采购单不存在");
        if (!PurchaseOrderStatus.DRAFT.getCode().equals(order.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<PurchaseOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, id));
        if (items.isEmpty()) throw new BusinessException("采购单明细不能为空");
        // 1) 库存联动：入库加库存 + 写流水
        for (PurchaseOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity(),
                    StockChangeType.PURCHASE_IN, order.getCode(), RelatedBillType.PURCHASE_ORDER, it.getProductId(),
                    product != null ? product.getSpec() : "", order.getId(), it.getQualityType());
        }
        // 2) 生成应付台账
        FinancePayable fp = new FinancePayable();
        fp.setBillNo(order.getCode());
        fp.setSupplierId(order.getSupplierId());
        Supplier s = order.getSupplierId() != null ? supplierMapper.selectById(order.getSupplierId()) : null;
        fp.setSupplierName(s != null ? s.getName() : "");
        fp.setSourceBillType(SourceBillType.PURCHASE_ORDER.getCode());
        fp.setSourceBillNo(order.getCode());
        fp.setAmount(order.getTotalAmount());
        fp.setPaidAmount(BigDecimal.ZERO);
        fp.setUnpaidAmount(order.getTotalAmount());
        fp.setDueDate(order.getOrderDate() != null ? order.getOrderDate().plusMonths(1) : null);
        fp.setStatus(SettlementStatus.UNSETTLED.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fp.setCompanyId(cid);
        payableMapper.insert(fp);
        // 3) 更新订单状态为"已完成"，记录审核人
        PurchaseOrder u = new PurchaseOrder();
        u.setId(id);
        u.setStatus(PurchaseOrderStatus.COMPLETED.getCode());
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
        if (!PurchaseOrderStatus.COMPLETED.getCode().equals(order.getStatus())) throw new BusinessException("只有已完成状态可反审核");

        // 1) 检查应付台账状态
        LambdaQueryWrapper<FinancePayable> payableW = new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getSourceBillType, "采购单")
                .eq(FinancePayable::getSourceBillNo, order.getCode());
        List<FinancePayable> payables = payableMapper.selectList(payableW);
        for (FinancePayable fp : payables) {
            if (!SettlementStatus.UNSETTLED.getCode().equals(fp.getStatus())) {
                throw new BusinessException("该采购单对应的应付账款已核销，无法反审核。请先处理应付账款。");
            }
        }

        // 2) 检查库存：需要冲回的数量是否超出现有库存
        List<PurchaseOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, id));
        for (PurchaseOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            // 冲回库存（扣减库存），changeStock 内部会校验是否够扣
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity().negate(),
                    StockChangeType.PURCHASE_UN_AUDIT, order.getCode(), RelatedBillType.PURCHASE_ORDER, it.getProductId(),
                    product != null ? product.getSpec() : "", order.getId(), it.getQualityType());
        }

        // 3) 删除应付台账
        for (FinancePayable fp : payables) {
            payableMapper.deleteById(fp.getId());
        }

        // 4) 回退状态到草稿，清除审核信息
        PurchaseOrder u = new PurchaseOrder();
        u.setId(id);
        u.setStatus(PurchaseOrderStatus.DRAFT.getCode());
        u.setAuditorId(null);
        u.setAuditorName(null);
        u.setAuditTime(null);
        orderMapper.updateById(u);
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "CG-" + d;
        LambdaQueryWrapper<PurchaseOrder> w = new LambdaQueryWrapper<PurchaseOrder>()
                .likeRight(PurchaseOrder::getCode, pat).orderByDesc(PurchaseOrder::getCode).last("LIMIT 1");
        PurchaseOrder last = orderMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return "CG-" + d + String.format("%03d", seq);
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
