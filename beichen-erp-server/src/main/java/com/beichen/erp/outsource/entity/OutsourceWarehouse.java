package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("outsource_warehouse")
public class OutsourceWarehouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long factoryId;
    private String warehouseName;
    private String address;
    private String contact;
    private String phone;
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
