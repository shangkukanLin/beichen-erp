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

    /**
     * 公司归属（0 = 平台模板）。
     *
     * <p>⚠️ <b>2026-10-09 修正</b>：本字段原先误标了 {@code FieldFill.INSERT_UPDATE}
     * —— 那两个注解放反了位置（{@code updateTime} 才是该标 INSERT_UPDATE 的那个）。一个手误两个后果：</p>
     * <ol>
     *   <li>MyBatis-Plus 对"带填充标注的字段"会**无条件**拼进 UPDATE 的 SET 子句（不做 null 判断）
     *       ⇒ 用"只填了几个业务字段的裸实体"更新时，会把 company_id 写成 <b>NULL</b>，
     *       该角色随即在**所有公司视角下都查不到**（历史事故：「跟单员不显示了」，
     *       见 {@code RoleServiceImpl.saveRoleMenus} 的规避写法）；</li>
     *   <li>{@code updateTime} 反而没有任何填充标注 ⇒ 编辑保存后<b>修改时间不刷新</b>（用户报障）。</li>
     * </ol>
     *
     * <p>现与全库其它 44 个实体的写法对齐（Supplier / Customer / User / Company …）：
     * 公司归属只在<b>新增</b>时自动填充。</p>
     */
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
