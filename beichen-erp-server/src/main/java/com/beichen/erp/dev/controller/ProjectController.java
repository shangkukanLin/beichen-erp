package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.entity.dto.ProjectDTO;
import com.beichen.erp.dev.entity.dto.ProjectQueryDTO;
import com.beichen.erp.dev.service.ProjectService;
import com.beichen.erp.dev.service.ProjectTimelineService;
import jakarta.validation.Valid;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectTimelineService timelineService;

    @GetMapping("/page")
    public R<Page<Project>> page(ProjectQueryDTO query) {
        return R.ok(projectService.page(query));
    }

    @GetMapping("/{id}")
    public R<Project> getById(@PathVariable Long id) {
        return R.ok(projectService.getById(id));
    }

    @PostMapping
    public R<Void> add(@Valid @RequestBody ProjectDTO dto) {
        projectService.create(dto);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody ProjectDTO dto) {
        projectService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        projectService.updateStatus(id, status);
        return R.ok();
    }

    @GetMapping("/{id}/timeline")
    public R<List<ProjectTimeline>> getTimeline(@PathVariable Long id) {
        return R.ok(timelineService.listByProject(id));
    }

    @PutMapping("/{id}/timeline")
    public R<Void> saveTimeline(@PathVariable Long id, @RequestBody List<Map<String, Object>> list) {
        for (Map<String, Object> item : list) {
            String statusName = (String) item.get("statusName");
            Object planned = item.get("plannedEnd");
            Object actual = item.get("actualEnd");
            Object status = item.get("status");
            if (planned != null && !planned.toString().isBlank()) {
                timelineService.updatePlanned(id, statusName, LocalDate.parse(planned.toString()));
            }
            if (actual != null && !actual.toString().isBlank()) {
                timelineService.updateTimeline(id, statusName, LocalDate.parse(actual.toString()));
            }
            if (status != null && !status.toString().isBlank()) {
                timelineService.updateStatus(id, statusName, status.toString());
            }
        }
        return R.ok();
    }

    @PostMapping("/timelines/batch")
    public R<Map<Long, List<ProjectTimeline>>> batchTimelines(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("projectIds");
        Map<Long, List<ProjectTimeline>> result = new HashMap<>();
        if (ids != null) {
            for (Long id : ids) {
                result.put(id, timelineService.listByProject(id));
            }
        }
        return R.ok(result);
    }
}
