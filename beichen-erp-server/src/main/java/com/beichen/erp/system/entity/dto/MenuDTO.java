package com.beichen.erp.system.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MenuDTO {

    private Long id;

    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    private String menuName;

    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    private String routePath;

    private String routeName;

    private String icon;

    private Integer sortOrder;

    private Integer visible;

    private Integer status;
}
