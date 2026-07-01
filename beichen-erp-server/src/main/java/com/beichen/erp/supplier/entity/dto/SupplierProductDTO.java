package com.beichen.erp.supplier.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierProductDTO {

    @NotBlank(message = "产品名称不能为空")
    private String productName;

    private String spec;

    private String unit;

    private BigDecimal unitPrice;

    private String remark;
}
