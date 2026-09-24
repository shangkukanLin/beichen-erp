-- =====================================================================================
-- 迁移：库存形态 stock_form（2026-09-25 P0-2）
--
-- 目的：委外仓（warehouse_category='OUTSOURCE'，按 factory_id 关联加工厂）目前**只存物料**；
--       业务要求退回的成品也能"存放"在加工厂委外仓，并区分原因：
--         MATERIAL       = 物料（现状，默认值；存量行全部落到这里）
--         PRODUCT_DEFECT = 成品（加工退货）—— 工厂责任
--         PRODUCT_REPAIR = 成品（维修退货）—— 我方责任
--
-- 为什么必须同时改唯一键（关键，别只加一列）：
--   原键 uk_wh_prod_quality_company = (warehouse_id, product_id, quality_type, company_id)
--      与 uk_wh_material_company     = (warehouse_id, material_id, company_id)
--   **不含形态** ⇒ 若「成品（加工退货）」与「成品（维修退货）」落进同一 (仓,产品,规格)，
--   WarehouseStockMapper.updateQuantity / WarehouseStockMapper.updateMaterialQuantity 会**跨形态累加**、
--   WarehouseStockService.selectExist / selectMaterialExist 会**串行读取** ⇒ 料账必错。
--
-- 安全性：存量行全部为 MATERIAL；新唯一键是原键的**超集**（多一个恒定的维度）⇒ 重建不会冲突、无损。
--
-- 执行顺序建议（务必先备份）：
--   1) mysqldump 备份 / SHOW CREATE TABLE 归档 → tools/db-archive/before-stock-form.sql
--   2) 执行本脚本（幂等：重复执行安全）
--   3) 校验：SELECT stock_form, COUNT(*) FROM warehouse_stock GROUP BY stock_form;  -- 应全部 MATERIAL
-- =====================================================================================

-- ---------- 0) 幂等辅助：列不存在才加 ----------
DROP PROCEDURE IF EXISTS __add_col_if_missing;
DELIMITER $$
CREATE PROCEDURE __add_col_if_missing(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_after VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col
    ) THEN
        SET @__sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl,
                            IF(p_after IS NULL OR p_after = '', '', CONCAT(' AFTER `', p_after, '`')));
        PREPARE __stmt FROM @__sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
    END IF;
END$$
DELIMITER ;

-- ---------- 1) 库存表：加形态列 ----------
CALL __add_col_if_missing('warehouse_stock', 'stock_form', 'material_id',
  'stock_form VARCHAR(20) NOT NULL DEFAULT ''MATERIAL'' COMMENT ''库存形态：MATERIAL=物料；PRODUCT_DEFECT=成品(加工退货)；PRODUCT_REPAIR=成品(维修退货)''');

-- ---------- 2) 流水表：加形态列（否则"库存有形态、流水无形态"，冲红/反审核无法还原） ----------
CALL __add_col_if_missing('warehouse_stock_log', 'stock_form', 'material_id',
  'stock_form VARCHAR(20) NOT NULL DEFAULT ''MATERIAL'' COMMENT ''库存形态：同 warehouse_stock.stock_form''');

DROP PROCEDURE IF EXISTS __add_col_if_missing;

-- ---------- 3) 重建唯一键（含形态） ----------
-- 3.1 成品键：warehouse_id, product_id, quality_type, company_id  →  + stock_form
SET @has = (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouse_stock'
              AND INDEX_NAME = 'uk_wh_prod_quality_company' AND COLUMN_NAME = 'company_id');
SET @has_form = (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouse_stock'
                   AND INDEX_NAME = 'uk_wh_prod_quality_company' AND COLUMN_NAME = 'stock_form');
SET @sql = IF(@has = 1 AND @has_form = 0,
  'ALTER TABLE warehouse_stock DROP INDEX uk_wh_prod_quality_company,
     ADD UNIQUE KEY uk_wh_prod_quality_company (warehouse_id, product_id, quality_type, stock_form, company_id)',
  'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3.2 物料键：warehouse_id, material_id, company_id  →  + stock_form
SET @has2 = (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouse_stock'
               AND INDEX_NAME = 'uk_wh_material_company' AND COLUMN_NAME = 'company_id');
SET @has_form2 = (SELECT COUNT(*) FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouse_stock'
                    AND INDEX_NAME = 'uk_wh_material_company' AND COLUMN_NAME = 'stock_form');
SET @sql2 = IF(@has2 = 1 AND @has_form2 = 0,
  'ALTER TABLE warehouse_stock DROP INDEX uk_wh_material_company,
     ADD UNIQUE KEY uk_wh_material_company (warehouse_id, material_id, stock_form, company_id)',
  'DO 0');
PREPARE s2 FROM @sql2; EXECUTE s2; DEALLOCATE PREPARE s2;

-- ---------- 4) 形态筛选用索引（委外仓按形态盘点/查询） ----------
SET @has_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouse_stock' AND INDEX_NAME = 'idx_stock_form');
SET @sql3 = IF(@has_idx = 0, 'ALTER TABLE warehouse_stock ADD INDEX idx_stock_form (stock_form)', 'DO 0');
PREPARE s3 FROM @sql3; EXECUTE s3; DEALLOCATE PREPARE s3;

-- ---------- 5) 校验 ----------
-- 5.1 形态分布（执行后应全部为 MATERIAL，数量等于改造前的行数）
SELECT stock_form, COUNT(*) AS rows_cnt, SUM(quantity) AS qty FROM warehouse_stock GROUP BY stock_form;
-- 5.2 委外仓是否出现成品行（改造前应为 0，本脚本不会产生任何成品行）
SELECT w.warehouse_category, s.stock_form, COUNT(*) AS rows_cnt, SUM(s.product_id IS NOT NULL) AS with_product
FROM warehouse_stock s JOIN warehouse w ON w.id = s.warehouse_id
GROUP BY w.warehouse_category, s.stock_form;
