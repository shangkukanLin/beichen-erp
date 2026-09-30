<script setup lang="ts">
/**
 * 委外物料售后（目录 423；两个叶子 = **工厂维修** / **退货退款**）
 *
 * <p><b>2026-09-29 用户口径</b>：「委外加工子菜单『物料退货』改名『物料售后』；关联退料不需要了，
 * 以后关联退料在物料收退做；下面的子菜单改为 工厂维修 和 退货退款」—— 与加工侧 419「加工售后」
 * （408 关联退货下线、只留 工厂售后/客户售后）**同范式**：</p>
 * <ul>
 *   <li>目录 423 改名「物料售后」；两个叶子**按类型**分：**工厂维修**（`/repair`，REPAIR）/
 *       **退货退款**（`/unlinked`，REFUND）；</li>
 *   <li><b>「关联退料」叶子下线</b>（411 置 visible=0 保号 + 路由 `/outsource/material-return` 重定向）——
 *       挂物料订单的退料改在<b>「物料收退」</b>做：收退详情页工具栏「物料退货」→ RECEIVE_RETURN 记录，
 *       审核后 <b>冲减该单已收数量 + 冲减应付</b>（用户口径「关联退料需要冲减应付，然后减少该订单的收货数量」）；</li>
 *   <li>「订单退料」类型**以后不再新建**（历史单仅能从库存流水/应收台账点进详情）；</li>
 *   <li>列表口径写死 `linked=WITHOUT_ORDER` ⇒ 关联单不再进本模块列表（与加工侧一致）。</li>
 * </ul>
 *
 * <p>2026-09-21（用户口径「加工退货页面和物料退货的 UI 需要优化和统一，按 A+B+C+D 做」）：
 * 本页与「加工退货」页（`outsource/return-order`）**对齐成同一套列表页家规** ——
 * ①**一页一张卡片**：页签 → 筛选行 → 表格 → 分页；②**筛选行统一**：单号 / 供应商 + [查询][重置]，
 * **新增按钮靠右**；③**列宽瘦身、消除横向滚动**；④**术语统一**（与加工侧同名）；
 * ⑤**返回进度合并在「送修/已返回」列**（橙=还有未返回、绿=已全部返回；已结案直接显示在「状态」列）；
 * ⑥**动作集与顺序统一**：详情 → 审核 → 反审核 → 作废 → 结案 → 撤销结案（编辑在详情页内联做）。</p>
 *
 * <p>📏 列宽预算（家规：合计 ≤ 948，纵向滚动条出现时内容区从 963 缩到约 948，故留余量）：
 * 2026-09-29 两叶子列集（详见模板里的逐列合计）：退货退款 814 ✓ / 工厂维修 938 ✓
 * （公共列：单号 158 / 供应商 120 / 金额 90 / 操作 132；「送修/已返回」只在工厂维修叶子）。
 * 实测见 tools/regression/scan-col-truncation.ps1）。</p>
 *
 * <p>📌 详情入口规则（与加工侧同一条家规）：**有独立详情页的单据 → 行点击 / 单号链接跳详情页**。</p>
 */
import { computed, reactive, ref, watch, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel } from '@/api/enums'
import EntityLinks from '@/components/EntityLinks.vue'
import { useDomainRefresh } from '@/utils/dataFreshness'

defineOptions({ name: 'OutsourceMaterialReturn' })

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ code: '', supplierId: undefined as any })

/**
 * 三级菜单叶子（**2026-09-29 用户口径**：目录改名「物料售后」，「关联退料」下线、其业务改在「物料收退」做，
 * 下面两个子菜单改为 **工厂维修 + 退货退款**，顺序 = 工厂维修在前）—— 与加工侧 `/outsource/return-order`
 * 同范式：本组件被两个叶子共用，按**路由路径**判叶子：
 *  REPAIR 工厂维修 `/outsource/material-return/repair`   （单据类型 REPAIR：送修 → 回厂登记 → 全返回可结案）
 *  REFUND 退货退款 `/outsource/material-return/unlinked` （单据类型 REFUND：物料退回 + 生成对供应商的应收）
 *
 * <p>🔖 类型（`returnType`，见 `MaterialReturnType`）：`REPAIR` 工厂维修 / `REFUND` 退货退款 /
 * `ORDER` 订单退料（**2026-09-29 起不再新建**，历史单不在本模块列表 —— 仅能从库存流水/应收台账点进详情）。</p>
 *
 * <p>⚠️ **叶子 = 类型**（不再是 2026-09-27~28 的"关联/无单"）：类型由叶子表达 ⇒ 页面**不再有「类型」页签**；
 * 列表口径写死 `linked=WITHOUT_ORDER`（关联单不进本模块列表，与加工侧一致）。</p>
 */
