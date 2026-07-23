package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.mapper.ProjectTimelineMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dev/project-timeline")
@RequiredArgsConstructor
public class ProjectTimelineController {

    private final ProjectTimelineMapper timelineMapper;
    private final PhaseTemplateMapper templateMapper;
    private final ProjectMapper projectMapper;
    private final MaterialMapper materialMapper;

    @GetMapping("/list")
    public R<List<ProjectTimeline>> list(@RequestParam Long projectId) {
        return R.ok(timelineMapper.selectList(
            new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .orderByAsc(ProjectTimeline::getSortOrder)));
    }

    @GetMapping("/regenerate/{projectId}")
    @Transactional
    public R<List<ProjectTimeline>> regenerate(@PathVariable Long projectId, @RequestParam String startDate) {
        LocalDate start = LocalDate.parse(startDate);
        return R.ok(regeneratePhases(projectId, start));
    }

    @PutMapping
    @Transactional
    public R<List<ProjectTimeline>> updatePhase(@RequestBody ProjectTimeline t) {
        ProjectTimeline phase = timelineMapper.selectById(t.getId());
        if (phase == null) throw new BusinessException("阶段不存在");
        // 更新当前阶段的 planned_end
        if (t.getPlannedEnd() != null) phase.setPlannedEnd(t.getPlannedEnd());
        if (t.getActualEnd() != null) phase.setActualEnd(t.getActualEnd());
        if (t.getStatus() != null) phase.setStatus(t.getStatus());
        timelineMapper.updateById(phase);
        // 后推后续所有阶段
        shiftSubsequentPhases(phase.getProjectId(), phase);
        // 同步项目主状态为当前进行中的阶段名
        syncProjectStatusFromTimeline(phase.getProjectId());
        // 重新加载完整时间线
        return R.ok(timelineMapper.selectList(
            new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, phase.getProjectId())
                .orderByAsc(ProjectTimeline::getSortOrder)));
    }

    /** 完成阶段：自动填actualEnd，下一阶段变为"进行中"，最后阶段则自动结项。小批量及之后阶段同步产品状态 */
    @PostMapping("/complete/{id}")
    @Transactional
    public R<List<ProjectTimeline>> completePhase(@PathVariable Long id) {
        ProjectTimeline phase = timelineMapper.selectById(id);
        if (phase == null) throw new BusinessException("阶段不存在");
        if (!"进行中".equals(phase.getStatus())) throw new BusinessException("只有「进行中」的阶段才能完成");

        // 完成当前阶段（如果已手动设置了实际完成时间则保留）
        if (phase.getActualEnd() == null) {
            phase.setActualEnd(LocalDate.now());
        }
        phase.setStatus("已完成");
        timelineMapper.updateById(phase);

        Long projectId = phase.getProjectId();
        // 查找下一个需要激活的阶段（跳过已完成的，找到第一个"未开始"）
        List<ProjectTimeline> phases = timelineMapper.selectList(
            new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .orderByAsc(ProjectTimeline::getSortOrder));
        log.info("[completePhase] 当前完成阶段: id={}, name={}, sortOrder={}", id, phase.getStatusName(), phase.getSortOrder());
        ProjectTimeline nextNotStarted = null;
        boolean hasAnyInProgress = false;
        for (ProjectTimeline p : phases) {
            if (p.getSortOrder() > phase.getSortOrder()) {
                log.info("[completePhase] 检查后续阶段: name={}, sortOrder={}, status={}", p.getStatusName(), p.getSortOrder(), p.getStatus());
                if ("未开始".equals(p.getStatus()) && nextNotStarted == null) {
                    nextNotStarted = p;
                    log.info("[completePhase]   → 找到第一个未开始阶段: {}", p.getStatusName());
                }
                if ("进行中".equals(p.getStatus())) {
                    hasAnyInProgress = true;
                    log.info("[completePhase]   → 发现已有进行中的阶段: {}", p.getStatusName());
                }
            }
        }
        log.info("[completePhase] nextNotStarted={}, hasAnyInProgress={}", 
            nextNotStarted != null ? nextNotStarted.getStatusName() : "null", hasAnyInProgress);
        if (nextNotStarted != null && !hasAnyInProgress) {
            // 后续没有进行中的阶段时，才将第一个未开始阶段激活
            log.info("[completePhase] → 激活阶段: {}", nextNotStarted.getStatusName());
            nextNotStarted.setStatus("进行中");
            timelineMapper.updateById(nextNotStarted);
        }
        if (nextNotStarted == null && !hasAnyInProgress) {
            // 所有阶段都已处理完毕，自动结项
            log.info("[completePhase] → 所有阶段已完成，自动结项");
            Project project = projectMapper.selectById(projectId);
            if (project != null && !"结项".equals(project.getStatus())) {
                project.setStatus("结项");
                project.setActualEndDate(LocalDate.now());
                projectMapper.updateById(project);
            }
        }

        // 重新计算后续阶段的计划完成时间（以当前阶段实际完成时间为起点，级联累加默认天数）
        recalcSubsequentPlannedEnds(projectId, phase);

        // 同步项目主状态为当前进行中的阶段名
        syncProjectStatusFromTimeline(projectId);

        // 到达小批量及之后阶段时，同步产品状态（研发中 → 正常）
        int xiaopiliangOrder = -1;
        for (ProjectTimeline p : phases) {
            if ("小批量".equals(p.getStatusName())) {
                xiaopiliangOrder = p.getSortOrder();
                break;
            }
        }
        if (xiaopiliangOrder > 0 && phase.getSortOrder() >= xiaopiliangOrder) {
            syncProductStatus(projectId);
        }

        // 重新加载完整时间线
        return R.ok(timelineMapper.selectList(
            new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .orderByAsc(ProjectTimeline::getSortOrder)));
    }

    /** 以已完成阶段的 actualEnd 为起点，级联重算后续未完成阶段的 plannedEnd */
    private void recalcSubsequentPlannedEnds(Long projectId, ProjectTimeline completed) {
        List<ProjectTimeline> phases = timelineMapper.selectList(
            new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .orderByAsc(ProjectTimeline::getSortOrder));
        LocalDate cursor = completed.getActualEnd();
        for (ProjectTimeline p : phases) {
            if (p.getSortOrder() <= completed.getSortOrder()) continue;
            if ("已完成".equals(p.getStatus())) {
                // 已完成阶段：以其实际完成时间作为后续的锚点
                cursor = p.getActualEnd() != null ? p.getActualEnd() : cursor;
            } else {
                int days = p.getDefaultDays() != null && p.getDefaultDays() > 0 ? p.getDefaultDays() : 0;
                p.setPlannedEnd(cursor.plusDays(days));
                timelineMapper.updateById(p);
                cursor = p.getPlannedEnd();
            }
        }
    }

    /** 同步产品状态：将关联产品从"研发中"改为"正常" */
    private void syncProductStatus(Long projectId) {
        Material linked = materialMapper.selectOne(
            new LambdaQueryWrapper<Material>()
                .eq(Material::getProjectId, projectId)
                .eq(Material::getStatus, "研发中")
                .last("LIMIT 1"));
        if (linked != null) {
            linked.setStatus("正常");
            materialMapper.updateById(linked);
        }
    }

    /** 将项目主状态同步为时间线中当前进行中的阶段名 */
    private void syncProjectStatusFromTimeline(Long projectId) {
        List<ProjectTimeline> phases = timelineMapper.selectList(
            new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .orderByAsc(ProjectTimeline::getSortOrder));
        // 找当前进行中的阶段
        for (ProjectTimeline p : phases) {
            if ("进行中".equals(p.getStatus())) {
                updateProjectStatusIfChanged(projectId, p.getStatusName());
                return;
            }
        }
        // 没有进行中的阶段，找最后一个已完成的
        for (int i = phases.size() - 1; i >= 0; i--) {
            if ("已完成".equals(phases.get(i).getStatus())) {
                updateProjectStatusIfChanged(projectId, phases.get(i).getStatusName());
                return;
            }
        }
    }

    private void updateProjectStatusIfChanged(Long projectId, String newStatus) {
        Project project = projectMapper.selectById(projectId);
        if (project != null && !newStatus.equals(project.getStatus())) {
            project.setStatus(newStatus);
            projectMapper.updateById(project);
        }
    }

    /** 从模板重新生成所有阶段（按开始日期+累计天数） */
    private List<ProjectTimeline> regeneratePhases(Long projectId, LocalDate startDate) {
        // 删除旧的
        timelineMapper.delete(new LambdaQueryWrapper<ProjectTimeline>().eq(ProjectTimeline::getProjectId, projectId));
        // 加载模板
        List<PhaseTemplate> templates = templateMapper.selectList(
            new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder));
        // 生成
        LocalDate cursor = startDate;
        Long cid = CompanyContext.get();
        int idx = 0;
        for (PhaseTemplate tpl : templates) {
            ProjectTimeline p = new ProjectTimeline();
            p.setProjectId(projectId);
            p.setStatusName(tpl.getName());
            p.setSortOrder(tpl.getSortOrder());
            p.setDefaultDays(tpl.getDefaultDays());
            p.setStatus(idx == 0 ? "进行中" : "未开始");
            if (tpl.getDefaultDays() != null && tpl.getDefaultDays() > 0) {
                p.setPlannedEnd(cursor.plusDays(tpl.getDefaultDays()));
            } else {
                p.setPlannedEnd(cursor);
            }
            if (cid != null && cid > 0) p.setCompanyId(cid);
            timelineMapper.insert(p);
            cursor = p.getPlannedEnd();
            idx++;
        }
        return timelineMapper.selectList(
            new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .orderByAsc(ProjectTimeline::getSortOrder));
    }

    /** 修改阶段日期后，后推后续所有未完成的阶段 */
    private void shiftSubsequentPhases(Long projectId, ProjectTimeline updated) {
        List<ProjectTimeline> phases = timelineMapper.selectList(
            new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .orderByAsc(ProjectTimeline::getSortOrder));
        LocalDate cursor = updated.getPlannedEnd();
        for (ProjectTimeline p : phases) {
            if (p.getSortOrder() <= updated.getSortOrder()) continue;
            // 跳过已完成的阶段，避免覆盖历史数据
            if ("已完成".equals(p.getStatus())) {
                cursor = p.getPlannedEnd() != null ? p.getPlannedEnd() : cursor;
                continue;
            }
            if (p.getDefaultDays() != null && p.getDefaultDays() > 0) {
                p.setPlannedEnd(cursor.plusDays(p.getDefaultDays()));
            } else {
                p.setPlannedEnd(cursor);
            }
            timelineMapper.updateById(p);
            cursor = p.getPlannedEnd();
        }
    }
}
