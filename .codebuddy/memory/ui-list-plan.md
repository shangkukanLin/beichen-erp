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

## 5.1 B1 落地记录（2026-09-25）

**用户已批准的取舍（本计划 §4 全部按推荐执行）**：①备注类入白名单 ②库存流水关联单号按类型分流（B5 做）
③对方主体全站可点 ④产品/物料明细列统一用 EntityLinks ⑤放不下时允许把最低价值列移入详情 ⑥列宽上限维持 930。

**本批改动**
- `EntityLinks.vue` 扩展目标：`product / material / supplier / vendor / customer`（路由前缀表 + idKey 默认值），
  主体类可点全站复用同一组件。
- 白名单（`ui-e2e-zh.json → col_allow_truncate`）新增 **项目名称 / 备注**（长度无上界，有 tooltip）。
- 逐页：
  - `/inventory/customer` 客户名称 → 客户详情；
  - `/product` 产品名称 → 产品详情；并给名称腾空间（SKU 120→110、规格 84→80、通用型号 96→88、安全库存 86→80、
    当前库存 120→110、操作 130→124、品牌 min100→90、产品名称 min150→**200**；实测名称需 224，新布局实得 ~252 ⇒ 完整）；
  - `/supplier/manage` 与 `/outsource/supplier/manage`（同组件双模式）编码 120→**156**（实测 152/155），
    名称 min180→**220** 并按模式分流跳 `/supplier/detail` 或 `/outsource/supplier/detail`，
    类型 150→140、联系人 120→96、电话 130→108、状态 80→76、操作 120→116 抵平（实得名称 ~264 ≥ 需 254 ⇒ 完整）；
  - `/outsource/material-info` 物料名称 → 物料库存分布详情；供应商 → 供应商详情（多选弹层逐项可点，`supplierItems()` 由居间表返回的 ids 拼）；
  - `/dev/project`（4 张表同步）项目编码 120→**140**、品牌 72→**108**、项目名称 min100→132、显示/触摸方案 75→70、
    原机尺寸/改配尺寸 70→64、项目阶段 min80→70；
  - `/dev/material` 物料名称 → `/dev/material/detail/:id`；
  - `/inventory/brand`、`/dev/material-type`、`/template` 无可点目标（品牌/类型无详情页）：前者列宽已达标，
    模板页仅把「备注」登记进白名单，未动宽度。
- 守卫 `$routes` 纳入 B1 十页。

**验证**：`scan-col-truncation.ps1` B1 十页 **0 截断**；`scan-table-overflow.ps1` B1 十页 **0 越界**；四守卫 PASS；
浏览器逐条点击：客户名称→`/inventory/customer/detail/26`、产品名称→`/product/detail/130`、
供应商名称→`/supplier/detail/90`、供货商名称→`/outsource/supplier/detail/89`、物料名称→`/outsource/material-stock/detail/188`、
供应商（临时插一条 `supplier_material` 关联验证后已回滚）→`/supplier/detail/35`、研发物料名称→`/dev/material/detail/1`。

**顺带发现的现网问题（非本次改动引入，已上报用户）**：`supplier_material` 居间表**当前 0 行**
⇒ 「物料信息管理」的「供应商」列在所有行都显示 “-”（没有数据可点）。列表接口是正常返回 `supplierIds` 的，
关联一旦录入，本批接的链接即生效。
（另注：`outsource_material` 表已无 `supplier_ids` 列，早期“物料自带供应商字段”的口径已废弃，改由居间表维护。）

## 5.2 B2 落地记录：屏幕资料 `/dev/screen-model`（2026-09-25）

**问题**：11 列的实测需宽合计 **~1381px**（型号 180 / 分辨率 147 / 屏幕类型 181 / 屏幕供应商 165 /
主屏尺寸 102 / 发布时间 129 / 指纹识别 129），远大于内容区 948px ⇒ 原实现把 8 列压到 72~113px，全部截断。

**处置**（按已批准口径「放不下时把最低价值列移出列表」）：
- **移出 4 列**：刷新率 / 副屏尺寸 / 指纹识别 / 发布时间 —— 四者在「编辑」弹框里都可看可改，
  且 **Excel 导入导出仍是全 15 字段**（与列表列无关）⇒ 数据未丢失，只是列表不再赘列。
