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
    private Long materialId;
    private String materialName;
    private String materialType;
    private String unit;
    private BigDecimal quantity;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
