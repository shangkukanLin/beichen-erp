<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'
import {
  getSaleOrderPage, auditSaleOrder, cancelSaleOrder, unAuditSaleOrder, SALE_ORDER_DIRTY_KEY,
  type SaleOrder
} from '@/api/sale'

const router = useRouter()

const query = reactive({ code: '', customerId: '' as string | number, status: '' as string })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<SaleOrder[]>([])

const statusOptions = [
  { label: DocStatusLabel[DocStatus.DRAFT], value: DocStatus.DRAFT },
  { label: DocStatusLabel[DocStatus.AUDITED], value: DocStatus.AUDITED },
  { label: DocStatusLabel[DocStatus.CANCELLED], value: DocStatus.CANCELLED }
]

// Odoo 风格：下拉框展开/搜索时实时查库（不预缓存全量）
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })

// 列表显示用的本地轻量列表（组件内维护，不再依赖全局 optionsStore）
const customers = ref<any[]>([])
const warehouses = ref<any[]>([])

async function loadCustomers() { try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] } }
async function loadWarehouses() {
  try {
    const r: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseType: '成品仓' } })
    warehouses.value = r?.records || []
  } catch { warehouses.value = [] }
}

async function loadData() {
  tableLoading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.customerId !== '' && query.customerId !== null) params.customerId = query.customerId
    if (query.status) params.status = query.status
    const res = await getSaleOrderPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = []; pagination.total = 0 } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.customerId = ''; query.status = ''; pagination.pageNum = 1; loadData() }

async function handleAudit(row: SaleOrder) {
  try {
    await ElMessageBox.confirm(`确认审核销售单「${row.code}」？审核后将直接出库并生成应收。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditSaleOrder(row.id as number); ElMessage.success('审核成功'); loadData()
  } catch { }
}
async function handleCancel(row: SaleOrder) {
  try {
    await ElMessageBox.confirm(`确认作废销售单「${row.code}」？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelSaleOrder(row.id as number); ElMessage.success('已作废'); loadData()
  } catch { }
}
async function handleUnAudit(row: SaleOrder) {
  try {
    await ElMessageBox.confirm(`确认反审核销售单「${row.code}」？将冲回出库与应收。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await unAuditSaleOrder(row.id as number); ElMessage.success('已反审核'); loadData()
  } catch { }
}

function handleSizeChange(val: number) { pagination.pageSize = val; pagination.pageNum = 1; loadData() }
function handleCurrentChange(val: number) { pagination.pageNum = val; loadData() }
function statusType(s?: string) { return DocStatusTag[s || ''] || '' }
function customerName(id?: number) { const c = customers.value.find(x => x.id === id); return c ? c.name : '' }
function warehouseName(id?: number) { const w = warehouses.value.find(x => x.id === id); return w ? w.warehouseName : '' }
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

function goDetail(row: SaleOrder) { router.push('/inventory/sale/detail/' + row.id) }
function goCustomer(id?: number) { if (id) router.push(`/inventory/customer/detail/${id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
/** 新增/编辑统一走独立页面 /inventory/sale/add（带 id 为编辑） */
function goAdd() { router.push('/inventory/sale/add') }
function goEdit(row: SaleOrder) { router.push('/inventory/sale/add?id=' + row.id) }

onMounted(() => { loadCustomers(); loadWarehouses(); loadData() })
// 数据变动（新增/编辑页、详情页编辑/审核/反审核/作废）后返回列表时按需刷新，保留查询条件与分页现场
onActivated(() => {
  if (sessionStorage.getItem(SALE_ORDER_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(SALE_ORDER_DIRTY_KEY)
    loadData()
  }
})
</script>

<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="单号">
            <el-input v-model="query.code" placeholder="请输入单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="客户">
            <RemoteSelect v-model="query.customerId" :fetch="fetchCustomers" placeholder="请选择" clearable style="width:160px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="请选择" clearable style="width:120px">
              <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
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

    <el-card shadow="never" class="table-card">
      <el-table v-loading="tableLoading" :data="tableData" border stripe @row-click="goDetail">
        <el-table-column prop="orderDate" label="订单日期" width="120" align="center" />
        <el-table-column prop="code" label="单号" min-width="150" />
        <el-table-column label="客户" min-width="140">
          <template #default="{ row }">
            <el-button v-if="row.customerId" type="primary" link @click.stop="goCustomer(row.customerId)">{{ customerName(row.customerId) }}</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="出库仓库" min-width="120">
          <template #default="{ row }">
            <el-button v-if="row.warehouseId" type="primary" link @click.stop="goWarehouse(row.warehouseId)">{{ warehouseName(row.warehouseId) }}</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="总金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ DocStatusLabel[String(row.status)] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="320" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">详情</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="primary" link @click.stop="goEdit(row)">编辑</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.AUDITED" type="warning" link @click.stop="handleUnAudit(row)">反审核</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="danger" link @click.stop="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
