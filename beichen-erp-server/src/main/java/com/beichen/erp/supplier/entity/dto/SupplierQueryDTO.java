package com.beichen.erp.supplier.entity.dto;

import lombok.Data;

@Data
public class SupplierQueryDTO {

    private String supplierType;

    private String name;

    private String phone;

    private Integer status;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
