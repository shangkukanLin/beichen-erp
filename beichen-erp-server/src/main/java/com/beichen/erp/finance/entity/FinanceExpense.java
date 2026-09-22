package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 费用登记单（财务费用管理）
 * <p>审核后生成「费用支出」资金流水（账户扣款）；反审核写「费用冲正」流水冲回，
 * 与收款单的审核/冲正模式保持对称。账户余额由资金流水实时累计，不维护快照。</p>
 */
@Data
@TableName("finance_expense")
public class FinanceExpense {

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    /** 审核人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「审核人」，审核时盖章 */
    private Long auditorId;

    private String auditorName;


    @TableId(type = IdType.AUTO)
    private Long id;

    /** 费用单号（FY-yyyyMMddXXX） */
    private String expenseNo;

    /** 费用类型（中文字面量：办公费/房租水电/工资社保/运输费/差旅费/业务招待/其他） */
    private String expenseType;

    /** 费用金额 */
    private BigDecimal amount;

    /** 费用日期（利润表按此归月） */
    private LocalDate expenseDate;

    /** 支出账户ID */
    private Long accountId;

    /** 支出账户名称（冗余展示） */
    private String accountName;

    private String remark;

    /** 状态（DRAFT/AUDITED/CANCELLED） */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
