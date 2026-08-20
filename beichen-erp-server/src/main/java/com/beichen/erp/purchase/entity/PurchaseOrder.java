package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_order")
public class PurchaseOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long supplierId;

    /** 供应商名称（实时查名，不落库） */
    @TableField(exist = false)
    private String supplierName;

    private Long warehouseId;

    private LocalDate orderDate;

    private String status;

    private Integer taxIncluded;

    private BigDecimal taxRate;

    private BigDecimal totalAmount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
