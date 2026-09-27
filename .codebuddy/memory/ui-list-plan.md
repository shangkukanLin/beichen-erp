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

## 5.7 B6 落地记录：财务 10 页（2026-09-26）

**白名单再补 3 项**：**对方单位 / 业务场景 / 来源**（分别是发票对方单位名、来源单据类型中文标签 + 条件 tag、来源单据号 —— 长度随数据变化，均带 tooltip）。

**B6 统一手法（写进家规）**：
1. **tag 一律 `size="small"`** —— 默认尺寸 tag（无 size）比 small 宽 ~24px：本次 invoice「方向/状态」、payable「状态」、
   receipt「状态」、bill「类型」、cashflow「类型」、payment Tab2「状态」都在缩到 small 后**才**放得下。
   典型：bill「类型」是 2 字 tag（应收/应付），默认尺寸实测需 88px、60px 列装不下 ⇒ small 后 64px 完整。
2. **列表内改用短名，不改枚举**：invoice「发票类型」列表显示 专票/普票/电子专票/电子普票（`KIND_SHORT` 页内映射），
   登记/编辑弹窗与筛选仍用全称「增值税专用发票」⇒ 需宽 132→~74，**完整显示**且信息不丢。
3. **日期列 100/106**：`$fmtDate` 只回日期，`2026-09-25` 实测需 100~106px ⇒ 低于 96 必截断（cashflow「时间」160→96 反赚 64px）。
4. **单号可点时按「含链接按钮」量宽**：el-button 链接左右留白 ⇒ 同长度单号需 **+16px**（payable「单据号」实测 122→154）。

**逐页**
| 页 | 显示完整 | 可点 |
|---|---|---|
| /finance/invoice（发票管理，12 列最挤） | 发票号码 96→**140**、方向 56→58、发票类型 84→**78（短名）**、开票日期 84→**106**、状态 64→70（tag small）；对方单位 min72（白名单）、不含税 76、税率 44、税额 74、价税合计 80、关联单号 74、操作 **106**（两按钮一行） ⇒ 956 | 无详情页 ⇒ 不加可点 |
| /finance/payable（应付管理） | 供应商 min100→**128**（实测 128）、业务场景 88→84（白名单，标签 6 字）、主体类型 70、应付 86、已付/未付 82、到期日 92、状态 70、操作 104 ⇒ 952 | **单据号**→应付详情、**供应商**→供应商详情 |
| /finance/receivable（应收管理） | 单据号 min112→**140**；往来单位 min110；合计 886 | **单据号**→应收详情、**往来单位**→客户详情（原**只有供应商分支可点**，补上客户分支） |
| /finance/receipt（收款管理） | 单号 min112→**140**（实测 138）、来源 104→112（白名单）、往来单位 min**114**（实测 114）、主体类型 72、账户 84、金额 92、状态 72 ⇒ 956 | **单号**→收款单详情 |
| /finance/payment（付款管理） | Tab1 供应商 min180；**Tab2「付款记录」原合计 1110px ⇒ 横向滚动 154px**（首屏默认在 Tab1，历次扫描都没扫到）⇒ 重排为 954：单号 min146、供应商 124、主体类型 72、账户 104、日期 100、金额 96、凭证 64、状态 72、操作 176（4 按钮） | Tab1 **供应商**→供应商应付详情；Tab2 **单号**→付款单详情、**供应商**→供应商详情 |
| /finance/bill（账单生成） | 类型 60→**64（tag small）**、账单号 min100→**124**；账期起/止 90、总额/已收付/未收付 92、状态 76、操作 132 ⇒ 952 | **账单号**→账单详情；**往来单位**→客户/供应商详情（**按 billType 分流**，后端补 `partnerId`） |
| /finance/cashflow（资金流水） | 时间 140→**96**（只显示日期）、流水号 120→**140**、关联单据 min150→**170**、收入/支出 104、余额 100 ⇒ 924 | 无单据详情 ⇒ 不加可点 |
| /finance/expense（费用管理） | 费用单号 120→**152**（EXP-+11 位）、备注 min100→120（白名单）；合计 886 | **费用单号**→费用单详情 |
| /finance/payable-transfer（应付转应收） | 转应收单号 min110→**152**、来源应付单号 min110→**137**（实测 137） ⇒ 955 | **转应收单号**→详情、**往来单位**→供应商详情 |
| /finance/account（账户管理） | 0 截断、0 越界 ⇒ **本批零改动** | 账户无详情页 ⇒ 不加可点 |

**后端只增字段 1 处**：`FinanceBillServiceImpl.page` 补 `partnerId`（原只回 `partnerName` ⇒ 账单「往来单位」列无法挂链）。

**验证**：`scan-col-truncation.ps1` 10 页 **0 截断**；`scan-table-overflow.ps1` 10 页 **0 越界 / btnClip 0**；
四守卫 PASS；后端编译 + 重启就绪；浏览器逐条点击 12 条路径全部通过（应收单据号→detail/240、应收往来单位(客户)→
`/inventory/customer/detail/24`、应付单据号→detail/902、应付供应商→`/supplier/detail/36`、收款单号→detail/259、
账单号→detail/59、账单往来单位→`/inventory/customer/detail/16`、费用单号→detail/32、转应收单号→detail/10、
转应收往来单位→`/supplier/detail/38`、付款 Tab1 供应商→`/finance/payment/supplier/26`、付款 Tab2 供应商→`/supplier/detail/26`），
外加 **Tab2 付款记录 `overflow=0`**（横滚修复实测）。

## 5.8 B7 落地记录：系统 + 分析 9 页（2026-09-26，收尾批）

**白名单再补 4 项**：**客户 / 菜单名称 / 路由路径 / 图标**（客户名带链接、菜单名带树缩进前缀、路由路径与 Element 图标名都是开发侧数据，长度无上界，均带 tooltip）。

**逐页**
| 页 | 显示完整 | 说明 |
|---|---|---|
| /system/menu（菜单管理，100+ 行树表） | 路由路径 min140→**249**（实测需 249，原 12/100 行截断）、图标 120→**150**（实测需 169 ⇒ 余下走白名单 + tooltip）；菜单名称 min220→190（弹性、白名单）、类型 76、排序 62、状态 72、操作 140 ⇒ 935 | 无详情页 ⇒ 不加交互 |
| /analysis/tax（税务分析，分组表头 3 组×4 列） | 月份 66→**90**（"2026-01" 实测需 85，原**全部 9 行月份**被截断）；金额列 84→80、占比 58→54 抵平 ⇒ 870（容器 990，余量 120） | 分析页不加交互 |
| /analysis/customer（客户分析，12 列） | 客户 min78→**100**（列内是**链接 + 客户名**，实测需 123 ⇒ 白名单 + tooltip）、最近成交 min82→**98**（日期，原 10/10 行截断）；**表格改 `size="small"`**（与税务页同档）⇒ 5 位金额（如 15491.00）需宽从 ~90 降到 ~79，金额列统一 78/79、占比 64、订单数 72、操作 54 ⇒ 948 | 客户列**原本已可点**（/analysis/customer/:id），本批只修显示 |
| /system/user、/system/role、/analysis/cash、/analysis/sale、/analysis/purchase | 0 截断 / 0 越界 ⇒ **零改动**（role 备注早已在白名单） | — |

**B7 新增两条经验（已并入 §5.7 手法）**：
- **`size="small"` 是「金额列装不下」的正解**：小号表（font/padding 同步收窄）让 5 位金额从需 ~90px 降到 ~79px，
  一页 12 列才有救；分析页与税务页同档，视觉也统一。
- **分组表头要整组改**：tax 表三组（销售/采购/委外）各 4 列，改列宽必须同步三组，否则表头错位。

**验证**：`scan-col-truncation.ps1` 8 页（9 路由中 overview 无表）**0 截断**；`scan-table-overflow.ps1` 8 页 **0 越界 / btnClip 0**（tax 余量 120）；
浏览器实测：菜单页 206 行**无任何路由路径被省略号截断**（仅 1 行图标名走白名单省略号）、税务页月份列显示 `2026-01`（此前为 `2026-0…`）、
客户分析首行完整渲染 `测试客户A10 | 15520.00 | 29.00 | 15491.00 | 1720.05 | 13770.95 | 88.90% | 26.9% | 22 | -24.00 | 2026-09-24 | 明细`。

