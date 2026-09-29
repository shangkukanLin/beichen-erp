package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款单**分款明细**（2026-09-29 用户口径「收款账户可以添加，比如 A 账户收款 50、B 账户收款 100」）。
 *
 * <p>一个收款单可拆到多个账户收款，每行 = 一个账户本次实际收到的钱；审核时**按行各写一条资金流水**
 * （账户余额由流水实时累计，见 {@code FinanceCashflow}），反审核按行各写一条冲正流水。</p>
 *
 * <p>与主表的关系：{@code finance_receipt.amount} = 本表金额合计（收款总额）；
 * {@code finance_receipt.account_id/account_name} = **首行**账户（兼容列表列、老读法、销售单现金结算的自动单）。
 * 落库口径是"**分款表即权威**"：任何建单入口（含系统自动单）都会归一化成至少一条分款行，
 * 因此审核/反审核只认本表，不需要"有没有分款行"的分支判断。</p>
 */
@Data
@TableName("finance_receipt_account")
public class FinanceReceiptAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 收款单ID（finance_receipt.id） */
    private Long receiptId;
    private Long accountId;
    /** 账户名称（冗余留痕：账户改名后单据仍显示当时名称） */
    private String accountName;
    /** 该账户本次收款金额（> 0） */
    private BigDecimal amount;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
}
