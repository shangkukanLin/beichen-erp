package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售单**分款明细**（2026-09-30 用户口径：现金收款也要能多账户收款，并把金额填充分摊）。
 *
 * <p>一个销售单（结算方式=现金）可拆到多个账户收款，每行 = 一个账户本次实际收到的钱。
 * 审核销售单时按本表逐账户生成收款单分款行（见 {@code SaleOrderServiceImpl#createCashReceipt}），
 * 与收款单/付款单的多账户能力保持同构。</p>
 *
 * <p>与主表的关系：{@code sale_order.settle_amount} = 本表金额合计（本次收款总额）；
 * {@code sale_order.settle_account_id/settle_account_name} = **首行**账户
 * （兼容列表列、老读法，以及尚未升级到多账户的历史数据）。</p>
 *
 * <p>前端对应组件：{@code beichen-erp-web/src/components/AccountSplitTable.vue}
 * （总额输入 + 单一自动吸收行分摊）。</p>
 */
@Data
@TableName("sale_order_settle_account")
public class SaleOrderSettleAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 销售单ID（sale_order.id） */
    private Long orderId;
    private Long accountId;
    /** 账户名称（冗余留痕：账户改名后单据仍显示当时名称） */
    private String accountName;
    /** 该账户本次收款金额（> 0） */
    private BigDecimal amount;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
}
