# 全站列表「显示完整 + 关键字段可点」——方案与分批计划

> 状态：**待用户确认取舍点（§4）后开工**。委外加工 6 页已于 2026-09-25 完成（作为范式与验收口径的样板）。
> 基线数据：2026-09-25 全站普查（`probe-col-survey.ps1` 一次性探针）——
> **58 个有表格的菜单页，60 个「被省略号截断且不在白名单」的列**。

## 1. 统一规范（已定稿，直接复用）

**判据（两个守卫，改列宽后都必须跑 0/0）**
- `tools/regression/scan-table-overflow.ps1`：`margin = 表格宽 − 列宽和 < -2`、真横向滚动、操作列按钮被切 → FAIL；
- `tools/regression/scan-col-truncation.ps1`：单元格 `scrollWidth > clientWidth`（文本被切）或换行 → FAIL，
  白名单列允许省略号（`ui-e2e-zh.json → col_allow_truncate`，当前：产品/物料/物料名称/退货·送修内容/出库源仓）。
- ⚠️ 白名单比对必须在**浏览器内**做（PS 控制台按 GBK 解码子进程输出，中文比对会永远失败）。

**列宽档位（实测驱动，勿凭感觉）**

| 列类型 | 档位 |
|---|---|
| 单据号/编码 | 固定 = 实测需宽（含 link 按钮内边距，一般 138~160） |
| 日期 | **100**（`2026-09-18` 实测需 100~106，现网大量 76~98 ⇒ 都在被截断） |
| 对方主体（客户/供应商/供货商/加工厂） | 固定 = 实测需宽（120~140） |
| 仓库名 | 长名（工厂名+「委外仓库」最长 274px）⇒ 给 140~150 + tooltip，或入白名单 |
| 状态/类型/方向（tag） | 74~96；**长中文枚举**（如「成品加工退货入委外仓」11 字 = 192px）⇒ 建议入白名单或改短文案 |
| 操作 | 1/2/3/4 按钮 = 76/108/140/176（「下载合同」这类 4 字按钮会顶到 176） |
| 备注/明细概况/型号等无上界文本 | 入白名单 + `show-overflow-tooltip` |

**可点规范（目标路由全部已存在，无需新建页面）**

| 列 | 目标 |
|---|---|
| 单据号 | 该单据详情页（各模块 detail/:id） |
| 客户 | `/inventory/customer/detail/:id` |
| 供应商 | `/supplier/detail/:id` |
| 供货商（成品商） | `/outsource/supplier/detail/:id` |
| 仓库 | `/inventory/warehouse/detail/:id`；有 `factoryId` → `/outsource/warehouse/detail/:id`（分流，见 `outsource/other-io:55-60`） |
| 产品 | `/product/detail/:id` |
| 物料（委外物料） | `/outsource/material-stock/detail/:materialId` |
| 产品/物料多值 | 共享组件 `components/EntityLinks.vue`（单项直链 / 多项 Popover 逐项可点 / 无 id 回退文本） |
| 关联单号（库存流水） | 按 `related_bill_type` 分流到各单据详情（需写映射表，见 §4-②） |
| 无 id（历史数据/后端未给） | 保持纯文本（EntityLinks 自动回退） |

**做法（每批固定 6 步）**：①跑普查拿该批实测数据 → ②列宽重排（按 need，合计 ≤930）→ ③可点接线（复用 EntityLinks；缺 id 的补后端字段，只增不改）→
④把该批路由加进 `scan-col-truncation.ps1` 的 `$routes` → ⑤两个守卫 + 四守卫（`web-check.ps1`）→ ⑥浏览器逐条点击抽样（单值/多值各一）→ 文档 + 提交。

## 2. 全站基线（2026-09-25 实测，`现宽>需宽 | 样本`）

**已达标（0 截断）**：委外加工 6 页（本批样板）＋ `/inventory/customer`、`/product`、`/inventory/brand`、
`/dev/material-type`、`/outsource/material-info`、`/dev/material`、`/inventory/purchase-return`、`/inventory/sale`、
`/inventory/other-io`、`/inventory/warehouse-move`、`/inventory/stock-take`、`/finance/receivable`、`/finance/cashflow`、
`/finance/account`、`/finance/payment`、`/finance/payable-transfer`、`/system/user`、`/analysis/sale`、`/analysis/purchase`、
`/analysis/cash`、`/outsource/material-warehouse`、`/outsource/material-stock`。

