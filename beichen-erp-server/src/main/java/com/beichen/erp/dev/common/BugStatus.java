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

    /**
     * F7-100（2026-09-19）：按 code 解析（大小写不敏感、空值安全）。
     * <p>原先接口层对 {@code status} 不做任何校验，前端可写入任意字符串，
     * 导致列表页 {@code BugStatusLabel[status]} 映射不到中文而显示空白。</p>
     */
    public static BugStatus fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (BugStatus t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
