package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发票登记（财务发票管理，税务口径）
 * <p>销项（我方开出的发票）/ 进项（我方收到的发票）登记，用于掌握销项/进项税额与应纳增值税。
 * 与单据上的「含税开关」（业务口径拆税）互补，不强制关联业务单据。</p>
 */
@Data
@TableName("finance_invoice")
public class FinanceInvoice {

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

    /** 发票号码（未作废范围内唯一） */
    private String invoiceNo;

    /** 方向: SALE=销项 PURCHASE=进项 */
    private String direction;

    /** 发票类型（增值税专用发票/增值税普通发票/电子专票/电子普票） */
    private String invoiceKind;

    /** 开票日期 */
    private LocalDate invoiceDate;

    /** 对方单位（销项=购买方，进项=销售方） */
    private String partnerName;

    /** 不含税金额 */
    private BigDecimal amount;

    /** 税率(%) */
    private BigDecimal taxRate;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 价税合计 */
    private BigDecimal totalAmount;

    /** 关联业务单号（销售单/采购单号，可选） */
    private String sourceBillCode;

    private String remark;

    /** 状态（REGISTERED=已登记 CANCELLED=已作废） */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
