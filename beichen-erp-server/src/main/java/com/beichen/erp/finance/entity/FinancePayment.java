package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("finance_payment")
public class FinancePayment {

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
    private Long supplierId;
    private String supplierName;
    /** 往来主体类型：product/factory/material/solution（创建时按供应商标签固化，列表可按类型筛选） */
    private String supplierType;
    private Long accountId;
    private String accountName;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private String status;
    private String remark;
    private String attachUrl;

    /**
     * 来源单据类型 / ID（2026-10-09 随 V6 新增，与收款单 {@code finance_receipt} 同口径）。
     * <p>系统自动付款（采购单现金结算）时写入，用于：① 幂等（同一采购单不重复生成付款单）；
     * ② 反审核采购单时据此冲正对应付款单。人工创建的付款单为 NULL。</p>
     */
    private String sourceBillType;
    private Long sourceId;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
