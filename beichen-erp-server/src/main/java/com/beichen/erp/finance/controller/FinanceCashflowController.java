package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinanceAccount;
import com.beichen.erp.finance.entity.FinanceCashflow;
import com.beichen.erp.finance.mapper.FinanceAccountMapper;
import com.beichen.erp.finance.mapper.FinanceCashflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
public class FinanceCashflowController {

    private final FinanceCashflowMapper cashflowMapper;
    private final FinanceAccountMapper accountMapper;

    /** 资金流水 */
    @GetMapping("/api/finance/cashflow/page")
    public R<Page<FinanceCashflow>> page(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String flowType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        LambdaQueryWrapper<FinanceCashflow> w = new LambdaQueryWrapper<FinanceCashflow>()
                .eq(accountId != null, FinanceCashflow::getAccountId, accountId)
                .eq(flowType != null && !flowType.isBlank(), FinanceCashflow::getFlowType, flowType)
                .orderByDesc(FinanceCashflow::getId);
        return R.ok(cashflowMapper.selectPage(new Page<>(pageNum, pageSize), w));
    }

    /** 资金账户列表/分页 */
    @GetMapping("/api/finance/account/page")
    public R<Page<FinanceAccount>> accountPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(accountMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceAccount>().orderByDesc(FinanceAccount::getId)));
    }

    @GetMapping("/api/finance/account/list")
    public R<?> accountList() {
        return R.ok(accountMapper.selectList(new LambdaQueryWrapper<FinanceAccount>().eq(FinanceAccount::getStatus, 1)));
    }

    @PostMapping("/api/finance/account")
    public R<Void> addAccount(@RequestBody FinanceAccount a) {
        if (a.getBalance() == null) a.setBalance(BigDecimal.ZERO);
        if (a.getStatus() == null) a.setStatus(1);
        accountMapper.insert(a);
        return R.ok();
    }

    @PutMapping("/api/finance/account")
    public R<Void> updateAccount(@RequestBody FinanceAccount a) {
        accountMapper.updateById(a);
        return R.ok();
    }
}
