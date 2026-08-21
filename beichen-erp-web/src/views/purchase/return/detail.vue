<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { getPurchaseReturn, getPurchaseReturnItems, ReturnStatus, ReturnStatusLabel, type PurchaseReturn, type PurchaseReturnItem } from '@/api/purchase'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(false)
const detail = ref<Partial<PurchaseReturn>>({})
const items = ref<PurchaseReturnItem[]>([])
const warehouseOptions = ref<any[]>([])

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function warehouseName(wid?: number) { const w = warehouseOptions.value.find(x => x.id === wid); return w ? (w.warehouseName || w.name || '') : '' }
function productName(it?: PurchaseReturnItem) { return it?.productName || (it?.productId != null ? `#${it.productId}` : '') }
function statusLabel(s?: number) { return s != null ? (ReturnStatusLabel[s] || '') : '' }
function statusType(s?: any): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined {
  if (s === ReturnStatus.DRAFT) return 'info'
  if (s === ReturnStatus.AUDITED) return 'success'
  if (s === ReturnStatus.CANCELLED) return 'danger'
  return undefined
}

async function loadWarehouseOptions() { try { const r: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } }); warehouseOptions.value = r?.records || [] } catch { warehouseOptions.value = [] } }

async function loadData() {
  loading.value = true
  try {
    const d = await getPurchaseReturn(id)
    detail.value = d || {}
    // 后端随明细返回 productName，前端不再逐条查库
    items.value = await getPurchaseReturnItems(id) || []
  } finally { loading.value = false }
}

onMounted(() => { loadWarehouseOptions(); loadData() })
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-card shadow="never">
      <template #header><span style="font-weight:600">成品退货单详情 — {{ detail.code }}</span></template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="退货仓库">{{ warehouseName(detail.warehouseId) || '—' }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ detail.returnDate }}</el-descriptions-item>
        <el-descriptions-item label="退货总金额">{{ fmt(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">退货明细</el-divider>
      <el-table :data="items" border stripe size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="产品" min-width="140">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right" />
        <el-table-column prop="amount" label="金额" width="120" align="right" />
        <el-table-column prop="remark" label="备注" min-width="120" />
      </el-table>
    </el-card>

    <div style="text-align:center;margin-top:20px">
      <el-button @click="router.back()">返回</el-button>
    </div>
  </div>
</template>

<style scoped>
.detail-page { display: flex; flex-direction: column; gap: 12px; }
</style>
