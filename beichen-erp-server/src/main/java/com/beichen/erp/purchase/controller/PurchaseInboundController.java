package com.beichen.erp.purchase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.purchase.entity.PurchaseInbound;
import com.beichen.erp.purchase.entity.PurchaseInboundItem;
import com.beichen.erp.purchase.service.PurchaseInboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/inbound")
@RequiredArgsConstructor
public class PurchaseInboundController {

    private final PurchaseInboundService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, supplierId, code, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<PurchaseInbound> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<PurchaseInboundItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseInbound(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PurchaseInbound inbound = parseInbound(body);
        inbound.setId(id);
        service.update(inbound, parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        service.audit(id);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private PurchaseInbound parseInbound(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("inbound") ? (Map<String, Object>) body.get("inbound") : body;
        PurchaseInbound o = new PurchaseInbound();
        if (d.get("orderId") != null) o.setOrderId(Long.valueOf(d.get("orderId").toString()));
        if (d.get("supplierId") != null) o.setSupplierId(Long.valueOf(d.get("supplierId").toString()));
        if (d.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(d.get("warehouseId").toString()));
        if (d.get("inboundDate") != null && !d.get("inboundDate").toString().isBlank())
            o.setInboundDate(LocalDate.parse(d.get("inboundDate").toString()));
        o.setRemark((String) d.get("remark"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<PurchaseInboundItem> parseItems(Map<String, Object> body) {
        List<PurchaseInboundItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    PurchaseInboundItem it = new PurchaseInboundItem();
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
