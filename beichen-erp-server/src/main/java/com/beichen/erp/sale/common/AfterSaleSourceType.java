package com.beichen.erp.sale.common;

import lombok.Getter;

/**
 * 售后待整理批次来源单据类型（对应 after_sale_pending.source_type）
 * <p>
 * 销售退货单与销售换货单审核后，退回货品均以「待整理(PENDING)」入售后仓，
 * 并在 {@code after_sale_pending} 各写一条待整理批次，由退货整理单统一消费。
 * 新增售后单据类型时只需在此扩展，无需改动整理单逻辑。
 * </p>
 */
@Getter
public enum AfterSaleSourceType {

    /** 销售退货单（sale_return） */
    SALE_RETURN("销售退货单"),

    /** 销售换货单（sale_exchange） */
    SALE_EXCHANGE("销售换货单");

    private final String label;

    AfterSaleSourceType(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /** 按编码取枚举，非法则返回 null */
    public static AfterSaleSourceType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (AfterSaleSourceType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
