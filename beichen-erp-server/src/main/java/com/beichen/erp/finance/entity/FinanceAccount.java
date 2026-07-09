package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("finance_account")
public class FinanceAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String accountName;
    private String accountType;
    private String bankName;
    private String accountNo;
    private BigDecimal balance;
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
