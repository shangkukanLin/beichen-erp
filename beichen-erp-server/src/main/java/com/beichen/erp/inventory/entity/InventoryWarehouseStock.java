package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_warehouse_stock")
public class InventoryWarehouseStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private String productName;
    private Long productId;
    private BigDecimal quantity;
    private BigDecimal availableQuantity;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
