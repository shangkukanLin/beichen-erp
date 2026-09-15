package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("finance_receipt")
public class FinanceReceipt {
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
    private Long accountId;
    private String accountName;
    private LocalDate receiptDate;
    private BigDecimal amount;
    private String status;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
