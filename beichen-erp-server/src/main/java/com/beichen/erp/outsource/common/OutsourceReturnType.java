package com.beichen.erp.outsource.common;

/**
 * 委外加工退货类型（2026-09-17 新增，存 {@code outsource_return_order.return_type}）。
 *
 * <p>同一张「加工退货单」承载两类业务，规则差异集中在
 * {@code OutsourceReturnOrderServiceImpl} 的类型校验里：</p>
 * <ul>
 *   <li>{@link #DEFECT} <b>不良退货</b>：工厂交货给我方后我方发现不良 → 退回工厂。
 *       可关联加工单（也可不关联）；<b>不产生工厂收费</b>（不良是工厂的问题，加工厂不向我方收费）；
 *       退货物料按 BOM 快照还回工厂委外仓并按明细金额冲减已生成的加工应付。</li>
 *   <li>{@link #REPAIR} <b>维修退货</b>：客户使用后退回我方的售后品 → 推给工厂维修。
 *       <b>必须不关联加工单</b>（维修不看订单）；<b>必须由工厂向我方收费</b>（生成正向应付
 *       {@code OUTSOURCE_REPAIR_CHARGE}）；不还料、不冲减应付；修好后走「维修返回」登记入库。</li>
 * </ul>
 */
public enum OutsourceReturnType {

    /** 不良退货（工厂交货发现不良，退回工厂） */
    DEFECT("不良退货"),

    /** 维修退货（客户退货的售后品推给工厂维修，工厂收费） */
    REPAIR("维修退货");

    private final String label;

    OutsourceReturnType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) return false;
        for (OutsourceReturnType t : values()) {
            if (t.name().equalsIgnoreCase(code.trim())) return true;
        }
        return false;
    }

    public static OutsourceReturnType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (OutsourceReturnType t : values()) {
            if (t.name().equalsIgnoreCase(code.trim())) return t;
        }
        return null;
    }

    /**
     * 归一化退货类型：空值/非法值一律按 {@link #DEFECT}（与建表默认值一致，兼容 2026-09-17 之前的存量单据）。
     */
    public static String normalize(String code) {
        OutsourceReturnType t = fromCode(code);
        return (t == null ? DEFECT : t).getCode();
    }

    /** 是否为维修退货（无物料明细、必须收费、不关联加工单） */
    public static boolean isRepair(String code) {
        return REPAIR.getCode().equalsIgnoreCase(code == null ? "" : code.trim());
    }
}
