package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("finance_payable")
public class FinancePayable {

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
