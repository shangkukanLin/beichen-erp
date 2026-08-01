package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryWarehouseMove;
import com.beichen.erp.inventory.entity.InventoryWarehouseMoveItem;
import com.beichen.erp.inventory.service.WarehouseMoveService;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/inventory/warehouse-move")
@RequiredArgsConstructor
public class WarehouseMoveController {

    private final WarehouseMoveService service;
    private final MaterialMapper materialMapper;

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
    public R<InventoryWarehouseMove> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<Map<String, Object>>> getItems(@PathVariable Long id) {
        List<InventoryWarehouseMoveItem> raw = service.getItems(id);
        List<Map<String, Object>> list = new ArrayList<>();
        for (InventoryWarehouseMoveItem it : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", it.getId());
            m.put("productId", it.getProductId());
            m.put("qualityType", it.getQualityType());
            m.put("quantity", it.getQuantity());
            m.put("remark", it.getRemark());
            if (it.getProductId() != null) {
                Material product = materialMapper.selectById(it.getProductId());
                if (product != null) {
                    m.put("productName", product.getName());
                    m.put("spec", product.getSpec());
                    m.put("unit", product.getUnit());
                }
            }
            list.add(m);
        }
        return R.ok(list);
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseMove(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        InventoryWarehouseMove t = parseMove(body);
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
    private InventoryWarehouseMove parseMove(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("move") ? (Map<String, Object>) body.get("move") : body;
        InventoryWarehouseMove o = new InventoryWarehouseMove();
        if (d.get("fromWarehouseId") != null) o.setFromWarehouseId(Long.valueOf(d.get("fromWarehouseId").toString()));
        if (d.get("toWarehouseId") != null) o.setToWarehouseId(Long.valueOf(d.get("toWarehouseId").toString()));
        if (d.get("moveDate") != null && !d.get("moveDate").toString().isBlank())
            o.setMoveDate(LocalDate.parse(d.get("moveDate").toString()));
        o.setRemark((String) d.get("remark"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<InventoryWarehouseMoveItem> parseItems(Map<String, Object> body) {
        List<InventoryWarehouseMoveItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    InventoryWarehouseMoveItem it = new InventoryWarehouseMoveItem();
                    if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    it.setRemark((String) map.get("remark"));
                    it.setQualityType((String) map.get("qualityType"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
