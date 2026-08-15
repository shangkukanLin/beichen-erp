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
    /** 已完成：加工完成，成品已入库 */
    FINISHED("已完成"),
    /** 已取消：加工单被取消 */
    CANCELLED("已取消");

    private final String label;

    OutsourceOrderStatus(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }
}
