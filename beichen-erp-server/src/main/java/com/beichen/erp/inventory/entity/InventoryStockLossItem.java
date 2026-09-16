package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成品报损单明细：产品 × 品质 × 数量，单价默认带出产品成本价（可改） */
@Data
@TableName("inventory_stock_loss_item")
public class InventoryStockLossItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lossId;

    private Long productId;

    /** 产品名称（冗余快照，报损后产品改名不影响历史单据） */
    private String productName;

    /** SKU（冗余快照） */
    private String sku;

    /** 品质等级（ProductQualityType：A/B/C/DEFECT/PENDING） */
    private String qualityType;

    private String unit;

    private BigDecimal quantity;

    /** 报损单价：新增时带出产品成本价（product.cost_price），可手工修改 */
    private BigDecimal unitPrice;

    /** 报损金额 = 数量 × 单价 */
    private BigDecimal amount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
