package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.ProjectTimeline;

import java.time.LocalDate;
import java.util.List;

public interface ProjectTimelineService extends IService<ProjectTimeline> {

    /** 获取项目时间线列表 */
    List<ProjectTimeline> listByProject(Long projectId);

    /** 初始化项目时间线（从模板复制） */
    void initTimeline(Long projectId);

    /** 完成阶段：自动填actualEnd，下一阶段变为"进行中"，最后阶段则自动结项 */
    void completePhase(Long projectId, Long timelineId);

    /** 跳过阶段：标记SKIPPED，激活下一阶段 */
    void skipPhase(Long projectId, Long timelineId);

    /** 更新时间线记录（含状态变更+日期后推逻辑） */
    void saveTimelineRow(Long projectId, ProjectTimeline row);

    /** 更新计划日期 */
    void updatePlanned(Long projectId, String statusName, LocalDate plannedEnd);

    /** 更新plannedEnd并后推后续所有阶段 */
    void updatePlannedAndShift(Long projectId, String statusName, LocalDate plannedEnd);

    /** 更新单个阶段状态 */
    void updateStatus(Long projectId, String statusName, String status);
}
