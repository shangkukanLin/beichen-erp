package com.beichen.erp.dev.common;

/**
 * 项目时间线状态枚举
 * <p>
 * 管理 dev_project_timeline.status 字段。
 * 描述研发项目中各阶段/时间线的进展状态。
 * </p>
 */
public enum TimelineStatus {

    /** 未开始：阶段尚未启动 */
    NOT_STARTED("未开始"),
    /** 进行中：阶段正在执行 */
    IN_PROGRESS("进行中"),
    /** 已完成：阶段已结束 */
    FINISHED("已完成"),
    /** 已跳过：用户跳过该阶段 */
    SKIPPED("已跳过");

    private final String label;

    TimelineStatus(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }
}
