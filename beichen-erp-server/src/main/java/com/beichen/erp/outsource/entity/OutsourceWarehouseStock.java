package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("outsource_warehouse_stock")
public class OutsourceWarehouseStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private Long materialId;
    private BigDecimal quantity;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
