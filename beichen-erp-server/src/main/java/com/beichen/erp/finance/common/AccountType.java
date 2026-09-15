package com.beichen.erp.finance.common;

import com.beichen.erp.exception.BusinessException;

/**
 * 资金账户类型（管理 {@code finance_account.account_type} 字段）
 * <p>
 * 口径：**DB 存 code（小写英文）**，前端按 code 映射中文
 * （前端 {@code beichen-erp-web/src/api/enums.ts} 的 {@code AccountTypeLabel}）。
 * </p>
 * <p>
 * 历史问题（2026-09-14）：该列一度是自由字符串且无校验，边界测试直接写入大写 {@code 'BANK'}，
 * 导致前端映射（键为小写）漏匹配、页面显示成枚举。现由本枚举统一 trim + 转小写 + 白名单校验。
 * </p>
 */
public enum AccountType {

    CASH("现金"),
    BANK("银行"),
    WECHAT("微信"),
    ALIPAY("支付宝");

    private final String label;

    AccountType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }

    /** 存入数据库的 code（小写） */
    public String getCode() { return name().toLowerCase(); }

    /**
     * 归一化并校验账户类型：trim → 转小写 → 白名单比对。
     *
     * @param type 请求里的原始值（可能带空格或大小写不一致）
     * @return 归一化后的 code
     * @throws BusinessException 为空或不在白名单内
     */
    public static String normalize(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException("账户类型不能为空");
        }
        String code = type.trim().toLowerCase();
        for (AccountType t : values()) {
            if (t.getCode().equals(code)) return code;
        }
        throw new BusinessException("账户类型非法：" + type + "（允许：cash / bank / wechat / alipay）");
    }
}