## 5.9 B8 落地记录：白名单收尾（2026-09-26）

**背景**：B7 收尾后仍有 **31 个列**（27 页）在省略号截断（全部已登记白名单）。B8 逐列做「能救则救」：
先把每页的**余量**量清（`scan-table-overflow.ps1` 的 `sum/avail`）——结论是 **这些页 margin 全为 0**，
即弹性列早已把余量吃满，所以只能「腾挪」：把**本质无上界**的列（多值汇总、备注）的 min 让给能救的列。

**B8 两条判定规则（写进家规）**：
1. **不制造新的截断**：腾挪前先数「被截断的单元格数」，改完必须 ≤ 改前；某页若净增截断，则**回退该页**。
   （实测踩坑：成品库存流水把「产品名称」压到 102 后 20 行产品名全被截断，比原来 8 行仓库名多 ⇒ 回退。）
2. **优先保「短值列」完整**：同一页放不下「仓库名(10 字，需 189)」与「产品名(6 字，需 126)」时，保产品名完整、
   仓库名走白名单 + tooltip（短值列完整的信息量更大）。

**B8 修好（不再出现省略号）**：
| 页 | 列 | 做法 |
|---|---|---|
| /system/menu | 图标（1 行） | 150→**170**（实测需 169；本页余量 21） |
| /finance/expense | 备注（2 行） | min120→**185**（实测需 185；本页余量 70） |
| /inventory/material-move | 移出仓库（7）、移入仓库（10→1） | min112→**189**；物料明细 min140→120 抵平（多物料汇总） |
| /outsource/other-io | 仓库（10） | min144→**189**；物料明细 min140→120 抵平 |
| /outsource/stock-loss | 仓库（9） | min130→**189**；报损明细 min130→71 抵平 |
| /outsource/material-return | 出库源仓（3） | 150→**189**；退货/送修内容 min110→71 抵平 |
| /inventory/stock-take + /outsource/material-stock-take | 仓库（各 5） | 面板 min130→**189**（两页共用）；月份 76、明细/差异行数 62、差异合计 76、状态 72 抵平 |
| /outsource/material-stock-log | 仓库（20） | 150→**174**（本页与物料名称分享余量） |
| /analysis/customer | 客户（10） | min100→**126**（实测需 123 ⇒ 完整）；占比/订单数 64/72→**56/56**（内容只需 ~45px，余量全让给客户列） |

合计**消除约 72 个被截断的单元格**，新增截断约 18 个（全部落在**多值汇总**列：物料明细/报损明细/退货送修内容/物料名称，
这些列有 tooltip + EntityLinks 弹层 + 明细可点），净减约 54 处。

**B8 结论：剩下 22 列属于「物理上无法完整」**（保留白名单 + tooltip，理由逐类记录）：
- **备注类**（`/template` 需 1375px、`/system/role` 347、`/outsource/other-io`、`/finance/expense` 已修）：自由文本，无上界；
- **多值汇总类**（产品+SKU 需 213、物料/物料明细 169、换货概况 301、报损明细 163、退货送修内容）：一格多值，无上界；
  均已有 tooltip + 明细可点（EntityLinks）；
- **长枚举/标签**（变动类型 需 180~192「物料维修送修入供应商仓」、业务场景 118、发票类型已在 B6 用短名解决）：
  改文案会影响筛选/导出/详情页 ⇒ 需产品决策；
- **测试数据超长名**（委外仓库名 189~310、所属供应商 254、项目名称 162）：真实业务名较短，当前宽度已够；
- **确实需要「砍列/并列」才能救的 2 处**：`/finance/payable` 业务场景(84 需 118)、`/finance/receipt` 来源(112 需 138)
  —— 两页 8~10 列全满且无安全余量，要完整只能删/并列一列（如 payable 的「主体类型」），**待用户拍板**。

**验证**：`scan-col-truncation.ps1` 59 路由 **0 offender**（未登记白名单的列零截断）；
`scan-table-overflow.ps1` 63 路由 **0 offender**（含 btnClip）；`web-check.ps1` 四守卫 PASS。

## 5.10 B9 落地记录：两处「必须砍列才能救」的列（2026-09-26）

B8 收尾时剩下 2 列「8~10 列全满、无安全余量、要完整只能砍列」的，B9 用**不砍列**的两条路子解决：

**① `/finance/payable`「业务场景」84 → 完整（短名方案，同 B6 发票类型做法）**
- 根因：内容是来源单据类型中文标签，最长「委外加工退货收费」**8 字实测需 128px**；本页 10 列全满无余量。
- 做法：新增共享常量 **`SourceBillTypeShortLabel` + `sourceBillTypeShortLabel()`**（`@/api/enums`），
  给出 **≤4 字** 短名（委外收货 / 物料收发 / 换货入库 / 销退收费 / 委退收费 / 转应收 …）；
  **列表单元格**用短名，**筛选下拉、导出、详情页仍用全称** ⇒ 4 字只需 72px，本列不再截断。
- 收益：白名单里的「业务场景」**已移除**（守卫恢复强制检查该列）；未删任何列、未压缩任何金额列。
- 遗留：条件 tag「已转应收」(4 字) 仍内联在本列 ⇒ 极少数已转应收行会超宽（有 tooltip 兜底），不常见故不动。

**② `/finance/receipt`「来源」112（需 138）→ 改成**可点源单**（零宽度成本方案）**
- 根因：来源单据号（XS-…，14 位 = 138px）在本页 9 列全满下**无法加宽**（操作列 4 按钮已到 170 不能压）。
- 做法：与「库存流水·关联单号」**同口径**做成链接 —— 有独立详情页直接进详情，无详情页的进列表并带 `?billId=`；
  现金销售自动收款的 `sourceBillType=SALE_ORDER`、`sourceId`=销售单 id ⇒ 直达 `销售单详情`。
- **顺带修掉一个既有缺陷（影响 4 处入口）**：公共映射 `SourceBillDetailRoute` 里
  `SALE_ORDER: '/sale/order'`、`SALE_OUTBOUND: '/sale/outbound'` 指向**不存在的路径**，
  而消费方（`payment-supplier.vue`、`supplier-settlement.vue` 的「来源单号」）按 `${base}/${id}` 拼接
  ⇒ 点「来源单号」实际落 404。已改为 `/inventory/sale/detail`、`/sale/outbound/detail`（真实详情路由）。

**验证**：`scan-col-truncation.ps1`（payable / receipt）**0 offender**（payable 业务场景已不在白名单内仍通过）；
`scan-table-overflow.ps1` 两页 margin 0 / scroll 0 / btnClip 0；四守卫 PASS；无 lint；
浏览器实测：付款「来源」行 `XS-20260919007` 点击 → **`/inventory/sale/detail/281`**；
应付「业务场景」10 行 **0 截断**（显示「委外收货」）。

## 5.11 B10：表头被截修复 + 守卫补盲（2026-09-26）

### 用户报的两个现象与根因
- `/outsource/material-return`「退货金额」显示成 `退货…`、`/outsource/order`「是否缺料」显示成 `是否…`。
- **直接原因**：B5a/B5b 为给「仓库/单号」腾宽，把这两列压到 **70 / 62px**，而 4 字表头实测需 **90px** ⇒ 列名被省略。
- **根本原因（守卫盲区）**：`scan-col-truncation.ps1` 原先**只量 body 单元格**（`.el-table__body td .cell`），
  **完全没量表头**（`.el-table__header th .cell`）⇒ 列宽压到比列名还窄也照报 PASS。

### 守卫修复（永久防复发）
- 探针新增逐列表头测量（`hdrClip` / `hdrNeed`），判定优先级 **HDRCLIP > WRAPPED > CLIPPED**；
  **表头被截 = FAIL，且不可用白名单豁免**（白名单只针对「长内容」，列名不属于内容）。
- 全站扫出 **19 个表头被截的列**（全部是我此前"抵平"式压缩造成的）。

