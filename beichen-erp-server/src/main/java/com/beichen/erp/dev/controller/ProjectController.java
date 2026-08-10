package com.beichen.erp.dev.controller;

import com.beichen.erp.common.PageParam;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/page")
    public R<?> page(PageParam param,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String status) {
        return R.ok(projectService.page(param, keyword, status));
    }

    @GetMapping("/{id}")
    public R<Project> detail(@PathVariable Long id) {
        return R.ok(projectService.getById(id));
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

    @GetMapping("/{projectId}/timelines")
    public R<List<ProjectTimeline>> timelines(@PathVariable Long projectId) {
        return R.ok(projectService.listByProject(projectId));
    }

    @PostMapping("/batch-timelines")
    public R<Map<Long, List<ProjectTimeline>>> batchTimelines(@RequestBody List<Long> projectIds) {
        return R.ok(projectService.batchTimelines(projectIds));
    }

    @GetMapping("/{projectId}/related-orders")
    public R<Map<String, Object>> relatedOrders(@PathVariable Long projectId) {
        return R.ok(projectService.getRelatedOrders(projectId));
    }
}
