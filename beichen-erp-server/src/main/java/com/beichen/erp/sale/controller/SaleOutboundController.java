package com.beichen.erp.sale.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.sale.entity.SaleOutbound;
import com.beichen.erp.sale.entity.SaleOutboundItem;
import com.beichen.erp.sale.service.SaleOutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/outbound")
@RequiredArgsConstructor
public class SaleOutboundController {

    private final SaleOutboundService service;

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
                    if (map.get("materialId") != null) it.setProductId(Long.valueOf(map.get("materialId").toString()));
                    if (map.get("orderItemId") != null) it.setOrderItemId(Long.valueOf(map.get("orderItemId").toString()));
                    it.setMaterialCode((String) map.get("materialCode"));
                    it.setMaterialName((String) map.get("materialName"));
                    it.setSpec((String) map.get("spec"));
                    it.setUnit((String) map.get("unit"));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    if (map.get("unitPrice") != null && !map.get("unitPrice").toString().isBlank())
                        it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
                    it.setRemark((String) map.get("remark"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
