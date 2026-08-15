package com.beichen.erp.dev.common;

/**
 * 研发物料位置来源类型枚举
 * <p>
 * 管理 dev_material_flow.place_type 字段，标识物料某次流转所处的位置来源。
 * 存库使用 code（英文枚举名），前端展示使用 label（中文）。
 * </p>
 */
public enum DevMaterialPlaceTypeEnum {

    /** 自有仓库 */
    INVENTORY("自有仓库"),
    /** 委外仓库 */
    OUTSOURCE("委外仓库"),
    /** 供应商 */
    SUPPLIER("供应商"),
    /** 客户 */
    CUSTOMER("客户"),
    /** 自定义文本 */
    TEXT("自定义");

    private final String label;

    DevMaterialPlaceTypeEnum(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }

    public static String toLabel(String code) {
        if (code == null || code.isBlank()) return "";
        for (DevMaterialPlaceTypeEnum t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t.label;
        }
        return code;
    }
}
