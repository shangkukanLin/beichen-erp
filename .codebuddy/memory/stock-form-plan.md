# 委外退回成品 → 委外仓库存形态（`stock_form`）改造方案 + 全库读写点清单

> 来源：2026-09-25 用户口径（已确认）+ 全库只读盘点（68 次检索）。
> 状态：**P0-1 盘点完成；P0-2 的数据库迁移已执行完成（2026-09-25）**；**Java/前端传参改造尚未开始**。
> **动手前请按本文档逐项核对，勿只改咽喉方法**。

## 0.1 P0-2 数据库迁移：已执行 ✅（2026-09-25）
- 迁移脚本：`beichen-erp-server/sql/migration-stock-form.sql`（幂等）；基准 DDL 已同步进 `beichen-erp-server/src/main/resources/schema.sql`。
- 已执行内容：`warehouse_stock` 与 `warehouse_stock_log` 各加 `stock_form VARCHAR(20) NOT NULL DEFAULT 'MATERIAL'`；
  `uk_wh_prod_quality_company` → `(warehouse_id, product_id, quality_type, stock_form, company_id)`；
  `uk_wh_material_company` → `(warehouse_id, material_id, stock_form, company_id)`；新增 `idx_stock_form`。
- 改造前归档：`tools/db-archive/before-stock-form.sql`（含两张表的 SHOW CREATE TABLE + 计数）。
- **前后对照（完全一致 ⇒ 无损）**：`warehouse_stock` 78 行 / 数量 14572（形态全部 MATERIAL）；`warehouse_stock_log` 2128 行（全 MATERIAL）；
  委外仓 29 行/9129、自有仓 49 行/5443；委外仓成品行 0（符合预期）。
- ⚠️ 环境注意：mysql 客户端用 `-uroot -proot`（`MYSQL_PWD` 实测**不生效**，会 Access denied）；脚本里 **不要**设
  `$ErrorActionPreference='Stop'`（mysql 的密码告警走 stderr，会被当成致命错误中断）。
- ✅ **① 实体已接（2026-09-25）**：`warehouse/entity/WarehouseStock.java` 加 `stockForm`（默认 MATERIAL）+ 三个形态常量
  `FORM_MATERIAL/FORM_PRODUCT_DEFECT/FORM_PRODUCT_REPAIR`；`WarehouseStockLog.java` 同步加字段（提交 `1410dd2`/`b3df104`）。
- ✅ **② 成品侧 + 物料侧都已接（2026-09-25，提交 `34df021` + 本轮）**：
  - `WarehouseStockMapper.updateQuantity` / `updateMaterialQuantity`：WHERE 均增 `AND stock_form = #{stockForm}`，签名加参；
  - `selectExist` / `selectMaterialExist` / `insertStock` / `insertMaterialStock`：均带形态（定位键 + 建行显式落形态）；
  - `changeStock` 与 `changeMaterialStockInternal`：所有定位/建行/流水点都带形态；
  - 形态**暂固定 `FORM_MATERIAL`** ⇒ 既有 40+ 调用点一行未改、行为不变；`mvn compile` PASS（编译器点名并修掉了唯一漏点 `getQuantity` 内的 selectExist 调用）。
  - 「允许负库存」分支按**行 ID** 更新（`eq(WarehouseStock::getId, exist.getId())`）⇒ 天然不跨形态累加，SQL 无需改。
- ⏳ **Java 侧剩下的（②③④，P0-2 未完，别开 P0-3）**：
  - `warehouse/mapper/WarehouseStockMapper.java`：`updateQuantity`（:15-30）与 `updateMaterialQuantity`（:33-45）两条 UPDATE 的 WHERE 需加 `AND stock_form = #{stockForm}`，方法签名同步加参；
  - `warehouse/service/WarehouseStockService.java`（22.8KB）：`changeStock`(:71)、`changeMaterialStock`(:120/:133)、`changeMaterialStockAllowNegative`(:148) 三个咽喉加 `stockForm` 形参（**保留默认 MATERIAL 的重载**，先不动调用点也能跑），并同步 `selectExist`(:341)/`selectMaterialExist`(:349)/`insertStock`(:356)/`insertMaterialStock`(:367) 与 `WarehouseStockLog` 写入（流水也要带形态）；
  - 然后逐个给 40+ 调用点显式传值（现有调用一律 `MATERIAL` ⇒ 与今天等价），逐个确认 + 回归；
  - 前端 `api/enums.ts` 加形态 code→中文 映射。
