package com.beichen.erp.finance.common;

/**
 * 应收应付结算状态枚举
 */
public enum SettlementStatus {

    /** 未结清 */
    UNSETTLED("未结清"),
    /** 部分结清 */
    PARTIAL("部分结清"),
    /** 已结清 */
    SETTLED("已结清"),
    /** 已冲回（反审核作废） */
    CANCELLED("已冲回");

    private final String label;

    SettlementStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
