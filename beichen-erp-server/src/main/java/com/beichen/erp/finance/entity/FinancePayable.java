package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("finance_payable")
public class FinancePayable {

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

    private Long supplierId;

    private String supplierName;

    /**
     * 往来主体类型：product=供货商 factory=加工厂 material=辅料商 solution=方案商。
     * 开单时按业务场景固化，不实时取 supplier_type_ref——供应商类型变更后不能篡改历史账务。
     */
    private String supplierType;

    private String sourceBillType;

    private String sourceBillNo;

    /** 来源记录ID（交货/收货记录），用于编辑删除时定位 */
    private Long sourceId;

    private BigDecimal amount;

    private BigDecimal paidAmount;

    private BigDecimal unpaidAmount;

    private LocalDate dueDate;

    private String status;

    /**
     * 是否已转应收：0否 1是。
     * 为 1 时表示这笔（负数）冲减项已转成应收向对方收款，付款抵扣时须跳过，避免重复抵扣。
     */
    private Integer transferredToReceivable;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
