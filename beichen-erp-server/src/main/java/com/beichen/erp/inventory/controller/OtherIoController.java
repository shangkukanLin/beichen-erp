package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryOtherIo;
import com.beichen.erp.inventory.entity.InventoryOtherIoItem;
import com.beichen.erp.inventory.service.OtherIoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/other")
@RequiredArgsConstructor
public class OtherIoController {

    private final OtherIoService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String ioType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, warehouseId, ioType, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<InventoryOtherIo> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<InventoryOtherIoItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseIo(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        InventoryOtherIo io = parseIo(body);
        io.setId(id);
        service.update(io, parseItems(body));
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
    private InventoryOtherIo parseIo(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("otherIo") ? (Map<String, Object>) body.get("otherIo") : body;
        InventoryOtherIo o = new InventoryOtherIo();
        if (d.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(d.get("warehouseId").toString()));
        o.setIoType((String) d.get("ioType"));
        if (d.get("ioDate") != null && !d.get("ioDate").toString().isBlank())
            o.setIoDate(LocalDate.parse(d.get("ioDate").toString()));
        o.setRemark((String) d.get("remark"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<InventoryOtherIoItem> parseItems(Map<String, Object> body) {
        List<InventoryOtherIoItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    InventoryOtherIoItem it = new InventoryOtherIoItem();
                    if (map.get("materialId") != null) it.setMaterialId(Long.valueOf(map.get("materialId").toString()));
                    it.setMaterialName((String) map.get("materialName"));
                    it.setSpec((String) map.get("spec"));
                    it.setUnit((String) map.get("unit"));
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
