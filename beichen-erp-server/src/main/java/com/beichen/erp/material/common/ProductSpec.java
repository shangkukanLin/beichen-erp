package com.beichen.erp.material.common;

import com.beichen.erp.exception.BusinessException;

/**
 * 产品规格枚举（对应 {@code product.spec_type}）。
 *
 * <p>2026-09-21 用户要求：原「分类」（自由文本 {@code product.category}）替换为「规格」，
 * 固定三种取值 —— 原装 / 原配 / 改配，前端用下拉框选择。</p>
 *
 * <p>存库使用 code（英文枚举名），前端展示使用 label（中文）；
 * 须与前端 {@code api/enums.ts} 的 {@code ProductSpec} / {@code ProductSpecLabel} 保持一致。</p>
 */
public enum ProductSpec {

    /** 原装 */
    ORIGINAL("原装"),
    /** 原配 */
    MATCHED("原配"),
    /** 改配 */
    MODIFIED("改配");

    private final String label;

    ProductSpec(String label) {
        this.label = label;
    }

    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    /** code → 枚举（大小写不敏感）；空值或非法值返回 null */
    public static ProductSpec fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim();
        for (ProductSpec s : values()) {
            if (s.name().equalsIgnoreCase(c)) return s;
        }
        return null;
    }

    /**
     * 校验并归一化 code（**必填 + 必须在枚举内**），返回归一化后的 code。
     * <p>⚠️ 只在**用户入口**（{@code ProductController} 的新增/修改）调用，不放在 Service：
     * 研发立项会自动建产品（{@code ProjectProductSyncServiceImpl} 走 {@code ProductService.save}）
     * 且那时规格尚未确定，若在 Service 强制必填会把立项流程直接打断。</p>
     */
    public static String requireValid(String code) {
        ProductSpec s = fromCode(code);
        if (s == null) {
            throw new BusinessException("请选择规格（只能为：原装 / 原配 / 改配）");
        }
        return s.name();
    }
}
