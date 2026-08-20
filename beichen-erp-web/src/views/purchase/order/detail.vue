<script setup lang="ts">
defineOptions({ name: 'PurchaseDetail' })
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { getPurchaseOrderItems, type PurchaseOrder, type PurchaseOrderItem, PurchaseStatus, PurchaseStatusLabel } from '@/api/purchase'

const route = useRoute(); const router = useRouter()
const orderId = Number(route.params.id)
const order = ref<PurchaseOrder>({})
const items = ref<PurchaseOrderItem[]>([])
const loading = ref(false)
const supplierName = ref('')
const warehouseName = ref('')

function statusType(s?: number) {
  if (s === PurchaseStatus.DRAFT) return 'info'
  if (s === PurchaseStatus.AUDITED) return 'success'
  if (s === PurchaseStatus.CANCELLED) return 'danger'
  return undefined
}
function statusLabel(s?: number) { return s != null ? (PurchaseStatusLabel[s] || '') : '' }
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

/** 品质等级中文映射 */
function qualityLabel(qt?: string) {
  const map: Record<string, string> = { A: 'A规', B: 'B规', C: 'C规', DEFECT: '不良' }
  return qt ? (map[qt] || qt) : '—'
}

async function loadData() {
  loading.value = true
  try {
    const [res, itemRes] = await Promise.all([
      request.get<any, any>(`/inventory/purchase/${orderId}`),
      getPurchaseOrderItems(orderId)
    ])
    order.value = res || {}
    items.value = itemRes || []

    // 批量查供应商和仓库名称
    if (order.value.supplierId) {
      try {
        const r = await request.get('/supplier/page', { params: { pageSize: 200, supplierType: 'product' } })
        const s = (r?.records || []).find((x: any) => x.id === order.value.supplierId)
        supplierName.value = s?.name || ''
      } catch { /* */ }
    }
    if (order.value.warehouseId) {
      try {
        const r = await request.get('/warehouse/page', { params: { pageSize: 200 } })
        const w = (r?.records || []).find((x: any) => x.id === order.value.warehouseId)
        warehouseName.value = w?.warehouseName || ''
      } catch { /* */ }
    }
  } finally { loading.value = false }
}

function goSupplier(id?: number) { if (id) router.push(`/supplier/detail/${id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }

onMounted(() => loadData())
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <span style="font-weight:600">采购单详情 — {{ order.code }}</span>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="单号">{{ order.code }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(order.status)">{{ statusLabel(order.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="供应商">
          <el-button v-if="order.supplierId" type="primary" link @click="goSupplier(order.supplierId)">{{ supplierName }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="入库仓库">
          <el-button v-if="order.warehouseId" type="primary" link @click="goWarehouse(order.warehouseId)">{{ warehouseName }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单日期">{{ order.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="总金额">{{ fmt(order.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ order.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">明细</el-divider>
      <el-table :data="items" border stripe size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="productName" label="成品名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="品质" width="80" align="center">
          <template #default="{ row }">{{ qualityLabel(row.qualityType) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-card>

    <div style="text-align:center;margin-top:20px">
      <el-button @click="$router.back()">返回</el-button>
    </div>
  </div>
</template>

<style scoped>
.detail-page { display: flex; flex-direction: column; gap: 12px; }
</style>
