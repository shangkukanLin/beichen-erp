package com.beichen.erp.outsource.common;

/**
 * 品质类型枚举
 */
public enum QualityType {

    /** 良品 */
    GOOD("良品"),
    /** 不良品 */
    DEFECT("不良品");

    private final String label;

    QualityType(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
