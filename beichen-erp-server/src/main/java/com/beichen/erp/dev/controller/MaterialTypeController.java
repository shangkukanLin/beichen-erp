package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.service.MaterialTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/material-type")
@RequiredArgsConstructor
public class MaterialTypeController {

    private final MaterialTypeService materialTypeService;

    @GetMapping("/enabled")
    public R<List<MaterialType>> enabled() {
        return R.ok(materialTypeService.enabled());
    }

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                     @RequestParam(defaultValue = "20") Integer pageSize) {
        return R.ok(materialTypeService.page(pageNum, pageSize));
    }

    @PostMapping
    public R<Void> add(@RequestBody MaterialType type) {
        materialTypeService.add(type);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody MaterialType type) {
        materialTypeService.update(type);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        materialTypeService.delete(id);
        return R.ok();
    }
}
