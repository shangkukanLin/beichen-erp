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

    /** SKU（展示用，非表字段；流水接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 物料ID（委外仓物料流水） */
    private Long materialId;

    /** 物料名称（委外仓流水冗余字段，用于展示） */
    private String materialName;

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

    /** 详情跳转目标ID（仅展示用，不映射数据库列）：默认=relatedBillId；销售出库单映射为其关联销售单ID */
    @TableField(exist = false)
    private Long relatedBillDetailId;

    /** 变动后该仓库+产品所有品质的总库存（仅展示用，不映射数据库列） */
    @TableField(exist = false)
    private BigDecimal totalAfterStock;

    private LocalDateTime createTime;
}
