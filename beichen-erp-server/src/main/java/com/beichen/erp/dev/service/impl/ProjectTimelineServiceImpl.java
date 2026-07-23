package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.mapper.ProjectTimelineMapper;
import com.beichen.erp.dev.service.ProjectTimelineService;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectTimelineServiceImpl extends ServiceImpl<ProjectTimelineMapper, ProjectTimeline> implements ProjectTimelineService {

    private final ProjectTimelineMapper projectTimelineMapper;
    private final PhaseTemplateMapper phaseTemplateMapper;
    private final ProjectMapper projectMapper;
    private final MaterialMapper materialMapper;

    @Override
    public List<ProjectTimeline> listByProject(Long projectId) {
        LambdaQueryWrapper<ProjectTimeline> wrapper = new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .orderByAsc(ProjectTimeline::getSortOrder);
        return projectTimelineMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initTimeline(Long projectId) {
        Long companyId = CompanyContext.get();
        // 删除旧的时间线
        projectTimelineMapper.delete(new LambdaQueryWrapper<ProjectTimeline>().eq(ProjectTimeline::getProjectId, projectId));
        // 获取项目的立项日期
        Project project = projectMapper.selectById(projectId);
        LocalDate startDate = project != null && project.getStartDate() != null ? project.getStartDate() : LocalDate.now();
        // 按模板生成
        List<PhaseTemplate> templates = phaseTemplateMapper.selectList(
            new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder));
        LocalDate cursor = startDate;
        int idx = 0;
        for (PhaseTemplate tpl : templates) {
            ProjectTimeline timeline = new ProjectTimeline();
            timeline.setProjectId(projectId);
            timeline.setStatusName(tpl.getName());
            timeline.setSortOrder(tpl.getSortOrder());
            timeline.setDefaultDays(tpl.getDefaultDays());
            // 第一个阶段默认为"进行中"
            timeline.setStatus(idx == 0 ? "进行中" : "未开始");
            if (tpl.getDefaultDays() != null && tpl.getDefaultDays() > 0) {
                timeline.setPlannedEnd(cursor.plusDays(tpl.getDefaultDays()));
            } else {
                timeline.setPlannedEnd(cursor);
            }
            if (companyId != null && companyId > 0) {
                timeline.setCompanyId(companyId);
            }
            projectTimelineMapper.insert(timeline);
            cursor = timeline.getPlannedEnd();
            idx++;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTimeline(Long projectId, String statusName, LocalDate actualEnd) {
        LambdaQueryWrapper<ProjectTimeline> wrapper = new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .eq(ProjectTimeline::getStatusName, statusName);
        ProjectTimeline timeline = projectTimelineMapper.selectOne(wrapper);
        if (timeline == null) {
            throw new BusinessException("时间线记录不存在");
        }
        timeline.setActualEnd(actualEnd);
        projectTimelineMapper.updateById(timeline);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlanned(Long projectId, String statusName, LocalDate plannedEnd) {
        LambdaQueryWrapper<ProjectTimeline> wrapper = new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .eq(ProjectTimeline::getStatusName, statusName);
        ProjectTimeline timeline = projectTimelineMapper.selectOne(wrapper);
        if (timeline == null) {
            throw new BusinessException("时间线记录不存在");
        }
        timeline.setPlannedEnd(plannedEnd);
        projectTimelineMapper.updateById(timeline);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlannedAndShift(Long projectId, String statusName, LocalDate plannedEnd) {
        LambdaQueryWrapper<ProjectTimeline> wrapper = new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .eq(ProjectTimeline::getStatusName, statusName);
        ProjectTimeline timeline = projectTimelineMapper.selectOne(wrapper);
        if (timeline == null) {
            throw new BusinessException("时间线记录不存在");
        }
        timeline.setPlannedEnd(plannedEnd);
        projectTimelineMapper.updateById(timeline);
        // 后推后续所有阶段
        shiftSubsequentPhases(projectId, timeline);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long projectId, String statusName, String status) {
        LambdaQueryWrapper<ProjectTimeline> wrapper = new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .eq(ProjectTimeline::getStatusName, statusName);
        ProjectTimeline timeline = projectTimelineMapper.selectOne(wrapper);
        if (timeline == null) {
            throw new BusinessException("时间线记录不存在");
        }
        // 如果设置为"进行中"，确保该项目下没有其他"进行中"的阶段
        if ("进行中".equals(status) && !"进行中".equals(timeline.getStatus())) {
            List<ProjectTimeline> phases = listByProject(projectId);
            for (ProjectTimeline p : phases) {
                if ("进行中".equals(p.getStatus()) && !p.getId().equals(timeline.getId())) {
                    // 将其他进行中的阶段自动标记为已完成（视为被跳过的阶段）
                    p.setStatus("已完成");
                    if (p.getActualEnd() == null) {
                        p.setActualEnd(LocalDate.now());
                    }
                    projectTimelineMapper.updateById(p);
                }
            }
        }
        timeline.setStatus(status);
        projectTimelineMapper.updateById(timeline);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePhase(Long projectId, Long timelineId) {
        ProjectTimeline timeline = projectTimelineMapper.selectById(timelineId);
        if (timeline == null || !timeline.getProjectId().equals(projectId)) {
            throw new BusinessException("时间线记录不存在");
        }
        if (!"进行中".equals(timeline.getStatus())) {
            throw new BusinessException("只有「进行中」的阶段才能完成");
        }
        // 完成当前阶段（如果已手动设置了实际完成时间则保留）
        if (timeline.getActualEnd() == null) {
            timeline.setActualEnd(LocalDate.now());
        }
        timeline.setStatus("已完成");
        projectTimelineMapper.updateById(timeline);

        // 查找下一个需要激活的阶段（跳过已完成的，找到第一个"未开始"）
        List<ProjectTimeline> phases = listByProject(projectId);
        ProjectTimeline nextNotStarted = null;
        boolean hasAnyInProgress = false;
        for (ProjectTimeline p : phases) {
            if (p.getSortOrder() > timeline.getSortOrder()) {
                if ("未开始".equals(p.getStatus()) && nextNotStarted == null) {
                    nextNotStarted = p;
                }
                if ("进行中".equals(p.getStatus())) {
                    hasAnyInProgress = true;
                }
            }
        }
        if (nextNotStarted != null && !hasAnyInProgress) {
            // 后续没有进行中的阶段时，才将第一个未开始阶段激活
            nextNotStarted.setStatus("进行中");
            projectTimelineMapper.updateById(nextNotStarted);
        }
        if (nextNotStarted == null && !hasAnyInProgress) {
            // 所有阶段都已处理完毕，自动结项
            Project project = projectMapper.selectById(projectId);
            if (project != null && !"结项".equals(project.getStatus())) {
                project.setStatus("结项");
                project.setActualEndDate(LocalDate.now());
                projectMapper.updateById(project);
            }
        }

        // 到达小批量及之后阶段时，同步产品状态（研发中 → 正常）
        int xiaopiliangOrder = -1;
        for (ProjectTimeline p : phases) {
            if ("小批量".equals(p.getStatusName())) {
                xiaopiliangOrder = p.getSortOrder();
                break;
            }
        }
        if (xiaopiliangOrder > 0 && timeline.getSortOrder() >= xiaopiliangOrder) {
            syncProductStatus(projectId);
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

    /** 修改阶段日期后，后推后续所有未完成的阶段 */
    private void shiftSubsequentPhases(Long projectId, ProjectTimeline updated) {
        List<ProjectTimeline> phases = listByProject(projectId);
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
            projectTimelineMapper.updateById(p);
            cursor = p.getPlannedEnd();
        }
    }
}
