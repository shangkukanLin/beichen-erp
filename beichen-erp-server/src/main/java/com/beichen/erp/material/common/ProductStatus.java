package com.beichen.erp.material.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 产品状态枚举
 */
@Getter
public enum ProductStatus {

    NORMAL("正常"),
    DISCONTINUED("停售"),
    DEVELOPING("研发中");

    @EnumValue
    private final String value;
    private final String label;

    ProductStatus(String label) {
        this.value = this.name();
        this.label = label;
    }

    /** 根据数据库存储的值反查枚举 */
    public static ProductStatus fromValue(String value) {
        for (ProductStatus s : values()) {
            if (s.value.equals(value) || s.label.equals(value)) {
                return s;
            }
        }
        return NORMAL;
    }
}