### 列宽 / 列名两条新家规（实测得出）
1. **列宽必须 ≥ 表头需宽**：准确公式 = **列名文字实测宽 + 16（`.cell` 左右内边距）+ 1（边框）+ ≥2（安全余量）**，
   即 **文字宽 + 19**；14px 字号下的档位（已按 2026-09-26 修正，旧值偏大 14px）：
   **2 字 48px、3 字 62px、4 字 76px、5 字 90px**，含斜杠的 7 字约 **110~116px**；**可排序表头再加 14px**（实测排序箭头宽 14px）。
   ⚠️ 旧口径（2 字 62、4 字 90、5 字 104）是把守卫探针的 `need = scrollWidth + 18` 当成了真实需求，
   由此把数据列白挤了 14px/列 —— **列宽一律按"文字宽 + 19"给**，别再套旧档位。
2. **满页（9~10 列、margin 0）优先改「短列名」，不牺牲数据列** —— 列名是我们的字面量，改短不丢数据；
   同理 B6 发票类型、B9 应付业务场景已用此法。

### 19 处的修法
| 页 | 列 | 修法 |
|---|---|---|
| /outsource/order | 是否缺料 | 62→**90**（本页余量 28 ✓ 不动别人） |
| /outsource/order/delivery | 进度（原「收货进度」）、下单/已收/剩余 | 「收货进度」→**进度** 62、下单/已收/剩余 110→**130**；省下的还给「产品」（min90 不变） |
| /outsource/material-order | 下单总数 | 52→**90**；「状态」96→76 抵平 |
| /outsource/material-order/delivery | 进度（原「收货进度」）、往来单位 | 「收货进度」→**进度** 62；「供应商/加工厂」→**往来单位**（需 90）且 min100→**134**（名称+链接需 133）；物料 min56、状态 96 抵平 |
| /outsource/material-return | 退货金额、明细 | 退货金额 70→**90**；「退货/送修内容」→**明细**（7 字需 124px 给不出） |
| /dev/project（4 张表） | 原机、改配 | 「原机尺寸」→**原机**、「改配尺寸」→**改配**（2 字需 62 ≤ 64 ✓ 零成本，不动项目名称列） |
| /inventory/stock-take + /outsource/material-stock-take（共用面板） | 明细、差异 | 「明细行数」→**明细**、「差异行数」→**差异**（62 ✓ 零成本，保住仓库 189） |
| /finance/payable | 类型 | 「主体类型」→**类型**（70 ✓；页签已按主体类型筛选） |
| /finance/invoice | 对方、不含税 | 「对方单位」→**对方**（62 ✓）、「不含税金额」→**不含税**（76，宽 74→76） |
| /analysis/customer | 占比、订单、应收 | 金额列 78→72（small 档金额只需 ~66）腾宽；占比 56→**76**、订单数→**订单** 56→**76**、应收余额→**应收** 76 |

### 验证
- `scan-col-truncation.ps1`（含新表头检查）：**59 路由 / 58 表 → 0 offender**（表头零截断、非白名单列零截断、零换行）；
- `scan-table-overflow.ps1`：58 表 **0 越界**（margin/scroll/btnClip 全 0）；
- 四守卫 PASS；无 lint；
- 浏览器复现验证：`/outsource/material-return` 表头 = `退货单号 | 供应商 | 出库源仓 | 明细 | 退货金额 | 退货日期 | 状态 | 操作`；
  `/outsource/order` = `单号 | 模式 | 加工厂 | 产品 | 计划完成 | 最近收货 | 是否缺料 | 状态 | 操作`（均无截断）。

## 5.12 B11：表头「差 1px」全站排查与修复（2026-09-26）

### 用户报的现象与根因（第二次同类问题）
- `/outsource/material-order`「已收」显示成 `已…`。实测：列宽 44 ⇒ `.cell` 内容框 **27px**，而「已收」两字在 14px 下**需 28px**
  ⇒ **正好差 1px** 就触发省略号。
- **守卫盲区（第二处）**：B10 加的表头判定是 `scrollWidth − clientWidth > 1`（**只认 ≥2px 溢出**）⇒ 1px 溢出被放过；
  body 判定同样是 `> 1`，`analysis/customer` 金额列 1px 溢出也被放过。
- 另外发现 **`analysis/customer` 的金额列是 `sortable`**：表头除文字外还常驻 **14px 排序箭头**，
  而 B7 给的是"只按文字算"的 72px ⇒ 表头（文字 42 + 箭头 14 + 内边距 17 = 73）差 1px 被省略。

### 守卫加严（永久防复发）
1. 表头溢出阈值 `> 1` → **`>= 1`**（1px 也算截断）；
2. 新增**按实测文字宽**的判定：页面内用同字体隐藏 span 量出列名文字宽，要求 **内容框 − 文字宽 ≥ 2px**
   （`HDRTIGHT`，不可白名单豁免），并在报告里回报 `hdrText / hdrBox / hdrSlack`；
3. body 阈值同步 `> 1` → **`>= 1`**（非白名单列）。

### 全站结果：58 个有表格页 → 11 处 offender（6 页），全部修好
| 页 | 列 | 根因 | 修法 |
|---|---|---|---|
| /outsource/material-order | 已收 | 44px 差 1px（B3 抵平造成） | 44→**48**；同时**下单总数 90→76**（B10 按偏大公式多占 14px）⇒ 省下 10px 回补**物料名称 90→100** |
| /inventory/return-sort | 单位 / 可整理 | 44（差 1px）/ 60（余量 1px） | 44→**48**、60→**62** |
| /inventory/return-sort | 来源单据 | 正文是**链接按钮**，最长单号需 131px ⇒ 列宽要 150（142 也截断 6 行，此前未被识别） | **150**；宽度由 来源日期 106→98、SKU 110→100、状态 72→68 让出 |
| /finance/invoice | 税率 / 关联单号 | 44（差 1px）/ 74（余量 1px） | 44→**48**、74→**76** |
| /finance/receipt | 主体类型 | 72 差 1px（4 字需 56+17） | 72→**76**；金额 92→88 抵平（守 956 容器） |
| /inventory/product-stock | SKU / 分布仓库 | SKU：最长 SKU 正文 93px，min88 ⇒ 10 行全截断；分布仓库：4 字表头需 75，74 只剩 1px | SKU 88→**98**；分布仓库 74→**82**，品牌 108→100 让宽 |
| /analysis/customer | 销售额/退货额/利润率 表头 + 销售额/净额 金额正文 | **sortable 箭头 14px** 未计入；金额正文溢出 1px | 6 个金额列 72→**76**；占比/订单/应收 76→**62**、最近成交 98→**92**、操作 54→**48** 回收（声明合计 902 ≤ 948） |

### 验证（本轮口径升级）
- `scan-col-truncation.ps1`（加强版）：**59 路由 / 58 表 → 0 offender**（表头 0 溢出、表头余量全 ≥2px、非白名单列 0 截断）；
- `scan-table-overflow.ps1`：58 表 **0 越界**；四守卫 PASS；
- 浏览器**断言式**验证：6 个修复页逐表头断言 `内容框 − 文字宽 − 排序箭头 ≥ 2` → **ALL_HEADERS_OK**。

## 5.13 B12：加工退货 / 物料退货 改**三级菜单 + 页签**（2026-09-27，用户口径）

### 用户口径（原话要点 + 命名推荐已确认）
「加工退货 / 物料退货 太复杂，改三级菜单」：加工退货下 3 个叶子（关联退货 / 未关联退货 / 维修退货），
每个叶子带页签；「未关联退货」还要看得出**工厂把货还回来没有、还了多少**；页签名要专业一点。

### 命名定稿（方案 A）
- 加工退货（**目录**）→ **关联退货 / 无单退货 / 维修退货 / 加工返回单**
- 物料退货（**目录**）→ **退料 / 维修退货**
- 页签：**有效单据 | 已作废单据**（单据型）/ **待返回 | 已返回完 | 已作废**（返修型，默认待返回），标签后带**数量角标**
- 列：`退货/已返回`（与维修退货既有「送修/已返回」同序同口径）；ORB 列表新增 `来源退货单`
- 状态与进度**分开**：状态列恒显示 草稿/已审核/已作废，「已结案」作为附加 tag 并列（原先它替代"已审核"）

### 数据模型/接口（本次唯一的结构改动）
- `outsource_return_back` 加 **`source_delivery_id`**（来源无单加工退货记录）+ 索引；新库见 `schema.sql`，
  老库走 `DataInitializer.migrateReturnBackSource()`（addColumnIfMissing，幂等）
