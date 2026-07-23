package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sale_outbound_item")
public class SaleOutboundItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long outboundId;

    private Long orderItemId;

    private Long productId;

    private String materialCode;

    private String materialName;

    private String spec;

    private String unit;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
