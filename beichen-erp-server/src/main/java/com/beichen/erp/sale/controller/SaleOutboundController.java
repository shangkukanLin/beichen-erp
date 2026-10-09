package com.beichen.erp.sale.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.R;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOrderItem;
import com.beichen.erp.sale.entity.SaleOutbound;
import com.beichen.erp.sale.entity.SaleOutboundItem;
import com.beichen.erp.sale.mapper.SaleOrderItemMapper;
import com.beichen.erp.sale.mapper.SaleOrderMapper;
import com.beichen.erp.sale.service.SaleOutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory/outbound")
@RequiredArgsConstructor
public class SaleOutboundController {

    private final SaleOutboundService service;

    // 2026-10-09（§7.26 幽灵字段）：出库页「从销售单带入明细」所需的只读依赖（镜像退货/换货页的读隔离做法）。
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, customerId, code, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<SaleOutbound> getById(@PathVariable Long id) { return R.ok(service.getById(id)); }

    @GetMapping("/{id}/items")
    public R<List<SaleOutboundItem>> getItems(@PathVariable Long id) { return R.ok(service.getItems(id)); }

    /**
     * 销售单下拉选项（出库页「来源销售单」选择器）。**走出库页自身前缀**：与退货/换货页同款**读隔离**
     * —— 原先这类反查要直读 {@code /api/inventory/sale}（需 {@code sale:order}），只被授予
     * {@code sale:outbound} 的用户会 403。只回**已审核**销售单（草稿单不该出货）。
     */
    @GetMapping("/sale-order-options")
    public R<List<Map<String, Object>>> saleOrderOptions(
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "200") Integer pageSize) {
        Page<SaleOrder> p = saleOrderMapper.selectPage(new Page<>(1, pageSize),
                new LambdaQueryWrapper<SaleOrder>()
                        .eq(SaleOrder::getStatus, DocStatus.AUDITED.getCode())
                        .like(code != null && !code.isBlank(), SaleOrder::getCode, code)
                        .orderByDesc(SaleOrder::getId));
        Set<Long> cids = p.getRecords().stream().map(SaleOrder::getCustomerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> cname = new HashMap<>();
        if (!cids.isEmpty()) {
            for (Customer c : customerMapper.selectBatchIds(cids)) cname.put(c.getId(), c.getName());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SaleOrder o : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("customerId", o.getCustomerId());
            m.put("customerName", cname.getOrDefault(o.getCustomerId(), ""));
            m.put("warehouseId", o.getWarehouseId());
            m.put("orderDate", o.getOrderDate());
            rows.add(m);
        }
        return R.ok(rows);
    }

    /**
     * 销售单明细（供出库页「从销售单带入明细」）。一次带回**单据头 + 明细**（选择器场景一次调用更省事），
     * 字段与销售单详情**逐字段一致**（同一实体 {@code SaleOrderItem}），并按 productId 批量回填产品名/SKU/单位。
     *
     * <p>带入口径（前端）：{@code orderItemId} = 明细行 id（出库明细因此可追溯到销售单行）、
     * {@code productId}、品质、数量、单价 —— 这正是 {@link #parseItems} 需要的键。
     * <b>2026-10-09 之前前端发的是 {@code materialId}</b>，而实体里没有该字段 ⇒ Jackson 静默丢弃
     * ⇒ {@code sale_outbound_item.product_id} 恒 NULL（见报告 §7.26）。</p>
     */
    @GetMapping("/sale-order-items")
    public R<Map<String, Object>> saleOrderItems(@RequestParam Long saleOrderId) {
        SaleOrder order = saleOrderMapper.selectById(saleOrderId);
        if (order == null) throw new BusinessException("销售单不存在：" + saleOrderId);
        List<SaleOrderItem> items = saleOrderItemMapper.selectList(
                new LambdaQueryWrapper<SaleOrderItem>()
                        .eq(SaleOrderItem::getOrderId, saleOrderId)
                        .orderByAsc(SaleOrderItem::getId));
        Set<Long> pids = items.stream().map(SaleOrderItem::getProductId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (!pids.isEmpty()) {
            Map<Long, Product> pm = productMapper.selectBatchIds(pids).stream()
                    .collect(Collectors.toMap(Product::getId, x -> x, (a, b) -> a));
            for (SaleOrderItem it : items) {
                Product pr = it.getProductId() != null ? pm.get(it.getProductId()) : null;
                it.setProductName(pr != null && pr.getName() != null ? pr.getName() : "");
                it.setSku(pr != null && pr.getSku() != null ? pr.getSku() : "");
                it.setUnit(pr != null && pr.getUnit() != null ? pr.getUnit() : "");
            }
        }
        Customer c = order.getCustomerId() != null ? customerMapper.selectById(order.getCustomerId()) : null;
        order.setCustomerName(c != null && c.getName() != null ? c.getName() : "");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("order", order);
        res.put("items", items);
        return R.ok(res);
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseOutbound(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SaleOutbound outbound = parseOutbound(body);
        outbound.setId(id);
        service.update(outbound, parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }

    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) { service.unAudit(id); return R.ok(); }

    @SuppressWarnings("unchecked")
    private SaleOutbound parseOutbound(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("outbound") ? (Map<String, Object>) body.get("outbound") : body;
        SaleOutbound o = new SaleOutbound();
        if (d.get("orderId") != null) o.setOrderId(Long.valueOf(d.get("orderId").toString()));
        if (d.get("customerId") != null) o.setCustomerId(Long.valueOf(d.get("customerId").toString()));
        if (d.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(d.get("warehouseId").toString()));
        if (d.get("outboundDate") != null && !d.get("outboundDate").toString().isBlank())
            o.setOutboundDate(LocalDate.parse(d.get("outboundDate").toString()));
        o.setRemark((String) d.get("remark"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<SaleOutboundItem> parseItems(Map<String, Object> body) {
        List<SaleOutboundItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    SaleOutboundItem it = new SaleOutboundItem();
                    if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
                    if (map.get("orderItemId") != null) it.setOrderItemId(Long.valueOf(map.get("orderItemId").toString()));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    if (map.get("unitPrice") != null && !map.get("unitPrice").toString().isBlank())
                        it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
                    it.setRemark((String) map.get("remark"));
                    it.setQualityType((String) map.get("qualityType"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
