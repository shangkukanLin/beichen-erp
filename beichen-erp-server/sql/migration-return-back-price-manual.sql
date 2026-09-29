-- 2026-09-29：加工返回单用料明细新增「单价是否人工填写」列
-- 背景：用户口径「送回时按实际用料 FIFO 生成对工厂的赔料应收 —— 登记返回的时候可以填写具体价格，默认 FIFO 可修改」。
--       单价改为在**登记时**快照到 outsource_return_back_item.unit_price（人工填的用它、没填的用登记时点的
--       默认 FIFO 价）；审核时用该快照算**行料款 = 对加工厂的赔料应收**；而行料款对应的**成品成本结转**
--       仍按**审核时点**的 FIFO（人工定价不污染库存成本）。
--       本列标记"该行单价是人工填的还是默认的"（仅留痕/提示，不参与金额计算）。
--
-- 幂等：MySQL 8 的 ALTER TABLE 不支持 ADD COLUMN IF NOT EXISTS，
--       故应用中由 DataInitializer.migrateReturnBackPriceManual() 幂等补列（addColumnIfMissing：捕获"列已存在"忽略）。
--       本文件供人工/DBA 参考或全新环境手动执行。
-- 存量数据：历史行的 unit_price 是**审核时**写入的 FIFO 价（不是人工价）⇒ 统一置 0，语义不变。
ALTER TABLE outsource_return_back_item
    ADD COLUMN price_manual TINYINT DEFAULT 0
    COMMENT '单价是否人工填写: 1=人工定价 / 0=默认(登记时点的FIFO快照)'
    AFTER amount;
