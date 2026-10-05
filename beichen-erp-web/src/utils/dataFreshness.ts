/**
 * 数据域失效总线（2026-09-30）
 *
 * 解决的问题：页面被 `<keep-alive>` 缓存（layout/index.vue 只 exclude 3 个新增页），数据只在
 * onMounted 拉一次；一旦别处改了数据，回到页面看到的还是旧数据，必须按 F5 —— 实测三个现象：
 *   ① 产品管理新增后返回列表不显示新行；
 *   ② 新增研发立项期间去建了"驱动IC"物料，返回后改配下拉里选不到（RemoteSelect 会话缓存不失效）；
 *   ③ 详情页改了数据返回列表不更新。
 *
 * 旧做法（27 个 *_DIRTY_KEY，见 api/enums.ts）是**逐页手写** sessionStorage 三连：
 *   写操作处 setItem('1') → 列表页 onActivated 里 getItem === '1' → removeItem + 重拉。
 *   两个结构性缺陷：
 *   a) 覆盖率靠人记得写：产品列表压根没有 key（material/index.vue 只有 onMounted），
 *      物料新增页（outsource/material-info.vue）也不通知任何人；
 *   b) 布尔 + removeItem 只支持**一个**消费者：两个页面共享同一个 key 时，
 *      先激活的那个把标记清掉，第二个页面就漏刷。
 *
 * 本模块把"谁改了 → 谁该失效"收回到**请求层自动完成**（见 utils/request.ts 的响应拦截器），
 * 页面只需在激活时间一句"我这个域变过吗"。设计要点：
 *   1) 版本号**单调递增**（不是布尔），每个消费者各自记 lastSeen ⇒ 多消费者互不影响、无需 remove；
 *   2) 版本存 sessionStorage（与既有 *_DIRTY_KEY 同域同语义：同会话跨刷新保留）；
 *   3) 只在**写请求且业务成功（code === 200）**时失效 —— 失败/校验拦截不刷新；
 *   4) 域依赖用 DOMAIN_DEPS 表达（如销售审核 → 库存/应收），避免"列表更新了但库存页没更新"；
 *   5) 未登记的接口 = 不失效（保守，等同于旧行为），需要时在 URL_DOMAIN 里增量登记。
 *
 * 与 stores/pageGuard.ts 的 markDirty **无关**：那个是"表单改动未保存"守卫，同名不同义。
 */
import { onActivated, onMounted } from 'vue'

/** 数据域：会被“某个页面写、另一个页面读”的数据集合。新增域时同步登记到 URL_DOMAIN。 */
export type Domain =
  // 基础档案
  | 'product' | 'brand' | 'material' | 'materialType'
  | 'customer' | 'supplier' | 'vendor' | 'warehouse'
  | 'outsourceContract'
  // 销售
  | 'saleOrder' | 'saleReturn' | 'saleExchange'
  // 采购
  | 'purchaseOrder' | 'purchaseReturn' | 'purchaseExchange'
  // 委外
  | 'outsourceOrder' | 'outsourceDelivery' | 'materialOrder' | 'materialDelivery'
  | 'outsourceMaterialReturn' | 'outsourceReturnOrder' | 'outsourceDefectReturn'
  | 'outsourceOtherIo' | 'materialStockLoss' | 'outsourceStockTake'
  // 库存
  | 'warehouseMove' | 'materialMove' | 'inventoryOtherIo' | 'stockLoss'
  | 'reclassify' | 'returnSort' | 'stockTake' | 'productStock' | 'materialStock'
  // 财务
  | 'bill' | 'receipt' | 'payment' | 'receivable' | 'payable' | 'payableTransfer'
  | 'expense' | 'invoice' | 'account' | 'cashflow'
  // 研发
  | 'devProject' | 'devMaterial' | 'devBom' | 'devDrawing' | 'devBug' | 'screenModel' | 'phaseTemplate'
  // 系统
  | 'role' | 'user' | 'menu' | 'company' | 'sysParam'
  // 其它（P3-1 覆盖度校验补齐）
  | 'memo' | 'supplierSettlement'

