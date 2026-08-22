package com.beichen.erp.outsource.common;

/**
 * 物料订单状态枚举
 * <p>
 * 管理 material_order.status 字段。
 * 描述委外物料订单从创建到完成的生命周期。
 * </p>
 */
public enum MaterialOrderStatus {

    /** 待审核：订单刚创建，等待审核 */
    PENDING("待审核"),
    /** 收货中：已审核，正在收货 */
    RECEIVING("收货中"),
    /** 已完成：全部收货完成 */
    FINISHED("已完成"),
    /** 已作废：订单被作废 */
    CANCELLED("已作废");

    private final String label;

    MaterialOrderStatus(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }
}
