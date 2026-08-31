package com.beichen.erp.sale.common;

import lombok.Getter;

/**
 * 销售换货单收费类型（对应 sale_exchange.charge_type）
 * <p>收费金额一律手工填写，本枚举仅用于标记业务场景、便于统计与对账。</p>
 */
@Getter
public enum ExchangeChargeType {

    /** 服务费：客户人为损坏、超出保修等，收取维修/服务费 */
    SERVICE("服务费"),

    /** 品质差价：同品换货，换出品质与原销售品质不同产生的差价 */
    DIFF("品质差价"),

    /** 全额货值：客户原因换货，按货值全额收费 */
    FULL("全额货值"),

    /** 其他 */
    OTHER("其他");

    private final String label;

    ExchangeChargeType(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /** 按编码取枚举，非法则返回 null */
    public static ExchangeChargeType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (ExchangeChargeType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }

    /** 是否为合法的收费类型编码 */
    public static boolean isValid(String code) { return fromCode(code) != null; }
}
