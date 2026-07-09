package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("outsource_material")
public class OutsourceMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectIds;
    private Long warehouseId;
    private String materialName;
    private String materialType;
    private String spec;
    private String supplierName;
    private String supplierIds;
    private String unit;
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
