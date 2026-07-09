package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinanceBill;
import com.beichen.erp.finance.entity.FinanceBillItem;
import com.beichen.erp.finance.service.FinanceBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/bill")
@RequiredArgsConstructor
public class FinanceBillController {

    private final FinanceBillService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(billType, partnerId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<FinanceBill> getById(@PathVariable Long id) { return R.ok(service.getById(id)); }

    @GetMapping("/{id}/items")
    public R<List<FinanceBillItem>> getItems(@PathVariable Long id) { return R.ok(service.getItems(id)); }

    @PostMapping("/generate")
    public R<FinanceBill> generate(@RequestBody Map<String, Object> body) {
        String billType = (String) body.get("billType");
        Long partnerId = body.get("partnerId") != null ? Long.valueOf(body.get("partnerId").toString()) : null;
        String partnerName = (String) body.get("partnerName");
        LocalDate periodStart = body.get("periodStart") != null ? LocalDate.parse(body.get("periodStart").toString()) : null;
        LocalDate periodEnd = body.get("periodEnd") != null ? LocalDate.parse(body.get("periodEnd").toString()) : null;
        return R.ok(service.generate(billType, partnerId, partnerName, periodStart, periodEnd));
    }
}
