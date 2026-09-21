package com.beichen.erp.dev.controller;

import com.beichen.erp.common.PageParam;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectPhase;
import com.beichen.erp.dev.service.ProjectService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProductMapper productMapper;

    /**
     * 产品名称查重：新增项目时校验「产品名称」是否与已有产品重名。
     * <p>2026-09-21：随「总成名称 → 产品名称」改名，端点由 {@code /check-assembly} 更名为
     * {@code /check-product-name}、方法名 {@code checkAssembly → checkProductName}。</p>
     */
    @GetMapping("/check-product-name")
    public R<Map<String, Object>> checkProductName(@RequestParam String name) {
        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>().eq(Product::getName, name).last("LIMIT 1"));
        Map<String, Object> result = new LinkedHashMap<>();
        if (product != null) {
            result.put("exists", true);
            result.put("productId", product.getId());
            result.put("productName", product.getName());
        } else {
            result.put("exists", false);
        }
        return R.ok(result);
    }

    @GetMapping("/page")
    public R<?> page(PageParam param,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) Long brandId) {
        return R.ok(projectService.page(param, keyword, status, brandId));
    }

    @GetMapping("/{id}")
    public R<Project> detail(@PathVariable Long id) {
        Project p = projectService.getById(id);
        // 2026-09-21（需求 1）：回填关联产品的**当前 SKU**（Project.productSku 是 @TableField(exist=false)
        // 的瞬态字段，不落库）⇒ 立项详细页可直接展示/修改它，无需再单独查产品。
        if (p != null && p.getProductId() != null) {
            Product prod = productMapper.selectById(p.getProductId());
            if (prod != null) p.setProductSku(prod.getSku());
        }
        return R.ok(p);
    }

    @PostMapping
    public R<Project> create(@RequestBody Project project,
                             @RequestParam(required = false) Long linkExistingProductId) {
        return R.ok(projectService.create(project, linkExistingProductId));
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        projectService.cancel(id);
        return R.ok();
    }

    @PutMapping("/{id}/reactivate")
    public R<Void> reactivate(@PathVariable Long id) {
        projectService.reactivate(id);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody Project project) {
        projectService.updateProject(project);
        return R.ok();
    }

    @GetMapping("/{projectId}/phases")
    public R<List<ProjectPhase>> phases(@PathVariable Long projectId) {
        return R.ok(projectService.listByProject(projectId));
    }

    @PostMapping("/batch-phases")
    public R<Map<Long, List<ProjectPhase>>> batchPhases(@RequestBody List<Long> projectIds) {
        return R.ok(projectService.batchPhases(projectIds));
    }

    @GetMapping("/{projectId}/related-orders")
    public R<Map<String, Object>> relatedOrders(@PathVariable Long projectId) {
        return R.ok(projectService.getRelatedOrders(projectId));
    }
}
