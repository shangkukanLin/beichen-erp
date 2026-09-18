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
}
