package com.beichen.erp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.UserRole;
import com.beichen.erp.system.entity.dto.ResetPasswordDTO;
import com.beichen.erp.system.entity.dto.UserDTO;
import com.beichen.erp.system.entity.dto.UserQueryDTO;
import com.beichen.erp.system.entity.vo.UserVO;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.mapper.UserRoleMapper;
import com.beichen.erp.system.service.RoleService;
import com.beichen.erp.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Page<UserVO> page(UserQueryDTO query) {
        // 若指定了 roleId，先查关联表得到 userIds
        List<Long> userIds = null;
        if (query.getRoleId() != null) {
            List<UserRole> urs = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getRoleId, query.getRoleId()));
            userIds = urs.stream().map(UserRole::getUserId).toList();
            if (userIds.isEmpty()) {
                Page<UserVO> emptyPage = new Page<>(query.getPageNum(), query.getPageSize(), 0);
                emptyPage.setRecords(Collections.emptyList());
                return emptyPage;
            }
        }

        Page<User> page = new Page<>(query.getPageNum(), query.getPageSize());
        // 普通公司租户仅可见本公司用户，超管（CompanyContext 为 null）可见全部
        Long currentCompany = CompanyContext.get();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(currentCompany != null, User::getCompanyId, currentCompany)
                .like(query.getUsername() != null && !query.getUsername().isBlank(),
                        User::getUsername, query.getUsername())
                .like(query.getPhone() != null && !query.getPhone().isBlank(),
                        User::getPhone, query.getPhone())
                .eq(query.getStatus() != null, User::getStatus, query.getStatus())
                .in(userIds != null, User::getId, userIds)
                .orderByDesc(User::getId);
        Page<User> userPage = baseMapper.selectPage(page, wrapper);

        Page<UserVO> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        List<UserVO> records = new ArrayList<>();
        for (User u : userPage.getRecords()) {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(u, vo);
            vo.setRoles(getRolesByUserId(u.getId()));
            records.add(vo);
        }
        result.setRecords(records);
        return result;
    }

    @Override
    public UserVO getUserById(Long id) {
        Long currentCompany = CompanyContext.get();
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<User>().eq(User::getId, id);
        if (currentCompany != null) {
            w.eq(User::getCompanyId, currentCompany);
        }
        User user = baseMapper.selectOne(w);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setRoles(getRolesByUserId(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUser(UserDTO dto) {
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        // 用户名查重限定本公司内，避免跨公司用户名冲突误判
        Long currentCompany = CompanyContext.get();
        LambdaQueryWrapper<User> dupWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername());
        if (currentCompany != null) {
            dupWrapper.eq(User::getCompanyId, currentCompany);
        }
        Long count = baseMapper.selectCount(dupWrapper);
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setDept(dto.getDept());
        user.setStatus(dto.getStatus());
        // 归属当前租户；超管上下文为 null 时不设置 companyId（留待后续明确）
        user.setCompanyId(currentCompany);
        baseMapper.insert(user);

        saveUserRoles(user.getId(), dto.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
        User exist = baseMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验归属本公司，防止越权改其他公司用户
        if (CompanyContext.get() != null && !CompanyContext.get().equals(exist.getCompanyId())) {
            throw new BusinessException("无权限操作该用户");
        }
        // 不改密码不改 username
        User update = new User();
        update.setId(dto.getId());
        update.setPhone(dto.getPhone());
        update.setDept(dto.getDept());
        update.setStatus(dto.getStatus());
        baseMapper.updateById(update);

        // 替换角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, dto.getId()));
        saveUserRoles(dto.getId(), dto.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        User user = baseMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验归属本公司，防止越权删其他公司用户
        if (CompanyContext.get() != null && !CompanyContext.get().equals(user.getCompanyId())) {
            throw new BusinessException("无权限操作该用户");
        }
        if (SystemConstants.SUPER_ADMIN_USERNAME.equals(user.getUsername())) {
            throw new BusinessException("超级管理员不可删除");
        }
        baseMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, id));
    }

    @Override
    public void resetPassword(ResetPasswordDTO dto) {
        User user = baseMapper.selectById(dto.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验归属本公司，防止越权重置其他公司用户密码
        if (CompanyContext.get() != null && !CompanyContext.get().equals(user.getCompanyId())) {
            throw new BusinessException("无权限操作该用户");
        }
        User update = new User();
        update.setId(dto.getId());
        update.setPassword(passwordEncoder.encode(dto.getPassword()));
        baseMapper.updateById(update);
    }

    @Override
    public void toggleStatus(Long id) {
        User user = baseMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验归属本公司，防止越权禁用其他公司用户
        if (CompanyContext.get() != null && !CompanyContext.get().equals(user.getCompanyId())) {
            throw new BusinessException("无权限操作该用户");
        }
        if (SystemConstants.SUPER_ADMIN_USERNAME.equals(user.getUsername())) {
            throw new BusinessException("超级管理员不可禁用");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(user.getStatus() != null && user.getStatus() == 1 ? 0 : 1);
        baseMapper.updateById(update);
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        // 查询超级管理员角色ID，禁止通过用户管理分配
        Role superAdminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, SystemConstants.SUPER_ADMIN_ROLE_CODE));
        Long superAdminId = superAdminRole != null ? superAdminRole.getId() : null;
        for (Long roleId : roleIds) {
            if (superAdminId != null && roleId.equals(superAdminId)) {
                continue; // 跳过 super_admin
            }
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    private List<Role> getRolesByUserId(Long userId) {
        List<Long> roleIds = roleService.getRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleService.listByIds(roleIds);
    }
}
