-- =====================================================================================
-- V6 (2026-10-09) 「结算方式（挂账 / 现金）」下沉到采购侧（用户口径：**参考新增销售单做法**）
--
-- 【业务口径（用户 2026-10-09 拍板）】
--   1) CASH=现金：审核时**照常挂应付**，随后自动生成并**立即审核**一张「付款单」把应付核销
--      （= 立刻付钱；与销售侧"现金 = 立刻到账即结算"完全对称）。**不是**"不挂应付"。
--   2) CREDIT=账期：只挂应付，不生成付款单。
--   3) **老单据默认现金**（用户明确口径）⇒ 两列 DEFAULT 'CASH'。
--      ⚠️ 该列只在**审核那一刻**被读取 ⇒ 已审核完的老单据不会被追溯付款、既有应付分文不动；
--         但**新建 / 重新审核**的采购单若未显式选择，即按现金走（= 审核后自动付款）。
--   4) 物料订单（委外物料订单）的结算方式放在**订单**上，收货审核产生应付时从订单读取（透传，不另存一列）。
--
-- 【为什么同时给 finance_payment 补来源列】
--   收款单（finance_receipt）有 source_bill_type / source_id，付款单**没有** ⇒ 无法回答两个必需问题：
--   ① "这张付款单是不是本采购单自动生成的"（幂等：避免重复付款）；
--   ② 反审核采购单时该冲正哪张付款单（只能靠备注匹配 ⇒ 本仓明确反对的脆弱做法）。
--   故按收款侧同口径补两列（纯新增，历史付款单为 NULL，不影响既有行为）。
--
-- 【为什么用增量 V6 而不是改 V1】见 V2/V3/V4/V5 的说明：V1 已被生产库执行过，
--   改动会使校验和不匹配（Flyway validate 失败、应用起不来）⇒ 一律增量。
-- =====================================================================================

ALTER TABLE purchase_order
    ADD COLUMN settle_type       VARCHAR(20)   NOT NULL DEFAULT 'CASH'
        COMMENT 'CREDIT=账期(只挂应付) CASH=现金(审核后自动生成并审核付款单)',
    ADD COLUMN settle_account_id BIGINT        NULL COMMENT '现金结算的付款账户（账期时清空）',
    ADD COLUMN settle_amount     DECIMAL(18,4) NULL COMMENT '本次付款总额（NULL=按应付全额付款）';

ALTER TABLE outsource_material_order
    ADD COLUMN settle_type       VARCHAR(20)   NOT NULL DEFAULT 'CASH'
        COMMENT 'CREDIT=账期(只挂应付) CASH=现金(收货审核后自动生成并审核付款单)',
    ADD COLUMN settle_account_id BIGINT        NULL COMMENT '现金结算的付款账户（账期时清空）',
    ADD COLUMN settle_amount     DECIMAL(18,4) NULL COMMENT '本次付款总额（NULL=按应付全额付款）';

ALTER TABLE finance_payment
    ADD COLUMN source_bill_type VARCHAR(40) NULL COMMENT '来源单据类型（系统自动付款时写，如 PURCHASE_ORDER）',
    ADD COLUMN source_id        BIGINT      NULL COMMENT '来源单据ID（系统自动付款时写；用于幂等与反审核冲正）';

-- 老单据显式落现金（DEFAULT 已覆盖，这里写明口径便于追溯；无 WHERE 条件 = 全表，符合"老单据默认现金"）
UPDATE purchase_order SET settle_type = 'CASH' WHERE settle_type IS NULL;
UPDATE outsource_material_order SET settle_type = 'CASH' WHERE settle_type IS NULL;