- **保留 7 列并给足实测宽**：品牌 min65（弹性吃余量）/ 型号 180 / 主屏尺寸 102 / 分辨率 147 /
  屏幕类型 181 / 屏幕供应商 165 / 操作 90 ⇒ 声明合计 930，实测**0 截断 / 0 越界 / btnClip 0**。

**验证**：两个守卫该页 0/0；四守卫 PASS；浏览器实测首行（Blackview Hero 10）三处长文本
（`6.9 inches` / `1080 x 2560 pixels` / `Foldable AMOLED`）完整显示，编辑弹框字段 15 项齐全
（含被移出的刷新率/指纹识别/副屏尺寸/发布时间）。

## 5.3 B3 落地记录：采购 3 页（2026-09-25）

**后端只增字段 2 处**（同一口径：列表行附逐项明细 `[{id,name,quantity}]`，`id` = 产品主数据ID）：
- `PurchaseOrderServiceImpl.page`：顺带去掉"摘要与逐项各查一次产品"的重复（原为逐项 `selectById`）；
- `PurchaseReturnServiceImpl.page`：复用已有的 `finalProductMap`。

**前端逐页**
- `/inventory/purchase`：单号 min120→**148 固定**并做成链接（详情）；采购明细 min130→160 改 `EntityLinks(product)`
  —— 摘要改链接后内容变短（多值只显示「首个 等 N 项」+ 弹层），原先"采购明细需 250px"的截断随之消失；
  订单日期 100→96、总金额 108→100、状态 78→74 抵平 ⇒ 声明合计 **930**。
- `/inventory/purchase-return`：退货单号 150 做成链接；退货明细改 `EntityLinks(product)`（实测得 236px，完整）。
- `/inventory/purchase-exchange`：换货单号 min134→**150** 做成链接；换货概况（"付费 + 退1(DEFECT) → 换1(A)"复合文本，
  实测需 301px）**不拆链**，min138→175 并登记进白名单（tooltip 看全文）；同时把被挤到 97px 的「供货商」
  min94→**121**（实测需 121，原先 20/20 行被截断）⇒ 声明合计 916。

**验证**：两个守卫三页 **0 截断 / 0 越界 / btnClip 0**；四守卫 PASS；后端编译 + 重启就绪；
浏览器逐条点击：采购单单号→`/inventory/purchase/detail/298`、采购明细产品→`/product/detail/130`、
采购退货单号→`/inventory/purchase-return/detail/35`、退货明细产品→`/product/detail/61`、
采购换货单号→`/inventory/purchase-exchange/detail/112`。

## 5.4 B4 落地记录：销售 4 页（2026-09-26）

**后端只增字段 1 处**：`SaleReturnServiceImpl.page` 列表行补 `items:[{id,name,quantity}]`（`id` = 产品主数据ID）。

**前端逐页**
- `/inventory/sale`（销售单）：单号 min120→**148 固定**并做成链接进详情。
- `/sale/return`（销售退货单）：退货单号 120→**150 固定**（实测需 148）并做成链接；
  退货概况改 `EntityLinks(product)`（多值只显示「首个 等 N 项」⇒ 原先"需 230px"的截断消失）；
  金额 108→104、收费 96→94 抵平 ⇒ 声明合计 894。
- `/sale/exchange`（销售换货单）：换货单号 120→**150** 并做成链接；换货概况 min130→170（复合文本，白名单 + tooltip）；
  **收费列改单行**（收费类型从并排小字挪进 tag 的 title，与销售退货页同一写法 ⇒ 需宽 159→110）；
  换货日期 100→92、换入/换出仓 min110→100、状态 80→76 ⇒ 声明合计 930。
- `/inventory/return-sort`（退货整理）：
  - 待整理总览：来源日期 82→**106**（实测需 106，原先全被截断）、客户 84→**118** 并做成链接进客户详情（行自带 `customerId`）、
    SKU 94→**110**（实测需 110）；为抵平把产品 min110→80（弹性列，白名单）、状态 78→72、来源单据 150→142、
    操作 58→54、单位 46→44，并**保住表头下限**（待整理/已整理 110、可整理 60、停留天数 76 ——
    本页专属守卫 `verify-returnsort-list-fit.ps1` 断言"表头不得被裁"）。声明合计 1006 ≤ 1005+2 容差。
  - **顺带修掉一个既有缺陷**：详情页明细表列宽合计 1080px ＞ 容器 963px（该守卫的"详情页"段一直 FAIL、
    横向溢出 117px）⇒ 来源单据 170→150、SKU 130→110、产品 min160→130、待整理数量 110→96、
    A/B/C/不良各 100→92、校验 110→96 ⇒ 合计 950 ≤ 963 ✓，该守卫现已整体 PASS。

