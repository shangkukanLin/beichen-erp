package com.beichen.erp.sale.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOrderItem;
import com.beichen.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/sale")
@RequiredArgsConstructor
public class SaleOrderController {

    private final SaleOrderService service;

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
    public R<SaleOrder> getById(@PathVariable Long id) { return R.ok(service.getById(id)); }

    @GetMapping("/{id}/items")
    public R<List<SaleOrderItem>> getItems(@PathVariable Long id) { return R.ok(service.getItems(id)); }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseOrder(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SaleOrder order = parseOrder(body);
        order.setId(id);
        service.update(order, parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }

    /** 库存检查：传入 warehouseId 和 items，返回各物料的库存对比 */
    @PostMapping("/check-stock")
    public R<List<Map<String, Object>>> checkStock(@RequestBody Map<String, Object> body) {
        Long warehouseId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        List<SaleOrderItem> items = parseItems(body);
        return R.ok(service.checkStock(warehouseId, items));
    }

    @SuppressWarnings("unchecked")
    private SaleOrder parseOrder(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("order") ? (Map<String, Object>) body.get("order") : body;
        SaleOrder o = new SaleOrder();
        if (d.get("customerId") != null) o.setCustomerId(Long.valueOf(d.get("customerId").toString()));
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
    private List<SaleOrderItem> parseItems(Map<String, Object> body) {
        List<SaleOrderItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    SaleOrderItem it = new SaleOrderItem();
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
