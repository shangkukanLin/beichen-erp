package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryProductReclassify;
import com.beichen.erp.inventory.entity.InventoryProductReclassifyItem;
import com.beichen.erp.inventory.service.ReclassifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 品质重分类
 */
@RestController
@RequestMapping("/api/inventory/reclassify")
@RequiredArgsConstructor
public class ReclassifyController {

    private final ReclassifyService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, warehouseId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<InventoryProductReclassify> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<InventoryProductReclassifyItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseRc(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        InventoryProductReclassify rc = parseRc(body);
        rc.setId(id);
        service.update(rc, parseItems(body));
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
    private InventoryProductReclassify parseRc(Map<String, Object> body) {
        InventoryProductReclassify rc = new InventoryProductReclassify();
        if (body.get("warehouseId") != null) rc.setWarehouseId(Long.valueOf(body.get("warehouseId").toString()));
        if (body.get("reclassifyDate") != null && !body.get("reclassifyDate").toString().isBlank())
            rc.setReclassifyDate(LocalDate.parse(body.get("reclassifyDate").toString()));
        rc.setRemark((String) body.get("remark"));
        return rc;
    }

    @SuppressWarnings("unchecked")
    private List<InventoryProductReclassifyItem> parseItems(Map<String, Object> body) {
        List<InventoryProductReclassifyItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    InventoryProductReclassifyItem it = new InventoryProductReclassifyItem();
                    if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
                    it.setFromQuality((String) map.get("fromQuality"));
                    it.setToQuality((String) map.get("toQuality"));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    it.setRemark((String) map.get("remark"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