- ORB 创建/修改可传 `sourceDeliveryId`；**三重校验**：来源单必须存在 / 已审核 / 不关联加工单 / 同厂同产品同规格；
  **按单防超返**（Σ已审核返回单 + 本次 ≤ 该单退货量）——创建时校验，**审核时再复核一次**
  （堵住"多张草稿各自不超、审核后累计超"的窗口）
- 台账 `return-defect/page` 新增：`status` 支持**逗号多值**、`returnProgress=PENDING|DONE`（SQL 子查询按来源单聚合，分页不下错）、
  `factoryId/productId/qualityType`（供 ORB 绑定来源时定位）；行内新增 `returnedQty/unreturnedQty/returnProgress`
- 加工退货新增 **`PUT /outsource/order-delivery/{id}/cancel`**（DRAFT→CANCELLED，取代草稿物理删除；DELETE 保留供脚本）
- 维修退货/物料退货 `page` 新增 `statuses`（逗号多值）+ `progress=OPEN`（待返回**含草稿**）/`RETURNED`（已返回完）
- ⚠️ 踩坑：**成品**流水的 delivery id 落在 `related_bill_id`（`related_delivery_id` 是**物料**流水专用列）

### 前端实现（关键取舍）
- **一个工作台组件被多个叶子共用**，按 `route.path` 判叶子（`views/outsource/return-order/index.vue` 的 `leaf`、
  `views/outsource/material-return/index.vue` 的 `leaf`）——避免把 ~700 行已验证的列表/弹窗/动作复制 4 份；
  筛选行随之大幅简化（「关联加工单 / 状态 / 返回进度」三个下拉全部由**叶子 + 页签**表达）
- 页签角标用 `pageSize=1` 只读 `total`（零后端改动）
- ORB 弹窗新增「来源退货单」下拉（必填；按 加工厂+产品+在厂规格 拉未返回完的来源单）

### 菜单/权限（`DataInitializer`）
- 新增目录 **419 加工退货** / **423 物料退货**；叶子 **420 无单退货 / 421 维修退货 / 422 加工返回单 / 424 维修退货(物料)**；
  408/411 由 menu **改父级**（同步改名 关联退货 / 退料），route_path 不变 ⇒ 旧链接/脚本不废
- 叶子 `perms` 必须"自带其 API 需要的码"（**目录 perms 会被 initMenuPerms 强制清空**）：
  408/420 = `outsource:order-delivery`；421/422 = `outsource:return-order`；424 = `outsource:material-return`
- 存量库按**旧叶子继承**补授（`sys_role_menu where menu_id=408 → 419~422`，`=411 → 423/424`），幂等
- `SideMenu.vue` 补登记 `CircleClose / Refrigerator` 图标（种子早就写了这两个名字，映射表一直缺 ⇒ 一直退化成默认图标）

### 验证
- **API 实证**：ORB 绑定来源 202 后审核 → 台账 202 行 `returned=4 / unreturned=6 / PENDING` ✓；
  超量创建被拦（核销超量）；按单复核拦"多草稿累计超"；来源不存在/规格不符/已关联加工单 均被拦；清理后回到 `returned=0`
- **浏览器**：4+2 个叶子渲染正确（页签含角标、`退货/已返回` 列、ORB `来源退货单` 列）；**侧栏三级菜单正确展开**
- 守卫：6 叶子 `scan-col-truncation` **0 offender**（顺带修 台账单号 130→158 装不下 GTW- 单号、白名单补 供应商/维修供应商）；
  全站 `scan-table-overflow` **58 页 0 越界**；四守卫 PASS

### 脚本同步（2026-09-27 已完成，全绿）
`ui-e2e-zh.json` 补页签键（`tab_leaf_effective/void/pending/returned`）；下列 UI 脚本把"开旧页面 + 点页签"改成
**直达叶子 URL**（页签点击在带数量角标后不再可用，见下条踩坑）：
`ui-e2e-16`（68/0，含 S5/S7 改用 `ClickTabIdx` 切「已返回完」）、`ui-e2e-15`（53/0）、`ui-e2e-14`（42/0，
改为两个叶子各自断言页签 + 各 1 个「新增」）、`ui-e2e-p5b`（10/0）、`ui-e2e-p8a`（9/0）、
`ui-e2e-1-nav`（新增 4 个叶子路由，`bad=0 total=69`）；`ui-e2e-12` 无需改（S1 早已 `if($false)`、S2 路由不变）；
`ui-p5a` 已标 OBSOLETE BY DESIGN（早于本次失效，未动）。

### 两条踩坑（复用价值高）
1. **页签点击不能再用文本精确匹配**：`ClickTab` 是 `innerText === 文本`；本次给页签加了数量角标
   （`待返回3`）⇒ 精确匹配必失败。**点页签一律用 `ClickTabIdx <0基序号>`**；断言用 `BodyHas`（子串）✓。
2. **顶部标签栏不允许同名标签**（`ui-e2e-1-nav` 的 `dupTabBad` 不变量，本次抓到 47 页）：
   加工侧与物料侧都有「维修退货」⇒ 标签栏出现两个同名标签（用户无法区分）⇒
   两个叶子各自带对象前缀去重：**成品维修退货**（menu 421）/ **物料维修退货**（menu 424）
   （菜单名与 `router meta.title` 都改，标签名取 `meta.title`）。
   ⚠️ 新增三级菜单时**先想好叶子名全局唯一**，否则这条不变量会在 47 个页面上一起报红。

## 5.14 物料侧再按「关联物料订单 / 未关联」拆叶子（2026-09-27，用户口径）

### 结构（与加工侧完全对称）
`物料退货`（目录 423）→ **关联退料**（menu 411，`/outsource/material-return`，MRH-，由物料收货页发起）/
**无单退料**（menu **425**，`/outsource/material-return/unlinked`，MRW-，手工发起）/
**物料维修退货**（menu 424，REPAIR，**不拆** —— 与加工侧一致）。

### 关键实现（一个条件就够）
- 关联/无单**同 `returnType=REFUND`**，只靠 `linked` 区分 ⇒ 后端 `page(...)` 加一个参数：
  `WITH_ORDER → material_order_id IS NOT NULL`、`WITHOUT_ORDER → IS NULL`（**与加工侧 return-defect 的 linked 同口径**）。
- 实测条数（拆分当天）：REFUND 关联 3 / 无单 11；REPAIR 18（不筛）。
- 菜单补授：425 走既有「从旧叶子继承」SQL（`IN (423,424,425) WHERE menu_id=411`）+ admin 列表 ✓ 存量库无需手工。
- 列集按叶子（三套都不超 948）：**关联叶子** = 单号 158+对方 134+**关联物料订单 158**+明细 min80+金额 90+日期 96+状态 74+操作 132 = 922
  （多一列 ⇒ **不列「出库源仓」**，镜像加工侧关联叶子）；**无单叶子** = 拆分前原样 944（含源仓 189）；**维修叶子** = 919。
- 新建入口：三个叶子各一个「新增」（关联/无单都进 `add?returnType=REFUND`，维修进 `?returnType=REPAIR`）。

### 脚本同步
`ui-e2e-14`（S1 断言三叶子 + 关联叶子有「关联物料订单」列、无单叶子没有 → 46/0）、
`ui-e2e-p5b`（按 `type + material_order_id` 选叶子；其 REFUND 单不挂订单 ⇒ 落**无单**叶子 → 10/0）、
`ui-e2e-1-nav`（**70 路由 0 bad**，`dupTabBad=0`）、`ui-e2e-11`（36/0，从收货页发起关联退料 ✓ 证明关联叶子真链路）、
两个扫描守卫的**路由清单补上全部叶子**（`scan-col-truncation` / `scan-table-overflow`，原来只扫两级页面）。

## 5.15 维修退货详情「送修出库仓」显示成仓库 ID（2026-09-27，用户实测）

### 根因（一行之差，属"静默降级"）
`views/outsource/return-order/detail.vue` 写成 `label-key="(row:any)=>row.warehouseName"` —— **漏了绑定冒号**。
Vue 把箭头函数当**静态字符串**传给 RemoteSelect ⇒ `getLabel(o)` 取 `o["(row:any)=>row.warehouseName"]` = undefined
⇒ `el-option` 没有 label ⇒ Element Plus 回退**显示 value**（下拉与回显全变成仓库 ID，实测显示 `73`）。
全站扫描 `\slabel-key="\(`：**仅此 1 处**；其余静态 label-key 都是真实字段名（materialName / warehouseName / typeName / code）✓。

