<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { localDate } from '@/utils/date'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import {
  WarehouseType, ProductQualityType, ProductQualityTypeLabel,
  AfterSaleSourceType,
  INVENTORY_RETURN_SORT_DIRTY_KEY,
} from '@/api/enums'
import {
  getReturnSortPage, auditReturnSort, cancelReturnSort, deleteReturnSort,
  getReturnSortPendingOverview, batchCreateReturnSortDrafts, type ReturnSortPendingRow,
} from '@/api/inventory'
import { useDomainRefresh } from '@/utils/dataFreshness'
const route = useRoute()
const router = useRouter()

// ==================== 页签（2026-09-19 退货整理页优化；2026-09-28 加「已整理」） ====================
/**
 * ① 待整理（默认）：跨**自有成品仓**看"哪些仓还有什么要整理"——两态 可整理/实物不足（账实不符）。
 *    可勾选多个批次**批量生成整理草稿**；点行「整理」/「整理本仓」跳到独立开单页
 *    （/inventory/return-sort/add + query 预设，2026-09-22 由抽屉改为页面，与「编辑退货整理」共用 form.vue）。
 * ② 已整理（2026-09-28 用户口径）：同一份跨仓总览里**已整理完**（`sortedQuantity >= quantity`）的批次，
 *    **只读**（无勾选/无整理按钮——已无待整理量）。原先是待整理页里的「显示已整理完」开关，
 *    现改为独立页签：待办与历史各自成页，待整理不再被历史淹没。
 * ③ 整理单：原有的整理单列表（查询 + 审核/反审核/删除）。
 * 页签同步到 URL（?tab=cleared / ?tab=bills），可直接落到目标页（回归脚本长期依赖列表页）。
 */
const TAB_KEYS = ['pending', 'cleared', 'bills'] as const
function normTab(v: unknown): string { const s = String(v || ''); return (TAB_KEYS as readonly string[]).includes(s) ? s : 'pending' }
const activeTab = ref<string>(normTab(route.query.tab))
/** 当前是否停在「已整理」页签 —— 决定总览的取数口径（是否含已整理完的批次） */
const isClearedTab = computed(() => activeTab.value === 'cleared')
/** 是否停在某个**总览**页签（待整理/已整理共用同一份跨仓总览数据） */
const isOverviewTab = computed(() => activeTab.value === 'pending' || activeTab.value === 'cleared')
watch(activeTab, (v) => {
  if (String(route.query.tab || '') === v) return
  // 与模版页（views/template/index.vue）同一约定：**显式给 path**（只给 query 会丢 path）
  router.replace({ path: route.path, query: { ...route.query, tab: v } })
})
// 反向同步：浏览器前进/后退、外部深链 `?tab=bills` 时把页签切回来
watch(() => route.query.tab, (t) => {
  const v = normTab(t)
  if (v !== activeTab.value) activeTab.value = v
})

const query = reactive({ code: '', status: '' as string | number, warehouseId: '' as string | number })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<any[]>([])
const warehouseOptions = ref<any[]>([])

// 源仓库：自有成品仓（2026-09-16 方案 A：原"售后仓"取消，退回品直接压在成品仓、品质 PENDING）
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })

// 列表
async function loadData() {
  tableLoading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.status) params.status = query.status
    if (query.warehouseId) params.warehouseId = query.warehouseId
    const res = await getReturnSortPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = []; pagination.total = 0 } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
/** 2026-09-20（F7-181）：改每页条数时回到第 1 页（与 stock-loss/index.vue:146 同口径） */
function onSizeChange(v: number) { pagination.pageSize = v; pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.status = ''; query.warehouseId = ''; pagination.pageNum = 1; loadData() }

