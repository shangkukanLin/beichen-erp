package com.beichen.erp.material.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料BOM组成关系：父物料(parent) 由 子物料(child) 组成
 * 仅存储子物料ID，展示时联表取最新物料信息，子物料修改后BOM自动同步
 */
@Data
@TableName("material_bom")
public class MaterialBom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentMaterialId;

    private Long childMaterialId;

    /** 单台/单套用量 */
    private BigDecimal quantity;

    /** 损耗率 */
    private BigDecimal lossRate;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
