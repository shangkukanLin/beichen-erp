package com.beichen.erp.dev.common;

/**
 * Bug类型枚举
 */
public enum BugTypeEnum {

    /** 显示 */
    DISPLAY("显示"),
    /** 触摸 */
    TOUCH("触摸"),
    /** 结构 */
    STRUCTURE("结构");

    private final String label;

    BugTypeEnum(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }

    public static BugTypeEnum fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (BugTypeEnum t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
