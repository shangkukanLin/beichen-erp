package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 委外物料退货单明细
 */
@Data
@TableName("outsource_material_return_item")
public class OutsourceMaterialReturnItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联退货单ID */
    private Long returnOrderId;

    /** 委外物料ID（关联 outsource_material.id） */
    @TableField("outsource_material_id")
    private Long materialId;

    /** BOM类型ID（关联 dev_bom_type.id） */
    @TableField("bom_type_id")
    private Long bomTypeId;

    /** 单位 */
    private String unit;

    /** 退货数量 */
    private BigDecimal quantity;

    /** 单价（FIFO 默认带出，可手填） */
    private BigDecimal unitPrice;

    /** 小计金额 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;

    /** 公司ID */
    private Long companyId;
}
