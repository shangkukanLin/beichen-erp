package com.beichen.erp.finance.common;

/**
 * 财务来源单据类型枚举
 * <p>
 * 管理 finance_receivable.source_bill_type / finance_payable.source_bill_type 字段。
 * 标识应收/应付的来源业务单据类型，禁止散落硬编码中文字符串。
 * </p>
 */
public enum SourceBillType {

    /** 销售出库 */
    SALE_OUTBOUND("销售出库"),
    /** 销售单 */
    SALE_ORDER("销售单"),
    /** 采购单 */
    PURCHASE_ORDER("采购单"),
    /** 采购入库 */
    PURCHASE_INBOUND("采购入库"),
    /** 成品退货单 */
    PURCHASE_RETURN("成品退货单"),
    /** 销售退货 */
    SALE_RETURN("销售退货");

    private final String label;

    SourceBillType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /**
     * 根据数据库存储的 code（枚举名）反向查找。
     */
    public static SourceBillType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (SourceBillType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
