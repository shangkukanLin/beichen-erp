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
