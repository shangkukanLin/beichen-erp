package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.common.CashflowType;
import com.beichen.erp.finance.entity.FinanceAccount;
import com.beichen.erp.finance.entity.FinanceCashflow;
import com.beichen.erp.finance.mapper.FinanceAccountMapper;
import com.beichen.erp.finance.mapper.FinanceCashflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        Page<FinanceCashflow> page = cashflowMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 流水「余额」列实时累计回填（方案①：后端累计，跨页正确）
        fillCashflowBalance(page.getRecords());
        return R.ok(page);
    }

    /**
     * 流水余额累计回填：余额 = 账户期初 + 截至该笔流水为止的累计(income - expense)
     * 按 id 升序逐笔累计，保证跨页、跨账户均正确
     */
    private void fillCashflowBalance(List<FinanceCashflow> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> accountIds = records.stream().map(FinanceCashflow::getAccountId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (accountIds.isEmpty()) return;
        // 查这些账户的全部流水（按 id 升序，用于累计）
        List<FinanceCashflow> allFlows = cashflowMapper.selectList(new LambdaQueryWrapper<FinanceCashflow>()
                .in(FinanceCashflow::getAccountId, accountIds)
                .orderByAsc(FinanceCashflow::getId));
        // 按账户分组累计，计算每笔流水的变动后余额（期初流水为第一笔，income=期初，无需额外初始值）
        Map<Long, BigDecimal> runningMap = new java.util.HashMap<>();
        Map<Long, BigDecimal> balanceById = new java.util.HashMap<>();
        for (FinanceCashflow f : allFlows) {
            BigDecimal running = runningMap.getOrDefault(f.getAccountId(), BigDecimal.ZERO);
            BigDecimal delta = (f.getIncome() != null ? f.getIncome() : BigDecimal.ZERO)
                    .subtract(f.getExpense() != null ? f.getExpense() : BigDecimal.ZERO);
            running = running.add(delta);
            runningMap.put(f.getAccountId(), running);
            balanceById.put(f.getId(), running);
        }
        // 回填到本页记录
        for (FinanceCashflow f : records) {
            f.setBalance(balanceById.getOrDefault(f.getId(), BigDecimal.ZERO));
        }
    }

    /** 资金账户列表/分页 */
    @GetMapping("/api/finance/account/page")
    public R<Page<FinanceAccount>> accountPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<FinanceAccount> page = accountMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceAccount>().orderByDesc(FinanceAccount::getId));
        fillAccountBalance(page.getRecords());
        return R.ok(page);
    }

    @GetMapping("/api/finance/account/list")
    public R<?> accountList() {
        List<FinanceAccount> list = accountMapper.selectList(new LambdaQueryWrapper<FinanceAccount>().eq(FinanceAccount::getStatus, 1));
        fillAccountBalance(list);
        return R.ok(list);
    }

    /** 账户实时余额批量回填（避免逐账户 N+1） */
    private void fillAccountBalance(List<FinanceAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) return;
        List<Long> ids = accounts.stream().map(FinanceAccount::getId).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) return;
        Map<Long, Map<String, Object>> balanceMap = accountMapper.sumBalance(ids);
        for (FinanceAccount a : accounts) {
            Map<String, Object> row = balanceMap.get(a.getId());
            if (row != null && row.get("balance") != null) {
                a.setBalance(new BigDecimal(row.get("balance").toString()));
            } else {
                a.setBalance(BigDecimal.ZERO);
            }
        }
    }

    @PostMapping("/api/finance/account")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> addAccount(@RequestBody FinanceAccount a) {
        if (a.getOpeningBalance() == null) a.setOpeningBalance(BigDecimal.ZERO);
        if (a.getStatus() == null) a.setStatus(1);
        accountMapper.insert(a);
        // 期初余额落「期初」流水，保证余额可加和、可追溯
        if (a.getOpeningBalance().compareTo(BigDecimal.ZERO) > 0) {
            FinanceCashflow cf = new FinanceCashflow();
            cf.setFlowNo(genFlowNo());
            cf.setAccountId(a.getId());
            cf.setAccountName(a.getAccountName());
            cf.setFlowType(CashflowType.OPENING.getCode());
            cf.setRelatedBillNo(a.getAccountNo());
            cf.setRelatedBillType("期初余额");
            cf.setIncome(a.getOpeningBalance());
            cf.setExpense(BigDecimal.ZERO);
            cf.setRemark("期初余额");
            cashflowMapper.insert(cf);
        }
        return R.ok();
    }

    /** 生成流水号：FL-日期-3位序号 */
    private String genFlowNo() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.CASHFLOW + d;
        LambdaQueryWrapper<FinanceCashflow> w = new LambdaQueryWrapper<FinanceCashflow>()
                .likeRight(FinanceCashflow::getFlowNo, pat)
                .orderByDesc(FinanceCashflow::getFlowNo).last("LIMIT 1");
        FinanceCashflow last = cashflowMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getFlowNo() != null) {
            try { seq = Integer.parseInt(last.getFlowNo().substring(last.getFlowNo().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.CASHFLOW + d + String.format("%03d", seq);
    }

    @PutMapping("/api/finance/account")
    public R<Void> updateAccount(@RequestBody FinanceAccount a) {
        // 期初余额开户后不可变：编辑时禁止修改，用库中旧值兜底，防止破坏流水一致性
        FinanceAccount old = accountMapper.selectById(a.getId());
        if (old != null) {
            a.setOpeningBalance(old.getOpeningBalance());
        }
        accountMapper.updateById(a);
        return R.ok();
    }
}
