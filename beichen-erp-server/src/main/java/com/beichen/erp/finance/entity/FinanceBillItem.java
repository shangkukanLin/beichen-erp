package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("finance_bill_item")
public class FinanceBillItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long billId;
    private String sourceBillType;
    private String sourceBillNo;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private BigDecimal unpaidAmount;
    private LocalDate dueDate;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
}
