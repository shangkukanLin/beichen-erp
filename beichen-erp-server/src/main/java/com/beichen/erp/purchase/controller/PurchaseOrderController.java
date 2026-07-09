package com.beichen.erp.purchase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.entity.PurchaseOrderItem;
import com.beichen.erp.purchase.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/purchase")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;

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
    public R<PurchaseOrder> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<PurchaseOrderItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseOrder(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PurchaseOrder order = parseOrder(body);
        order.setId(id);
        service.update(order, parseItems(body));
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
    private PurchaseOrder parseOrder(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("order") ? (Map<String, Object>) body.get("order") : body;
        PurchaseOrder o = new PurchaseOrder();
        if (d.get("supplierId") != null) o.setSupplierId(Long.valueOf(d.get("supplierId").toString()));
        if (d.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(d.get("warehouseId").toString()));
        if (d.get("orderDate") != null && !d.get("orderDate").toString().isBlank())
            o.setOrderDate(LocalDate.parse(d.get("orderDate").toString()));
        if (d.get("taxIncluded") != null) o.setTaxIncluded(Integer.valueOf(d.get("taxIncluded").toString()));
        if (d.get("taxRate") != null && !d.get("taxRate").toString().isBlank())
            o.setTaxRate(new BigDecimal(d.get("taxRate").toString()));
        o.setRemark((String) d.get("remark"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<PurchaseOrderItem> parseItems(Map<String, Object> body) {
        List<PurchaseOrderItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    PurchaseOrderItem it = new PurchaseOrderItem();
                    if (map.get("materialId") != null) it.setMaterialId(Long.valueOf(map.get("materialId").toString()));
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
