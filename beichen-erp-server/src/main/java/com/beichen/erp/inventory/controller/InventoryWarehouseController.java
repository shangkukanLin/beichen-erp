package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryWarehouse;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/inventory/warehouse")
@RequiredArgsConstructor
public class InventoryWarehouseController {

    private final InventoryWarehouseMapper mapper;

    @GetMapping("/page")
    public R<Page<InventoryWarehouse>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) String warehouseType) {
        return R.ok(mapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<InventoryWarehouse>()
                .like(warehouseName != null && !warehouseName.isBlank(), InventoryWarehouse::getWarehouseName, warehouseName)
                .eq(warehouseType != null && !warehouseType.isBlank(), InventoryWarehouse::getWarehouseType, warehouseType)
                .orderByDesc(InventoryWarehouse::getId)));
    }

    @PostMapping
    public R<Void> add(@RequestBody InventoryWarehouse w) {
        if (w.getCode() == null || w.getCode().isBlank()) {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Long cnt = mapper.selectCount(new LambdaQueryWrapper<InventoryWarehouse>().likeRight(InventoryWarehouse::getCode, "WH-" + date));
            w.setCode("WH-" + date + String.format("%03d", (cnt != null ? cnt : 0) + 1));
        }
        if (w.getStatus() == null) w.setStatus(1);
        mapper.insert(w);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody InventoryWarehouse w) { mapper.updateById(w); return R.ok(); }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { mapper.deleteById(id); return R.ok(); }
}
