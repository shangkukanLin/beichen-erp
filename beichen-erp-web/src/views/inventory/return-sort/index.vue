<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { WarehouseType } from '@/api/enums'
import {
  getReturnSortPage, auditReturnSort, cancelReturnSort, deleteReturnSort,
} from '@/api/inventory'

const route = useRoute()
const router = useRouter()

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
  const loss = Number(row.lossAmount) || 0
  const lossTip = loss > 0 ? `\n并将生成一条向客户收取的折损应收 ${loss.toFixed(2)} 元（台账单号 ${row.code}-LOSS）。` : ''
  try {
    await ElMessageBox.confirm(`确认审核单号「${row.code}」？审核后将从成品仓扣减待分类品并分品质入库（A/B/C/不良 均入成品仓，按品质区分）。${lossTip}`, '审核确认', { type: 'warning' })
    await auditReturnSort(row.id)
    ElMessage.success('审核成功')
    loadData()
  } catch { /* 取消 */ }
}
async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核单号「${row.code}」？反审核后将逆向恢复库存。`, '反审核确认', { type: 'warning' })
    await cancelReturnSort(row.id)
    ElMessage.success('已反审核')
    loadData()
  } catch { /* 取消 */ }
}
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除草稿单「${row.code}」？`, '删除确认', { type: 'warning' })
    await deleteReturnSort(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* 取消 */ }
}

async function loadWarehouses() {
  try { const res: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY' } }); warehouseOptions.value = res?.records || [] } catch { warehouseOptions.value = [] }
}
function warehouseName(id?: number) { const w = warehouseOptions.value.find((x: any) => x.id === id); return w ? w.warehouseName : '' }

// 从库存流水点击关联单号跳转：草稿打开编辑页，其余进详情
function openFromStockLog() {
  const billId = route.query.billId
  if (!billId) return
  const row = tableData.value.find((r: any) => r.id === Number(billId))
  if (row) openRow(row)
}
onMounted(async () => { await loadData(); loadWarehouses(); openFromStockLog() })
</script>

<template>
  <div>
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
        <el-table-column label="折损收款" width="110" align="right">
          <template #default="{ row }">
            <span v-if="Number(row.lossAmount) > 0" style="color:#e6a23c;font-weight:600">{{ Number(row.lossAmount).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
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
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="loadData" />
      </div>
    </el-card>
  </div>
</template>
