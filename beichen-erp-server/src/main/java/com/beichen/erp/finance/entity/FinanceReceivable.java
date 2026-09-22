package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("finance_receivable")
public class FinanceReceivable {

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充
     *  （台账由单据/收款付款动作触发，此处记录触发该台账的操作人） */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    /** 审核人（ID + 姓名快照）—— 台账无审核流程时为空 */
    private Long auditorId;

    private String auditorName;


    @TableId(type = IdType.AUTO)
    private Long id;

    private String billNo;

    private Long customerId;

    private String customerName;

    /**
     * 往来主体类型：CUSTOMER=客户应收（默认，销售业务） / SUPPLIER=供应商应收（应付转应收产生）。
     * CUSTOMER 看 customerId，SUPPLIER 看 supplierId。
     */
    private String subjectType;

    /** 供应商ID（subjectType=SUPPLIER 时有值；客户应收为空） */
    private Long supplierId;

    /** 供应商名称（冗余留痕，SUPPLIER 时展示） */
    private String supplierName;

    private String sourceBillType;

    private String sourceBillNo;

    private Long sourceId;

    private BigDecimal amount;

    private BigDecimal paidAmount;

    private BigDecimal unpaidAmount;

    private LocalDate dueDate;

    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
