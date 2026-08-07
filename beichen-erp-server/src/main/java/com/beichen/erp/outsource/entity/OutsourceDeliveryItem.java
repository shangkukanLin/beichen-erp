package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("outsource_delivery_item")
public class OutsourceDeliveryItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deliveryId;
    @TableField("outsource_material_id")
    private Long materialId;
    /** BOM类型ID（关联 dev_bom_type.id） */
    @TableField("bom_type_id")
    private Long bomTypeId;
    /** 来源订单明细行ID（关联 outsource_material_order_item.id），用于回写累计收货/退不良数量 */
    private Long itemId;
    private String unit;
    private BigDecimal quantity;
    /** 单价 */
    private BigDecimal unitPrice;
    /** 行金额（数量 × 单价），用于生成应付 */
    private BigDecimal amount;
    private String qualityType;
    private String handleType;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