### 三处一起补齐（前两处是根因，第三处是完整性）
1. 改为 `:label-key="(row:any)=>row.warehouseName"`；
2. 后端 `OutsourceReturnOrderServiceImpl.detail()` 补 `warehouseName`（此前只回 `warehouseId`；
   **物料退货详情的 detail() 早就回了** —— 同类页面互为参照，缺字段一眼可查）；
3. 只读区补一项：维修退货=「**送修出库仓**」/ 加工退货=「**扣减成品仓**」
   （此前只读区**完全没有这一项**：只有草稿编辑态有下拉 ⇒ 审核后就看不到从哪个仓出库了）。

### 新守卫（防复发，已负例验证）
`web-check.ps1` 增加 **[label-key守卫]**：禁止 `\slabel-key="\(` —— 函数型 label-key 必须写成 `:label-key="..."`。
负例实测：临时放入一个含该写法的 .vue ⇒ 守卫 FAIL 并点名「文件:行」✓；删除后 PASS ✓。

### ⚠️ 工具脚本有**两份**（运维注意，本次踩到）
`web-check.ps1` 同时存在于**工作区根**（`20260710123705\web-check.ps1`，**未纳入 git**，只有 3 个守卫）
与 **`beichen-erp/tools/regression/web-check.ps1`**（**受版本控制**，有 5 个守卫：+枚举守卫 +本次 label-key 守卫）。
根目录那份的 `$wsRoot = $PSScriptRoot` ⇒ 扫的是工作区根，那里只有 48 个 ui-e2e 脚本（仓库内是 55 个）⇒
**守卫会漏检**。⇒ **跑守卫一律用仓库内那份**；新守卫也只往仓库内那份加（否则不进版本控制）。

## 5.16 维修返回「实际用料」范围收口（2026-09-27，用户口径）

**用户要求**：登记维修退货时不能列出全部物料 —— **成品**要从**它的 BOM** 里选，**物料**要从**它的子物料**里选。
**落地口径（已确认）**：硬限制 + 允许用料行留空；加工侧用**该产品行的 BOM 快照**（不用产品最新 BOM）；
默认用量 = 用量 × 本次返回数量；**数量仍可超 BOM**（只收口"范围"，原"可超 BOM、不做比对"的数量口径不变）。

| 侧 | 候选来源 | 端点 |
|---|---|---|
| 加工退货(REPAIR) 成品 | `outsource_return_order_product.bom_snapshot_id` → `bom_snapshot_item` | `GET /api/outsource/return-order/{id}/repair-material-candidates` |
| 物料退货(REPAIR) 物料 | `outsource_material_component`（父=送修行物料） | `GET /api/outsource/material-return/{id}/repair-material-candidates` |

- 候选**同一份逻辑**供端点与提交校验复用（`materialCandidatesOf`），落库校验不通过 → 明确报错（含物料名/原因/可选数）；
- 前端两个弹窗：用料行改**本地候选下拉**（`el-option-group` 按来源分组：`BOM·产品X` / `子物料·物料Y`），
  选料后默认数量 = 用量 × 该来源行的本次返回数量，同一物料只允许一行；池为空 → 警示「只登记返回、不填用料」+ 补救路径；
- 实测（库内真数据）：加工侧 doc 265 → 3 个 BOM 候选；物料侧 doc 91 → 2 个子物料候选；
- 守卫：`verify-repair-material-scope.ps1`（正例 + 负例：池外物料必被拒、且不产生任何库存/记录变动）**PASS**。

## 5.17 ⚠️ 修掉「在厂行只减不还」（2026-09-27，本次验证时抓到）

**现象**：`ui-e2e-15` S8 失败 —— 撤销维修返回后「反审核」被拒：`在厂物料（维修送修）不足：物料ID=61 当前在厂 1、需核销 3`。
**根因**：`OutsourceMaterialReturnServiceImpl.cancelRepairReturn` 把「恢复在厂 `MATERIAL_REPAIR` 行」
关在 `if (!mats.isEmpty())`（有实际用料）里，而登记侧 `allocateOnSiteRepair` 是**无条件**核销的
⇒ **没填实际用料的维修返回，撤销后在厂行永久少记**，该单随后反审核必被拒（在厂账目 vs 单据永久不一致）。
**加工侧无此问题**：它按 ALLOC 明细行恢复，而 `allocateOnSiteRepair` 每次都会写 ALLOC 行 ⇒ 不受 materials 影响。
**修法**：把在厂行恢复移到 `if (!mats.isEmpty())` **之外**、无条件执行（`supplierWhId` 为 null 时跳过 ✓ 与登记同口径）。
**验证**：新增 `verify-repair-cancel-onsite.ps1`（登记无用料 → 撤销 → 断言在厂行复原）**PASS**；
`ui-e2e-15` 从 47/6 回到 **53/0** ✓；另修复 dev 库被漏减的在厂行（`warehouse_stock` id=244 手工修正），
并把两次失败运行遗留的 AUDITED 单（110/111）反审核 + 作废收尾。

### 5.17b 二修：同一处的**反向**漏记（同日，做存量对账时发现）

**发现**：5.17 的修法让撤销**无条件**恢复在厂，但**旧单**（审核于 2026-09-25 物料形态化**之前**）在厂行从未
+ 过送修量 ⇒ 登记返回时 `allocateOnSiteRepair` 会**跳过**核销腿（在厂为 0 时）⇒ 撤销若仍无条件恢复，
就会**凭空给在厂 +qty**（负例实测：把标记强制回"无条件恢复"，在厂 0 → 1）。
**修法（打标，不猜）**：`outsource_material_return_repair` 新增 **`onsite_leg`**（1=登记时确实核销过在厂 /
0=旧单跳过）：登记时 `allocateOnSiteRepair` 返回 boolean，跳过则把本行插入的记录改标 0；撤销**仅当
`onsite_leg != 0`** 才恢复（存量列默认 1 = 历史绝大多数确实核销过）。新库 `schema.sql` 建列，存量库走
`DataInitializer.migrateMaterialRepairOnsiteLeg()`（幂等 `addColumnIfMissing`）。

### 存量对账 `reconcile-repair-onsite.ps1`（本次新增；默认只读，`-Apply` 才改数）

- 口径：per（公司 + 供应商委外仓 + 物料）`应有 = Σ送修 − Σ现存返回记录`，对比
  `warehouse_stock form=MATERIAL_REPAIR` 的实际量；`diff>0` = 漏减（少记）/ `diff<0` = 无单据支撑的多记。
- **形态化判定不能只看单据**：旧单从未入过厂 ⇒ 用「该单+物料在**库存流水**里的 `MATERIAL_REPAIR_STOCK_IN`
  腿合计 ≥ 送修量」判定为形态化单，否则排除并打印 EXCLUDED（否则会把旧单的送修量误当成应有在厂）。
- 附带诊断：`Σ库存流水 ≠ 库存行数量` ⇒ **有人改数没留痕**（本次就查出此前手工把 −4 改成 1 的那 5 个单位）。
- `-Apply` 的每一条改动都写一条库存流水（`RECON-<日期>` + 中文备注），否则改数在流水里不可见。
- dev 实测：物料33 一致 / 物料61 多记 1 ⇒ 3 条语句修正（补记人工改数 + 修正差额）→ 复核
  `diffs=0 unlogged-edits=0` ✓；负例对照造出的幽灵单位也被它抓到并清掉。
- ⚠️ 这是**通用工具**不是一次性补丁：以后每次动在厂账的批次收尾都跑一次，返回 `RESULT DIFF / PASS`。

### 守卫升级（`verify-repair-cancel-onsite.ps1` 重写为双案例自造 fixture）

- **A（形态化单，原 bug）**：自己建单 → 审核（在厂 +3）→ 登记无用料返回（在厂 2）→ 撤销（回 3，**A 断言**）
  → 反审核（回 base，顺带覆盖曾经硬报错的路径）→ 作废清理；
