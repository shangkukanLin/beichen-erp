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
    CANCELLED("已冲回"),
    /** 预付/多付：超额付款产生的负数应付（供应商欠我方），实时汇总时自然抵扣 */
    ADVANCE("预付");

    private final String label;

    SettlementStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
