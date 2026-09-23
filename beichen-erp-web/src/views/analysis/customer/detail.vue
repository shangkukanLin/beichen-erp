<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'

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
  <!-- 统一骨架（2026-09-23 全站定稿口径）：标题与区间信息移入骨架页头；返回交骨架；本页只读 -->
  <PageShell :title="`${data.customerName || '客户'}销售单明细`" back-fallback="/analysis/customer">
    <template #sub><span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ data.start }} ~ {{ data.end }}｜共 {{ data.records?.length || 0 }} 单</span></template>

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
  </PageShell>
</template>
<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .p/.toolbar/.title/.dim 已删除 */
</style>
