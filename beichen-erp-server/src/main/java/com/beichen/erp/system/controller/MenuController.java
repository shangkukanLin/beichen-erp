package com.beichen.erp.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.entity.dto.MenuDTO;
import com.beichen.erp.system.service.MenuService;
import com.beichen.erp.system.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final RoleService roleService;

    @SaCheckRole(value = {"super_admin"}, mode = SaMode.OR)
    @GetMapping("/tree")
    public R<List<Menu>> tree() {
        return R.ok(menuService.getMenuTree());
    }

    @GetMapping("/tree/user")
    public R<List<Menu>> userTree() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<Long> roleIds = roleService.getRoleIdsByUserId(userId);
        return R.ok(menuService.getMenuTreeByRoleIds(roleIds));
    }

    @SaCheckRole(value = {"super_admin"}, mode = SaMode.OR)
    @PostMapping
    public R<Void> add(@Valid @RequestBody MenuDTO dto) {
        Menu menu = new Menu();
        menu.setParentId(dto.getParentId());
        menu.setMenuName(dto.getMenuName());
        menu.setMenuType(dto.getMenuType());
        menu.setRoutePath(dto.getRoutePath());
        menu.setRouteName(dto.getRouteName());
        menu.setIcon(dto.getIcon());
        menu.setSortOrder(dto.getSortOrder());
        menu.setVisible(dto.getVisible());
        menu.setStatus(dto.getStatus());
        menuService.save(menu);
        return R.ok();
    }

    @SaCheckRole(value = {"super_admin"}, mode = SaMode.OR)
    @PutMapping
    public R<Void> update(@Valid @RequestBody MenuDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("菜单ID不能为空");
        }
        Menu exist = menuService.getById(dto.getId());
        if (exist == null) {
            throw new BusinessException("菜单不存在");
        }
        Menu menu = new Menu();
        menu.setId(dto.getId());
        menu.setParentId(dto.getParentId());
        menu.setMenuName(dto.getMenuName());
        menu.setMenuType(dto.getMenuType());
        menu.setRoutePath(dto.getRoutePath());
        menu.setRouteName(dto.getRouteName());
        menu.setIcon(dto.getIcon());
        menu.setSortOrder(dto.getSortOrder());
        menu.setVisible(dto.getVisible());
        menu.setStatus(dto.getStatus());
        menuService.updateById(menu);
        return R.ok();
    }

    @SaCheckRole(value = {"super_admin"}, mode = SaMode.OR)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Menu menu = menuService.getById(id);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        long childCount = menuService.lambdaQuery()
                .eq(Menu::getParentId, id)
                .count();
        if (childCount > 0) {
            throw new BusinessException("存在子菜单，不可删除");
        }
        menuService.removeById(id);
        return R.ok();
    }
}
