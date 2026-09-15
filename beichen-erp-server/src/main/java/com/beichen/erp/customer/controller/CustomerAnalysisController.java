package com.beichen.erp.customer.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.customer.service.CustomerAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** 客户分析 Controller（经营分析 → 客户分析）：客户销售额排行 / 欠款 / 钻取该客户销售单 */
@RestController
@RequestMapping("/api/customer/analysis")
@RequiredArgsConstructor
public class CustomerAnalysisController {

    private final CustomerAnalysisService service;

    /** 客户分析总览：KPI + TOP10 + 客户明细表 */
    @GetMapping
    public R<Map<String, Object>> customer(@RequestParam(defaultValue = "month") String preset,
                                           @RequestParam(required = false) String start,
                                           @RequestParam(required = false) String end) {
        try {
            return R.ok(service.customer(preset, parse(start), parse(end)));
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
    }

    /** 单客户分析：档案 + KPI + 月度趋势 + 按产品/品牌/品质分布 + 退货明细 */
    @GetMapping("/profile/{customerId}")
    public R<Map<String, Object>> profile(@PathVariable Long customerId,
                                          @RequestParam(defaultValue = "year") String preset,
                                          @RequestParam(required = false) String start,
                                          @RequestParam(required = false) String end) {
        try {
            return R.ok(service.profile(customerId, preset, parse(start), parse(end)));
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
    }

    /** 钻取明细：某客户在区间的销售单 */
    @GetMapping("/records")
    public R<Map<String, Object>> records(@RequestParam Long customerId,
                                          @RequestParam(defaultValue = "month") String preset,
                                          @RequestParam(required = false) String start,
                                          @RequestParam(required = false) String end) {
        try {
            return R.ok(service.records(customerId, preset, parse(start), parse(end)));
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
    }

    private String parse(String v) {
        if (v != null && !v.isBlank()) LocalDate.parse(v);
        return v;
    }
}
