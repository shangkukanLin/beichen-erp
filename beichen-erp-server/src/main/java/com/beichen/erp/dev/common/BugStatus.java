package com.beichen.erp.dev.common;

/**
 * Bug状态枚举
 */
public enum BugStatus {

    /** 待处理 */
    OPEN("待处理"),
    /** 处理中 */
    FIXING("处理中"),
    /** 已修复 */
    FIXED("已修复"),
    /** 已验证 */
    VERIFIED("已验证"),
    /** 已关闭 */
    CLOSED("已关闭");

    private final String label;

    BugStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
