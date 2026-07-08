package com.beichen.erp.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.auth.entity.LoginDTO;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.auth.service.AuthService;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.service.MenuService;
import com.beichen.erp.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleService roleService;
    private final MenuService menuService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Map<String, Object> login(LoginDTO loginDTO) {
        // 验证公司存在且用户属于该公司
        Long companyId = loginDTO.getCompanyId();
        if (companyId == null) {
            // 默认使用公司1（向后兼容）
            companyId = 1L;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginDTO.getUsername()));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 验证用户属于该公司
        if (user.getCompanyId() != null && !user.getCompanyId().equals(companyId)) {
            throw new BusinessException("用户不属于该公司");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        // 存入公司ID到session
        StpUtil.getSession().set("companyId", companyId);

        // 查询角色 codes，存入 session 供 @SaCheckRole 使用
        List<String> roleCodes = roleService.getRoleCodesByUserId(user.getId());
        StpUtil.getSession().set("roles", roleCodes);

        // 查询用户有权限的菜单树
        List<Long> roleIds = roleService.getRoleIdsByUserId(user.getId());
        List<Menu> menus = menuService.getMenuTreeByRoleIds(roleIds);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("phone", user.getPhone());
        userInfo.put("dept", user.getDept());
        userInfo.put("status", user.getStatus());
        userInfo.put("roles", roleCodes);
        userInfo.put("companyId", companyId);

        Map<String, Object> result = new HashMap<>();
        result.put("token", tokenInfo.tokenValue);
        result.put("userInfo", userInfo);
        result.put("menus", menus);
        return result;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public User getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }
}
