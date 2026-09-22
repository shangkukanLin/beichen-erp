package com.beichen.erp.finance.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.finance.service.FinanceAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * 首页经营总览 KPI（2026-09-15 新增，供首页「经营总览」第一/第二排卡片使用）：
     * 快捷区间或自定义日期，返回所选区间的 4 指标 + 固定「本年 1/1~今天」的同 4 指标。
     */
    @GetMapping("/overview-kpi")
    public R<Map<String, Object>> overviewKpi(@RequestParam(defaultValue = "today") String preset,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        try {
            if (start != null && !start.isBlank()) LocalDate.parse(start);
            if (end != null && !end.isBlank()) LocalDate.parse(end);
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
        return R.ok(service.overviewKpi(preset, start, end));
    }

    /**
     * 进货分析（2026-09-15 新增，供「经营分析 → 进货分析」）：
     * 所选区间的采购 KPI（采购金额/采购退货/净采购额/采购单数）+ 趋势序列 + 单据明细（可下钻）。
     */
    @GetMapping("/purchase-analysis")
    public R<Map<String, Object>> purchaseAnalysis(@RequestParam(defaultValue = "month") String preset,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        try {
            if (start != null && !start.isBlank()) LocalDate.parse(start);
            if (end != null && !end.isBlank()) LocalDate.parse(end);
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
        return R.ok(service.purchaseAnalysis(preset, start, end));
    }

    /**
     * 单供货商分析（2026-09-22 新增，供「进货分析」列表点供货商行进入）：
     * 供货商档案 + 同区间 KPI + 月度趋势 + 采购产品 TOP + 该供货商的单据明细。
     * <p>与「进货分析」同源同口径；本前缀已在 ApiPermGuard 只读聚合白名单内，无需额外权限码。</p>
     */
    @GetMapping("/purchase-supplier/{supplierId}")
    public R<Map<String, Object>> purchaseSupplier(@PathVariable Long supplierId,
            @RequestParam(defaultValue = "year") String preset,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        try {
            if (start != null && !start.isBlank()) LocalDate.parse(start);
            if (end != null && !end.isBlank()) LocalDate.parse(end);
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
        return R.ok(service.purchaseSupplier(supplierId, preset, start, end));
    }

    /**
     * 资金趋势与账户余额分布（2026-09-15 起支持「统计区间」）：
     * preset 快捷预设（today/yesterday/week/month/quarter/year）或 start/end 自定义（优先）；都没有时按 months 取近 N 月。
     */
    @GetMapping("/cash-trend")
    public R<Map<String, Object>> cashTrend(@RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate s = null, e = null;
        try {
            if (start != null && !start.isBlank()) s = LocalDate.parse(start);
            if (end != null && !end.isBlank()) e = LocalDate.parse(end);
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
        return R.ok(service.cashTrend(preset, months, s, e));
    }

    /** 应收应付账龄 + 回款/付款率 + TOP 往来单位 */
    @GetMapping("/aging")
    public R<Map<String, Object>> aging() {
        return R.ok(service.aging());
    }

    /** 主体往来统计：供应商应付（按类型）、供应商应收未收、报损损失 */
    @GetMapping("/subject")
    public R<Map<String, Object>> subject() {
        return R.ok(service.subject());
    }

    /** 税务分析：已税/未税按月比例与金额（preset 快捷预设 / start-end 自定义；都没有时按 months 近 N 月） */
    @GetMapping("/tax")
    public R<Map<String, Object>> tax(@RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate s = null, e = null;
        try {
            if (start != null && !start.isBlank()) s = LocalDate.parse(start);
            if (end != null && !end.isBlank()) e = LocalDate.parse(end);
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
        return R.ok(service.tax(preset, months, s, e));
    }
}
