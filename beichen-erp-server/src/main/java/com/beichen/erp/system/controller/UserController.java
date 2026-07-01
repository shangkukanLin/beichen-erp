package com.beichen.erp.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.system.entity.dto.ResetPasswordDTO;
import com.beichen.erp.system.entity.dto.UserDTO;
import com.beichen.erp.system.entity.dto.UserQueryDTO;
import com.beichen.erp.system.entity.vo.UserVO;
import com.beichen.erp.system.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/user")
@SaCheckRole(value = {"super_admin", "admin"}, mode = SaMode.OR)
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/page")
    public R<Page<UserVO>> page(UserQueryDTO query) {
        return R.ok(userService.page(query));
    }

    @GetMapping("/{id}")
    public R<UserVO> getById(@PathVariable Long id) {
        return R.ok(userService.getUserById(id));
    }

    @PostMapping
    public R<Void> add(@Valid @RequestBody UserDTO dto) {
        userService.createUser(dto);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody UserDTO dto) {
        userService.updateUser(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return R.ok();
    }

    @PutMapping("/reset-password")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        userService.toggleStatus(id);
        return R.ok();
    }
}
