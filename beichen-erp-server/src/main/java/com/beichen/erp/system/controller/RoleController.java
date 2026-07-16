package com.beichen.erp.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.RoleMenu;
import com.beichen.erp.system.entity.UserRole;
import com.beichen.erp.system.entity.dto.RoleDTO;
import com.beichen.erp.system.mapper.RoleMenuMapper;
import com.beichen.erp.system.mapper.UserRoleMapper;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/role")
@SaCheckRole(value = {"super_admin"}, mode = SaMode.OR)
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;

    @GetMapping("/page")
    public R<Page<Role>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Integer status) {
        Page<Role> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .like(roleName != null && !roleName.isBlank(), Role::getRoleName, roleName)
                .eq(status != null, Role::getStatus, status)
                .orderByDesc(Role::getId);
        return R.ok(roleService.page(page, wrapper));
    }

    @GetMapping("/enabled")
    public R<List<Role>> enabled() {
        return R.ok(roleService.listEnabled());
    }

    @PostMapping
    public R<Void> add(@Valid @RequestBody RoleDTO dto) {
        Long count = roleService.lambdaQuery()
                .eq(Role::getRoleCode, dto.getRoleCode())
                .count();
        if (count != null && count > 0) {
            throw new BusinessException("角色编码已存在");
        }
        Role role = new Role();
        role.setRoleName(dto.getRoleName());
        role.setRoleCode(dto.getRoleCode());
        role.setStatus(dto.getStatus());
        role.setRemark(dto.getRemark());
        roleService.save(role);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody RoleDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        Role exist = roleService.getById(dto.getId());
        if (exist == null) {
            throw new BusinessException("角色不存在");
        }
        Long count = roleService.lambdaQuery()
                .eq(Role::getRoleCode, dto.getRoleCode())
                .ne(Role::getId, dto.getId())
                .count();
        if (count != null && count > 0) {
            throw new BusinessException("角色编码已存在");
        }
        Role role = new Role();
        role.setId(dto.getId());
        role.setRoleName(dto.getRoleName());
        role.setRoleCode(dto.getRoleCode());
        role.setStatus(dto.getStatus());
        role.setRemark(dto.getRemark());
        roleService.updateById(role);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        String code = role.getRoleCode();
        if ("super_admin".equals(code) || "admin".equals(code) || "user".equals(code)) {
            throw new BusinessException("内置角色不可删除");
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, id));
        if (count != null && count > 0) {
            throw new BusinessException("该角色下存在" + count + "个用户关联，不可删除");
        }
        // 清理角色菜单关联
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, id));
        roleService.removeById(id);
        return R.ok();
    }

    @GetMapping("/{id}/menus")
    public R<List<Long>> getMenus(@PathVariable Long id) {
        return R.ok(roleService.getMenuIdsByRoleId(id));
    }

    @PutMapping("/{id}/menus")
    public R<Void> saveMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.saveRoleMenus(id, menuIds);
        return R.ok();
    }
}
