package com.beichen.erp.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.service.MenuService;
import com.beichen.erp.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限/角色实现
 * <ul>
 *   <li>{@code @SaCheckRole} ← session 中的角色 codes（登录时写入，见 AuthServiceImpl.login）</li>
 *   <li>{@code @SaCheckPermission} ← session 中的**页面级接口权限码**（F3-3 专项，2026-09-18）</li>
 * </ul>
 *
 * <p>权限口径：权限码 = 该用户**可见菜单**的 {@code sys_menu.perms} 集合（与侧栏同源 ⇒
 * "看得见的页面，其接口一定调得通；看不见的页面，接口拒绝"）。超级管理员角色
 * （{@link SystemConstants#SUPER_ADMIN_ROLE_CODE}）自身不挂菜单，走全量码兜底。</p>
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final MenuService menuService;
    private final RoleService roleService;

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 优先读登录时写入 session 的权限码（零查询）
        try {
            Object perms = StpUtil.getSession().get("perms");
            if (perms instanceof List) {
                return (List<String>) perms;
            }
        } catch (Exception e) {
            // 会话未建立等情况：继续走下面的回源兜底
        }
        // 兜底：升级前建立的旧会话没有 perms（内存 session 重启即失效，此处仅为稳妥），按角色现场回源并缓存
        try {
            long userId = Long.parseLong(String.valueOf(loginId));
            List<String> roleCodes = getRoleList(loginId, loginType);
            List<String> computed = roleCodes.contains(SystemConstants.SUPER_ADMIN_ROLE_CODE)
                    ? menuService.listAllPerms()
                    : menuService.collectPermsWithButtons(
                            menuService.getMenuTreeByRoleIds(roleService.getRoleIdsByUserId(userId), userId));
            StpUtil.getSession().set("perms", computed);
            return computed;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            Object roles = StpUtil.getSession().get("roles");
            if (roles instanceof List) {
                return (List<String>) roles;
            }
        } catch (Exception e) {
            // 会话未建立等情况返回空列表
        }
        return new ArrayList<>();
    }
}
