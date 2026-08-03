package com.beichen.erp.outsource.common;

/**
 * 委外交货类型枚举
 * <p>
 * 管理 outsource_delivery.delivery_type 字段。
 * 用于区分委外交货单的发货方式。
 * </p>
 */
public enum DeliveryType {

    /** 发料：从我方仓库发料到委外工厂 */
    DELIVERY("发料"),
    /** 收料：从委外工厂收回物料 */
    RECEIVE("收料"),
    /** 调拨：仓库间调拨物料 */
    TRANSFER("调拨"),
    /** 退料：退回物料到供应商或仓库 */
    RETURN("退料"),
    /** 退不良：退回不良品 */
    DEFECT_RETURN("退不良");

    private final String label;

    DeliveryType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /**
     * 根据数据库存储的 code（枚举名）反向查找。
     */
    public static DeliveryType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (DeliveryType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
