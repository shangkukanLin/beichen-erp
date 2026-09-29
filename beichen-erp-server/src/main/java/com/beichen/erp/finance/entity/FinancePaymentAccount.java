package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 付款单**分款明细**（2026-09-29 用户口径「付款侧与收款侧对称：账户可以添加 + 核销项做成开关」）。
 *
 * <p>一个付款单可拆到多个账户付款（如 A 账户付 50、B 账户付 100），每行 = 一个账户本次实际付出的钱；
 * 审核时**按行各写一条资金流水**（账户余额由流水实时累计，见 {@code FinanceCashflow}）并逐账户校验余额，
 * 反审核按行各写一条冲正流水。</p>
 *
 * <p>与主表的关系：{@code finance_payment.amount} = 本表金额合计（付款总额）；
 * {@code finance_payment.account_id/account_name} = **首行**账户（兼容列表列与老读法）。
 * 落库口径是"**分款表即权威**"：任何建单入口都会归一化成至少一条分款行，
 * 因此审核/反审核只认本表，不需要"有没有分款行"的分支判断。</p>
 *
 * <p>与 {@link FinanceReceiptAccount} 逐字对称（收款侧先做的多账户，本类是付款侧镜像）。</p>
 */
@Data
@TableName("finance_payment_account")
public class FinancePaymentAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 付款单ID（finance_payment.id） */
    private Long paymentId;
    private Long accountId;
    /** 账户名称（冗余留痕：账户改名后单据仍显示当时名称） */
    private String accountName;
    /** 该账户本次付款金额（> 0） */
    private BigDecimal amount;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
}
