package com.beichen.erp.system.service;

import java.util.Map;

/**
 * 用户表格列宽偏好（2026-10-09）：所有列表的列宽，用户拖动后记住、下次进来仍是这个宽度。
 *
 * <p>口径：**跟用户走、不跟公司走** —— 同一用户在任何公司下共用同一套列宽。</p>
 */
public interface UserTablePrefService {

    /** 当前用户全部偏好：prefKey → prefs(JSON 字符串，未自定义的表不出现) */
    Map<String, String> listByUser(Long userId);

    /** 写入/覆盖某张表的列宽；prefs 为空/空白 = 删除该表偏好（等价重置） */
    void save(Long userId, String prefKey, String prefs);

    /** 重置单张表 */
    void resetOne(Long userId, String prefKey);

    /** 重置该用户全部表格 */
    void resetAll(Long userId);
}
