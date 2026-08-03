package com.beichen.erp.finance.common;

/**
 * 财务账单类型枚举
 * <p>
 * 管理 finance_bill.bill_type 字段。
 * 用于区分应收/应付账单，禁止散落硬编码中文字符串。
 * </p>
 */
public enum BillType {

    /** 应收账单 */
    RECEIVABLE("应收"),
    /** 应付账单 */
    PAYABLE("应付");

    private final String label;

    BillType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /**
     * 根据数据库存储的 code（枚举名）反向查找。
     */
    public static BillType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (BillType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
