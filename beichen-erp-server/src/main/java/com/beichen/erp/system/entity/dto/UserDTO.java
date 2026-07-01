package com.beichen.erp.system.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 新增/编辑用户入参
 * 新增时 password 必填（在 service 中校验），编辑时不传 password
 */
@Data
public class UserDTO {

    /** 编辑时必填 */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 新增必填 */
    private String password;

    private String phone;

    private String dept;

    @NotNull(message = "状态不能为空")
    private Integer status;

    private List<Long> roleIds;
}
