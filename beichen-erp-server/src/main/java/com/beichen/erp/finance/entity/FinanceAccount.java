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
    /** 期初余额（开户时的初始资金，落库，之后不可变） */
    private BigDecimal openingBalance;
    /** 当前余额（实时汇总，不落库，用于列表展示） */
    @TableField(exist = false)
    private BigDecimal balance;
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
