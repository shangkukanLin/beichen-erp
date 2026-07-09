package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("finance_cashflow")
public class FinanceCashflow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowNo;
    private Long accountId;
    private String accountName;
    private String flowType;
    private String relatedBillNo;
    private String relatedBillType;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal balance;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
}
