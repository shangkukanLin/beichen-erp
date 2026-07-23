package com.beichen.erp.material.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.beichen.erp.exception.BusinessException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/material")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    public R<Page<Material>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        Page<Material> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<Material>()
                .like(code != null && !code.isBlank(), Material::getCode, code)
                .like(name != null && !name.isBlank(), Material::getName, name)
                .eq(category != null && !category.isBlank(), Material::getCategory, category)
                .eq(status != null && !status.isBlank(), Material::getStatus, status)
                .orderByDesc(Material::getId);
        Page<Material> result = materialService.page(page, wrapper);

        // 从真实库存表汇总当前库存
        List<Long> materialIds = result.getRecords().stream()
                .map(Material::getId).filter(id -> id != null).collect(Collectors.toList());
        if (!materialIds.isEmpty()) {
            Map<Long, BigDecimal> stockMap = new java.util.HashMap<>();
            String idPlaceholders = materialIds.stream().map(id -> "?").collect(Collectors.joining(","));

            // 方式1: 按 material_id 汇总
            List<Map<String, Object>> rows1 = jdbcTemplate.queryForList(
                "SELECT material_id, SUM(quantity) AS total FROM inventory_warehouse_stock WHERE material_id IN (" + idPlaceholders + ") GROUP BY material_id",
                materialIds.toArray());
            for (Map<String, Object> row : rows1) {
                Long mid = ((Number) row.get("material_id")).longValue();
                stockMap.merge(mid, (BigDecimal) row.get("total"), BigDecimal::add);
            }

            // 方式2: 按 product_name 汇总（兜底 material_id 为 NULL 的记录）
            List<String> productNames = result.getRecords().stream()
                .map(Material::getName).filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toList());
            if (!productNames.isEmpty()) {
                String namePlaceholders = productNames.stream().map(n -> "?").collect(Collectors.joining(","));
                List<Map<String, Object>> rows2 = jdbcTemplate.queryForList(
                    "SELECT m.id AS material_id, SUM(s.quantity) AS total FROM inventory_warehouse_stock s " +
                    "JOIN material m ON s.product_name = m.name WHERE s.material_id IS NULL AND s.product_name IN (" + namePlaceholders + ") GROUP BY m.id",
                    productNames.toArray());
                for (Map<String, Object> row : rows2) {
                    Long mid = ((Number) row.get("material_id")).longValue();
                    stockMap.merge(mid, (BigDecimal) row.get("total"), BigDecimal::add);
                }
            }

            result.getRecords().forEach(m -> {
                BigDecimal stock = stockMap.get(m.getId());
                if (stock != null) m.setCurrentStock(stock);
            });
        }

        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Material> getById(@PathVariable Long id) {
        return R.ok(materialService.getById(id));
    }

    @PostMapping
    public R<Void> add(@RequestBody Material material) {
        materialService.save(material);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody Material material) {
        materialService.updateById(material);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Map<String, Object> check = checkDelete(id).getData();
        if (!(Boolean) check.get("canDelete")) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> associations = (Map<String, Integer>) check.get("associations");
            StringBuilder sb = new StringBuilder("该物料有关联数据，无法删除：");
            associations.forEach((k, v) -> sb.append("\n  - ").append(k).append("：").append(v).append("条"));
            throw new BusinessException(sb.toString());
        }
        materialService.removeById(id);
        return R.ok();
    }

    @GetMapping("/{id}/check-delete")
    public R<Map<String, Object>> checkDelete(@PathVariable Long id) {
        Map<String, Integer> associations = new LinkedHashMap<>();

        // 采购订单明细
        int cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_order_item WHERE product_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("采购订单明细", cnt);

        // 采购入库明细
        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_inbound_item WHERE product_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("采购入库明细", cnt);

        // 销售订单明细
        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_order_item WHERE product_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("销售订单明细", cnt);

        // 销售出库明细
        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_outbound_item WHERE product_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("销售出库明细", cnt);

        // 库存记录
        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_warehouse_stock WHERE product_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("库存记录", cnt);

        // 库存流水
        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_stock_log WHERE product_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("库存流水", cnt);

        // 盘点明细
        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_stock_take_item WHERE product_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("盘点明细", cnt);

        // 调拨明细
        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transfer_item WHERE product_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("调拨明细", cnt);

        // 其他出入库明细
        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_other_io_item WHERE product_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("其他出入库明细", cnt);

        // 委外加工
        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_order_material WHERE material_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("委外订单物料", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_warehouse_stock WHERE material_id = ?", Integer.class, id);
        if (cnt > 0) associations.merge("委外库存", cnt, Integer::sum);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_delivery_item WHERE material_id = ?", Integer.class, id);
        if (cnt > 0) associations.merge("委外交货", cnt, Integer::sum);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_material_order_item WHERE material_id = ?", Integer.class, id);
        if (cnt > 0) associations.merge("委外物料订单", cnt, Integer::sum);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_material_component WHERE parent_material_id = ? OR child_material_id = ?", Integer.class, id, id);
        if (cnt > 0) associations.merge("委外物料组件", cnt, Integer::sum);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_order_close_report_item WHERE material_id = ?", Integer.class, id);
        if (cnt > 0) associations.merge("委外结单", cnt, Integer::sum);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canDelete", associations.isEmpty());
        result.put("associations", associations);
        return R.ok(result);
    }
}
