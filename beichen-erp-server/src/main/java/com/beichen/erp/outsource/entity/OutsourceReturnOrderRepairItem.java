package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 维修返回明细（P2-1，2026-09-25）：挂在维修返回记录（outsource_return_order_repair.id）下，
 * 两类行：ALLOC=在厂核销（产品，撤销时按行规格恢复在厂 PRODUCT_REPAIR 行）；
 * MATERIAL=实际用料（物料，可超 BOM，撤销时等量回补委外仓）。
 */
@Data
@TableName("outsource_return_order_repair_item")
public class OutsourceReturnOrderRepairItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 维修返回记录ID（outsource_return_order_repair.id） */
    private Long repairRecordId;

    /** 行类型：ALLOC=在厂核销(产品) / MATERIAL=实际用料(物料) */
    private String itemType;

    /** 核销行-产品主数据ID（item_type=ALLOC） */
    private Long productId;

    /** 核销行-产品名称快照 */
    private String productName;

    /** 核销行-在厂规格（核销时的 PRODUCT_REPAIR 行规格） */
    private String qualityType;

    /** 用料行-委外物料ID（item_type=MATERIAL） */
    private Long materialId;

    /** 用料行-物料名称快照 */
    private String materialName;

    /** 用料行-单位 */
    private String unit;

    /** 数量（核销量/用料量，可超 BOM） */
    private BigDecimal quantity;

    /** 用料行-FIFO 单价快照（登记时） */
    private BigDecimal unitPrice;

    /** 用料行-金额快照（登记时） */
    private BigDecimal amount;

    private Long companyId;

    public static final String TYPE_ALLOC = "ALLOC";
    public static final String TYPE_MATERIAL = "MATERIAL";
}
