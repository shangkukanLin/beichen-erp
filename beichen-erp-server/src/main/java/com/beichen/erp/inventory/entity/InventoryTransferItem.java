package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_transfer_item")
public class InventoryTransferItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long transferId;

    private Long productId;

    private String materialName;

    private String spec;

    private String unit;

    private BigDecimal quantity;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
