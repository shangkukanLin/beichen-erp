package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 加工返回单用料明细（P1-2，2026-09-25）
 * <p>数量允许<b>超过 BOM 标准用量</b>（按实际耗用记账）；审核时逐行按 FIFO 计料款并快照单价。</p>
 */
@Data
@TableName("outsource_return_back_item")
public class OutsourceReturnBackItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 返回单ID（outsource_return_back.id） */
    private Long returnBackId;

    /** 委外物料ID（outsource_material.id） */
    private Long materialId;

    /** 物料名称快照 */
    private String materialName;

    /** 单位 */
    private String unit;

    /** 实际用料数量（可超 BOM 标准用量） */
    private BigDecimal quantity;

    /** FIFO 结转单价快照（审核时） */
    private BigDecimal unitPrice;

    /** 行料款 = 单价 × 数量（审核时） */
    private BigDecimal amount;

    private Long companyId;
}
