<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="移出仓">
          <el-select v-model="query.fromWarehouseId" placeholder="全部" clearable filterable style="width:150px">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="移入仓">
          <el-select v-model="query.toWarehouseId" placeholder="全部" clearable filterable style="width:150px">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增移仓</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="code" label="单号" min-width="150" />
        <el-table-column label="移出仓库" min-width="140">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="$router.push(`/inventory/warehouse/detail/${row.fromWarehouseId}`)">{{ warehouseName(row.fromWarehouseId) }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="移入仓库" min-width="140">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="$router.push(`/inventory/warehouse/detail/${row.toWarehouseId}`)">{{ warehouseName(row.toWarehouseId) }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="itemsSummary" label="产品明细" min-width="200" show-overflow-tooltip />
        <el-table-column label="移仓日期" width="120" align="center">
          <template #default="{ row }">{{ $fmtDate(row.moveDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
                <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click="handleAudit(row)">审核</el-button>
                <el-button v-if="row.status === DocStatus.AUDITED" type="warning" link @click="handleUnAudit(row)">反审核</el-button>
                <el-button v-if="row.status === DocStatus.DRAFT" type="warning" link @click="handleEdit(row)">编辑</el-button>
                <el-button v-if="row.status === DocStatus.DRAFT" type="danger" link @click="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="(v:number)=>{pagination.pageSize=v;loadData()}" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="移仓单详情" size="60%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.status)">{{ detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="移出仓库">{{ warehouseName(detail.fromWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="移入仓库">{{ warehouseName(detail.toWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="移仓日期">{{ detail.moveDate }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">明细</el-divider>
      <el-table :data="detailItems" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="productName" label="产品" min-width="140" />
        <el-table-column prop="spec" label="规格" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'

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
]

const warehouses = ref<any[]>([])

const detailVisible = ref(false)
const detail = ref<any>({})
const detailItems = ref<any[]>([])

function warehouseName(id?: number) {
  const w = warehouses.value.find((x: any) => x.id === id)
  return w ? w.warehouseName : ''
}
function statusType(s?: string) { return DocStatusTag[s || ''] || '' }

async function loadWarehouses() {
  try {
    const res = await request.get('/inventory/warehouse/page', { params: { pageSize: 200 } })
    warehouses.value = res?.records || []
  } catch { warehouses.value = [] }
}

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
function handleEdit(row: any) { router.push(`/inventory/warehouse-move/add?id=${row.id}`) }

async function handleAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认审核移仓单「${row.code}」？将从移出仓扣减库存并增加到移入仓。`, '提示', { type: 'warning' })
    await request.put(`/inventory/warehouse-move/${row.id}/audit`)
    ElMessage.success('审核成功')
    loadData()
  } catch { }
}

async function handleUnAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核移仓单「${row.code}」？将退回移出仓库存并从移入仓扣回。`, '提示', { type: 'warning' })
    await request.put(`/inventory/warehouse-move/${row.id}/un-audit`)
    ElMessage.success('反审核成功')
    loadData()
  } catch { }
}

async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认作废移仓单「${row.code}」？`, '提示', { type: 'warning' })
    await request.put(`/inventory/warehouse-move/${row.id}/cancel`)
    ElMessage.success('已作废')
    loadData()
  } catch { }
}

async function handleDetail(row: any) {
  detail.value = { ...row }
  try {
    const r = await request.get<any, any>(`/inventory/warehouse-move/${row.id}/items`)
    detailItems.value = r || []
  } catch { detailItems.value = [] }
  detailVisible.value = true
}

onMounted(() => {
  loadWarehouses()
  loadData().then(() => {
    // 库存流水链接跳转：自动打开指定单据详情
    const billId = route.query.billId
    if (billId) {
      const row = tableData.value.find((r: any) => r.id === Number(billId))
      if (row) handleDetail(row)
    }
  })
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding: 16px; }
.query-form { align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
