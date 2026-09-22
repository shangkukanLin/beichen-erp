package com.beichen.erp.config;

/**
 * 当前请求的操作人上下文（ThreadLocal）。
 *
 * <p>2026-09-23 新增（用户口径：所有单据生成的详情都要显示「制单人 / 审核人」）：
 * 全站需要一处统一取"当前登录用户"，避免各 Service 各自复制 {@code getCurrentUserId()/getCurrentUserName()}。</p>
 *
 * <p>与 {@link CompanyContext} 同范式：在 {@code SaTokenConfig} 的登录拦截器里写入，请求结束
 * {@code afterCompletion} 清理，防止线程复用串号。</p>
 *
 * <p>取值来源 = sa-token 登录态（userId）+ 会话中的 username。{@code sys_user} 表没有 nickname/real_name 字段，
 * 故展示名就是 {@code username}。</p>
 */
public class UserContext {

    private static final ThreadLocal<Long> ID = new ThreadLocal<>();
    private static final ThreadLocal<String> NAME = new ThreadLocal<>();

    /** 设置当前操作人（拦截器里调用） */
    public static void set(Long userId, String userName) {
        if (userId != null && userId > 0) ID.set(userId);
        if (userName != null && !userName.isBlank()) NAME.set(userName);
    }

    /** 当前操作人 ID；null = 未登录（如内部调用/定时任务/登录接口本身） */
    public static Long getId() {
        return ID.get();
    }

    /** 当前操作人显示名（= sys_user.username）；null = 未登录 */
    public static String getName() {
        return NAME.get();
    }

    /** 清除（请求结束时调用，避免线程池复用串号） */
    public static void clear() {
        ID.remove();
        NAME.remove();
    }
}
