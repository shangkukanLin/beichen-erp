package com.beichen.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.finance.entity.FinancePaymentAccount;
import org.apache.ibatis.annotations.Mapper;

/** 付款单分款明细（2026-09-29 多账户付款；与 FinanceReceiptAccountMapper 对称） */
@Mapper
public interface FinancePaymentAccountMapper extends BaseMapper<FinancePaymentAccount> {
}
