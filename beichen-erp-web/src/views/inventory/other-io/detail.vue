<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { getQualityTypes, type QualityOption } from '@/api/product'
import { IoType, IoTypeLabel } from '@/api/enums'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id) || 0
const loading = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
const warehouses = ref<any[]>([])
const products = ref<any[]>([])
const qualityOptions = ref<QualityOption[]>([])

function getWhName(wid: number) {
  return warehouses.value.find((w: any) => w.id === wid)?.warehouseName || '-'
}
function getProdName(pid: number | undefined) {
  if (pid == null) return '-'
  return products.value.find((p: any) => p.id === pid)?.name || '-'
}
function getProdSpec(pid: number | undefined) {
  if (pid == null) return '-'
  return products.value.find((p: any) => p.id === pid)?.spec || '-'
}
function getProdUnit(pid: number | undefined) {
  if (pid == null) return '-'
  return products.value.find((p: any) => p.id === pid)?.unit || '-'
}
function qualityLabel(q: string | undefined) {
  if (q == null) return '-'
  return qualityOptions.value.find((o: any) => o.value === q)?.label || q
}
function statusLabel(s: string) {
  if (s === 'AUDITED') return '已审核'
  if (s === 'CANCELLED') return '已取消'
  return s || '-'
}
function statusTag(s: string): any {
  if (s === 'AUDITED') return 'success'
  if (s === 'CANCELLED') return 'danger'
  return 'warning'
}

async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY' } })
    warehouses.value = r?.records || []
  } catch {}
}
async function loadProducts() {
  try {
    const r = await request.get<any, any>('/product/page', { params: { pageSize: 500 } })
    products.value = r?.records || []
  } catch {}
}
async function loadQualityTypes() {
  try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] }
}
async function loadDetail() {
  loading.value = true
  try {
    const io = await request.get<any, any>(`/inventory/other/${id}`)
    detail.value = io || {}
    const its = await request.get<any, any>(`/inventory/other/${id}/items`)
    items.value = Array.isArray(its) ? its : []
  } finally { loading.value = false }
}

onMounted(() => { loadWarehouses(); loadProducts(); loadQualityTypes(); loadDetail() })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never" v-loading="loading">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="单号">{{ detail.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ getWhName(detail.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ IoTypeLabel[detail.ioType] || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.ioDate ? $fmtDate(detail.ioDate) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">成品明细</span></template>
      <el-table :data="items" border size="small">
        <el-table-column type="index" label="#" width="50" align="center"/>
        <el-table-column label="成品名称" min-width="160" show-overflow-tooltip>
          <template #default="{row}">{{ getProdName(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="规格" width="120" show-overflow-tooltip>
          <template #default="{row}">{{ getProdSpec(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="单位" width="80">
          <template #default="{row}">{{ getProdUnit(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="品质" width="90">
          <template #default="{row}">{{ qualityLabel(row.qualityType) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="110" align="right"/>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{row}">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <div style="display:flex;gap:12px;justify-content:center">
      <el-button @click="router.push('/inventory/other-io')">返回列表</el-button>
    </div>
  </div>
</template>
