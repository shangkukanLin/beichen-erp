package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 应收台账辅助：冲回应收（与应付 PayableHelper 对称） */
@Component
@RequiredArgsConstructor
public class ReceivableHelper {

    private final FinanceReceivableMapper receivableMapper;

    /** 台账单号列宽上限（与 `finance_receivable.bill_no` / `finance_payable.bill_no` 的 varchar(50) 一致） */
    private static final int BILL_NO_MAX = 50;

    /**
     * 预收台账单号（D5 口径 · 2026-09-12；I27 修复 2026-09-18）。
     * <p>应收台账的命名规则是「来源单据号（+ 业务后缀）」（如销售单 `XS-…`、费用 `…-FEE`、报损 `…-LOSS`），
     * 预收的来源就是收款单，故取 `基础单号 + "-" + ADVANCE`；与来源的关联另由
     * `source_bill_type/source_bill_no/source_id` 承载（反审核按 `source_id` 精确定位冲回）。
     * 与应付侧对称：应付走独立号段（{@code PayableHelper.newBillNo()} → `YF-…`，见 D1）。</p>
     *
     * <p><b>I27 修复点</b>（旧实现直接 `入参 + "-ADVANCE"`，存在两个坑）：</p>
     * <ol>
     *   <li><b>幂等归一化</b>：先把入参末尾**所有** `-ADVANCE` 去掉再追加一次。
     *       旧实现下若预收单又被当作核销目标，会生成 `…-ADVANCE-ADVANCE-…`，后缀越叠越长，
     *       最终撑破 varchar(50) → 审核报 `Data too long for column 'bill_no'`（单据永久卡草稿）。</li>
     *   <li><b>长度护栏</b>：归一化后仍超列宽时退化为稳定短号 `ADV-<hash>`；
     *       同一基础单号恒得同一值，保证"反审核后重新审核"仍能按 bill_no 复用同一行（避免撞唯一键）。</li>
     * </ol>
     */
    public static String advanceBillNo(String billNo) {
        String base = billNo == null ? "" : billNo.trim();
        String suffix = "-" + SettlementStatus.ADVANCE.getCode();
        while (base.endsWith(suffix)) base = base.substring(0, base.length() - suffix.length());
        if (base.isEmpty()) base = "AR";
        String candidate = base + suffix;
        if (candidate.length() > BILL_NO_MAX) {
            candidate = "ADV-" + Integer.toHexString(base.hashCode() & 0x7fffffff).toUpperCase();
        }
        return candidate;
    }

    /**
     * 冲回应收（反审核时使用）：按单据编号定位未结清应收，置为已冲回且不可恢复。
     * 资金安全护栏：已有收款记录的应收禁止冲回，需先退款再反审核。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reverseReceivable(String billNo) {
        if (billNo == null || billNo.isBlank()) return;
        // 按单据编号定位应收台账
        FinanceReceivable fr = receivableMapper.selectOne(
                new LambdaQueryWrapper<FinanceReceivable>().eq(FinanceReceivable::getBillNo, billNo));
        if (fr == null) throw new BusinessException("应收单不存在：单据号 " + billNo);
        // 资金安全护栏：已收款不可直接冲回
        if (fr.getPaidAmount() != null && fr.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
            throw new BusinessException("应收单「" + fr.getBillNo() + "」已有收款记录，不可反审核");
        // 置为已冲回并清零未收金额（客户余额回退由调用方 unAudit 统一处理，避免重复回退）
        cancelLedger(fr);
    }

    /**
     * 作废台账（I29 口径，2026-09-18，与应付 {@code PayableHelper.cancelLedger} 对称）：
     * **已作废的应收不计金额** —— 状态置 CANCELLED 的同时把 {@code amount} 一并清零，
     * 使「amount = paid + unpaid」在**所有行**上恒成立（作废行 paid/unpaid 均为 0）。
     * 原金额写入 {@code remark} 留痕，避免金额凭空消失无法追溯。
     */
    public void cancelLedger(FinanceReceivable fr) {
        if (fr == null) return;
        BigDecimal old = fr.getAmount();
        fr.setStatus(SettlementStatus.CANCELLED.getCode());
        fr.setUnpaidAmount(BigDecimal.ZERO);
        if (old != null && old.compareTo(BigDecimal.ZERO) != 0) {
            String mark = "[已作废] 原金额=" + old.stripTrailingZeros().toPlainString();
            String r = fr.getRemark();
            fr.setRemark(r == null || r.isBlank() ? mark : r + " " + mark);
        }
        fr.setAmount(BigDecimal.ZERO);
        receivableMapper.updateById(fr);
    }
}
