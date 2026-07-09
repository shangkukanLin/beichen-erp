package com.beichen.erp.material.entity;

import lombok.Data;

/**
 * 物料简要信息（用于"被哪些成品/半成品使用"列表）
 */
@Data
public class MaterialBriefVO {

    private Long id;
    private String code;
    private String name;
    private String spec;
    private String unit;
    private String category;
}
