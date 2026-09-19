package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 应收台账的**只读查询**（2026-09-19 期 2「读隔离」抽出）。
 *
 * <p>为什么需要这个类：收款页（{@code /api/finance/receipt}）的「核销明细」要从**未结清应收**里选，
 * 原先它直读应收页的接口 {@code /api/finance/receivable/unpaid}（需 {@code finance:receivable}）
 * ⇒ 只有 {@code finance:receipt} 的用户会 403（这是接口级权限校准后新查出的真实缺口）。</p>
 *
 * <p>查询口径收敛到本类，由 {@code FinanceReceivableController}（应收页）与
 * {@code FinanceReceiptController}（收款页）共用，方法语义与原先接口逐字一致。</p>
 */
@Component
@RequiredArgsConstructor
public class ReceivableQuery {

    private final FinanceReceivableMapper receivableMapper;

    /**
     * 未结清应收（供收款单下拉）：客户应收按 {@code customerId} 查，供应商应收（应付转应收）按 {@code supplierId} 查。
     * <p>口径：排除已结清（SETTLED）与已作废（CANCELLED）。</p>
     *
     * <p><b>2026-09-19 修复（F7-41）</b>：再排除 <b>ADVANCE（预收台账）</b> 与 <b>非正数应收</b> —— 与付款侧
     * {@code PayableQuery.unpaid} 的 {@code amount > 0} 完全对称。修复前负数/预收台账会出现在核销下拉里：
     * ① ADVANCE 选中后审核必被 I27 守卫拒绝（"可选但必然失败"）；② 负数应收（UNSETTLED，实测 -5）
     * 连 I27 都不覆盖，选中核销会走"超额"分支<b>再生成一条预收 ADVANCE</b>
     * —— 正是 I11（2026-09-18）在应付侧修掉的同型路径。</p>
     */
    public List<FinanceReceivable> unpaid(Long customerId, Long supplierId, String subjectType) {
        if (supplierId != null || (subjectType != null && "SUPPLIER".equalsIgnoreCase(subjectType))) {
            return receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                    .eq(FinanceReceivable::getSubjectType, SubjectType.SUPPLIER.getCode())
                    .eq(supplierId != null, FinanceReceivable::getSupplierId, supplierId)
                    .ne(FinanceReceivable::getStatus, SettlementStatus.SETTLED.getCode())
                    .ne(FinanceReceivable::getStatus, SettlementStatus.CANCELLED.getCode())
                    .ne(FinanceReceivable::getStatus, SettlementStatus.ADVANCE.getCode())
                    .gt(FinanceReceivable::getAmount, BigDecimal.ZERO)
                    .orderByDesc(FinanceReceivable::getId));
        }
        return receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getCustomerId, customerId)
                .ne(FinanceReceivable::getStatus, SettlementStatus.SETTLED.getCode())
                .ne(FinanceReceivable::getStatus, SettlementStatus.CANCELLED.getCode())
                .ne(FinanceReceivable::getStatus, SettlementStatus.ADVANCE.getCode())
                .gt(FinanceReceivable::getAmount, BigDecimal.ZERO)
                .orderByDesc(FinanceReceivable::getId));
    }
}
