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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
            // F3-2（2026-09-18 审核修复）：CUSTOM 但用户级记录为空（仅直改库/清库等异常可达）⇒ 回退角色菜单，
            // 否则该用户登录后"一个菜单都没有"（连首页都点不到）。正常保存路径已保证「至少一项 + 强制保留首页」。
            if (SystemConstants.MENU_MODE_CUSTOM.equals(menuMode) && roleIds != null && !roleIds.isEmpty()) {
                log.warn("用户 {} 为自定义页面权限但无用户级菜单记录，本次回退为角色菜单（请检查数据）", userId);
                roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                        .in(RoleMenu::getRoleId, roleIds)).forEach(rm -> menuIds.add(rm.getMenuId()));
            }
            if (menuIds.isEmpty()) {
                return Collections.emptyList();
            }
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

    @Override
    public List<String> collectPerms(List<Menu> menus) {
        // F3-3（2026-09-18 接口级权限专项）：菜单树 → 权限码集合（去重；递归含子菜单）
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        collectPermsInto(menus, set);
        return new ArrayList<>(set);
    }

    private void collectPermsInto(List<Menu> menus, java.util.Set<String> out) {
        if (menus == null) return;
        for (Menu m : menus) {
            if (m == null) continue;
            String p = m.getPerms();
            if (p != null && !p.isBlank()) out.add(p.trim());
            collectPermsInto(m.getChildren(), out);
        }
    }

    @Override
    public List<String> collectPermsWithButtons(List<Menu> menus) {
        // F3-3 按钮级权限（方案 A）：页面码 ∪ 已授权页面下的按钮码（自动跟随，无需补授数据）
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(collectPerms(menus));
        Set<Long> ids = new HashSet<>();
        collectIds(menus, ids);
        if (!ids.isEmpty()) {
            set.addAll(listButtonPermsByParentIds(ids));
        }
        return new ArrayList<>(set);
    }

    private void collectIds(List<Menu> menus, Set<Long> out) {
        if (menus == null) return;
        for (Menu m : menus) {
            if (m == null) continue;
            if (m.getId() != null) out.add(m.getId());
            collectIds(m.getChildren(), out);
        }
    }

    @Override
    public List<String> listButtonPermsByParentIds(java.util.Collection<Long> pageIds) {
        if (pageIds == null || pageIds.isEmpty()) return new ArrayList<>();
        return this.list(new LambdaQueryWrapper<Menu>()
                        .select(Menu::getPerms)
                        .eq(Menu::getMenuType, "button")
                        .in(Menu::getParentId, pageIds)
                        .isNotNull(Menu::getPerms))
                .stream()
                .map(Menu::getPerms)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listAllPerms() {
        return this.list(new LambdaQueryWrapper<Menu>()
                        .select(Menu::getPerms)
                        .isNotNull(Menu::getPerms))
                .stream()
                .map(Menu::getPerms)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * F8-21（2026-09-30 设置模块批 E 修复）：**界面上的菜单编辑一律打上 {@code customized=1}**。
     *
     * <p>根因回顾：{@code DataInitializer.syncMenus()} 每次启动用 {@code ON DUPLICATE KEY UPDATE}
     * 无条件覆盖 {@code menu_name/parent_id/sort_order/visible/status} ⇒ 用户在「菜单管理」里的改名、
     * 调序、隐藏、停用**下次重启即被打回种子值**（已 E2 实测）。现在的口径：</p>
     * <ul>
     *   <li>{@code customized=0}（从未被用户改过）⇒ 启动同步照旧覆盖展示字段（代码升级自动下发）；</li>
     *   <li>{@code customized=1}（用户改过）⇒ 展示字段以用户为准，只有结构性字段
     *       （menu_type/route_path/route_name/icon）仍随代码同步。</li>
     * </ul>
     * <p>用基类覆盖而不是改 Controller：写菜单的调用方可能不止一处，覆盖 {@code updateById/save}
     * 能一次覆盖全部入口（当前唯一入口是 {@code PUT /api/system/menu}）。</p>
     */
    @Override
    public boolean updateById(Menu entity) {
        if (entity != null) {
            entity.setCustomized(1);
        }
        return super.updateById(entity);
    }

    /**
     * F8-21：用户**新建**的菜单同样标记 {@code customized=1}。
     * <p>新建菜单的自增 id 目前不与种子 id 冲突，但种子里的按钮码（9101+）是预留区 ⇒ 标记后可确保
     * 将来任何 id 撞上时也**不会**把用户自建的菜单覆盖掉。</p>
     */
    @Override
    public boolean save(Menu entity) {
        if (entity != null && entity.getCustomized() == null) {
            entity.setCustomized(1);
        }
        return super.save(entity);
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
