package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.service.ProjectService;
import com.beichen.erp.dev.service.ProjectTimelineService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.OutsourceOrderMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderProductMapper;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectTimelineService projectTimelineService;
    private final OutsourceOrderProductMapper outsourceOrderProductMapper;
    private final OutsourceOrderMapper outsourceOrderMapper;
    private final ProductMapper productMapper;
    private final ProjectProductSyncService projectProductSyncService;

    /** 批量获取项目时间线（供列表页展示当前阶段名） */
    @PostMapping("/timelines/batch")
    public R<Map<Long, List<ProjectTimeline>>> batchTimelines(@RequestBody Map<String, List<Long>> body) {
        List<Long> projectIds = body.getOrDefault("projectIds", Collections.emptyList());
        Map<Long, List<ProjectTimeline>> result = new HashMap<>();
        for (Long projectId : projectIds) {
            result.put(projectId, projectTimelineService.listByProject(projectId));
        }
        return R.ok(result);
    }

    /** 分页查询项目 */
    @GetMapping("/page")
    public R<Page<Project>> page(PageParam param,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String status) {
        return R.ok(projectService.page(param, keyword, status));
    }

    /** 获取项目详情 */
    @GetMapping("/{id}")
    public R<Project> detail(@PathVariable Long id) {
        return R.ok(projectService.getById(id));
    }

    /** 新增项目（linkExistingProductId=可选，重名时用户选择关联已有产品） */
    @PostMapping
    public R<Project> add(@RequestBody Project project,
                          @RequestParam(required = false) Long linkExistingProductId) {
        return R.ok(projectService.create(project, linkExistingProductId));
    }

    /** 检查总成名称是否与已有产品重名（用于新增项目前的关联确认） */
    @GetMapping("/check-assembly")
    public R<Map<String, Object>> checkAssembly(@RequestParam String name) {
        Map<String, Object> result = new HashMap<>();
        result.put("exists", false);
        if (name == null || name.isBlank()) return R.ok(result);
        Product p = productMapper.selectOne(
                new LambdaQueryWrapper<Product>().eq(Product::getName, name).last("LIMIT 1"));
        result.put("exists", p != null);
        if (p != null) {
            result.put("productId", p.getId());
            result.put("productName", p.getName());
        }
        return R.ok(result);
    }

    /** 修改项目 */
    @PutMapping
    public R<Void> update(@RequestBody Project project) {
        // 若总成名称变更，同步更新关联产品的产品名称
        if (project.getId() != null && project.getAssemblyName() != null) {
            Project old = projectService.getById(project.getId());
            if (old != null && (old.getAssemblyName() == null
                    || !old.getAssemblyName().equals(project.getAssemblyName()))) {
                projectProductSyncService.syncProductNameFromProject(project.getId(), project.getAssemblyName());
            }
        }
        projectService.updateById(project);
        return R.ok();
    }

    /** 取消项目 */
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        projectService.cancel(id);
        return R.ok();
    }

    /** 重新激活项目 */
    @PutMapping("/{id}/reactivate")
    public R<Void> reactivate(@PathVariable Long id) {
        projectService.reactivate(id);
        return R.ok();
    }

    /** 获取项目关联的委外订单 */
    @GetMapping("/{id}/related-orders")
    public R<List<Map<String, Object>>> relatedOrders(@PathVariable Long id) {
        List<OutsourceOrderProduct> products = outsourceOrderProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>()
                        .eq(OutsourceOrderProduct::getProjectId, id));
        if (products.isEmpty()) return R.ok(Collections.emptyList());

        Set<Long> orderIds = products.stream()
                .map(OutsourceOrderProduct::getOrderId)
                .collect(Collectors.toSet());
        List<OutsourceOrder> orders = outsourceOrderMapper.selectBatchIds(orderIds);

        Map<Long, String> orderProductNames = new HashMap<>();
        for (OutsourceOrderProduct p : products) {
            orderProductNames.merge(p.getOrderId(),
                    p.getProductName() != null ? p.getProductName() : "",
                    (a, b) -> a + "、" + b);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (OutsourceOrder o : orders) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", o.getId());
            map.put("code", o.getCode());
            map.put("status", o.getStatus());
            map.put("createTime", o.getCreateTime() != null ? o.getCreateTime().toString() : "");
            map.put("productName", orderProductNames.getOrDefault(o.getId(), ""));
            result.add(map);
        }
        return R.ok(result);
    }
}
