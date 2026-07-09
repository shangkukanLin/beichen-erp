package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_inbound_item")
public class PurchaseInboundItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inboundId;

    private Long orderItemId;

    private Long materialId;

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
