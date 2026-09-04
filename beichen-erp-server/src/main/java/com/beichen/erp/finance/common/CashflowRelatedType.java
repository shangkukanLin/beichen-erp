package com.beichen.erp.finance.common;

/**
 * 资金流水关联单据类型枚举
 * <p>
 * 管理 finance_cashflow.related_bill_type 字段（DB 存 code），标识流水由哪类单据触发。
 * </p>
 */
public enum CashflowRelatedType {

    /** 收款单 */
    RECEIPT("收款单"),
    /** 付款单 */
    PAYMENT("付款单"),
    /** 费用单 */
    EXPENSE("费用单"),
    /** 期初余额 */
    OPENING("期初余额");

    private final String label;

    CashflowRelatedType(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
