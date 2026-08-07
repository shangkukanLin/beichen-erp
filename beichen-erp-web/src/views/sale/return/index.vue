<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="退货单号">
          <el-input v-model="query.code" placeholder="请输入单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="query.customerId" filterable clearable placeholder="请选择客户" style="width: 200px">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option v-for="(label, val) in statusOptions" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="load(1)">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="goAdd">新增退货单</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="code" label="退货单号" width="150" />
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column prop="returnDate" label="退货日期" width="120" />
        <el-table-column label="退货概况" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemsSummary }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button v-if="row.status === SaleReturnStatus.DRAFT" link type="primary" @click="goEdit(row)">编辑</el-button>
            <el-button v-if="row.status === SaleReturnStatus.DRAFT" link type="success" @click="doAudit(row)">审核</el-button>
            <el-button v-if="row.status === SaleReturnStatus.AUDITED" link type="warning" @click="doUnAudit(row)">反审核</el-button>
            <el-button v-if="row.status === SaleReturnStatus.DRAFT" link type="danger" @click="doCancel(row)">作废</el-button>
            <el-button v-if="row.status === SaleReturnStatus.DRAFT" link type="danger" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        @current-change="(p: number) => load(p)"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'
import {
  getSaleReturnPage,
  auditSaleReturn,
  unAuditSaleReturn,
  cancelSaleReturn,
  deleteSaleReturn,
  SaleReturnStatus,
  SaleReturnStatusLabel,
} from '@/api/sale'

interface Customer { id: number; name: string }

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const customers = ref<Customer[]>([])
const statusOptions = SaleReturnStatusLabel

const query = reactive({
  code: '',
  customerId: undefined as number | undefined,
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10,
})

function statusLabel(s: number) {
  return SaleReturnStatusLabel[s as 0 | 1 | 2] ?? '未知'
}
function statusTagType(s: number) {
  if (s === SaleReturnStatus.AUDITED) return 'success'
  if (s === SaleReturnStatus.CANCELLED) return 'info'
  return 'warning'
}
function formatMoney(v: any) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load(p?: number) {
  if (p) query.pageNum = p
  loading.value = true
  try {
    const res = await getSaleReturnPage({
      code: query.code || undefined,
      customerId: query.customerId,
      status: query.status,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.code = ''
  query.customerId = undefined
  query.status = undefined
  load(1)
}

async function loadCustomers() {
  try {
    const res = await request.get('/inventory/customer/page', { params: { pageNum: 1, pageSize: 1000 } })
    customers.value = res.records || []
  } catch {
    customers.value = []
  }
}

function goAdd() {
  router.push('/sale/return/add')
}
function goEdit(row: any) {
  router.push(`/sale/return/add?id=${row.id}`)
}
function goDetail(row: any) {
  router.push(`/sale/return/detail/${row.id}`)
}

function doAudit(row: any) {
  ElMessageBox.confirm(`确认审核退货单 ${row.code}？审核后客户退回的不良品将入库增加库存。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await auditSaleReturn(row.id)
      ElMessage.success('审核成功')
      load()
    })
    .catch(() => {})
}

function doUnAudit(row: any) {
  ElMessageBox.confirm(`确认反审核退货单 ${row.code}？将扣减已入库的不良品库存。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await unAuditSaleReturn(row.id)
      ElMessage.success('反审核成功')
      load()
    })
    .catch(() => {})
}

function doCancel(row: any) {
  ElMessageBox.confirm(`确认作废退货单 ${row.code}？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await cancelSaleReturn(row.id)
      ElMessage.success('作废成功')
      load()
    })
    .catch(() => {})
}

function doDelete(row: any) {
  ElMessageBox.confirm(`确认删除退货单 ${row.code}？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await deleteSaleReturn(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

onMounted(() => {
  loadCustomers()
  load()
})
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
