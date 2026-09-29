<script setup lang="ts">
/**
 * 委外加工售后（2026-09-29 用户口径：目录「加工售后」＝下辖 **2 个三级叶子**，按路由路径判定叶子）：
 *  **工厂售后**（原「无单退货」，GTW-）= **工厂责任** —— 工厂发来的货、结单后才发现的问题 ⇒ 工厂负责维修，
 *  修好送回时按实际用料 FIFO 生成**对工厂的赔料应收**（source_bill_type=OUTSOURCE_RETURN_BACK），我方不付钱；
 *  **客户售后**（原「成品维修退货」，REPAIR）= **我方责任** —— 客户退回给我们的售后品 ⇒ 工厂帮我们修，
 *  我方按行付**维修费应付**（OUTSOURCE_REPAIR_CHARGE），维修用料扣工厂委外仓并 FIFO 摊入我方回仓成品成本。
 * （两条链路的钱/料方向「恰好相反」，2026-09-29 已按代码逐条核对后才改名。）
 *
 * <p><b>2026-09-29（用户口径「三级菜单关联退货不要了，以后关联退货在加工收货里面退就行」）</b>：
 * 「关联退货」叶子（408，`/outsource/return-order`）**整体下线** —— 菜单行置 visible=0 保号，
 * 旧地址重定向到「工厂售后」（原无单退货；老书签不吃 403，与 `/outsource/return-back` 同范式）。
 * 同时删掉本页仅为该叶子存在的三样东西：①页签「有效单据｜已作废单据」②叶子级「新增」选单弹窗
 * （2026-09-28 加的 `openLinkedAdd`）③`linked=WITH_ORDER` 取数口径。</p>
 * <p><b>有单（关联）的加工退货现在只有一条路</b>：在「加工收退」（原名「加工收货」，同日按用户口径改名）里退 —— 列表行内「退货」
 * 或收货详细页的「加工退货」按钮 → 既有录入页 `/outsource/order/delivery/return-defect/{orderId}`
 * （按该单产品行拆规格数量 + 选扣减成品仓），审核/反审核照旧（该单的收货记录表里就能审）。
 * 因此本页不再需要 `?from=return-order` 这个返回口径，录入页也已同步删除该分支。</p>
 *
 * <p>2026-09-21（用户口径）：**加工退货 = 一张台账表**（原「有单/无单两个页签」合一）。它们本来就是同一条
 * 负数收货记录（`delivery_type=DEFECT_RETURN`、`is_reverse=1`），审核/反审核/作废走同一套端点。</p>
 *
 * <p>2026-09-21（用户口径「文案统一成加工退货」+「历史加工退货单不要了」）：动作名全链一个词（加工单
 * 收货详细页的按钮、后端提示语与备注快照同步改名；沿革 退不良 → 加工退货 → 不良退货 → **定稿加工退货**）；
 * 上一代独立加工退货单（`outsource_return_order` 的 DEFECT，已停止新增）**不再单独列页签** ——
 * 存量单据如需反审核/作废，走详情页 URL 直达（`/outsource/return-order/detail/{id}`）。</p>
 *
 * <p>2026-09-21（用户口径「加工退货页面和物料退货的 UI 需要优化和统一，按 A+B+C+D 做」）：
 * 本页与「物料退货」页（`outsource/material-return`）**对齐成同一套列表页家规** ——
 * ①**一页一张卡片**：页签 → 筛选行 → 表格 → 分页（原先「新增/说明卡片 + 表格卡片」两张卡片）；
 * ②**筛选行统一**：左侧筛选 + [查询][重置]，**新增按钮靠右且随页签切换**（原先新增按钮在另一张卡片里，
 *   而筛选（台账）在表格上方、（维修的返回进度）却在按钮卡片上 —— 两页签位置不一致）；
 * ③**页头说明改用 `el-alert`**（原先写在一段普通文字里，里面的 `**` **会被原样显示出来**，实测确认）；
 * ④**列宽/动作集/详情入口与物料退货页统一**：操作列 174、动作顺序 = 详情 → 编辑 → 审核 → 反审核 →
 *   作废 → 结案 → 撤销结案；「详情」在台账走**抽屉**（该记录没有独立详情页）、在维修退货走**详情页**。</p>
 *
 * <p>📏 列宽预算（家规：合计 ≤ 930；纵向滚动条出现时内容区从 963 缩到约 948，故留余量）：
 * 台账 = 130+140+160+64+84+78+132 = 788 固定 ＋ 产品 min 130 = **918** ✓
 * 维修退货 = 150+140+116+100+78+132 = 716 固定 ＋ 内容列 min 136 = **852** ✓
 * 加工返回单 = 160+140+64+84+96+78+132 = 754 固定 ＋ 产品 min 130 = **884** ✓
 * （2026-09-25 用户口径：**三个页签「加工厂」统一 140、产品列统一 min130**；台账「关联加工单」110→130；
 *   三个页签分别去掉「退货日期」（台账 / 维修退货）与「返回日期」（加工返回单）——日期详情页可见，列表不重复占宽）。</p>
 */
