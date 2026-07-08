package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("dev_bom")
public class Bom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long supplierId;

    private String materialName;

    private String unit;

    private BigDecimal quantityPerSet;

    private BigDecimal lossRate;

    private String materialType;

    private String remark;

    private Long parentId;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private List<Bom> children;
}
