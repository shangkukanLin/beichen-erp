package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 品质重分类主表
 */
@Data
@TableName("product_reclassify")
public class InventoryProductReclassify {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long warehouseId;

    private LocalDate reclassifyDate;

    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
