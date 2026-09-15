package com.beichen.erp.sale.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.sale.service.SaleAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** 销售分析 Controller（经营分析 → 销售分析）：销售额趋势 / 产品排行 / 仓库分布 / 钻取明细 */
@RestController
@RequestMapping("/api/sale/analysis")
@RequiredArgsConstructor
public class SaleAnalysisController {

    private final SaleAnalysisService service;

    /** 销售分析总览：快捷区间（month 默认）或自定义 start/end */
    @GetMapping
    public R<Map<String, Object>> sale(@RequestParam(defaultValue = "month") String preset,
                                       @RequestParam(required = false) String start,
                                       @RequestParam(required = false) String end) {
        try {
            return R.ok(service.sale(preset, parse(start), parse(end)));
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
    }

    /** 钻取明细：区间内的销售单，可按 productId / warehouseId / customerId 过滤 */
    @GetMapping("/records")
    public R<Map<String, Object>> records(@RequestParam(defaultValue = "month") String preset,
                                          @RequestParam(required = false) String start,
                                          @RequestParam(required = false) String end,
                                          @RequestParam(required = false) Long productId,
                                          @RequestParam(required = false) Long warehouseId,
                                          @RequestParam(required = false) Long customerId) {
        try {
            return R.ok(service.records(preset, parse(start), parse(end), productId, warehouseId, customerId));
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
    }

    /**
     * 首页「当日销售构成」饼图：按**单据日期**统计指定日（默认今天）已审核销售单的产品/客户构成（数量 + 金额）。
     * 与上面的 `preset` 系列**口径不同**（那些按审核日归期），故单列端点，避免混用。
     */
    @GetMapping("/by-doc-date")
    public R<Map<String, Object>> byDocDate(@RequestParam(required = false) String date) {
        try {
            return R.ok(service.byDocDate(date));
        } catch (Exception ex) {
            return R.fail("日期格式无效，应为 yyyy-MM-dd");
        }
    }

    private String parse(String v) {
        if (v != null && !v.isBlank()) LocalDate.parse(v);
        return v;
    }
}
