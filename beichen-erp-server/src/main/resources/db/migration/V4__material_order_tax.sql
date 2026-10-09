-- =====================================================================================
-- V4 (2026-10-08) 物料订单增加「是否含税 / 税率 / 税额 / 总金额」（用户口径：与加工单一致）
--
-- 【背景】加工单(outsource_order) / 采购单(purchase_order) / 销售单(sale_order) 三张单据都有一套
--   完全同名的四列（见 V1__base.sql 第 214~217 / 1130~1133 / 1304~1307 行）：
--       tax_included TINYINT       DEFAULT 0  '0未含税 1含税'
--       tax_rate     DECIMAL(18,4) DEFAULT 0  '税率'
--       tax_amount   DECIMAL(18,4) DEFAULT 0  '税额(含税总额按税率拆分)'
--       total_amount DECIMAL(18,4) DEFAULT 0  '总金额'
--   物料订单(outsource_material_order) 唯独缺这四列 —— 本脚本对齐口径。
--
-- 【税额算法（与加工单 OutsourceOrderServiceImpl.calcTaxAmount 完全一致）】
--   单价含税口径：tax_included=1 且 tax_rate>0 时，从**含税总额**中拆出税额
--       tax_amount = total × rate/(100+rate)      （rate 为百分数，如 13 表示 13%）
--   否则为 0。税额仅作价税分离展示/统计之用，不改变 total_amount 与明细金额（二者仍为含税口径）。
--
-- 【为什么用增量 V4 而不是改 V1__base.sql】
--   本项目已上线，V1 已被生产库执行过 —— 改动 V1 会使校验和不匹配 ⇒ Flyway validate 失败、应用起不来。
--   沿用 V2/V3 的增量口径：不动 V1、不重建库，重启时由 Flyway 自动应用本脚本。
--
-- 【存量数据】四列均带 DEFAULT，既有订单自动取 0（未含税、税率 0、税额 0）；
--   total_amount 为 0 表示"未回填"，不参与任何账务（应付金额取自收货明细，与此列无关）。
-- =====================================================================================

ALTER TABLE outsource_material_order
    ADD COLUMN tax_included TINYINT       DEFAULT 0 COMMENT '0未含税 1含税',
    ADD COLUMN tax_rate     DECIMAL(18,4) DEFAULT 0 COMMENT '税率',
    ADD COLUMN tax_amount   DECIMAL(18,4) DEFAULT 0 COMMENT '税额(含税总额按税率拆分)',
    ADD COLUMN total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额';
