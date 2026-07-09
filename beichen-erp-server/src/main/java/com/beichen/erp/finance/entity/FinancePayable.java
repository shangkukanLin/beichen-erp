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

    private String sourceBillType;

    private String sourceBillNo;

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
