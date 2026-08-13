package com.beichen.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一库存实体（合并 inventory_warehouse_stock + outsource_warehouse_stock）
 * <p>material_id 和 product_id 互斥：成品库存用 product_id，物料库存用 material_id</p>
 */
@Data
@TableName("warehouse_stock")
public class WarehouseStock {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仓库ID */
    private Long warehouseId;

    /** 成品ID（自有仓成品库存） */
    private Long productId;

    /** 物料ID（委外仓物料库存，对应 outsource_material.id） */
    private Long materialId;

    /** 品质等级: A/B/C/DEFECT / GOOD/DEFECT */
    private String qualityType;

    /** 库存数量 */
    private BigDecimal quantity;

    /** 可用数量（仅成品库存使用） */
    private BigDecimal availableQuantity;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
