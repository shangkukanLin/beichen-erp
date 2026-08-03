package com.beichen.erp.inventory.common;

/**
 * 出入库类型枚举
 * <p>
 * 管理 inventory_other_io.io_type / outsource_other_io.io_type 字段。
 * 统一"入库"/"出库"概念，禁止散落硬编码中文字符串。
 * </p>
 */
public enum IoType {

    /** 入库 */
    IN("入库"),
    /** 出库 */
    OUT("出库");

    private final String label;

    IoType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /**
     * 根据数据库存储的 code（枚举名）反向查找。
     */
    public static IoType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (IoType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
