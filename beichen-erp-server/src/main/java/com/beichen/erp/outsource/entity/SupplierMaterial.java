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

    // F7-125（2026-09-20）：移除展示字段 `spec`（规格已下线，其来源 `OutsourceMaterial.spec` 已删除 ⇒ 拷贝处一并移除）

    @TableField(exist = false)
    private String materialTypeName;

    private BigDecimal unitPrice;

    private String remark;

    private Long companyId;
    private LocalDateTime createTime;
}
