package com.beichen.erp.material.common;

import lombok.Getter;

/**
 * 产品等级类型：A规/B规/C规/不良品/待整理
 * DB存储枚举name(A/B/C/DEFECT/PENDING)，前端展示label(A规/B规/C规/不良品/待整理)
 */
@Getter
public enum ProductQualityType {

    /** A规：优等品，质量最好 */
    A("A规"),

    /** B规：合格品，略有瑕疵但可用 */
    B("B规"),

    /** C规：次品，有明显瑕疵 */
    C("C规"),

    /** 不良品：无法使用的缺陷品 */
    DEFECT("不良品"),

    /** 待整理：售后退回，品质待定，待退货整理重新分类 */
    PENDING("待整理");

    private final String label;

    ProductQualityType(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举常量名（A/B/C/DEFECT/PENDING） */
    public String getCode() { return name(); }

    /** 判断是否为合法的品质等级编码（空值视为非法） */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) return false;
        for (ProductQualityType t : values()) {
            if (t.name().equals(code)) return true;
        }
        return false;
    }

    /** 按编码取枚举，非法则返回 null */
    public static ProductQualityType of(String code) {
        if (code == null || code.isBlank()) return null;
        for (ProductQualityType t : values()) {
            if (t.name().equals(code)) return t;
        }
        return null;
    }
}
