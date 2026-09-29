package com.beichen.erp.system.common;

/**
 * 系统模块常量：内置角色编码、超管用户名、平台级公司哨兵
 */
public final class SystemConstants {

    /** 超级管理员角色编码 */
    public static final String SUPER_ADMIN_ROLE_CODE = "super_admin";

    /** 管理员角色编码 */
    public static final String ADMIN_ROLE_CODE = "admin";

    /** 普通用户角色编码 */
    public static final String USER_ROLE_CODE = "user";

    /** 超级管理员用户名 */
    public static final String SUPER_ADMIN_USERNAME = "lin";

    /** 平台级（共享）公司ID哨兵：角色/菜单等共享资源的 company_id 取此值表示平台级 */
    public static final Long PLATFORM_COMPANY_ID = 0L;

    /** 页面权限模式：跟随角色（默认）——菜单取自角色授权，sys_user_menu 应为空 */
    public static final String MENU_MODE_ROLE = "ROLE";

    /** 页面权限模式：自定义——菜单**完全以 sys_user_menu 为准**（可加可减，不叠加角色） */
    public static final String MENU_MODE_CUSTOM = "CUSTOM";

    /**
     * 当前租户公司 —— **统一哨兵判据**（F8-12，2026-09-30 设置模块审核批 C）。
     *
     * <p>口径：`CompanyContext` 为 `null` **或平台哨兵 {@link #PLATFORM_COMPANY_ID}（0）** 时一律返回 `null`，
     * 表示"**平台/超管上下文 ⇒ 不做公司过滤**"。为什么需要它：超管会话的 companyId 是 **0**（`admin/verify`
     * 写入），而 `RoleServiceImpl.assertOwned` 原判据是 `CompanyContext.get() == null` ⇒ 超管被当成"公司 0"，
     * 连平台级角色都改不了（F8-11）；同一工程里 `UserServiceImpl.tenantCompany()` 早已是"0 视为不过滤"，
     * 两处口径不一致（F8-12）。本方法即该口径的唯一实现。</p>
     */
    public static Long tenantCompany() {
        Long cid = com.beichen.erp.config.CompanyContext.get();
        return (cid == null || cid.equals(PLATFORM_COMPANY_ID)) ? null : cid;
    }

    private SystemConstants() {
    }
}
