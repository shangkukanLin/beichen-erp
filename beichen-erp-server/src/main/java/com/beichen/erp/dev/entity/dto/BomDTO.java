package com.beichen.erp.dev.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BomDTO {

    private Long id;

    private Long parentId;

    private Integer sortOrder;

    @NotBlank(message = "物料名称不能为空")
    private String materialName;

    private Long supplierId;

    private String unit;

    private BigDecimal quantityPerSet;

    private BigDecimal lossRate;

    private String materialType;

    private String remark;
}
