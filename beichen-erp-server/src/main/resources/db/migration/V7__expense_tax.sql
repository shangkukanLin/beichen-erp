-- ==================== V7（2026-10-09）：费用单「是否含税」 ====================
--
-- 需求（用户 2026-10-09）：「**项目物料的研发支出**也需要有是否含税功能」。
-- 项目物料的研发支出 = 研发物料（dev_purchase_item）的「研发支出」登记 ⇒ 落 finance_expense
-- （expense_type=RND / source_bill_type=RD_DEV_MATERIAL，见 RdExpenseServiceImpl）。
--
-- 口径与既有四个单据（purchase_order / outsource_order / sale_order / outsource_material_order，
-- 见 V4__material_order_tax.sql）**完全一致**：
--   · 用户填的金额口径**跟随「含税」开关**：tax_included=1 ⇒ 用户填的是**含税总额**，为 0 时是不含税金额；
--   · 本项目**不做价税分离**：税额仅按 `金额 × 税率/(100+税率)` 拆出用于**展示/统计**，
--     **不改变** amount 本身 ⇒ 审核扣款、资金流水、利润表入账口径全部不变（"加含税"不得悄悄改钱的口径）；
--   · 未含税 ⇒ 税率与税额一律置 0（与前端"关掉开关即清税率"同一口径）。
--
-- ⚠️ 为什么**不加 total_amount**（其它四个单据都有这一列）：
--   其它单据的 `amount` 是明细合计、`total_amount` 是含税总额，两者需要并存；
--   费用单只有**一笔**金额，`amount` 就是金额本体 ⇒ 再加一列必然产生"两个真相源"（存量/新单还可能与
--   迁移口径不一致）。故本表只加 tax_included / tax_rate / tax_amount 三列。
--
-- 默认值 0/0/0 ⇒ **存量费用单语义不变**（未含税、税额 0）✓；其它费用类型（办公费/房租/…）不受影响 ✓。

ALTER TABLE finance_expense
    ADD COLUMN tax_included TINYINT DEFAULT 0 COMMENT '是否含税: 0未含税 1含税（2026-10-09 V7；口径与 V4 的四个单据一致）' AFTER amount,
    ADD COLUMN tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率(%)；tax_included=0 时恒为 0' AFTER tax_included,
    ADD COLUMN tax_amount DECIMAL(18,4) DEFAULT 0 COMMENT '税额 = 金额×税率/(100+税率)（含税总额拆税）；仅展示/统计，不改 amount' AFTER tax_rate;
