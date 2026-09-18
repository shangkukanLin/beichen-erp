package com.beichen.erp.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String phone;

    private String dept;

    private Integer status;
    private Long companyId;

    @TableLogic
    private Integer deleted;

    /**
     * 页面权限模式：ROLE=跟随角色（默认，菜单取自角色授权）/ CUSTOM=自定义（完全以 sys_user_menu 为准）。
     * 见 {@code UserServiceImpl.saveUserMenuPerm} 与 {@code MenuServiceImpl.getMenuTreeByRoleIds}。
     */
    private String menuMode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
