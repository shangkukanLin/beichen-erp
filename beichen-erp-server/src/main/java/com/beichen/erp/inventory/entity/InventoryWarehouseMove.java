package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_warehouse_move")
public class InventoryWarehouseMove {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long fromWarehouseId;

    private Long toWarehouseId;

    private LocalDate moveDate;

    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
