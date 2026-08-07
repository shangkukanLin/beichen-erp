package com.beichen.erp.dev.common;

/**
 * 研发物料存放位置仓库归属类型
 * 自有仓库与委外仓库是两张独立表，ID 空间独立，需用 type + id 复合定位唯一仓库
 */
public enum DevMaterialLocationTypeEnum {

    /** 自有仓库 */
    INVENTORY("自有仓库"),
    /** 委外仓库 */
    OUTSOURCE("委外仓库");

    private final String label;

    DevMaterialLocationTypeEnum(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }

    public static DevMaterialLocationTypeEnum fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (DevMaterialLocationTypeEnum t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
