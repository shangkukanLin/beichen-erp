package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.AccountType;
import com.beichen.erp.finance.common.CashflowRelatedType;
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
     * 流水余额累计回填：余额 = 账户期初 + 截至该笔流水为止的累计(income - expense)。
     *
     * <p>F7-238（2026-09-29 决策 D-15）：改为**只为本页行做一次聚合** —— 原实现把这些账户的**全部流水**读进内存
     * 逐笔累计（O(账户历史)，账户流水一多就慢，且全量明细进 JVM）。现在由数据库对每行按 `id &lt;= 本行 id`
     * 求和（索引段扫描），只返回本页这几行的余额 ⇒ 内存 O(页大小)。语义与逐笔累计**完全等价**（含期初行）。</p>
     */
    private void fillCashflowBalance(List<FinanceCashflow> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> ids = records.stream().map(FinanceCashflow::getId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return;
        Map<Long, BigDecimal> balanceById = new java.util.HashMap<>();
        for (Map<String, Object> row : cashflowMapper.sumBalanceByIds(ids)) {
            Object idv = row.get("id");
            Object bv = row.get("balance");
            if (idv != null) {
                balanceById.put(Long.valueOf(idv.toString()),
                        bv == null ? BigDecimal.ZERO : new BigDecimal(bv.toString()));
            }
        }
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
        // F7-229（2026-09-29 审核批 C）：补最小入参校验 —— 原先只校验类型，账户名可空、期初可为负。
        if (a.getAccountName() == null || a.getAccountName().isBlank())
            throw new BusinessException("账户名称不能为空");
        a.setAccountName(a.getAccountName().trim());
        // D-14（2026-09-29 决策）：**同名账户收口** —— 原先账户名可重复（库中曾有 6 个 `FIN-ACC` 夹具残留）。
        // 口径：同公司内账户名不可重复（**含已停用**：停用≠可复用名字）。DB 侧配套
        // `uk_account_name (company_id, account_name)`（见 docs 报告 §4.5），此处给出可读提示并把口径前置。
        if (accountMapper.selectCount(new LambdaQueryWrapper<FinanceAccount>()
                .eq(FinanceAccount::getAccountName, a.getAccountName())) > 0)
            throw new BusinessException("账户名称「" + a.getAccountName() + "」已存在");
        if (a.getOpeningBalance().compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("期初余额不能为负数");
        // 2026-09-14：类型统一归一化为小写 code 并做白名单校验（此前自由字符串，曾写入大写 BANK）
        a.setAccountType(AccountType.normalize(a.getAccountType()));
        accountMapper.insert(a);
        // 期初余额落「期初」流水，保证余额可加和、可追溯
        if (a.getOpeningBalance().compareTo(BigDecimal.ZERO) > 0) {
            FinanceCashflow cf = new FinanceCashflow();
            cf.setFlowNo(genFlowNo());
            cf.setAccountId(a.getId());
            cf.setAccountName(a.getAccountName());
            cf.setFlowType(CashflowType.OPENING.getCode());
            // F7-227（2026-09-29 审核批 C）：原先写 `account_no`（银行账号，**可空**）⇒ 库中 9/9 条期初流水
            // 「关联单号」为空、无法按号回溯。改为"有账号写账号，否则退回账户名"，保证期初行也带可读来源。
            cf.setRelatedBillNo(a.getAccountNo() != null && !a.getAccountNo().isBlank()
                    ? a.getAccountNo() : a.getAccountName());
            cf.setRelatedBillType(CashflowRelatedType.OPENING.getCode());
            cf.setIncome(a.getOpeningBalance());
            cf.setExpense(BigDecimal.ZERO);
            cf.setRemark("期初余额");
            cashflowMapper.insert(cf);
        }
        return R.ok();
    }

    /**
     * 生成流水号：FL-日期-3位序号。
     *
     * <p>F7-228（2026-09-29 审核批 C）：**补查重重试** —— 同款生成器在
     * {@code FinanceExpenseServiceImpl.genFlowNo} / {@code FinancePaymentServiceImpl} 已按 F7-39#3 修好，
     * 唯独开户这一处漏了：并发开户（或与收付款同时编号）会直接撞 {@code finance_cashflow.uk_flow_no}
     * 唯一索引、抛数据库原始错（无脏数据，但报错不可读、三处口径不一致）。</p>
     */
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
        for (int i = 0; i < 999; i++) {
            String code = BillPrefix.CASHFLOW + d + String.format("%03d", seq);
            if (cashflowMapper.selectCount(new LambdaQueryWrapper<FinanceCashflow>()
                    .eq(FinanceCashflow::getFlowNo, code)) == 0) return code;
            seq++;
        }
        throw new BusinessException("当日资金流水号已用尽（前缀 " + pat + "），请联系管理员");
    }

    @PutMapping("/api/finance/account")
    public R<Void> updateAccount(@RequestBody FinanceAccount a) {
        // 期初余额开户后不可变：编辑时禁止修改，用库中旧值兜底，防止破坏流水一致性
        FinanceAccount old = accountMapper.selectById(a.getId());
        if (old != null) {
            a.setOpeningBalance(old.getOpeningBalance());
        }
        // 2026-09-14：类型归一化 + 白名单（未传类型时保持原值，避免局部更新被拒）
        if (a.getAccountType() != null) {
            a.setAccountType(AccountType.normalize(a.getAccountType()));
        }
        // D-14（2026-09-29 决策）：**改名同样拦同名**（排除自身）—— 否则把 A 改成与 B 同名即可绕过建户护栏。
        if (a.getAccountName() != null && !a.getAccountName().isBlank()) {
            a.setAccountName(a.getAccountName().trim());
            Long self = a.getId();
            if (accountMapper.selectCount(new LambdaQueryWrapper<FinanceAccount>()
                    .eq(FinanceAccount::getAccountName, a.getAccountName())
                    .ne(self != null, FinanceAccount::getId, self)) > 0)
                throw new BusinessException("账户名称「" + a.getAccountName() + "」已存在");
        }
        accountMapper.updateById(a);
        return R.ok();
    }
}
