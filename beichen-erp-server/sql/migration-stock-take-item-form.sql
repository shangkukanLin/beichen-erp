-- =====================================================================================
-- 迁移：盘点明细表加库存形态 stock_form（2026-09-25，P0-3 收尾「盘点不并表」）
--
-- 目的：inventory_stock_take_item 原无 stock_form ⇒ 仅形态不同的两行库存（同 仓+产品/物料+品质）
--       会在开单快照时生成两条"看起来相同"的明细、对账/落账无法区分形态。
--       加列后：快照按行带形态、currentBook 按形态读账面、applyDiff 把差异写回**同形态**库存行。
--
-- 安全性：纯加列（NOT NULL DEFAULT 'MATERIAL'，存量行自动落 MATERIAL，与现状语义一致）；
--         该表无唯一键/定位键 ⇒ 无索引改造、迁移无损、幂等。
--
-- 执行：mysql -uroot -proot beichen_erp < 本脚本（勿设 $ErrorActionPreference='Stop'）
-- 校验：SELECT stock_form, COUNT(*) FROM inventory_stock_take_item GROUP BY stock_form;  -- 应全部 MATERIAL
-- =====================================================================================

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

CALL __add_col_if_missing('inventory_stock_take_item', 'stock_form', 'quality_type',
  'stock_form VARCHAR(20) NOT NULL DEFAULT ''MATERIAL'' COMMENT ''库存形态：MATERIAL=物料；PRODUCT_DEFECT=成品(加工退货)；PRODUCT_REPAIR=成品(维修退货)''');

DROP PROCEDURE IF EXISTS __add_col_if_missing;

-- 校验
SELECT stock_form, COUNT(*) AS rows_cnt FROM inventory_stock_take_item GROUP BY stock_form;
