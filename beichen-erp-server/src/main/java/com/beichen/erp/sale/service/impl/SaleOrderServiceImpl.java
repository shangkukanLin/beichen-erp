package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
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
import java.time.LocalDateTime;
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
    public Page<Map<String, Object>> page(String status, Long customerId, String code,
                                          String startDate, String endDate, int pageNum, int pageSize) {
        LambdaQueryWrapper<SaleOrder> w = new LambdaQueryWrapper<SaleOrder>()
                .eq(status != null && !status.isBlank(), SaleOrder::getStatus, status)
                .eq(customerId != null, SaleOrder::getCustomerId, customerId)
                .like(code != null && !code.isBlank(), SaleOrder::getCode, code)
                // 单据日期区间过滤（与报损接口的 startDate/endDate 同写法；order_date 为 DATE，与 yyyy-MM-dd 串比较）
                .ge(startDate != null && !startDate.isBlank(), SaleOrder::getOrderDate, startDate)
                .le(endDate != null && !endDate.isBlank(), SaleOrder::getOrderDate, endDate)
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
            m.put("taxAmount", o.getTaxAmount());
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

    /**
     * 明细查询：批量关联 product 回填名称/规格/单位。
     * <p>sale_order_item 只存 product_id（不冗余名称），若直接返回原始行，前端需自行翻译；
     * 一旦前端字典未就绪就会显示空白。此处统一在后端回填，前端可直接展示。</p>
     */
    @Override
    public List<SaleOrderItem> getItems(Long orderId) {
        List<SaleOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, orderId));
        if (items.isEmpty()) return items;
        fillProductInfo(items);
        return items;
    }

    /** 批量回填产品名称/规格/单位，消除 N+1 */
    private void fillProductInfo(List<SaleOrderItem> items) {
        List<Long> productIds = items.stream().map(SaleOrderItem::getProductId)
                .filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) return;
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        for (SaleOrderItem it : items) {
            Product p = it.getProductId() != null ? productMap.get(it.getProductId()) : null;
            it.setProductName(p != null && p.getName() != null ? p.getName() : "");
            it.setSku(p != null && p.getSku() != null ? p.getSku() : "");
            it.setSpec(p != null && p.getSpec() != null ? p.getSpec() : "");
            it.setUnit(p != null && p.getUnit() != null ? p.getUnit() : "");
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
        u.setTaxAmount(calcTaxAmount(total, order.getTaxIncluded(), order.getTaxRate()));
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
        u.setTaxAmount(calcTaxAmount(total, order.getTaxIncluded(), order.getTaxRate()));
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SaleOrder old = orderMapper.selectById(id);
        if (old == null) throw new BusinessException("销售单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(orderMapper, SaleOrder::getId, id, SaleOrder::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
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
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免应收台账重复生成
        if (!DocStatusGuard.claim(orderMapper, SaleOrder::getId, id, SaleOrder::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<SaleOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, id));
        if (items.isEmpty()) throw new BusinessException("订单明细不能为空");
        // P2-33：数量必须为正（负数量会生成负向应收，边界矩阵实测可审核通过）
        for (SaleOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("销售数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        // P2-33：客户与产品必须存在（原先会生成"客户不存在/空名"的应收台账，产品不存在则静默通过）
        if (order.getCustomerId() == null || customerMapper.selectById(order.getCustomerId()) == null)
            throw new BusinessException("客户不存在：ID=" + order.getCustomerId());
        for (SaleOrderItem it : items) {
            if (it.getProductId() != null && productMapper.selectById(it.getProductId()) == null)
                throw new BusinessException("产品不存在：ID=" + it.getProductId());
        }

        // 库存校验：库存不足不允许审核（真实出库走"销售出库单"，此处按当前库存把关；
        // 前端详情页审核前已预校验，此处兜底，避免从列表页等其它入口绕过）
        List<Map<String, Object>> shortage = checkStock(order.getWarehouseId(), items).stream()
                .filter(m -> !Boolean.TRUE.equals(m.get("sufficient")))
                .toList();
        if (!shortage.isEmpty()) {
            String detail = shortage.stream().limit(5)
                    .map(m -> String.format("%s（需 %s，库存 %s，缺 %s）",
                            m.get("productName"), m.get("required"), m.get("available"), m.get("shortage")))
                    .collect(Collectors.joining("；"));
            throw new BusinessException("库存不足，无法审核：" + detail
                    + (shortage.size() > 5 ? " 等 " + shortage.size() + " 项" : ""));
        }

        // 1) 生成应收台账（销售订单仅负责成交与应收，真实出库由"销售出库单"审核统一扣库存，避免双重扣减）
        // 反审核后重新审核时该单号台账已存在（冲销仅置 CANCELLED 并未删除），此处复用并重置，避免 bill_no 唯一键冲突
        FinanceReceivable exist = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, order.getCode()));
        FinanceReceivable fr = exist != null ? exist : new FinanceReceivable();
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
        if (exist != null) receivableMapper.updateById(fr);
        else receivableMapper.insert(fr);
        // 4) 更新订单状态为"已完成"（审核即出库）；记录审核时间供财务分析按月归集收入
        SaleOrder u = new SaleOrder();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditTime(LocalDateTime.now());
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        SaleOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("销售单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免应收台账重复冲销
        if (!DocStatusGuard.claim(orderMapper, SaleOrder::getId, id, SaleOrder::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核的销售单可反审核");
        // 1) 冲销应收台账（反审核，已收款单据会校验拦截）；台账不存在时跳过（历史单据可能未生成）
        FinanceReceivable exist = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, order.getCode()));
        if (exist != null) receivableHelper.reverseReceivable(order.getCode());
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
