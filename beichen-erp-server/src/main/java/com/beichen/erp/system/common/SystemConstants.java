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

    private SystemConstants() {
    }
}
