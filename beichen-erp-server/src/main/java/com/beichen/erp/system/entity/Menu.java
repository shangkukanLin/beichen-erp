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

    /**
     * F8-21（2026-09-30 设置模块批 E）：1 = 用户通过「菜单管理」改过（或自己新建的）菜单
     * ⇒ {@code DataInitializer.syncMenus()} 启动同步**不再覆盖**该行的展示字段
     * （menu_name / parent_id / sort_order / visible / status）。
     */
    private Integer customized;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 公司归属。⚠️ <b>2026-10-09 修正</b>：本字段原先误标 {@code FieldFill.INSERT_UPDATE}
     * —— 注解放反了位置（{@code updateTime} 才是该标 INSERT_UPDATE 的那个），与 {@link Role} 同一手误。
     * 后果：① 该列被**无条件**拼进 UPDATE 的 SET ⇒ 裸实体会把菜单公司归属写成 NULL，菜单在公司视角下"隐身"；
     * ② {@code updateTime} 无填充标注 ⇒ 编辑保存后修改时间不刷新。
     * 现与全库其它实体对齐：公司归属只在**新增**时填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<Menu> children;
}
