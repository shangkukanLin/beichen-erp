package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.common.ProjectStatus;
import com.beichen.erp.dev.common.PhaseStatus;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectPhase;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.mapper.ProjectPhaseMapper;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import com.beichen.erp.dev.service.ProjectPhaseService;
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
public class ProjectPhaseServiceImpl extends ServiceImpl<ProjectPhaseMapper, ProjectPhase>
        implements ProjectPhaseService {

    private final ProjectPhaseMapper projectPhaseMapper;
    private final PhaseTemplateMapper phaseTemplateMapper;
    private final ProjectMapper projectMapper;
    private final ProjectProductSyncService projectProductSyncService;

    @Override
    public List<ProjectPhase> listByProject(Long projectId) {
        return projectPhaseMapper.selectList(
                new LambdaQueryWrapper<ProjectPhase>()
                        .eq(ProjectPhase::getProjectId, projectId)
                        .orderByAsc(ProjectPhase::getSortOrder));
    }

    @Override
    @Transactional
    public void initPhase(Long projectId) {
        List<PhaseTemplate> templates = phaseTemplateMapper.selectList(
                new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder));
        LocalDate today = LocalDate.now();
        for (int i = 0; i < templates.size(); i++) {
            PhaseTemplate tpl = templates.get(i);
            ProjectPhase tl = new ProjectPhase();
            tl.setProjectId(projectId);
            tl.setPhaseName(tpl.getName());
            tl.setDefaultDays(tpl.getDefaultDays());
            tl.setSortOrder(tpl.getSortOrder());
            tl.setStatus(i == 0 ? PhaseStatus.IN_PROGRESS.getCode() : PhaseStatus.NOT_STARTED.getCode());
            // 立项阶段（第一个阶段）默认计划完成日期为创建当天
            if (i == 0) {
                tl.setPlannedEnd(today);
            }
            tl.setCreateTime(LocalDateTime.now());
            projectPhaseMapper.insert(tl);
        }
    }

    @Override
    @Transactional
    public void completePhase(Long projectId, Long phaseId) {
        ProjectPhase current = projectPhaseMapper.selectById(phaseId);
        if (current == null) return;
        // 已取消项目不允许再推进阶段，避免阶段状态与项目状态脱节
        if (isProjectCancelled(projectId)) {
            log.warn("项目已取消，禁止推进阶段: projectId={}, phaseId={}", projectId, phaseId);
            return;
        }

        current.setStatus(PhaseStatus.FINISHED.getCode());
        // 保留用户设置的实际完成日期，未设置则默认当天
        if (current.getActualEnd() == null) {
            current.setActualEnd(LocalDate.now());
        }
        projectPhaseMapper.updateById(current);

        checkProductStatusSync(current.getPhaseName(), projectId);
        activateNextPhase(projectId, current.getSortOrder());
        syncProjectStatus(projectId);
    }

    @Override
    @Transactional
    public void skipPhase(Long projectId, Long phaseId) {
        ProjectPhase current = projectPhaseMapper.selectById(phaseId);
        if (current == null) return;
        // 已取消项目不允许再推进阶段
        if (isProjectCancelled(projectId)) {
            log.warn("项目已取消，禁止推进阶段: projectId={}, phaseId={}", projectId, phaseId);
            return;
        }

        current.setStatus(PhaseStatus.SKIPPED.getCode());
        if (current.getActualEnd() == null) {
            current.setActualEnd(LocalDate.now());
        }
        projectPhaseMapper.updateById(current);

        checkProductStatusSync(current.getPhaseName(), projectId);
        activateNextPhase(projectId, current.getSortOrder());
        syncProjectStatus(projectId);
    }

    @Override
    @Transactional
    public void revertPhase(Long projectId, Long phaseId) {
        ProjectPhase current = projectPhaseMapper.selectById(phaseId);
        if (current == null) return;

        String oldStatus = current.getStatus();
        // 只有已完成或已跳过的阶段才能撤销
        if (!PhaseStatus.FINISHED.getCode().equals(oldStatus)
                && !PhaseStatus.SKIPPED.getCode().equals(oldStatus)) {
            log.warn("阶段状态不允许撤销: projectId={}, phaseId={}, status={}", projectId, phaseId, oldStatus);
            return;
        }

        // 1. 将当前阶段恢复为进行中
        current.setStatus(PhaseStatus.IN_PROGRESS.getCode());
        current.setActualEnd(null);
        projectPhaseMapper.updateById(current);

        // 2. 将排序在当前之后的所有阶段重置为未开始，清空实际完成日期
        List<ProjectPhase> all = listByProject(projectId);
        for (ProjectPhase t : all) {
            if (t.getSortOrder() > current.getSortOrder()) {
                t.setStatus(PhaseStatus.NOT_STARTED.getCode());
                t.setActualEnd(null);
                projectPhaseMapper.updateById(t);
            }
        }

        // 3. 如果项目已结项，恢复为进行中
        Project project = projectMapper.selectById(projectId);
        if (project != null && ProjectStatus.CLOSED.getCode().equals(project.getStatus())) {
            project.setStatus(ProjectStatus.IN_PROGRESS.getCode());
            projectMapper.updateById(project);
            log.info("项目撤销结项: projectId={}", projectId);
        }
    }

    @Override
    @Transactional
    public void recalcAllPlannedEnds(Long projectId) {
        List<ProjectPhase> all = listByProject(projectId);
        if (all.isEmpty()) return;

        // 找到第一个进行中的阶段，作为推算起点
        ProjectPhase inProgress = null;
        for (ProjectPhase t : all) {
            if (PhaseStatus.IN_PROGRESS.getCode().equals(t.getStatus())) {
                inProgress = t;
                break;
            }
        }
        if (inProgress == null) {
            // 没有进行中的阶段，不做重算
            return;
        }

        // 进行中阶段基准：实际完成日期优先，否则今天
        LocalDate baseDate = inProgress.getActualEnd() != null ? inProgress.getActualEnd() : LocalDate.now();
        // 进行中阶段自身计划完成日期为空时回填基准
        if (inProgress.getPlannedEnd() == null) {
            inProgress.setPlannedEnd(baseDate);
            projectPhaseMapper.updateById(inProgress);
        }

        // 顺序推算后续阶段，跳过已完成/已跳过的阶段（其计划日期为历史快照不应改写）
        boolean found = false;
        for (ProjectPhase next : all) {
            if (!found) {
                // 先定位到进行中阶段之后再开始推算
                if (next.getId().equals(inProgress.getId())) found = true;
                continue;
            }
            if (PhaseStatus.FINISHED.getCode().equals(next.getStatus())
                    || PhaseStatus.SKIPPED.getCode().equals(next.getStatus())) {
                // 历史阶段保持原计划日期，仅刷新基准为它的结束，保证后续推算连续
                if (next.getPlannedEnd() != null) baseDate = next.getPlannedEnd();
                continue;
            }
            int days = next.getDefaultDays() != null ? next.getDefaultDays() : 7;
            next.setPlannedEnd(baseDate.plusDays(days));
            projectPhaseMapper.updateById(next);
            baseDate = next.getPlannedEnd();
        }
    }

    @Override
    @Transactional
    public void savePhaseRow(Long projectId, ProjectPhase row) {
        ProjectPhase existing = projectPhaseMapper.selectById(row.getId());
        if (existing == null) return;
        // 已取消项目不允许通过手动编辑阶段推进状态
        if (isProjectCancelled(projectId)) {
            log.warn("项目已取消，禁止编辑阶段: projectId={}, phaseId={}", projectId, row.getId());
            return;
        }

        String oldStatus = existing.getStatus();
        String newStatus = row.getStatus();
        boolean statusChanged = !oldStatus.equals(newStatus);

        existing.setStatus(newStatus);
        existing.setPlannedEnd(row.getPlannedEnd());
        existing.setActualEnd(row.getActualEnd());
        existing.setRemark(row.getRemark());
        // 状态变为完成/跳过且未设实际日期时，默认当天
        if ((PhaseStatus.FINISHED.getCode().equals(newStatus) || PhaseStatus.SKIPPED.getCode().equals(newStatus))
                && existing.getActualEnd() == null) {
            existing.setActualEnd(LocalDate.now());
        }
        projectPhaseMapper.updateById(existing);

        if (statusChanged) {
            if (PhaseStatus.FINISHED.getCode().equals(newStatus)
                    || PhaseStatus.SKIPPED.getCode().equals(newStatus)) {
                checkProductStatusSync(existing.getPhaseName(), projectId);
                activateNextPhase(projectId, existing.getSortOrder());
                syncProjectStatus(projectId);
            }
        }
    }

    @Override
    @Transactional
    public void updatePlanned(Long projectId, String phaseName, LocalDate plannedEnd) {
        ProjectPhase tl = findByPhaseName(projectId, phaseName);
        if (tl != null) {
            tl.setPlannedEnd(plannedEnd);
            projectPhaseMapper.updateById(tl);
        }
    }

    @Override
    @Transactional
    public void updatePlannedAndShift(Long projectId, String phaseName, LocalDate plannedEnd) {
        ProjectPhase tl = findByPhaseName(projectId, phaseName);
        if (tl == null) return;

        // 原计划完成日期为空时（非首阶段默认不填），以今天为基准计算偏移，避免空指针
        LocalDate oldPlannedEnd = tl.getPlannedEnd() != null ? tl.getPlannedEnd() : LocalDate.now();
        long daysDiff = plannedEnd.toEpochDay() - oldPlannedEnd.toEpochDay();
        tl.setPlannedEnd(plannedEnd);
        projectPhaseMapper.updateById(tl);

        List<ProjectPhase> all = listByProject(projectId);
        boolean found = false;
        for (ProjectPhase t : all) {
            if (found && t.getPlannedEnd() != null) {
                t.setPlannedEnd(t.getPlannedEnd().plusDays(daysDiff));
                projectPhaseMapper.updateById(t);
            }
            if (t.getId().equals(tl.getId())) found = true;
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long projectId, String phaseName, String status) {
        ProjectPhase tl = findByPhaseName(projectId, phaseName);
        if (tl != null) {
            tl.setStatus(status);
            projectPhaseMapper.updateById(tl);
        }
    }

    // ===== 内部方法 =====

    /** 判断项目是否已取消（取消后阶段不可再推进/编辑） */
    private boolean isProjectCancelled(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        return project != null && project.getCancelledAt() != null;
    }

    private ProjectPhase findByPhaseName(Long projectId, String phaseName) {
        return projectPhaseMapper.selectOne(
                new LambdaQueryWrapper<ProjectPhase>()
                        .eq(ProjectPhase::getProjectId, projectId)
                        .eq(ProjectPhase::getPhaseName, phaseName));
    }

    private void activateNextPhase(Long projectId, int currentSortOrder) {
        List<ProjectPhase> all = listByProject(projectId);
        for (ProjectPhase t : all) {
            if (t.getSortOrder() > currentSortOrder
                    && PhaseStatus.NOT_STARTED.getCode().equals(t.getStatus())) {
                t.setStatus(PhaseStatus.IN_PROGRESS.getCode());
                if (t.getPlannedEnd() == null) {
                    t.setPlannedEnd(LocalDate.now().plusDays(t.getDefaultDays() != null ? t.getDefaultDays() : 7));
                }
                projectPhaseMapper.updateById(t);
                break;
            }
        }
    }

    /** 从项目阶段推导项目状态 */
    private void syncProjectStatus(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getCancelledAt() != null) return;

        List<ProjectPhase> all = listByProject(projectId);
        boolean allDone = all.stream().allMatch(t ->
                PhaseStatus.FINISHED.getCode().equals(t.getStatus())
                        || PhaseStatus.SKIPPED.getCode().equals(t.getStatus()));
        if (allDone && !ProjectStatus.CLOSED.getCode().equals(project.getStatus())) {
            project.setStatus(ProjectStatus.CLOSED.getCode());
            projectMapper.updateById(project);
            log.info("项目自动结项: projectId={}", projectId);
            // 结项时同步关联产品状态（研发中→正常），避免产品停留在研发中
            projectProductSyncService.syncProductStatus(projectId);
        }
    }

    private void checkProductStatusSync(String phaseName, Long projectId) {
        PhaseTemplate tpl = phaseTemplateMapper.selectOne(
                new LambdaQueryWrapper<PhaseTemplate>()
                        .eq(PhaseTemplate::getName, phaseName));
        if (tpl != null && PhaseTemplate.SYNC_PRODUCT_STATUS == valueOf(tpl.getProductStatusSync())) {
            projectProductSyncService.syncProductStatus(projectId);
        }
    }

    /** 将可能为 null 的 Integer 规整为语义值，避免 null 拆箱与魔法值比较 */
    private static int valueOf(Integer v) {
        return v == null ? 0 : v;
    }
}
