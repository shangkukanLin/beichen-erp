package com.beichen.erp.sale.common;

import lombok.Getter;

/**
 * 销售退货单收费类型（对应 sale_return.charge_type / sale_return_item.charge_type）。
 *
 * <p><b>2026-10-02（用户口径）</b>：销售**退货**单的收费类型只保留两项 —— 「盖板划伤」「其他」。
 * 此前退货单是**借用**销售换货的 {@link ExchangeChargeType}（服务费 / 品质差价 / 全额货值 / 其他），
 * 与退单的实际业务场景（客户退回产品的外观损伤责任判定）不符；拆成独立枚举后：
 * <ul>
 *   <li>退货侧改选项不再牵连换货单（换货仍用 {@link ExchangeChargeType}）；</li>
 *   <li>枚举守卫 / 表单校验各查各的口径。</li>
 * </ul>
 * 收费金额仍**逐产品手工填写**，本枚举只标记业务场景，便于统计与对账（口径同换货单）。</p>
 *
 * <p>⚠️ 存量兼容：切换前全库 4 张退货单的 charge_type 均为 NULL（从未填过类型）⇒ 无历史值需要映射。</p>
 */
@Getter
public enum SaleReturnChargeType {

    /** 盖板划伤：退回产品盖板有划伤，按此场景向客户收取费用 */
    COVER_SCRATCH("盖板划伤"),

    /** 其他 */
    OTHER("其他");

    private final String label;

    SaleReturnChargeType(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /** 按编码取枚举，非法则返回 null（大小写不敏感） */
    public static SaleReturnChargeType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (SaleReturnChargeType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }

    /** 是否为合法的收费类型编码 */
    public static boolean isValid(String code) { return fromCode(code) != null; }

    /** 编码 → 中文标签；非法/空则回退原编码（台账备注等展示用） */
    public static String labelOf(String code) {
        SaleReturnChargeType t = fromCode(code);
        return t != null ? t.getLabel() : (code == null ? "" : code);
    }
}
