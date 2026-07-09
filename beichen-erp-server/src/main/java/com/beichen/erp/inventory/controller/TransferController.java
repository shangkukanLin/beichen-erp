package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryTransfer;
import com.beichen.erp.inventory.entity.InventoryTransferItem;
import com.beichen.erp.inventory.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService service;

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
    public R<InventoryTransfer> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<InventoryTransferItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseTransfer(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        InventoryTransfer t = parseTransfer(body);
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

    @SuppressWarnings("unchecked")
    private InventoryTransfer parseTransfer(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("transfer") ? (Map<String, Object>) body.get("transfer") : body;
        InventoryTransfer o = new InventoryTransfer();
        if (d.get("fromWarehouseId") != null) o.setFromWarehouseId(Long.valueOf(d.get("fromWarehouseId").toString()));
        if (d.get("toWarehouseId") != null) o.setToWarehouseId(Long.valueOf(d.get("toWarehouseId").toString()));
        if (d.get("transferDate") != null && !d.get("transferDate").toString().isBlank())
            o.setTransferDate(LocalDate.parse(d.get("transferDate").toString()));
        o.setRemark((String) d.get("remark"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<InventoryTransferItem> parseItems(Map<String, Object> body) {
        List<InventoryTransferItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    InventoryTransferItem it = new InventoryTransferItem();
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
