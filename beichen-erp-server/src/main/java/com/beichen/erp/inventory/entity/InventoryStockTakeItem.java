package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_stock_take_item")
public class InventoryStockTakeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long takeId;

    private Long materialId;

    private String materialName;

    private String spec;

    private String unit;

    private BigDecimal bookQuantity;

    private BigDecimal actualQuantity;

    private BigDecimal profitLossQuantity;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