type Leaf = 'REFUND' | 'REPAIR'
const leaf = computed<Leaf>(() => {
  const p = route.path.replace(/\/$/, '')
  if (p.endsWith('/repair')) return 'REPAIR'
  return 'REFUND'
})
/** 叶子 → 单据类型（后端 returnType 落参） */
const leafReturnType = computed(() => (leaf.value === 'REPAIR' ? MaterialReturnType.REPAIR : MaterialReturnType.REFUND))
/** 是否「工厂维修」叶子（决定页签集与列集） */
const isRepairLeaf = computed(() => leaf.value === 'REPAIR')

/**
 * 页签（**按叶子不同**，与加工侧同款）：
 * · **退货退款**：`草稿和已审核 | 已作废`（退款没有"返回"概念）；
 * · **工厂维修**：`待返回 | 已返回完 | 已作废` —— 2026-09-29 用户口径「需要补」：让"还有多少没修回来"一眼可见。
 *   `待返回` **含草稿**（未审核的单不能在任何页签里消失）；`已返回完` = 已审核且送修已全部送回（含已结案）。
 *   两者走后端既有的 `progress=OPEN|RETURNED`（`OutsourceMaterialReturnServiceImpl` 早已实现）⇒ **零后端改动**。
 */
type TabKey = 'ACTIVE' | 'PENDING' | 'DONE' | 'CANCELLED'
const TABS: Record<Leaf, Array<{ key: TabKey; label: string }>> = {
  REFUND: [{ key: 'ACTIVE', label: '草稿和已审核' }, { key: 'CANCELLED', label: '已作废' }],
  REPAIR: [{ key: 'PENDING', label: '待返回' }, { key: 'DONE', label: '已返回完' }, { key: 'CANCELLED', label: '已作废' }]
}
const tabs = computed(() => TABS[leaf.value])
const activeTab = ref<TabKey>('ACTIVE')
/** 页签角标：各页签条数（pageSize=1 取 total，零后端改动）；key = 叶子:页签 */
const tabCounts = reactive<Record<string, number>>({})
function countKey(tab: TabKey) { return leaf.value + ':' + tab }
function countOf(tab: TabKey) { return tabCounts[countKey(tab)] }
/**
 * 列表查询参数（2026-09-29 叶子=类型）：
 * · `returnType` 由**叶子**决定；`linked` 写死 `WITHOUT_ORDER`（关联单不进本模块列表）；
 * · 页签落参：`已作废` ⇒ 状态 CANCELLED；`待返回` ⇒ `progress=OPEN`（**含草稿**）；
 *   `已返回完` ⇒ `progress=RETURNED`（已审核且全部送回，含已结案）；退货退款叶子 ⇒ 状态 DRAFT,AUDITED。
 */
function listParams(tab: TabKey, pageNum: number, pageSize: number) {
  const p: any = {
    pageNum, pageSize,
    linked: 'WITHOUT_ORDER',
    returnType: leafReturnType.value,
    code: query.code || undefined, supplierId: query.supplierId || undefined
  }
  if (tab === 'CANCELLED') p.statuses = DocStatus.CANCELLED
  else if (tab === 'PENDING') p.progress = 'OPEN'
  else if (tab === 'DONE') p.progress = 'RETURNED'
  else p.statuses = [DocStatus.DRAFT, DocStatus.AUDITED].join(',')
  return p
}

/**
 * 退货对象（辅料商/供应商）实时查库。
 * <p>2026-09-21（用户口径）：**物料退货的对方只可能是辅料商或供应商，不会是供货商** ⇒
 * 筛选与表单下拉一律 `excludeSupplierType: 'product'`（与新增页、与后端兜底校验同一口径 ✓）。</p>
 */
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { pageSize: 500, name: kw, excludeSupplierType: 'product' } })

/**
 * 行类型判定：叶子已=类型，但**动作仍按行类型**判（结案/撤销结案只对工厂维修；审核/反审核提示语按类型分别写）——
 * 这样即使历史数据里混进另一种类型的行（例如从库存流水点进来的关联单），动作也不会错。
 */
