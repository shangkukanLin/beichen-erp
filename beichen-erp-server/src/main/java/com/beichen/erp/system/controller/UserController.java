package com.beichen.erp.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.entity.dto.ResetPasswordDTO;
import com.beichen.erp.system.entity.dto.UserDTO;
import com.beichen.erp.system.entity.dto.UserQueryDTO;
import com.beichen.erp.system.entity.vo.UserMenuPermVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/user")
@SaCheckRole(value = {SystemConstants.SUPER_ADMIN_ROLE_CODE, SystemConstants.ADMIN_ROLE_CODE}, mode = SaMode.OR)
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

    /**
     * 启用/停用用户（F8-27，2026-09-30 本轮修复）：**接受可选的目标状态**。
     * <p>界面 {@code user/index.vue} 会算出目标状态并调用 {@code toggleUserStatus(id, next)}，
     * 而原实现**完全忽略请求体**、由服务端自行翻转 ⇒ 并发操作或重复点击时会出现"点了禁用却变成启用"
     * 且不报错。现在传入 {@code {status}} 时按其执行；为空时兼容旧行为（翻转）。</p>
     */
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id,
                                @org.springframework.web.bind.annotation.RequestBody(required = false)
                                java.util.Map<String, Object> body) {
        Integer target = null;
        if (body != null && body.get("status") != null) {
            target = Integer.valueOf(String.valueOf(body.get("status")));
        }
        userService.toggleStatus(id, target);
        return R.ok();
    }

    /** 按角色推导首页业务 TAB 默认勾选（新增用户弹窗用） */
    @GetMapping("/default-dashboard-tabs")
    public R<List<String>> defaultDashboardTabs(@RequestParam("roleIds") List<Long> roleIds) {
        return R.ok(userService.deriveDefaultDashboardTabs(roleIds));
    }

    /** 查询某用户的页面权限（menuMode + 自定义菜单 + 角色菜单并集），「页面权限」弹窗用（与角色权限接口对称） */
    @GetMapping("/{id}/menus")
    public R<UserMenuPermVO> getUserMenus(@PathVariable Long id) {
        return R.ok(userService.getUserMenuPerm(id));
    }

    /** 保存某用户的页面权限：ROLE=跟随角色（清空自定义）/ CUSTOM=以用户级勾选为准 */
    @PutMapping("/{id}/menus")
    public R<Void> saveUserMenus(@PathVariable Long id, @RequestBody UserMenuPermVO body) {
        userService.saveUserMenuPerm(id, body.getMenuMode(), body.getMenuIds());
        return R.ok();
    }
}