**验证**：`scan-col-truncation.ps1` 销售三页 + 退货整理 **0 截断**（产品列为白名单）；`scan-table-overflow.ps1` 三页 0 越界；
`verify-returnsort-list-fit.ps1` **整体 PASS**（2 个页签 + 开单页 + 详情页）；四守卫 PASS；后端编译 + 重启就绪；
浏览器逐条点击：销售单单号→`detail/296`、销售退货单号→`detail/156`、退货概况产品→`/product/detail/63`、
销售换货单号→`/sale/exchange/detail/27`、退货整理客户→`/inventory/customer/detail/24`。

**踩坑（已记入 2026-09-26 日志）**：给 `goSource` 后面追加新函数时把原有收尾花括号留下 ⇒ Vue 组件编译失败、
页面白屏（Vite "Failed to fetch dynamically imported module"）。**警示：`replace_in_file` 追加函数时务必确认原有 `}` 的位置。**
本轮同时给守卫加了"慢页重试"（退货整理表格 ~3s 才渲染，首次测不到就加长等待重测）。

## 5.5 B5a 落地记录：成品库存 + 移仓（进销存 9 页，2026-09-26）

**白名单扩展**（`ui-e2e-zh.json → col_allow_truncate`）：新增 **产品名称 / 仓库 / 移出仓库 / 移入仓库 / 变动类型**。
理由：仓库名 = 主体名 + 「委外仓库」后缀（实测最长 214px）、变动类型 = 中文枚举（实测最长 192px），
两者长度随数据变化且**都已带 tooltip**（仓库/产品均另有可点入口）。

**后端只增字段 2 处**：
- `WarehouseMoveServiceImpl.page` → `items:[{id,name,quantity}]`（id = 产品主数据ID）；
- `MaterialMoveServiceImpl.page` → `items:[{materialId,materialName,quantity}]`。

**逐页**
| 页 | 显示完整 | 可点 |
|---|---|---|
| /inventory/product-stock | 品牌 min78→**108**（实测需 107）；SKU/待整理/总库存/安全库存/分布仓库微收 ⇒ 938 | **产品名称**→产品详情 |
| /inventory/warehouse（启用/停用两张表） | 仓库编码 120→**146**（实测需 146）；仓型 74、本月/上次盘点 96、地址 110、电话 104、操作 134 ⇒ 940 | **仓库名称**→仓库详情 |
| /inventory/stock-log | 时间 140→**96**（只显示日期，原 140 白占 44px）、变动类型 110→150（白名单）、关联单号 130→**166**、仓库 110→**150**（白名单 + 补 tooltip）、数量列 84/92/92；产品名称 min110 | 产品名称→产品详情（**关联单号的分流本是现成实现，直接沿用**） |
| /inventory/other-io | —（0 截断） | **单号**→详情 |
| /inventory/reclassify | 单号 120→**146**（实测需 139） | **单号**→规格调整详情 |
| /inventory/warehouse-move | 单号 min120→146；仓库 min110→112 | **单号**→移仓详情；**产品明细**→产品详情（EntityLinks） |
| /inventory/stock-take（共用 `stock-take/StockTakePanel.vue`，与物料盘点同组件） | 盘点单号 130→**146**、仓库 min110→130、月份 80、日期 96、明细/差异行数 70/70、差异合计 88 ⇒ 930 | **盘点单号**→明细页（与「查看明细」同一出口）；**仓库**→仓库详情（按 warehouseCategory 分流） |
| /inventory/stock-loss | 报损单号 min112→**150**、仓库 min100→**140**、日期 92、原因 80、明细 min110、金额 88、审核人 70 ⇒ 938 | **报损单号**→详情；**仓库**→仓库详情 |
| /inventory/material-move | 单号 min120→146；移出/移入仓库 min110→112 | **单号**→详情；**移出/移入仓库**→仓库详情（**委外/自有分流**）；**物料明细**→物料详情 |

