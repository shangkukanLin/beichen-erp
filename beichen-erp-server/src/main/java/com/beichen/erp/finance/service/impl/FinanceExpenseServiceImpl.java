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
import com.beichen.erp.finance.common.ExpenseType;
import com.beichen.erp.finance.entity.FinanceAccount;
import com.beichen.erp.finance.entity.FinanceCashflow;
import com.beichen.erp.finance.entity.FinanceExpense;
import com.beichen.erp.finance.mapper.FinanceAccountMapper;
import com.beichen.erp.finance.mapper.FinanceCashflowMapper;
import com.beichen.erp.finance.mapper.FinanceExpenseMapper;
import com.beichen.erp.finance.service.FinanceExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
    public FinanceExpense findActiveBySource(String sourceBillType, Long sourceId) {
        if (sourceBillType == null || sourceId == null) return null;
        return expenseMapper.selectOne(new LambdaQueryWrapper<FinanceExpense>()
                .eq(FinanceExpense::getSourceBillType, sourceBillType)
                .eq(FinanceExpense::getSourceId, sourceId)
                .ne(FinanceExpense::getStatus, DocStatus.CANCELLED.getCode())
                .orderByDesc(FinanceExpense::getId)
                .last("LIMIT 1"));
    }

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
    public void audit(Long id) { audit(id, false); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean allowOverdraft) {
        FinanceExpense expense = expenseMapper.selectById(id);
        if (expense == null) throw new BusinessException("费用单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免重复写支出流水
        if (!DocStatusGuard.claim(expenseMapper, FinanceExpense::getId, id, FinanceExpense::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        // 2026-09-29（用户口径「报损需要走财务流程」）：**非资金费用**（无账户的损失费用，如报损损失）——
        // 存货损失不发生现金流出 ⇒ 不校验余额、不写资金流水，仅置「已审核」；
        // 金额由来源单据（报损单）在生成时决定，故这里没有金额可变项。
        if (expense.getAccountId() == null) {
            if (expense.getSourceBillType() == null || expense.getSourceBillType().isBlank())
                throw new BusinessException("支出账户不能为空（手工登记的费用单必须指定账户）");
            FinanceExpense u0 = new FinanceExpense();
            u0.setId(id);
            u0.setStatus(DocStatus.AUDITED.getCode());
            expenseMapper.updateById(u0);
            return;
        }
        // F7-140（2026-09-20）：**账户行锁** —— 与付款侧同款问题：余额是 Σ 流水的派生值，
        // "读余额校验 → 写支出流水"两步不原子 ⇒ 并发两笔费用可双双通过校验、账户被透支（报告的 P3 残留）。
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        // F7-230（2026-09-29 审核批 C）：行锁取账户后**一并校验停用状态** —— 原先只看"存在"，
        // 停用账户照样能用于费用支出（前端下拉过滤了 status=1，但直调 API 可绕）。
        // 与收/付款侧 F7-207/F7-215 同口径（`status` 为 null 的历史行不拦）。
        FinanceAccount lockedAcc = accountMapper.selectForUpdate(expense.getAccountId(), lockCid);
        if (lockedAcc == null) throw new BusinessException("支出账户不存在");
        if (lockedAcc.getStatus() != null && lockedAcc.getStatus() == 0)
            throw new BusinessException("支出账户「" + lockedAcc.getAccountName() + "」已停用，不能用于费用支出");
        // 余额校验：账户实时余额（期初+收入-支出）须足够支付本笔费用
        // F7-140：用**当前读**取余额（见 accountBalanceForUpdate 的说明：一致性读会读到旧快照 ⇒ 并发可透支）
        BigDecimal balance = accountBalanceForUpdate(expense.getAccountId());
        BigDecimal amount = expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO;
        boolean overdraft = false;
        if (balance.subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
            // 2026-10-09（用户口径「扣款时余额不足 ⇒ 给用户提示，用户确认后可通过」）：
            // ① **未确认** ⇒ 抛业务码 409（HTTP 仍 200，本仓业务码约定）。抛错发生在任何写库之前 ⇒
            //    "业务码非 200"就一定**没动账**（脚本/守卫不会把"没执行"误当成功 ✗），前端据此弹确认框。
            // ② **已确认**（allowOverdraft=true）⇒ 放行、允许扣成负数，但**必须留痕**（warn 日志 + 流水备注），
            //    与委外「强制出库 ⇒ 允许负库存」同口径：不拦，但事后必须查得到"谁在什么时候把哪个账户扣成了多少"。
            if (!allowOverdraft)
                throw new BusinessException(409, "账户余额不足：当前余额 " + balance + "，费用 " + amount
                        + "，扣款后余额将为 " + balance.subtract(amount) + "。确认后将继续扣款（该账户将透支）。");
            overdraft = true;
            log.warn("费用审核：余额不足已由用户确认，允许透支扣款 —— expenseNo={}, accountId={}, 余额={}, 费用={}, 扣后={}",
                    expense.getExpenseNo(), expense.getAccountId(), balance, amount, balance.subtract(amount));
        }
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
        if (overdraft) {
            // 留痕②（①是上面的 warn 日志）：资金流水备注里带一次，费用/资金流水页面直接可查
            String note = "[余额不足已确认｜余额 " + balance + " → " + balance.subtract(amount) + "]";
            cf.setRemark(cf.getRemark() == null || cf.getRemark().isBlank() ? note : cf.getRemark() + "｜" + note);
        }
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
        // 2026-09-29：非资金费用（无账户）⇒ 没有流水可冲，直接回草稿（与审核分支严格对称）
        if (expense.getAccountId() == null) {
            FinanceExpense u0 = new FinanceExpense();
            u0.setId(id);
            u0.setStatus(DocStatus.DRAFT.getCode());
            expenseMapper.updateById(u0);
            return;
        }
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
        // 2026-09-27：类型收敛到 ExpenseType 枚举 —— 校验 + 归一化大小写（历史/脚本里出现过小写 office），
        // 未知值拒绝（此前无校验，前端写错即脏数据，清单页会显示英文 code，与 F7-53 同源）
        try {
            expense.setExpenseType(ExpenseType.normalize(expense.getExpenseType()));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
        // F7-232（2026-09-29 审核批 C）：**报损损失（LOSS）语义 = 非资金费用**（存货损失不产生现金流出）⇒
        // 不允许指定支出账户，否则"同一语义两种记法"：选了账户就会写一笔真金白银的支出流水。
        // （由报损单带出的费用走内部调用、accountId 为空；手工登记页选到 LOSS 时会被这里拒绝。）
        if (ExpenseType.LOSS.getCode().equals(expense.getExpenseType()) && expense.getAccountId() != null)
            throw new BusinessException("报损损失为非资金费用，不能指定支出账户（请清空支出账户）");
        if (expense.getAmount() == null || expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("费用金额必须大于 0");
        // 2026-09-29（用户口径「报损需要走财务流程」）：**支出账户可为空** —— 但**仅限"由业务单据带出的
        // 非资金损失费用"**（source_bill_type 非空，如报损损失 ExpenseType.LOSS：存货损失不发生现金流出
        // ⇒ 不写资金流水、不扣账户）。手工登记的费用单仍必须指定账户，避免"凭空一笔费用"。
        if (expense.getAccountId() == null
                && (expense.getSourceBillType() == null || expense.getSourceBillType().isBlank())) {
            throw new BusinessException("支出账户不能为空");
        }
        if (expense.getExpenseDate() == null) expense.setExpenseDate(LocalDate.now());
        normalizeTax(expense);
    }

    /**
     * 含税口径归一化（2026-10-09 V7；算法与采购单 {@code PurchaseOrderServiceImpl}、委外单
     * {@code OutsourceOrderServiceImpl} 的税额拆分**同源**，故写在这一处、供全部入口共用 ——
     * 研发物料「研发支出」登记（{@code RdExpenseServiceImpl}）与费用管理手工登记都走本方法，不再各写一份）：
     *
     * <p>① **未含税**（{@code taxIncluded != 1}）⇒ 税率与税额一律归 0
     * （用户关掉开关后残留的税率不得留在库里；存量单默认即此分支 ⇒ 语义不变）；
     * ② **含税** ⇒ 校验税率 0~100，并按 {@code 金额 × 税率/(100+税率)} 重算税额（HALF_UP、2 位小数）。</p>
     *
     * <p>⚠️ **金额本身绝不改写**：费用单的 amount 是"真正从账户扣款、进利润表"的那个数，
     * 「是否含税」只影响税额的拆分展示 ⇒ 加这个开关**不改变任何钱的口径**。</p>
     */
    private void normalizeTax(FinanceExpense expense) {
        boolean included = expense.getTaxIncluded() != null && expense.getTaxIncluded() == 1;
        if (!included) {
            expense.setTaxIncluded(0);
            expense.setTaxRate(BigDecimal.ZERO);
            expense.setTaxAmount(BigDecimal.ZERO);
            return;
        }
        BigDecimal rate = expense.getTaxRate() == null ? BigDecimal.ZERO : expense.getTaxRate();
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100")) > 0)
            throw new BusinessException("税率应在 0~100 之间：" + rate);
        // 与采购/委外单同式：含税总额 × 税率/(100+税率)
        BigDecimal tax = expense.getAmount().multiply(rate)
                .divide(new BigDecimal("100").add(rate), 2, java.math.RoundingMode.HALF_UP);
        expense.setTaxRate(rate);
        expense.setTaxAmount(tax);
    }

    private void fillAccountName(FinanceExpense expense) {
        // 2026-09-29：无账户 = 非资金费用（报损损失等由业务单据带出的损失）⇒ 账户名一并留空
        if (expense.getAccountId() == null) { expense.setAccountName(null); return; }
        FinanceAccount acc = accountMapper.selectById(expense.getAccountId());
        if (acc == null) throw new BusinessException("支出账户不存在");
        // F7-230（2026-09-29 审核批 C）：建单/改单即拦停用账户（审核路径另有行锁版同款校验）
        if (acc.getStatus() != null && acc.getStatus() == 0)
            throw new BusinessException("支出账户「" + acc.getAccountName() + "」已停用，不能用于费用支出");
        expense.setAccountName(acc.getAccountName());
    }

    /** 账户实时余额 = Σ(流水 income - expense)（期初余额开户时已落一条「期初」流水） */
    private BigDecimal accountBalance(Long accountId) {
        Map<Long, Map<String, Object>> map = accountMapper.sumBalance(List.of(accountId));
        Map<String, Object> row = map.get(accountId);
        if (row == null || row.get("balance") == null) return BigDecimal.ZERO;
        return new BigDecimal(row.get("balance").toString());
    }

    /**
     * F7-140（2026-09-20）：**当前读**版本的余额（`FOR UPDATE`），供审核路径使用。
     *
     * <p>普通 {@link #accountBalance} 走一致性读：审核事务在**取单据时**就已建立快照 ⇒ 之后即使拿到了账户行锁，
     * 读到的仍是**旧余额** ⇒ 并发第二笔照样通过校验。必须用当前读才能读到"前一笔已提交的流水"。</p>
     */
    private BigDecimal accountBalanceForUpdate(Long accountId) {
        // F7-239（2026-09-29 审核批 C）：与 selectForUpdate 口径一致，显式带上租户条件
        Long cid = CompanyContext.get();
        if (cid != null && cid <= 0) cid = null;
        Map<String, Object> row = accountMapper.sumBalanceForUpdate(accountId, cid);
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
        // F7-39#3（2026-09-19）：冲突检测 + 递增重试 —— finance_expense.expense_no **无索引**，
        // 原兜底 seq=1 会静默重号（本批同时补唯一索引，双重兜底）。
        for (int i = 0; i < 999; i++) {
            String code = BillPrefix.EXPENSE + d + String.format("%03d", seq);
            if (expenseMapper.selectCount(new LambdaQueryWrapper<FinanceExpense>().eq(FinanceExpense::getExpenseNo, code)) == 0) return code;
            seq++;
        }
        throw new BusinessException("当日费用单编号已用尽（前缀 " + pat + "），请联系管理员");
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
        // F7-39#3（2026-09-19）：冲突检测 + 递增重试（finance_cashflow.flow_no 无索引，原会静默重号）
        for (int i = 0; i < 999; i++) {
            String code = BillPrefix.CASHFLOW + d + String.format("%03d", seq);
            if (cashflowMapper.selectCount(new LambdaQueryWrapper<FinanceCashflow>().eq(FinanceCashflow::getFlowNo, code)) == 0) return code;
            seq++;
        }
        throw new BusinessException("当日资金流水号已用尽（前缀 " + pat + "），请联系管理员");
    }
}