**待整改（按模块）**

| 页 | 截断列（现宽>需宽 ｜ 样本） |
|---|---|
| **基础数据/研发** | |
| /outsource/supplier/manage | 供货商编码 120>155 |
| /supplier/manage | 供应商编码 120>152 |
| /template | 备注 548>1375（无上界 ⇒ 白名单） |
| /dev/project | 项目编码 120>139；项目名称 127>162；品牌 72>107 |
| /dev/screen-model | **8 列全截断**：型号 113>180、主屏尺寸 72>102、分辨率 102>147、屏幕类型 102>181、副屏尺寸 72>102、指纹识别 84>129、屏幕供应商 102>165、发布时间 76>129 |
| **采购** | |
| /inventory/purchase | 采购明细 148>250 |
| /inventory/purchase-exchange | 换货概况 156>301 |
| **销售** | |
| /sale/return | 退货单号 120>148；退货概况 163>230 |
| /sale/exchange | 换货单号 120>143；换货概况 156>196；收费 100>159 |
| /inventory/return-sort | 来源日期 82>106；客户 84>114 |
| **成品/物料库存** | |
| /inventory/product-stock | 品牌 79>107 |
| /inventory/warehouse | 仓库编码 120>146 |
| /inventory/stock-log | 变动类型 110>180；关联单号 130>164；仓库 110>189 |
| /inventory/reclassify | 单号 120>139 |
| /inventory/stock-loss | 仓库 115>177 |
| /inventory/material-move | 移出仓库 148>177；移入仓库 148>214 |
| /outsource/warehouse | 所属供应商 140>218；仓库名称 211>274 |
| /outsource/material-stock-log | 变动类型 110>192；关联单号 120>161；仓库 120>189 |
| /outsource/material-stock-take | 仓库 130>177 |
| /outsource/stock-loss | 报损单号 130>151；仓库 115>177；报损明细 143>163 |
| /outsource/other-io | 单号 120>156；备注 149>176 |
| **财务** | |
| /finance/payable | 供应商 107>128；业务场景 88>118 |
| /finance/bill | 类型 60>88 |
| /finance/receipt | 单号 117>138；来源 104>138 |
| /finance/expense | 备注 158>185 |
| /finance/invoice | **5 列**：发票号码 96>140；方向 56>84；发票类型 84>132；开票日期 84>106；状态 64>96 |
| **系统/分析** | |
| /system/role | 备注 260>347（无上界 ⇒ 白名单） |
| /system/menu | 路由路径 177>249；图标 120>169 |
| /analysis/customer | 客户 86>123；最近成交 84>106 |
| /analysis/tax | 已税 78>85 |

**已可点 vs 缺可点（普查实测）**
- 已有可点：`/inventory/purchase`「供货商/入库仓库」、`/inventory/purchase-exchange`「退回出库仓/换入入库仓」、
  `/inventory/sale`「客户/出库仓库」、`/sale/return`「客户」、`/sale/exchange`「换入仓/换出仓」、
  `/inventory/return-sort`「来源单据」、`/inventory/stock-log`「关联单号/仓库」、`/inventory/other-io`「仓库」、
  `/inventory/reclassify`「仓库」、`/inventory/warehouse-move`「移出/移入仓库」、`/outsource/material-stock-log`「关联单号/仓库」、
  `/outsource/other-io`「仓库」、`/analysis/customer`「客户」，以及委外加工 6 页（本批做的）。
