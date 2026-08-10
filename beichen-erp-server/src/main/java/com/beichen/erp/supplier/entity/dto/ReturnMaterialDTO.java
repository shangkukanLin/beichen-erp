package com.beichen.erp.supplier.entity.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 供应商一键退料入参
 */
@Data
public class ReturnMaterialDTO {

    /** 退回目标仓库ID */
    @NotNull(message = "请选择退回目标仓库")
    private Long toWarehouseId;
}
