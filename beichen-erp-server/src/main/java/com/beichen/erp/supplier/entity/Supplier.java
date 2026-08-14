package com.beichen.erp.supplier.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String contact;
    /** 类型编码列表（不持久化，用于返回前端） */
    @TableField(exist = false)
    private java.util.List<String> typeCodes;

    private String phone;

    private String address;

    private Integer status;

    private Integer hasDisplay;

    private Integer hasTouch;

    private Long relatedSupplierId;

    /** 应付余额（实时汇总，不落库，用于列表展示） */
    @TableField(exist = false)
    private BigDecimal payableBalance;

    /** 账期（月） */
    private Integer creditPeriodMonths;

    /** 账期（天） */
    private Integer creditPeriod;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
