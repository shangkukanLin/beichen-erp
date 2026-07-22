package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dev_bom")
public class Bom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long supplierId;

    /** 关联物料管理中的物料ID */
    @TableField("outsource_material_id")
    private Long materialId;

    private String materialName;

    private String spec;

    private String unit;

    private BigDecimal quantityPerSet;

    private BigDecimal lossRate;

    private String materialType;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
