package com.beichen.erp.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleName;

    private String roleCode;

    private Integer status;

    private String remark;

    /**
     * F8-22（2026-09-30，本轮修复）：1 = 该角色的菜单被用户在界面上手工调过
     * ⇒ 启动期的「角色→菜单」声明式重放**跳过该角色**（不把用户收掉的菜单再加回来）。
     */
    private Integer customizedMenu;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long companyId;
    private LocalDateTime updateTime;
}
