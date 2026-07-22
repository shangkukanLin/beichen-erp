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
    private String materialName;
    private String materialType;
    private String unit;
    private BigDecimal orderQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal defectReturnedQty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
}
