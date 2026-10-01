-- =====================================================================================
-- V2 (2026-09-30) 销售单「现金收款」支持多账户分款 + 本次收款总额
--
-- 背景：销售单原来只有一个 settle_account_id（单账户），用户要求与收款单/付款单一致 ——
--       现金收款可拆到多个账户，并按金额分摊（前端 components/AccountSplitTable.vue）。
--
-- 为什么用增量脚本而不是改 V1__base.sql：
--       按 docs/数据库演化约定.md，未上线期直接改 V1 会让**已执行过 V1 的库**校验和不匹配
--       （Flyway validate 失败）⇒ 必须 DROP DATABASE 重建。本次用 V2 增量：不动 V1、不重建、
--       现有数据（账户/客户/系统表等）全部保留，Flyway 重启时自动执行本脚本。
--
-- 表结构照抄 finance_receipt_account（收款单分款明细），保持两条链路同构：
--       finance_receipt.amount  = 分款合计        ⇔ sale_order.settle_amount = 分款合计
--       finance_receipt.account_id = 首行快照     ⇔ sale_order.settle_account_id = 首行快照
-- =====================================================================================

CREATE TABLE IF NOT EXISTS sale_order_settle_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分款明细ID',
    order_id BIGINT NOT NULL COMMENT '销售单ID',
    account_id BIGINT NOT NULL COMMENT '收款账户ID(finance_account.id)',
    account_name VARCHAR(100) COMMENT '账户名称(冗余留痕：账户改名后单据仍显示当时名称)',
    amount DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '该账户本次收款金额(>0)',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_sosa_order (order_id),
    INDEX idx_sosa_account (account_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售单分款明细表（一单多账户，现金结算）';

-- 本次收款总额：默认等于应收总额；小于应收 = 部分收款（差额由后端挂预收台账）。
-- 账期单为空。settle_account_id 继续保留为"首行快照"，兼容列表列与既有读法。
ALTER TABLE sale_order
    ADD COLUMN settle_amount DECIMAL(18,4) DEFAULT NULL COMMENT '本次收款总额（现金结算；为空表示未填写）';
