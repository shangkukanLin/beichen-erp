package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
     */
    public List<FinanceReceivable> unpaid(Long customerId, Long supplierId, String subjectType) {
        if (supplierId != null || (subjectType != null && "SUPPLIER".equalsIgnoreCase(subjectType))) {
            return receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                    .eq(FinanceReceivable::getSubjectType, SubjectType.SUPPLIER.getCode())
                    .eq(supplierId != null, FinanceReceivable::getSupplierId, supplierId)
                    .ne(FinanceReceivable::getStatus, SettlementStatus.SETTLED.getCode())
                    .ne(FinanceReceivable::getStatus, SettlementStatus.CANCELLED.getCode())
                    .orderByDesc(FinanceReceivable::getId));
        }
        return receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getCustomerId, customerId)
                .ne(FinanceReceivable::getStatus, SettlementStatus.SETTLED.getCode())
                .ne(FinanceReceivable::getStatus, SettlementStatus.CANCELLED.getCode())
                .orderByDesc(FinanceReceivable::getId));
    }
}
