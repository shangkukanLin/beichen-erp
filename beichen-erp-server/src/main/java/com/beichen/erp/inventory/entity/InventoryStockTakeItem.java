package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存盘点单明细
 * <p>成品记录 productId 有值（按产品+品质），委外物料记录 materialId 有值，两者互斥（同 warehouse_stock）。</p>
 */
@Data
@TableName("inventory_stock_take_item")
public class InventoryStockTakeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long takeId;

    private Long productId;

    /** 产品名称（冗余） */
    private String productName;

    /** SKU（冗余） */
    private String sku;

    private Long materialId;

    /** 物料名称（冗余） */
    private String materialName;

    /**
     * 物料类型名（展示用，**非表字段**；建单时按 material_id 回填）。
     *
     * <p>2026-10-10 用户口径「物料名称前面需要显示物料类型」：盘点明细也不能例外 —— 服务层建单时查一次
     * {@code material_type} 填进来，前端用全局 {@code $mLabel} 显示成 `类型 | 名称` ✓。</p>
     *
     * <p>⚠️ `@TableField(exist = false)`：这是纯展示字段，**不参与建表/迁移**（本仓禁止为展示字段加列 ✓）。</p>
     */
    @TableField(exist = false)
    private String materialTypeName;

    /** 品质等级（成品 A/B/C/DEFECT/PENDING；物料 GOOD/DEFECT） */
    private String qualityType;

    /** 库存形态（MATERIAL=物料；PRODUCT_DEFECT=成品(加工退货)；PRODUCT_REPAIR=成品(维修退货)）—— 盘点按形态不并表 */
    private String stockForm;

    private String unit;

    /** 账面数量（建单时快照） */
    private BigDecimal bookQuantity;

    /** 实盘数量 */
    private BigDecimal actualQuantity;

    /** 差异 = 实盘 − 账面 */
    private BigDecimal diffQuantity;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
}
