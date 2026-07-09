package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_other_io")
public class InventoryOtherIo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long warehouseId;

    private String ioType;

    private LocalDate ioDate;

    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
