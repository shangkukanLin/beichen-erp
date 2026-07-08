package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.mapper.ProjectTimelineMapper;
import com.beichen.erp.dev.service.ProjectTimelineService;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectTimelineServiceImpl extends ServiceImpl<ProjectTimelineMapper, ProjectTimeline> implements ProjectTimelineService {

    private static final List<String> STATUS_LIST = List.of(
            "立项", "排线图纸", "排线打样", "FOG打样", "显示调试",
            "触摸调试", "背贴盖板打样", "总成样品", "测试", "小批量", "结项"
    );

    private final ProjectTimelineMapper projectTimelineMapper;

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
        for (int i = 0; i < STATUS_LIST.size(); i++) {
            ProjectTimeline timeline = new ProjectTimeline();
            timeline.setProjectId(projectId);
            timeline.setStatusName(STATUS_LIST.get(i));
            timeline.setSortOrder(i + 1);
            timeline.setPlannedEnd(null);
            timeline.setActualEnd(null);
            timeline.setStatus("未完成");
            if (companyId != null && companyId > 0) {
                timeline.setCompanyId(companyId);
            }
            projectTimelineMapper.insert(timeline);
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
    public void updateStatus(Long projectId, String statusName, String status) {
        LambdaQueryWrapper<ProjectTimeline> wrapper = new LambdaQueryWrapper<ProjectTimeline>()
                .eq(ProjectTimeline::getProjectId, projectId)
                .eq(ProjectTimeline::getStatusName, statusName);
        ProjectTimeline timeline = projectTimelineMapper.selectOne(wrapper);
        if (timeline == null) {
            throw new BusinessException("时间线记录不存在");
        }
        timeline.setStatus(status);
        projectTimelineMapper.updateById(timeline);
    }
}
