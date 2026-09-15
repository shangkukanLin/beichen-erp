package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.finance.common.CashflowRelatedType;
import com.beichen.erp.finance.common.CashflowType;
import com.beichen.erp.finance.entity.FinanceAccount;
import com.beichen.erp.finance.entity.FinanceCashflow;
import com.beichen.erp.finance.entity.FinanceExpense;
import com.beichen.erp.finance.mapper.FinanceAccountMapper;
import com.beichen.erp.finance.mapper.FinanceCashflowMapper;
import com.beichen.erp.finance.mapper.FinanceExpenseMapper;
import com.beichen.erp.finance.service.FinanceExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 费用登记 Service：审核联动资金流水（模式与收款单一致——余额由流水实时累计，不维护快照）。
 */
@Service
@RequiredArgsConstructor
public class FinanceExpenseServiceImpl implements FinanceExpenseService {

    private final FinanceExpenseMapper expenseMapper;
    private final FinanceAccountMapper accountMapper;
    private final FinanceCashflowMapper cashflowMapper;

    @Override
    public Page<Map<String, Object>> page(String expenseType, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<FinanceExpense> w = new LambdaQueryWrapper<FinanceExpense>()
                .eq(expenseType != null && !expenseType.isBlank(), FinanceExpense::getExpenseType, expenseType)
                .eq(status != null && !status.isBlank(), FinanceExpense::getStatus, status)
                .orderByDesc(FinanceExpense::getId);
        Page<FinanceExpense> raw = expenseMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId()); m.put("expenseNo", e.getExpenseNo());
            m.put("expenseType", e.getExpenseType()); m.put("amount", e.getAmount());
            m.put("expenseDate", e.getExpenseDate()); m.put("accountId", e.getAccountId());
            m.put("accountName", e.getAccountName()); m.put("status", e.getStatus());
            m.put("remark", e.getRemark()); m.put("createTime", e.getCreateTime());
            return m;
        }).toList());
        return res;
    }

    @Override
    public FinanceExpense getById(Long id) { return expenseMapper.selectById(id); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(FinanceExpense expense) {
        validate(expense);
        fillAccountName(expense);
        expense.setExpenseNo(genNo());
        expense.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) expense.setCompanyId(cid);
        expenseMapper.insert(expense);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FinanceExpense expense) {
        FinanceExpense old = expenseMapper.selectById(expense.getId());
        if (old == null) throw new BusinessException("费用单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可修改");
        validate(expense);
        fillAccountName(expense);
        expense.setExpenseNo(old.getExpenseNo());
        expense.setStatus(old.getStatus());
        expenseMapper.updateById(expense);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        FinanceExpense expense = expenseMapper.selectById(id);
        if (expense == null) throw new BusinessException("费用单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免重复写支出流水
        if (!DocStatusGuard.claim(expenseMapper, FinanceExpense::getId, id, FinanceExpense::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        if (expense.getAccountId() == null) throw new BusinessException("支出账户不能为空");
        // 余额校验：账户实时余额（期初+收入-支出）须足够支付本笔费用
        BigDecimal balance = accountBalance(expense.getAccountId());
        BigDecimal amount = expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO;
        if (balance.subtract(amount).compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("账户余额不足：当前余额 " + balance + "，费用 " + amount);
        // 写「费用支出」资金流水（账户余额由流水实时累计）
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(genFlowNo());
        cf.setAccountId(expense.getAccountId());
        cf.setAccountName(expense.getAccountName());
        cf.setFlowType(CashflowType.EXPENSE.getCode());
        cf.setRelatedBillNo(expense.getExpenseNo());
        cf.setRelatedBillType(CashflowRelatedType.EXPENSE.getCode());
        cf.setIncome(BigDecimal.ZERO);
        cf.setExpense(amount);
        if (expense.getRemark() != null && !expense.getRemark().isBlank()) cf.setRemark(expense.getRemark());
        cashflowMapper.insert(cf);
        FinanceExpense u = new FinanceExpense();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        expenseMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        FinanceExpense expense = expenseMapper.selectById(id);
        if (expense == null) throw new BusinessException("费用单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免重复写冲正流水
        if (!DocStatusGuard.claim(expenseMapper, FinanceExpense::getId, id, FinanceExpense::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核的费用单可反审核");
        // 写「费用冲正」流水把钱冲回账户（保留审计轨迹，不删除原流水），与收款单反审核模式对称
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(genFlowNo());
        cf.setAccountId(expense.getAccountId());
        cf.setAccountName(expense.getAccountName());
        cf.setFlowType(CashflowType.EXPENSE_REVERSE.getCode());
        cf.setRelatedBillNo(expense.getExpenseNo());
        cf.setRelatedBillType(CashflowRelatedType.EXPENSE.getCode());
        cf.setIncome(expense.getAmount());
        cf.setExpense(BigDecimal.ZERO);
        cf.setRemark("反审核冲正");
        cashflowMapper.insert(cf);
        FinanceExpense u = new FinanceExpense();
        u.setId(id);
        u.setStatus(DocStatus.DRAFT.getCode());
        expenseMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        FinanceExpense old = expenseMapper.selectById(id);
        if (old == null) throw new BusinessException("费用单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(expenseMapper, FinanceExpense::getId, id, FinanceExpense::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
        FinanceExpense u = new FinanceExpense();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        expenseMapper.updateById(u);
    }

    // ==================== 私有辅助 ====================

    private void validate(FinanceExpense expense) {
        if (expense.getExpenseType() == null || expense.getExpenseType().isBlank()) throw new BusinessException("费用类型不能为空");
        if (expense.getAmount() == null || expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("费用金额必须大于 0");
        if (expense.getAccountId() == null) throw new BusinessException("支出账户不能为空");
        if (expense.getExpenseDate() == null) expense.setExpenseDate(LocalDate.now());
    }

    private void fillAccountName(FinanceExpense expense) {
        FinanceAccount acc = accountMapper.selectById(expense.getAccountId());
        if (acc == null) throw new BusinessException("支出账户不存在");
        expense.setAccountName(acc.getAccountName());
    }

    /** 账户实时余额 = Σ(流水 income - expense)（期初余额开户时已落一条「期初」流水） */
    private BigDecimal accountBalance(Long accountId) {
        Map<Long, Map<String, Object>> map = accountMapper.sumBalance(List.of(accountId));
        Map<String, Object> row = map.get(accountId);
        if (row == null || row.get("balance") == null) return BigDecimal.ZERO;
        return new BigDecimal(row.get("balance").toString());
    }

    private String genNo() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.EXPENSE + d;
        LambdaQueryWrapper<FinanceExpense> w = new LambdaQueryWrapper<FinanceExpense>()
                .likeRight(FinanceExpense::getExpenseNo, pat).orderByDesc(FinanceExpense::getExpenseNo).last("LIMIT 1");
        FinanceExpense last = expenseMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getExpenseNo() != null) {
            try { seq = Integer.parseInt(last.getExpenseNo().substring(last.getExpenseNo().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.EXPENSE + d + String.format("%03d", seq);
    }

    private String genFlowNo() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.CASHFLOW + d;
        LambdaQueryWrapper<FinanceCashflow> w = new LambdaQueryWrapper<FinanceCashflow>()
                .likeRight(FinanceCashflow::getFlowNo, pat).orderByDesc(FinanceCashflow::getFlowNo).last("LIMIT 1");
        FinanceCashflow last = cashflowMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getFlowNo() != null) {
            try { seq = Integer.parseInt(last.getFlowNo().substring(last.getFlowNo().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.CASHFLOW + d + String.format("%03d", seq);
    }
}
