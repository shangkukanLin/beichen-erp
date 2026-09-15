package com.beichen.erp.inventory.common;

import lombok.Getter;

/**
 * 报损原因（成品报损与委外物料报损共用）
 * DB存储枚举name(DAMAGE/EXPIRED/LOST/QUALITY/OTHER)，前端展示label
 */
@Getter
public enum LossReason {

    /** 破损：运输或搬运过程中损坏 */
    DAMAGE("破损"),

    /** 变质过期：超过保质期或存放变质 */
    EXPIRED("变质过期"),

    /** 丢失：盘点或流转中发现短少 */
    LOST("丢失"),

    /** 质量不合格：来料或加工后检验不合格，无法修复 */
    QUALITY("质量不合格"),

    /** 其他：以上都不适用，需在备注说明 */
    OTHER("其他");

    private final String label;

    LossReason(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举常量名（DAMAGE/EXPIRED/LOST/QUALITY/OTHER） */
    public String getCode() { return name(); }

    /** 判断是否为合法的报损原因编码（空值视为非法） */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) return false;
        for (LossReason r : values()) {
            if (r.name().equals(code)) return true;
        }
        return false;
    }

    /** 按编码取枚举，非法则返回 null */
    public static LossReason of(String code) {
        if (code == null || code.isBlank()) return null;
        for (LossReason r : values()) {
            if (r.name().equals(code)) return r;
        }
        return null;
    }
}