**验证**：`scan-col-truncation.ps1` 9 页 **0 截断**；`scan-table-overflow.ps1` 9 页 **0 越界 / btnClip 0**（仓库页操作列 130→134 消除 1px 裁切）；
四守卫 PASS；后端编译 + 重启就绪；浏览器逐条点击 13 条路径全部通过，含**委外仓分流**实测
（物料移仓「测试供货商A1委外仓库」→ `/outsource/warehouse/detail/58`、「自有物料一号仓」→ `/inventory/warehouse/detail/74`）。

## 5.6 B5b 落地记录：物料仓库 7 页（2026-09-26）

**白名单再补 3 项**：**仓库名称 / 所属供应商 / 报损明细**（仓库名称 = 主体名 + 「委外仓」后缀，实测最长 274px；
所属供应商 218px；报损明细 = 明细汇总 —— 三者长度都随数据变化，均带 tooltip）。

**逐页**
| 页 | 显示完整 | 可点 |
|---|---|---|
| /outsource/warehouse（委外仓库管理，2 表） | 所属供应商 140→**160**、仓库名称 min140→**180**、地址 min150→140 ⇒ 860 | **仓库名称**→委外仓详情；**所属供应商**→供应商详情 |
| /outsource/material-warehouse（自有物料仓，2 表） | —（0 截断） | **仓库名称**→仓库详情 |
| /outsource/material-stock（物料库存详情） | —（0 截断，行=仓库×物料） | **物料名称**→物料库存分布详情；**所在仓库**→仓库详情（按 `warehouseCategory` 分流） |
| /outsource/material-stock-log（物料库存流水） | 时间 160→**96**、变动类型 110→150、关联单号 120→**166**、仓库 120→150、数量 84/92/92 ⇒ 940 | **物料名称**→物料详情；**仓库**→仓库详情 —— 并**修掉一个既有缺陷**：原 `goWarehouse` 一律跳 `/inventory/warehouse/detail`，而物料流水**大半发生在委外仓** ⇒ 委外仓会落到空页；现已按 `warehouseCategory` 分流 |
| /outsource/material-stock-take（物料库存盘点） | 复用 `stock-take/StockTakePanel.vue`（B5a 已改）⇒ 本批零改动 | 同 B5a |
| /outsource/stock-loss（物料报损） | 报损单号 min112→**152**、仓库 min100→130、明细 min124→130、日期 92、原因 80、金额 80、状态 72、审核人 70 ⇒ 938 | **报损单号**→详情；**仓库**→仓库详情（**委外/自有分流**，挂载时拉一次仓库列表建映射） |
| /outsource/other-io（物料其他出入库） | 单号 120→**156**（OWO- 实测需 156）、备注 min110→130 ⇒ 870 | **单号**→详情 |

**验证**：`scan-col-truncation.ps1` 7 页 **0 截断**；`scan-table-overflow.ps1` 7 页 **0 越界 / btnClip 0**；
四守卫 PASS；浏览器逐条点击 10 条路径全部通过，含**委外/自有分流**实测（物料库存详情「测试加工厂A1委外仓库」→
`/outsource/warehouse/detail/66`、物料流水委外仓 → `detail/68`、物料报损委外仓 → `detail/68`、
自有物料二号仓 → `/inventory/warehouse/detail/75`）。

## 6. 进度

- [x] 委外加工 6 页（2026-09-25，含 EntityLinks 组件与两个守卫，提交 `5644f4e`）
- [x] B1 基础数据 + 研发（2026-09-25）
- [x] B2 屏幕资料（2026-09-25，列表精简为 7 列、全部完整）
- [x] B3 采购（2026-09-25，单号/明细可点，明细列改 EntityLinks）
- [x] B4 销售（2026-09-26，单号/客户可点；退货整理顺带修掉详情页既有溢出）
- [x] B5a 成品库存 + 移仓（2026-09-26，9 页：单号/仓库/产品/物料可点 + 仓库分流）
- [x] B5b 物料仓库（2026-09-26，7 页：仓库/主体/物料/单号可点；顺带修掉物料流水的仓库跳转缺陷）
- [ ] B6 财务
- [ ] B7 系统 + 分析
