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

    /**
     * 库存形态常量（2026-09-25 P0-2）：委外仓既能放物料，也能放退回的成品（区分责任方）。
     * <p>与 {@link #qualityType}（规格 A/B/C/DEFECT/GOOD）**正交**；已纳入两条唯一键
     * （uk_wh_prod_quality_company / uk_wh_material_company）⇒ 不同形态**不会**互相累加。</p>
     */
    public static final String FORM_MATERIAL = "MATERIAL";
    /** 成品（加工退货）：工厂交货后发现不良退回工厂 —— **工厂责任**（返回单要生成赔料应收） */
    public static final String FORM_PRODUCT_DEFECT = "PRODUCT_DEFECT";
    /** 成品（维修退货）：客户退回的售后品推给工厂维修 —— **我方责任**（不赔料；工厂可收维修费） */
    public static final String FORM_PRODUCT_REPAIR = "PRODUCT_REPAIR";

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
     * 库存形态（2026-09-25 P0-2 新增，DB 列已迁移）—— 委外仓既能放物料，也能放退回的成品：
     * <ul>
     *   <li>{@code MATERIAL}（默认）—— 物料（现状；存量行全部落在这一档）</li>
     *   <li>{@code PRODUCT_DEFECT} —— 成品（加工退货）：工厂交货后发现不良退回工厂，**工厂责任**</li>
     *   <li>{@code PRODUCT_REPAIR} —— 成品（维修退货）：客户退回的售后品推给工厂维修，**我方责任**</li>
     * </ul>
     * 与 {@link #qualityType} 是**正交**的两件事：qualityType 是规格(A/B/C/DEFECT/GOOD)，
     * 本列是"这条库存到底算料还是算某类退回成品"。已纳入两条唯一键
     * （uk_wh_prod_quality_company / uk_wh_material_company）⇒ 不同形态**不会**互相累加。
     * <p>⚠️ 默认值只为"存量与既有调用不受影响"：任何新增的成品形态写入都必须**显式**赋值，
     * 否则会被记成 MATERIAL（参见 .codebuddy/memory/stock-form-plan.md 第 6 节风险清单）。</p>
     */
    private String stockForm = "MATERIAL";

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
