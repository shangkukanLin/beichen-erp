---
name: 北辰ERP进销存与财务模块补全
overview: 在现有代码基础上，参考管家婆进销存，一次性补全进销存（客户/采购订单/采购入库/成品库存查询+出库+盘点+调拨/销售订单/销售出库）与财务（应收/应付台账+收付款单+资金流水+按账期账单）全部模块。沿用现有多租户、自动编码、状态字段驱动、DECIMAL 金额精度、单据+明细子表等约定；单据审核自动联动库存与往来账（管家婆式自动流转）。
design:
  styleKeywords:
    - 企业级中后台
    - Element Plus
    - 表格驱动
    - 深色侧边栏
    - 简洁高效
  fontSystem:
    fontFamily: PingFang SC, Microsoft YaHei, sans-serif
    heading:
      size: 18px
      weight: 600
    subheading:
      size: 15px
      weight: 500
    body:
      size: 14px
      weight: 400
  colorSystem:
    primary:
      - "#409EFF"
      - "#304156"
    background:
      - "#F0F2F5"
      - "#FFFFFF"
      - "#304156"
    text:
      - "#303133"
      - "#bfcbd9"
    functional:
      - "#67C23A"
      - "#F56C6C"
      - "#E6A23C"
todos:
  - id: db-schema
    content: 在 schema.sql 新增 customer/purchase/sale/inventory扩展/finance 全部表（含 company_id、DECIMAL(18,4)、唯一键与索引）
    status: completed
  - id: customer-module
    content: 实现 customer 全栈（后端实体/服务/控制器 + 前端客户档案页 + api 定义）
    status: completed
    dependencies:
      - db-schema
  - id: purchase-module
    content: 实现 purchase 全栈（采购订单+入库+审核联动加库存与生成应付 + 前端页 + api）
    status: completed
    dependencies:
      - db-schema
  - id: sale-module
    content: 实现 sale 全栈（销售订单+出库+审核联动减库存与生成应收+可用量校验 + 前端页 + api）
    status: completed
    dependencies:
      - db-schema
  - id: inventory-ext
    content: 扩展 inventory 全栈（库存查询/盘点/调拨/其他出入库/库存流水 + 改造 stockService 按 material_id + 前端页 + api）
    status: completed
    dependencies:
      - db-schema
  - id: finance-ledger
    content: 实现 finance 台账与流水全栈（资金账户/应收/应付台账/资金流水展示 + 前端页 + api）
    status: completed
    dependencies:
      - db-schema
  - id: finance-pay
    content: 实现 finance 收付款全栈（收款/付款单审核自动核销往来账+写资金流水+更新账户余额 + 前端页 + api）
    status: completed
    dependencies:
      - finance-ledger
  - id: finance-bill
    content: 实现 finance 账单生成全栈（按账期/客户/供应商汇总未结清应收应付生成账单 + 前端页 + api）
    status: completed
    dependencies:
      - finance-ledger
  - id: frontend-wire
    content: 修复路由菜单（inventory/material 指向、coming-soon 替换为真实页）、统一列表/表单/详情组件并联调校验联动一致性与金额精度
    status: completed
    dependencies:
      - customer-module
      - purchase-module
      - sale-module
      - inventory-ext
      - finance-ledger
      - finance-pay
      - finance-bill
---

## 用户需求

承接现有北辰 ERP 代码，参照管家婆进销存，一次性补全未实现的进销存与财务模块：客户管理、采购（订单/入库）、销售（订单/出库）、库存（查询/盘点/调拨/其他出入库/流水）、财务（应收/应付台账、收付款、资金流水、账期账单）。库存账沿用 inventory_warehouse 体系并与委外仓库并行；单据审核后自动联动库存与往来账（管家婆式自动流转）；财务做到普及版级别（不含凭证与财务报表）。

## 产品概述

一套面向屏幕行业的企业进销存 + 财务一体化后台。在现有登录、RBAC、物料、供应商、委外、研发模块之上，打通"客户→采购→入库→销售→出库→收付款→账单"业务闭环，并提供实时库存与资金视图。

## 核心功能

