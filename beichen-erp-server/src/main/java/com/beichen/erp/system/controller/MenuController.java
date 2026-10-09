package com.beichen.erp.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.common.SystemConstants;
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

    @SaCheckRole(value = {SystemConstants.SUPER_ADMIN_ROLE_CODE, SystemConstants.ADMIN_ROLE_CODE}, mode = SaMode.OR)
    @GetMapping("/tree")
    public R<List<Menu>> tree() {
        return R.ok(menuService.getMenuTree());
    }

    @GetMapping("/tree/user")
    public R<List<Menu>> userTree() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<Long> roleIds = roleService.getRoleIdsByUserId(userId);
        return R.ok(menuService.getMenuTreeByRoleIds(roleIds, userId));
    }

    @SaCheckRole(value = {SystemConstants.SUPER_ADMIN_ROLE_CODE, SystemConstants.ADMIN_ROLE_CODE}, mode = SaMode.OR)
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
        // 新增菜单自动授权给 super_admin 和 admin
        roleService.grantMenuToAdminRoles(menu.getId());
        return R.ok();
    }

    @SaCheckRole(value = {SystemConstants.SUPER_ADMIN_ROLE_CODE, SystemConstants.ADMIN_ROLE_CODE}, mode = SaMode.OR)
    @PutMapping
    public R<Void> update(@Valid @RequestBody MenuDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("菜单ID不能为空");
        }
        Menu exist = menuService.getById(dto.getId());
        if (exist == null) {
            throw new BusinessException("菜单不存在");
        }
        // 2026-10-08：改在**已加载的 exist 实体**上更新（原实现上方已查出 exist 却弃之不用，
        // 转而新建一个只填业务字段的裸 Menu）—— 因为 Menu.companyId 标了
        // @TableField(fill = FieldFill.INSERT_UPDATE)，MyBatis-Plus 会把该列**无条件**拼进
        // UPDATE 的 SET（不做 null 判断）⇒ 裸实体会把菜单的公司归属写成 NULL，该菜单随即在公司
        // 视角下"隐身"（与 Role 的「跟单员不显示了」同一根因，见 RoleServiceImpl.saveRoleMenus）。
        exist.setParentId(dto.getParentId());
        exist.setMenuName(dto.getMenuName());
        exist.setMenuType(dto.getMenuType());
        exist.setRoutePath(dto.getRoutePath());
        exist.setRouteName(dto.getRouteName());
        exist.setIcon(dto.getIcon());
        exist.setSortOrder(dto.getSortOrder());
        exist.setVisible(dto.getVisible());
        exist.setStatus(dto.getStatus());
        // 2026-10-09（处理「角色/菜单的修改时间不刷新」）：Menu.companyId 误标的 INSERT_UPDATE 已在实体上
        // 从根修正（见 Menu.java）。updateTime 的刷新需要显式清空 —— exist 是刚从库里读出的对象、
        // updateTime 非空，而 updateFill 用 strictUpdateFill（仅当为 null 才填）⇒ 不清空就写回旧时间。
        exist.setUpdateTime(null);
        menuService.updateById(exist);
        return R.ok();
    }

    @SaCheckRole(value = {SystemConstants.SUPER_ADMIN_ROLE_CODE, SystemConstants.ADMIN_ROLE_CODE}, mode = SaMode.OR)
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
        // 先清理角色菜单关联，再删除菜单
        roleService.removeMenuFromAllRoles(id);
        menuService.removeById(id);
        return R.ok();
    }
}
