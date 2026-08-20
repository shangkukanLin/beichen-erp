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

    private Long warehouseId;

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
