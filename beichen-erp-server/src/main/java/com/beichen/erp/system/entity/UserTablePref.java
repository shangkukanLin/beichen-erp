package com.beichen.erp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表格列宽偏好（2026-10-09，对应 Flyway V5）。
 *
 * <p><b>口径：跟用户走、不跟公司走</b> —— 同一用户在任何公司下共用同一套列宽，因此本表
 * <b>没有 company_id 列</b>，且必须保持在 {@code CompanyTenantHandler.IGNORE_TABLES} 里：
 * 公司域插件会给未登记的表自动拼 `company_id = ?`，本表没有该列会直接报
 * {@code Unknown column 'company_id' in 'where clause'}（2026-09-18 的 sys_user_menu 踩过同坑）。</p>
 *
 * <p>{@code prefKey} = 路由路径 + '#' + 该路由下表格序号（页面也可用 {@code data-cw-key} 显式指定，便于
 * 表格顺序变化时保持稳定）；{@code prefs} = JSON 字符串「列标识 → 宽度px」，为空表示该表未自定义
 * （此时列宽保持页面默认，与没有本功能时完全一致）。</p>
 */
@Data
@TableName("sys_user_table_pref")
public class UserTablePref {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 表格键：`路由#序号` 或页面 data-cw-key */
    private String prefKey;

    /** JSON：{"列标识": 宽度px}；后端不解析、原样存取（解析口径只在前端一处） */
    private String prefs;

    /** 由数据库默认值/ON UPDATE 维护 */
    private LocalDateTime updateTime;
}
