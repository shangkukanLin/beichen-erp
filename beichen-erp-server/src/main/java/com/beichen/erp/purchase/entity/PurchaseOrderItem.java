package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_order_item")
public class PurchaseOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long productId;

    /** 品质等级: A/B/C/DEFECT */
    private String qualityType;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    /** 产品名称（仅展示用，不映射数据库列） */
    @TableField(exist = false)
    private String productName;

    private LocalDateTime createTime;
}
