package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("inventory_warehouse")
public class InventoryWarehouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String warehouseName;
    private String warehouseType;
    private String address;
    private String manager;
    private String phone;
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
