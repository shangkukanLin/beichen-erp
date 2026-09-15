package com.beichen.erp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户首页业务 TAB 可见性配置
 * <p>首页最终可见 = 角色菜单推导 && 本表勾选；无记录视为全部可见（兼容存量账号）。</p>
 */
@Data
@TableName("sys_user_dashboard_tab")
public class UserDashboardTab {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** TAB 标识：dev/outsource/purchase/sale/stock/finance */
    private String tabKey;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long companyId;
}
