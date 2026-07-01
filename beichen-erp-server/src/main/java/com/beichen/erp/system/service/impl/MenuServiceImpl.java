package com.beichen.erp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.entity.RoleMenu;
import com.beichen.erp.system.mapper.MenuMapper;
import com.beichen.erp.system.mapper.RoleMenuMapper;
import com.beichen.erp.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {

    private final RoleMenuMapper roleMenuMapper;

    @Override
    public List<Menu> getMenuTree() {
        List<Menu> all = this.list(new LambdaQueryWrapper<Menu>()
                .orderByAsc(Menu::getSortOrder));
        return buildTree(all);
    }

    @Override
    public List<Menu> getMenuTreeByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<RoleMenu> roleMenus = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                .in(RoleMenu::getRoleId, roleIds));
        List<Long> menuIds = roleMenus.stream()
                .map(RoleMenu::getMenuId)
                .distinct()
                .collect(Collectors.toList());
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Menu> menus = this.list(new LambdaQueryWrapper<Menu>()
                .in(Menu::getId, menuIds)
                .eq(Menu::getStatus, 1)
                .eq(Menu::getVisible, 1)
                .orderByAsc(Menu::getSortOrder));
        return buildTree(menus);
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
