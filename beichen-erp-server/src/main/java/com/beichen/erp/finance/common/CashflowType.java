package com.beichen.erp.finance.common;

/**
 * 资金流水类型枚举
 * <p>
 * 管理 finance_cashflow.flow_type 字段。
 * 存库使用 code（英文枚举名），前端展示使用 label（中文）。
 * </p>
 */
public enum CashflowType {

    /** 期初余额 */
    OPENING("期初"),
    /** 付款 */
    PAYMENT("付款"),
    /** 收款 */
    RECEIPT("收款"),
    /** 付款冲正 */
    PAYMENT_REVERSE("付款冲正"),
    /** 收款冲正 */
    RECEIPT_REVERSE("收款冲正");

    private final String label;

    CashflowType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /** 根据数据库存储的 code（枚举名）反向查找 label，匹配不到时原样返回（兼容历史中文数据） */
    public static String toLabel(String code) {
        if (code == null || code.isBlank()) return "";
        for (CashflowType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t.label;
        }
        return code;
    }
}
