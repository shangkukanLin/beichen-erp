package com.beichen.erp.dev.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class ProjectDTO {

    private Long id;

    private String code;

    @NotBlank(message = "项目名称不能为空")
    private String name;

    private String displaySupplierName;

    private String touchSupplierName;

    private String adaptModel;

    private String originalSize;

    private String originalResolution;

    private Long projectLeaderId;

    private Long sampleFactoryId;

    private Long outsourceFactoryId;

    private LocalDate startDate;

    private LocalDate expectedEndDate;

    private LocalDate actualEndDate;

    private String status;

    private String remark;

    /** BOM 物料数据，key=类型名(如"玻璃"), value=物料名 */
    private Map<String, String> bomData;
}
