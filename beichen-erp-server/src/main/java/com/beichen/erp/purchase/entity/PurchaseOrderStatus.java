package com.beichen.erp.purchase.entity;

/**
 * 成品采购单状态枚举
 * 0=草稿  1=已完成  2=已作废
 */
public enum PurchaseOrderStatus {

    DRAFT(0, "草稿"),
    COMPLETED(1, "已完成"),
    CANCELLED(2, "已作废");

    private final Integer code;
    private final String label;

    PurchaseOrderStatus(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public Integer getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static PurchaseOrderStatus fromCode(Integer code) {
        if (code == null) return null;
        for (PurchaseOrderStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}
