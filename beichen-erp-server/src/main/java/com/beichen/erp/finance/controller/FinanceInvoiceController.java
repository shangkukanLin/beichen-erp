package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinanceInvoice;
import com.beichen.erp.finance.service.FinanceInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 发票登记 Controller（财务管理 → 发票管理）
 * <p>销项/进项发票登记（税务口径）。路由前缀: /api/finance/invoice</p>
 */
@RestController
@RequestMapping("/api/finance/invoice")
@RequiredArgsConstructor
public class FinanceInvoiceController {

    private final FinanceInvoiceService service;

    @GetMapping("/page")
    public R<Page<FinanceInvoice>> page(
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(direction, status, keyword, start, end, pageNum, pageSize));
    }

    @PostMapping
    public R<Void> create(@RequestBody FinanceInvoice invoice) { service.create(invoice); return R.ok(); }

    @PutMapping
    public R<Void> update(@RequestBody FinanceInvoice invoice) { service.update(invoice); return R.ok(); }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }
}
