package com.beichen.erp.outsource.common;

/**
 * 物料订单状态枚举
 * <p>
 * 管理 material_order.status 字段。
 * 描述委外物料订单从创建到完成的生命周期。
 * </p>
 */
public enum MaterialOrderStatus {

    /** 待确认：订单刚创建，等待确认 */
    PENDING("待确认"),
    /** 收货中：已确认，正在收货 */
    RECEIVING("收货中"),
    /** 已完成：全部收货完成 */
    FINISHED("已完成"),
    /** 已取消：订单被取消 */
    CANCELLED("已取消");

    private final String label;

    MaterialOrderStatus(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }
}