/**
 * URL 前缀 → 域（单一来源）。
 * 口径：只匹配**路径边界**（`/` 或结尾），所以 `/outsource/material` 不会误命中 `/outsource/material-order`。
 * 顺序即优先级：更具体的放前面。
 * 注意传入的是 axios 的 config.url（未经 baseURL 拼接），形如 `/outsource/material/page`。
 */
const URL_DOMAIN: Array<[RegExp, Domain]> = [
  // ---- 基础档案 ----
  [/^\/product(\/|$)/, 'product'],
  [/^\/brand(\/|$)/, 'brand'],
  // 物料（含 /outsource/material-info 弹窗新增、/outsource/material/page 下拉查库）
  [/^\/outsource\/material(\/|$)/, 'material'],
  [/^\/dev\/material-type(\/|$)/, 'materialType'],
  [/^\/customer(\/|$)/, 'customer'],
  [/^\/supplier(\/|$)/, 'supplier'],
  [/^\/outsource\/supplier(\/|$)/, 'vendor'],
  [/^\/(inventory\/)?warehouse(\/|$)/, 'warehouse'],
  // ---- 销售 ----
  [/^\/sale\/order(\/|$)/, 'saleOrder'],
  [/^\/sale\/return(\/|$)/, 'saleReturn'],
  [/^\/sale\/exchange(\/|$)/, 'saleExchange'],
  // ---- 采购 ----
  [/^\/purchase\/order(\/|$)/, 'purchaseOrder'],
  [/^\/purchase\/return(\/|$)/, 'purchaseReturn'],
  [/^\/purchase\/exchange(\/|$)/, 'purchaseExchange'],
  // ---- 委外（靠 (\/|$) 边界区分 material / material-order / material-return / material-warehouse）----
  [/^\/outsource\/material-order(\/|$)/, 'materialOrder'],
  [/^\/outsource\/material-return(\/|$)/, 'outsourceMaterialReturn'],
  [/^\/outsource\/material-warehouse(\/|$)/, 'warehouse'],
  [/^\/outsource\/order(\/|$)/, 'outsourceOrder'],
  [/^\/outsource\/delivery(\/|$)/, 'outsourceDelivery'],
  [/^\/outsource\/return-order(\/|$)/, 'outsourceReturnOrder'],
  [/^\/outsource\/defect-return(\/|$)/, 'outsourceDefectReturn'],
  [/^\/outsource\/other-io(\/|$)/, 'outsourceOtherIo'],
  [/^\/outsource\/stock-loss(\/|$)/, 'materialStockLoss'],
  [/^\/outsource\/stock-take(\/|$)/, 'outsourceStockTake'],
  [/^\/outsource\/contract(\/|$)/, 'outsourceContract'],
  // ---- 库存 ----
  [/^\/inventory\/warehouse-move(\/|$)/, 'warehouseMove'],
  [/^\/inventory\/material-move(\/|$)/, 'materialMove'],
  [/^\/inventory\/other-io(\/|$)/, 'inventoryOtherIo'],
  // ⚠️ P3-1：其他出入库的**后端接口路径是 /inventory/other**，与前端路由 /inventory/other-io 不一致！
  // 只登记路由形式会漏掉全部写请求（新增/审核/反审核/作废共 9 处），表现为"审核后返回列表不刷新"。
  [/^\/inventory\/other(\/|$)/, 'inventoryOtherIo'],
  [/^\/inventory\/stock-loss(\/|$)/, 'stockLoss'],
  [/^\/inventory\/reclassify(\/|$)/, 'reclassify'],
  [/^\/inventory\/return-sort(\/|$)/, 'returnSort'],
  [/^\/inventory\/stock-take(\/|$)/, 'stockTake'],
  [/^\/inventory\/product-stock(\/|$)/, 'productStock'],
  [/^\/inventory\/material-stock(\/|$)/, 'materialStock'],
  // ---- 财务 ----
  [/^\/finance\/bill(\/|$)/, 'bill'],
  [/^\/finance\/receipt(\/|$)/, 'receipt'],
  [/^\/finance\/payment(\/|$)/, 'payment'],
  [/^\/finance\/receivable(\/|$)/, 'receivable'],
  [/^\/finance\/payable(\/|$)/, 'payable'],
  [/^\/finance\/expense(\/|$)/, 'expense'],
  [/^\/finance\/invoice(\/|$)/, 'invoice'],
  [/^\/finance\/account(\/|$)/, 'account'],
  [/^\/finance\/cashflow(\/|$)/, 'cashflow'],
  // ---- 研发 ----
  [/^\/dev\/project(\/|$)/, 'devProject'],
  [/^\/dev\/bom(\/|$)/, 'devBom'],
  [/^\/dev\/drawing(\/|$)/, 'devDrawing'],
  [/^\/dev\/bug(\/|$)/, 'devBug'],
  [/^\/dev\/screen-model(\/|$)/, 'screenModel'],
  [/^\/dev\/phase-template(\/|$)/, 'phaseTemplate'],
  // ---- 系统 ----
  [/^\/system\/role(\/|$)/, 'role'],
  [/^\/system\/user(\/|$)/, 'user'],
  [/^\/system\/menu(\/|$)/, 'menu'],
  [/^\/(system\/)?company(\/|$)/, 'company'],
  // ---- P3-1 覆盖度校验补齐（tools/regression/audit-url-domain-coverage.ps1 报出的漏项）----
  // 注意顺序：/inventory/purchase-return 必须在 /inventory/purchase 之前（具体优先）
  [/^\/finance\/payable-transfer(\/|$)/, 'payableTransfer'],
  [/^\/outsource\/order-delivery(\/|$)/, 'outsourceDelivery'],
  [/^\/inventory\/purchase-return(\/|$)/, 'purchaseReturn'],
  [/^\/inventory\/purchase(\/|$)/, 'purchaseOrder'],
  [/^\/dev\/material-flow(\/|$)/, 'devMaterial'],
  // 2026-10-05 F7-283 修复：`/dev/purchase-item` 是**研发物料主数据**的读写路径，原先登记成 `devProject`，
  //   而物料列表页订阅的是 `devMaterial`（views/dev/material/index.vue）⇒ 改完物料列表不刷新（跨页失效断裂）。
  //   物料主数据与物料流水（/dev/material-flow）同属 devMaterial 域，故改登记到 devMaterial。
  [/^\/dev\/purchase-item(\/|$)/, 'devMaterial'],
  [/^\/dev\/file(\/|$)/, 'devProject'],
  [/^\/settings\/company(\/|$)/, 'company'],
  [/^\/settings\/params(\/|$)/, 'sysParam'],
  [/^\/memo(\/|$)/, 'memo'],
  [/^\/supplier-settlement(\/|$)/, 'supplierSettlement'],
  // ---- P3-2 真实接口前缀补齐 ----
  // audit-url-domain-coverage.ps1 扫**全方法**后报出的写缺口。教训：域表不能只按 views 目录名推断 ——
  // 后端接口前缀与前端路由/目录并不总是一致，漏登记的后果是"写操作不 bump 域" ⇒
  // 下拉不刷新（用户实测：新增客户后销售单页客户下拉找不到），甚至列表本身也不刷新。
  // 新增域或接口后请务必重跑该 audit 脚本。
  [/^\/inventory\/sale\/check-stock(\/|$)/, 'saleOrder'],
  [/^\/inventory\/sale(\/|$)/, 'saleOrder'],
  [/^\/inventory\/outbound(\/|$)/, 'saleOrder'],
  [/^\/inventory\/customer(\/|$)/, 'customer'],
  [/^\/inventory\/purchase-exchange(\/|$)/, 'purchaseExchange'],
  [/^\/outsource\/contract-template(\/|$)/, 'outsourceContract'],
]

