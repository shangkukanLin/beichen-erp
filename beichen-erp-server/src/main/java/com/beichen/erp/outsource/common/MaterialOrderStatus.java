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
    /** 生产中：已审核，正在收货（2026-09-28 用户口径：状态文案由「收货中」改「生产中」——
     * 与页签、与加工单侧 {@code OutsourceOrderStatus.PRODUCING=生产中} 命名统一；code 不变、库中数据不动） */
    RECEIVING("生产中"),
    /** 已结单：人工结单（2026-09-27 用户口径：状态文案由「已完成」统一为「已结单」；
     * 2026-09-27 同批新增反结单能力，见 MaterialOrderService.reopen） */
    FINISHED("已结单"),
    /** 已作废：订单被作废 */
    CANCELLED("已作废");

    private final String label;

    MaterialOrderStatus(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }
}