- 客户档案：客户编码、联系人、账期、信用额度、应收/预收余额
- 采购：采购订单、采购入库（审核自动加库存 + 生成应付台账）
- 销售：销售订单、销售出库（审核自动减库存 + 生成应收台账，校验可用量）
- 库存：成品库存查询、盘点、调拨、其他出入库、全量库存流水追溯
- 财务：应收/应付台账、收款/付款单（自动核销往来账 + 写资金流水 + 更新账户余额）、资金流水、按账期汇总生成应收/应付账单

## 技术栈

- 后端：Spring Boot 3 + Java 21 + MyBatis-Plus 3.5 + Sa-Token，沿用现有 R&lt;T&gt;、PageParam、BusinessException、多租户（company_id）、自动编码 genCode、状态字段驱动等约定
- 前端：Vue 3 + TypeScript + Vite + Pinia + Element Plus + Axios（沿用现有）
- 数据库：MySQL 8，金额一律 DECIMAL(18,4)，新增表仅写入 schema.sql

## 实现方式

采用与现有模块（material/outsource/supplier）完全一致的"实体 → Mapper → Service(impl) → Controller"分层，主表 + 明细子表结构。核心策略是**单据审核即触发联动事务**：采购入库/销售出库审核在一个 @Transactional 内完成"库存账变更 + 库存流水写入 + 应收应付台账生成"；收付款单审核完成"往来账核销 + 资金流水 + 账户余额变更"；账单生成由定时或手动按客户/供应商 + 账期汇总未结清台账。这样保证账实一致、可追溯，且沿用状态字段而非 Flowable。

## 关键决策

- **库存维度统一为 material_id**：现有 inventory_warehouse_stock 按 product_name 存、且无 company_id。重构为 (warehouse_id, material_id, company_id) 唯一键 + quantity + available_quantity，改造现有 stockIn 累加逻辑，杜绝同名物料错乱、支撑准确联动与可用量校验。委外仓库体系保持不变、互不干预。
- **余额实时计算与冗余并存**：应收/应付台账逐单据记录 amount/paid_amount/unpaid_amount/status，客户应收余额由台账汇总，同时在 customer 冗余 receivable_balance 便于列表展示（联动时同步更新）。
- **金额精度**：所有金额/数量字段 DECIMAL(18,4)，Service 层用 BigDecimal 运算，禁止浮点。
- **不改动稳定模块**：outsource/material/supplier/system/auth/dev 业务逻辑保持不变，仅新增模块与补齐 inventory 对外接口。

## 实现要点

