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
    /**
     * SKU 编码（产品级唯一，公司内不重复）。
     * 新增时留空则由后端按最大流水自动生成（SKU-000001），也可手工填写（需唯一）。
     */
    private String sku;
    private Long brandId;
    private String category;
    private String spec;
    /** 通用型号（适用多款机型） */
    private String generalModel;
    private String unit;
    private BigDecimal safetyStock;

    /** 移动加权平均成本价（入库自动更新；cost_manual=1 时以手填为准） */
    private BigDecimal costPrice;

    /** 成本价是否手工锁定（1=手改，自动加权跳过） */
    private Integer costManual;

    /** 最近一次入库单价（参考价） */
    private BigDecimal lastInPrice;

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
