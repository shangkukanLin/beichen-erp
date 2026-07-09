package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("finance_payment")
public class FinancePayment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long supplierId;
    private String supplierName;
    private Long accountId;
    private String accountName;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private String status;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
