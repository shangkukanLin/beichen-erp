# ADR-0007: 资金账户余额实时算与期初余额落流水

- **状态**: 已采纳
- **日期**: 2026-08-14

## 背景

`finance_account.balance` 是账户余额快照，收付款审核时通过「读-改-写」维护，非原子、并发丢更新。`finance_cashflow.balance` 是从该快照复制来的冗余副本，并非累加得出，二者可能不一致。

改造目标：账户余额改为实时可加和计算。核心难点是「期初余额」从哪来——账户开户时可能已有初始资金，不能凭空消失。

## 决策

**账户余额 = 期初余额 + Σ流水(income − expense)**，期初余额显式落流水：

1. `finance_account` 新增 `opening_balance`（期初余额）字段，开户时填写，之后不可变
2. 开户时自动生成一条流水：`flow_type = '期初'`，`income = opening_balance`（期初进入流水，实时算无需特判）
3. 废弃 `finance_account.balance` 与 `finance_cashflow.balance` 两个快照字段
4. 实时余额 = `a.opening_balance + IFNULL(SUM(cf.income - cf.expense), 0)`，单条 SQL `LEFT JOIN + GROUP BY` 完成

## 后果

- **正面**：余额可加和、可对账；消除并发丢更新；流水是唯一真相源。
- **负面/代价**：流水不再冗余存「变动后余额」，任何需要「某时点余额」的场景都需从头累计（数据量小时可接受，量大后可物化）。
- **不可逆**：删掉 `cashflow.balance` 后无法从单条流水直接读出当时余额。

## 备选方案

- **取最后一笔流水 balance**：看似简单，但 `cashflow.balance` 本就是快照副本，且依赖流水严格有序，补录/反审核/排序会错。被否决。
- **保留 account.balance 改原子 UPDATE**：用 `UPDATE ... SET balance = balance + delta` 原子更新。被否决——仍是快照双轨，与本项目「台账唯一真相源」原则相悖。

## 参考

- 关联 ADR-0006（余额实时汇总总纲）
