package com.beichen.erp.finance.common;

/**
 * 往来主体类型：应收台账既可以挂客户，也可以挂供应商（应付转应收场景）。
 * <p>
 * DB 存枚举名（CUSTOMER/SUPPLIER），前端展示 label。
 * </p>
 */
public enum SubjectType {

    /** 客户应收（销售业务产生，挂 customer_id） */
    CUSTOMER("客户"),

    /** 供应商应收（挂 supplier_id：退货/超损扣款无货款可抵时，转为向供应商收款） */
    SUPPLIER("供应商");

    private final String label;

    SubjectType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /** 按编码取枚举，非法则返回 null */
    public static SubjectType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (SubjectType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
