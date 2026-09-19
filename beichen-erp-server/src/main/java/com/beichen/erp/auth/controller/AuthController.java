package com.beichen.erp.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.auth.entity.LoginDTO;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.service.AuthService;
import com.beichen.erp.common.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO) {
        return R.ok(authService.login(loginDTO));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    @GetMapping("/info")
    public R<User> info() {
        return R.ok(authService.getCurrentUser());
    }

    /**
     * F3-3（2026-09-18）：当前登录用户的**接口权限码**（页面码 + 按钮动作码）。
     * <p>登录响应里已带 {@code userInfo.perms}；此端点供前端刷新页面时重新取一次（前端按钮 gating 用）。</p>
     */
    @GetMapping("/perms")
    @SuppressWarnings("unchecked")
    public R<List<String>> perms() {
        Object perms = StpUtil.getSession().get("perms");
        if (perms instanceof List) {
            return R.ok((List<String>) perms);
        }
        return R.ok(List.of());
    }

    /** 获取登录公司名称（从 session 读取，刷新页面后仍可用） */
    @GetMapping("/company-name")
    public R<String> companyName() {
        String name = StpUtil.getSession().getString("companyName");
        return R.ok(name != null ? name : "");
    }
}
