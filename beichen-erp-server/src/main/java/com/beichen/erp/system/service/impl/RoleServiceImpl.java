package com.beichen.erp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.RoleMenu;
import com.beichen.erp.system.entity.UserRole;
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

    @Override
    public List<Role> listEnabled() {
        return this.list(new LambdaQueryWrapper<Role>()
                .eq(Role::getStatus, 1)
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
        // 删除旧关联
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getRoleId, roleId));
        // 批量插入新关联
        if (menuIds != null && !menuIds.isEmpty()) {
            List<RoleMenu> roleMenus = new ArrayList<>();
            for (Long menuId : menuIds) {
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
}
