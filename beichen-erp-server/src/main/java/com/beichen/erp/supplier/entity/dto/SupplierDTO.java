package com.beichen.erp.supplier.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SupplierDTO {

    private Long id;

    // 编码由后端自动生成，不需要前端传
    private String code;

    @NotBlank(message = "供应商名称不能为空")
    private String name;

    /**
     * 供货SKU（2026-09-21 新增）：该供货商所供产品 SKU 的前缀，可空。
     * <p>前端传原值即可，后端统一 trim + 转大写并校验格式（1-24 位字母/数字/短横线）与公司内唯一。</p>
     */
    private String supplySku;

    /** 供应商类型编码列表（product/factory/solution/material） */
    @NotEmpty(message = "供应商类型不能为空")
    private List<String> typeCodes;

    private String contact;

    private String phone;

    private String address;

    private Integer status;

    private Integer hasDisplay;

    private Integer hasTouch;

    private Long relatedSupplierId;

    private Integer creditPeriodMonths;

    private Integer creditPeriod;

    private String remark;
}
