package com.beichen.erp.material.common;

import lombok.Getter;

/**
 * 产品等级类型：A规/B规/C规/不良
 * DB存储枚举name(A/B/C/DEFECT)，前端展示label(A规/B规/C规/不良)
 */
@Getter
public enum ProductQualityType {

    /** A规：优等品，质量最好 */
    A("A规"),

    /** B规：合格品，略有瑕疵但可用 */
    B("B规"),

    /** C规：次品，有明显瑕疵 */
    C("C规"),

    /** 不良：无法使用的缺陷品 */
    DEFECT("不良");

    private final String label;

    ProductQualityType(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举常量名（A/B/C/DEFECT） */
    public String getCode() { return name(); }
}
