# ADR-0002: 库存按三元组 (warehouse, product, quality) 管理

- **状态**: 已采纳
- **日期**: 2026-07-31

## 背景

引入品质等级后，同一产品在同一仓库可能存在多个等级库存（如仓库1中 FIND X7 屏幕 A规50、B规20），需要唯一标识每条库存记录。

## 决策

`inventory_warehouse_stock` 唯一键从 `(warehouse_id, product_id)` 改为 `(warehouse_id, product_id, quality_type, company_id)`。

`changeStock()` 方法签名增加 `String qualityType` 参数（最后一个参数），调用方传入 `null` 时兜底为 `"A"`。

所有业务环节（采购入库/销售出库/移仓/其他出入库/采购退货/品质重分类）的明细行均需选择品质等级，审核时传递给 `changeStock`。

## 后果

- 库存操作粒度更细，所有 `changeStock` 调用方（26 处）需传递品质参数
- 库存查询页需聚合展示各等级数量（PIVOT 视图）
- 移仓/其他出入库/退货等原本无品质选择的环节需补全前端表单
