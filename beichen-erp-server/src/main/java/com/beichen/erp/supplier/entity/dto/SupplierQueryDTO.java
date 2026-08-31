package com.beichen.erp.supplier.entity.dto;

import lombok.Data;

@Data
public class SupplierQueryDTO {

    private String supplierType;

    /** 排除的类型编码（如供应商列表"全部"需排除成品商） */
    private String excludeSupplierType;

    private String name;

    private String phone;

    private Integer status;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
