# ADR-0003: product_quality 快照表废弃

- **状态**: 已采纳
- **日期**: 2026-07-31

## 背景

`product_quality` 表初始设计用于产品级别的等级库存快照（每个产品 4 行：A/B/C/DEFECT 各一行），旨在产品管理页面展示等级库存概览。但它存在根本缺陷：

1. **不区分仓库**：同一产品在不同仓库的等级分布不同，快照只能做全仓库汇总，不准确
2. **不同步**：库存变动后 product_quality 不会自动更新，需要额外逻辑维护一致性
3. **冗余**：`inventory_warehouse_stock` 已是权威数据源，product_quality 是派生的冗余数据

## 决策

废弃 `product_quality` 表及相关代码（Entity/Mapper/Service 逻辑）。产品管理页仅展示基础信息（编码/名称/分类/规格/通用型号/单位），等级库存统一从 `inventory_warehouse_stock` 实时查询。

## 后果

- 删除 `product_quality` 表 DDL、Entity、Mapper、Service 中的 qualities 保存/查询逻辑
- 产品管理页移除等级库存表格，产品新增时不再初始化 4 行等级记录
- 库存查询页改为后端 GROUP BY + PIVOT 聚合视图，按产品+仓库维度展示各等级数量
