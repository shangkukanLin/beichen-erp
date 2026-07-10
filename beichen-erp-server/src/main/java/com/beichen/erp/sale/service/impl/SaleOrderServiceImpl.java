package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import com.beichen.erp.inventory.mapper.InventoryWarehouseStockMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOrderItem;
import com.beichen.erp.sale.mapper.SaleOrderMapper;
import com.beichen.erp.sale.mapper.SaleOrderItemMapper;
import com.beichen.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements SaleOrderService {

    private final SaleOrderMapper orderMapper;
    private final SaleOrderItemMapper itemMapper;
    private final CustomerMapper customerMapper;
    private final InventoryWarehouseStockService stockService;
    private final FinanceReceivableMapper receivableMapper;
    private final InventoryWarehouseStockMapper stockMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long customerId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<SaleOrder> w = new LambdaQueryWrapper<SaleOrder>()
                .eq(status != null && !status.isBlank(), SaleOrder::getStatus, status)
                .eq(customerId != null, SaleOrder::getCustomerId, customerId)
                .like(code != null && !code.isBlank(), SaleOrder::getCode, code)
                .orderByDesc(SaleOrder::getId);
        Page<SaleOrder> raw = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("customerId", o.getCustomerId());
            m.put("warehouseId", o.getWarehouseId());
            m.put("orderDate", o.getOrderDate());
            m.put("status", o.getStatus());
            m.put("taxIncluded", o.getTaxIncluded());
            m.put("taxRate", o.getTaxRate());
            m.put("totalAmount", o.getTotalAmount());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            if (o.getCustomerId() != null) {
                Customer c = customerMapper.selectById(o.getCustomerId());
                m.put("customerName", c != null ? c.getName() : "");
            }
            return m;
        }).toList());
        return res;
    }

    @Override
    public SaleOrder getById(Long id) { return orderMapper.selectById(id); }

    @Override
    public List<SaleOrderItem> getItems(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, orderId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaleOrder order, List<SaleOrderItem> items) {
        if (order.getCustomerId() == null) throw new BusinessException("客户不能为空");
        if (order.getCustomerId() != null) {
            Customer c = customerMapper.selectById(order.getCustomerId());
            order.setCustomerName(c != null ? c.getName() : "");
        }
        order.setCode(generateCode());
        order.setStatus("草稿");
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        BigDecimal total = BigDecimal.ZERO;
        orderMapper.insert(order);
        for (SaleOrderItem it : items) {
            it.setId(null);
            it.setOrderId(order.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        SaleOrder u = new SaleOrder();
        u.setId(order.getId());
        u.setTotalAmount(total);
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SaleOrder order, List<SaleOrderItem> items) {
        SaleOrder old = orderMapper.selectById(order.getId());
        if (old == null) throw new BusinessException("销售单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        if (order.getCustomerId() != null) {
            Customer c = customerMapper.selectById(order.getCustomerId());
            order.setCustomerName(c != null ? c.getName() : "");
        }
        order.setCode(old.getCode());
        orderMapper.updateById(order);
        itemMapper.delete(new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, order.getId()));
        BigDecimal total = BigDecimal.ZERO;
        Long cid = CompanyContext.get();
        for (SaleOrderItem it : items) {
            it.setId(null);
            it.setOrderId(order.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        SaleOrder u = new SaleOrder();
        u.setId(order.getId());
        u.setTotalAmount(total);
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SaleOrder old = orderMapper.selectById(id);
        if (old == null) throw new BusinessException("销售单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        SaleOrder u = new SaleOrder();
        u.setId(id);
        u.setStatus("已作废");
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("销售单不存在");
        if (!"草稿".equals(order.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<SaleOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, id));
        if (items.isEmpty()) throw new BusinessException("订单明细不能为空");

        // 1) 库存联动：出库减库存（quantity 负值，库存不足自动抛异常）
        for (SaleOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            stockService.changeStock(order.getWarehouseId(), it.getMaterialName(),
                    it.getQuantity().negate(), "销售出库", order.getCode(), "销售单",
                    it.getMaterialId(), it.getSpec());
        }
        // 2) 生成应收台账
        FinanceReceivable fr = new FinanceReceivable();
        fr.setBillNo(order.getCode());
        fr.setCustomerId(order.getCustomerId());
        fr.setCustomerName(order.getCustomerName());
        fr.setSourceBillType("销售单");
        fr.setSourceBillNo(order.getCode());
        fr.setAmount(order.getTotalAmount());
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(order.getTotalAmount());
        fr.setDueDate(calcDueDate(order));
        fr.setStatus("未结清");
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fr.setCompanyId(cid);
        receivableMapper.insert(fr);
        // 3) 更新客户应收余额（冗余）
        if (order.getCustomerId() != null) {
            Customer c = customerMapper.selectById(order.getCustomerId());
            if (c != null) {
                Customer u = new Customer();
                u.setId(c.getId());
                BigDecimal newBal = (c.getReceivableBalance() != null ? c.getReceivableBalance() : BigDecimal.ZERO)
                        .add(order.getTotalAmount());
                u.setReceivableBalance(newBal);
                customerMapper.updateById(u);
            }
        }
        // 4) 更新订单状态为"已完成"（审核即出库）
        SaleOrder u = new SaleOrder();
        u.setId(id);
        u.setStatus("已完成");
        orderMapper.updateById(u);
    }

    @Override
    public List<Map<String, Object>> checkStock(Long warehouseId, List<SaleOrderItem> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (warehouseId == null || items == null || items.isEmpty()) return result;

        for (SaleOrderItem it : items) {
            if (it.getMaterialName() == null || it.getQuantity() == null) continue;
            BigDecimal required = it.getQuantity();
            InventoryWarehouseStock stock = stockMapper.selectOne(
                    new LambdaQueryWrapper<InventoryWarehouseStock>()
                            .eq(InventoryWarehouseStock::getWarehouseId, warehouseId)
                            .eq(InventoryWarehouseStock::getProductName, it.getMaterialName()));
            BigDecimal available = (stock != null && stock.getQuantity() != null) ? stock.getQuantity() : BigDecimal.ZERO;
            BigDecimal shortage = required.subtract(available);
            Map<String, Object> m = new HashMap<>();
            m.put("materialName", it.getMaterialName());
            m.put("spec", it.getSpec() != null ? it.getSpec() : "");
            m.put("unit", it.getUnit() != null ? it.getUnit() : "");
            m.put("required", required);
            m.put("available", available);
            m.put("shortage", shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage : BigDecimal.ZERO);
            m.put("sufficient", shortage.compareTo(BigDecimal.ZERO) <= 0);
            result.add(m);
        }
        return result;
    }

    /** 按客户账期计算到期日：月数+天数，都为0则当天到期（立即收款） */
    private LocalDate calcDueDate(SaleOrder order) {
        if (order.getOrderDate() == null) return null;
        LocalDate base = order.getOrderDate();
        if (order.getCustomerId() != null) {
            Customer c = customerMapper.selectById(order.getCustomerId());
            int months = c != null && c.getCreditPeriodMonths() != null ? c.getCreditPeriodMonths() : 0;
            int days = c != null && c.getCreditPeriod() != null ? c.getCreditPeriod() : 0;
            return base.plusMonths(months).plusDays(days);
        }
        return base; // 无客户信息默认当天到期
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "XS-" + d;
        LambdaQueryWrapper<SaleOrder> w = new LambdaQueryWrapper<SaleOrder>()
                .likeRight(SaleOrder::getCode, pat).orderByDesc(SaleOrder::getCode).last("LIMIT 1");
        SaleOrder last = orderMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return "XS-" + d + String.format("%03d", seq);
    }
}
