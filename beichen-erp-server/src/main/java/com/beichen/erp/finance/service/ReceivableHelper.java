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
        fr.setStatus(SettlementStatus.CANCELLED.getCode());
        fr.setUnpaidAmount(BigDecimal.ZERO);
        receivableMapper.updateById(fr);
    }
}
