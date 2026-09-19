package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.ProjectPhase;

import java.time.LocalDate;
import java.util.List;

public interface ProjectPhaseService extends IService<ProjectPhase> {

    /** 获取项目项目阶段列表 */
    List<ProjectPhase> listByProject(Long projectId);

    /** 初始化项目项目阶段（从模板复制） */
    void initPhase(Long projectId);

    /** 完成阶段：自动填actualEnd，下一阶段变为"进行中"，最后阶段则自动结项 */
    void completePhase(Long projectId, Long phaseId);

    /** 跳过阶段：标记SKIPPED，激活下一阶段 */
    void skipPhase(Long projectId, Long phaseId);

    /** 撤销阶段：将已完成/已跳过的阶段恢复为进行中，后续阶段重置为未开始 */
    void revertPhase(Long projectId, Long phaseId);

    /** 更新项目阶段记录（含状态变更+日期后推逻辑） */
    void savePhaseRow(Long projectId, ProjectPhase row);

    /** 更新计划日期 */
    void updatePlanned(Long projectId, String phaseName, LocalDate plannedEnd);

    /** 更新plannedEnd并后推后续所有阶段 */
    void updatePlannedAndShift(Long projectId, String phaseName, LocalDate plannedEnd);

    /** 级联重算所有未开始阶段的计划日期（从第一个进行中阶段开始推算） */
    void recalcAllPlannedEnds(Long projectId);

    // F7-93（2026-09-19）：updateStatus 已删除（无控制器映射、无调用方的死代码）
}