- 迁移本身安全（新列有 DEFAULT、存量全 MATERIAL ⇒ 现有 UPSERT 的 WHERE 不会误配），但**P0-2 未完成**，P0-3 及之后都别开始。

### P0-2 ②③ 精确改法（照此执行；目标是"**行为等价 + 可编译**"：既有方法保留为委托重载，先不动 40+ 调用点）
> 文件均在 `beichen-erp-server/src/main/java/com/beichen/erp/` 下。

1. **`warehouse/entity/WarehouseStockLog.java`**：加 `private String stockForm;`（流水表已迁列，不补则"库存有形态、流水无形态"）。
2. **`warehouse/mapper/WarehouseStockMapper.java`**
   - `updateQuantity`（:15-30）：WHERE 加 `AND stock_form = #{stockForm}`，签名末位加 `@Param("stockForm") String stockForm`。
   - `updateMaterialQuantity`（:33-45）：同上。
   - ⚠️ 另有"允许负数"分支走 **setSql 裸加**（`changeMaterialStockInternal` 内，约 :197）：那条 SQL 也要带 `stock_form` 条件（或另加一个带形式的 mapper 方法），否则负库存路径会跨形态累加。
3. **`warehouse/service/WarehouseStockService.java`**
   - `changeStock`（:71-114，9 参）：**保留原签名**，改为委托新增的 10 参重载并传 `MATERIAL`；新重载体内把 :81/:91 `updateQuantity`、:83/:97 `selectExist`、:89 `insertStock` 全部带上 stockForm；:101-113 的 log 补 `setStockForm(stockForm)`。
   - 物料侧：`changeMaterialStock`（:120/:133）、`changeMaterialStockAllowNegative`（:148）保留为委托重载（MATERIAL），新增带 stockForm 的重载 → `changeMaterialStockInternal(..., stockForm)`；log（:228-250）与 `insertMaterialStock`（:369-374）补 stockForm。
   - `selectExist`（:341）/ `selectMaterialExist`（:349）的 WHERE 也必须带 stockForm（否则会读到别的形态的行）。
   - :77/:189 现在写死的 `ProductQualityType.A` / `QualityType.GOOD` 与形态是**正交**的，别混用（形态是物料侧唯一区分维度）。
4. **建议**：新增枚举 `warehouse/common/StockForm.java`（`MATERIAL` / `PRODUCT_DEFECT` / `PRODUCT_REPAIR`），避免满项目写字符串。
5. **每步验证**：`mvn -o compile` → 启动后端 → 四守卫 → 关键路径回归（采购入库/销售出库/调拨/盘点/报损/委外收发/加工退货红冲/维修返回）→ 抽查 `warehouse_stock_log.stock_form` 全为 `MATERIAL`（证明零回归）。
6. **④（最后一步）**：逐个把 40+ 调用点改为显式传形态（现有调用一律 `MATERIAL`），逐个确认；前端 `api/enums.ts` 加 code→中文映射。

### 批 1 尾（④ + 回归）：范围裁决 + 执行配方（2026-09-25）
**裁决：不做"40+ 调用点逐个显式传 MATERIAL"这一大批量改动** ✗。理由：
- 所有既有流程的形态**必然是 MATERIAL**，默认值已经正确 ⇒ 改动零功能收益；
- 形态是 **P1/P2 新流程**才需要的维度 ⇒ 只需在新流程写代码时**显式传值**即可，与老调用点无关；
- 为"形式统一"去改 ~25 个文件、40+ 处，收益为零却引入 40+ 处误改风险（这是账务代码，不划算）。
⇒ 改为：**等 P1/P2 落地时，给三个 public 咽喉加"带 stockForm 的重载"**（新流程调新重载，老调用点继续走默认 MATERIAL）。

