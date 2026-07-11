package com.beichen.erp.material.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.entity.MaterialBom;
import com.beichen.erp.material.service.MaterialBomService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/material")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;
    private final MaterialBomService materialBomService;
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
            String placeholders = materialIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Map<String, Object>> stockRows = jdbcTemplate.queryForList(
                "SELECT material_id, SUM(quantity) AS total FROM inventory_warehouse_stock WHERE material_id IN (" + placeholders + ") GROUP BY material_id",
                materialIds.toArray());
            Map<Long, BigDecimal> stockMap = stockRows.stream()
                .collect(Collectors.toMap(
                    r -> ((Number) r.get("material_id")).longValue(),
                    r -> (BigDecimal) r.get("total"),
                    (a, b) -> a));
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
        // 新增后同步子物料组成
        if (material.getId() != null && material.getBomChildren() != null) {
            materialBomService.saveChildren(material.getId(), material.getBomChildren());
        }
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody Material material) {
        materialService.updateById(material);
        // 编辑后同步子物料组成（全量替换）
        if (material.getId() != null && material.getBomChildren() != null) {
            materialBomService.saveChildren(material.getId(), material.getBomChildren());
        }
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        materialService.removeById(id);
        // 级联清理相关BOM关系
        materialBomService.removeByMaterial(id);
        return R.ok();
    }
}
