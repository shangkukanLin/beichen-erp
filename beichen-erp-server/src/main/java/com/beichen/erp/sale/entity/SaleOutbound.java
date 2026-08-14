package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sale_outbound")
public class SaleOutbound {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long orderId;

    private Long customerId;

    /** 客户名称（实时查名，不落库） */
    @TableField(exist = false)
    private String customerName;

    private Long warehouseId;

    private LocalDate outboundDate;

    private String status;

    private BigDecimal totalAmount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
