package com.beichen.erp.system.entity.dto;

import lombok.Data;

/**
 * 表格列宽偏好写入入参（2026-10-09）。
 *
 * <p>{@code prefs} 为空/空白 = 清除该表的偏好（等价于重置）。</p>
 */
@Data
public class TablePrefDTO {

    /** 表格键：`路由#序号` 或页面 data-cw-key，最长 191 */
    private String prefKey;

    /** JSON：{"列标识": 宽度px}；后端只校验形状与大小，不解释语义 */
    private String prefs;
}
