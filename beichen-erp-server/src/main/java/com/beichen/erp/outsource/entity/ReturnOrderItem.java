package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("outsource_return_order_item")
public class ReturnOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnOrderId;
    /** 委外物料ID（关联 outsource_material.id），替代冗余 material_name */
    @TableField("outsource_material_id")
    private Long materialId;
    /** 物料类型ID（关联 material_type.id） */
    @TableField("material_type_id")
    private Long materialTypeId;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    private Long companyId;
}
