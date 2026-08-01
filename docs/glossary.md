# 北辰 ERP 术语表

| 术语 | 英文 | 定义 |
|------|------|------|
| **品质等级** | quality_type | 产品质检分类：A 规（优等品）、B 规（合格品）、C 规（次品）、不良（缺陷品）。DB 存储枚举 name（A/B/C/DEFECT），前端通过接口动态获取中文标签 |
| **品质重分类** | Product Reclassify | 对已有库存进行等级调整的独立单据。审核后从原品质扣减库存、向目标品质增加库存，提供完整审计追踪 |
| **三元组** | Triple Key | warehouse_id + product_id + quality_type 唯一确定一条库存记录，是 `inventory_warehouse_stock` 的唯一约束 |
| **通用型号** | general_model | 产品适用多款机型的通用型号标识（如"AMOLED-7"适用多款 7 寸设备） |
| **changeStock** | changeStock | 统一库存变更入口方法，所有入库/出库/移仓/重分类均通过此方法操作。签名包含 warehouseId/productId/qualityType/quantity/changeType 等参数 |
| **成品仓** | Finished Goods Warehouse | 存放成品（product 表管理）的仓库，区别于辅料仓 |
| **PIVOT 视图** | Pivot View | 库存查询页的聚合展示方式：按产品+仓库维度，一行展示 A规/B规/C规/不良四列数量 |
