package com.beichen.erp.material.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 成品管理（product 表）
 */
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;

    /** 分页查询（支持关键字/分类筛选） */
    @GetMapping("/page")
    public R<Page<Product>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return R.ok(service.page(keyword, category, pageNum, pageSize));
    }

    /** 单条查询 */
    @GetMapping("/{id}")
    public R<Product> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    /** 新增 */
    @PostMapping
    public R<Void> add(@RequestBody Product product) {
        service.save(product);
        return R.ok();
    }

    /** 修改 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        service.updateById(product);
        return R.ok();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return R.ok();
    }

    /** 获取品质等级枚举列表 */
    @GetMapping("/quality-types")
    public R<List<Map<String, String>>> getQualityTypes() {
        List<Map<String, String>> list = new ArrayList<>();
        for (ProductQualityType t : ProductQualityType.values()) {
            list.add(Map.of("value", t.name(), "label", t.getLabel()));
        }
        return R.ok(list);
    }
}
