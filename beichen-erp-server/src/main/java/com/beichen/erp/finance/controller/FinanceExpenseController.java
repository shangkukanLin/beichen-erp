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
    public R<Void> create(@RequestBody FinanceExpense expense) {
        clearSource(expense);
        service.create(expense);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody FinanceExpense expense) {
        clearSource(expense);
        service.update(expense);
        return R.ok();
    }

    /**
     * F7-231（2026-09-29 审核批 C）：**服务端自有的来源三列不接受客户端写入**。
     *
     * <p>费用单是实体直绑请求体（{@code @RequestBody FinanceExpense}）+ {@code updateById} ⇒ 原先客户端
     * 可以自填 {@code source_bill_type/source_id}，把手工费用单伪造成"**由业务单据带出的非资金费用**"：
     * {@code accountId} 为空也会被放行（`validate` 只按"来源是否为空"判非资金）⇒ 凭空记一笔无现金流的损失。
     * 来源三列只由服务端内部调用写入（{@code RdExpenseService} 研发支出、报损走财务的费用登记），
     * 故手工入口一律清空。</p>
     */
    private void clearSource(FinanceExpense expense) {
        if (expense == null) return;
        expense.setSourceBillType(null);
        expense.setSourceId(null);
        expense.setSourceBillNo(null);
    }

    // E1 口径（2026-09-12）：审核族统一 PUT（旧 POST 保留为别名）；反审核统一 /un-audit（旧 /unAudit 保留为别名）
    // 2026-10-09（用户口径「余额不足 ⇒ 提示，确认后可通过」）：`allowOverdraft=true` = 用户已确认"允许把账户扣成负数"。
    // 余额不足时后端**先**返回业务码 409、**一分钱没动**；前端弹确认框后带此参数**重发同一个请求**。
    // 不带 ⇒ 维持原口径（余额不足即拒）。
    @RequestMapping(value = "/{id}/audit", method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> audit(@PathVariable Long id,
                         @RequestParam(defaultValue = "false") boolean allowOverdraft) {
        service.audit(id, allowOverdraft);
        return R.ok();
    }

    @RequestMapping(value = {"/{id}/un-audit", "/{id}/unAudit"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> unAudit(@PathVariable Long id) { service.unAudit(id); return R.ok(); }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }
}
