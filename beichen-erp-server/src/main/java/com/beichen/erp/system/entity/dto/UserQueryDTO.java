package com.beichen.erp.system.entity.dto;

import lombok.Data;

@Data
public class UserQueryDTO {

    private String username;

    private String phone;

    private Integer status;

    private Long roleId;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
