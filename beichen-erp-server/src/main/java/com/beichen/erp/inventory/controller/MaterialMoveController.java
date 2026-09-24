package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryMaterialMove;
import com.beichen.erp.inventory.entity.InventoryMaterialMoveItem;
import com.beichen.erp.inventory.service.MaterialMoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 物料移仓单（2026-09-24 新增）：REST 前缀与成品移仓（{@code /api/inventory/warehouse-move}）对称。
 */
@RestController
@RequestMapping("/api/inventory/material-move")
@RequiredArgsConstructor
public class MaterialMoveController {

    private final MaterialMoveService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long fromWarehouseId,
            @RequestParam(required = false) Long toWarehouseId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, fromWarehouseId, toWarehouseId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<InventoryMaterialMove> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<InventoryMaterialMoveItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseMove(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        InventoryMaterialMove t = parseMove(body);
        t.setId(id);
        service.update(t, parseItems(body));
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

    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) {
        service.unAudit(id);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private InventoryMaterialMove parseMove(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("move") ? (Map<String, Object>) body.get("move") : body;
        InventoryMaterialMove o = new InventoryMaterialMove();
        if (d.get("fromWarehouseId") != null) o.setFromWarehouseId(Long.valueOf(d.get("fromWarehouseId").toString()));
        if (d.get("toWarehouseId") != null) o.setToWarehouseId(Long.valueOf(d.get("toWarehouseId").toString()));
        if (d.get("moveDate") != null && !d.get("moveDate").toString().isBlank())
            o.setMoveDate(LocalDate.parse(d.get("moveDate").toString()));
        o.setRemark((String) d.get("remark"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<InventoryMaterialMoveItem> parseItems(Map<String, Object> body) {
        List<InventoryMaterialMoveItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    InventoryMaterialMoveItem it = new InventoryMaterialMoveItem();
                    if (map.get("materialId") != null) it.setMaterialId(Long.valueOf(map.get("materialId").toString()));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    it.setRemark((String) map.get("remark"));
                    // 品质分级（2026-09-24）：与成品移仓同口径；空值由服务层兜底为 A
                    it.setQualityType((String) map.get("qualityType"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
