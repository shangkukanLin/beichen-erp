package com.beichen.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.system.entity.Role;

import java.util.List;

public interface RoleService extends IService<Role> {

    /**
     * 查询所有启用状态的角色（下拉用）
     */
    List<Role> listEnabled();

    /**
     * 查询用户的所有角色 code 列表
     */
    List<String> getRoleCodesByUserId(Long userId);

    /**
     * 查询用户的所有角色 id 列表
     */
    List<Long> getRoleIdsByUserId(Long userId);

    /**
     * 查询角色关联的菜单 ID 列表
     */
    List<Long> getMenuIdsByRoleId(Long roleId);

    /**
     * 保存角色菜单关联：先删旧再插新
     */
    void saveRoleMenus(Long roleId, List<Long> menuIds);
}