/**
 * 域依赖：写 A 也要让 B 失效。
 * 用于“审核/联动写入”场景 —— 例如销售单审核会扣库存、生成应收；采购单审核会增库存、生成应付。
 * P0/P1 先留空（本轮三个现象都不涉及跨域联动）；P3 逐步补全，只写“确实会被联动改动”的域，
 * 否则会退化成“每次回来都刷新”。
 */
const DOMAIN_DEPS: Partial<Record<Domain, Domain[]>> = {
  // ---- 库存联动：单据审核会改库存（★ 后为已实测的业务联动）----
  // 2026-10-05 F7-286 修复：原为 ['productStock'] —— 与 saleOrder 不对称（销售审核标脏 receivable/cashflow，
  //   采购审核却不标 payable/cashflow），于是"采购审核后应付/资金流水页不刷新"。采购审核同样生成应付 ⇒ 补齐。
  //   注：该缺口此前被 F7-284「这些页本来就没接总线」掩盖，两条必须一起修，否则登记完订阅反而露出新缺口。
  purchaseOrder: ['productStock', 'payable', 'cashflow'],   // 采购审核 => 入成品库存 + 生成应付（★ 实测）
  purchaseReturn: ['productStock'],
  purchaseExchange: ['productStock'],
  saleOrder: ['productStock', 'receivable', 'cashflow'],       // 销售审核 => 出库 + 生成应收（★ 实测）
  saleReturn: ['productStock', 'receivable', 'cashflow'],      // 销退审核 => 回库 + 冲应收
  saleExchange: ['productStock'],
  inventoryOtherIo: ['productStock'],         // 其他出入库审核 => 成品库存（★ 实测）
  warehouseMove: ['productStock'],            // 移仓审核：总量不变，但按仓库分布变化
  stockLoss: ['productStock', 'expense'],     // 报损审核 => 减库存 + 生成 LOSS 费用单（★ 实测 source_bill_no）
  stockTake: ['productStock'],
  reclassify: ['productStock'],
  returnSort: ['productStock'],
  // ---- 委外物料/成品 ----
  outsourceOrder: ['materialStock', 'outsourceDelivery'],
  outsourceDelivery: ['productStock', 'materialStock'],
  materialOrder: ['materialStock', 'materialDelivery'],
  materialDelivery: ['materialStock'],
  outsourceMaterialReturn: ['materialStock'],
  outsourceReturnOrder: ['productStock', 'materialStock'],
  outsourceOtherIo: ['materialStock'],        // 物料其他出入库审核 => 物料库存（★ 实测）
  materialStockLoss: ['materialStock', 'expense'],   // 物料报损审核 => 生成 LOSS 费用单（★ 实测）
  // ---- 主数据联动 ----
  supplier: ['warehouse'],                    // ★ 实测：创建供应商后后端自动建委外仓库
  vendor: ['warehouse'],                      // ★ 同上（供货商）
  product: ['productStock'],                  // 改产品（名称/SKU）会让库存列表的显示过期
  brand: ['product'],                         // 品牌改名会影响产品列表的品牌列
  materialType: ['material'],
  material: ['materialStock'],
  // 2026-10-05 F7-283/F7-286：研发物料主数据 —— 改动会影响研发立项页（BOM/用料引用），
  //   而「研发支出登记」（/dev/purchase-item/{id}/rd-expense）会生成费用单与资金流水 ⇒ 一并标脏。
  devMaterial: ['devProject', 'expense', 'cashflow'],
  // ---- 财务联动 ----
  receipt: ['receivable', 'cashflow'],
  payment: ['payable', 'cashflow'],
  payableTransfer: ['payable', 'cashflow'],
  bill: ['receivable', 'payable', 'cashflow'],
  expense: ['cashflow'],
  invoice: ['receivable', 'payable'],
  // ---- 系统联动 ----
  menu: ['role', 'user'],                     // 菜单变化 => 角色授权/可见页面变化
  role: ['user'],                             // 角色授权变化 => 用户页的角色下拉与权限
}

