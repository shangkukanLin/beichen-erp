package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sale_order")
public class SaleOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long customerId;

    /** 客户名称（实时查名，不落库） */
    @TableField(exist = false)
    private String customerName;

    private Long warehouseId;

    private LocalDate orderDate;

    private String status;

    /** 审核时间（财务分析利润表按此归月；存量单据为 NULL 时按 createTime 兜底） */
    private LocalDateTime auditTime;

    private Integer taxIncluded;

    private BigDecimal taxRate;

    /** 税额（taxIncluded=1 时按税率从含税总额中拆出：total × rate/(100+rate)） */
    private BigDecimal taxAmount;

    private BigDecimal totalAmount;

    /**
     * 结算方式（2026-09-18，**按单记**）：CREDIT 账期（只挂应收）/ CASH 现金（审核后自动生成草稿收款单）。
     * 见 {@link com.beichen.erp.sale.common.SettleType}。
     */
    private String settleType;

    /** 结算账户ID（finance_account.id）：settleType=CASH 时必填，默认取现金账户 */
    private Long settleAccountId;

    /** 结算账户名（实时查名，不落库） */
    @TableField(exist = false)
    private String settleAccountName;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
