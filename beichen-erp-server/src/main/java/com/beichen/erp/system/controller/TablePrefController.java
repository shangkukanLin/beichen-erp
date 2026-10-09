package com.beichen.erp.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.common.R;
import com.beichen.erp.system.entity.dto.TablePrefDTO;
import com.beichen.erp.system.service.UserTablePrefService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户表格列宽偏好（2026-10-09）：所有列表的列宽，用户拖动调整后记住，下次进来仍是这个宽度。
 *
 * <p><b>口径</b>：跟用户走、不跟公司走（同一用户换公司共用一套）。任何登录用户只能读写**自己**的偏好。</p>
 *
 * <p><b>权限</b>：本控制器落在 {@code /api/system} 前缀下，而 {@code ApiPermGuard} 把该前缀列为
 * "已由角色注解保护、不重复收口"（见其 RULES 上方注释）。本接口**刻意不加 admin 角色限制** ——
 * 每个用户都要能保存自己的列宽，因此只依赖 Sa-Token 登录态（未登录会抛 NotLoginException → 全局 401）。</p>
 */
@RestController
@RequestMapping("/api/system/table-prefs")
@RequiredArgsConstructor
public class TablePrefController {

    private final UserTablePrefService service;

    /** 当前用户全部表格列宽偏好：prefKey → JSON 字符串（前端登录后拉一次并缓存） */
    @GetMapping("/mine")
    public R<Map<String, String>> mine() {
        return R.ok(service.listByUser(StpUtil.getLoginIdAsLong()));
    }

    /** 保存/覆盖某张表的列宽；prefs 为空 = 清除该表（"重置列宽"即走这里） */
    @PutMapping
    public R<Void> save(@RequestBody TablePrefDTO dto) {
        service.save(StpUtil.getLoginIdAsLong(),
                dto == null ? null : dto.getPrefKey(),
                dto == null ? null : dto.getPrefs());
        return R.ok();
    }

    /** 重置单张表 */
    @DeleteMapping("/item")
    public R<Void> resetOne(@RequestParam("prefKey") String prefKey) {
        service.resetOne(StpUtil.getLoginIdAsLong(), prefKey);
        return R.ok();
    }

    /** 重置全部表格 */
    @DeleteMapping("/mine")
    public R<Void> resetAll() {
        service.resetAll(StpUtil.getLoginIdAsLong());
        return R.ok();
    }
}