import { computed, reactive, ref, watch, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import EntityLinks from '@/components/EntityLinks.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_RETURN_ORDER_DIRTY_KEY, OutsourceChargeTypeLabel, OutsourceReturnType, OutsourceReturnTypeLabel } from '@/api/enums'

const route = useRoute()
const router = useRouter()

/**
 * 三级菜单叶子：本工作台组件被 **2 个叶子**共用，按路由路径判定当前叶子 —— 避免把 ~700 行已验证的
 * 列表/动作复制 2 份。
 *  UNLINKED 工厂售后     /outsource/return-order/unlinked 页签：待返回 | 已返回完 | 已作废
 *  REPAIR   客户售后     /outsource/return-order/repair   页签：待返回 | 已返回完 | 已作废
 * ⚠️ 2026-09-29 用户口径「关联退货不要了，以后在加工收货（收退）里面退」：**LINKED 关联退货叶子已下线**
 *    （菜单 408 置 visible=0，旧地址 `/outsource/return-order` 在前端路由重定向到「工厂售后」）。
 * ⚠️ 2026-09-27 用户口径「加工返回单多余了」：「加工返回单」叶子已**下线** —— 工厂把货修好送回不再单独开单，
 *    改在**工厂售后的记录详情页**点「登记返回」（`views/outsource/defect-return/detail.vue`），
 *    与「客户售后」详情页的「登记维修返回」同范式（登记即生效 + 逐条撤销）。
 *    旧地址 `/outsource/return-back` 在前端路由里重定向到「工厂售后」，老书签不吃 403。
 */
type Leaf = 'UNLINKED' | 'REPAIR'
const leaf = computed<Leaf>(() => {
  const p = route.path.replace(/\/$/, '')
  if (p.endsWith('/repair')) return 'REPAIR'
  return 'UNLINKED'
})

/** 页签 key：CANCELLED=已作废 PENDING=待返回 DONE=已返回完（原 LINKED 叶子的 ACTIVE「有效单据」已随叶子下线） */
type TabKey = 'CANCELLED' | 'PENDING' | 'DONE'
const TABS: Record<Leaf, Array<{ key: TabKey; label: string }>> = {
  // 「待返回」含**草稿**（还没送修/还没审核的单不能在任何页签里消失）；「已返回完」= 已审核且全部送回
  UNLINKED: [{ key: 'PENDING', label: '待返回' }, { key: 'DONE', label: '已返回完' }, { key: 'CANCELLED', label: '已作废' }],
  REPAIR: [{ key: 'PENDING', label: '待返回' }, { key: 'DONE', label: '已返回完' }, { key: 'CANCELLED', label: '已作废' }]
}
const tabs = computed(() => TABS[leaf.value])
/**
 * D-25 ②（2026-09-30）：本文件同时服务两个叶子（`UNLINKED`=工厂售后、`REPAIR`=客户售后），
 * 两叶子的菜单码不同（`outsource:order-delivery` / `outsource:return-order`）⇒ 行内按钮的 `v-perm`
 * 用**本计算属性**给出对应码。
 *
 * <p>为什么不在模板里写三元：模板中这些按钮位于 `v-if="leaf === 'UNLINKED'"` 等分支内，vue-tsc 会把
 * `leaf` 收窄成字面量，三元里的另一分支被判为"无重叠比较"（`TS2367`）。放在 `<script setup>` 里比较
 * `ComputedRef.value` 则没有收窄问题。</p>
 */
const actionPerm = computed(() => (leaf.value === 'REPAIR' ? 'outsource:return-order' : 'outsource:order-delivery'))
const activeTab = ref<TabKey>('PENDING')
/** 页签角标：各页签条数（用 pageSize=1 的轻量请求取 total —— 零后端改动） */
const tabCounts = reactive<Record<string, number>>({})
function countOf(key: TabKey) { return tabCounts[leaf.value + ':' + key] }

/**
 * 筛选行条件（2026-09-28 用户口径）：三个叶子共用「单号 + 加工厂」两个条件。
 * <p>原先筛选行只有「查询 / 重置」两个按钮、**前面没有任何输入**（条件全由叶子 + 页签表达），
 * 用户实测反馈"点查询不知道查什么" ⇒ 补：单号（**模糊**）+ 加工厂（远程下拉，
 * 与「新增工厂售后」同一口径 `excludeSupplierType=product`：只能退给加工厂/辅料商/方案商）。</p>
 */
const filters = reactive<{ code: string; factoryId: any }>({ code: '', factoryId: undefined })
function clearFilters() { filters.code = ''; filters.factoryId = undefined }
/** 单号占位提示：两个叶子单号前缀不同（GTW- 无单 / OR- 维修），动态提示避免"不知道该填什么" */
const codePlaceholder = computed(() => {
  if (leaf.value === 'UNLINKED') return '退货单号（GTW-）'
  return '退货单号（OR-）'
})

/** 台账（**只有无单**，2026-09-29 关联叶子下线）查询参数：页签决定 status / returnProgress，筛选行决定 code / factoryId */
function ledgerParams(tab: TabKey, pageNum: number, pageSize: number) {
  const active = [DocStatus.DRAFT, DocStatus.AUDITED].join(',')
  const p: any = {
    page: pageNum, size: pageSize, linked: 'WITHOUT_ORDER',
    code: filters.code || undefined, factoryId: filters.factoryId ?? undefined
  }
  if (tab === 'CANCELLED') p.status = DocStatus.CANCELLED
  else if (tab === 'PENDING') { p.status = active; p.returnProgress = 'PENDING' }
  else if (tab === 'DONE') p.returnProgress = 'DONE'
  return p
}

// ==================== ① 加工退货台账（关联 / 无单各一个叶子，2026-09-21 + 2026-09-27 口径） ====================
const ledger = ref<any[]>([])
const ledgerLoading = ref(false)
const ledgerPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function loadLedger() {
  ledgerLoading.value = true
  try {
    const r = await request.get<any, any>('/outsource/order-delivery/return-defect/page',
      { params: ledgerParams(activeTab.value, ledgerPage.pageNum, ledgerPage.pageSize) })
    ledger.value = r?.records || []
    ledgerPage.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载加工退货失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { ledgerLoading.value = false }
}
function ledgerSearch() { ledgerPage.pageNum = 1; loadLedger() }
/** 规格显示：DEFECT 显示"不良"，其余显示"A规/B规/C规" */
function specText(q?: string) { return q === 'DEFECT' ? '不良' : (q ? q + '规' : '-') }

async function auditLedger(row: any) {
  const tip = row.orderCode
    ? '确定审核该加工退货吗？审核后将扣减成品库存、把 BOM 料还回工厂委外仓并冲减应付，该加工单的已收数量同步回退。'
    : '确定审核该加工退货吗？审核后将扣减成品库存、把 BOM 料还回所选加工厂的委外仓，并按还回料的 FIFO 价值冲减应付。'
  try { await ElMessageBox.confirm(tip, '审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/order-delivery/${row.id}/audit`); ElMessage.success('已审核'); await loadLedger() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function unauditLedger(row: any) {
  try { await ElMessageBox.confirm('确定反审核吗？将回滚成品库存、扣回已还的料并冲回应付，回到草稿。', '反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/order-delivery/${row.id}/un-audit`); ElMessage.success('已反审核'); await loadLedger() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
/**
 * 作废加工退货草稿（2026-09-27 用户口径）：DRAFT → CANCELLED，落进「已作废」页签（留痕可查、可授权）。
 * <p>原先这里是**物理删除**（草稿删除）——服务端端点保留供历史脚本调用，页面不再暴露删除入口；
 * 已审核的撤销仍走「反审核」（详情页内，账务等量逆回）。</p>
 */
async function cancelLedger(row: any) {
  try { await ElMessageBox.confirm('确定作废该加工退货草稿吗？作废后进「已作废」页签，可查、不可再审核。', '作废', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/outsource/order-delivery/${row.id}/cancel`)
    ElMessage.success('已作废')
    await loadLedger(); await loadCounts()
  } catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

// ---------- 详情抽屉（2026-09-21 用户口径「加工退货页面的列表也应该有详情」）----------
// 列表只留扫读几列，细节进抽屉（与「收货记录」同一家规）：记录全字段 + 落账明细
//（审核后实际扣的成品、按 BOM 还回工厂委外仓的物料、冲减的应付）。
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})

/** 详情改独立页（2026-09-23 用户要求：抽屉改独立界面）：台账行点击 / 行内「详情」都跳详情页 */
function openDetail(row: any) { if (row?.id != null) router.push(`/outsource/defect-return/detail/${row.id}`) }

// ---------- 新增"无单"加工退货（2026-09-27 用户口径：弹窗改**独立页面** /outsource/return-order/unlinked/add；
//   有单的退回请到该加工单的收货详细页）。BOM 快照的解析/选择也在那边做。 ----------
/** 退货规格：与加工单收货详细页的退货弹窗同一口径（A/B/C/不良）；加工返回单的「回仓品质」也复用本表 */
const NO_ORDER_SPECS = [
  { value: 'A', label: 'A规' }, { value: 'B', label: 'B规' },
  { value: 'C', label: 'C规' }, { value: 'DEFECT', label: '不良' }
]
/**
 * 退货对象（= 还料与应付对象，无单时靠它定位工厂委外仓）。
 * <p>2026-09-21（用户口径）：**加工退货只能退给加工厂或供应商，不能退给供货商** ⇒
 * `excludeSupplierType: 'product'`（供货商=成品商 product；其余 加工厂/辅料商/方案商 都放行）。
 * 后端 `returnDefectNoOrder` 有同一口径的兜底校验 ✓。</p>
 */
const fetchFactories = (kw: string) =>
  request.get('/supplier/page', { params: { pageSize: 500, name: kw, excludeSupplierType: 'product' } })
/** 扣减的成品仓（我方自有成品仓） */
const fetchFinishedWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })
/** 产品主数据（无单时没有加工单产品行可选） */
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })

/** 去独立新增页（BOM 快照的自动解析与换版本都在该页；保存后回到本叶子） */
function openNoOrder() { router.push('/outsource/return-order/unlinked/add') }

// ---------- 新增"关联"加工退货（2026-09-28 加，**2026-09-29 已删**）----------
// 用户口径「三级菜单关联退货不要了，以后关联退货在加工收货里面退就行」⇒ 本页不再有"选加工单"入口，
// 有单的加工退货统一在「加工收退」（列表行内「退货」/ 收货详细页「加工退货」按钮）发起。

// ==================== ③ 加工返回单（**2026-09-27 已下线**） ====================
// 用户口径「加工返回单多余了，和成品维修退货一样在详细里面登记返回就行」：
//   · 前端入口与列表/弹窗已整体删除（本文件不再有 BACK 叶子）；
//   · 登记/撤销改在**工厂售后记录详情页**（原无单退货）：`views/outsource/defect-return/detail.vue`
//     → `POST/DELETE /api/outsource/order-delivery/{id}/return-back`（登记即生效，逐条撤销）；
//   · 后端表 `outsource_return_back` 与 `/api/outsource/return-back/*` 端点**保留**（存量查询/回归脚本仍用），
//     只是前端不再有独立单据入口。

// ==================== ② 独立退货单（维修退货） ====================
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

/** 维修退货查询参数：页签 → 进度（OPEN=待返回含草稿 / RETURNED=已返回完 / CANCELLED=已作废） */
function repairParams(tab: TabKey, pageNum: number, pageSize: number) {
  // 筛选行条件（单号 / 加工厂）对维修退货叶子同样生效；后端 /outsource/return-order/page 本就有 code / factoryId
  const p: any = {
    pageNum, pageSize, returnType: OutsourceReturnType.REPAIR,
    code: filters.code || undefined, factoryId: filters.factoryId ?? undefined
  }
  if (tab === 'PENDING') p.progress = 'OPEN'
  else if (tab === 'DONE') p.progress = 'RETURNED'
  else if (tab === 'CANCELLED') p.statuses = DocStatus.CANCELLED
  else p.statuses = [DocStatus.DRAFT, DocStatus.AUDITED].join(',')
  return p
}

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/return-order/page',
      { params: repairParams(activeTab.value, pagination.pageNum, pagination.pageSize) })
    list.value = r?.records || []; pagination.total = Number(r?.total || 0)
  } finally { loading.value = false }
}

/** 切页签：重置分页并只加载当前叶子的数据（筛选条件已由页签本身表达） */
function handleTabChange() { resetPages(); loadCurrent() }
/** 各叶子筛选行的「查询」：带条件重查，页签角标同步刷新（角标要反映筛选后的条数，否则与列表对不上） */
function handleSearch() { resetPages(); loadCurrent(); loadCounts() }
/** 筛选行「重置」（2026-09-28）：先清空条件再重查 —— 原先重置与查询同为一个动作，条件根本清不掉 */
function handleReset() { clearFilters(); handleSearch() }
function resetPages() { ledgerPage.pageNum = 1; pagination.pageNum = 1 }
/** 当前叶子对应的列表加载（加工返回单叶子已下线 ⇒ 只剩台账与维修退货单两种） */
function loadCurrent() {
  if (leaf.value === 'REPAIR') return loadData()
  return loadLedger()
}

/**
 * 页签角标（2026-09-27）：各页签条数 —— 每页取 1 条只读 total，零后端改动；
 * 让"还有多少没回来"一眼可见（用户核心诉求）。
 */
async function loadCounts() {
  for (const t of tabs.value) {
    const k = leaf.value + ':' + t.key
    try {
      let total = 0
      if (leaf.value === 'REPAIR') {
        const r = await request.get<any, any>('/outsource/return-order/page', { params: repairParams(t.key, 1, 1) })
        total = Number(r?.total || 0)
      } else {
        const r = await request.get<any, any>('/outsource/order-delivery/return-defect/page', { params: ledgerParams(t.key, 1, 1) })
        total = Number(r?.total || 0)
      }
      tabCounts[k] = total
    } catch { tabCounts[k] = 0 }
  }
}

async function handleAudit(row: any) {
  const tip = row.returnType === OutsourceReturnType.REPAIR
    ? '确认审核该维修退货单？审核后成品送修出库（不冲减应付）并生成加工厂向我方收取的维修费应付'
    : '确认审核该退货单？审核后物料入工厂仓、成品出库并冲减应付'
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/audit`); ElMessage.success('已审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit(row: any) {
  try { await ElMessageBox.confirm('确认反审核？将逆向库存并冲销应付', '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/** 结案（仅维修退货）：工厂把送修成品全部送回（未返回=0）后确认收尾 */
async function handleClose(row: any) {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/撤销维修返回，也不能反审核（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/close`); ElMessage.success('已结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen(row: any) {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/re-open`); ElMessage.success('已撤销结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

/** 新增时带上类型（维修退货），进新增页后表单按类型切换（2026-09-21：加工退货已不在本页新增） */
function handleAdd(type: string) { router.push(`/outsource/return-order/add?returnType=${type || OutsourceReturnType.REPAIR}`) }
/** E4：草稿可编辑（后端 PUT /outsource/return-order/{id}，仅 DRAFT） */
/* 2026-09-24（用户口径）：列表不再提供「编辑」 —— 草稿态统一在详情页内联改+存（handleEdit 已移除），
   且详情页只对**维修退货（REPAIR）**开放就地编辑（加工退货的退货物料按 BOM 快照联动派生，搬进详情会规则分叉）。 */
/** 加工单号 → 该加工单详情（有单的加工退货由它承载数量回退）。
 *  2026-09-29：本页台账的「关联加工单」列已随关联退货叶子删除 ⇒ 该跳转在本页不再有入口
 *  （有单的退货记录在「加工收退」里看，那边单号列可点）。 */
function goReturnDetail(row: any) { router.push(`/outsource/return-order/detail/${row.id}`) }

function reloadCurrent() { loadCurrent(); loadCounts() }

// 叶子切换（点左侧菜单 / 直达 URL）：页签回到该叶子的第一个（「待返回」）并加载
watch(leaf, (lv) => {
  activeTab.value = TABS[lv][0].key
  resetPages()
  loadCurrent()
  loadCounts()
})

onActivated(() => {
  // 详情/新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY)
    reloadCurrent()
  }
})
onMounted(() => {
  activeTab.value = TABS[leaf.value][0].key
  loadCurrent()
  loadCounts()
})

</script>

<template>
  <!-- 一页一张卡片（家规）：页签 → 筛选行（含新增按钮）→ 业务提示 → 表格 → 分页 -->
  <div class="page-list">
    <el-card shadow="never">
      <!-- 页签按叶子生成（2026-09-27 三级菜单）：标签后带**数量角标**（页签条数），
           让"还有多少没回来 / 多少已作废"一眼可见；口径见 TABS / loadCounts。 -->
      <el-tabs v-model="activeTab" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane v-for="t in tabs" :key="t.key" :name="t.key">
          <template #label>
            <span>{{ t.label }}<span v-if="countOf(t.key)" style="margin-left:4px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ countOf(t.key) }}</span></span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 筛选行（2026-09-28 用户口径「查询按钮前面都没有输入框和条件」）：补「单号（模糊）+ 加工厂（下拉）」，
           三个叶子共用这一行（单号前缀不同 ⇒ placeholder 动态提示）；右侧新增按钮随叶子切换（一页一个新增入口）。
           原「（单号 GTW-/GTH-）」文字说明已并入输入框 placeholder，筛选行更紧凑。 -->
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <el-input v-model="filters.code" :placeholder="codePlaceholder" clearable
          style="width:220px" @keyup.enter="handleSearch" />
        <RemoteSelect v-model="filters.factoryId" :fetch="fetchFactories" value-key="id" label-key="name"
          placeholder="加工厂" style="width:240px" disable-cache />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <!-- 新增按钮随叶子切换（一页一个新增入口，2026-09-28：关联退货叶子也补齐入口） -->
        <div style="margin-left:auto">
          <el-button v-if="leaf === 'UNLINKED'" type="success" :icon="'Plus'" @click="openNoOrder">新增</el-button>
          <el-button v-else type="success" :icon="'Plus'" @click="handleAdd(OutsourceReturnType.REPAIR)">新增</el-button>
        </div>
      </div>

      <!-- 业务提示（按叶子）：说清"这一页在干什么 + 单从哪来 + 后续在哪办"（2026-09-29 按用户口径：
           叶子名与两行的业务语义对齐 —— 工厂售后=工厂责任（赔料应收）/ 客户售后=我方责任（我方付维修费）） -->
      <el-alert v-if="leaf === 'UNLINKED'" type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            <b>工厂售后（工厂责任）</b>：工厂发来的货、结单后才发现的问题 ⇒ 退回成品、由工厂负责维修；
            工厂修好送回时在<b>本记录的详情页</b>点「登记返回」（2026-09-27 起不再单独开「加工返回单」），
            送回时按实际用料计价生成<b>对工厂的赔料应收</b>（单价默认 FIFO、登记时可人工修改）。
            <b>「待返回」含草稿</b>（未审核也算未返回）；全部送回后落进「已返回完」。
          </span>
        </template>
      </el-alert>
      <el-alert v-else type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            <b>客户售后（我方责任）</b>：我们卖给客户、客户退回给我们的售后品 ⇒ 工厂帮我们维修：
            送修出库 → 工厂送回时在详情页「登记维修返回」→ 全部送回后可<b>结案</b>；
            我方按行付<b>维修费应付</b>，维修用料计入我方成品成本。
            「待返回」含草稿；「已作废」= 草稿被作废的单。
          </span>
        </template>
      </el-alert>

      <!-- ============ ① 加工退货台账：**只有无单**（2026-09-29 关联退货叶子下线；有单的在「加工收退」里退）= ============
           2026-09-27 三级菜单：叶子决定 linked（不再用下拉），页签决定状态 / 返回进度。
           列宽：无单叶子 = 158+140+min130+64+84+116(返回进度)+78+132 = 902 ✓（≤948 容器） -->
      <template v-if="leaf === 'UNLINKED'">
        <!-- 列宽合计 902px（**留余量**）＜ 内容区（行数多时纵向滚动条约吃掉 15px：963→948），一行显示完、不横向滑动。
             2026-09-25：加「退货单号」列（GTW-），去掉「备注」列（详情可见）；扣减仓库挂「退货数量」title。
             2026-09-25（用户口径）：①去掉「退货日期」列（日期在详情页可见，列表不用重复占宽）；
             ②「加工厂」统一 140、「产品」统一 min130；退货单号做成链接进详情、加工厂做成链接进供应商详情。
             2026-09-29：「关联加工单」列随关联退货叶子一起**删除**（本页只有无单，该列恒为"未关联"）。 -->
        <el-table :data="ledger" border stripe v-loading="ledgerLoading" @row-click="openDetail">
          <!-- 2026-09-27（实测）：单号 130→**158** —— GTW- + 11 位（15 字）实测需 ~151px，
               130 会把 GTW-20260927001 截断（该数据 2026-09-27 才出现，此前扫描未覆盖）。 -->
          <el-table-column label="退货单号" width="158" show-overflow-tooltip>
            <template #default="{ row }"><el-button type="primary" link @click.stop="openDetail(row)">{{ row.code || ('加工退货#' + row.id) }}</el-button></template>
          </el-table-column>
          <el-table-column label="加工厂" width="140" show-overflow-tooltip>
            <template #default="{ row }"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.factoryId}`)">{{ row.factoryName }}</el-button></template>
          </el-table-column>
          <!-- 2026-09-25：产品可点进产品详情（台账行自带 productMasterId = product.id） -->
          <el-table-column label="产品" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">
              <EntityLinks :items="row.productMasterId ? [{ id: row.productMasterId, name: row.productName, sku: row.sku }] : []" target="product" sub-key="sku">
                <span>{{ row.productName || '-' }}</span>
              </EntityLinks>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="64" align="center"><template #default="{ row }">{{ specText(row.qualityType) }}</template></el-table-column>
          <el-table-column label="退货数量" width="84" align="right">
            <template #default="{ row }">
              <span :title="row.warehouseName ? ('扣减仓库：' + row.warehouseName) : ''" style="color:var(--app-color-danger);font-weight:500">{{ Math.abs(Number(row.quantity || 0)) }}</span>
            </template>
          </el-table-column>
          <!-- 「返回进度」（2026-09-27 用户口径「还给我们没有、还了多少」）：已返回 = Σ已审核加工返回单（按来源单）。
               「待返回」页签 = 未返回 > 0（含草稿）；「已返回完」页签 = 已返回 ≥ 退货数量 -->
          <el-table-column v-if="leaf === 'UNLINKED'" label="退货/已返回" width="116" align="center" show-overflow-tooltip>
            <template #default="{ row }">
              <span :style="{ color: Number(row.unreturnedQty) > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontWeight: 500 }"
                :title="Number(row.unreturnedQty) > 0 ? ('已返回 ' + row.returnedQty + ' 件，还有 ' + row.unreturnedQty + ' 件未返回') : '已全部返回'">
                {{ Math.abs(Number(row.quantity || 0)) }} / {{ row.returnedQty }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="78" align="center">
            <template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
          </el-table-column>
          <!-- 动作集（2026-09-27）：详情 → 审核（草稿）→ 作废（草稿）。
               ⚠️「删除」已由**作废**取代：作废留痕并进「已作废」页签；反审核仍在详情页。 -->
          <el-table-column label="操作" width="132" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click.stop="openDetail(row)">详情</el-button>
              <el-button v-perm="actionPerm" type="success" link v-if="row.status === DocStatus.DRAFT" @click.stop="auditLedger(row)">审核</el-button>
              <el-button v-perm="actionPerm" type="danger" link v-if="row.status === DocStatus.DRAFT" @click.stop="cancelLedger(row)">作废</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination">
          <el-pagination v-model:current-page="ledgerPage.pageNum" v-model:page-size="ledgerPage.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="ledgerPage.total"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="ledgerSearch" @current-change="loadLedger" />
        </div>
      </template>

      <!-- ============ ③ 加工返回单叶子已下线（2026-09-27 用户口径）============
           工厂把货修好送回不再单独开单 ⇒ 登记入口搬到「工厂售后」记录详情页
           （views/outsource/defect-return/detail.vue：登记返回 / 返回记录 / 逐条撤销）。 -->

      <!-- ============ ② 独立退货单：维修退货（送修 / 返回 / 结案） ============ -->
      <template v-else>
        <!-- 列宽合计 834px（**留余量**）＜ 内容区，保证「一行显示完、不横向滑动」。
             2026-09-21 与物料退货页对齐：单号 132 / 送修已返回 116 / 内容 min136 / 工厂收费 100 / 状态 78 / 操作 132。
             2026-09-25（用户口径）：①去掉「退货日期」列（日期在详情页可见，列表不重复占宽）；
             ②「加工厂」100→140（三个页签统一）。 -->
        <el-table :data="list" border stripe v-loading="loading" @row-click="goReturnDetail">
          <!-- 2026-09-25（用户口径）：退货单号 132→150（OR-+11 位 + 链接按钮内边距，实测需 ~150）并做成链接进详情 -->
          <el-table-column label="退货单号" width="150" show-overflow-tooltip>
            <template #default="{ row }"><el-button type="primary" link @click.stop="goReturnDetail(row)">{{ row.code }}</el-button></template>
          </el-table-column>
          <el-table-column label="加工厂" width="140" show-overflow-tooltip>
            <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.factoryId}`)">{{ row.factoryName }}</el-button></template>
          </el-table-column>
          <!-- 送修 / 已返回（2026-09-17）：橙=工厂还没送完、绿=已全部送回；结案入口见操作列 -->
          <el-table-column label="送修/已返回" width="116" align="center" show-overflow-tooltip>
            <template #default="{ row }">
              <span :style="{ color: Number(row.unreturnedQty) > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontWeight: 500 }"
                :title="Number(row.unreturnedQty) > 0 ? ('还有 ' + row.unreturnedQty + ' 件未返回') : '已全部返回'">
                {{ row.sentQty ?? '-' }} / {{ row.repairReturnedQty ?? 0 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="退货/送修内容" min-width="136" show-overflow-tooltip>
            <!-- 维修退货没有物料明细 → 显示"产品×数量"；加工退货显示"退货物料"（BOM 快照）。
                 2026-09-25：本页签按 returnType=REPAIR 查，行里的产品可点进产品详情（后端新增 products[]） -->
            <template #default="{ row }">
              <EntityLinks :items="row.products" target="product" qty-key="quantity">
                <span>{{ row.itemSummary || row.productSummary || '-' }}</span>
              </EntityLinks>
            </template>
          </el-table-column>
          <!-- 2026-09-28（用户口径）：维修费改为**按返回产品行**在「登记维修返回」时收 ⇒ 本列显示该单
               已登记返回的维修费合计（后端 repairFee = Σ 各返回行的 repair_amount，已挂对加工厂的应付）。
               存量老单若仍带整单收费（历史口径 charge_flag=1）照旧显示，便于新旧对照。 -->
          <el-table-column label="维修费" width="100" align="right">
            <template #default="{ row }">
              <span v-if="Number(row.repairFee) > 0" style="color:var(--app-color-warning);font-weight:500">{{ Number(row.repairFee).toFixed(2) }}</span>
              <el-tag v-else-if="Number(row.chargeFlag) === 1 && Number(row.chargeAmount) > 0" type="warning" size="small"
                :title="'加工厂向我方收取（历史整单口径）：' + (OutsourceChargeTypeLabel[String(row.chargeType)] || '')">
                {{ Number(row.chargeAmount).toFixed(2) }}
              </el-tag>
              <span v-else style="color:var(--app-text-placeholder)">—</span>
            </template>
          </el-table-column>
          <!-- 2026-09-27：状态与进度**分开** —— 状态列恒显示单据状态（草稿/已审核/已作废），
               「已结案」作为附加标签并列（原先"已结案"替代"已审核"，把两个维度挤在一列里） -->
          <el-table-column label="状态" width="126" align="center">
            <template #default="{ row }">
              <el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
              <el-tag v-if="row.returnType === OutsourceReturnType.REPAIR && row.closedFlag === 1" type="success" size="small" style="margin-left:4px">已结案</el-tag>
            </template>
          </el-table-column>
          <!-- 动作集与顺序统一（与物料退货页一致）：详情 → 审核 → 反审核 → 作废 → 结案 → 撤销结案 -->
          <!-- 2026-09-24（用户口径）：反审核与编辑都收进详情页（详情草稿态可就地改+存）⇒ 操作列 176→132。
               本表按 returnType=REPAIR 查询（DEFECT 走另一张台账表、本来就没有编辑入口）⇒ 口径刚好对齐。 -->
          <el-table-column label="操作" width="132" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click.stop="goReturnDetail(row)">详情</el-button>
              <el-button v-perm="actionPerm" type="success" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleAudit(row)">审核</el-button>
              <el-button v-perm="actionPerm" type="danger" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleCancel(row)">作废</el-button>
              <!-- 结案（仅维修退货）：未返回=0 才出现 -->
              <el-button v-perm="actionPerm" type="success" link v-if="row.returnType===OutsourceReturnType.REPAIR && row.status===DocStatus.AUDITED && row.closedFlag!==1 && Number(row.unreturnedQty)===0" @click.stop="handleClose(row)">结案</el-button>
              <el-button v-perm="actionPerm" type="warning" link v-if="row.closedFlag===1" @click.stop="handleReOpen(row)">撤销结案</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination">
          <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="handleSearch" @current-change="loadData" />
        </div>
      </template>
    </el-card>

    <!-- 2026-09-27（用户口径）：新增无单加工退货已从**弹窗改为独立页面**
         `/outsource/return-order/unlinked/add`（含 BOM 快照自动解析 + 可换版本）—— 入口见本页「新增」按钮；
         2026-09-29：叶子改文案「工厂售后」⇒ 该入口即「新增工厂售后」 -->

    <!-- 加工返回单叶子已下线（2026-09-27 用户口径）：登记/撤销在工厂售后详情页 —— 见本文件头注释与
         views/outsource/defect-return/detail.vue 的「登记返回」弹窗（同名字段：回仓仓库/回仓品质/实际用料）。 -->

    <!-- 「新增关联退货」选单弹窗已于 2026-09-29 随关联退货叶子一起删除（改在「加工收退」里退） -->

  </div>
</template>
