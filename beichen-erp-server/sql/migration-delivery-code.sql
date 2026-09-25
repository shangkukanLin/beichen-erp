-- =====================================================================================
-- 迁移：加工退货红冲单号 code 列（2026-09-25）
-- 背景：红冲记录（DEFECT_RETURN）原无单号——无单显示"加工退货#<ID>"、有单混用加工单号。
--       新增 code 列：关联加工单 GTH- / 无单 GTW-，建草稿即取号；普通交货/收货记录保持 NULL。
-- 安全性：纯加列（NULL 允许重复，唯一索引不冲突）；存量红冲记录不回填，读侧兜底"加工退货#<ID>"
--         （保证存量反审核流水单号与历史审核一致）。幂等。
-- 执行：mysql -uroot -proot beichen_erp -e "source 本脚本路径"
-- =====================================================================================

SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outsource_order_delivery' AND COLUMN_NAME = 'code');
SET @sql = IF(@has_col = 0,
    'ALTER TABLE outsource_order_delivery ADD COLUMN code VARCHAR(50) DEFAULT NULL COMMENT ''加工退货红冲单号（仅 DEFECT_RETURN 记录填写：关联加工单 GTH- / 无单 GTW-；普通交货为 NULL）'' AFTER source_type',
    'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 唯一索引（NULL 不参与唯一约束；存量无 code 行不冲突）
SET @has_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outsource_order_delivery' AND INDEX_NAME = 'uk_return_code');
SET @sql2 = IF(@has_idx = 0,
    'ALTER TABLE outsource_order_delivery ADD UNIQUE KEY uk_return_code (code)',
    'DO 0');
PREPARE s2 FROM @sql2; EXECUTE s2; DEALLOCATE PREPARE s2;

SELECT COUNT(*) AS code_col_ok FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outsource_order_delivery' AND COLUMN_NAME = 'code';
