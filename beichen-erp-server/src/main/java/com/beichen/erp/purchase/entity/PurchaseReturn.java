package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_return")
public class PurchaseReturn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long supplierId;

    /** 供货商名称（非表字段，查询时按 supplierId 回填） */
    @TableField(exist = false)
    private String supplierName;

    private Long warehouseId;

    /** 关联采购单ID（可选，用于追溯） */
    private Long purchaseOrderId;

    /** 关联采购单号（冗余展示） */
    private String purchaseOrderCode;

    private LocalDate returnDate;

    private String status;

    private BigDecimal totalAmount;

    private String remark;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
