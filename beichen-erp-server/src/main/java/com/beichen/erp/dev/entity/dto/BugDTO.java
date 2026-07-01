package com.beichen.erp.dev.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BugDTO {

    private Long id;

    @NotBlank(message = "Bug标题不能为空")
    private String title;

    private String severity;

    private String bugType;

    private String status;

    private String description;

    private Long assignedTo;
}
