package com.beichen.erp.finance.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.finance.service.FinanceAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 财务分析 Controller（财务管理 → 财务分析）
 * 经营概览 / 利润表 / 资金趋势 / 应收应付账龄。
 */
@RestController
@RequestMapping("/api/finance/analysis")
@RequiredArgsConstructor
public class FinanceAnalysisController {

    private final FinanceAnalysisService service;

    /** 经营概览（本月与上月对比） */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(service.summary());
    }

    /** 利润表：months 快捷模式（默认 12），或 start/end 自定义日期区间（yyyy-MM-dd，优先生效） */
    @GetMapping("/profit")
    public R<Map<String, Object>> profit(@RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate s = null, e = null;
        try {
            if (start != null && !start.isBlank()) s = LocalDate.parse(start);
            if (end != null && !end.isBlank()) e = LocalDate.parse(end);
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
        return R.ok(service.profit(months, s, e));
    }

    /** 资金趋势与账户余额分布 */
    @GetMapping("/cash-trend")
    public R<Map<String, Object>> cashTrend(@RequestParam(defaultValue = "12") int months) {
        return R.ok(service.cashTrend(months));
    }

    /** 应收应付账龄 + 回款/付款率 + TOP 往来单位 */
    @GetMapping("/aging")
    public R<Map<String, Object>> aging() {
        return R.ok(service.aging());
    }

    /** 税务分析：已税/未税按月比例与金额（months 快捷模式默认 12，start/end 自定义区间优先生效） */
    @GetMapping("/tax")
    public R<Map<String, Object>> tax(@RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate s = null, e = null;
        try {
            if (start != null && !start.isBlank()) s = LocalDate.parse(start);
            if (end != null && !end.isBlank()) e = LocalDate.parse(end);
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
        return R.ok(service.tax(months, s, e));
    }
}
