package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.entity.InventoryWarehouse;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/warehouse")
@RequiredArgsConstructor
public class InventoryWarehouseController {

    private final InventoryWarehouseMapper mapper;
    private final JdbcTemplate jdbcTemplate;

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
    public R<Void> delete(@PathVariable Long id) {
        Map<String, Object> check = checkDelete(id).getData();
        if (!(Boolean) check.get("canDelete")) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> associations = (Map<String, Integer>) check.get("associations");
            StringBuilder sb = new StringBuilder("该仓库有关联数据，无法删除：");
            associations.forEach((k, v) -> sb.append("\n  - ").append(k).append("：").append(v).append("条"));
            throw new BusinessException(sb.toString());
        }
        mapper.deleteById(id);
        return R.ok();
    }

    @GetMapping("/{id}/check-delete")
    public R<Map<String, Object>> checkDelete(@PathVariable Long id) {
        Map<String, Integer> associations = new LinkedHashMap<>();

        int cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_order WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("采购订单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_inbound WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("采购入库单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_order WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("销售订单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_outbound WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("销售出库单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_warehouse_stock WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("库存记录", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_stock_log WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("库存流水", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_stock_take WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("盘点单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transfer WHERE from_warehouse_id = ? OR to_warehouse_id = ?", Integer.class, id, id);
        if (cnt > 0) associations.put("调拨单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_other_io WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("其他出入库单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_warehouse_stock WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("委外库存", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_order_delivery WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.merge("委外加工", cnt, Integer::sum);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_material WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.merge("委外加工", cnt, Integer::sum);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_delivery WHERE from_warehouse_id = ? OR to_warehouse_id = ?", Integer.class, id, id);
        if (cnt > 0) associations.merge("委外加工", cnt, Integer::sum);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canDelete", associations.isEmpty());
        result.put("associations", associations);
        return R.ok(result);
    }
}
