<template>
  <div class="page">
    <el-card class="query-card">
      <el-form :inline="true" class="query-form">
        <el-form-item label="单号">
          <el-input v-model="query.code" placeholder="退货单号" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="query.supplierId" placeholder="请选择" clearable filterable style="width:180px">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="请选择" clearable style="width:120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="query-actions">
        <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
      </div>
    </el-card>

    <el-card style="margin-top:16px">
      <el-table :data="list" border stripe v-loading="loading" row-key="id">
        <el-table-column prop="code" label="退货单号" width="180" />
        <el-table-column label="供应商" min-width="140">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleSupplierClick(row.supplierId)">{{ row.supplierName }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="退货仓库" min-width="120">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleWarehouseClick(row.warehouseId)">{{ warehouseName(row.warehouseId) }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="returnDate" label="退货日期" width="120" align="center" />
        <el-table-column prop="itemsSummary" label="退货明细" min-width="200" show-overflow-tooltip />
        <el-table-column prop="totalAmount" label="退货总金额" width="130" align="right">
          <template #default="{ row }">{{ row.totalAmount ? Number(row.totalAmount).toFixed(2) : '0.00' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.status === ReturnStatus.DRAFT" type="success" link @click="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === ReturnStatus.AUDITED" type="warning" link @click="handleUnAudit(row)">反审核</el-button>
            <el-button v-if="row.status === ReturnStatus.DRAFT" type="warning" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === ReturnStatus.DRAFT" type="danger" link @click="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px;justify-content:flex-end"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10,20,50]"
        :total="pagination.total"
        layout="total,sizes,prev,pager,next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="退货单详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="退货单号">{{ detailData.code }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ detailData.returnDate }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailData.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="退货仓库">{{ warehouseName(detailData.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detailData.status)">{{ statusLabel(detailData.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="退货总金额">{{ detailData.totalAmount ? Number(detailData.totalAmount).toFixed(2) : '0.00' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detailItems" border style="margin-top:16px">
        <el-table-column prop="productId" label="产品ID" width="80" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column prop="unitPrice" label="单价" width="100" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">{{ row.amount ? Number(row.amount).toFixed(2) : '' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPurchaseReturnPage, getPurchaseReturnItems,
  auditPurchaseReturn, cancelPurchaseReturn, unAuditPurchaseReturn,
  ReturnStatus, ReturnStatusLabel,
  type PurchaseReturn, type PurchaseReturnItem
} from '@/api/purchase'
import { useOptionsStore } from '@/stores/options'

const router = useRouter()
const optionsStore = useOptionsStore()
const list = ref<PurchaseReturn[]>([])
const loading = ref(false)
const query = reactive({ code: '', supplierId: '' as string | number, status: '' as string | number })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

const suppliers = computed(() => optionsStore.suppliers['suppliers:product'] || [])
const warehouses = computed(() => optionsStore.warehouses || [])

const statusOptions = [
  { label: ReturnStatusLabel[ReturnStatus.DRAFT], value: ReturnStatus.DRAFT },
  { label: ReturnStatusLabel[ReturnStatus.AUDITED], value: ReturnStatus.AUDITED },
  { label: ReturnStatusLabel[ReturnStatus.CANCELLED], value: ReturnStatus.CANCELLED },
]
function statusLabel(s?: number) { return s != null ? (ReturnStatusLabel[s] || '') : '' }
function statusType(s?: number): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined {
  if (s === ReturnStatus.DRAFT) return 'info'
  if (s === ReturnStatus.AUDITED) return 'success'
  if (s === ReturnStatus.CANCELLED) return 'danger'
  return undefined
}
function warehouseName(id?: number) {
  const w = warehouses.value.find((x: any) => x.id === id)
  return w ? w.name || w.warehouseName : ''
}

const detailVisible = ref(false)
const detailData = ref<Partial<PurchaseReturn>>({})
const detailItems = ref<PurchaseReturnItem[]>([])

async function loadData() {
  loading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.supplierId) params.supplierId = query.supplierId
    if (query.status !== '' && query.status != null) params.status = query.status
    const res = await getPurchaseReturnPage(params)
    list.value = res.records
    pagination.total = res.total
  } finally { loading.value = false }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.supplierId = ''; query.status = ''; handleQuery() }
function handleAdd() { router.push('/inventory/purchase-return/add') }
function handleEdit(row: PurchaseReturn) { router.push({ path: '/inventory/purchase-return/add', query: { id: row.id } }) }
function handleSupplierClick(id?: number) { if (id) router.push(`/supplier/detail/${id}`) }
function handleWarehouseClick(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }

async function handleDetail(row: PurchaseReturn) {
  detailData.value = { ...row }
  try {
    detailItems.value = await getPurchaseReturnItems(row.id as number) || []
  } catch { detailItems.value = [] }
  detailVisible.value = true
}

async function handleAudit(row: PurchaseReturn) {
  try {
    await ElMessageBox.confirm(`确认审核退货单「${row.code}」？审核后出库减库存并冲减应付账款。`, '确认审核', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditPurchaseReturn(row.id as number)
    ElMessage.success('审核成功')
    loadData()
  } catch { /* */ }
}
async function handleUnAudit(row: PurchaseReturn) {
  try {
    await ElMessageBox.confirm(`确认反审核退货单「${row.code}」？反审核后恢复库存、清除应付台账，回到草稿状态。`, '确认反审核', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await unAuditPurchaseReturn(row.id as number)
    ElMessage.success('反审核成功')
    loadData()
  } catch { /* */ }
}
async function handleCancel(row: PurchaseReturn) {
  try {
    await ElMessageBox.confirm(`确认作废退货单「${row.code}」？`, '确认作废', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelPurchaseReturn(row.id as number)
    ElMessage.success('已作废')
    loadData()
  } catch { /* */ }
}

onMounted(() => {
  optionsStore.ensureSuppliers('product')
  optionsStore.ensureWarehouses()
  loadData()
})
onActivated(() => { loadData() })
</script>

<style scoped>
.page { padding: 16px; }
.query-form { align-items: center; }
.query-actions { display: flex; justify-content: center; align-items: center; margin-top: 12px; }
</style>
