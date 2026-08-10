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

    /**
     * 将菜单自动授权给 super_admin 和 admin 角色
     */
    void grantMenuToAdminRoles(Long menuId);

    /**
     * 删除菜单时，从所有角色中移除该菜单关联
     */
    void removeMenuFromAllRoles(Long menuId);

    /**
     * 校验当前登录租户是否有权操作该角色：
     * 超管（CompanyContext 为 null）可操作全部；
     * 普通公司租户仅可操作归属本公司的角色，平台级共享角色（companyId 为 0/空）不可被普通租户改/删/授权。
     * 校验不通过抛出 BusinessException。
     */
    void assertOwned(Role role);
}
