package com.beichen.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.entity.PurchaseOrderItem;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final SupplierMapper supplierMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long supplierId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<PurchaseOrder> w = new LambdaQueryWrapper<PurchaseOrder>()
                .eq(status != null && !status.isBlank(), PurchaseOrder::getStatus, status)
                .eq(supplierId != null, PurchaseOrder::getSupplierId, supplierId)
                .like(code != null && !code.isBlank(), PurchaseOrder::getCode, code)
                .orderByDesc(PurchaseOrder::getId);
        Page<PurchaseOrder> raw = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
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
        order.setStatus("草稿");
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
        if (old == null) throw new BusinessException("采购订单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
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
        if (old == null) throw new BusinessException("采购订单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        PurchaseOrder u = new PurchaseOrder();
        u.setId(id);
        u.setStatus("已作废");
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        PurchaseOrder old = orderMapper.selectById(id);
        if (old == null) throw new BusinessException("采购订单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可审核");
        PurchaseOrder u = new PurchaseOrder();
        u.setId(id);
        u.setStatus("已审核");
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
}