// 新增走独立页面；详情统一一个入口
// 2026-09-24（用户口径）：草稿态在**详情页**就地编辑 ⇒ 独立编辑页 `/inventory/return-sort/edit/:id` 已删除
function goAdd() { router.push('/inventory/return-sort/add') }
function goDetail(row: any) { router.push(`/inventory/return-sort/detail/${row.id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
/**
 * 来源单据跳转（2026-09-22）：总览行「来源单据」单号点击 → 对应来源单据详情。
 * 换货单 → /sale/exchange/detail/{id}；销售退货单 → /sale/return/detail/{id}。
 * 与退货整理详情页 goSource 完全同口径（两处必须一致，改一处记得改另一处）。
 */
function goSource(row: any) {
  if (!row.sourceId) return
  if (row.sourceType === AfterSaleSourceType.SALE_EXCHANGE) router.push(`/sale/exchange/detail/${row.sourceId}`)
  else router.push(`/sale/return/detail/${row.sourceId}`)
}

/** 客户 → 客户详情（2026-09-26 B4：待整理总览的「客户」列由纯文本改为可点） */
function goCustomer(id?: number) { if (id) router.push(`/inventory/customer/detail/${id}`) }

/** 行点击/详情：统一进详情页（2026-09-24 —— 草稿态在详情页就地改+存，不再分流到独立编辑页） */
function openRow(row: any) {
  goDetail(row)
}

async function handleAudit(row: any) {
  // 2026-09-20（F7-173）：confirm 与业务请求**必须各自 try/catch** —— 原先共用一个 try/catch，
  // `catch { /* 取消 */ }` 会把**接口报错也当成"用户取消"静默吞掉**；审核/反审核/删除都是**不可逆库存动作**，
  // 失败却无任何提示（用户以为是自己点了取消）。同单据 `detail.vue:78-98` 已是正确范式，此处对齐。
  try {
    await ElMessageBox.confirm(`确认审核单号「${row.code}」？审核后将从成品仓扣减待整理品并分品质入库（A/B/C/不良 均入成品仓，按品质区分）。`, '审核确认', { type: 'warning' })
  } catch { return }
  try {
    await auditReturnSort(row.id)
    ElMessage.success('已审核')
    loadData()
    loadOverview()
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核单号「${row.code}」？反审核后将逆向恢复库存。`, '反审核确认', { type: 'warning' })
  } catch { return }
  try {
    await cancelReturnSort(row.id)
    ElMessage.success('已反审核')
    loadData()
    loadOverview()
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除草稿单「${row.code}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await deleteReturnSort(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

async function loadWarehouses() {
  try { const res: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY' } }); warehouseOptions.value = res?.records || [] } catch { warehouseOptions.value = [] }
}
function warehouseName(id?: number) { const w = warehouseOptions.value.find((x: any) => x.id === id); return w ? w.warehouseName : '' }

// 从库存流水点击关联单号跳转：统一进详情页（草稿态在详情页就地编辑）
function openFromStockLog() {
  const billId = route.query.billId
  if (!billId) return
  activeTab.value = 'bills'
  const row = tableData.value.find((r: any) => r.id === Number(billId))
  if (row) openRow(row)
}

// ==================== 跨仓总览（待整理 / 已整理 两个页签共用同一份数据） ====================
const pendingLoading = ref(false)
/** 只看超期（停留天数 > 阈值）—— 仅「待整理」页签有该开关（已整理完的批次不再谈超期） */
const overdueOnly = ref(false)
const overview = ref<any>({ warehouses: [], summary: {} })
/** 展开的仓库分组（默认全展开；用 v-model 以便用户手动折叠） */
const activeGroups = ref<string[]>([])
/** 表格重建版本号：刷新后清空勾选，避免残留已消失的行 */
const selVersion = ref(0)
const selectedByWh = ref<Record<string, ReturnSortPendingRow[]>>({})
const stayAlertDays = computed(() => Number(overview.value?.stayAlertDays ?? 3))

async function loadOverview() {
  pendingLoading.value = true
  try {
    // 2026-09-28（用户口径：开关改页签）：待整理页只要**未整理完**的批次，已整理页要**含**已整理完的批次
    // —— 是否返回已整理完的批次由后端 includeCleared 决定（服务端语义见 verify-return-sort-overview.ps1）。
    overview.value = (await getReturnSortPendingOverview({ includeCleared: isClearedTab.value })) || { warehouses: [], summary: {} }
    selectedByWh.value = {}
    selVersion.value++
    // 默认展开所有有批次的仓（刷新后保持"一眼看全"）
    activeGroups.value = (overview.value.warehouses || []).map((g: any) => String(g.warehouseId))
  } catch {
    overview.value = { warehouses: [], summary: {} }
    activeGroups.value = []
  } finally { pendingLoading.value = false }
}

/** 页签切换 ⇒ 取数口径变了（是否含已整理完），重拉一次总览；整理单页签不触发 */
watch(isClearedTab, () => { if (isOverviewTab.value) loadOverview() })

/** 「待整理」分组视图：只留**未整理完**的批次，按「只看超期」过滤并丢掉空分组；各计数按**可见行**重算 */
const groupsView = computed(() => {
  const list = overview.value?.warehouses || []
  return list.map((g: any) => {
    const rows: ReturnSortPendingRow[] = (g.rows || [])
      .filter((r: ReturnSortPendingRow) => r.status !== 'CLEARED')   // 双保险：该页签永不显示已整理完
      .filter((r: ReturnSortPendingRow) => !overdueOnly.value || r.overdue)
    let sortableCount = 0, shortageCount = 0, overdueCount = 0
    let sortableQuantity = 0, remainQuantity = 0
    for (const r of rows) {
      if (r.status === 'SORTABLE') { sortableCount++; sortableQuantity += Number(r.quantity || 0) }
      else if (r.status === 'SHORTAGE') shortageCount++
      if (r.overdue) overdueCount++
      remainQuantity += Number(r.remainQuantity || 0)
    }
    return { ...g, rows, sortableCount, shortageCount, overdueCount, sortableQuantity, remainQuantity }
  }).filter((g: any) => g.rows.length > 0)
})

/**
 * 「已整理」分组视图（2026-09-28 用户口径）：同一份总览里**已整理完**的批次（`sortedQuantity >= quantity`），
 * 分组与计数口径与待整理一致（按**可见行**重算），仅供只读展示。
 */
const groupsCleared = computed(() => {
  const list = overview.value?.warehouses || []
  return list.map((g: any) => {
    const rows: ReturnSortPendingRow[] = (g.rows || []).filter((r: ReturnSortPendingRow) => r.status === 'CLEARED')
    return {
      ...g, rows,
      clearedCount: rows.length,
      sortedQuantity: rows.reduce((s: number, r: ReturnSortPendingRow) => s + Number(r.sortedQuantity || 0), 0),
    }
  }).filter((g: any) => g.rows.length > 0)
})
/** 已整理页签的汇总（页签头那行文字用它） */
const clearedSummary = computed(() => ({
  batchCount: groupsCleared.value.reduce((n: number, g: any) => n + g.clearedCount, 0),
  sortedQuantity: groupsCleared.value.reduce((s: number, g: any) => s + g.sortedQuantity, 0),
}))
/** 是否已有任何分组（含被过滤掉的）—— 用于区分"本来就没有批次"与"被过滤空了" */
const hasAnyGroup = computed(() => (overview.value?.warehouses || []).length > 0)
/** 仓里有批次但被"只看超期"过滤空了 */
const filteredEmpty = computed(() => hasAnyGroup.value && groupsView.value.length === 0)

const STATUS_META: Record<string, { label: string; type: 'success' | 'danger' | 'info' }> = {
  SORTABLE: { label: '可整理', type: 'success' },
  SHORTAGE: { label: '实物不足', type: 'danger' },
  CLEARED: { label: '已整理完', type: 'info' },
}
function statusMeta(s?: string) { return STATUS_META[s || ''] || { label: s || '-', type: 'info' as const } }

function onSelChange(whId: any, rows: ReturnSortPendingRow[]) {
  selectedByWh.value = { ...selectedByWh.value, [String(whId)]: rows }
}
const selectedRows = computed(() => Object.values(selectedByWh.value).flat())
const selectedIds = computed(() => selectedRows.value.map((r) => Number(r.pendingId)).filter((n) => n > 0))
const selectedQty = computed(() => selectedRows.value.reduce((s, r) => s + Number(r.quantity || 0), 0))

// ==================== 批量生成整理草稿 ====================
const batchVisible = ref(false)
const batchSaving = ref(false)
const batchForm = reactive({
  defaultQuality: ProductQualityType.A as string,
  sortDate: localDate(),
  remark: '',
})
// 2026-09-22 用户口径：分选后**默认回到源仓库** ⇒ 批量生成草稿时不再需要选/预填 A/B/C/不良 目标仓
// （服务端按每张草稿自己的源仓库回填，一张草稿只对应一个仓库/客户）

async function openBatch() {
  if (selectedIds.value.length === 0) { ElMessage.warning('请先勾选需要整理的来源批次'); return }
  batchForm.sortDate = localDate()
  batchForm.defaultQuality = ProductQualityType.A
  batchVisible.value = true
}

async function submitBatch() {
  batchSaving.value = true
  try {
    const data = {
      pendingIds: selectedIds.value,
      defaultQuality: batchForm.defaultQuality,
      sortDate: batchForm.sortDate,
      remark: batchForm.remark,
    }
    const res: any = await batchCreateReturnSortDrafts(data)
    const n = Number(res?.draftCount || 0)
    const skipped = res?.skipped || []
    if (skipped.length > 0) {
      ElMessage.warning(`已生成 ${n} 张整理草稿；${skipped.length} 个批次被跳过：${skipped[0].reason}`)
    } else {
      ElMessage.success(`已生成 ${n} 张整理草稿（按仓库/客户分单），请到「整理单」页签审核`)
    }
    batchVisible.value = false
    await loadOverview()
    pagination.pageNum = 1
    await loadData()
    activeTab.value = 'bills'
  } catch (e: any) {
    ElMessage.error(e?.message || '批量生成失败')
  } finally { batchSaving.value = false }
}

// ==================== 开单入口（2026-09-22 用户要求：不再用抽屉，改为跳「新增退货整理」页面） ====================
/**
 * 跳新增页并带预设：整仓整理（row 为空）或单个来源批次（row 非空）。
 * <p>页面（form.vue）从 route.query 读 warehouseId / pendingIds；保存后回列表，
 * 靠 DIRTY 标志让「待整理总览 + 整理单列表」各刷新一次（见 onActivated）。</p>
 */
function gotoSortForm(whId: number, row?: ReturnSortPendingRow) {
  const query: Record<string, string> = { warehouseId: String(whId) }
  if (row && row.pendingId) query.pendingIds = String(row.pendingId)
  router.push({ path: '/inventory/return-sort/add', query })
}

onMounted(async () => {
  await loadData()
  loadWarehouses()
  loadOverview()
  openFromStockLog()
})
// 2026-09-20（F7-174）：本路由在 keep-alive 内 ⇒ 从新增/编辑页返回时组件被复用、onMounted 不再触发，
// 列表会停留在旧数据。改为按需刷新：写操作页（form.vue 的独立模式）保存成功后置脏标志，回列表才拉一次。
useDomainRefresh('returnSort', () => {
    loadData()
    loadOverview()
}, INVENTORY_RETURN_SORT_DIRTY_KEY)
</script>

<template>
  <div class="page-list">
    <el-tabs v-model="activeTab">
      <!-- ==================== ① 待整理（跨仓总览） ==================== -->
      <el-tab-pane name="pending">
        <!-- 计数单独成元素：页签文本本身保持精确的「待整理」，UI 脚本可按文本点选 -->
        <template #label>
          <span>待整理</span>
          <span v-if="Number(overview.summary?.sortableCount || 0) > 0" style="margin-left:2px">（{{ overview.summary.sortableCount }}）</span>
        </template>

        <div v-loading="pendingLoading">
          <div class="pending-bar">
            <!-- 2026-09-28（用户口径）：原「显示已整理完」开关**下线** —— 已整理完的批次改由「已整理」页签查看 -->
            <el-switch v-model="overdueOnly" active-text="只看超期" />
            <el-button :icon="'Refresh'" @click="loadOverview">刷新</el-button>
            <span class="pending-summary">
              {{ groupsView.length }} 个仓 · {{ overview.summary?.batchCount ?? 0 }} 个批次 · 待整理
              {{ Number(overview.summary?.remainQuantity || 0) }} 件 · 可整理
              <b>{{ Number(overview.summary?.sortableQuantity || 0) }}</b> 件
              <template v-if="Number(overview.summary?.shortageCount || 0) > 0">
                · <span style="color:#f56c6c">实物不足 {{ overview.summary.shortageCount }} 批（账实不符，需盘库）</span>
              </template>
              <template v-if="Number(overview.summary?.overdueCount || 0) > 0">
                · <span style="color:#e6a23c">超期 {{ overview.summary.overdueCount }} 批（停留 &gt; {{ stayAlertDays }} 天）</span>
              </template>
            </span>
            <div class="pending-actions">
              <span v-if="selectedIds.length > 0" class="sel-tip">已选 {{ selectedIds.length }} 批 / {{ selectedQty }} 件</span>
              <el-button type="primary" :icon="'Plus'" :disabled="selectedIds.length === 0" @click="openBatch">批量生成整理草稿</el-button>
            </div>
          </div>

          <el-alert v-if="!hasAnyGroup" type="success" :closable="false" show-icon
            title="暂无待整理批次：自有成品仓的待整理库存已全部整理。"
            description="已整理完的历史批次请切到「已整理」页签查看。" />
          <el-alert v-else-if="filteredEmpty" type="info" :closable="false" show-icon
            title="没有超期的待整理批次（已按「只看超期」过滤）。" />

          <el-collapse v-else v-model="activeGroups" style="margin-top:8px">
            <el-collapse-item v-for="g in groupsView" :key="String(g.warehouseId)" :name="String(g.warehouseId)">
              <template #title>
                <span style="font-weight:600;margin-right:8px">{{ g.warehouseName }}</span>
                <el-tag v-if="g.sortableCount > 0" type="success" size="small" style="margin-right:4px">可整理 {{ g.sortableCount }} 批 / {{ g.sortableQuantity }} 件</el-tag>
                <el-tag v-if="g.shortageCount > 0" type="danger" size="small" style="margin-right:4px">实物不足 {{ g.shortageCount }} 批</el-tag>
                <el-tag v-if="g.overdueCount > 0" type="warning" size="small" style="margin-right:4px">超期 {{ g.overdueCount }} 批</el-tag>
                <span class="wh-extra">最早停留 {{ g.oldestStayDays }} 天 · 待整理合计 {{ g.remainQuantity }} 件</span>
              </template>

              <div v-if="g.sortableCount > 0" style="margin-bottom:8px">
                <el-button type="primary" link :icon="'Edit'" @click="gotoSortForm(g.warehouseId)">整理本仓（带出 {{ g.sortableCount }} 批可整理）</el-button>
              </div>

              <el-table :key="`${g.warehouseId}-${selVersion}`" :data="g.rows" border size="small" row-key="pendingId" stripe
                @selection-change="(rows: any) => onSelChange(g.warehouseId, rows)">
                <!--
                  列宽预算（2026-09-22 用户要求：列表一行显示完、不要左右滑动，且**表头不能被截断**）：
                  Σ = 34+78+150+82+84+94+110+46+110+60+76+58 = 982px ≤ 容器 1005px ⇒ 不横向滚动。
                  「产品」是 min-width 列，富余宽度全给它（实测约 175px）。
                  每条列宽按**实测表头需要宽**定（探针：th .cell 的 scrollWidth ≤ clientWidth）；
                  曾被裁的表头与修前值：待整理/已整理 94→110 · 停留天数 66→76。
                  历史：原 Σ=1309px 溢出 304px；收紧后本表曾把表头压裁 ⇒ 现按表头实测回调，其余列各让 2~6px。
                  **改列宽前请先加总并确认表头没被裁**（跑 verify-returnsort-list-fit.ps1）。
                -->
                <!-- 2026-09-26 B4（用户口径「显示完整」，实测驱动）：
                     ① 来源日期 82→**106**（实测需 106，原先全部行被截断）；
                     ② 客户 84→**118** 并做成链接进客户详情（行自带 customerId；表格内 link 按钮左右内边距已被
                        全局样式清零，故只需"文本需宽 114 + 少量"）；
                     ③ SKU 94→**110**（实测需 110，原 94 也被截断）；
                     ④ 为抵平把产品 min110→84（弹性列，宽屏吃余量）、待整理/已整理 110→104、可整理 60→60、
                        停留天数 76→76 保持、状态 78→72、来源单据 150→142、操作 58→56、单位 46→44。
                     ⚠️ 本表 12 列、容器仅 1005px，**改动前请跑 verify-returnsort-list-fit.ps1**
                        （它同时断言"表头不被裁"—— 待整理/已整理≥104、可整理≥60、停留天数≥76 是表头下限）。
                     声明合计 1006 ≤ 1005+2（守卫容差）。 -->
                <el-table-column type="selection" width="34" :selectable="(row: any) => row.status === 'SORTABLE'" />
                <el-table-column label="状态" width="72">
                  <template #default="{ row }">
                    <el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).label }}</el-tag>
                    <el-tooltip v-if="row.partial" content="实物少于批次剩余量，只能先整理可整理部分" placement="top">
                      <span style="color:#e6a23c;margin-left:2px">部分</span>
                    </el-tooltip>
                  </template>
                </el-table-column>
                <!-- 来源单据（2026-09-22 用户要求，与新增/编辑页、详情页统一口径）：
                     ① 只显示单据号（去掉「销售退货单/销售换货单」类型标签）；
                     ② 单号可点击 → 进来源单据详情（换货单去换货详情、销售退货单去退货单详情，同详情页 goSource）。
                     列宽 116→150 让单号完整显示（去掉标签后仍有富余，不再被省略号截断）。 -->
                <!-- 2026-09-26 B6（实测）：本列正文是**链接按钮**（不是纯文本），最长单号「XTH-20260921022」实测
                     131px ⇒ 需要 131 + 内边距 16 + 边框 1 + 2 余量 = **150**；原先 142 / 一度收 136 都会让 6 行被省略。
                     同批把「单位」44→48、「可整理」60→62 补到安全值（此前差 1px / 余量 1px），
                     宽度从同表富余列回收：来源日期 106→98、SKU 110→100、状态 72→68（三处正文分别只需 89/96/59）。 -->
                <el-table-column label="来源单据" width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-button v-if="row.sourceId" type="primary" link @click="goSource(row)">{{ row.sourceCode || '-' }}</el-button>
                    <span v-else>{{ row.sourceCode || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="来源日期" width="98">
                  <template #default="{ row }">{{ row.sourceDate || '-' }}</template>
                </el-table-column>
                <!-- 2026-09-26 B4：客户 84→118 并做成链接进客户详情（行自带 customerId） -->
                <el-table-column label="客户" width="118" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-button v-if="row.customerId" type="primary" link @click.stop="goCustomer(row.customerId)">{{ row.customerName || '-' }}</el-button>
                    <span v-else>{{ row.customerName || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="sku" label="SKU" width="100" show-overflow-tooltip />
                <el-table-column prop="productName" label="产品" min-width="80" show-overflow-tooltip />
                <!-- 2026-09-26 B6：44→48（表头「单位」2 字需 28 + 内边距 16 + 边框 1 + 余量 3；44 时内容框仅 27px ⇒「单…」） -->
                <el-table-column prop="unit" label="单位" width="48" />
                <el-table-column label="待整理/已整理" width="110" align="center">
                  <template #default="{ row }">{{ row.remainQuantity ?? 0 }} / {{ row.sortedQuantity ?? 0 }}</template>
                </el-table-column>
                <!-- 2026-09-26 B6：60→62（表头「可整理」3 字需 42 + 内边距 16 + 边框 1 + 余量 3） -->
                <el-table-column label="可整理" width="62" align="center">
                  <template #default="{ row }">
                    <b :style="row.status === 'SHORTAGE' ? 'color:#f56c6c' : ''">{{ row.quantity ?? 0 }}</b>
                  </template>
                </el-table-column>
                <el-table-column label="停留天数" width="76" align="center">
                  <template #default="{ row }">
                    <span :style="row.overdue ? 'color:#f56c6c;font-weight:bold' : ''">
                      {{ row.stayDays ?? '-' }}<span v-if="row.overdue"> 超期</span>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="54" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="row.status === 'SORTABLE'" type="primary" link @click="gotoSortForm(g.warehouseId, row)">整理</el-button>
                    <span v-else style="color:#909399">—</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-tab-pane>

      <!-- ==================== ② 已整理（2026-09-28 用户口径：原「显示已整理完」开关改为独立页签） ====================
           同一份跨仓总览，只取**已整理完**的批次（sortedQuantity >= quantity），**只读**：
           已无待整理量 ⇒ 不给勾选/「整理」/批量开单（原开关态的开关已下线）。
           列宽预算：150+98+118+100+48+110+94 = 718（固定）+ 产品 min200 = 918 ≤ 容器 1005 ⇒ 一行不横滑。 -->
      <el-tab-pane name="cleared" lazy>
        <template #label><span>已整理</span></template>
        <div v-loading="pendingLoading">
          <div class="pending-bar">
            <el-button :icon="'Refresh'" @click="loadOverview">刷新</el-button>
            <span class="pending-summary">
              {{ groupsCleared.length }} 个仓 · {{ clearedSummary.batchCount }} 个批次已整理完 · 累计已整理
              <b>{{ clearedSummary.sortedQuantity }}</b> 件
            </span>
          </div>

          <el-alert v-if="groupsCleared.length === 0" type="info" :closable="false" show-icon
            title="暂无已整理完的批次：自有成品仓还没有整批整理完的来源批次。" />

          <el-collapse v-else v-model="activeGroups" style="margin-top:8px">
            <el-collapse-item v-for="g in groupsCleared" :key="String(g.warehouseId)" :name="String(g.warehouseId)">
              <template #title>
                <span style="font-weight:600;margin-right:8px">{{ g.warehouseName }}</span>
                <el-tag type="info" size="small" style="margin-right:4px">已整理完 {{ g.clearedCount }} 批</el-tag>
                <span class="wh-extra">已整理合计 {{ g.sortedQuantity }} 件</span>
              </template>

              <el-table :data="g.rows" border size="small" row-key="pendingId" stripe>
                <el-table-column label="来源单据" width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-button v-if="row.sourceId" type="primary" link @click="goSource(row)">{{ row.sourceCode || '-' }}</el-button>
                    <span v-else>{{ row.sourceCode || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="来源日期" width="98">
                  <template #default="{ row }">{{ row.sourceDate || '-' }}</template>
                </el-table-column>
                <el-table-column label="客户" width="118" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-button v-if="row.customerId" type="primary" link @click.stop="goCustomer(row.customerId)">{{ row.customerName || '-' }}</el-button>
                    <span v-else>{{ row.customerName || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="sku" label="SKU" width="100" show-overflow-tooltip />
                <el-table-column prop="productName" label="产品" min-width="200" show-overflow-tooltip />
                <el-table-column prop="unit" label="单位" width="48" />
                <el-table-column label="已整理数量" width="110" align="center">
                  <template #default="{ row }"><b>{{ row.sortedQuantity ?? 0 }}</b></template>
                </el-table-column>
                <el-table-column label="状态" width="94">
                  <template #default="{ row }"><el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).label }}</el-tag></template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-tab-pane>

      <!-- ==================== ③ 整理单列表 ==================== -->
      <el-tab-pane name="bills" lazy>
        <template #label><span>整理单</span></template>

        <el-card shadow="never" class="query-card">
          <div class="query-bar">
            <el-form :inline="true" :model="query" class="query-form">
              <el-form-item label="单号"><el-input v-model="query.code" placeholder="单号" clearable @keyup.enter="handleQuery" /></el-form-item>
              <el-form-item label="源仓库">
                <RemoteSelect v-model="query.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" clearable style="width:160px" domain="warehouse" />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
                  <el-option :label="DocStatusLabel[DocStatus.DRAFT]" :value="DocStatus.DRAFT" /><el-option :label="DocStatusLabel[DocStatus.AUDITED]" :value="DocStatus.AUDITED" /><el-option :label="DocStatusLabel[DocStatus.CANCELLED]" :value="DocStatus.CANCELLED" />
                </el-select>
              </el-form-item>
            </el-form>
            <div class="toolbar">
              <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
              <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
              <el-button type="success" :icon="'Plus'" @click="goAdd">新增</el-button>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="table-card" style="margin-top:12px">
          <el-table :data="tableData" border v-loading="tableLoading" row-key="id" @row-click="openRow" stripe>
            <el-table-column prop="sortDate" label="整理日期" width="110" />
            <el-table-column prop="code" label="单号" width="170" />
            <el-table-column label="源仓库" width="130">
              <template #default="{ row }">
                <el-button v-if="row.warehouseId" type="primary" link @click.stop="goWarehouse(row.warehouseId)">{{ warehouseName(row.warehouseId) }}</el-button>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <!-- 整理概况：产品名 + 分选结果（后端按明细拼接，超长省略） -->
            <el-table-column label="整理概况" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sortSummary || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="DocStatusTag[row.status]||'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <!-- 2026-09-24（用户口径）：反审核移入详情页 ⇒ 操作列 200→132（详情/审核/删除 3 个按钮）。 -->
            <el-table-column label="操作" width="132" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click.stop="openRow(row)">详情</el-button>
                <el-button v-perm="'stock:return-sort'" v-if="row.status === DocStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
                <el-button v-if="row.status === DocStatus.DRAFT" type="danger" link @click.stop="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <!-- 2026-09-20（F7-181）：改每页条数时必须回到第 1 页（原先 @change 直接 loadData） -->
            <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
              :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
              layout="total, sizes, prev, pager, next, jumper" background
              @size-change="onSizeChange" @current-change="loadData" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 批量生成整理草稿：按 (仓库, 客户) 各生成一张草稿；分选数量先按所选品质整批预置 -->
    <el-dialog v-model="batchVisible" title="批量生成整理草稿" width="var(--app-dialog-md)">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        :title="`已勾选 ${selectedIds.length} 个来源批次 / ${selectedQty} 件`"
        description="服务端按 (仓库, 客户) 各生成一张草稿（一张整理单只对应一个客户）；分选数量先按所选品质整批预置，之后可到「整理单」里按单调整。" />
      <el-form :model="batchForm" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="默认分选品质" required>
              <el-select v-model="batchForm.defaultQuality" style="width:100%">
                <el-option :label="ProductQualityTypeLabel[ProductQualityType.A]" :value="ProductQualityType.A" />
                <el-option :label="ProductQualityTypeLabel[ProductQualityType.B]" :value="ProductQualityType.B" />
                <el-option :label="ProductQualityTypeLabel[ProductQualityType.C]" :value="ProductQualityType.C" />
                <el-option :label="ProductQualityTypeLabel[ProductQualityType.DEFECT]" :value="ProductQualityType.DEFECT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="整理日期"><el-input v-model="batchForm.sortDate" type="date" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注"><el-input v-model="batchForm.remark" placeholder="选填" /></el-form-item>
          </el-col>
          <!-- 2026-09-22 用户口径：分选后**默认回到源仓库** ⇒ 不再让用户逐个选 A/B/C/不良 入库仓，
               服务端按每张草稿自己的源仓库回填（不良品用品质 DEFECT 区分，不需要独立不良仓） -->
          <el-col :span="24">
            <el-form-item label="入库仓库">
              <span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">
                分选后按品质回到<b>各批次自己的源仓库</b>（A/B/C/不良 均入源仓，以品质区分），无需逐个选择
              </span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="submitBatch">生成整理草稿</el-button>
      </template>
    </el-dialog>

    <!-- 2026-09-22 用户要求：原「整理待整理品」抽屉已去掉，改为跳「新增退货整理」页面
         （/inventory/return-sort/add + warehouseId/pendingIds 预设）—— 与「编辑退货整理」共用 form.vue，
         两个入口行为完全一致（保存后回列表，靠 DIRTY 标志刷新总览与整理单列表）。 -->
  </div>
</template>

<style scoped>
.pending-bar { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.pending-summary { color: var(--app-text-secondary); font-size: var(--app-font-base); }
.pending-actions { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.sel-tip { color: var(--el-color-primary); font-size: var(--app-font-base); }
.wh-extra { color: var(--app-text-secondary); font-size: var(--app-font-xs); }
</style>