**回归配方（必须重启后端后才有效 —— 运行中的进程仍是旧编译产物）**：
1. 重启后端（8080）；
2. 跑四守卫：`tools/regression/web-check.ps1`；
3. 九条关键路径：采购入库 / 销售出库 / 调拨 / 盘点 / 报损 / 委外收发 / 加工退货红冲 / 维修返回（＋退货整理可选）；
4. 抽查：`SELECT stock_form, COUNT(*) FROM warehouse_stock_log GROUP BY stock_form;` ⇒ 应**全为 MATERIAL**；
   并与改造前基线对比：`warehouse_stock` 78 行 / 14572、`warehouse_stock_log` 2128 行；
5. 每条路径都做「审核 → 查库存与流水 → 反审核 → **逐行回到原值**」的成对验证（这是零回归的实证口径）。

**迁移对旧代码是安全的（已生效的事实，不依赖重启）**：新列有 DEFAULT `'MATERIAL'`，旧代码不写该列也不带该条件
⇒ 新流水自动落 MATERIAL、旧 UPSERT 定位不受影响；两条唯一键只是原键的超集 ⇒ 存量定位不变。

### 批 1 收口实证 ✅（2026-09-25，提交 `e91188a` 之后）
方法（可复用）：取一张 **DRAFT 销售单** → `PUT /inventory/sale/{id}/audit` → 查库存/流水 → `un-audit` → 期望**精确回原值**。
实测（id=294）：
```
BEFORE      stock total=14572 · log=2128 · maxLogId=3907
AUDIT 后    stock total=14567(−5) · log=2129 · 新流水 stock_form=MATERIAL / change_type=SALE_OUT
UN-AUDIT 后 stock total=14572 ✅ 精确回基线 · log=2130
新流水里非 MATERIAL 行 = 0 ✅
```
⇒ **成品侧 chokepoint（`changeStock`）在新代码下端到端正常、成对性完好、零回归** ✅。
⚠️ 本探针只覆盖**成品侧**；物料侧（`changeMaterialStock*`）已由编译 + 启动验证 ✅，但**尚未用真实委外单据跑过** ✗ ⇒
进 P0-3 之前建议补一条物料侧成对验证（委外收发/还料任一 ✓）。

### 物料侧成对验证 ✅（2026-09-25，批 1 正式收口）
真实单据：委外其他出入库 id=221（AUDITED）→ `PUT /outsource/other-io/{id}/un-audit` → `…/audit`：
```
反审核: 委外仓 9129 → 8629（−500，物料实动 = 阳性对照成立 ✓）· 新流水 stock_form=MATERIAL / change_type=CANCEL_IN ✅
再审核: 9129 ✅ 精确回原值
```
⇒ 物料侧 chokepoint（`changeMaterialStock*` 路径）端到端正常 ✅。
⇒ **批 1 全部收口** ✅：成品侧（销售单 294：14572→14567→14572 ✓）+ 物料侧（other-io 221：9129→8629→9129 ✓）双侧均通过"阳性对照 + 成对回原值"验证。
**探针口径（固定下来）**：审核/反审核后必须先证明"确实动了"（库存或流水 delta ≠ 0），否则记 **INVALID** 换样本；绝不把"什么都没发生"记成 PASS。

### 两条血泪教训（2026-09-25 各踩一次，务必按此做）
1. **判断"编译是否通过"绝不能把 mvn 串进管道后读 `$LASTEXITCODE`** ✗：`mvn | Select-String ...` 之后
   `$LASTEXITCODE` 取到的是**管道最后一个命令**（Select-String）的退出码 ⇒ 编译失败也会显示 `exit=0` ✗✗
   （本轮因此**漏掉一处 `selectMaterialExist` 调用点**、把编译失败的版本提交了，直到重启后端才暴露 ✗）。
   正确写法：`$out = & mvn.cmd -q -o compile -DskipTests 2>&1 ; $code = $LASTEXITCODE`（赋值不经过管道 ✓）。
2. **重启后端前先确认 MySQL 在跑** ✓：`backend_diag.log` 里 `java.net.ConnectException: Connection refused`
   ＝ MySQL(3306) 没起 ✗（`restart-backend.ps1` **不负责起 MySQL** ✗）。正确顺序：
   `start-mysql.ps1` → `restart-backend.ps1`（→ `web-dev.ps1`）。日志在**仓库根目录** `backend_diag.log`（不在 tools/regression ✗）。
   另外三件套会一起掉（串跑重脚本被 idle timeout 取消会连带带走进程树 ✗ ⇒ 重脚本一条命令只跑一个 ✓）。

