package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.common.ProjectStatus;
import com.beichen.erp.dev.common.TimelineStatus;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.mapper.ProjectTimelineMapper;
import com.beichen.erp.dev.service.ProjectService;
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
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    private final ProjectMapper projectMapper;
    private final PhaseTemplateMapper phaseTemplateMapper;
    private final ProjectTimelineMapper projectTimelineMapper;

    @Override
    public Page<Project> page(PageParam param, String keyword, String status) {
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(wr -> wr.like(Project::getName, keyword)
                    .or().like(Project::getCode, keyword)
                    .or().like(Project::getAssemblyName, keyword));
        }
        if (status != null && !status.isBlank()) {
            w.eq(Project::getStatus, status);
        }
        w.orderByDesc(Project::getId);
        return projectMapper.selectPage(new Page<>(param.getPageNum(), param.getPageSize()), w);
    }

    @Override
    @Transactional
    public Project create(Project project) {
        project.setCode(generateProjectCode());
        project.setStatus(ProjectStatus.IN_PROGRESS.getCode());
        project.setCreateTime(LocalDateTime.now());
        projectMapper.insert(project);

        // 创建时间线，第一个阶段自动激活
        createTimelinesFromTemplate(project.getId());
        return project;
    }

    @Override
    @Transactional
    public void cancel(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) return;
        project.setStatus(ProjectStatus.CANCELLED.getCode());
        project.setCancelledAt(LocalDateTime.now());
        projectMapper.updateById(project);
        log.info("项目已取消: projectId={}", projectId);
    }

    @Override
    @Transactional
    public void reactivate(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) return;
        project.setStatus(ProjectStatus.IN_PROGRESS.getCode());
        project.setCancelledAt(null);
        projectMapper.updateById(project);
        log.info("项目已重新激活: projectId={}", projectId);
    }

    @Override
    public List<Project> listByStatus(String status) {
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        w.eq(Project::getStatus, status);
        return projectMapper.selectList(w);
    }

    // ===== 内部方法 =====

    private String generateProjectCode() {
        String date = LocalDate.now().toString().replace("-", "").substring(2);
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        w.likeRight(Project::getCode, "DEV-" + date);
        long count = projectMapper.selectCount(w);
        return "DEV-" + date + "-" + String.format("%03d", count + 1);
    }

    private void createTimelinesFromTemplate(Long projectId) {
        List<PhaseTemplate> templates = phaseTemplateMapper.selectList(
                new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder));
        for (int i = 0; i < templates.size(); i++) {
            PhaseTemplate tpl = templates.get(i);
            ProjectTimeline tl = new ProjectTimeline();
            tl.setProjectId(projectId);
            tl.setStatusName(tpl.getName());
            tl.setDefaultDays(tpl.getDefaultDays());
            tl.setSortOrder(tpl.getSortOrder());
            // 第一个阶段自动激活
            tl.setStatus(i == 0 ? TimelineStatus.IN_PROGRESS.getCode() : TimelineStatus.NOT_STARTED.getCode());
            tl.setCreateTime(LocalDateTime.now());
            projectTimelineMapper.insert(tl);
        }
    }
}
