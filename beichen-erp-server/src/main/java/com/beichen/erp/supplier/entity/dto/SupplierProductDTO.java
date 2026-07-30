package com.beichen.erp.supplier.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierProductDTO {

    private Long productId;

    private BigDecimal unitPrice;

    private String remark;
}