## 0. 已确认的业务口径（用户 2026-09-25）
- **加工退货（DEFECT）**
  - 关联加工单：红冲该单出货/收货数据 + BOM 分解成物料到「加工厂委外仓」+ 冲减应付（＝**现状，保持**）。
  - 不关联：**不红冲、不分解料、不动应付**；退回成品以「**成品（加工退货）**」进「加工厂委外仓」。
    - 修好送回时填**返回单**：核销在厂成品 + 按**实际用料**从委外仓扣物料 + **料款生成对加工厂的应收（工厂赔料）**。
  - 成本：**退回那一刻不产生成本/应付**；成本只在**返回单按实际用料 FIFO 结转**。
- **维修退货（REPAIR）**
  - 退回成品以「**成品（维修退货）**」进加工厂委外仓（**必须与加工退货的成品区分**）。
  - 返回单：核销在厂成品 + 扣实际用料；**不产生赔料应收**（我方责任）；加工厂**可能**向我方收维修费（现有单据级 `charge*` 字段/审核生成应付，保持）。
- **返回单用料**：允许多物料多行，且**数量可超 BOM 标准用量**。
- 库存承载：**方案 B** —— `warehouse_stock` 加一列 `stock_form`（`MATERIAL` 默认 / `PRODUCT_DEFECT` / `PRODUCT_REPAIR`），工厂那边的成品存量**要能按仓查询与盘点**。

## 1. 现在的事实（盘点结论，勿再重复探索）
- 库存表 `warehouse_stock`：`warehouse_id, product_id, material_id, quality_type, quantity, available_quantity, company_id`。
- **唯一键**（`schema.sql:467-468`）：
  - 成品 `uk_wh_prod_quality_company` = `(warehouse_id, product_id, quality_type, company_id)`
  - 物料 `uk_wh_material_company` = `(warehouse_id, material_id, company_id)`（**物料侧 `quality_type` 恒写 `GOOD`**，见 `WarehouseStockService.java:189/:234/:371`）
- 委外仓实测：`warehouse_category='OUTSOURCE'`、`warehouse_type` 为 NULL、按 `factory_id` 关联加工厂；**当前委外仓 0 行成品**（只有物料）。
- 写库模式：全部走 UPSERT「先 UPDATE，0 行再 INSERT（捕获 DuplicateKey 重试）」——`WarehouseStockService.java:81-94`、`:202-213`。

## 2. 咽喉方法（改造必须从这里下手，再往下传参）
| 位置 | 方法 | 说明 |
|---|---|---|
| `WarehouseStockService.java:71` | `changeStock(wh, productId, qty, StockChangeType, relatedBillNo, RelatedBillType, spec, relatedBillId, qualityType)` | **成品唯一写入口（40+ 调用点）** |
| `WarehouseStockService.java:120 / :133` | `changeMaterialStock(...)` | 物料严格口径（不足抛错） |
| `WarehouseStockService.java:148` | `changeMaterialStockAllowNegative(...)` | 物料允许负库存（委外领料/还料/红冲/清算） |
| `WarehouseStockService.java:179-251` | `changeMaterialStockInternal` | 物料统一实现（`:189` 固化 `qt=GOOD`） |
| `WarehouseStockService.java:254 / :266 / :280` | `getQuantity` / `getMaterialQuantity` / `getMaterialQuantities` | 库存量读（反审核前置校验） |
| `WarehouseStockService.java:341 / :349 / :356 / :367` | `selectExist` / `selectMaterialExist` / `insertStock` / `insertMaterialStock` | 定位行 / 建行 |
| `WarehouseStockMapper.java:26 / :42` | `updateQuantity` / `updateMaterialQuantity` | 原子加减（带 `>=0` 护栏），仅本 service 调用 |

