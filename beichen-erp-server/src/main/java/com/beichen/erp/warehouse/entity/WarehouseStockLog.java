package com.beichen.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一库存流水实体（合并 inventory_stock_log + outsource_stock_log）
 * <p>material_id 和 product_id 互斥：成品流水用 product_id，物料流水用 material_id</p>
 */
@Data
@TableName("warehouse_stock_log")
public class WarehouseStockLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仓库ID */
    private Long warehouseId;

    /** 成品ID（自有仓成品流水） */
    private Long productId;

    /** 物料ID（委外仓物料流水） */
    private Long materialId;

    /** 物料名称（委外仓流水冗余字段，用于展示） */
    private String materialName;

    /** 品质等级 */
    private String qualityType;

    /** 变动类型（枚举名，如 PURCHASE_IN） */
    private String changeType;

    /** 变动数量 */
    private BigDecimal changeQuantity;

    /** 变动前库存 */
    private BigDecimal beforeQuantity;

    /** 变动后库存 */
    private BigDecimal afterQuantity;

    /** 关联单据号 */
    private String relatedBillNo;

    /** 关联单据类型（枚举名，如 PURCHASE_ORDER） */
    private String relatedBillType;

    /** 关联单据ID */
    private Long relatedBillId;

    /** 关联发货单ID（委外物料流水使用） */
    private Long relatedDeliveryId;

    /** 关联订单号（委外物料流水使用） */
    private String relatedOrderCode;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    /** 产品名称（仅展示用，不映射数据库列） */
    @TableField(exist = false)
    private String productName;

    /** 变动类型中文标签（仅展示用，不映射数据库列） */
    @TableField(exist = false)
    private String changeTypeLabel;

    /** 关联单据类型中文标签（仅展示用，不映射数据库列） */
    @TableField(exist = false)
    private String relatedBillTypeLabel;

    /** 变动后该仓库+产品所有品质的总库存（仅展示用，不映射数据库列） */
    @TableField(exist = false)
    private BigDecimal totalAfterStock;

    private LocalDateTime createTime;
}
