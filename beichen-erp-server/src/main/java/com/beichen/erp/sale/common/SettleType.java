package com.beichen.erp.sale.common;

/**
 * 销售单结算方式（2026-09-18）。
 *
 * <p>口径：**按单记** —— 同一个客户有时给现金、有时账期，故结算方式落在销售单头上，不用客户主数据表达。</p>
 * <ul>
 *   <li>{@link #CREDIT}：账期 —— 审核只挂应收（与历史行为完全一致），后续走「收款单」核销；</li>
 *   <li>{@link #CASH}：现金 —— 审核时除挂应收外，**自动生成一张草稿收款单**（挂所选收款账户、
 *       金额 = 本单应收全额、核销明细指向本单应收），人工审核该收款单即完成收款（账务口径不变）。</li>
 * </ul>
 * 枚举一律存 code（英文），中文标签由前端 {@code api/enums.ts} 映射。
 */
public enum SettleType {

    /** 账期（默认）：只挂应收，后续手工收款核销 */
    CREDIT("CREDIT"),

    /** 现金：审核销售单后自动生成草稿收款单 */
    CASH("CASH");

    private final String code;

    SettleType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 空值/非法值一律归一为账期（兼容历史单据与未升级的前端提交） */
    public static SettleType normalize(String code) {
        if (code == null || code.isBlank()) return CREDIT;
        for (SettleType t : values()) {
            if (t.code.equalsIgnoreCase(code.trim())) return t;
        }
        return CREDIT;
    }

    /** 是否现金结算（需要自动生成草稿收款单） */
    public static boolean isCash(String code) {
        return CASH == normalize(code);
    }
}
