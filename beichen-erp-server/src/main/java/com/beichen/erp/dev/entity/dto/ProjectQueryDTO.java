package com.beichen.erp.dev.entity.dto;

import lombok.Data;

@Data
public class ProjectQueryDTO {

    private String name;

    private String status;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