## 3. 写入点清单（按模块；格式 `绝对路径:行号`，路径省略前缀 `beichen-erp-server/src/main/java/com/beichen/erp/`）
**采购**：`purchase/service/impl/PurchaseOrderServiceImpl.java:284`(审核+)/`:341`(反审核−)；`PurchaseReturnServiceImpl.java:221`(−)/`:323`(+)；`PurchaseExchangeServiceImpl.java:471/474`(换出−/换入+)/`:522/525`(回滚)。
**销售**：`sale/.../SaleOrderServiceImpl.java:427`(−)/`:488`(+)；`SaleReturnServiceImpl.java:504`(+)/`:566`(−)；`SaleExchangeServiceImpl.java:435/443`/`:474/481`。
**退货整理**：`inventory/.../ReturnSortServiceImpl.java:591`(扣 PENDING)/`:598-610`(A/B/C/DEFECT 入)/`:646-665`(回滚)。
**库存单据**：`WarehouseMoveServiceImpl.java:186/188`（出/入）、`:225/227`；`StockTakeServiceImpl.java:459`(成品盘点差异)/`:465`(物料)；`ReclassifyServiceImpl.java:183/188`、`:291/296`；`OtherIoServiceImpl.java:288/305`；`MaterialMoveServiceImpl.java:206/210`、`:242/245`；`InventoryStockLossServiceImpl.java:254/267`。
**委外**：
- `outsource/.../OutsourceOrderDeliveryServiceImpl.java:1421`(收货+)/`:1439`(−)；`:1368`(BOM 领料−允许负)/`:1400`(+)；
  **加工退货（`is_reverse=1`）**：`:888`、`:1016`（扣成品−）/`:928`、`:1051`（反审核+）；`:899`、`:1035`（BOM 料还回委外仓+允许负）/`:938`、`:1068`（回滚−）。
- `OutsourceReturnOrderServiceImpl.java:482`(退货审核：成品从我方仓−)/`:574`(反审核+)；`:467`(退货物料入工厂委外仓+)/`:561`(−)；`:1133` `updateOutsourceStock` 私有咽喉；`:669`(维修返回成品入库+)/`:706`(撤销−)。
- `OutsourceMaterialReturnServiceImpl.java:349`/`:510`(物料维修返回 登记/撤销)；`:725`(−)/`:879`(+)。
- `OutsourceOtherIoController.java:244/258`（**Controller 层写库存**，允许负）。
- `outsource/.../DeliveryServiceImpl.java:681` 私有 `updateStock`；`:717`(子件耗用−)/`:742`(回滚+)。
- `CloseReportServiceImpl.java:584/588/593/705/709/750/793/797`（结单清算各仓物料归集/冲销）。
- `SupplierSettlementServiceImpl.java:216/232`（供应商清算：委外仓清零 + 入我方物料仓）。
- 说明：`SaleOutboundServiceImpl.java:43` 注释明确"本单不再变动库存"；`MaterialOrderServiceImpl` 不直接写库存。

## 4. 读取/聚合点（改造后要按形态区分的地方）
- `WarehouseStockController.java:61` `/page`；`:91-116` `/product-stock/page`（按品质 5 档）；`:205-231` `/product-summary/page`；`:330-355` `/material-stock/page`；`:431-456` `/material-summary/page`；`:587-677` `/log` 流水；`:682-759` `/by-warehouse/{id}`（**委外仓库存展示**）；`:764` `/material-history`。
- `StockTakeServiceImpl.java:177`（开单快照全仓库存行）/`:225 currentBook`（对账反查）—— **风险最高**。
- `SaleOrderServiceImpl.java:543` `check-stock` 可用量；`ReturnSortServiceImpl.java:495`（PENDING 停留天数按 (wh,product,quality) 做键）。
- `MaterialOrderServiceImpl.java:469/528/823`；`OutsourceOrderController.java:250`；`OutsourceMaterialReturnServiceImpl.java:940`；`SupplierSettlementServiceImpl.java:120/186/208`。
- **原生 SQL 硬编码**（不会自动按形态拆分）：`SupplierMaterialSummaryServiceImpl.java:96`、`OutsourceOrderServiceImpl.java:220`、`DashboardService.java:244`、`CostService.java:178 sumStock`（按 product/material 全品质求和做加权）。
- 前置校验类：`WarehouseController.java:218/222`、`OutsourceMaterialServiceImpl.java:77`、`SupplierServiceImpl.java:417`、`ClearController.java:103/106`。

