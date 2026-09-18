package com.beichen.erp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.entity.RoleMenu;
import com.beichen.erp.system.entity.UserMenu;
import com.beichen.erp.system.mapper.MenuMapper;
import com.beichen.erp.system.mapper.RoleMenuMapper;
import com.beichen.erp.system.mapper.UserMenuMapper;
import com.beichen.erp.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {

    private final RoleMenuMapper roleMenuMapper;
    private final UserMenuMapper userMenuMapper;
    private final UserMapper userMapper;

    @Override
    public List<Menu> getMenuTree() {
        List<Menu> all = this.list(new LambdaQueryWrapper<Menu>()
                .orderByAsc(Menu::getSortOrder));
        return buildTree(all);
    }

    @Override
    public List<Menu> getMenuTreeByRoleIds(List<Long> roleIds, Long userId) {
        String menuMode = menuModeOf(userId);
        Set<Long> menuIds = new LinkedHashSet<>();
        // CUSTOM：菜单完全以用户级为准（不叠加角色，才能"收权"）；ROLE：角色授权 ∪ 用户级（正常用户级为空，并集兜底）
        if (!SystemConstants.MENU_MODE_CUSTOM.equals(menuMode)) {
            if (roleIds != null && !roleIds.isEmpty()) {
                List<RoleMenu> roleMenus = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                        .in(RoleMenu::getRoleId, roleIds));
                roleMenus.forEach(rm -> menuIds.add(rm.getMenuId()));
            }
        }
        if (userId != null) {
            List<UserMenu> userMenus = userMenuMapper.selectList(new LambdaQueryWrapper<UserMenu>()
                    .eq(UserMenu::getUserId, userId));
            userMenus.forEach(um -> menuIds.add(um.getMenuId()));
        }
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Menu> menus = this.list(new LambdaQueryWrapper<Menu>()
                .in(Menu::getId, menuIds)
                .eq(Menu::getStatus, 1)
                .eq(Menu::getVisible, 1)
                .orderByAsc(Menu::getSortOrder));
        return buildTree(withAncestors(menus));
    }

    /** 读取用户页面权限模式：仅 CUSTOM 生效自定义，其余（含空值/用户不存在）一律 ROLE=跟随角色 */
    private String menuModeOf(Long userId) {
        if (userId == null) {
            return SystemConstants.MENU_MODE_ROLE;
        }
        User user = userMapper.selectById(userId);
        String mode = user == null ? null : user.getMenuMode();
        return (mode != null && SystemConstants.MENU_MODE_CUSTOM.equalsIgnoreCase(mode.trim()))
                ? SystemConstants.MENU_MODE_CUSTOM
                : SystemConstants.MENU_MODE_ROLE;
    }

    /**
     * 补齐祖先链：历史授权数据可能只授权了子菜单、缺父目录，
     * 而 buildTree 只返回 parentId=0 的子树，缺父会导致这些子菜单整体丢失 → 向上递归补齐父节点。
     */
    private List<Menu> withAncestors(List<Menu> menus) {
        List<Menu> result = new ArrayList<>(menus);
        Set<Long> known = menus.stream().map(Menu::getId).collect(Collectors.toSet());
        Set<Long> missing = menus.stream()
                .map(Menu::getParentId)
                .filter(pid -> pid != null && pid != 0L && !known.contains(pid))
                .collect(Collectors.toSet());
        while (!missing.isEmpty()) {
            List<Menu> parents = this.list(new LambdaQueryWrapper<Menu>()
                    .in(Menu::getId, missing)
                    .eq(Menu::getStatus, 1)
                    .eq(Menu::getVisible, 1)
                    .orderByAsc(Menu::getSortOrder));
            if (parents.isEmpty()) {
                break;
            }
            Set<Long> next = new HashSet<>();
            for (Menu p : parents) {
                if (known.add(p.getId())) {
                    result.add(p);
                }
                Long pid = p.getParentId();
                if (pid != null && pid != 0L && !known.contains(pid)) {
                    next.add(pid);
                }
            }
            missing = next;
        }
        return result;
    }

    @Override
    public List<Menu> getAllEnabledMenus() {
        return this.baseMapper.selectAllEnabled();
    }

    /**
     * 构建菜单树：按 parentId 分组，设置 children，返回 parentId=0 的一级菜单
     */
    private List<Menu> buildTree(List<Menu> menus) {
        Map<Long, List<Menu>> groupByParent = menus.stream()
                .collect(Collectors.groupingBy(Menu::getParentId));
        for (Menu menu : menus) {
            menu.setChildren(groupByParent.getOrDefault(menu.getId(), new ArrayList<>()));
        }
        return groupByParent.getOrDefault(0L, new ArrayList<>());
    }
}
