package com.beichen.erp.outsource.common;

/**
 * 物料订单类型枚举
 */
public enum OrderType {

    /** 采购 */
    PURCHASE("采购"),
    /** 委外 */
    OUTSOURCE("委外");

    private final String label;

    OrderType(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
