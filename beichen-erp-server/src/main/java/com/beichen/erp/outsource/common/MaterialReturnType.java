package com.beichen.erp.outsource.common;

/**
 * 委外物料退货类型（2026-09-17 定稿，对齐「委外加工退货」的两类型）
 * <ul>
 *   <li><b>REFUND 退货退款</b>：物料退还给供应商，供应商把货款退给我们 →
 *       审核 = 源仓扣减（{@code MATERIAL_RETURN_OUT}）+ <b>负向应付</b>冲减；</li>
 *   <li><b>REPAIR 维修返还</b>：物料退给供应商维修，修好后<b>把物料还给我们</b> →
 *       审核 = 源仓扣减（{@code MATERIAL_REPAIR_OUT}）、<b>不冲减应付</b>；
 *       修好回来时在详情页「登记维修返回」入库（{@code MATERIAL_REPAIR_IN}），可逐行撤销。</li>
 * </ul>
 * <p>历史值 {@code MATERIAL}（旧枚举"物料商退货"）与空值都在 {@link #normalize(String)} 归一到 REFUND，
 * 保证存量单据可正常显示与反审核。</p>
 */
public enum MaterialReturnType {

    /** 退货退款：物料退回供应商并退款（冲减应付） */
    REFUND("退货退款"),

    /** 维修返还：退回供应商维修，修好后返还（不冲应付，登记维修返回入库） */
    REPAIR("维修返还");

    private final String label;

    MaterialReturnType(String label) {
        this.label = label;
    }

    /** 前端显示的中文名称 */
    public String getLabel() {
        return label;
    }

    /** 存入数据库的枚举常量名（如 REFUND） */
    public String getCode() {
        return name();
    }

    public static MaterialReturnType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (MaterialReturnType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }

    /** 归一化：空值/历史值（MATERIAL）/非法值一律按 REFUND 处理 */
    public static MaterialReturnType normalize(String code) {
        MaterialReturnType t = fromCode(code);
        return t != null ? t : REFUND;
    }

    /** 是否维修返还 */
    public static boolean isRepair(String code) {
        return normalize(code) == REPAIR;
    }
}
