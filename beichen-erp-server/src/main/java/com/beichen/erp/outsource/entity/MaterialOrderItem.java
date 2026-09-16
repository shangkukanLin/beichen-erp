package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("outsource_material_order_item")
public class MaterialOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    @TableField("outsource_material_id")
    private Long materialId;
    /** 物料类型ID（关联 material_type.id） */
    @TableField("material_type_id")
    private Long materialTypeId;
    private String unit;
    private BigDecimal orderQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal defectReturnedQty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    /** 逻辑删除标记：0未删除/1已删除 */
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
}
