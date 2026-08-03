package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.service.ProjectTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class ProjectTimelineController {

    private final ProjectTimelineService projectTimelineService;

    /** 获取项目时间线 */
    @GetMapping("/{projectId}/timeline")
    public R<List<ProjectTimeline>> list(@PathVariable Long projectId) {
        return R.ok(projectTimelineService.listByProject(projectId));
    }

    /** 保存时间线行（含状态变更+日期后推） */
    @PutMapping("/timeline/{id}")
    public R<Void> saveRow(@PathVariable Long id, @RequestBody ProjectTimeline row) {
        row.setId(id);
        projectTimelineService.saveTimelineRow(row.getProjectId(), row);
        return R.ok();
    }

    /** 完成阶段 */
    @PutMapping("/timeline/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        ProjectTimeline tl = projectTimelineService.getById(id);
        if (tl == null) return R.fail("时间线记录不存在");
        projectTimelineService.completePhase(tl.getProjectId(), id);
        return R.ok();
    }

    /** 跳过阶段 */
    @PutMapping("/timeline/{id}/skip")
    public R<Void> skip(@PathVariable Long id) {
        ProjectTimeline tl = projectTimelineService.getById(id);
        if (tl == null) return R.fail("时间线记录不存在");
        projectTimelineService.skipPhase(tl.getProjectId(), id);
        return R.ok();
    }

    /** 更新计划日期 */
    @PutMapping("/{projectId}/timeline/planned")
    public R<Void> updatePlanned(@PathVariable Long projectId,
                                 @RequestParam String phaseName,
                                 @RequestParam LocalDate plannedEnd) {
        projectTimelineService.updatePlanned(projectId, phaseName, plannedEnd);
        return R.ok();
    }

    /** 更新计划日期并后推 */
    @PutMapping("/{projectId}/timeline/planned-shift")
    public R<Void> updatePlannedAndShift(@PathVariable Long projectId,
                                         @RequestParam String phaseName,
                                         @RequestParam LocalDate plannedEnd) {
        projectTimelineService.updatePlannedAndShift(projectId, phaseName, plannedEnd);
        return R.ok();
    }
}
