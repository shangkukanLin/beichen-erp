package com.beichen.erp.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.Company;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.service.CompanyService;
import com.beichen.erp.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 验证超级管理员凭证，返回 token */
    @PostMapping("/admin/verify")
    public R<Map<String, Object>> verifyAdmin(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null) throw new BusinessException("账号不存在");
        if (!passwordEncoder.matches(password, user.getPassword())) throw new BusinessException("密码错误");
        if (user.getStatus() != null && user.getStatus() == 0) throw new BusinessException("账号已被禁用");
        List<Long> roleIds = roleService.getRoleIdsByUserId(user.getId());
        if (!roleIds.contains(getSuperAdminRoleId())) throw new BusinessException("无超级管理员权限");
        // 登录超管
        StpUtil.login(user.getId());
        // 超管公司ID设为0，不受租户限制
        StpUtil.getSession().set("companyId", 0L);
        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenInfo().tokenValue);
        return R.ok(result);
    }

    /** 获取所有公司（需登录） */
    @GetMapping("/list")
    public R<List<Company>> list() {
        return R.ok(companyService.listAll());
    }

    /** 获取单条 */
    @GetMapping("/{id}")
    public R<Company> getById(@PathVariable Long id) {
        return R.ok(companyService.getById(id));
    }

    /** 创建公司（仅超管） */
    @PostMapping
    public R<Void> create(@RequestBody Company company) {
        checkSuperAdmin();
        companyService.create(company);
        return R.ok();
    }

    /** 更新公司（仅超管） */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Company company) {
        checkSuperAdmin();
        company.setId(id);
        companyService.update(company);
        return R.ok();
    }

    /** 删除公司（仅超管） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        checkSuperAdmin();
        companyService.delete(id);
        return R.ok();
    }

    private void checkSuperAdmin() {
        long userId = StpUtil.getLoginIdAsLong();
        List<Long> roleIds = roleService.getRoleIdsByUserId(userId);
        if (!roleIds.contains(getSuperAdminRoleId())) throw new BusinessException("仅超级管理员可操作");
    }

    private Long getSuperAdminRoleId() {
        Role superAdmin = roleMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "super_admin"));
        return superAdmin != null ? superAdmin.getId() : 1L;
    }
}
