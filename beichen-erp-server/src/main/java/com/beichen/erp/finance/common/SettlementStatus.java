package com.beichen.erp.finance.common;

import java.math.BigDecimal;

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

    /**
     * 是否「已发生结算」——反审核护栏专用。
     * <p>已结清 / 部分结清 / 已有付款额 视为已结算，禁止反审核来源单据；
     * <b>CANCELLED（已冲回）不算已结算</b>：它正是来源单据反审核时对台账做的冲销留痕
     * （台账已冲销但单据仍为已审核的异常场景）。若把它当成"已核销"拦下，单据将永久卡死
     * ——既不能反审核、也不能重新审核（重新审核要求草稿态）。</p>
     */
    public static boolean isSettled(String status, BigDecimal paidAmount) {
        return SETTLED.getCode().equals(status)
            || PARTIAL.getCode().equals(status)
            || (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0);
    }
}