const VER_PREFIX = 'beichen_domain_ver_'
const WRITE_METHODS = ['post', 'put', 'delete', 'patch']

function readVer(d: Domain): number {
  try {
    const v = sessionStorage.getItem(VER_PREFIX + d)
    return v ? Number(v) || 0 : 0
  } catch {
    return 0
  }
}

/** 当前域版本号（供 RemoteSelect 之类做缓存比对；未声明的域返回 0）。 */
export function domainVersion(d?: Domain | null): number {
  return d ? readVer(d) : 0
}

/** 标记域已变更（自动带上级联依赖）。写操作成功后调用；请求层已自动调用，业务代码一般无需手写。 */
export function invalidate(...domains: Domain[]) {
  const queue = [...domains]
  const done = new Set<Domain>()
  while (queue.length) {
    const d = queue.shift() as Domain
    if (done.has(d)) continue
    done.add(d)
    try {
      sessionStorage.setItem(VER_PREFIX + d, String(readVer(d) + 1))
    } catch {
      /* 隐私模式等场景下 sessionStorage 不可用：退化为“本次不失效”，不影响功能 */
    }
    const deps = DOMAIN_DEPS[d]
    if (deps) queue.push(...deps)
  }
}

/** 全部域失效（顶栏“刷新数据”按钮：语义 = 我不知道改了啥，全部重来一次）。 */
export function invalidateAll() {
  const all = new Set<Domain>()
  for (const [, d] of URL_DOMAIN) all.add(d)
  invalidate(...all)
}

