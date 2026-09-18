<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'

/** 客户分析钻取：某客户在统计区间内的销售单明细（由客户分析页「明细」进入） */
const route = useRoute(); const router = useRouter()
const customerId = computed(() => Number(route.query.customerId) || 0)
const preset = computed(() => String(route.query.preset || 'month'))
const start = computed(() => route.query.start ? String(route.query.start) : undefined)
const end = computed(() => route.query.end ? String(route.query.end) : undefined)

const loading = ref(false)
const data = ref<any>({ start: '', end: '', customerName: '', totalAmount: 0, records: [] })

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }

async function loadData() {
  loading.value = true
  try {
    data.value = await request.get<any, any>('/customer/analysis/records', {
      params: { customerId: customerId.value, preset: preset.value, start: start.value, end: end.value },
    }) || { records: [] }
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
      <el-button :icon="'Back'" size="small" @click="router.push('/analysis/customer')">返回</el-button>
      <span class="title">{{ data.customerName || '客户' }}销售单明细</span>
      <span class="dim">{{ data.start }} ~ {{ data.end }}｜共 {{ data.records?.length || 0 }} 单</span>
    </div>
    <el-table v-loading="loading" :data="data.records" border stripe size="small" show-summary :summary-method="summaries">
      <el-table-column label="单号" min-width="150">
        <template #default="{ row }">
          <el-link type="primary" underline="never" @click="router.push(`/inventory/sale/detail/${row.billId}`)">{{ row.billNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="date" label="日期" width="110" align="center"/>
      <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip/>
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
