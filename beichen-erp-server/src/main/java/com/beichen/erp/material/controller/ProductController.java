package com.beichen.erp.material.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.material.common.ProductStatus;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.service.ProductService;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import jakarta.validation.Valid;
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
    private final ProjectProductSyncService projectProductSyncService;

    /** 分页查询（支持关键字/分类/状态筛选） */
    @GetMapping("/page")
    public R<Page<Product>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        ProductStatus ps = status != null ? ProductStatus.fromValue(status) : null;
        return R.ok(service.page(keyword, category, ps, pageNum, pageSize));
    }

    /** 单条查询 */
    @GetMapping("/{id}")
    public R<Product> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    /** 新增 */
    @PostMapping
    public R<Void> add(@Valid @RequestBody Product product) {
        service.save(product);
        return R.ok();
    }

    /** 修改 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        // 若产品名称变更，同步更新关联项目的总成名称
        if (product.getName() != null) {
            Product old = service.getById(id);
            if (old != null && (old.getName() == null || !old.getName().equals(product.getName()))) {
                projectProductSyncService.syncAssemblyNameFromProduct(id, product.getName());
            }
        }
        service.updateById(product);
        return R.ok();
    }

    /** 删除（物理删除改为停用，走 status 生命周期，避免关联库存/单据成孤儿数据） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.discontinue(id);
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
