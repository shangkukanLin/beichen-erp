package com.beichen.erp.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.RoleMenu;
import com.beichen.erp.system.entity.UserRole;
import com.beichen.erp.system.entity.dto.RoleDTO;
import com.beichen.erp.system.mapper.RoleMenuMapper;
import com.beichen.erp.system.mapper.UserRoleMapper;
import com.beichen.erp.system.common.SystemConstants;
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
@SaCheckRole(value = {SystemConstants.SUPER_ADMIN_ROLE_CODE, SystemConstants.ADMIN_ROLE_CODE}, mode = SaMode.OR)
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
        // S-9②（2026-09-30 批 C）：公司上下文只看本公司角色（原实现无过滤 ⇒ 迁移后跨公司可见）
        Long cid = SystemConstants.tenantCompany();
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .eq(cid != null, Role::getCompanyId, cid)
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
        // S-9②（批 C · 口径「角色按公司隔离」）：角色必须归属到**具体公司** ⇒ 平台上下文（超管未选公司）
        // 直接拒绝，避免再产生 company_id=0 的"客户端口角色"（这正是现网 7 个角色全为 0 的成因）。
        Long cid = SystemConstants.tenantCompany();
        if (cid == null) {
            throw new BusinessException("请先选择公司（当前为平台上下文，无法新建公司角色）");
        }
        Long count = roleService.lambdaQuery()
                .eq(Role::getCompanyId, cid)
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
        role.setCompanyId(cid);
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
        // 校验当前租户是否有权操作该角色，防止跨公司越权改角色
        roleService.assertOwned(exist);
        if (SystemConstants.SUPER_ADMIN_ROLE_CODE.equals(exist.getRoleCode())
                || SystemConstants.ADMIN_ROLE_CODE.equals(exist.getRoleCode())) {
            throw new BusinessException("内置角色不可编辑");
        }
        // S-9②：编码唯一性按**公司内**判定（与 uk_role_code 改为 (company_id, role_code) 一致）
        Long count = roleService.lambdaQuery()
                .eq(Role::getCompanyId, exist.getCompanyId())
                .eq(Role::getRoleCode, dto.getRoleCode())
                .ne(Role::getId, dto.getId())
                .count();
        if (count != null && count > 0) {
            throw new BusinessException("角色编码已存在");
        }
        // 2026-10-08：改在**已加载的 exist 实体**上更新，不再新建"只填 5 个字段"的 Role ——
        // 因为 Role.companyId 是 @TableField(fill = FieldFill.INSERT_UPDATE)，MyBatis-Plus 会把它
        // **无条件**拼进 UPDATE 的 SET 子句（不加 null 判断）⇒ 新建实体会把 company_id 写成 NULL，
        // 该角色随即在公司视角下"隐身"（与 RoleServiceImpl.saveRoleMenus 同一根因）。
        // exist 来自库中，companyId / customizedMenu 等原值原样带回，不会被抹掉。
        exist.setRoleName(dto.getRoleName());
        exist.setRoleCode(dto.getRoleCode());
        exist.setStatus(dto.getStatus());
        exist.setRemark(dto.getRemark());
        // 2026-10-09（处理「角色/菜单的修改时间不刷新」）：
        // Role.companyId 误标的 INSERT_UPDATE 已在实体上从根修正（见 Role.java），上面那段
        // "必须先加载 exist 把 companyId 带回去"的顾虑已消失（本写法仍保留 —— 更稳，且不依赖实体标注）。
        // 但 updateTime 的刷新还差这一步：exist 是**刚从库里读出来的对象**，其 updateTime 非空，
        // 而 MybatisPlusConfig 的 updateFill 用的是 strictUpdateFill（**仅当字段为 null 才填**）
        // ⇒ 不清空就仍把旧时间原样写回。置 null 让填充器重新盖章为当前时间。
        exist.setUpdateTime(null);
        roleService.updateById(exist);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        // 校验当前租户是否有权操作该角色，防止跨公司越权删角色
        roleService.assertOwned(role);
        String code = role.getRoleCode();
        if (SystemConstants.SUPER_ADMIN_ROLE_CODE.equals(code)
                || SystemConstants.ADMIN_ROLE_CODE.equals(code)
                || SystemConstants.USER_ROLE_CODE.equals(code)) {
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
