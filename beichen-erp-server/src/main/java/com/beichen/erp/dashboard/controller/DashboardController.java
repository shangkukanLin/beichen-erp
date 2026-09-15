package com.beichen.erp.dashboard.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 首页接口（待办与预警汇总）
 * <p>经营总览的财务数据直接复用 /api/finance/analysis/summary，由前端按菜单权限控制可见性。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    /** 待办与预警：各模块待审核数 + 盘点待办/超期 + 售后仓超期待整理 + 超期应收 */
    @GetMapping("/pending")
    public R<Map<String, Object>> pending() {
        return R.ok(service.pending());
    }

    /** 销售工作台（首页「销售业务」TAB，2026-09-15 新增）：待办数 + 沉默客户 + 业绩（今日/昨日/本月/上月） */
    @GetMapping("/sale-workbench")
    public R<Map<String, Object>> saleWorkbench() {
        return R.ok(service.saleWorkbench());
    }
}
