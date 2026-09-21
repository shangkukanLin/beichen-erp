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
  AfterSaleSourceType, AfterSaleSourceTypeLabel,
  INVENTORY_RETURN_SORT_DIRTY_KEY,
} from '@/api/enums'
import {
  getReturnSortPage, auditReturnSort, cancelReturnSort, deleteReturnSort,
  getReturnSortPendingOverview, batchCreateReturnSortDrafts, type ReturnSortPendingRow,
} from '@/api/inventory'
const route = useRoute()
const router = useRouter()

// ==================== 页签（2026-09-19 退货整理页优化） ====================
/**
 * ① 待整理（默认）：跨**自有成品仓**看"哪些仓还有什么要整理"——三态 可整理/实物不足（账实不符）/
 *    已整理完（默认隐藏）。可勾选多个批次**批量生成整理草稿**；点行「整理」/「整理本仓」跳到独立开单页
 *    （/inventory/return-sort/add + query 预设，2026-09-22 由抽屉改为页面，与「编辑退货整理」共用 form.vue）。
 * ② 整理单：原有的整理单列表（查询 + 审核/反审核/删除）。
 * 页签同步到 URL（?tab=bills），可直接落到列表（回归脚本长期依赖列表页）。
 */
const activeTab = ref<string>(route.query.tab === 'bills' ? 'bills' : 'pending')
watch(activeTab, (v) => {
  if (String(route.query.tab || '') === v) return
  // 与模版页（views/template/index.vue）同一约定：**显式给 path**（只给 query 会丢 path）
  router.replace({ path: route.path, query: { ...route.query, tab: v } })
})
// 反向同步：浏览器前进/后退、外部深链 `?tab=bills` 时把页签切回来
watch(() => route.query.tab, (t) => {
  const v = String(t || '') === 'bills' ? 'bills' : 'pending'
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

// 新增/编辑/详情统一走独立页面（title 各不相同）
function goAdd() { router.push('/inventory/return-sort/add') }
function goEdit(row: any) { router.push(`/inventory/return-sort/edit/${row.id}`) }
function goDetail(row: any) { router.push(`/inventory/return-sort/detail/${row.id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }

/** 行点击/详情按状态分流：草稿进编辑页（可直接改），其余进只读详情 */
function openRow(row: any) {
  if (row.status === DocStatus.DRAFT) goEdit(row)
  else goDetail(row)
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
    ElMessage.success('审核成功')
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
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

async function loadWarehouses() {
  try { const res: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY' } }); warehouseOptions.value = res?.records || [] } catch { warehouseOptions.value = [] }
}
function warehouseName(id?: number) { const w = warehouseOptions.value.find((x: any) => x.id === id); return w ? w.warehouseName : '' }

// 从库存流水点击关联单号跳转：草稿打开编辑页，其余进详情
function openFromStockLog() {
  const billId = route.query.billId
  if (!billId) return
  activeTab.value = 'bills'
  const row = tableData.value.find((r: any) => r.id === Number(billId))
  if (row) openRow(row)
}

// ==================== 待整理总览（跨自有成品仓） ====================
const pendingLoading = ref(false)
/** 显示「已整理完」的历史批次（默认隐藏，否则历史会把待办淹没） */
const includeCleared = ref(false)
/** 只看超期（停留天数 > 阈值） */
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
    overview.value = (await getReturnSortPendingOverview({ includeCleared: includeCleared.value })) || { warehouses: [], summary: {} }
    selectedByWh.value = {}
    selVersion.value++
    // 默认展开所有有批次的仓（刷新后保持"一眼看全"）
    activeGroups.value = (overview.value.warehouses || []).map((g: any) => String(g.warehouseId))
  } catch {
    overview.value = { warehouses: [], summary: {} }
    activeGroups.value = []
  } finally { pendingLoading.value = false }
}

/** 分组视图：按「只看超期」过滤并丢掉空分组；各计数按**可见行**重算，避免过滤后数字对不上 */
const groupsView = computed(() => {
  const list = overview.value?.warehouses || []
  return list.map((g: any) => {
    const rows: ReturnSortPendingRow[] = (g.rows || []).filter((r: ReturnSortPendingRow) => !overdueOnly.value || r.overdue)
    let sortableCount = 0, shortageCount = 0, clearedCount = 0, overdueCount = 0
    let sortableQuantity = 0, remainQuantity = 0
    for (const r of rows) {
      if (r.status === 'SORTABLE') { sortableCount++; sortableQuantity += Number(r.quantity || 0) }
      else if (r.status === 'SHORTAGE') shortageCount++
      else clearedCount++
      if (r.overdue) overdueCount++
      remainQuantity += Number(r.remainQuantity || 0)
    }
    return { ...g, rows, sortableCount, shortageCount, clearedCount, overdueCount, sortableQuantity, remainQuantity }
  }).filter((g: any) => g.rows.length > 0)
})
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
onActivated(() => {
  if (sessionStorage.getItem(INVENTORY_RETURN_SORT_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(INVENTORY_RETURN_SORT_DIRTY_KEY)
    loadData()
    loadOverview()
  }
})
</script>

<template>
  <div>
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
            <el-switch v-model="includeCleared" active-text="显示已整理完" @change="loadOverview" />
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
            description="如需查看历史批次（已整理完），打开上面的「显示已整理完」开关。" />
          <el-alert v-else-if="filteredEmpty" type="info" :closable="false" show-icon
            title="没有超期的待整理批次（已按「只看超期」过滤）。" />

          <el-collapse v-else v-model="activeGroups" style="margin-top:8px">
            <el-collapse-item v-for="g in groupsView" :key="String(g.warehouseId)" :name="String(g.warehouseId)">
              <template #title>
                <span style="font-weight:600;margin-right:8px">{{ g.warehouseName }}</span>
                <el-tag v-if="g.sortableCount > 0" type="success" size="small" style="margin-right:4px">可整理 {{ g.sortableCount }} 批 / {{ g.sortableQuantity }} 件</el-tag>
                <el-tag v-if="g.shortageCount > 0" type="danger" size="small" style="margin-right:4px">实物不足 {{ g.shortageCount }} 批</el-tag>
                <el-tag v-if="g.clearedCount > 0" type="info" size="small" style="margin-right:4px">已整理完 {{ g.clearedCount }} 批</el-tag>
                <el-tag v-if="g.overdueCount > 0" type="warning" size="small" style="margin-right:4px">超期 {{ g.overdueCount }} 批</el-tag>
                <span class="wh-extra">最早停留 {{ g.oldestStayDays }} 天 · 待整理合计 {{ g.remainQuantity }} 件</span>
              </template>

              <div v-if="g.sortableCount > 0" style="margin-bottom:8px">
                <el-button type="primary" link :icon="'Edit'" @click="gotoSortForm(g.warehouseId)">整理本仓（带出 {{ g.sortableCount }} 批可整理）</el-button>
              </div>

              <el-table :key="`${g.warehouseId}-${selVersion}`" :data="g.rows" border size="small" row-key="pendingId"
                @selection-change="(rows: any) => onSelChange(g.warehouseId, rows)">
                <!--
                  列宽预算（2026-09-22 用户要求：列表一行显示完、不要左右滑动，且**表头不能被截断**）：
                  Σ = 34+78+112+82+84+94+110+46+110+56+76+58 = 940px ≤ 容器 1005px ⇒ 不横向滚动。
                  「产品」是 min-width 列，富余宽度全给它（实测约 175px）。
                  每条列宽按**实测表头需要宽**定（探针：th .cell 的 scrollWidth ≤ clientWidth）；
                  曾被裁的表头与修前值：待整理/已整理 94→110 · 停留天数 66→76。
                  历史：原 Σ=1309px 溢出 304px；收紧后本表曾把表头压裁 ⇒ 现按表头实测回调，其余列各让 2~6px。
                  **改列宽前请先加总并确认表头没被裁**（跑 verify-returnsort-list-fit.ps1）。
                -->
                <el-table-column type="selection" width="34" :selectable="(row: any) => row.status === 'SORTABLE'" />
                <el-table-column label="状态" width="78">
                  <template #default="{ row }">
                    <el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).label }}</el-tag>
                    <el-tooltip v-if="row.partial" content="实物少于批次剩余量，只能先整理可整理部分" placement="top">
                      <span style="color:#e6a23c;margin-left:2px">部分</span>
                    </el-tooltip>
                  </template>
                </el-table-column>
                <el-table-column label="来源单据" width="116" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.sourceType === AfterSaleSourceType.SALE_EXCHANGE ? 'warning' : 'info'" style="margin-right:4px">
                      {{ AfterSaleSourceTypeLabel[row.sourceType] || '-' }}
                    </el-tag>
                    {{ row.sourceCode || '-' }}
                  </template>
                </el-table-column>
                <el-table-column label="来源日期" width="82">
                  <template #default="{ row }">{{ row.sourceDate || '-' }}</template>
                </el-table-column>
                <el-table-column label="客户" width="84" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.customerName || '-' }}</template>
                </el-table-column>
                <el-table-column prop="sku" label="SKU" width="94" show-overflow-tooltip />
                <el-table-column prop="productName" label="产品" min-width="110" show-overflow-tooltip />
                <el-table-column prop="unit" label="单位" width="46" />
                <el-table-column label="待整理/已整理" width="110" align="center">
                  <template #default="{ row }">{{ row.remainQuantity ?? 0 }} / {{ row.sortedQuantity ?? 0 }}</template>
                </el-table-column>
                <el-table-column label="可整理" width="60" align="center">
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
                <el-table-column label="操作" width="58" align="center" fixed="right">
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

      <!-- ==================== ② 整理单列表 ==================== -->
      <el-tab-pane name="bills" lazy>
        <template #label><span>整理单</span></template>

        <el-card shadow="never" class="query-card">
          <div class="query-bar">
            <el-form :inline="true" :model="query" class="query-form">
              <el-form-item label="单号"><el-input v-model="query.code" placeholder="单号" clearable @keyup.enter="handleQuery" /></el-form-item>
              <el-form-item label="源仓库">
                <RemoteSelect v-model="query.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" clearable style="width:160px" />
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
              <el-button type="success" :icon="'Plus'" @click="goAdd">新增退货整理</el-button>
            </div>
          </div>
        </el-card>

        <el-card style="margin-top:12px">
          <el-table :data="tableData" border v-loading="tableLoading" row-key="id" @row-click="openRow">
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
            <el-table-column label="操作" width="200" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click.stop="openRow(row)">详情</el-button>
                <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
                <el-button v-if="row.status === DocStatus.DRAFT" type="danger" link @click.stop="handleDelete(row)">删除</el-button>
                <el-button v-if="row.status === DocStatus.AUDITED" type="warning" link @click.stop="handleCancel(row)">反审核</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top:12px;display:flex;justify-content:flex-end">
            <!-- 2026-09-20（F7-181）：改每页条数时必须回到第 1 页（原先 @change 直接 loadData） -->
            <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total"
              :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @size-change="onSizeChange" @current-change="loadData" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 批量生成整理草稿：按 (仓库, 客户) 各生成一张草稿；分选数量先按所选品质整批预置 -->
    <el-dialog v-model="batchVisible" title="批量生成整理草稿" width="720px">
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
