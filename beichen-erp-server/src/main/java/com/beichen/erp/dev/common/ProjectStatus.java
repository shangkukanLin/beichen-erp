package com.beichen.erp.dev.common;

/**
 * 研发项目状态枚举
 * <p>项目状态由时间线自动推导：有进行中阶段=IN_PROGRESS，全部完成/跳过=CLOSED，取消=CANCELLED</p>
 */
public enum ProjectStatus {

    /** 进行中 */
    IN_PROGRESS("进行中"),
    /** 已结项 */
    CLOSED("已结项"),
    /** 已取消 */
    CANCELLED("已取消");

    private final String label;

    ProjectStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }

    public static ProjectStatus fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (ProjectStatus t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
