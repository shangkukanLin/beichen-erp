package com.beichen.erp.outsource.common;

/**
 * 委外物料退货类型枚举
 * <p>统一管理 outsource_material_return.return_type 字段，预留成品商退货扩展。</p>
 */
public enum MaterialReturnType {

    /** 物料商退货：物料退回物料商，冲减应付 */
    MATERIAL("物料商退货"),

    /** 成品商退货：物料退回成品商（来料退回，预留，本次未启用） */
    PRODUCT("成品商退货");

    private final String label;

    MaterialReturnType(String label) {
        this.label = label;
    }

    /** 前端显示的中文名称 */
    public String getLabel() {
        return label;
    }

    /** 存入数据库的枚举常量名（如 MATERIAL） */
    public String getCode() {
        return name();
    }

    public static MaterialReturnType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (MaterialReturnType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        for (MaterialReturnType t : values()) {
            if (t.label.equals(code)) return t;
        }
        return null;
    }
}
