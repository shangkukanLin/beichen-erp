package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 品质重分类明细表
 */
@Data
@TableName("product_reclassify_item")
public class InventoryProductReclassifyItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reclassifyId;

    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 产品名称（展示用，非表字段；明细接口回填） */
    @TableField(exist = false)
    private String productName;

    /** 原品质等级: A/B/C/DEFECT */
    private String fromQuality;

    /** 目标品质等级: A/B/C/DEFECT */
    private String toQuality;

    private BigDecimal quantity;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
