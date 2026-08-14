package com.beichen.erp.warehouse.common;

/**
 * 仓库类别枚举
 * <p>
 * 管理 warehouse.warehouse_category 字段，区分自有仓与委外仓。
 * 存库使用 code（英文枚举名），前端展示使用 label（中文）。
 * </p>
 */
public enum WarehouseCategory {

    /** 自有仓库 */
    INVENTORY("自有仓库"),
    /** 委外仓库 */
    OUTSOURCE("委外仓库");

    private final String label;

    WarehouseCategory(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
