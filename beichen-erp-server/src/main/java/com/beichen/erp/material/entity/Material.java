package com.beichen.erp.material.entity;

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
@TableName("material")
public class Material {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private Long brandId;

    private String category;

    private String spec;

    private String unit;

    private BigDecimal safetyStock;

    private BigDecimal currentStock;

    private String status;

    /** 关联的研发项目ID（来源为研发项目时填充） */
    private Long projectId;

    private String remark;

    /** 子物料组成（仅用于新增/编辑时的入参，不入物料表） */
    @TableField(exist = false)
    private List<MaterialBom> bomChildren;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
