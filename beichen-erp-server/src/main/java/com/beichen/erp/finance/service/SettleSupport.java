package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.finance.entity.FinanceAccount;
import com.beichen.erp.finance.entity.FinancePayment;
import com.beichen.erp.finance.entity.FinancePaymentItem;
import com.beichen.erp.finance.mapper.FinanceAccountMapper;
import com.beichen.erp.finance.mapper.FinancePaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购侧「结算方式」共享助手（2026-10-09，用户口径：**参考新增销售单做法**）。
 *
 * <p>口径：{@code CASH}=现金 ⇒ 挂应付后**自动生成并立即审核付款单**把应付核销（= 立刻付钱）；
 * {@code CREDIT}=账期 ⇒ 只挂应付。**未指定 = 现金**（用户："老单据默认现金"）。</p>
 *
 * <p>为什么抽成共享组件：采购单（成品采购，审核即付款）与物料订单收货（委外物料，收货审核即付款）
 * 两条链的**校验与付款逻辑必须逐字一致** —— 各写一份是历史债，改一处漏一处的经典来源。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettleSupport {

    public static final String CREDIT = "CREDIT";
    public static final String CASH = "CASH";
    /** 用户 2026-10-09 口径：**老单据默认现金**（未指定即现金 ⇒ 审核后自动付款） */
    public static final String DEFAULT = CASH;

    private final FinanceAccountMapper accountMapper;
    private final FinancePaymentService paymentService;
    private final FinancePaymentMapper paymentMapper;

    /** 归一结果：调用方把这三个值写回自己的单据实体（两个实体类型不同，故不在此处直接 set） */
    public record Normalized(String settleType, Long accountId, BigDecimal amount) {
        public boolean cash() { return CASH.equals(settleType); }
    }

    public static boolean isCash(String settleType) {
        return CASH.equalsIgnoreCase(settleType == null ? "" : settleType.trim());
    }

    /**
     * 结算方式归一 + 校验。
     * <p>与销售侧刻意不同的两点：① <b>未指定 → 现金</b>（销售侧是"未指定 → 账期"，照抄会让"老单据默认现金"落空）；
     * ② <b>非法值直接报错</b>，不静默兜底 —— 本字段会驱动"自动付款"（动钱），写错一个字母就静默变成自动付款
     * 属于不可接受的歧义，必须当场让用户看见。</p>
     * <p>现金 ⇒ 必须给出**可用**（存在且未停用）的付款账户；账期 ⇒ 账户与金额一律清空。</p>
     */
    public Normalized normalize(String rawSettleType, Long accountId, BigDecimal amount, String billLabel) {
        String st;
        if (rawSettleType == null || rawSettleType.isBlank()) {
            st = DEFAULT;
        } else if (CASH.equalsIgnoreCase(rawSettleType.trim())) {
            st = CASH;
        } else if (CREDIT.equalsIgnoreCase(rawSettleType.trim())) {
            st = CREDIT;
        } else {
            throw new BusinessException("结算方式非法：" + rawSettleType + "（只允许 CASH=现金 / CREDIT=账期）");
        }
        if (CREDIT.equals(st)) return new Normalized(st, null, null);
        if (accountId == null)
            throw new BusinessException("结算方式为「现金」时必须选择付款账户（" + billLabel + "）");
        FinanceAccount acc = accountMapper.selectById(accountId);
        if (acc == null) throw new BusinessException("付款账户不存在：ID=" + accountId);
        if (acc.getStatus() != null && acc.getStatus() == 0)
            throw new BusinessException("付款账户已停用，请重新选择：" + acc.getAccountName());
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("本次付款总额必须大于 0（现金结算；不填 = 按应付全额付款）");
        return new Normalized(st, accountId, amount);
    }

    /** 本来源单据已由系统自动生成的付款单（按 {@code finance_payment.source_bill_type/source_id} 反查） */
    public List<FinancePayment> autoPayments(String sourceBillType, Long sourceId) {
        if (sourceBillType == null || sourceId == null) return List.of();
        return paymentMapper.selectList(new LambdaQueryWrapper<FinancePayment>()
                .eq(FinancePayment::getSourceBillType, sourceBillType)
                .eq(FinancePayment::getSourceId, sourceId));
    }

    /**
     * 现金结算：按来源生成付款单并**立即审核**（= 立刻付钱、即结算）。
     *
     * @param payableId     待核销的应付台账 ID
     * @param payableBillNo 应付台账号（核销明细要带）
     * @param payableAmount 应付金额（付款上限；{@code settleAmount} 为空时按它全额付）
     * @param settleAmount  本次付款额（null = 全额）
     * @param bizNo         业务单号（报错信息用，便于用户定位到具体单据）
     * @return 生成的付款单 ID；已存在未作废自动付款单时返回既有 ID
     */
    public Long payNow(String sourceBillType, Long sourceId, Long supplierId, Long accountId,
                       Long payableId, String payableBillNo,
                       BigDecimal payableAmount, BigDecimal settleAmount, String billLabel, String bizNo) {
        for (FinancePayment exist : autoPayments(sourceBillType, sourceId)) {
            if (!DocStatus.CANCELLED.getCode().equals(exist.getStatus())) return exist.getId();
        }
        BigDecimal total = payableAmount != null ? payableAmount : BigDecimal.ZERO;
        BigDecimal paid = settleAmount != null ? settleAmount : total;
        // 上下界在**生成之前**自查并把错误归因到业务单（否则坏数据会在付款单层抛出用户无从定位的错）
        if (paid.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException(billLabel + "「本次付款总额」必须大于 0（当前 "
                    + paid.stripTrailingZeros().toPlainString() + "，单号 " + bizNo + "）");
        if (paid.compareTo(total) > 0)
            throw new BusinessException("本次付款总额 " + paid.stripTrailingZeros().toPlainString()
                    + " 不能超过应付金额 " + total.stripTrailingZeros().toPlainString()
                    + "（单号 " + bizNo + "）");

        FinancePayment p = new FinancePayment();
        p.setSupplierId(supplierId);
        p.setAccountId(accountId);
        p.setPaymentDate(LocalDate.now());
        p.setAmount(paid);
        p.setStatus(DocStatus.DRAFT.getCode());
        p.setRemark("系统自动付款（" + billLabel + "现金结算 " + bizNo + "）");
        p.setSourceBillType(sourceBillType);
        p.setSourceId(sourceId);

        FinancePaymentItem it = new FinancePaymentItem();
        it.setPayableId(payableId);
        it.setPayableBillNo(payableBillNo);
        it.setThisAmount(paid);
        // 两参重载：accounts 缺省 ⇒ 由单账户字段 + 核销合计归一化成一条分款行（现金结算单账户足够）
        paymentService.create(p, List.of(it));

        Long pid = p.getId();
        if (pid == null) {
            pid = autoPayments(sourceBillType, sourceId).stream()
                    .filter(x -> !DocStatus.CANCELLED.getCode().equals(x.getStatus()))
                    .map(FinancePayment::getId).findFirst().orElse(null);
        }
        if (pid == null) throw new BusinessException("现金结算付款单生成失败（" + billLabel + " " + bizNo + "）");
        paymentService.audit(pid);
        return pid;
    }

    /**
     * 反审核业务单前**必须先冲正**自动付款单（2026-10-09）：
     * 否则"应付已付款 ⇒ 不可反审核"的既有护栏会把我方自动付款当成人工核销，把反审核永久拦死
     * （销售侧踩过同一个顺序坑）。已审核 → 反审核；再作废留痕；草稿 → 直接作废。人工付款单不受影响。
     */
    public void reverseAutoPayments(String sourceBillType, Long sourceId) {
        for (FinancePayment p : autoPayments(sourceBillType, sourceId)) {
            if (DocStatus.CANCELLED.getCode().equals(p.getStatus())) continue;
            if (DocStatus.AUDITED.getCode().equals(p.getStatus())) paymentService.unAudit(p.getId());
            paymentService.cancel(p.getId());
        }
    }
}
