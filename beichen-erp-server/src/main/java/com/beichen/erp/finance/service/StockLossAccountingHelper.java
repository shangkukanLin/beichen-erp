package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.finance.common.ExpenseType;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.finance.entity.FinanceExpense;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceExpenseMapper;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报损落账辅助（2026-09-29 用户口径「**报损需要走财务流程**」）。
 *
 * <p>成品报损（{@code inventory_stock_loss}）与委外物料报损（{@code outsource_stock_loss}）**共用本类**，
 * 避免两处各写一套记账口径（口径漂移是本项目高发缺陷面）。落点由报损单的**损失承担方**决定：</p>
 *
 * <table border="1">
 *   <caption>记账规则</caption>
 *   <tr><th>承担方</th><th>审核</th><th>反审核</th></tr>
 *   <tr>
 *     <td>{@link #PARTY_INTERNAL} 内部损失（默认）</td>
 *     <td>生成 <b>报损损失费用单</b>（{@code finance_expense}，类型 {@link ExpenseType#LOSS}，
 *         <b>account_id 为空 = 非资金费用</b> ⇒ 不写资金流水、不扣账户）并置「已审核」</td>
 *     <td>该费用单置「已作废」（无流水可冲）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #PARTY_SUPPLIER} 供应商/加工厂承担</td>
 *     <td>生成 <b>对供应商的应收</b>（{@code finance_receivable}，{@code subject_type=SUPPLIER}，索赔挂账）</td>
 *     <td>{@link ReceivableHelper#reverseReceivable(String)} 冲回（已有收款会被其护栏拒绝 ⇒ 正确）</td>
 *   </tr>
 * </table>
 *
 * <p><b>金额口径</b>：取报损单 {@code total_amount}（= 明细「单价×数量」合计，单价默认带出成本价/最近进价）；
 * <b>金额 ≤ 0 时不落账</b>（仍照常扣库存 —— 零金额凭证无意义，且费用单要求金额 &gt; 0）。</p>
 *
 * <p><b>幂等</b>：两条腿都按**来源**（{@code source_bill_type + source_id}）或**单号**（报损单号）定位既有行并复用 ——
 * 「审核 → 反审核 → 再审核」不会累积多行/多张，单号保持稳定（与台账 {@code OUTSOURCE_RETURN_BACK} 同范式）。</p>
 */
@Component
@RequiredArgsConstructor
public class StockLossAccountingHelper {

    /** 承担方：内部损失（默认） */
    public static final String PARTY_INTERNAL = "INTERNAL";
    /** 承担方：供应商/加工厂承担（索赔） */
    public static final String PARTY_SUPPLIER = "SUPPLIER";

    private final FinanceExpenseService expenseService;
    private final FinanceExpenseMapper expenseMapper;
    private final FinanceReceivableMapper receivableMapper;
    private final ReceivableHelper receivableHelper;

    /** 合法承担方（空值由 {@link #normalizeParty} 归一到内部） */
    public static boolean isValidParty(String party) {
        if (party == null || party.isBlank()) return true;
        return PARTY_INTERNAL.equalsIgnoreCase(party.trim()) || PARTY_SUPPLIER.equalsIgnoreCase(party.trim());
    }

    /** 归一化承担方：只有显式 SUPPLIER 才走索赔，其余（含空）按内部损失 */
    public static String normalizeParty(String party) {
        if (party != null && PARTY_SUPPLIER.equalsIgnoreCase(party.trim())) return PARTY_SUPPLIER;
        return PARTY_INTERNAL;
    }

    /**
     * 报损落账（**审核时调用**，与调用方同事务）。
     *
     * @param sourceBillType 来源类型（{@code SourceBillType.INVENTORY_STOCK_LOSS} / {@code OUTSOURCE_STOCK_LOSS}）
     * @param sourceId       报损单ID
     * @param sourceBillNo   报损单号（也是索赔应收的台账单号）
     * @param amount         报损金额（≤0 直接返回，不落账）
     * @param liableParty    {@link #PARTY_INTERNAL} / {@link #PARTY_SUPPLIER}
     * @param supplierId     承担方供应商ID（承担方为 SUPPLIER 时非空）
     * @param supplierName   承担方供应商名称（快照）
     * @param lossDate       报损日期（费用归月 / 应收到期日）
     * @param remark         备注（写明来源报损单与原因，便于财务追溯）
     */
    @Transactional(rollbackFor = Exception.class)
    public void post(String sourceBillType, Long sourceId, String sourceBillNo, BigDecimal amount,
                     String liableParty, Long supplierId, String supplierName,
                     LocalDate lossDate, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        if (PARTY_SUPPLIER.equals(normalizeParty(liableParty))) {
            upsertClaimReceivable(sourceBillType, sourceId, sourceBillNo, amount, supplierId, supplierName, lossDate, remark);
        } else {
            upsertLossExpense(sourceBillType, sourceId, sourceBillNo, amount, lossDate, remark);
        }
    }

    /**
     * 报损冲销（**反审核时调用**，与调用方同事务）。
     *
     * <p>两条腿**都**处理（不依赖当前承担方）：① 该来源的损失费用单置「已作废」；
     * ② 若已存在同单号的索赔应收且未作废 ⇒ 走 {@link ReceivableHelper#reverseReceivable}
     * （其内部护栏会拦住"已收款"的行）。不存在则跳过 —— 保证反审核**可重入**、不因历史/异常数据卡死。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void reverse(String sourceBillType, Long sourceId, String sourceBillNo) {
        FinanceExpense exp = latestExpenseBySource(sourceBillType, sourceId);
        if (exp != null && !DocStatus.CANCELLED.getCode().equals(exp.getStatus())) {
            expenseMapper.update(null, new LambdaUpdateWrapper<FinanceExpense>()
                    .eq(FinanceExpense::getId, exp.getId())
                    .set(FinanceExpense::getStatus, DocStatus.CANCELLED.getCode()));
        }
        FinanceReceivable fr = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, sourceBillNo).last("LIMIT 1"));
        if (fr != null && !SettlementStatus.CANCELLED.getCode().equals(fr.getStatus())) {
            receivableHelper.reverseReceivable(sourceBillNo);
        }
    }

    /**
     * 详情页展示的「财务影响」描述（无凭证时返回 {@code null}）—— 让业务单据能直接看到落到了哪张凭证上。
     */
    public String financeInfo(String sourceBillType, Long sourceId, String sourceBillNo, String liableParty) {
        if (PARTY_SUPPLIER.equals(normalizeParty(liableParty))) {
            FinanceReceivable fr = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                    .eq(FinanceReceivable::getBillNo, sourceBillNo).last("LIMIT 1"));
            if (fr == null || SettlementStatus.CANCELLED.getCode().equals(fr.getStatus())) return null;
            BigDecimal unpaid = fr.getUnpaidAmount() != null ? fr.getUnpaidAmount() : BigDecimal.ZERO;
            String st = SettlementStatus.SETTLED.getCode().equals(fr.getStatus()) ? "已结清"
                    : (SettlementStatus.PARTIAL.getCode().equals(fr.getStatus()) ? "部分结清" : "未结清");
            return "对供应商应收（索赔）" + sourceBillNo + "：" + st
                    + "，未收 " + unpaid.stripTrailingZeros().toPlainString();
        }
        FinanceExpense exp = expenseService.findActiveBySource(sourceBillType, sourceId);
        if (exp == null) return null;
        String st = DocStatus.AUDITED.getCode().equals(exp.getStatus()) ? "已审核" : "草稿";
        return "报损损失费用单 " + exp.getExpenseNo() + "（" + st + "，非资金）";
    }

    // ==================== 私有 ====================

    /** 该来源的**最后一张**费用单（含已作废：反审核后重审要复用同一张，保持单号稳定） */
    private FinanceExpense latestExpenseBySource(String sourceBillType, Long sourceId) {
        if (sourceBillType == null || sourceId == null) return null;
        return expenseMapper.selectOne(new LambdaQueryWrapper<FinanceExpense>()
                .eq(FinanceExpense::getSourceBillType, sourceBillType)
                .eq(FinanceExpense::getSourceId, sourceId)
                .orderByDesc(FinanceExpense::getId)
                .last("LIMIT 1"));
    }

    /** 供应商/加工厂承担 ⇒ **对供应商的应收**（索赔）：按报损单号幂等复用同一行 */
    private void upsertClaimReceivable(String sourceBillType, Long sourceId, String sourceBillNo, BigDecimal amount,
                                       Long supplierId, String supplierName, LocalDate dueDate, String remark) {
        FinanceReceivable exist = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, sourceBillNo).last("LIMIT 1"));
        FinanceReceivable fr = exist != null ? exist : new FinanceReceivable();
        fr.setBillNo(sourceBillNo);
        fr.setSubjectType(SubjectType.SUPPLIER.getCode());
        fr.setSupplierId(supplierId);
        fr.setSupplierName(supplierName);
        fr.setSourceBillType(sourceBillType);
        fr.setSourceBillNo(sourceBillNo);
        fr.setSourceId(sourceId);
        fr.setAmount(amount);
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(amount);
        fr.setDueDate(dueDate);
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        fr.setRemark(remark);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fr.setCompanyId(cid);
        if (exist != null) receivableMapper.updateById(fr);
        else receivableMapper.insert(fr);
    }

    /** 内部承担 ⇒ **报损损失费用单**（非资金）：按来源幂等复用同一张，反审核作废后重审沿用同一单号 */
    private void upsertLossExpense(String sourceBillType, Long sourceId, String sourceBillNo, BigDecimal amount,
                                   LocalDate lossDate, String remark) {
        FinanceExpense exist = latestExpenseBySource(sourceBillType, sourceId);
        if (exist != null) {
            if (DocStatus.AUDITED.getCode().equals(exist.getStatus())) return;   // 幂等：已审核不动
            expenseMapper.update(null, new LambdaUpdateWrapper<FinanceExpense>()
                    .eq(FinanceExpense::getId, exist.getId())
                    .set(FinanceExpense::getAmount, amount)
                    .set(FinanceExpense::getExpenseDate, lossDate != null ? lossDate : LocalDate.now())
                    .set(FinanceExpense::getRemark, remark)
                    .set(FinanceExpense::getStatus, DocStatus.DRAFT.getCode()));
            expenseService.audit(exist.getId());
            return;
        }
        FinanceExpense e = new FinanceExpense();
        e.setExpenseType(ExpenseType.LOSS.getCode());
        e.setAmount(amount);
        e.setExpenseDate(lossDate != null ? lossDate : LocalDate.now());
        e.setAccountId(null);                       // 非资金费用：不写资金流水、不扣账户
        e.setRemark(remark);
        e.setSourceBillType(sourceBillType);
        e.setSourceId(sourceId);
        e.setSourceBillNo(sourceBillNo);
        expenseService.create(e);
        expenseService.audit(e.getId());
    }
}
