package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 委外物料报损单明细
 * <p>注意：物料库存的唯一键是（仓库+物料），不区分品质，扣减统一按良品 GOOD 记账；
 * qualityType 仅作冗余展示，前端不展示该列。</p>
 */
@Data
@TableName("outsource_stock_loss_item")
public class OutsourceStockLossItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lossId;

    @TableField("material_id")
    private Long materialId;

    /** 物料名称（冗余快照） */
    private String materialName;

    /** 物料类型ID（关联 material_type.id） */
    @TableField("material_type_id")
    private Long materialTypeId;

    /** 物料类型名称（冗余，前端免查） */
    private String materialTypeName;

    /** 品质（QualityType：GOOD 良品 / DEFECT 不良品） */
    private String qualityType;

    private String spec;

    private String unit;

    private BigDecimal quantity;

    /** 报损单价：新增时带出物料最近进价，可手工修改 */
    private BigDecimal unitPrice;

    /** 报损金额 = 数量 × 单价 */
    private BigDecimal amount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
