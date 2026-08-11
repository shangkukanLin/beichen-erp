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

    private Long productId;

    private String qualityType;

    private String changeType;

    private BigDecimal changeQuantity;

    private BigDecimal beforeQuantity;

    private BigDecimal afterQuantity;

    private String relatedBillNo;

    private String relatedBillType;

    private Long relatedBillId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    /** 产品名称（仅展示用，不映射数据库列） */
    @TableField(exist = false)
    private String productName;

    /** 变动类型中文标签（仅展示用，不映射数据库列） */
    @TableField(exist = false)
    private String changeTypeLabel;

    private LocalDateTime createTime;
}
