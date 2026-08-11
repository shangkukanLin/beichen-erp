package com.beichen.erp.outsource.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierMaterialDTO {

    private Long supplierId;

    private Long materialId;

    private BigDecimal unitPrice;

    private String remark;
}