- **缺可点（本计划要补）**：
  - **单据号**：采购单、采购退货、采购换货、销售单、销售退货、销售换货、移仓单、物料移仓、规格调整、成品报损、成品其他出入库、
    物料报损、物料其他出入库、收付款/费用/账单/发票等财务单据、盘点单、委外仓库/自有物料仓（→ 仓库详情）；
  - **对方主体**：`/supplier/manage`、`/outsource/supplier/manage`、`/inventory/return-sort`（客户）、`/finance/payable`（供应商）、
    `/outsource/warehouse`（所属供应商）、`/analysis/customer`（客户，已可点）；
  - **仓库**：`/inventory/stock-loss`、`/inventory/material-move`（移出/移入）、`/outsource/stock-loss`、
    `/outsource/material-stock-take`、`/outsource/warehouse`（仓库名称）、`/outsource/material-warehouse`；
  - **产品/物料明细**：`/inventory/purchase`（采购明细）、`/sale/return`（退货概况）、`/sale/exchange`（换货概况）、
    `/outsource/stock-loss`（报损明细）、盘点明细、`/inventory/sale`（产品）、`/inventory/purchase-exchange`（换货概况）等。

## 3. 分批计划（每批一次提交，批次内逐页走 §1 六步）

| 批 | 范围（页数） | 主要工作 | 备注 |
|---|---|---|---|
| B1 | 基础数据 + 研发（11 页） | 编码/名称列加宽（120→实测）、品牌、项目 3 列 | 含 `/template` 备注 → 白名单 |
| B2 | 屏幕资料 `/dev/screen-model`（1 页，8 列） | 全表重排（英文串长：分辨率/屏幕类型/型号） | 最丑的一页，建议单独一批，可能需要删/并列 |
| B3 | 采购（3 页） | 采购单/退货/换货：单号可点、明细概况改 EntityLinks、列宽 | 概况列需确认行内 id |
| B4 | 销售（4 页） | 销售单/退货/换货/退货整理：单号、客户、产品可点 | 概况列同上 |
| B5 | 成品库存 + 物料仓库（15 页） | 仓库列统一可点（分流）、单号可点、流水页「关联单号」按类型分流 | 最大一批，可拆两次提交 |
| B6 | 财务（10 页） | 单号/供应商可点、日期列 100、长 tag 收窄 | 只改展示，不动资金逻辑 |
| B7 | 系统 + 分析（8 页） | 列宽为主（备注/路由/图标入白名单或加宽） | 分析页不加交互 |

## 4. 需用户决策（开工前）

① **备注类列**（`/template` 1375px、`/system/role` 347px、`/finance/expense`、`/outsource/other-io`）长度无上界 ⇒ 建议**入白名单 + tooltip**，不强行加宽。
② **无详情页可跳的字段**：
   - `/inventory/stock-log`、`/outsource/material-stock-log` 的「关联单号」→ 可按 `related_bill_type` 分流到各单据详情（需一张映射表：STOCK_TAKE→盘点明细、WAREHOUSE_MOVE→移仓、LOSS→报损、OUTSOURCE_*→委外单据…）。做还是保持现状？
   - `/analysis/*`（市场分析）无单据详情 ⇒ 保持不可点。
③ **对方主体是否全站都做可点**（客户/供应商/供货商/所属供应商）？路由都现成，推荐全做。
④ **产品/物料明细列**是否统一用 `EntityLinks` 做成可点（推荐，与委外口径一致）？预计 **4~6 处需后端补明细 id**（采购明细、退货/换货概况、报损明细、盘点明细…）。
⑤ **放不下时的处置授权**：允许把**最低价值列移入详情页**（如物料订单「下单日期」的处理）以腾位？还是宁可保留列、允许该列省略号？
⑥ 列宽家规上限维持 **930**（保守，1200px 窗口下仍一行显示完）还是放宽到 **948**（本机 1262 视口下的安全值）？

## 5. 验证口径（每批）

1. `scan-col-truncation.ps1`（该批路由已加入）→ **0 截断**；2. `scan-table-overflow.ps1 -Only <该批>` → **0 越界**；
3. `web-check.ps1` 四守卫；4. 浏览器逐条点击抽样（单号/对方/仓库/产品/物料各取一条，多值行验 Popover）；
5. 纯展示层改动 ⇒ 不涉库存/资金/状态机；若补了后端字段，走 `be-build.ps1` + `restart-backend.ps1`。

## 6. 进度

- [x] 委外加工 6 页（2026-09-25，含 EntityLinks 组件与两个守卫）
- [ ] B1 基础数据 + 研发
- [ ] B2 屏幕资料
- [ ] B3 采购
- [ ] B4 销售
- [ ] B5 成品库存 + 物料仓库
- [ ] B6 财务
- [ ] B7 系统 + 分析
