package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料维修返回实际用料明细（2026-09-25 物料形态化）
 * <p>挂在维修返回记录（outsource_material_return_repair.id）下：供应商维修主物料时实际耗用的
 * 子物料补料行（可超 BOM、允许扣负），登记时按 FIFO 计价快照，撤销时等量回补供应商委外仓。</p>
 */
@Data
@TableName("outsource_material_return_repair_material")
public class OutsourceMaterialReturnRepairMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 维修返回记录ID（outsource_material_return_repair.id） */
    private Long repairRecordId;

    /** 子物料ID（outsource_material.id，实际耗用的补料） */
    private Long materialId;

    /** 子物料名称快照 */
    private String materialName;

    /** 单位 */
    private String unit;

    /** 实际耗用数量（可超 BOM 标准用量） */
    private BigDecimal quantity;

    /** FIFO 单价快照（登记时） */
    private BigDecimal unitPrice;

    /** 金额 = 单价 × 数量快照（登记时） */
    private BigDecimal amount;

    private Long companyId;
}
