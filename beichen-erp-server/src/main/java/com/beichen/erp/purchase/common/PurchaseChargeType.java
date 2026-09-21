package com.beichen.erp.purchase.common;

import lombok.Getter;

/**
 * 采购侧「付费」类型（对应 {@code purchase_exchange.charge_type} / {@code purchase_return.charge_type}
 * 以及两者**明细行**的 charge_type，2026-09-21）
 *
 * <p><b>方向极其重要</b>：这里是 <b>我们向供货商付费</b>（采购侧的对方是供货商）——
 * 审核后生成一条<b>正向应付</b>（我方欠供货商的钱增加，
 * source_bill_type = {@code PURCHASE_EXCHANGE_CHARGE} / {@code PURCHASE_RETURN_CHARGE}）。
 * 与销售侧 {@code sale.common.ExchangeChargeType}（<b>向客户收费</b> ⇒ 正向应收）方向恰好相反，
 * 故两侧各自定义枚举、Javadoc 各自写明方向，避免两个方向混用。</p>
 *
 * <p>金额一律由**明细行**填写（用户口径：精确到产品），单据级金额 = Σ 明细行；
 * 本枚举只用于标记业务场景，便于统计与对账。</p>
 *
 * <p>2026-09-21：由 {@code ExchangePayType} 改名为中性名（采购退货单也要用同一套类型）——
 * <b>取值与标签与历史数据完全一致</b>（SERVICE/DIFF/FULL/OTHER），无需数据迁移。</p>
 */
@Getter
public enum PurchaseChargeType {

    /** 服务费：供货商收取的换货 / 翻新 / 退货处理手续费等 */
    SERVICE("服务费"),

    /** 品质差价：换入品质高于退回品质、或退货折让等，我方补差价 */
    DIFF("品质差价"),

    /** 全额货值：按货值全额付费（非等价换货 / 全额赔付） */
    FULL("全额货值"),

    /** 其他 */
    OTHER("其他");

    private final String label;

    PurchaseChargeType(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /** 按编码取枚举，非法则返回 null */
    public static PurchaseChargeType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (PurchaseChargeType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }

    /** 是否为合法的付费类型编码 */
    public static boolean isValid(String code) { return fromCode(code) != null; }
}
