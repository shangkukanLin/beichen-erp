package com.beichen.erp.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_menu")
public class Menu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String menuName;

    private String menuType;

    private String routePath;

    private String routeName;

    private String icon;

    /** F3-3（2026-09-18）：页面级接口权限码（catalog 为 null），见 schema.sql / DataInitializer.initMenuPerms */
    private String perms;

    private Integer sortOrder;

    private Integer visible;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long companyId;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<Menu> children;
}
