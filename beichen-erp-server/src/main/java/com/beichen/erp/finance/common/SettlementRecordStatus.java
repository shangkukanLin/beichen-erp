package com.beichen.erp.finance.common;

/**
 * 核销流水状态（{@code finance_settlement.status}）。
 * <p>反审核不物理删除核销流水，而是置 {@link #CANCELLED} 留痕，保证"这笔款曾核销过哪些应收/应付"
 * 可追溯；重新审核会写入新的 NORMAL 记录，查询有效核销时必须过滤 {@link #NORMAL}。</p>
 */
public enum SettlementRecordStatus {

    /** 有效核销 */
    NORMAL("有效"),
    /** 已冲销（来源收付款单反审核留痕） */
    CANCELLED("已冲销");

    private final String label;

    SettlementRecordStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
