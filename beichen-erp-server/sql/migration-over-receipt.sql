-- 2026-09-29：两张收货单据表新增「是否已确认超收」列
-- 背景：用户口径「加工订单和物料订单都可以超量收货」—— 收货数量超过订单数量时不再硬拒，
--       改为**录入时二次确认**后放行，确认过的单据把本列置 1（审核期不再复核数量上限），
--       未确认（0/NULL）的行审核时仍按原口径拦（F7-66 / F2-2 的第二道防线保留）。
--
-- 幂等：MySQL 8 的 ALTER TABLE 不支持 ADD COLUMN IF NOT EXISTS，
--       故应用中由 DataInitializer.migrateOverReceipt() 幂等补列（addColumnIfMissing：捕获"列已存在"忽略）。
--       本文件供人工/DBA 参考或全新环境手动执行。
ALTER TABLE outsource_order_delivery
    ADD COLUMN over_receipt TINYINT DEFAULT 0
    COMMENT '是否已确认超收: 1=录入时已二次确认(审核期不再复核数量上限) / 0=否'
    AFTER is_reverse;

ALTER TABLE outsource_delivery
    ADD COLUMN over_receipt TINYINT DEFAULT 0
    COMMENT '是否已确认超收: 1=建单时已二次确认(审核期不再复核数量上限) / 0=否'
    AFTER source_order_id;
