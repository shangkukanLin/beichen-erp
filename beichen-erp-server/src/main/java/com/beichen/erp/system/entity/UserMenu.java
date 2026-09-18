package com.beichen.erp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户级菜单（页面权限）关联：与 {@link RoleMenu} 完全对称。
 * <p>仅在 {@code sys_user.menu_mode = CUSTOM} 时生效（此时可见菜单以本表为准）。</p>
 */
@Data
@TableName("sys_user_menu")
public class UserMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long menuId;
}
