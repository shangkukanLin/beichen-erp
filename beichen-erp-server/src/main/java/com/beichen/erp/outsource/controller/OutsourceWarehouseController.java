package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.OutsourceWarehouse;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/outsource/warehouse")
@RequiredArgsConstructor
public class OutsourceWarehouseController {

    private final OutsourceWarehouseMapper mapper;
    private final SupplierMapper supplierMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) Long factoryId) {
        LambdaQueryWrapper<OutsourceWarehouse> w = new LambdaQueryWrapper<OutsourceWarehouse>()
                .like(warehouseName != null && !warehouseName.isBlank(), OutsourceWarehouse::getWarehouseName, warehouseName)
                .eq(factoryId != null, OutsourceWarehouse::getFactoryId, factoryId)
                .orderByDesc(OutsourceWarehouse::getId);
        Page<OutsourceWarehouse> page = mapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(wr -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", wr.getId());
            map.put("factoryId", wr.getFactoryId());
            Supplier s = supplierMapper.selectById(wr.getFactoryId());
            map.put("factoryName", s != null ? s.getName() : "");
            map.put("warehouseName", wr.getWarehouseName());
            map.put("address", wr.getAddress());
            map.put("contact", wr.getContact());
            map.put("phone", wr.getPhone());
            map.put("status", wr.getStatus());
            map.put("remark", wr.getRemark());
            return map;
        }).toList());
        return R.ok(result);
    }

    @GetMapping("/by-factory/{factoryId}")
    public R<Object> byFactory(@PathVariable Long factoryId) {
        return R.ok(mapper.selectList(new LambdaQueryWrapper<OutsourceWarehouse>()
                .eq(OutsourceWarehouse::getFactoryId, factoryId)));
    }

    @PostMapping
    public R<Void> add(@RequestBody OutsourceWarehouse w) {
        if (w.getWarehouseName() == null || w.getWarehouseName().isBlank()) w.setWarehouseName("默认仓库");
        mapper.insert(w);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody OutsourceWarehouse w) { mapper.updateById(w); return R.ok(); }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { mapper.deleteById(id); return R.ok(); }
}
