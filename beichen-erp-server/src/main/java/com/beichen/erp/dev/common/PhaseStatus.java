package com.beichen.erp.dev.common;

/**
 * 项目项目阶段状态枚举
 * <p>
 * 管理 dev_project_phase.status 字段。
 * 描述研发项目中各阶段/项目阶段的进展状态。
 * </p>
 */
public enum PhaseStatus {

    /** 未开始：阶段尚未启动 */
    NOT_STARTED("未开始"),
    /** 进行中：阶段正在执行 */
    IN_PROGRESS("进行中"),
    /** 已完成：阶段已结束 */
    FINISHED("已完成"),
    /** 已跳过：用户跳过该阶段 */
    SKIPPED("已跳过");

    private final String label;

    PhaseStatus(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /**
     * F7-96（2026-09-19）：按 code 解析（大小写不敏感、空值安全）。
     * <p>原先 {@code savePhaseRow} 直接采用请求体里的 {@code status}（无白名单），
     * 写入非法值后 {@code syncProjectStatus} 的 allMatch 会把它当作"未完成"，
     * 项目将永远无法结项。</p>
     */
    public static PhaseStatus fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (PhaseStatus t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
