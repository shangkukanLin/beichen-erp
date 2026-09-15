package com.beichen.erp.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.common.R;
import com.beichen.erp.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页业务 TAB 可见性（登录用户自查）
 */
@RestController
@RequestMapping("/api/system/dashboard-tabs")
@RequiredArgsConstructor
public class DashboardTabController {

    private final UserService userService;

    /** 当前用户的 TAB 勾选；空列表=全部可见 */
    @GetMapping("/mine")
    public R<List<String>> mine() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.getDashboardTabsByUserId(userId));
    }
}
