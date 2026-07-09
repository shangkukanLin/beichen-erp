package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_stock_log")
public class InventoryStockLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    private Long materialId;

    private String materialName;

    private String spec;

    private String unit;

    private String changeType;

    private BigDecimal changeQuantity;

    private BigDecimal beforeQuantity;

    private BigDecimal afterQuantity;

    private String relatedBillNo;

    private String relatedBillType;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
