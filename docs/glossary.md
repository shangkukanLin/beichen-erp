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
| **余额实时汇总** | Balance Realtime Aggregation | 供应商应付/客户应收/账户余额均废弃快照字段，由台账流水实时 SUM 计算（详见 ADR-0006/0007） |
| **期初余额** | Opening Balance | 资金账户开户时的初始资金，显式落为一条「期初」流水（flow_type='期初'），保证余额可加和 |
| **核销流水** | Settlement Flow | `finance_settlement` 表记录收付款单 ↔ 应付/应收台账的双向核销关系，反审核据此精确冲销（详见 ADR-0008） |
| **负数应付** | Negative Payable | 超额付款产生的预付/多付，用 `finance_payable.amount = -超额额` 表达，实时汇总时自然抵扣（详见 ADR-0008） |
| **结算单（账单）** | Settlement Bill | `finance_bill` 的定位：生成→审核→核销→结清，作为收付款的可选核销来源（双通道，详见 ADR-0009） |
| **双通道核销** | Dual-channel Settlement | 付款/收款既可直核应付/应收台账，也可通过已审核账单核销，向后兼容 |
| **枚举 code/label** | Enum code vs label | 全项目统一「存 code（英文 name()），显示 label（中文）」；`getCode()` 返回英文 name，`getLabel()` 返回中文，比较业务值时注意确认字段存的是 code 还是 label |
