package com.beichen.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.system.entity.Menu;

import java.util.List;

public interface MenuService extends IService<Menu> {

    /**
     * 构建完整菜单树（两级）
     */
    List<Menu> getMenuTree();

    /**
     * 按角色 + 用户级自定义权限查菜单树（登录返回 / 前端刷新 / 超管切公司 共用）。
     * <p>口径（2026-09-18 起）：</p>
     * <ul>
     *   <li>{@code sys_user.menu_mode = CUSTOM} ⇒ 可见菜单**完全以 sys_user_menu 为准**（不叠加角色，可加可减）</li>
     *   <li>否则（ROLE，默认）⇒ 角色授权菜单 ∪ 该用户级记录（正常用户级为空，并集仅为容错）</li>
     * </ul>
     * 两种模式都只回 {@code status=1 AND visible=1}，并自动补齐祖先目录（避免菜单组整组丢失）。
     *
     * @param roleIds 用户拥有的角色 id（CUSTOM 模式下不参与计算）
     * @param userId  用户 id（为 null 时按纯角色口径）
     */
    List<Menu> getMenuTreeByRoleIds(List<Long> roleIds, Long userId);

    /**
     * 所有启用可见的菜单平列表
     */
    List<Menu> getAllEnabledMenus();

    /**
     * F3-3（2026-09-18 接口级权限专项）：从菜单树收集**页面级接口权限码**（去重，忽略 null/空白）。
     * <p>权限码定义在 {@code sys_menu.perms}（仅 menu_type=menu 的页面行有值，目录为 null）。
     * 用户的有效权限 = 其**可见菜单**的 perms 集合 ⇒ 与本方法传入的菜单树同源，
     * 保证"看得见的页面，其接口一定调得通；看不见的页面，接口拒绝"。</p>
     */
    List<String> collectPerms(List<Menu> menus);

    /**
     * F3-3：全库所有已配置的权限码（供**超级管理员**兜底 —— super_admin 角色自身不挂菜单，
     * 若按菜单求权限会得到空集而被误拦）。
     */
    List<String> listAllPerms();

    /**
     * F3-3 按钮级权限（方案 A）：页面码 + 其下**按钮码**。动作码跟随页面自动带出 ⇒ 不改变任何授权数据。
     * <p>按钮码来自 {@code sys_menu} 中 {@code menu_type='button'} 行（{@code parent_id} = 所属页面菜单 id），
     * 由 {@code DataInitializer.initButtonPerms()} 登记。</p>
     */
    List<String> collectPermsWithButtons(List<Menu> menus);

    /** 取指定页面下的按钮权限码（{@code parent_id ∈ pageIds}） */
    List<String> listButtonPermsByParentIds(java.util.Collection<Long> pageIds);
}
