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

    /** SKU（展示用，非表字段；库存接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 物料ID（委外仓物料库存，对应 outsource_material.id） */
    private Long materialId;

    /**
     * 品质等级（成品与委外物料共用本列，取值随记录类型而定，两者互斥不会串）：
     * <ul>
     *   <li>成品记录（product_id 非空、material_id 为空）：取 {@link com.beichen.erp.material.common.ProductQualityType}
     *       —— A / B / C / DEFECT / PENDING</li>
     *   <li>委外物料记录（material_id 非空、product_id 为空）：取 {@link com.beichen.erp.outsource.common.QualityType}
     *       —— GOOD / DEFECT</li>
     * </ul>
     * 注意两套体系都有 DEFECT（均表示不良品），但 GOOD 仅存在于委外物料体系。
     */
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
