package com.beichen.erp.material.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.material.service.ProductAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 产品分析 Controller（经营分析 → 产品分析，2026-10-02 用户要求新增）。
 *
 * <p>读隔离：本页接口只走自己的前缀 {@code /api/product/analysis}，在
 * {@code ApiPermGuard.RULES} 里按页面码 {@code analysis:product} 收口 —— 产品分析页**不许**去读
 * {@code /sale/analysis}、{@code /customer/analysis}、{@code /finance/analysis} 等其它页面的接口
 * （否则被 {@code audit-frontend-api-crosspage.ps1 -Strict} 判为跨页读）。</p>
 */
@RestController
@RequestMapping("/api/product/analysis")
@RequiredArgsConstructor
public class ProductAnalysisController {

    private final ProductAnalysisService service;

    /** 产品分析总览：快捷区间（默认 month）或自定义 start/end。只捕获**日期解析异常**，其余交给全局兜底 */
    @GetMapping
    public R<Map<String, Object>> product(@RequestParam(defaultValue = "month") String preset,
                                          @RequestParam(required = false) String start,
                                          @RequestParam(required = false) String end) {
        try {
            return R.ok(service.product(preset, parse(start), parse(end)));
        } catch (DateTimeParseException ex) {
            return R.fail(400, "日期格式无效，应为 yyyy-MM-dd");
        }
    }

    /** 下钻明细：某产品在区间内的销售明细行 + 退货明细行 */
    @GetMapping("/records")
    public R<Map<String, Object>> records(@RequestParam(defaultValue = "month") String preset,
                                          @RequestParam(required = false) String start,
                                          @RequestParam(required = false) String end,
                                          @RequestParam(required = false) Long productId) {
        try {
            return R.ok(service.records(preset, parse(start), parse(end), productId));
        } catch (DateTimeParseException ex) {
            return R.fail(400, "日期格式无效，应为 yyyy-MM-dd");
        }
    }

    private String parse(String v) {
        if (v != null && !v.isBlank()) LocalDate.parse(v);
        return v;
    }
}
