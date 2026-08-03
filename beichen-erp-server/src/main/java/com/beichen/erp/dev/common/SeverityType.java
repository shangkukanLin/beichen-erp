package com.beichen.erp.dev.common;

/**
 * Bug严重程度枚举
 */
public enum SeverityType {

    /** 致命 */
    CRITICAL("致命"),
    /** 严重 */
    MAJOR("严重"),
    /** 一般 */
    NORMAL("一般"),
    /** 轻微 */
    MINOR("轻微");

    private final String label;

    SeverityType(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }

    public static SeverityType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (SeverityType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
