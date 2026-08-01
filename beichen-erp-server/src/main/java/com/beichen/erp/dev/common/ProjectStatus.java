package com.beichen.erp.dev.common;

/**
 * 研发项目状态枚举
 */
public enum ProjectStatus {

    /** 未开始 */
    NOT_STARTED("未开始"),
    /** 进行中 */
    IN_PROGRESS("进行中"),
    /** 小批量 */
    SMALL_BATCH("小批量"),
    /** 结项 */
    CLOSED("结项");

    private final String label;

    ProjectStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
