package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应付转应收单
 * <p>
 * 业务背景：退货、超损扣款产生的是「负向应付」，正常情况下在下次付款时净额抵扣；
 * 当月没有货款要结算时，就需要对方把钱付给我们——本单据即把这笔冲减项转为「向供应商收款」的应收。
 * </p>
 * <p>
 * 流程与系统其他单据一致：草稿 → 审核（生成应收台账 + 来源应付标记已转）→ 反审核（冲销应收 + 恢复抵扣）→ 作废（仅草稿）。
 * </p>
 */
@Data
@TableName("finance_payable_transfer")
public class PayableTransfer {

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;


    @TableId(type = IdType.AUTO)
    private Long id;

    /** 转应收单号 PZ-yyyyMMdd-NNN */
    private String code;

    /** 来源应付台账ID（负数冲减项） */
    private Long payableId;

    /** 来源应付台账单号（冗余，列表展示用） */
    private String payableBillNo;

    private Long supplierId;

    /** 供应商名称（冗余：开单时留痕，后续改名不影响历史单据） */
    private String supplierName;

    /** 往来主体类型：product/factory/material/solution（从来源应付带出） */
    private String supplierType;

    /** 转出金额（正数，取来源应付金额的绝对值） */
    private BigDecimal amount;

    private LocalDate transferDate;

    /** 状态：DRAFT/AUDITED/CANCELLED */
    private String status;

    private String remark;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
