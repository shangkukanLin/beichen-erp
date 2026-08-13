<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>销售退货单详情</span>
          <el-tag :type="statusTagType(header.status)">{{ statusLabel(header.status) }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="退货单号">{{ header.code }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ header.customerName }}</el-descriptions-item>
        <el-descriptions-item label="退货仓库">{{ warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ head.returnDate }}</el-descriptions-item>
        <el-descriptions-item label="退货金额">{{ formatMoney(head.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ head.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">退货明细（退回均为不良品）</el-divider>
      <el-table :data="items" border>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="productName" label="产品" min-width="200" />
        <el-table-column label="品质等级" width="110" align="center">
          <template #default>
            <el-tag type="danger">不良品</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="退货数量" width="130" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />
      </el-table>

      <div class="footer">
        <el-button @click="goBack">返回</el-button>
        <template v-if="head.status === SaleReturnStatus.DRAFT">
          <el-button type="primary" @click="goEdit">编辑</el-button>
          <el-button type="success" :loading="acting" @click="doAudit">审核</el-button>
          <el-button type="danger" :loading="acting" @click="doCancel">作废</el-button>
        </template>
        <el-button v-if="String(head.status) === String(SaleReturnStatus.AUDITED)" type="warning" :loading="acting" @click="doUnAudit">反审核</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import {
  getSaleReturn,
  getSaleReturnItems,
  auditSaleReturn,
  unAuditSaleReturn,
  cancelSaleReturn,
  SaleReturnStatus,
  SaleReturnStatusLabel,
} from '@/api/sale'

const route = useRoute()
const router = useRouter()
const acting = ref(false)
const warehouses = ref<any[]>([])
const items = ref<any[]>([])

const head = reactive({
  code: '',
  customerName: '',
  warehouseId: undefined as number | undefined,
  returnDate: '',
  totalAmount: 0,
  auditorName: '',
  remark: '',
  status: SaleReturnStatus.DRAFT,
})
const header = head

const warehouseName = computed(() => {
  const w = warehouses.value.find((x) => x.id === head.warehouseId)
  return w ? w.name : '—'
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

async function loadWarehouses() {
  const res = await request.get('/warehouse/page', { params: { pageNum: 1, pageSize: 1000 } })
  warehouses.value = res.records || []
}

async function loadDetail(id: number) {
  const h = await getSaleReturn(id)
  Object.assign(head, {
    code: h.code,
    customerName: h.customerName,
    warehouseId: h.warehouseId,
    returnDate: h.returnDate,
    totalAmount: h.totalAmount,
    auditorName: h.auditorName,
    remark: h.remark,
    status: h.status,
  })
  items.value = await getSaleReturnItems(id)
}

function goBack() {
  router.push('/sale/return')
}
function goEdit() {
  router.push(`/sale/return/add?id=${route.params.id}`)
}

async function doAudit() {
  await ElMessageBox.confirm('确认审核？审核后客户退回的不良品将入库增加库存。', '提示', { type: 'warning' })
  acting.value = true
  try {
    await auditSaleReturn(Number(route.params.id))
    ElMessage.success('审核成功')
    loadDetail(Number(route.params.id))
  } finally {
    acting.value = false
  }
}
async function doUnAudit() {
  await ElMessageBox.confirm('确认反审核？将扣减已入库的不良品库存。', '提示', { type: 'warning' })
  acting.value = true
  try {
    await unAuditSaleReturn(Number(route.params.id))
    ElMessage.success('反审核成功')
    loadDetail(Number(route.params.id))
  } finally {
    acting.value = false
  }
}
async function doCancel() {
  await ElMessageBox.confirm('确认作废该退货单？', '提示', { type: 'warning' })
  acting.value = true
  try {
    await cancelSaleReturn(Number(route.params.id))
    ElMessage.success('作废成功')
    loadDetail(Number(route.params.id))
  } finally {
    acting.value = false
  }
}

onMounted(async () => {
  await loadWarehouses()
  await loadDetail(Number(route.params.id))
})
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.footer { margin-top: 20px; text-align: right; }
</style>
