package com.beichen.erp.outsource.common;

/**
 * 委外发货/退货状态枚举
 */
public enum DeliveryStatus {

    /** 已确认（发货/退货确认后即为已确认状态） */
    CONFIRMED("已确认"),
    /** 已取消 */
    CANCELLED("已取消");

    private final String label;

    DeliveryStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
