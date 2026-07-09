package com.beichen.erp.outsource.entity;

import lombok.Data;

@Data
public class OutsourceMaterialBriefVO {
    private Long id;
    private String materialName;
    private String materialType;
    private String spec;
    private String unit;
}
