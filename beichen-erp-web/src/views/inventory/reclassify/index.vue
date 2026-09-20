<script setup lang="ts">
import { WarehouseCategory, INVENTORY_RECLASSIFY_DIRTY_KEY } from '@/api/enums'
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { getReclassifyPage, auditReclassify, unAuditReclassify, cancelReclassify } from '@/api/inventory'

const router = useRouter()

const query = reactive({ code: '', status: '' as string | number, warehouseId: '' as string | number })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })

// 列表
async function loadData() {
  tableLoading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.status) params.status = query.status
    if (query.warehouseId) params.warehouseId = query.warehouseId
    const res = await getReclassifyPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = []; pagination.total = 0 } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.status = ''; query.warehouseId = ''; pagination.pageNum = 1; loadData() }

// 新增与详情已拆成独立页面：详情页在草稿态可直接编辑（与成品其他出入库一致）
function handleAdd() { router.push('/inventory/reclassify/add') }
function handleDetail(row: any) { router.push(`/inventory/reclassify/detail/${row.id}`) }

// F7-29（2026-09-19）：原先 confirm 与请求同在一个 try 且 `catch { /* 取消 */ }` —— 注释说"取消"，
// 实际把请求失败也吞了（语义误导、无法区分）。现与 StockTakePanel.vue 口径统一：取消即 return，
// 请求失败由 request 拦截器提示；成功提示只在请求成功后给出。
async function handleAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认审核单号「${row.code}」？审核后库存将立即变更。`, '审核确认', { type: 'warning' })
  } catch { return }
  try {
    await auditReclassify(row.id)
    ElMessage.success('审核成功')
    loadData()
  } catch { /* 已由 request 拦截器提示 */ }
}
/** 反审核（E2：走 /un-audit，逆向恢复库存并置 CANCELLED） */
async function handleUnAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核单号「${row.code}」？反审核后将逆向恢复库存。`, '反审核确认', { type: 'warning' })
  } catch { return }
  try {
    await unAuditReclassify(row.id)
    ElMessage.success('已反审核')
    loadData()
  } catch { /* 已由 request 拦截器提示 */ }
}
/** 作废（E2：仅草稿走 /cancel，不再承担反审核语义） */
async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认作废草稿单「${row.code}」？`, '作废确认', { type: 'warning' })
  } catch { return }
  try {
    await cancelReclassify(row.id)
    ElMessage.success('已作废')
    loadData()
  } catch { /* 已由 request 拦截器提示 */ }
}

async function loadWarehouses() {
  try { const res = await request.get<any, any>('/warehouse/page', { params: { pageSize: 200, warehouseCategory: WarehouseCategory.INVENTORY } }); warehouseOptions.value = res?.records || [] } catch { warehouseOptions.value = [] }
}

function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }
function warehouseName(id?: number) { const w = warehouseOptions.value.find((x: any) => x.id === id); return w ? w.warehouseName : '' }

const route = useRoute()
// 从库存流水点击关联单号跳转：直接进入该单据的独立详情页
function openFromStockLog() {
  const billId = route.query.billId
  if (billId) router.push(`/inventory/reclassify/detail/${billId}`)
}
/** 2026-09-20（F7-181）：改每页条数时回到第 1 页（与 stock-loss/index.vue:146 同口径） */
function onSizeChange(v: number) { pagination.pageSize = v; pagination.pageNum = 1; loadData() }

onMounted(async () => { await loadData(); loadWarehouses(); openFromStockLog() })
// 新增/详情页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
onActivated(() => {
  if (sessionStorage.getItem(INVENTORY_RECLASSIFY_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(INVENTORY_RECLASSIFY_DIRTY_KEY)
    loadData()
  }
})

</script>

<template>
  <div>
    <el-card class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="单号" clearable /></el-form-item>
        <el-form-item label="仓库">
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
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增品质重分类</el-button>
        </div>
      </div>
    </el-card>

    <el-card style="margin-top:12px">
      <el-table :data="tableData" border v-loading="tableLoading" row-key="id" @row-click="handleDetail">
        <el-table-column label="日期" width="110">
          <template #default="{ row }">{{ row.reclassifyDate ? $fmtDate(row.reclassifyDate) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="code" label="单号" width="160" />
        <el-table-column label="仓库" width="140">
          <template #default="{ row }">
            <el-button v-if="row.warehouseId" type="primary" link @click.stop="router.push(`/inventory/warehouse/detail/${row.warehouseId}`)">
              {{ warehouseName(row.warehouseId) }}
            </el-button>
            <span v-else>{{ warehouseName(row.warehouseId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="重分类概况" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemSummary || '-' }}</template>
        </el-table-column>
        <!-- 整理人=建单时的登录账户；历史单据无此字段显示 — -->
        <el-table-column label="整理人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createByName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status]||'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.AUDITED" type="danger" link @click.stop="handleUnAudit(row)">反审核</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="info" link @click.stop="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <!-- 2026-09-20（F7-181）：改每页条数时必须回到第 1 页（原先 @change 直接 loadData ⇒ 会停在旧页码，出现空白页） -->
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @size-change="onSizeChange" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>