/**
 * 按请求失效（给响应拦截器调用）。只有**写方法**才判定；读请求（get）一律忽略。
 * 一条 URL 只映射到第一个命中的域（域表按具体→宽泛排序）。
 */
/**
 * 写这些接口会让“几乎所有数据”失效，单独维护比塞进域表更清晰：
 * 切公司（换了一套数据）、数据导入、清空本公司数据。
 */
const ALL_TRIGGERS = [
  /^\/company\/switch(\/|$)/,
  /^\/system\/import-data(\/|$)/,
  /^\/system\/clear-company-data(\/|$)/,
]

export function invalidateByRequest(method?: string, url?: string) {
  if (!method || !url) return
  if (!WRITE_METHODS.includes(method.toLowerCase())) return
  const path = url.split('?')[0]
  for (const re of ALL_TRIGGERS) {
    if (re.test(path)) {
      invalidateAll()
      return
    }
  }
  for (const [re, d] of URL_DOMAIN) {
    if (re.test(path)) {
      invalidate(d)
      return
    }
  }
}

/**
 * 列表页标准接法：首次挂载拉一次；此后每次**激活**仅在域变更时重拉。
 * 注意 Vue 的行为：keep-alive 组件首次进入会 mounted → activated 各触发一次，
 * 所以 onMounted 里要把 seen 记账，否则首屏会白拉两遍。
 *
 *   useDomainRefresh('product', loadData)
 */
export function useDomainRefresh(d: Domain, loader: () => void, legacyKey?: string) {
  let seen = -1
  // P2 过渡期兼容：旧机制是“写操作方 setItem(key,'1')、列表 onActivated 里读取并清除”。
  // 迁移期间**只改消费端**（列表页），写入端的 setItem 全部保持不动 —— 本函数同时识别
  // “新总线版本变化”与“旧 key 存在”两种信号，所以迁移不会有任何功能回落。
  // 后续把写入端逐个换成 invalidate('域') 后，第三个参数即可删除。
  const consumeLegacy = (): boolean => {
    if (!legacyKey) return false
    try {
      if (sessionStorage.getItem(legacyKey) === '1') {
        sessionStorage.removeItem(legacyKey)
        return true
      }
    } catch {
      /* sessionStorage 不可用 */
    }
    return false
  }
  onMounted(() => {
    loader()
    seen = readVer(d)
    consumeLegacy() // 首次加载已是最新数据，顺手清掉过期标记
  })
  onActivated(() => {
    const cur = readVer(d)
    if (cur !== seen || consumeLegacy()) {
      seen = cur
      loader()
    }
  })
}

/**
 * 非生命周期场景的域观察器（弹窗打开、下拉展开、点击查询前判定）。
 * 内部自动记账，每个调用点独立，互不影响。
 *
 *   const w = createDomainWatcher('material')
 *   function onOpen() { if (w.changed()) reload() }
 */
export function createDomainWatcher(d: Domain) {
  let seen = readVer(d)
  return {
    /** 自上次记账以来该域是否被写过（返回 true 并自动记账） */
    changed(): boolean {
      const cur = readVer(d)
      if (cur === seen) return false
      seen = cur
      return true
    },
    /** 手工把“当前版本”记为已读（例如自己刚拉过一次数据） */
    markFresh() {
      seen = readVer(d)
    },
  }
}