const isRepairRow = (row: any) => row?.returnType === MaterialReturnType.REPAIR
const isOrderReturnRow = (row: any) => row?.returnType === MaterialReturnType.ORDER
/** 「送修 / 已返回」列只在**工厂维修**叶子（该叶子全是维修单 ⇒ 返回进度用独立列显示） */
const showSentCol = computed(() => isRepairLeaf.value)

// 注（2026-09-28）：原「出库源仓」列与它的仓库详情跳转（goWarehouseDetail + 挂载时拉仓库列表）
// 已随两叶子列重排移除 —— 类型列（含维修返回的"已返回数量"）优先级更高，源仓在**详情页**可见。
// 若后续要恢复该列，见详情页「出库源仓」字段（同一 id→factoryId 分流逻辑在该页仍在用）。

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/material-return/page',
      { params: listParams(activeTab.value, pagination.pageNum, pagination.pageSize) })
    list.value = r?.records || []; pagination.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载物料退货失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}
/** 切二级页签（状态）：重置到第 1 页再查（筛选条件已由 叶子+类型页签+状态页签 表达） */
function handleTabChange() { pagination.pageNum = 1; loadData() }
function handleSearch() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.supplierId = undefined; handleSearch() }
/**
 * 页签角标：各页签条数 —— 每页取 1 条只读 total（零后端改动）。
 * 2026-09-29：叶子=类型 ⇒ 只有「页签」一个维度（退货退款 2 个 / 工厂维修 3 个）。
 */
async function loadCounts() {
  for (const t of tabs.value) {
    try {
      const r = await request.get<any, any>('/outsource/material-return/page', { params: listParams(t.key, 1, 1) })
      tabCounts[countKey(t.key)] = Number(r?.total || 0)
    } catch { tabCounts[countKey(t.key)] = 0 }
  }
}

