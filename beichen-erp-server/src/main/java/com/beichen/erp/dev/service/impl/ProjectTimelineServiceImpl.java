package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.common.ProjectStatus;
import com.beichen.erp.dev.common.TimelineStatus;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.mapper.ProjectTimelineMapper;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import com.beichen.erp.dev.service.ProjectTimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectTimelineServiceImpl extends ServiceImpl<ProjectTimelineMapper, ProjectTimeline>
        implements ProjectTimelineService {

    private final ProjectTimelineMapper projectTimelineMapper;
    private final PhaseTemplateMapper phaseTemplateMapper;
    private final ProjectMapper projectMapper;
    private final ProjectProductSyncService projectProductSyncService;

    @Override
    public List<ProjectTimeline> listByProject(Long projectId) {
        return projectTimelineMapper.selectList(
                new LambdaQueryWrapper<ProjectTimeline>()
                        .eq(ProjectTimeline::getProjectId, projectId)
                        .orderByAsc(ProjectTimeline::getSortOrder));
    }

    @Override
    @Transactional
    public void initTimeline(Long projectId) {
        List<PhaseTemplate> templates = phaseTemplateMapper.selectList(
                new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder));
        for (int i = 0; i < templates.size(); i++) {
            PhaseTemplate tpl = templates.get(i);
            ProjectTimeline tl = new ProjectTimeline();
            tl.setProjectId(projectId);
            tl.setStatusName(tpl.getName());
            tl.setDefaultDays(tpl.getDefaultDays());
            tl.setSortOrder(tpl.getSortOrder());
            tl.setStatus(i == 0 ? TimelineStatus.IN_PROGRESS.getCode() : TimelineStatus.NOT_STARTED.getCode());
            tl.setCreateTime(LocalDateTime.now());
            projectTimelineMapper.insert(tl);
        }
    }

    @Override
    @Transactional
    public void completePhase(Long projectId, Long timelineId) {
        ProjectTimeline current = projectTimelineMapper.selectById(timelineId);
        if (current == null) return;

        current.setStatus(TimelineStatus.FINISHED.getCode());
        current.setActualEnd(LocalDate.now());
        projectTimelineMapper.updateById(current);

        checkProductStatusSync(current.getStatusName(), projectId);
        activateNextPhase(projectId, current.getSortOrder());
        syncProjectStatus(projectId);
    }

    @Override
    @Transactional
    public void skipPhase(Long projectId, Long timelineId) {
        ProjectTimeline current = projectTimelineMapper.selectById(timelineId);
        if (current == null) return;

        current.setStatus(TimelineStatus.SKIPPED.getCode());
        current.setActualEnd(LocalDate.now());
        projectTimelineMapper.updateById(current);

        checkProductStatusSync(current.getStatusName(), projectId);
        activateNextPhase(projectId, current.getSortOrder());
        syncProjectStatus(projectId);
    }

    @Override
    @Transactional
    public void saveTimelineRow(Long projectId, ProjectTimeline row) {
        ProjectTimeline existing = projectTimelineMapper.selectById(row.getId());
        if (existing == null) return;

        String oldStatus = existing.getStatus();
        String newStatus = row.getStatus();
        boolean statusChanged = !oldStatus.equals(newStatus);

        existing.setStatus(newStatus);
        existing.setPlannedEnd(row.getPlannedEnd());
        existing.setActualEnd(row.getActualEnd());
        projectTimelineMapper.updateById(existing);

        if (statusChanged) {
            if (TimelineStatus.FINISHED.getCode().equals(newStatus)
                    || TimelineStatus.SKIPPED.getCode().equals(newStatus)) {
                checkProductStatusSync(existing.getStatusName(), projectId);
                activateNextPhase(projectId, existing.getSortOrder());
                syncProjectStatus(projectId);
            }
        }
    }

    @Override
    @Transactional
    public void updatePlanned(Long projectId, String statusName, LocalDate plannedEnd) {
        ProjectTimeline tl = findByStatusName(projectId, statusName);
        if (tl != null) {
            tl.setPlannedEnd(plannedEnd);
            projectTimelineMapper.updateById(tl);
        }
    }

    @Override
    @Transactional
    public void updatePlannedAndShift(Long projectId, String statusName, LocalDate plannedEnd) {
        ProjectTimeline tl = findByStatusName(projectId, statusName);
        if (tl == null) return;

        long daysDiff = plannedEnd.toEpochDay() - tl.getPlannedEnd().toEpochDay();
        tl.setPlannedEnd(plannedEnd);
        projectTimelineMapper.updateById(tl);

        List<ProjectTimeline> all = listByProject(projectId);
        boolean found = false;
        for (ProjectTimeline t : all) {
            if (found && t.getPlannedEnd() != null) {
                t.setPlannedEnd(t.getPlannedEnd().plusDays(daysDiff));
                projectTimelineMapper.updateById(t);
            }
            if (t.getId().equals(tl.getId())) found = true;
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long projectId, String statusName, String status) {
        ProjectTimeline tl = findByStatusName(projectId, statusName);
        if (tl != null) {
            tl.setStatus(status);
            projectTimelineMapper.updateById(tl);
        }
    }

    // ===== 内部方法 =====

    private ProjectTimeline findByStatusName(Long projectId, String statusName) {
        return projectTimelineMapper.selectOne(
                new LambdaQueryWrapper<ProjectTimeline>()
                        .eq(ProjectTimeline::getProjectId, projectId)
                        .eq(ProjectTimeline::getStatusName, statusName));
    }

    private void activateNextPhase(Long projectId, int currentSortOrder) {
        List<ProjectTimeline> all = listByProject(projectId);
        for (ProjectTimeline t : all) {
            if (t.getSortOrder() > currentSortOrder
                    && TimelineStatus.NOT_STARTED.getCode().equals(t.getStatus())) {
                t.setStatus(TimelineStatus.IN_PROGRESS.getCode());
                if (t.getPlannedEnd() == null) {
                    t.setPlannedEnd(LocalDate.now().plusDays(t.getDefaultDays() != null ? t.getDefaultDays() : 7));
                }
                projectTimelineMapper.updateById(t);
                break;
            }
        }
    }

    /** 从时间线推导项目状态 */
    private void syncProjectStatus(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getCancelledAt() != null) return;

        List<ProjectTimeline> all = listByProject(projectId);
        boolean allDone = all.stream().allMatch(t ->
                TimelineStatus.FINISHED.getCode().equals(t.getStatus())
                        || TimelineStatus.SKIPPED.getCode().equals(t.getStatus()));
        if (allDone && !ProjectStatus.CLOSED.getCode().equals(project.getStatus())) {
            project.setStatus(ProjectStatus.CLOSED.getCode());
            projectMapper.updateById(project);
            log.info("项目自动结项: projectId={}", projectId);
        }
    }

    private void checkProductStatusSync(String statusName, Long projectId) {
        PhaseTemplate tpl = phaseTemplateMapper.selectOne(
                new LambdaQueryWrapper<PhaseTemplate>()
                        .eq(PhaseTemplate::getName, statusName));
        if (tpl != null && tpl.getProductStatusSync() != null && tpl.getProductStatusSync() == 1) {
            projectProductSyncService.syncProductStatus(projectId);
        }
    }
}
