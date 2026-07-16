package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("outsource_stock_log")
public class OutsourceStockLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private Long materialId;
    private String materialName;
    private String changeType;
    private BigDecimal changeQuantity;
    private BigDecimal beforeQuantity;
    private BigDecimal afterQuantity;
    private String relatedOrderCode;
    private Long relatedDeliveryId;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
