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
}
