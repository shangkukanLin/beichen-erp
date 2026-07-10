package com.beichen.erp.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String contact;

    private String phone;

    private String address;

    private Integer creditPeriod;

    private Integer creditPeriodMonths;

    private BigDecimal creditLimit;

    private BigDecimal receivableBalance;

    private BigDecimal prepaidBalance;

    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
