package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.service.ReceivableHelper;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements SaleOrderService {

    private final SaleOrderMapper orderMapper;
    private final SaleOrderItemMapper itemMapper;
    private final CustomerMapper customerMapper;
    private final FinanceReceivableMapper receivableMapper;
    private final ReceivableHelper receivableHelper;
    private final WarehouseStockMapper stockMapper;
    private final ProductMapper productMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long customerId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<SaleOrder> w = new LambdaQueryWrapper<SaleOrder>()
                .eq(status != null && !status.isBlank(), SaleOrder::getStatus, status)
                .eq(customerId != null, SaleOrder::getCustomerId, customerId)
                .like(code != null && !code.isBlank(), SaleOrder::getCode, code)
                .orderByDesc(SaleOrder::getId);
        Page<SaleOrder> raw = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量查询客户名称，消除 N+1
        List<Long> customerIds = raw.getRecords().stream().map(SaleOrder::getCustomerId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Customer> customerMap = customerIds.isEmpty() ? Collections.emptyMap()
                : customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));
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
            Customer c = o.getCustomerId() != null ? customerMap.get(o.getCustomerId()) : null;
            m.put("customerName", c != null ? c.getName() : "");
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
        order.setCode(generateCode());
        order.setStatus(DocStatus.DRAFT.getCode());
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
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
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
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        SaleOrder u = new SaleOrder();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("销售单不存在");
        if (!DocStatus.DRAFT.getCode().equals(order.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<SaleOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, id));
        if (items.isEmpty()) throw new BusinessException("订单明细不能为空");

        // 1) 生成应收台账（销售订单仅负责成交与应收，真实出库由"销售出库单"审核统一扣库存，避免双重扣减）
        FinanceReceivable fr = new FinanceReceivable();
        fr.setBillNo(order.getCode());
        fr.setCustomerId(order.getCustomerId());
        // 应收台账留痕：固化开单时的客户名（实时查一次）
        Customer c = order.getCustomerId() != null ? customerMapper.selectById(order.getCustomerId()) : null;
        fr.setCustomerName(c != null ? c.getName() : "");
        fr.setSourceBillType(SourceBillType.SALE_ORDER.getCode());
        fr.setSourceBillNo(order.getCode());
        fr.setSourceId(order.getId());
        fr.setAmount(order.getTotalAmount());
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(order.getTotalAmount());
        fr.setDueDate(calcDueDate(order));
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fr.setCompanyId(cid);
        receivableMapper.insert(fr);
        // 4) 更新订单状态为"已完成"（审核即出库）
        SaleOrder u = new SaleOrder();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        SaleOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("销售单不存在");
        if (!DocStatus.AUDITED.getCode().equals(order.getStatus())) throw new BusinessException("只有已审核的销售单可反审核");
        // 1) 冲销应收台账（反审核，已收款单据会校验拦截）
        receivableHelper.reverseReceivable(order.getCode());
        // 3) 订单状态回退为草稿（库存由"销售出库单"反审核统一回补，订单本身不触碰库存）
        SaleOrder u = new SaleOrder();
        u.setId(id);
        u.setStatus(DocStatus.DRAFT.getCode());
        orderMapper.updateById(u);
    }

    @Override
    public List<Map<String, Object>> checkStock(Long warehouseId, List<SaleOrderItem> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (warehouseId == null || items == null || items.isEmpty()) return result;

        for (SaleOrderItem it : items) {
            if (it.getProductId() == null || it.getQuantity() == null) continue;
            Product product = productMapper.selectById(it.getProductId());
            if (product == null) continue;
            BigDecimal required = it.getQuantity();
            BigDecimal available = stockMapper.selectList(
                    new LambdaQueryWrapper<WarehouseStock>()
                            .eq(WarehouseStock::getWarehouseId, warehouseId)
                            .eq(WarehouseStock::getProductId, it.getProductId()))
                    .stream().map(s -> s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal shortage = required.subtract(available);
            Map<String, Object> m = new HashMap<>();
            m.put("productId", it.getProductId());
            m.put("productName", product.getName());
            m.put("spec", product.getSpec() != null ? product.getSpec() : "");
            m.put("unit", product.getUnit() != null ? product.getUnit() : "");
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
        String pat = BillPrefix.SALE + d;
        LambdaQueryWrapper<SaleOrder> w = new LambdaQueryWrapper<SaleOrder>()
                .likeRight(SaleOrder::getCode, pat).orderByDesc(SaleOrder::getCode).last("LIMIT 1");
        SaleOrder last = orderMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.SALE + d + String.format("%03d", seq);
    }
}
