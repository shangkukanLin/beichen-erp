package com.beichen.erp.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.system.entity.dto.ResetPasswordDTO;
import com.beichen.erp.system.entity.dto.UserDTO;
import com.beichen.erp.system.entity.dto.UserQueryDTO;
import com.beichen.erp.system.entity.vo.UserVO;

public interface UserService extends IService<User> {

    /**
     * 分页查询用户（带角色列表）
     */
    Page<UserVO> page(UserQueryDTO query);

    /**
     * 查询用户详情（含角色列表）
     */
    UserVO getUserById(Long id);

    /**
     * 新增用户
     */
    void createUser(UserDTO dto);

    /**
     * 编辑用户（不改密码不改 username）
     */
    void updateUser(UserDTO dto);

    /**
     * 删除用户（逻辑删除 + 删角色关联）
     */
    void deleteUser(Long id);

    /**
     * 重置密码
     */
    void resetPassword(ResetPasswordDTO dto);

    /**
     * 切换启用/禁用状态
     */
    void toggleStatus(Long id);

    /**
     * 查询用户首页业务 TAB 勾选（无记录=全部可见）
     */
    java.util.List<String> getDashboardTabsByUserId(Long userId);

    /**
     * 按角色集合推导首页业务 TAB 默认勾选（与首页可见性同一套映射规则，多角色取并集）
     */
    java.util.List<String> deriveDefaultDashboardTabs(java.util.List<Long> roleIds);

    /**
     * 查询用户的页面权限配置（用户管理「页面权限」弹窗用）：
     * {@code menuMode}（ROLE/CUSTOM）+ 自定义菜单集合 + 当前角色菜单并集（前端切「自定义」时的初始勾选）。
     */
    com.beichen.erp.system.entity.vo.UserMenuPermVO getUserMenuPerm(Long userId);

    /**
     * 保存用户页面权限（只改该用户的菜单可见性，不动角色/接口鉴权）。
     * <p>护栏：①不能改自己（防自锁）②super_admin 用户只能跟随角色 ③CUSTOM 时自动补齐祖先目录并保留「首页」。</p>
     */
    void saveUserMenuPerm(Long userId, String menuMode, java.util.List<Long> menuIds);
}
