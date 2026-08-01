package com.beichen.erp.outsource.common;

/**
 * 结单报表状态枚举
 */
public enum CloseReportStatus {

    /** 未生成/草稿 */
    DRAFT("未生成"),
    /** 已结单 */
    FINISHED("已结单");

    private final String label;

    CloseReportStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
