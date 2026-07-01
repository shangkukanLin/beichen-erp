package com.beichen.erp.supplier.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupplierDTO {

    private Long id;

    // 编码由后端自动生成，不需要前端传
    private String code;

    @NotBlank(message = "供应商名称不能为空")
    private String name;

    @NotBlank(message = "供应商类型不能为空")
    private String supplierType;

    private String contact;

    private String phone;

    private String address;

    private Integer status;

    private Integer hasDisplay;

    private Integer hasTouch;

    private Long relatedSupplierId;

    private String processType;

    private String brand;

    private String materialType;

    private String remark;
}
