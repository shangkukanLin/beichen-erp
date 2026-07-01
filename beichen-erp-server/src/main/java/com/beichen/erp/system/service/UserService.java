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
}
