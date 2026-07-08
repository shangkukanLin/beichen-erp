package com.beichen.erp.config;

/**
 * 当前请求的公司上下文（ThreadLocal）
 */
public class CompanyContext {
    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    /** 设置当前公司ID，0 表示超管（不过滤租户） */
    public static void set(Long companyId) { if (companyId != null) HOLDER.set(companyId); }

    /** 获取当前公司ID，null 表示未设置（超管/未登录） */
    public static Long get() { return HOLDER.get(); }

    /** 清除 */
    public static void clear() { HOLDER.remove(); }
}
