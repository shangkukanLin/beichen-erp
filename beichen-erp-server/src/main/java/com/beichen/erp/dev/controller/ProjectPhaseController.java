package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.ProjectPhase;
import com.beichen.erp.dev.service.ProjectPhaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class ProjectPhaseController {

    private final ProjectPhaseService projectPhaseService;

    /** 获取项目项目阶段 */
    @GetMapping("/{projectId}/phase")
    public R<List<ProjectPhase>> list(@PathVariable Long projectId) {
        return R.ok(projectPhaseService.listByProject(projectId));
    }

    /** 级联重算所有未开始阶段的计划日期（必须在 /phase/{id} 之前，避免 recalc 被当成 id） */
    @PutMapping("/phase/recalc")
    public R<Void> recalc(@RequestParam Long projectId) {
        projectPhaseService.recalcAllPlannedEnds(projectId);
        return R.ok();
    }

    /** 保存项目阶段行（含状态变更+日期后推） */
    @PutMapping("/phase/{id}")
    public R<Void> saveRow(@PathVariable Long id, @RequestBody ProjectPhase row) {
        row.setId(id);
        projectPhaseService.savePhaseRow(row.getProjectId(), row);
        return R.ok();
    }

    /** 完成阶段 */
    @PutMapping("/phase/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        ProjectPhase tl = projectPhaseService.getById(id);
        if (tl == null) return R.fail("项目阶段记录不存在");
        projectPhaseService.completePhase(tl.getProjectId(), id);
        return R.ok();
    }

    /**
     * 一键完成：把本项目**所有未完成阶段**（未开始 / 进行中）一次性标记为已完成。
     * <p>2026-09-21 新增（用户需求）：项目有 14 个阶段，原先只能逐个点「完成」。
     * 在同一事务内批量推进，终态与逐个点击一致 —— 全部完成后**项目自动结项**。</p>
     *
     * @return 本次实际完成的阶段数（0 表示本来就都完成了）
     */
    @PutMapping("/{projectId}/phase/complete-all")
    public R<Integer> completeAll(@PathVariable Long projectId) {
        return R.ok(projectPhaseService.completeAllPhases(projectId));
    }

    /** 跳过阶段 */
    @PutMapping("/phase/{id}/skip")
    public R<Void> skip(@PathVariable Long id) {
        ProjectPhase tl = projectPhaseService.getById(id);
        if (tl == null) return R.fail("项目阶段记录不存在");
        projectPhaseService.skipPhase(tl.getProjectId(), id);
        return R.ok();
    }

    /** 撤销阶段：将已完成/已跳过的阶段恢复为进行中，后续阶段重置 */
    @PutMapping("/phase/{id}/revert")
    public R<Void> revert(@PathVariable Long id) {
        ProjectPhase tl = projectPhaseService.getById(id);
        if (tl == null) return R.fail("项目阶段记录不存在");
        projectPhaseService.revertPhase(tl.getProjectId(), id);
        return R.ok();
    }

    /** 更新计划日期 */
    @PutMapping("/{projectId}/phase/planned")
    public R<Void> updatePlanned(@PathVariable Long projectId,
                                 @RequestParam String phaseName,
                                 @RequestParam LocalDate plannedEnd) {
        projectPhaseService.updatePlanned(projectId, phaseName, plannedEnd);
        return R.ok();
    }

    /** 更新计划日期并后推 */
    @PutMapping("/{projectId}/phase/planned-shift")
    public R<Void> updatePlannedAndShift(@PathVariable Long projectId,
                                         @RequestParam String phaseName,
                                         @RequestParam LocalDate plannedEnd) {
        projectPhaseService.updatePlannedAndShift(projectId, phaseName, plannedEnd);
        return R.ok();
    }
}
