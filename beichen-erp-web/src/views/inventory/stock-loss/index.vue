<template>
  <div class="page-list">
    <!-- 查询区用 4 列固定栅格（与物料收货页一致）：行距 16 / 列距 24，控件填满格子，不随窗口流式换行 -->
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" label-width="84px" class="query-form">
        <el-form-item label="报损单号">
          <el-input v-model="query.keyword" placeholder="报损单号或备注" clearable @keyup.enter="doQuery" />
        </el-form-item>
        <el-form-item label="仓库">
          <RemoteSelect v-model="query.warehouseId" add-route="/inventory/warehouse" :fetch="fetchWarehouses"
            :label-key="(row:any)=>row.warehouseName" placeholder="全部" domain="warehouse" />
        </el-form-item>
        <el-form-item label="报损原因">
          <el-select v-model="query.lossReason" placeholder="全部" clearable>
            <el-option v-for="r in lossReasons" :key="r.code" :label="r.label" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable>
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="报损日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <div class="query-actions">
          <el-button type="primary" :icon="'Search'" @click="doQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="resetQuery">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="goAdd">新增</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1230px > 内容区 956px
           ⇒ 横向滚动 274px。收窄为合计 942px。
           2026-09-26 B5a（用户口径「数据显示完整 + 单号/仓库可点」）：
           ①报损单号 min112→**150 固定**并做成链接进详情（BS-+11 位，原先 112px 被截断）；
           ②仓库 min100→140 并做成链接进仓库详情（成品报损只发生在自有成品仓 ⇒ 固定走 /inventory/warehouse）；
           ③为抵平：报损日期 96→92、报损原因 88→80、报损明细 min124→110、报损金额 96→88、审核人 80→70。
           合计 = 150+140+92+80+110+88+76+70+132 = **938** ✓ -->
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column label="报损单号" width="150" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column label="仓库" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.warehouseId" type="primary" link @click.stop="router.push(`/inventory/warehouse/detail/${row.warehouseId}`)">{{ row.warehouseName || '—' }}</el-button>
            <span v-else>{{ row.warehouseName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lossDate" label="报损日期" width="92" />
        <el-table-column label="报损原因" width="80" show-overflow-tooltip>
          <template #default="{ row }">{{ LossReasonLabel[row.lossReason] || row.lossReason || '—' }}</template>
        </el-table-column>
        <el-table-column prop="itemSummary" label="报损明细" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemSummary || '—' }}</template>
        </el-table-column>
        <el-table-column label="报损金额" width="88" align="right">
          <template #default="{ row }">{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="76" align="center">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核人" width="70" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditorName || '—' }}</template>
        </el-table-column>
        <!-- 2026-09-24（用户口径）：草稿态在**详情页**改+存（含明细增删）⇒ 列表去掉「编辑」入口；操作列 170→132。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button v-perm="'stock:stock-loss'" v-if="row.status === DocStatus.DRAFT" link type="success" @click="onAudit(row)">审核</el-button>
            <el-button v-perm="'stock:stock-loss'" v-if="row.status === DocStatus.DRAFT" link type="danger" @click="onCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="page.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="onSizeChange" @current-change="load" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, LossReasonLabel, WarehouseCategory, WarehouseType, codeLabelOptions, INVENTORY_STOCK_LOSS_DIRTY_KEY } from '@/api/enums'
import { useDomainRefresh } from '@/utils/dataFreshness'

const router = useRouter()

// 成品报损只在自有成品仓发生（辅料仓放物料，委外仓归物料报损管）
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
    .then((res: any) => {
      res.records = (res.records || []).filter((w: any) => w.warehouseType !== WarehouseType.AUXILIARY)
      return res
    })

const statusOptions = Object.entries(DocStatusLabel).map(([value, label]) => ({ value, label }))
/** 报损原因下拉取后端枚举，避免前端硬编码（与库存变动类型下拉同一套做法） */
const lossReasons = ref<any[]>([])

const query = reactive({
  keyword: '',
  warehouseId: undefined as number | undefined,
  lossReason: undefined as string | undefined,
  status: undefined as string | undefined
})
const dateRange = ref<string[]>([])
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const rows = ref<any[]>([])

function money(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

/** 报损原因选项（2026-09-14：改由前端枚举映射生成；后端接口已只回 code） */
function loadReasons() { lossReasons.value = codeLabelOptions(LossReasonLabel) }

async function load() {
  loading.value = true
  try {
    const params: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.keyword) params.keyword = query.keyword
    if (query.warehouseId) params.warehouseId = query.warehouseId
    if (query.lossReason) params.lossReason = query.lossReason
    if (query.status) params.status = query.status
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await request.get<any, any>('/inventory/stock-loss/page', { params })
    rows.value = res?.records || []
    page.total = res?.total || 0
  } catch {
    rows.value = []; page.total = 0
  } finally { loading.value = false }
}

function doQuery() { page.pageNum = 1; load() }
function resetQuery() {
  query.keyword = ''
  query.warehouseId = undefined
  query.lossReason = undefined
  query.status = undefined
  dateRange.value = []
  page.pageNum = 1
  load()
}
function onSizeChange(v: number) { page.pageSize = v; page.pageNum = 1; load() }

function goAdd() { router.push('/inventory/stock-loss/add') }
function goDetail(row: any) { router.push(`/inventory/stock-loss/detail/${row.id}`) }
/* 2026-09-24（用户口径）：goEdit 已移除 —— 草稿态编辑统一在详情页内联完成（含明细增删），列表不再提供编辑入口。 */

async function onAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认审核报损单「${row.code}」？审核后将扣减对应仓库的库存。`, '审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/stock-loss/${row.id}/audit`)
    ElMessage.success('已审核，库存已扣减')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function onUnAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核报损单「${row.code}」？反审核后库存将加回。`, '反审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/stock-loss/${row.id}/un-audit`)
    ElMessage.success('已反审核，库存已加回')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function onCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认作废报损单「${row.code}」？`, '作废确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/stock-loss/${row.id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(() => { loadReasons(); load() })
// 2026-09-20（F7-174）：本路由在 keep-alive 内 ⇒ 从新增/编辑页返回时 onMounted 不再触发，列表会停在旧数据。
// 新增/编辑页保存成功后置脏标志，回到列表才拉一次（保留查询条件与分页现场）。
useDomainRefresh('stockLoss', () => {
    load()
}, INVENTORY_STOCK_LOSS_DIRTY_KEY)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
/* 卡片内边距已统一到全局（styles/page.css 的 .table-card .el-card__body） */
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */

/* 查询区：4 列 Grid 栅格，列距 24px / 行距 16px 固定 */
.query-form { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px 24px; }
.query-form :deep(.el-form-item) { margin: 0; }
.query-form :deep(.el-form-item__content) { flex: 1; min-width: 0; }
.query-form :deep(.el-form-item__content .el-input),
.query-form :deep(.el-form-item__content .el-select),
.query-form :deep(.el-form-item__content .el-date-editor),
.query-form :deep(.el-form-item__content > .remote-select) { width: 100%; }
.query-actions { display: flex; align-items: center; gap: 8px; }
</style>