- 自动编码：采购单 CG-日期+序号、采购入库 RK-、销售单 XS-、销售出库 CK-、盘点 PD-、调拨 DB-、其他出入库 QT-、收款 SK-、付款 FK-、账单 ZD-，复用 OutsourceOrderServiceImpl.genCode 思路（日期 + 当日序号）。
- 多租户：新实体均含 company_id；MybatisPlusConfig 多租户插件需把新表纳入过滤（参考 CompanyTenantHandler）。
- 库存可用量：销售出库/调拨出库审核时校验 available_quantity，不足则抛 BusinessException。
- 库存流水 inventory_stock_log 记录每次变更的前后数量、类型、关联单据号，支撑追溯与对账。
- 菜单对齐：新接口路径与 config/DataInitializer 已建菜单 routePath 对齐（/inventory/customer、/inventory/purchase、/inventory/inbound、/inventory/stock、/inventory/sale、/inventory/outbound、/finance/*），前端路由替换 coming-soon 指向真实页面。

## 架构设计

```mermaid
flowchart TD
  C[客户档案] -->|销售订单| SO[销售订单]
  SUP[供应商] -->|采购订单| PO[采购订单]
  PO -->|生成| PI[采购入库]
  PI -->|审核@Transactional| W[库存+]
  PI -->|审核| AP[应付台账]
  SO -->|生成| SOUT[销售出库]
  SOUT -->|审核 校验可用量| W2[库存-]
  SOUT -->|审核| AR[应收台账]
  AR -->|收款单 核销| CF[资金流水+账户余额+]
  AP -->|付款单 核销| CF2[资金流水-账户余额-]
  AR -->|账期汇总| BILL[应收账单]
  AP -->|账期汇总| BILL2[应付账单]
  W & W2 --> LOG[库存流水]
```

## 目录结构

### 后端（全部为新增，位于 com/beichen/erp 下）

```
beichen-erp-server/src/main/java/com/beichen/erp/
├── customer/                     # [NEW] 客户管理
│   ├── entity/Customer.java      # 客户主数据（code,name,contact,credit_period,credit_limit,receivable_balance,prepaid_balance,status）
│   ├── entity/dto/CustomerDTO.java
│   ├── entity/vo/CustomerVO.java
│   ├── mapper/CustomerMapper.java
│   ├── service/CustomerService.java + impl/CustomerServiceImpl.java  # 分页/编码/校验/余额更新
│   └── controller/CustomerController.java  # /api/inventory/customer
├── purchase/                     # [NEW] 采购订单 + 入库
│   ├── entity/PurchaseOrder.java, PurchaseOrderItem.java
│   ├── entity/PurchaseInbound.java, PurchaseInboundItem.java
│   ├── mapper/*Mapper.java
│   ├── service/PurchaseService.java + impl  # genCode、审核入库(加库存+流水+生成应付)、作废回滚
│   └── controller/PurchaseOrderController.java, PurchaseInboundController.java  # /api/inventory/purchase, /api/inventory/inbound
├── sale/                         # [NEW] 销售订单 + 出库
│   ├── entity/SaleOrder.java, SaleOrderItem.java
│   ├── entity/SaleOutbound.java, SaleOutboundItem.java
│   ├── service/SaleService.java + impl  # genCode、审核出库(校验可用量,减库存+流水+生成应收)、作废回滚
│   └── controller/SaleOrderController.java, SaleOutboundController.java # /api/inventory/sale, /api/inventory/outbound
├── inventory/                    # [MODIFY/NEW] 扩展库存
│   ├── entity/InventoryStockLog.java        # [NEW] 库存流水
│   ├── entity/InventoryStockTake.java, InventoryStockTakeItem.java  # [NEW] 盘点
│   ├── entity/InventoryTransfer.java, InventoryTransferItem.java    # [NEW] 调拨
│   ├── entity/InventoryOtherIo.java, InventoryOtherIoItem.java      # [NEW] 其他出入库
│   ├── service/InventoryWarehouseStockService.java  # [MODIFY] 改为按 material_id 累加/扣减/盘点/调拨，并写流水
│   ├── service/StockTakeService.java, TransferService.java, OtherIoService.java  # [NEW]
│   └── controller/StockController.java, StockTakeController.java, TransferController.java, OtherIoController.java  # [NEW] /api/inventory/stock(/take/transfer/other)
└── finance/                      # [NEW] 财务
    ├── entity/FinanceAccount.java            # 资金账户(现金/银行)
    ├── entity/FinanceReceivable.java         # 应收台账
    ├── entity/FinancePayable.java            # 应付台账
    ├── entity/FinanceReceipt.java, FinanceReceiptItem.java   # 收款单+核销明细
    ├── entity/FinancePayment.java, FinancePaymentItem.java  # 付款单+核销明细
    ├── entity/FinanceCashflow.java           # 资金流水
    ├── entity/FinanceBill.java, FinanceBillItem.java         # 应收/应付账单+明细
    ├── service/FinanceService.java + impl    # 台账生成、收付款核销、账户余额、账单汇总
    └── controller/FinanceReceivableController.java, FinancePayableController.java, FinanceReceiptController.java, FinancePaymentController.java, FinanceCashflowController.java, FinanceBillController.java  # /api/finance/*
beichen-erp-server/src/main/resources/schema.sql  # [MODIFY] 新增上述全部表的 CREATE TABLE IF NOT EXISTS（含 company_id、DECIMAL(18,4)、唯一键、索引）
```

### 前端（全部为新增，位于 src 下）

```
beichen-erp-web/src/
├── api/customer.ts, purchase.ts, sale.ts, inventory.ts, finance.ts  # [NEW] 接口定义（对齐后端路径）
├── views/customer/index.vue                                  # [NEW] 客户档案列表+表单+详情
├── views/purchase/order/{index,add,detail}.vue              # [NEW] 采购订单
├── views/purchase/inbound/{index,add,detail}.vue           # [NEW] 采购入库
├── views/sale/order/{index,add,detail}.vue                 # [NEW] 销售订单
├── views/sale/outbound/{index,add,detail}.vue             # [NEW] 销售出库
├── views/inventory/stock.vue                               # [NEW] 成品库存查询
├── views/inventory/transfer/{index,add,detail}.vue        # [NEW] 库存调拨
├── views/inventory/take/{index,add,detail}.vue            # [NEW] 库存盘点
├── views/inventory/other-io/{index,add,detail}.vue        # [NEW] 其他出入库
├── views/finance/receivable/{index,detail}.vue            # [NEW] 应收台账
├── views/finance/payable/{index,detail}.vue               # [NEW] 应付台账
├── views/finance/receipt/{index,add,detail}.vue           # [NEW] 收款单
├── views/finance/payment/{index,add,detail}.vue           # [NEW] 付款单
├── views/finance/cashflow/index.vue                       # [NEW] 资金流水
├── views/finance/bill/{index,generate,detail}.vue         # [NEW] 账单生成
└── router/index.ts                                         # [MODIFY] 修复 inventory/material 指向 material 模块；将 coming-soon 路由替换为上述真实页面
```

## 关键数据结构（文本描述，不贴实现代码）

- 库存流水 InventoryStockLog：company_id, warehouse_id, material_id, change_type(采购入库/销售出库/其他入库/其他出库/调拨入/调拨出/盘点溢/盘点损), change_quantity, before_quantity, after_quantity, related_bill_no, related_bill_type, remark, create_time
- 应收/应付台账：company_id, bill_no, partner_id, partner_name, related_bill_type, amount, paid_amount, unpaid_amount, due_date, status(未结清/部分结清/已结清), create_time
- 收/付款单 + 核销明细：主表单据号/往来单位/账户/金额；明细关联 receivable_id/payable_id 与本次核销金额
- 账单：company_id, bill_type(应收/应付), partner_id, 账期起止, total/paid/unpaid, 明细关联源单据号与金额

## 设计风格

承接现有北辰 ERP 后台风格：深色侧边栏（#304156）+ 顶栏用户下拉的 Element Plus 企业级中后台布局。所有新页面统一采用"筛选栏 + 数据表格 + 分页 + 操作按钮"的列表页范式，新增/编辑用抽屉或对话框，详情用抽屉展示主表信息与明细表格。保持表格驱动、信息密度高、操作明确的管家婆式进销存体验，不做花哨视觉，强调数据准确性与操作效率。

## 代表性页面区块设计

### 1. 采购入库页（inventory/inbound）

- 顶部筛选区：入库单号、供应商、仓库、日期范围、状态（草稿/已审核/已作废）下拉
- 操作区：新增入库、审核、作废、导出按钮
- 表格区：入库单号、供应商、仓库、日期、总金额、状态、操作（详情/审核）
- 详情抽屉：主表字段 + 明细表格（物料、规格、单位、数量、单价、金额）+ 关联采购订单号

### 2. 销售出库页（inventory/outbound）

- 顶部筛选区：出库单号、客户、仓库、日期、状态
- 操作区：新增出库、审核、作废
- 表格区：出库单号、客户、仓库、日期、总金额、状态、操作
- 详情抽屉：主表 + 明细表格 + 可用量校验提示

### 3. 应收台账页（finance/receivable）

- 顶部筛选区：客户、账期、状态（未结清/部分/已结清）、日期
- 表格区：单据号、客户、应收总额、已收、未收、到期日、状态
- 操作区：收款（打开收款单）、查看明细；底部汇总未收总额

### 4. 资金流水页（finance/cashflow）

- 顶部筛选区：账户、流水类型（收款/付款/其他收入/费用支出）、日期
- 表格区：流水号、日期、账户、类型、收入、支出、余额、关联单据
- 以收入/支出用不同颜色区分（绿/红）

### 5. 账单生成页（finance/bill）

- 顶部：账单类型（应收/应付）切换、账期选择、生成按钮
- 表格区：按客户/供应商汇总的未结清单据列表（单据号、金额、到期日、未收/未付）
- 生成后展示账单号、汇总金额、明细抽屉

## Agent Extensions

### SubAgent

- **code-explorer**
- Purpose: 在各模块实现时深入参考 material/outsource/supplier 现有代码结构（entity/mapper/service/controller 约定、genCode 自动编码、多租户 CompanyTenantHandler、状态字段状态机、stockIn 累加逻辑），确保新模块风格、命名、联动方式与既有代码完全一致。
- Expected outcome: 产出可复用的现有模式清单与待对齐的接口/菜单路径，避免另起炉灶或破坏既有约定。