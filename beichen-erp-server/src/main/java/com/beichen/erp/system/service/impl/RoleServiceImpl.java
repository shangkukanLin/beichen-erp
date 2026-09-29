package com.beichen.erp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.RoleMenu;
import com.beichen.erp.system.entity.UserRole;
import com.beichen.erp.system.mapper.MenuMapper;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.mapper.RoleMenuMapper;
import com.beichen.erp.system.mapper.UserRoleMapper;
import com.beichen.erp.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    @Override
    public List<Role> listEnabled() {
        // S-9②（2026-09-30 批 C · 口径「角色按公司隔离」）：公司上下文只看**本公司**角色；
        // 平台上下文（超管，companyId=0/null）看全部。原先无过滤 ⇒ 迁移后公司之间会互相看到角色。
        Long cid = SystemConstants.tenantCompany();
        return this.list(new LambdaQueryWrapper<Role>()
                .eq(Role::getStatus, 1)
                .eq(cid != null, Role::getCompanyId, cid)
                .orderByAsc(Role::getId));
    }

    @Override
    public List<String> getRoleCodesByUserId(Long userId) {
        List<Long> roleIds = getRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Role> roles = this.list(new LambdaQueryWrapper<Role>()
                .in(Role::getId, roleIds));
        return roles.stream().map(Role::getRoleCode).collect(Collectors.toList());
    }

    @Override
    public List<Long> getRoleIdsByUserId(Long userId) {
        List<UserRole> list = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));
        return list.stream().map(UserRole::getRoleId).collect(Collectors.toList());
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        List<RoleMenu> list = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getRoleId, roleId));
        return list.stream().map(RoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleMenus(Long roleId, List<Long> menuIds) {
        // 校验目标角色归属，防止给越权角色（含平台级内置角色/其他公司角色）授权菜单
        Role role = this.getById(roleId);
        assertOwned(role);
        // 删除旧关联
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getRoleId, roleId));
        // 批量插入新关联，并校验每个菜单归属，防止把其他公司的私有菜单授权出去
        // F8-12：用统一哨兵判据（0/null ⇒ 平台上下文 ⇒ 跳过归属校验）
        Long currentCompany = SystemConstants.tenantCompany();
        if (menuIds != null && !menuIds.isEmpty()) {
            List<RoleMenu> roleMenus = new ArrayList<>();
            for (Long menuId : menuIds) {
                if (currentCompany != null) {
                    Menu menu = menuMapper.selectById(menuId);
                    if (menu != null && menu.getCompanyId() != null
                            && !menu.getCompanyId().equals(currentCompany)
                            && !menu.getCompanyId().equals(SystemConstants.PLATFORM_COMPANY_ID)) {
                        throw new BusinessException(403, "存在无权限授权的菜单");
                    }
                }
                RoleMenu rm = new RoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                roleMenus.add(rm);
            }
            for (RoleMenu rm : roleMenus) {
                roleMenuMapper.insert(rm);
            }
        }
    }

    @Override
    public void assertOwned(Role role) {
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        // 平台/超管上下文可操作全部角色
        // F8-11（2026-09-30 批 C）：原判据是 `CompanyContext.get() == null`，但**超管会话的 companyId 是 0 哨兵**
        // （admin/verify 写入 0）⇒ 超管被当成"公司 0"，平台级角色一律 403 而**谁都改不了**（现网 7 个角色
        // company_id 全为 0 ⇒ 角色菜单授权功能整体不可用）。改用统一判据 SystemConstants.tenantCompany()。
        Long currentCompany = SystemConstants.tenantCompany();
        if (currentCompany == null) {
            return;
        }
        Long roleCompany = role.getCompanyId();
        // 平台级共享角色（companyId 为空或等于哨兵值）仅超管可操作，普通租户不可改/删/授权
        if (roleCompany == null || roleCompany.equals(SystemConstants.PLATFORM_COMPANY_ID)) {
            throw new BusinessException(403, "无权限操作平台级共享角色");
        }
        if (!roleCompany.equals(currentCompany)) {
            throw new BusinessException(403, "无权限操作其他公司的角色");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantMenuToAdminRoles(Long menuId) {
        if (menuId == null) return;
        // 查询 super_admin 和 admin 角色ID
        List<Role> adminRoles = this.list(new LambdaQueryWrapper<Role>()
                .in(Role::getRoleCode, SystemConstants.SUPER_ADMIN_ROLE_CODE, SystemConstants.ADMIN_ROLE_CODE));
        for (Role role : adminRoles) {
            // 检查是否已存在，避免重复
            Long cnt = roleMenuMapper.selectCount(new LambdaQueryWrapper<RoleMenu>()
                    .eq(RoleMenu::getRoleId, role.getId())
                    .eq(RoleMenu::getMenuId, menuId));
            if (cnt == null || cnt == 0) {
                RoleMenu rm = new RoleMenu();
                rm.setRoleId(role.getId());
                rm.setMenuId(menuId);
                roleMenuMapper.insert(rm);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMenuFromAllRoles(Long menuId) {
        if (menuId == null) return;
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getMenuId, menuId));
    }
}
