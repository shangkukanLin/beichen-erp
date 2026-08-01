package com.beichen.erp.outsource.common;

/**
 * 退不良处理方式枚举
 */
public enum DefectHandleType {

    /** 维修返还：退回后维修好再返还，需操作库存 */
    REPAIR_RETURN("维修返还"),
    /** 折现退款：不退实物，直接退款，仅记录不操作库存 */
    CASH_REFUND("折现退款");

    private final String label;

    DefectHandleType(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
