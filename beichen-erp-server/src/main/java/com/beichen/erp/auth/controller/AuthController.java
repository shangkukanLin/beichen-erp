package com.beichen.erp.auth.controller;

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
}
