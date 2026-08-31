package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinanceExpense;
import com.beichen.erp.finance.service.FinanceExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 费用登记 Controller（财务管理 → 费用管理）
 * <p>路由前缀: /api/finance/expense</p>
 */
@RestController
@RequestMapping("/api/finance/expense")
@RequiredArgsConstructor
public class FinanceExpenseController {

    private final FinanceExpenseService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(expenseType, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<FinanceExpense> getById(@PathVariable Long id) { return R.ok(service.getById(id)); }

    @PostMapping
    public R<Void> create(@RequestBody FinanceExpense expense) { service.create(expense); return R.ok(); }

    @PutMapping
    public R<Void> update(@RequestBody FinanceExpense expense) { service.update(expense); return R.ok(); }

    @PostMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @PostMapping("/{id}/unAudit")
    public R<Void> unAudit(@PathVariable Long id) { service.unAudit(id); return R.ok(); }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }
}
