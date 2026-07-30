package com.beichen.erp.common;

/**
 * 通用单据状态枚举（草稿/已审核/已作废）
 * <p>
 * 覆盖以下表的 status 字段：purchase_inbound（采购入库单）、sale_order（销售单）、
 * sale_outbound（销售出库单）、inventory_warehouse_move（成品移仓单）、
 * inventory_other_io（其他出入库）、finance_receipt（收款单）、
 * finance_payment（付款单）。
 * </p>
 */
public enum DocStatus {

    /** 草稿：单据刚创建，可编辑、可作废、可审核 */
    DRAFT("草稿"),

    /** 已审核：审核通过后生效，库存已变更，不可编辑 */
    AUDITED("已审核"),

    /** 已作废：单据被作废，不产生库存影响 */
    CANCELLED("已作废");

    private final String label;

    DocStatus(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名（如 DRAFT） */
    public String getCode() { return name(); }

    /**
     * 根据数据库存储的 code（枚举名）或中文 label 反向查找。
     * 优先按枚举名匹配，回退按中文 label 匹配（兼容存量数据）。
     */
    public static DocStatus fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (DocStatus s : values()) {
            if (s.name().equalsIgnoreCase(code)) return s;
            if (s.label.equals(code)) return s;
        }
        return null;
    }
}
