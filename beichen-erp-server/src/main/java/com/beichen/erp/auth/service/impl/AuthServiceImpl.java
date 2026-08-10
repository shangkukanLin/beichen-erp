package com.beichen.erp.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.auth.entity.LoginDTO;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.auth.service.AuthService;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.Company;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.mapper.CompanyMapper;
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
    private final CompanyMapper companyMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Map<String, Object> login(LoginDTO loginDTO) {
        // 公司ID为必填入参（LoginDTO已校验非空），按"公司+用户名"唯一定位，避免跨公司越权登录
        Long companyId = loginDTO.getCompanyId();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginDTO.getUsername())
                .eq(User::getCompanyId, companyId));
        // 统一提示，防止用户名枚举
        if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        // 存入公司ID到session
        StpUtil.getSession().set("companyId", companyId);

        // 查公司名称，存入 session 和 userInfo
        Company company = companyMapper.selectById(companyId);
        String companyName = company != null ? company.getCompanyName() : "";
        StpUtil.getSession().set("companyName", companyName);

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
        userInfo.put("companyName", companyName);

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
