package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_material")
public class SupplierMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    private Long materialId;

    @TableField(exist = false)
    private String materialName;

    @TableField(exist = false)
    private String spec;

    @TableField(exist = false)
    private String bomTypeName;

    private BigDecimal unitPrice;

    private String remark;

    private Long companyId;
    private LocalDateTime createTime;
}