## 5. 前端
- 委外仓库存展示：`views/outsource/warehouse-detail.vue:106` → `/warehouse/stock/by-warehouse/{id}`（**P0-3 的落点**）。
- 其他展示：`views/inventory/product-stock/*`、`views/outsource/material-stock/*`、`views/inventory/stock-log.vue`、`views/outsource/material-stock-log.vue`、`views/outsource/warehouse-material-history.vue`。
- 可用量展示/校验：`views/sale/order/{add,detail}.vue`、`views/purchase/return/add.vue`、`views/outsource/stock-loss/*`、`views/outsource/return-order/add.vue:265`。
- code→中文映射（后端只回 code）：`api/enums.ts` 的 `StockChangeTypeLabel` 等，需同步新增 `stock_form` 映射与筛选列。

## 6. 风险提示（新增 `stock_form` 最易漏改处，按危险度排序）
1. **唯一键/定位键**：两条唯一索引不含 `stock_form` ⇒ 不同形态若落同一 `(wh,product,quality)` 会被 `updateQuantity/updateMaterialQuantity` **跨形态累加**、`selectExist/selectMaterialExist` **串行读取**。必须同步改：两条唯一索引、两条 UPDATE 的 WHERE、INSERT 分支。
2. **咽喉参数面**：三个 `change*(9 参)` 方法需加 `stockForm`，**40+ 调用点**要逐个确认；物料侧现在恒写 `quality_type=GOOD`，形态是物料侧**唯一**的区分维度，不能照抄。
3. **两套 quality 枚举且 DEFECT 重名**：成品 `A/B/C/DEFECT/PENDING`、物料 `GOOD/DEFECT`；`WarehouseStockController.java:144-150/:258-264/:379-383/:479-483` 的归类 switch **显式不允许 else 兜底** ⇒ 新增形态必须同步加分支，否则静默漏算。
4. **原生聚合 SQL 硬编码**（见 §4）会混算多形态 ⇒ 逐个补 `stock_form` 过滤。
5. **盘点对账**：`StockTakeServiceImpl:177/:225` 若不区分形态，会把同 (product,quality) 的两形态行并成一行、差异写回错形态 ⇒ 账实不符。
6. **流水表** `warehouse_stock_log`（`schema.sql:471`）**无 `stock_form` 列** ⇒ 必须补列，否则"库存有形态、流水无形态"，反审核/追溯无法还原。
7. **成对性**：所有 ± 成对（审核/反审核、登记/撤销、归集/回滚）。`stock_form` 必须两条腿同值；**加工退货红冲**（`OutsourceOrderDeliveryServiceImpl:888/928`、`:1016/1051`）与**维修返回**（`OutsourceReturnOrderServiceImpl:669/706`）尤其危险——一侧默认 `MATERIAL`、另一侧写 `PRODUCT_*` 会冲错行/冲成负。
8. **仓与形态的业务约束**：`OutsourceReturnOrderServiceImpl.java:637`、`:491` 现按仓类别分流 ⇒ 需明确"哪些形态只能落委外仓"。
9. **实体与前端枚举**：`WarehouseStock.java` 加字段；前端 `api/enums.ts` 同步映射与筛选。

## 7. 分期（P0-3 起按此顺序）
```
P0-2 库存表 + 流水表加 stock_form；两条唯一索引改造；WarehouseStockService/Mapper 咽喉方法传参；40+ 调用点逐个确认
P0-3 委外仓只读展示（物料 / 成品（加工退货）/ 成品（维修退货）分开，可查询可盘点）
P1-1 加工退货·无单：改成"成品转移进加工厂委外仓"（不红冲/不分解料/不动应付）
P1-2 返回单（新）：核销在厂成品 + 实际用料多行（可超 BOM）+ 赔料应收 + FIFO 成本结转
P2-1 维修退货：送修=成品（维修退货）转移进委外仓；返回单扣料但无赔料应收
P3-1 复核有单加工退货 + 端到端实证（**重点断言"同一笔只冲一次"**）
```

## 8. 验收铁律（每期都要做）
- 端到端实证：建单 → 审核 → 查 `warehouse_stock`（按形态分行）/ 流水 / 应付应收 / 成本；反审核后**逐行回到原值**。
- **同一笔业务只能冲一次**：把「有单加工退货」「无单加工退货」「独立 DEFECT 单」三条路径的账务结果对齐比对，防止重复冲账。
- 盘点回归：改造后跑一次盘点开单/对账，确认形态不并表。
