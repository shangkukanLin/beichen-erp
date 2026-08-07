package com.beichen.erp.sale.common;

/**
 * 销售退货单状态枚举
 * <p>与采购退货单 PurchaseReturnStatus 保持一致的整数编码方案。</p>
 * 0=草稿 1=已审核(已完成) 2=已作废
 */
public enum SaleReturnStatus {

    DRAFT(0, "草稿"),
    AUDITED(1, "已审核"),
    CANCELLED(2, "已作废");

    private final Integer code;
    private final String label;

    SaleReturnStatus(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public Integer getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SaleReturnStatus fromCode(Integer code) {
        if (code == null) return null;
        for (SaleReturnStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        return null;
    }
}
