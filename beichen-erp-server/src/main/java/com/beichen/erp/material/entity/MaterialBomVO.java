package com.beichen.erp.material.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * BOM 节点视图对象：包含组成关系 + 子物料实时信息 + 下级子节点(用于多级树)
 */
@Data
public class MaterialBomVO {

    private Long id;
    private Long parentMaterialId;
    private Long childMaterialId;
    private BigDecimal quantity;
    private BigDecimal lossRate;
    private String remark;

    /** 子物料实时信息（来自 material 表，子物料修改后自动同步） */
    private String childCode;
    private String childName;
    private String childSpec;
    private String childUnit;
    private String childCategory;

    /** 下级组成（多级展开） */
    private List<MaterialBomVO> children;
}
