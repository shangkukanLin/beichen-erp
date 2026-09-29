package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("finance_receipt")
public class FinanceReceipt {

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
    private String code;
    private Long customerId;
    private String customerName;
    /** 往来主体类型：CUSTOMER=客户收款（默认） / SUPPLIER=供应商收款（应付转应收的收款闭环） */
    private String subjectType;
    /** 供应商ID（subjectType=SUPPLIER 时有值） */
    private Long supplierId;
    /** 供应商名称（冗余留痕） */
    private String supplierName;
    /**
     * 收款账户（2026-09-29 多账户改造后 = **分款明细首行**的快照）。
     * <p>真正的收款分款在 {@code finance_receipt_account}（一个单可多账户收款）；本字段保留是为了
     * 兼容列表「账户」列、老读法与销售单现金结算的自动单。</p>
     */
    private Long accountId;
    private String accountName;
    private LocalDate receiptDate;
    /** 收款总额 = 分款明细金额合计（多账户时 A 50 + B 100 = 150） */
    private BigDecimal amount;
    private String status;
    /**
     * 来源单据（2026-09-18）：销售单现金结算时由「审核销售单」自动生成草稿收款单，
     * 销售单反审核需按此精确定位并联动（草稿→自动作废；已审核→拦住提示先撤收款）。
     */
    private String sourceBillType;
    private String sourceBillNo;
    private Long sourceId;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
