-- =====================================================================================
-- V5 (2026-10-09) 用户表格列宽偏好（sys_user_table_pref）
--
-- 【背景】用户要求：所有列表的列宽，用户拖动调整后要记住，下次进来仍是这个宽度（跟用户走）。
--   实现口径（用户 2026-10-09 拍板）：
--     ① 前端**全局兜底** —— 一次覆盖全部 el-table（200+ 处，不逐个页面改）；
--     ② 偏好存**服务端** —— 跨设备、跟用户走；
--     ③ **不跟公司走** —— 同一用户在任何公司下共用同一套列宽。
--
-- 【为什么本表没有 company_id，而且必须登记进 CompanyTenantHandler.IGNORE_TABLES】
--   公司域插件（MyBatis-Plus 租户拦截器）会给**未登记的表**自动拼上 `company_id = ?` 条件；
--   本表按"用户"维度存储、没有 company_id 列 ⇒ 不登记就会报
--   `Unknown column 'company_id' in 'where clause'`（2026-09-18 加 sys_user_menu 时踩过同一个坑，
--   CompanyTenantHandler 的类注释里也写了这条 ⚠️）。本表已加入该忽略集合，后续维护勿删。
--
-- 【为什么用增量 V5 而不是改 V1__base.sql】本项目已上线，V1 已被生产库执行过 —— 改动 V1 会使校验和
--   不匹配 ⇒ Flyway validate 失败、应用起不来。沿用 V2/V3/V4 的增量口径：不动 V1、不重建库，
--   重启时由 Flyway 自动应用本脚本（见 docs/数据库演化约定.md）。
--
-- 【存量数据】无 —— 新表为空；前端读不到偏好时行为与"没有这个功能"完全一致（列宽保持页面默认）。
-- 【幂等】CREATE TABLE IF NOT EXISTS；重复执行无副作用。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS sys_user_table_pref (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '所属用户（跟用户走，不分公司）',
    pref_key    VARCHAR(191) NOT NULL COMMENT '表格键：路由路径#表格序号，或页面显式指定的 data-cw-key',
    prefs       TEXT         NULL COMMENT 'JSON：{"列标识": 宽度px}；空/非法一律按"未设置"处理',
    update_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_pref (user_id, pref_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表格列宽偏好（无 company_id：按用户全局，见 CompanyTenantHandler.IGNORE_TABLES）';