- **B（旧单，反向漏记）**：挑「送修从未进在厂的 AUDITED 旧单」→ 登记（核销腿跳过、记录标 0、在厂不动）
  → 撤销（在厂仍不动，**B 断言**）；负例对照证明 B 在旧行为下会 FAIL（造出幽灵 +1）。

## 5.18 表单 label 也有一档「宽度临界」（2026-09-27，用户实测）

**现象**：委外加工退货**详情**的可编辑态，「送修出库仓」的"仓"掉到第二行。
**根因**：`--app-label-width: 90px` 是按 4 字标签收口的；**默认字号（14px）**下
5 字标签 = 70px ＋ 必填星号 ＋ 冒号 ≈ 87~90px，**正好卡在 90 边缘**（headless 实测"勉强单行"，
字体略宽的机器就换行）；6 字标签（关联物料订单 = 84px）更是必然换行。
⚠️ 新增页多为 `size="small"`（12px）⇒ 5~6 字也放得下，所以只有**详情页**暴露。
**修法**：新增令牌 `--app-label-width-lg: 104px`，用在两个**默认字号**的草稿表单
（`outsource/return-order/detail.vue`、`outsource/material-return/detail.vue`）。
**实测**：修前 label 盒宽 90（临界）/ 修后 **104**，高度仍 32（单行）✓；
两边（5 字 / 6 字）均验证；临时建的 REPAIR 草稿已删除，未留数据。
**经验**：新增 **默认字号**表单时，label ≥5 字就上 lg 档；`size="small"` 的表单 90px 够用。

## 5.19 无单加工退货带上 BOM 快照 + 新增入口改独立页面（2026-09-27，用户口径）

### 背景（关键前提）
无单加工退货**不是一张退货单**，而是「成品收货台账」`outsource_order_delivery` 里一条
`DEFECT_RETURN` 记录（没有产品明细子表、原先没有快照列）⇒ 想让"返回时按 BOM 选料"，
只能**给该记录加一列**。它的"返回"是 `outsource_return_back`（加工返回单），
已有 `source_delivery_id` 指回来源单 ✓ 落点齐全。

### 用户确认的四条口径
① 落库（含 1 列 DDL）；② 默认解析 = **该产品在该工厂「最近一次被加工单用过的快照」**（不是版本最大）；
③ 返回单用料**硬限制**；④ 存量兜底 = 按产品+工厂**现场解析**（池空则允许留空，不卡流程）。

### 实现
- DDL：`outsource_order_delivery.bom_snapshot_id`（`schema.sql` 已同步；存量库手工 ALTER ✓ 文件头有约定）。
- 建单 `returnDefectNoOrder`：入参可带 `bomSnapshotId`（人工换版本）→ 否则 `recentOrderSnapshotId(factoryId, productId)`
  → 都拿不到则留空；台账/详情返回 `bomSnapshotId/bomVersion/bomKind`。
- 快照候选端点：`GET /api/outsource/order-delivery/product-snapshot-options?factoryId=&productMasterId=`
  （该产品在该工厂用过的快照，按最近使用的加工单倒序 ⇒ 第一项即默认）。
- **加工返回单**：`GET /api/outsource/return-back/material-candidates?...`（另有 `/{id}/material-candidates`）
  = 来源单快照 → 现场解析兜底 → 空；提交时 `assertMaterialsInPool` 硬校验；**池空时允许 items 为空**
  （`itemsOf` 不再直接报"用料明细不能为空"，改由调用方按池判定），审核时料款应收按 0。
- 前端：**新增无单加工退货由弹窗改独立页面** `/outsource/return-order/unlinked/add`
  （`unlinked-add.vue`：选完加工厂+产品自动解析并展示「BOM 快照 vN（研发BOM，N 项料）」+ 可换版本；
  无快照时黄条提示）；加工返回单弹窗用料改用候选下拉（占位用量=单套用量×返回数量、同一物料一行、空池黄条）；
  台账详情显示「BOM 快照 vN / 未绑定」。
- **不动**：P1-1「无单退货不拆料还料、不冲应付」✓（快照只用于返回时用料的**可选范围**）。

### 验证
`verify-no-order-back-material-scope.ps1`（新，纯 ASCII，自造自清）：建单不传快照 → 落库 = 选项端点第一项 ✓；
显式传 → 原样落库 ✓；池外物料 → 500「只能从**该加工退货单的 BOM 快照**里选…（可选物料 3 个）」且零落库 ✓；
池内物料 → 通过 ✓；清理无残留 ✓。`verify-delivery-menu.ps1` §⑨b-2 增加「新增 → 独立页面 + 含 BOM 快照字段 + 无弹窗」断言。

## 5.19 「维修退货」页命名错位 + 页签名跟随类型（2026-09-27，用户实测）

**现象**：从「成品维修退货」叶子点「新增」，页签/页头却写着「新增委外加工退货」（名不符实）。
**根因**：这些路由 `meta.title` 是**历史名**（本页早先主营加工退货）——`新增/编辑委外加工退货`、`委外加工退货详情`、
`新增/编辑委外物料退货`、`委外物料退货详情`；而两张 add 页现在**默认就是维修退货**（加工侧 form.returnType
注释写明「加工退货已统一到成品收货办理」）。
**修法（页签标题跟随实际类型，不动落库类型）**：
- add 页（`return-order/add.vue`、`material-return/add.vue`）：`syncTabTitle()` 在 `onMounted` 里把页签名改成
  「新增成品维修退货 / 新增物料维修退货」（DEFECT/REFUND 仍用旧名），页内切类型时 `watch(isRepair)` 同步（layout
  在路由变化时按 meta.title 打开页签，页面在其后覆盖）。
- detail 页（两张）：`pageTitleText = isRepair ? '成品/物料维修退货详情' : '委外…退货详情'`，
  同时用在 `PageShell :title`（页头）与卡片头，并在数据回来后 `watch` 同步页签名（**故意不写 immediate**：
  加载中先沿用 meta 名，对 DEFECT/REFUND 单本来就是对的）。
- 回归实测：加工 REPAIR=成品维修退货详情 / DEFECT=委外加工退货详情；物料 REPAIR=物料维修退货详情 /
  REFUND=委外物料退货详情（页签 = 页头 = 卡片头三处一致）。

### ⚠️⚠️ 踩坑（高复用价值，本次把自己打白了一次）：`watch()` 会**立刻求值**源，触发 TDZ 白屏

`watch(pageTitleText, cb)` 在 **setup 阶段就会读一次** `pageTitleText.value`（**即使没有 `immediate`**）。
`pageTitleText → isRepair → showDraftForm / form` 而这些 `const` 声明在**后面** ⇒
`Cannot access 'showDraftForm' before initialization` ⇒ 整个应用白屏。**特征非常隐蔽**：
- 模块 **HTTP 200、编译通过**（Vite 转换没问题）、**没有 vite error overlay**、路由也正常解析；
- `document.querySelector('#app').innerHTML.length === 0`（整个布局都没了）；
- 只有 `agent-browser console` 里能看到 `Unhandled error during execution of setup function`（`errors` 只给空 ✗）。

⇒ 本仓库大量使用「computed 互相引用 + 声明顺序自由」的写法（懒求值通常没问题），**但只要被 `watch` 之类
立即求值，就必须把声明顺序理顺**（放在被依赖常量之后，或改到 `onMounted` 里做）。
⇒ 定位手法：`git stash push -- <两个文件>` 对照复测（不带改动时同一 URL 正常渲染 ⇒ 确定是自己改的），
然后 `agent-browser errors / console` 看真实报错。

### 浏览器标签页标题也跟随类型（同日，用户口径）
后缀 ` - 北辰ERP管理系统` 原先 **5 处各写一遍**（router 守卫、供应商/供货商详情、采购退货新增 ×2、移仓新增 ×2），
现收敛到 **`@/utils/pageTitle.ts`**：`APP_TITLE_SUFFIX` + `applyPageTitle(name)`（router 守卫与上述 4 处都改用它）。
动态标题页在 `syncTabTitle()` / `watch(pageTitleText)` 里**并排调** `tabStore.updateTabTitle(...)` 与
`applyPageTitle(...)` ⇒ **页头 = 顶部页签 = 浏览器标签页** 三处一致。
`verify-detail-render.ps1` 已把三处都纳入断言（实测 `成品维修退货详情 - 北辰ERP管理系统` 等）。

