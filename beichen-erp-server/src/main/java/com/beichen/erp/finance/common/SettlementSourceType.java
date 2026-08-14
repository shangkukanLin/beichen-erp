package com.beichen.erp.finance.common;

/**
 * 核销来源类型枚举
 * <p>
 * 管理 finance_settlement.source_type 字段，标识核销流水由付款单/收款单/账单触发。
 * </p>
 */
public enum SettlementSourceType {

    /** 付款单 */
    PAYMENT("付款单"),
    /** 收款单 */
    RECEIPT("收款单"),
    /** 账单 */
    BILL("账单");

    private final String label;

    SettlementSourceType(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
