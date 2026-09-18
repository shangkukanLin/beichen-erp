<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'

/**
 * 销售分析钻取：某产品 / 仓库在统计区间内的销售单明细。
 * 由销售分析页「明细」按钮带 query 进入（preset/start/end/productId/warehouseId）。
 * 单号可点击进销售单详情；keep-alive 下用 computed 取路由参数。
 */
const route = useRoute(); const router = useRouter()
const preset = computed(() => String(route.query.preset || 'month'))
const start = computed(() => route.query.start ? String(route.query.start) : undefined)
const end = computed(() => route.query.end ? String(route.query.end) : undefined)
const productId = computed(() => route.query.productId ? Number(route.query.productId) : undefined)
const warehouseId = computed(() => route.query.warehouseId ? Number(route.query.warehouseId) : undefined)
const dimName = computed(() => String(route.query.productName || route.query.warehouseName || ''))

const loading = ref(false)
const data = ref<any>({ start: '', end: '', totalAmount: 0, records: [] })

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }

async function loadData() {
  loading.value = true
  try {
    const params: any = { preset: preset.value, start: start.value, end: end.value }
    if (productId.value) params.productId = productId.value
    if (warehouseId.value) params.warehouseId = warehouseId.value
    data.value = await request.get<any, any>('/sale/analysis/records', { params }) || { records: [] }
  } catch { data.value = { records: [] } } finally { loading.value = false }
}

function summaries({ columns }: any) {
  const total = data.value.records.reduce((s: number, r: any) => s + Number(r.amount || 0), 0)
  return columns.map((_c: any, i: number) => (i === 0 ? '合计' : (i === 4 ? fmt(total) : '')))
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
</script>
<template>
  <div class="p">
    <div class="toolbar">
      <el-button :icon="'Back'" size="small" @click="router.push('/analysis/sale')">返回</el-button>
      <span class="title">{{ dimName || '销售' }}单据明细</span>
      <span class="dim">{{ data.start }} ~ {{ data.end }}｜共 {{ data.records?.length || 0 }} 单</span>
    </div>
    <el-table v-loading="loading" :data="data.records" border stripe size="small" show-summary :summary-method="summaries">
      <el-table-column label="单号" min-width="150">
        <template #default="{ row }">
          <el-link type="primary" underline="never" @click="router.push(`/inventory/sale/detail/${row.billId}`)">{{ row.billNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="date" label="日期" width="110" align="center"/>
      <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.customerName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="warehouseName" label="仓库" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.warehouseName || '—' }}</template>
      </el-table-column>
      <el-table-column label="金额" width="130" align="right">
        <template #default="{ row }"><span style="color:var(--app-color-success);font-weight:600">{{ fmt(row.amount) }}</span></template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.toolbar{display:flex;align-items:center;gap:12px;margin-bottom:12px}
.title{font-weight:600;font-size:var(--app-font-md)}
.dim{font-size:var(--app-font-xs);color:var(--el-text-color-secondary)}
</style>
