package com.beichen.erp.outsource.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OutsourceMaterialBomVO {
    private Long id;
    private Long parentMaterialId;
    private Long childMaterialId;
    private BigDecimal quantity;
    private BigDecimal lossRate;
    private String remark;
    // 关联子物料的实时信息（联表查询，子物料修改后自动同步）
    private String childName;
    private String childType;
    private String childSpec;
    private String childUnit;
    private String childSupplierName;
    // 多级树
    private List<OutsourceMaterialBomVO> children;
}
