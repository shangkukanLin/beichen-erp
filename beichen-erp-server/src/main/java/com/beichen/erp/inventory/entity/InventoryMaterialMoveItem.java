package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料移仓单明细表（2026-09-24 新增）。
 *
 * <p>明细对象是**委外物料**（{@code outsource_material}），不做品质分级 —— 物料库存
 * （{@code warehouse_stock} 的物料维度）本身没有品质列，与成品移仓的 A/B/C/DEFECT 不同。</p>
 */
@Data
@TableName("inventory_material_move_item")
public class InventoryMaterialMoveItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long moveId;

    private Long materialId;

    /** 物料名称（展示用，非表字段；明细接口回填） */
    @TableField(exist = false)
    private String materialName;

    /** 单位（展示用，非表字段；明细接口回填） */
    @TableField(exist = false)
    private String unit;

    /**
     * 物料类型名（展示用，**非表字段**；明细接口按 material_id 回填）。
     *
     * <p>2026-10-10：原字段名是 {@code typeName}，全仓**无任何读写**（死字段 ✗，值永远是 null ⇒ 前端
     * 一直显示不出类型 ✓），且与全局约定（{@code materialLabel} 读 {@code materialTypeName}）不一致。
     * 现改名并真正回填，前端统一走 `$mLabel(row)` ✓。</p>
     */
    @TableField(exist = false)
    private String materialTypeName;

    private BigDecimal quantity;

    /**
     * 品质分级 A/B/C/DEFECT（2026-09-24 用户要求：与成品移仓单同口径，明细分品质）。
     *
     * <p><b>⚠️ 只落在单据上，不参与库存</b>：物料库存（{@code warehouse_stock} 的物料维度）本身没有
     * 品质列，物料侧库存一贯"不区分品质、按良品扣减"（见 413 物料报损的口径）。因此审核时
     * {@code changeMaterialStock} 不带品质参数 —— 本字段用于"这单搬的是哪一档物料"的业务留痕与展示，
     * 不等于按品质分账。若将来要让物料库存也分品质，是数据模型级变更，需另行评估。</p>
     */
    private String qualityType;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
