package com.beichen.erp.finance.common;

/**
 * 核销方向枚举
 * <p>
 * 管理 finance_settlement.direction 字段，标识核销流水是付款方向还是收款方向。
 * </p>
 */
public enum SettlementDirection {

    /** 付款核销（应付） */
    PAY("付款"),
    /** 收款核销（应收） */
    RECEIVE("收款");

    private final String label;

    SettlementDirection(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
