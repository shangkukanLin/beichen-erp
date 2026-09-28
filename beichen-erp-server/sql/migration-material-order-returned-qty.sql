-- 2026-09-28：物料订单明细新增「订单退料已退数量」列
-- 背景：用户口径新增「订单退料」类型（关联订单 + 订单未结单）—— 审核扣源仓 + 扣该订单的出货/收料数量，
--       且**永久**扣减（不像"送修中"会回补），反审核加回。
-- 用途：可退 = 已收 − 已退不良 − 送修中 − 订单退料 − 本单之外已审核的退货退款。
--
-- 幂等：MySQL 8 的 ALTER TABLE 不支持 ADD COLUMN IF NOT EXISTS，
--       故应用中由 DataInitializer.migrateMaterialOrderReturnedQty() 幂等补列（检查 information_schema 后再 ALTER）。
--       本文件供人工/DBA 参考或全新环境手动执行。
ALTER TABLE outsource_material_order_item
    ADD COLUMN order_returned_qty DECIMAL(18,0) DEFAULT 0 COMMENT '订单退料已退数量(2026-09-28)'
    AFTER repair_returned_qty;
