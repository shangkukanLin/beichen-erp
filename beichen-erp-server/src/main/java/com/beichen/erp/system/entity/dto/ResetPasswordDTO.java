package com.beichen.erp.system.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordDTO {

    @NotNull(message = "用户ID不能为空")
    private Long id;

    @NotBlank(message = "密码不能为空")
    @Size(min = 3, message = "密码长度不能少于3位")
    private String password;
}
