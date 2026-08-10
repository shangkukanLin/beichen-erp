package com.beichen.erp.auth.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 登录所属公司，多租户隔离核心入参，前端必传 */
    @NotNull(message = "请选择公司")
    private Long companyId;
}