/** 结案（仅维修退货）：全部送修数量已返回（未返回=0）后确认收尾 */
async function handleClose(row: any) {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/审核/反审核维修返回，也不能反审核本单（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/close`); ElMessage.success('已结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen(row: any) {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/re-open`); ElMessage.success('已撤销结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

/**
 * 审核提示按**行类型**区分（2026-09-28 三态）：
 * 订单退料 = 出源仓 + 扣关联订单的出货/收料数；退货退款 = 出源仓 + 冲减应付（P2 起改为"对供应商的应收"）；
 * 维修返回 = 只出库送修（不冲应付），修好回厂时在详情页登记维修返回。
 */
async function handleAudit(row: any) {
  const tip = isOrderReturnRow(row)
    ? ('确认审核该订单退料单？审核后物料出源仓，并扣减关联订单的出货/收料数量（永久扣减，反审核才加回）'
       + (row.materialOrderCode ? `：${row.materialOrderCode}` : ''))
    : (isRepairRow(row)
      ? '确认审核该维修返回单？审核后物料出源仓送供应商维修；「填了维修费」则按明细金额生成对供应商的应付。修好回厂时在详情页「登记维修返回」'
      : '确认审核该退货退款单？审核后物料出源仓，并生成「对供应商的应收」（供应商把货款退来后走收款核销）')
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/audit`); ElMessage.success('已审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit(row: any) {
  const tip = isOrderReturnRow(row)
    ? '确认反审核？将物料回源仓，并把关联订单的出货/收料数量加回'
    : (isRepairRow(row)
      ? '确认反审核？将送修物料回源仓（若有已审核的维修返回记录需先「反审核」；已生成的维修费应付会一并冲回）'
      : '确认反审核？将物料回源仓并冲回对供应商的应收（已有收款需先退款）')
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

/**
 * 新增：类型 = **当前叶子**（2026-09-29 叶子=类型）。
 * `linked` 固定 `WITHOUT_ORDER`：挂物料订单的关联退料已不在本模块发起 —— 它在「物料收退」做
 * （收退详情页工具栏「物料退货」→ RECEIVE_RETURN 记录：冲减该单已收数量 + 冲减应付）。
 */
function handleAdd() {
  router.push(`/outsource/material-return/add?returnType=${leafReturnType.value}&linked=WITHOUT_ORDER`)
}
/** 编辑草稿（D 档 2026-09-21）：复用新增页（后端 PUT /{id} 仅允许草稿） */
/* 2026-09-24（用户口径）：列表不再提供「编辑」 —— 草稿态统一在详情页内联改+存（handleEdit 已移除）；
   新增仍走 /outsource/material-return/add（可带 fromDelivery 等预填）。 */
function goDetail(row: any) { router.push(`/outsource/material-return/detail/${row.id}`) }

// 叶子切换（点左侧菜单 / 直达 URL）：页签回到**该叶子的第一个**（退货退款=草稿和已审核 / 工厂维修=待返回）
// ⚠️ 两个叶子的页签集**不同**（工厂维修多「待返回 / 已返回完」）⇒ 必须先重置 activeTab，
//    否则会停在另一个叶子的 key 上（无对应页签 ⇒ 不高亮 + 查询参数错位）
watch(leaf, () => {
  activeTab.value = tabs.value[0].key
  pagination.pageNum = 1
  loadData(); loadCounts()
})

// 详情/新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
useDomainRefresh('outsourceMaterialReturn', () => {
    loadData(); loadCounts()
}, OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY)
onMounted(() => {
  // 页签默认 = 该叶子的第一个（与 watch(leaf) 同口径；不设会出现"高亮与查询参数不匹配"的错位）
  activeTab.value = tabs.value[0].key
  loadData(); loadCounts()
})

</script>

<template>
  <!-- 一页一张卡片（家规）：页签 → 筛选行 → 表格 → 分页 -->
  <div class="page-list">
    <el-card shadow="never">
      <!-- 页签（**按叶子不同**；2026-09-29 用户口径「工厂维修需要补返回进度页签」）：
           退货退款 = 草稿和已审核 | 已作废；工厂维修 = **待返回 | 已返回完 | 已作废**
           （「待返回」含草稿 —— 未审核的单不能在任何页签里消失）。本页无「类型」页签：类型已由叶子表达；
           挂物料订单的关联退料改在「物料收退」做。标签后带**数量角标**。 -->
      <el-tabs v-model="activeTab" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane v-for="t in tabs" :key="t.key" :name="t.key">
          <template #label>
            <span>{{ t.label }}<span v-if="countOf(t.key)" style="margin-left:4px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ countOf(t.key) }}</span></span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 筛选行：叶子 → 类型 + linked；页签 → 状态/返回进度 ⇒ 这里只留单号 + 供应商 -->
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <span v-if="leaf === 'REPAIR'" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">工厂维修单（MRW-）：送供应商维修 → 回厂登记 → 全部返回后结案</span>
        <span v-else style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">退货退款单（MRW-）：物料退回供应商 + 生成对供应商的应收</span>
        <el-input v-model="query.code" placeholder="退货单号" clearable style="width:180px" @keyup.enter="handleSearch" />
        <RemoteSelect v-model="query.supplierId" add-route="/supplier/manage/add" :fetch="fetchSuppliers" placeholder="供应商" style="width:170px" domain="supplier" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <!-- 新增入口（2026-09-29）：类型 = **当前叶子**（工厂维修 / 退货退款）；`linked` 固定 WITHOUT_ORDER
             （挂物料订单的关联退料改在「物料收退」做：收退详情页工具栏「物料退货」）⇒ 本页新增的都是无单售后单。 -->
        <div style="margin-left:auto;display:flex;gap:8px">
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>

      <!-- 业务提示（按叶子）：说清这一页在干什么、后续在哪办、另一类去哪做 -->
      <el-alert v-if="leaf === 'REPAIR'" type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            <b>工厂维修</b>（单号 MRW-）：物料送供应商维修 → 修好回厂在<b>详情页登记维修返回</b>（先存草稿、审核后才入库）；
            <b>全部返回后可结案</b>（结案后不能再登记/反审核，需先撤销结案）；填了维修费则按明细生成<b>对供应商的应付</b>。
            上方按返回进度分「待返回（含草稿）/ 已返回完 / 已作废」，行内「送修/已返回」显示进度。
            要退<b>退货退款</b>请看左侧同名叶子；要退<b>挂了物料订单的料</b>请到<b>「物料收退」</b>（该单收货详细页点「物料退货」）。
          </span>
        </template>
      </el-alert>
      <el-alert v-else type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            <b>退货退款</b>（单号 MRW-）：物料退回供应商 → 审核后出源仓 + <b>生成对供应商的应收</b>
            （供应商把货款退来后走收款核销）；上方按状态分「草稿和已审核 / 已作废」。
            要送修请看左侧<b>工厂维修</b>叶子；要退<b>挂了物料订单的料</b>请到<b>「物料收退」</b>（该单收货详细页点「物料退货」，
            按新口径会冲减该单已收数量与应付）。
          </span>
        </template>
      </el-alert>
      <!-- 列宽合计（家规：≤948 —— 纵向滚动条出现时内容区从 963 缩到约 948）：
           2026-09-29（叶子=类型；去掉「关联物料订单」列，关联单不再进本模块列表）：
           退货退款 = 158+120+min240+90+74+132 = 814 ✓
           工厂维修 = 158+120+116(送修/已返回)+min200+90+122+132 = 938 ✓（状态列放宽到 122 容纳「已结案」标签）
           （公共列：单号 158 / 供应商 120 / 金额 90 / 操作 132。） -->
      <el-table :data="list" border stripe v-loading="loading" @row-click="goDetail">
        <!-- 2026-09-25（用户口径「数据显示完整 + 单号/仓库可点」）：退货单号 132→158（MRW-+11 位，实测需 157）
             并做成链接进详情。 -->
        <el-table-column label="退货单号" width="158" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column label="供应商" width="120" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName }}</el-button></template>
        </el-table-column>
        <!-- 送修 / 已返回（**只在「工厂维修」叶子**，2026-09-29 叶子=类型后归此一页）：
             该叶子全是维修单 ⇒ 返回进度用**独立列**（橙=供应商还没送完、绿=已全部送回，悬停给未返回数）。 -->
        <el-table-column v-if="showSentCol" label="送修/已返回" width="116" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ color: Number(row.unreturnedQty) > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontWeight: 500 }"
              :title="Number(row.unreturnedQty) > 0 ? ('还有 ' + row.unreturnedQty + ' 件未返回') : '已全部返回'">
              {{ row.sentQty ?? '-' }} / {{ row.returnedQty ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B10：原列名「退货/送修内容」7 字实测需 124px（本页给不出）⇒ 按家规改为短列名「明细」
             （4 字以下才放得下；列内仍是可点的物料明细 + tooltip，信息不丢）。
             2026-09-25：物料可点进「物料库存分布详情」（后端 items[]）。 -->
        <el-table-column label="明细" :min-width="showSentCol ? 200 : 240" show-overflow-tooltip>
          <template #default="{ row }">
            <EntityLinks :items="row.items" target="material" name-key="materialName" qty-key="quantity">
              <span>{{ row.itemSummary || '-' }}</span>
            </EntityLinks>
          </template>
        </el-table-column>
        <el-table-column label="退货金额" width="90" align="right">
          <template #default="{ row }">
            <!-- 订单退料不产生金额（不退款/不收费）⇒ 显示 —；退货退款=退款金额；维修返回=维修费（P3 落地） -->
            <span v-if="isOrderReturnRow(row)">-</span>
            <span v-else>{{ row.totalAmount != null ? Number(row.totalAmount).toFixed(2) : '-' }}</span>
          </template>
        </el-table-column>
        <!-- 状态：恒显示单据状态。「已结案」是维修返回的收尾标记（结案即全部返回，二者互斥）⇒
             在「维修返回」页签与本列并排显示（该页签状态列放宽到 122 才放得下两个 tag；其它页签 74 够用）。 -->
        <el-table-column label="状态" :width="showSentCol ? 122 : 74" align="center">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
            <el-tag v-if="showSentCol && row.closedFlag === 1" type="success" size="small" style="margin-left:4px">已结案</el-tag>
          </template>
        </el-table-column>
        <!-- 动作集与顺序统一（与加工侧一致）：详情 → 审核 → 反审核 → 作废 → 结案 → 撤销结案 -->
        <!-- 2026-09-24（用户口径）：反审核与编辑都收进详情页（详情草稿态可就地改+存）⇒ 操作列 176→132。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">详情</el-button>
            <el-button v-perm="'outsource:material-return'" type="success" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-perm="'outsource:material-return'" type="danger" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleCancel(row)">作废</el-button>
            <!-- 结案（**仅维修返回**，2026-09-28 起按行类型判定而非按叶子）：未返回=0 时才出现，代表跟踪终点 -->
            <el-button v-perm="'outsource:material-return'" type="success" link v-if="isRepairRow(row) && row.status===DocStatus.AUDITED && row.closedFlag!==1 && Number(row.unreturnedQty)===0" @click.stop="handleClose(row)">结案</el-button>
            <el-button v-perm="'outsource:material-return'" type="warning" link v-if="isRepairRow(row) && row.closedFlag===1" @click.stop="handleReOpen(row)">撤销结案</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSearch" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>
