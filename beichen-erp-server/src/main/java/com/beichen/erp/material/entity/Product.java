package com.beichen.erp.material.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.beichen.erp.material.common.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    @NotBlank(message = "产品名称不能为空")
    private String name;
    private Long brandId;
    private String category;
    private String spec;
    /** 通用型号（适用多款机型） */
    private String generalModel;
    private String unit;
    private BigDecimal safetyStock;
    private ProductStatus status;
    private Long projectId;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
