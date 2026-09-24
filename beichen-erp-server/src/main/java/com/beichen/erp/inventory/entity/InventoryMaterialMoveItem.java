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

    /** 物料类型名（展示用，非表字段；明细接口回填） */
    @TableField(exist = false)
    private String typeName;

    private BigDecimal quantity;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