> ⚠️ 小坑：把一个**纯 ASCII 的 .ps1** 加入中文断言后，PowerShell 5.1 会按 ANSI 解析而**语法报错**
> （`BOM守卫` 只扫 `ui-e2e-*.ps1`，`verify-*.ps1` 不在其内）⇒ 新增中文断言后要**手工补 UTF-8 BOM**。

### 新守卫 `verify-detail-render.ps1`（补"编译/HTTP 守卫"的盲区）
5 个详情页（加工 REPAIR / 加工 DEFECT / 物料 REPAIR / 物料 REFUND / 加工单详情作对照，id 从库里取最新）：
断言 `#app` 真的渲染（innerHTML > 1000，白屏是 0）**且**控制台无 `execution of setup function`。
本次实测 PASS（并顺带断言了上面四类页头标题）。这类"整页白屏"以前对守卫完全隐形。

## 5.20 「加工返回单」叶子下线 → 在无单退货详情页登记返回（2026-09-27，用户口径）

**用户口径**：「加工退货的加工返回单多余了，和成品维修退货一样，在详细里面登记返回就行」。
**结论**：A（`outsource_return_back`）与 B（`outsource_return_order_repair`）底层是**同范式两套实现**
（核销在厂 + 回仓 + 实际用料 + FIFO 成本），A 只是多一条**对加工厂的赔料应收**（工厂责任）与
**来源绑定**（`source_delivery_id` → 无单退货记录）。⇒ 不重写账务，只把**入口搬家 + 降级为登记记录**。

### 落地（P1 入口搬家 / P2 下线叶子 / P3 脚本同步 一批完成）
- **后端**（3 个原子端点，挂在 `/api/outsource/order-delivery` 前缀 ⇒ 权限随页面 `outsource:order-delivery`，
  否则只有台账页权限的角色点「登记返回」会 403）：
  `POST /{id}/return-back`（= create + audit 一个事务，**登记即生效**，来源单由路径强制绑定）、
  `DELETE /return-back/{recordId}`（= unAudit + deleteDraft，逆回后删除）、
  `GET /{id}/return-backs`（返回记录列表）、`GET /{id}/return-back-material-candidates`（用料候选）。
  沿用 `OutsourceReturnBackService` 既有 `create/audit/unAudit/deleteDraft` + `rowOf()` 抽出行构造。
- **前端**：`views/outsource/defect-return/detail.vue`（无单退货记录详情）加「登记返回」按钮 + 弹窗
  （只读上下文 = 来源单/工厂/产品/在厂规格；表单 = 返回数量默认未返回量 / 回仓仓库 / 回仓品质默认沿用退货规格 /
  实际用料候选 / 日期 / 备注）+「返回记录」表（逐条撤销）+ 顶部「返回进度 退货/已返回」——
  与维修退货详情页的「登记维修返回」完全同构。`return-order/index.vue` 的 BACK 叶子整体删除
  （列表/两个弹窗/~190 行脚本）、叶子类型去掉 `BACK`、台账文案改「在**本记录详情页**登记返回」。
- **菜单/权限**：422「加工返回单」按本仓惯例**不再 upsert + 统一置 visible=0**（`DataInitializer` 的下线名单
  与 admin/跟单专员授权、继承补授 SQL、perms 表全部去 422）；前端路由 `/outsource/return-back` →
  **重定向** `/outsource/return-order/unlinked`（老书签不吃 403；页签身份随后者 ⇒ 不会产生同名重复页签）。
- **数据**：`outsource_return_back` 表与 `/api/outsource/return-back/*` 端点**保留**（存量查询 + 回归脚本仍用）。

### ⚠️ 顺带修掉一个**老缺陷**（登记即生效把它暴露了）
`audit()` 里有一句**无条件**要求至少一行实际用料；而 `create()` 早已按「可选池为空 ⇒ 允许只登记返回、不填料」
放过 ⇒ **空池草稿永远审核不了**（老流程是"保存草稿 + 去列表审核"两步，用户看到的是"审核失败"）。
现 `audit()` 与 create/update 同口径（池非空才要求用料；池空则成本结转与料款自然按 0 走）。

### 验证（全绿）
`verify-return-back-in-detail.ps1`（新，自造自清：登记→在厂 −2 / 回仓 +2 / 赔料应收 UNSETTLED / 记录列表 +
撤销后三腿还原、应收 CANCELLED 且金额清零、记录消失；负例：超返被拒 + 有单记录不能作来源）；
`verify-detail-render.ps1` 纳入「加工退货详情」（新增的登记落点）；`verify-no-order-back-material-scope`、
`verify-delivery-menu`、`ui-e2e-1-nav`（70 路由 bad=0 / dupTabBad=0，含旧地址重定向）、
`ui-e2e-16` 70/0、`ui-e2e-15` 55/0、`ui-e2e-14` 46/0、五守卫 + 6 叶子列截断 0 offender。

> 可选后续（用户没要求、本期未做）：① 若工厂也会把**有单**加工退货的货修好送回，需要一个新口径
> （有单红冲不进在厂 ⇒ 无在厂行可核销）；② `outsource_return_back` 的草稿态字段/端点可再瘦身。

## 5.21 首页「物料仓库」TAB 补齐统计卡片（2026-09-27，用户实测「怎么是空白的？」）

**现象与定性**：不是回归、也不是渲染失败 —— 该 TAB 2026-09-16 新建时**只做了快捷入口容器、零统计卡片**
（原代码注释即"本模块暂无汇总统计卡片（如需物料仓库存量/待处理收发单等统计，另行补充）"），
其余 6 个 TAB 都以 `stat-grid` 4 张卡片开场 ⇒ 对比之下它就是个空白页。
实测证据：面板 `innerText` 仅 59 字符（"快捷入口："+8 颗按钮），`htmlLen=1779`，零 `.stat-card`/`.el-card`。

**修法**（口径按用户"按推荐做"确认）：
- 后端 `DashboardService.materialWarehouseStat()`，挂在 `/dashboard/module-pages` 的 `materialWarehouse` 块，
  **按 perms 过滤**（`MATERIAL_WAREHOUSE_PERMS` 8 个物料仓库页面码，任一命中才返回）；前端零新增请求。
- 返回：`itemCount` / `goodQuantity` / `onSiteRepairQuantity` + `warehouses[]`（分仓：物料数/库存量/在厂）+
  `pendingDocs{materialMove,stockLoss,otherIo,total}`。
- 前端：4 张卡片（品项数 / 总数量 / 在厂维修 / 待处理单据[卡内列三单明细]）+ 「各仓库物料库存分布」表
  （仓库名按**仓库类别**选详情页：委外仓 → `/outsource/warehouse/detail/:id`，自有物料仓 → `/inventory/warehouse/detail/:id`；
  两页 `operate:true` 不会 403）。卡片点击前判 `hasMenu['OutsourceMaterialStock']`，避免小权限账号点出 403。

**口径（三条，都写进代码注释）**：
1. **只算物料行** `material_id IS NOT NULL` —— 委外仓/成品仓同一张表里也放成品行且 `stock_form` 就取默认值
   `'MATERIAL'`（实测自有成品仓 44 行成品行如此）⇒ 只按仓库或形态过滤会把成品算进物料；
2. **仓库范围** = 委外仓（`warehouse_category='OUTSOURCE'`）+ 自有物料仓（`INVENTORY` 且 `AUXILIARY`），
   **不按 `warehouse.status` 过滤**（停用仓里压着的仍是真实库存）；
3. **数量取良品**（`quality_type='GOOD'`）；「在厂维修」= `MATERIAL_REPAIR` 形态、不分品质单列（与物料库存是两笔账）。

**验证**：dev 实测卡片 `7 / 9,559 / 0 / 待处理 1(移仓1,报损0,其他0)`，分仓 6 行（零量仓自动排除）
与 SQL 逐项吻合；浏览器零 JS/API 错误；三处点击（卡片→物料库存详情 / 委外仓行 / 自有物料仓行）URL 与权限均正确。
守卫：`ui-e2e-p14-dashboard-quicklinks.ps1` 新增 **Step 2** ——
(a) **通用不变式**：7 个模块 TAB 每个 ≥1 张 `.stat-card`（专防"空白 TAB"复发）；
(b) 物料 4 张卡标签齐备（新文案键）且值可解析为数字；(c) **汇总 == 明细**（分仓表逐行相加 == 卡片总量/在厂，
两条独立 SQL，相等才说明口径没漂）。P14 由 15 项升到 **24 PASS / 0 FAIL**（含 6 行表数据校验）；
另：`ui-e2e-1-nav`（70 路由 bad=0）、角色走查 15/0、五守卫 + TS PASS。

