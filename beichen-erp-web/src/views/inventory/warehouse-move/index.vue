<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="移出仓">
          <RemoteSelect v-model="query.fromWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:150px" />
        </el-form-item>
        <el-form-item label="移入仓">
          <RemoteSelect v-model="query.toWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:150px" />
        </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1020px > 内容区 956px
           ⇒ 横向滚动 64px。收窄为合计 832px（单号/移出移入仓库/产品明细保持 min-width，宽屏自动吃余量）。 -->
      <el-table v-loading="loading" :data="tableData" border stripe @row-click="handleDetail">
        <el-table-column prop="code" label="单号" min-width="120" show-overflow-tooltip />
        <el-table-column label="移出仓库" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click.stop="$router.push(`/inventory/warehouse/detail/${row.fromWarehouseId}`)">{{ warehouseName(row.fromWarehouseId) }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="移入仓库" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click.stop="$router.push(`/inventory/warehouse/detail/${row.toWarehouseId}`)">{{ warehouseName(row.toWarehouseId) }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="itemsSummary" label="产品明细" min-width="140" show-overflow-tooltip />
        <el-table-column label="移仓日期" width="100" align="center">
          <template #default="{ row }">{{ $fmtDate(row.moveDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="78" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <!-- 2026-09-24（用户口径）：反审核移入详情页 ⇒ 操作列 174→132（详情/审核/作废 3 个按钮）。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button>
                <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
                <el-button v-if="row.status === DocStatus.DRAFT" type="danger" link @click.stop="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="(v:number)=>{pagination.pageSize=v;pagination.pageNum=1;loadData()}" @current-change="loadData" />
      </div>
    </el-card>

  </div>
</template>

<script setup lang="ts">
import { WarehouseCategory, INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY } from '@/api/enums'
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const route = useRoute()
const query = reactive({ status: '', fromWarehouseId: undefined as number | undefined, toWarehouseId: undefined as number | undefined })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const tableData = ref<any[]>([])

const statusOptions = [
  { label: DocStatusLabel[DocStatus.DRAFT], value: DocStatus.DRAFT },
  { label: DocStatusLabel[DocStatus.AUDITED], value: DocStatus.AUDITED },
  { label: DocStatusLabel[DocStatus.CANCELLED], value: DocStatus.CANCELLED }
]

// 仓库下拉选项（Odoo 实时查库，组件本地保存）
const warehouseOptions = ref<any[]>([])
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })

/**
 * 仓库名称映射：移仓单只返回 from/toWarehouseId，需要一次性拉仓库列表做本地映射。
 * 这里不按 warehouseCategory 过滤——下拉筛选仍只给自有仓，但名称映射要能覆盖到任意仓，避免列显示为空。
 */
async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } })
    warehouseOptions.value = r?.records || []
  } catch { warehouseOptions.value = [] }
}

function warehouseName(id?: number) {
  const w = warehouseOptions.value.find((x: any) => x.id === id)
  return w ? w.warehouseName : '-'
}
function statusType(s?: string) { return DocStatusTag[s || ''] || '' }

async function loadData() {
  loading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.status) params.status = query.status
    if (query.fromWarehouseId) params.fromWarehouseId = query.fromWarehouseId
    if (query.toWarehouseId) params.toWarehouseId = query.toWarehouseId
    const res = await request.get<any, any>('/inventory/warehouse-move/page', { params })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = []; pagination.total = 0 }
  finally { loading.value = false }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.status = ''; query.fromWarehouseId = undefined; query.toWarehouseId = undefined; pagination.pageNum = 1; loadData() }

function handleAdd() { router.push('/inventory/warehouse-move/add') }
// 详情已独立成页：草稿态在详情页内直接编辑，列表不再提供「编辑」入口
function handleDetail(row: any) { router.push(`/inventory/warehouse-move/detail/${row.id}`) }

// F7-29（2026-09-19）：把「用户取消」与「请求失败」分开 —— 原先 confirm 与请求同在一个 try，
// 取消与失败都落进 `catch { }`，无法区分；现与 stock-take/StockTakePanel.vue 的既有写法统一：
// confirm 取消即 return，请求失败由 request 拦截器统一提示（成功提示只在请求成功后才给）。
async function handleAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认审核移仓单「${row.code}」？将从移出仓扣减库存并增加到移入仓。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/warehouse-move/${row.id}/audit`)
    ElMessage.success('已审核')
    loadData()
  } catch { /* 已由 request 拦截器提示 */ }
}

async function handleUnAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核移仓单「${row.code}」？将退回移出仓库存并从移入仓扣回。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/warehouse-move/${row.id}/un-audit`)
    ElMessage.success('已反审核')
    loadData()
  } catch { /* 已由 request 拦截器提示 */ }
}

async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认作废移仓单「${row.code}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/warehouse-move/${row.id}/cancel`)
    ElMessage.success('已作废')
    loadData()
  } catch { /* 已由 request 拦截器提示 */ }
}

onActivated(() => {
  // 新增/编辑页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY)
    loadData()
  }
})
onMounted(async () => {
  await loadWarehouses()
  loadData().then(() => {
    // 库存流水链接跳转：直接进入该单据的独立详情页
    const billId = route.query.billId
    if (billId) router.push(`/inventory/warehouse-move/detail/${billId}`)
  })
})

</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
/* 卡片内边距已统一到全局（styles/page.css 的 .table-card .el-card__body） */
.query-form { align-items: center; }
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
</style>
