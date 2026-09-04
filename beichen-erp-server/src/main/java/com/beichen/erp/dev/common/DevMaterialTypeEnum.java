package com.beichen.erp.dev.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 研发项目物料类型枚举
 */
public enum DevMaterialTypeEnum {

    /** 基板 */
    BOARD("基板"),
    /** 屏幕 */
    SCREEN("屏幕"),
    /** 测试架 */
    TEST_FIXTURE("测试架"),
    /** 触摸资料盒 */
    TOUCH_BOX("触摸资料盒"),
    /** 显示资料盒 */
    DISPLAY_BOX("显示资料盒"),
    /** 其他 */
    OTHER("其他");

    private final String label;

    DevMaterialTypeEnum(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }

    public static DevMaterialTypeEnum fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (DevMaterialTypeEnum t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }

    /** 返回所有类型的中文标签，供前端下拉使用 */
    public static List<String> allLabels() {
        List<String> labels = new ArrayList<>();
        for (DevMaterialTypeEnum t : values()) {
            labels.add(t.label);
        }
        return labels;
    }

    /** 返回所有类型的 {code,label} 选项，供前端下拉使用（value=code，显示=label） */
    public static List<java.util.Map<String, Object>> allOptions() {
        List<java.util.Map<String, Object>> options = new ArrayList<>();
        for (DevMaterialTypeEnum t : values()) {
            java.util.Map<String, Object> o = new java.util.LinkedHashMap<>();
            o.put("code", t.getCode());
            o.put("label", t.label);
            options.add(o);
        }
        return options;
    }
}
