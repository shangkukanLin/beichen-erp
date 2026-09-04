package com.beichen.erp.outsource.common;

/**
 * 委外加工退货收费类型枚举
 * <p>
 * 管理 outsource_return_order.charge_type 字段。
 * 语义与销售域的 ExchangeChargeType 相反：销售换货/退货收费是「我方向客户收取」（生成应收），
 * 这里的收费是「加工厂向我方收取」（生成应付），故独立成枚举，不复用销售侧类型。
 * </p>
 */
public enum OutsourceChargeType {

    /** 返工费：退货后需要加工厂重新加工产生的费用 */
    REWORK("返工费"),

    /** 运费：退货物料往返运输费用 */
    FREIGHT("运费"),

    /** 检测费：退货品质检测/分拣产生的费用 */
    INSPECTION("检测费"),

    /** 超损赔偿：加工过程中超出约定损耗，加工厂向我方索赔 */
    EXCESS_LOSS("超损赔偿"),

    /** 其他 */
    OTHER("其他");

    private final String label;

    OutsourceChargeType(String label) {
        this.label = label;
    }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /** 按编码取枚举，非法则返回 null */
    public static OutsourceChargeType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (OutsourceChargeType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }

    /** 是否为合法收费类型（用于入参校验） */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
