# ADR-0006: 财务余额统一实时汇总，废弃全部余额快照字段

- **状态**: 已采纳
- **日期**: 2026-08-14

## 背景

财务模块历史上存在三处「余额快照 + 业务写入」的冗余维护，均与台账流水双轨并存，易漂移：

1. `supplier.payable_balance`（供应商应付余额快照）
2. `customer.receivable_balance` / `customer.prepaid_balance`（客户应收/预收余额快照，其中 prepaid_balance 为从未被业务写入的死字段）
3. `finance_account.balance`（资金账户余额快照）+ `finance_cashflow.balance`（流水冗余余额副本）

这些快照字段的维护分散在 14+ 处业务代码中，任一处漏改即产生永久性数字漂移，且无对账机制。客户/账户侧采用「读-改-写」非原子更新，存在并发丢更新风险。

## 决策

**废弃全部余额快照字段，余额统一由台账流水实时 SUM 计算**：

- 供应商应付余额 = `SUM(finance_payable.unpaid_amount) WHERE status != '已结清'`，列表页 `LEFT JOIN + GROUP BY` 批量汇总
- 客户应收余额 = `SUM(finance_receivable.unpaid_amount) WHERE status != '已结清'`，同上
- 资金账户余额 = `opening_balance + SUM(cashflow.income) − SUM(cashflow.expense)`

账户期初余额显式落为一条「期初」流水（或独立 `opening_balance` 字段），不藏在快照字段中，保证余额可加和。

物理删除字段：`supplier.payable_balance`、`customer.receivable_balance`、`customer.prepaid_balance`、`finance_account.balance`、`finance_cashflow.balance`。

## 后果

- **正面**：消除双轨漂移隐患；消除非原子读改写的并发丢更新；维护点从 14+ 处收敛为「查询时一次汇总」。
- **负面/代价**：列表查询从「读字段」变为「JOIN + GROUP BY」，依赖索引（现有 `idx_supplier_id`/`idx_customer_id`/`idx_account_id` 已足够）；余额值由台账重算，历史快照值被丢弃（项目未上线，无历史数据包袱）。
- **不可逆**：物理删列后无法恢复快照，余额口径永久改为台账实时汇总。

## 备选方案

- **快照同步（保留字段）**：老牌财务软件做法，记账时同步加减余额。被否决——维护点分散、易漏改、非原子，正是本次要解决的痛点。
- **物化视图/定时预聚合**：SAP/用友级做法，流水上亿时用。被否决——当前数据量无需，属于过度设计，待数据量增长后再渐进升级。

## 参考

- 供应商/客户余额改造已落地（见 2026-08-14 提交）
- 账户余额实时算与期初余额方案见 ADR-0007
