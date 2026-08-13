<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'

const route = useRoute(); const router = useRouter()
const warehouseId = Number(route.params.id)
const warehouse = ref<any>(null)
const loading = ref(false)
const matLoading = ref(false)
const materials = ref<any[]>([])

async function loadWarehouse() {
  loading.value = true
  try {
    const r = await request.get<any,any>('/warehouse/page', { params: { pageSize: 200, warehouseCategory: 'INVENTORY' } })
    warehouse.value = (r?.records || []).find((w:any) => w.id === warehouseId) || null
  } finally { loading.value = false }
}

// 按产品聚合库存：by-warehouse 返回一条品质一条记录，这里归并为每产品一行（A/B/C/不良四列）
function groupStocks(rows: any[]) {
  const map = new Map<number, any>()
  for (const r of rows || []) {
    if (r.productId == null) continue // 仅统计成品库存
    if (!map.has(r.productId)) {
      map.set(r.productId, { productId: r.productId, productName: r.productName || '', qtyA: 0, qtyB: 0, qtyC: 0, qtyDefect: 0 })
    }
    const row = map.get(r.productId)
    const q = Number(r.quantity) || 0
    // 兼容后端 qualityTypeLabel 对 DEFECT 误转为"不良品"的情况
    const qt = r.qualityType
    if (qt === 'A' || qt === 'A规') row.qtyA += q
    else if (qt === 'B' || qt === 'B规') row.qtyB += q
    else if (qt === 'C' || qt === 'C规') row.qtyC += q
    else row.qtyDefect += q // DEFECT / 不良 / 不良品 等归入不良
  }
  return Array.from(map.values())
}

async function loadMaterials() {
  matLoading.value = true
  try {
    const r = await request.get<any,any>(`/warehouse/stock/by-warehouse/${warehouseId}`)
    const rows = r?.records || r?.data || r || []
    materials.value = groupStocks(Array.isArray(rows) ? rows : [])
  } finally { matLoading.value = false }
}

function goLog(row: any) { router.push(`/inventory/warehouse/product-history/${warehouseId}/${row.productId}`) }
function fmt(v?: number) { return v == null ? '0' : parseFloat(Number(v).toFixed(4)).toString() }
function totalQty(row: any) {
  return (Number(row.qtyA) || 0) + (Number(row.qtyB) || 0) + (Number(row.qtyC) || 0) + (Number(row.qtyDefect) || 0)
}



onMounted(() => { loadWarehouse(); loadMaterials() })
</script>

<template>
  <div class="detail-page">
    <el-card shadow="never" v-loading="loading">
      <template #header><span style="font-weight:600">仓库信息</span></template>
      <el-descriptions v-if="warehouse" :column="2" border size="small">
        <el-descriptions-item label="仓库名称" :span="2">{{ warehouse.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="编码">{{ warehouse.code }}</el-descriptions-item>
        <el-descriptions-item label="类型"><el-tag :type="warehouse.warehouseType==='成品仓'?'success':warehouse.warehouseType==='不良仓'?'danger':'info'" size="small">{{ warehouse.warehouseType }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ warehouse.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ warehouse.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ warehouse.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ warehouse.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">库存物料</span></template>
      <el-table :data="materials" border stripe v-loading="matLoading" size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="productName" label="产品/物料" min-width="160" show-overflow-tooltip />
        <el-table-column label="A规" width="90" align="right">
          <template #default="{row}">
            <el-tag v-if="Number(row.qtyA)>0" type="success" size="small">{{ fmt(row.qtyA) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="B规" width="90" align="right">
          <template #default="{row}">
            <el-tag v-if="Number(row.qtyB)>0" type="warning" size="small">{{ fmt(row.qtyB) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="C规" width="90" align="right">
          <template #default="{row}">
            <el-tag v-if="Number(row.qtyC)>0" type="info" size="small">{{ fmt(row.qtyC) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="不良" width="90" align="right">
          <template #default="{row}">
            <el-tag v-if="Number(row.qtyDefect)>0" type="danger" size="small">{{ fmt(row.qtyDefect) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="总库存" width="100" align="right">
          <template #default="{row}"><span style="font-weight:600">{{ fmt(totalQty(row)) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{row}"><el-button type="primary" link size="small" @click="goLog(row)">流水</el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="materials.length===0" style="text-align:center;color:var(--app-text-secondary);padding:24px">暂无库存物料</div>
    </el-card>
  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:12px; }

</style>
