package com.beichen.erp.purchase.common;

import lombok.Getter;

/**
 * 采购换货单付费类型（对应 purchase_exchange.charge_type，2026-09-21 新增）
 *
 * <p><b>方向极其重要</b>：这里是 <b>我们向供货商付费</b>（采购换货的对方是供货商）——
 * 审核后生成一条<b>正向应付</b>（我方欠供货商的钱增加，source_bill_type=PURCHASE_EXCHANGE_CHARGE）。
 * 与销售换货 {@code sale.common.ExchangeChargeType}（<b>向客户收费</b> ⇒ 正向应收）方向恰好相反，
 * 故本枚举独立定义、Javadoc 各自写明方向，避免两个方向混用。</p>
 *
 * <p>金额一律手工填写，本枚举仅用于标记业务场景，便于统计与对账。</p>
 */
@Getter
public enum ExchangePayType {

    /** 服务费：供货商收取的换货/翻新手续费等 */
    SERVICE("服务费"),

    /** 品质差价：换入品质高于退回品质，我方补差价 */
    DIFF("品质差价"),

    /** 全额货值：按货值全额付费换新（非等价换货） */
    FULL("全额货值"),

    /** 其他 */
    OTHER("其他");

    private final String label;

    ExchangePayType(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /** 按编码取枚举，非法则返回 null */
    public static ExchangePayType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (ExchangePayType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }

    /** 是否为合法的付费类型编码 */
    public static boolean isValid(String code) { return fromCode(code) != null; }
}