> 现状已知（**本期按推荐未做**）：物料品质只有 GOOD/DEFECT，卡片与分仓表都取良品口径 ⇒ 物料**不良品件数**
> 在首页无处体现（若需要，加一张卡 + `quality_type='DEFECT'` 聚合即可，改动量约 10 行）。

## 5.22 首页 TAB「经营总览」改名「经营分析」（2026-09-27，用户口径：页签名与菜单名对齐）

**改动**：`views/dashboard/index.vue` 的 `<el-tab-pane label="经营总览" name="overview">` → `label="经营分析"`
（`name`/路由/数据源一律不动）。侧栏对应目录是 **`sys_menu` id=10「经营分析」**（子页 1001「经营概览」/
`AnalysisOverview`）—— 页签名与菜单名从此一致。

**改名口径（本次定的规矩，后续改名照此办理）**：
- **现行命名**（"使用方/区块/样式" 等指代当前 TAB 的注释）→ 一律跟着改：本批同步了
  `styles/index.css`（kpi-help 使用方）、`utils/kpiFormula.ts`（使用方）、`analysis/overview.vue`（4 处"与首页…同源/共用"）、
  `analysis/purchase.vue`（口径同源）与 `verify-material-stock-take.ps1` 的日志文案；
- **历史陈述**（"原为/原先是/导致…空白"）→ **保留**，不改写历史；改名点在注释里留一句
  "2026-09-27 由「经营总览」改"，保证 grep 旧名仍能定位到新名。

**守卫**：`ui-e2e-p14-dashboard-quicklinks.ps1` 新增 **Step 3** —— 断言**首页页签栏**含「经营分析」且**不再含**「经营总览」；
**必须限定在 `.dashboard > .el-tabs > .el-tabs__header` 内**（侧栏也有「经营分析」目录，全文档搜文本会假通过）。
实测页签栏 = `备忘录|经营分析|项目研发|委外加工|物料仓库|进货业务|销售业务|成品库存|财务`，P14 **26 PASS / 0 FAIL**
（Step 2 的 15→24 项 + Step 3 的 2 项）；`verify-material-stock-take`（覆盖该 TAB 与首页待办卡）PASS、五守卫 + TS PASS。

## 6. 进度

- [x] 委外加工 6 页（2026-09-25，含 EntityLinks 组件与两个守卫，提交 `5644f4e`）
- [x] B1 基础数据 + 研发（2026-09-25）
- [x] B2 屏幕资料（2026-09-25，列表精简为 7 列、全部完整）
- [x] B3 采购（2026-09-25，单号/明细可点，明细列改 EntityLinks）
- [x] B4 销售（2026-09-26，单号/客户可点；退货整理顺带修掉详情页既有溢出）
- [x] B5a 成品库存 + 移仓（2026-09-26，9 页：单号/仓库/产品/物料可点 + 仓库分流）
- [x] B5b 物料仓库（2026-09-26，7 页：仓库/主体/物料/单号可点；顺带修掉物料流水的仓库跳转缺陷）
- [x] B6 财务（2026-09-26，10 页：单号/主体可点 + tag 缩 small + 列表短名；顺带修掉付款「付款记录」页签的横向滚动）
- [x] B7 系统 + 分析（2026-09-26，3 页有改动 + 5 页 0 截断零改动；收尾批，全站 59 页守卫全绿）
- [x] B8 白名单收尾（2026-09-26，9 页/11 列改到完整显示，消除约 72 处截断；余 22 列物理无法完整，逐类记录理由）
- [x] B9 两处「需砍列」的收尾（2026-09-26，应付业务场景改短名、收款来源改可点源单；顺带修 SourceBillDetailRoute 失效路径）
- [x] B10 表头被截修复 + 守卫补盲（2026-09-26，19 列表头修好；守卫新增 HDRCLIP 且不可白名单豁免）
- [x] B11 表头「差 1px」全站排查（2026-09-26，58 页 11 处；守卫阈值收紧为 ≥1px + 新增 HDRTIGHT 余量判定；修正偏大 14px 的列宽档位家规）
- [x] B13 物料侧按「关联物料订单 / 未关联」拆叶子（2026-09-27）→ 关联退料 / 无单退料 / 物料维修退货；
      `linked` 参数与加工侧同口径，两个扫描守卫的路由清单补齐全部叶子；nav 70 路由 0 bad、全站 63 表 0 offender
- [x] B14 维修返回用料范围收口（2026-09-27，成品→BOM / 物料→子物料；两个候选端点 + 提交硬校验 + 前端分组下拉）+
      顺带修掉「在厂行只减不还」（撤销对称性，新增 verify-repair-cancel-onsite）
- [x] B15 无单加工退货带 BOM 快照（+1 列 DDL）+ 新增入口改独立页面（2026-09-27；返回单用料范围硬限制、
      存量现场兜底；新增 verify-no-order-back-material-scope；verify-delivery-menu 由 FAIL 修到 PASS）
- [x] B16 在厂账对称性二修 + 存量对账（2026-09-27）：`onsite_leg` 打标（旧单撤销不再凭空 +qty，幂等补列）
      + 新增 `reconcile-repair-onsite.ps1`（只读报表 / `-Apply` 修正且留痕；dev 已清到 diffs=0）；
      守卫升级为双案例 + 负例对照（ui-e2e-15 53/0、ui-e2e-14 46/0、两个 verify 与五守卫全 PASS）
- [x] B18「加工返回单」叶子下线 → 在无单退货详情页登记返回（2026-09-27，用户口径「多余了」）：
      3 个原子端点（登记即生效/逐条撤销/返回记录）+ 详情页按钮/弹窗/进度；菜单 422 置 visible=0 + 路由重定向；
      顺带修 `audit()` 无条件要求用料的老缺陷；新增 verify-return-back-in-detail；
      16: 70/0、15: 55/0、14: 46/0、nav 70 路由 bad=0、守卫全 PASS（详见 §5.20）
- [x] B17 维修退货页命名错位修复 + 页签名跟随类型（2026-09-27，用户实测：新增页/详情页叫"委外…退货"）：
      两张 add 页 onMounted 同步页签名、两张 detail 页按类型取页头/卡片头/页签名；**浏览器标签页标题**同口径
      （后缀收敛到 `@/utils/pageTitle.ts`，原先 5 处各拼一遍）⇒ 页头=页签=浏览器标题三处一致；
      顺带踩中并修掉 `watch()` 立即求值导致的 **TDZ 整页白屏**（新增 `verify-detail-render.ps1` 补盲，
      16: 70/0、15: 55/0、14: 46/0、五守卫 + 详情页渲染守卫全 PASS）
- [x] B12 加工退货/物料退货三级菜单 + 页签（2026-09-27，4+2 叶子；返回进度按来源单聚合 + 草稿作废 + 防超返；脚本层文案同步待办见 §5.13）
- [x] B20 首页 TAB「经营总览」改名「经营分析」（2026-09-27，用户口径：与侧栏菜单名对齐）：
      页签 label 改名 + 现行命名注释同步（6 文件）、历史陈述保留；P14 新增 Step 3 断言页签栏含新名/无旧名
      （限定在首页 tabs header 内，避免被侧栏同名目录假通过）⇒ P14 26/0（详见 §5.22）
- [x] B19 首页「物料仓库」TAB 补齐统计卡片 + 分仓分布（2026-09-27，用户实测「怎么是空白的？」）：
      后端 `materialWarehouse` 聚合块（按 8 个物料仓库页面码过滤）+ 4 卡 + 各仓库物料库存分布表；
      **通用不变式守卫**「模块 TAB 不得零卡片」+ 汇总==明细断言（P14 15→24 项全过）；nav 70 路由 bad=0、
      角色走查 15/0、五守卫 PASS（详见 §5.21）

