package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_warehouse_move_item")
public class InventoryWarehouseMoveItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long moveId;

    private Long productId;

    private BigDecimal quantity;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
