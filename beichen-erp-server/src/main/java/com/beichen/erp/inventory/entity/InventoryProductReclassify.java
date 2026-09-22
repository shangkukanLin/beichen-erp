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

    /** 审核人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「审核人」，审核时盖章
     *  （本单的制单人字段 createBy/createByName 早已存在，无需重复） */
    private Long auditorId;

    private String auditorName;


    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long warehouseId;

    private LocalDate reclassifyDate;

    private String status;

    private String remark;

    /** 整理人（建单时登录的账户），用于追溯是谁做的品质整理 */
    private Long createBy;

    /** 整理人名称（冗余展示，账户名快照） */
    private String createByName;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
