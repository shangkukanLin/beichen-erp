package com.beichen.erp.material.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/material")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping("/page")
    public R<Page<Material>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category) {
        Page<Material> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<Material>()
                .like(code != null && !code.isBlank(), Material::getCode, code)
                .like(name != null && !name.isBlank(), Material::getName, name)
                .eq(category != null && !category.isBlank(), Material::getCategory, category)
                .orderByDesc(Material::getId);
        return R.ok(materialService.page(page, wrapper));
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
        materialService.removeById(id);
        return R.ok();
    }
}
