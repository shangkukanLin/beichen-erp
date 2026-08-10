package com.beichen.erp.supplier.common;

import java.util.Arrays;

/**
 * 供应商类型枚举
 * code 为业务类型编码，prefix 为供应商编码前缀
 */
public enum SupplierTypeEnum {

    SOLUTION("solution", "SOL"),
    FACTORY("factory", "FAC"),
    PRODUCT("product", "PRO"),
    MATERIAL("material", "MAT");

    private final String code;
    private final String prefix;

    SupplierTypeEnum(String code, String prefix) {
        this.code = code;
        this.prefix = prefix;
    }

    public String getCode() {
        return code;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * 按业务编码查询枚举
     */
    public static SupplierTypeEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("供应商类型不能为空");
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的供应商类型: " + code));
    }

    /**
     * 按编码前缀反查枚举（用于根据已有供应商编码识别类型）
     */
    public static SupplierTypeEnum fromPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("供应商编码前缀不能为空");
        }
        return Arrays.stream(values())
                .filter(e -> e.prefix.equalsIgnoreCase(prefix))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的供应商编码前缀: " + prefix));
    }
}
