package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_inbound")
public class PurchaseInbound {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long orderId;

    private Long supplierId;

    private String supplierName;

    private Long warehouseId;

    private LocalDate inboundDate;

    private String status;

    private BigDecimal totalAmount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
