package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.ProjectTimeline;

import java.time.LocalDate;
import java.util.List;

public interface ProjectTimelineService extends IService<ProjectTimeline> {

    List<ProjectTimeline> listByProject(Long projectId);

    void initTimeline(Long projectId);

    void updateTimeline(Long projectId, String statusName, LocalDate actualEnd);

    void updatePlanned(Long projectId, String statusName, LocalDate plannedEnd);

    /** 更新plannedEnd并后推后续所有阶段 */
    void updatePlannedAndShift(Long projectId, String statusName, LocalDate plannedEnd);

    void updateStatus(Long projectId, String statusName, String status);

    /** 完成阶段：自动填actualEnd，下一阶段变为"进行中"，最后阶段则自动结项 */
    void completePhase(Long projectId, Long timelineId);
}
