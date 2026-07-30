package com.beichen.erp.material.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product_quality")
public class ProductQuality {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /** 等级类型: A/B/C/DEFECT(ProductQualityType.name()) */
    private String qualityType;

    private BigDecimal quantity;

    private BigDecimal safetyStock;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
