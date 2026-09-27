package com.beichen.erp.outsource.common;

/**
 * 委外加工单状态枚举
 * <p>
 * 管理 outsource_order.status 字段。
 * 描述委外加工单从创建到完成的生命周期。
 * </p>
 */
public enum OutsourceOrderStatus {

    /** 待审核：加工单刚创建，等待审核 */
    PENDING("待审核"),
    /** 生产中：已审核，正在生产加工 */
    PRODUCING("生产中"),
    /** 已结单：结单报表确认结单（2026-09-27 用户口径：状态文案由「已完成」统一为「已结单」——
     * 该状态**只能由结单动作进入**，反结单退回生产中，与后端报错文案/CloseReportStatus 的措辞一致） */
    FINISHED("已结单"),
    /** 已作废：加工单被作废 */
    CANCELLED("已作废");

    private final String label;

    OutsourceOrderStatus(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }
}
