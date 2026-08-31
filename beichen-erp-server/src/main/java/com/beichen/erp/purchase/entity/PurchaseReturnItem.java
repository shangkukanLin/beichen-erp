package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_return_item")
public class PurchaseReturnItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long returnId;

    /** 关联采购单明细ID（可选，用于追溯） */
    private Long purchaseOrderItemId;

    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 产品名称（关联 product，不落库，随明细接口一起返回） */
    @TableField(exist = false)
    private String productName;

    /** 品质等级: A/B/C/DEFECT */
    private String qualityType;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
