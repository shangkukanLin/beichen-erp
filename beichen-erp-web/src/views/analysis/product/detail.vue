<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'

/**
 * 产品分析钻取：某产品在统计区间内的**销售明细行 + 退货明细行**（2026-10-02 新增）。
 * 由产品分析页「明细」按钮带 query 进入（preset/start/end/productId/productName）。
 *
 * <p>两行同一张表、靠「类型」区分（销售 / 退货）：这样"销售额 − 退货额 = 净额"能在**同一屏逐行核对**，
 * 合计行给的是**净额**（不是把两类直接相加 —— 那会得出"销售+退货"的无意义数字）。</p>
 * <p>单号按类型分流：销售 → 销售单详情；退货 → 销售退货单详情。</p>
 */
const route = useRoute(); const router = useRouter()
const preset = computed(() => String(route.query.preset || 'month'))
const start = computed(() => route.query.start ? String(route.query.start) : undefined)
const end = computed(() => route.query.end ? String(route.query.end) : undefined)
const productId = computed(() => route.query.productId ? Number(route.query.productId) : undefined)
const dimName = computed(() => String(route.query.productName || ''))

const loading = ref(false)
const data = ref<any>({
  start: '', end: '', records: [], saleQty: 0, returnQty: 0, netQty: 0,
  saleAmount: 0, returnAmount: 0, netAmount: 0,
})

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtQty(v?: any) {
  if (v == null) return '0'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { preset: preset.value, start: start.value, end: end.value }
    if (productId.value) params.productId = productId.value
    data.value = await request.get<any, any>('/product/analysis/records', { params }) || { records: [] }
  } catch { data.value = { records: [] } } finally { loading.value = false }
}

/** 单号分流：退货行进销售退货单详情，销售行进销售单详情 */
function openBill(row: any) {
  if (row.type === 'RETURN') router.push(`/sale/return/detail/${row.billId}`)
  else router.push(`/inventory/sale/detail/${row.billId}`)
}

/** 合计行：数量列(5) 与金额列(7) 给**净额**（销售 − 退货），其余列留空 */
function summaries({ columns }: any) {
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '净额合计'
    if (i === 5) return fmtQty(data.value.netQty)
    if (i === 7) return fmt(data.value.netAmount)
    return ''
  })
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
</script>
<template>
  <PageShell :title="`${dimName || '产品'}销售 / 退货明细`" back-fallback="/analysis/product">
    <template #sub>
      <span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">
        {{ data.start }} ~ {{ data.end }}｜共 {{ data.records?.length || 0 }} 行｜
        销售 {{ fmt(data.saleAmount) }} − 退货 {{ fmt(data.returnAmount) }} = <b>净 {{ fmt(data.netAmount) }}</b>
      </span>
    </template>

    <el-table v-loading="loading" :data="data.records" border stripe size="small" show-summary :summary-method="summaries">
      <el-table-column prop="date" label="日期" width="104" align="center"/>
      <el-table-column label="类型" width="64" align="center">
        <template #default="{ row }">
          <el-tag :type="row.type === 'RETURN' ? 'danger' : 'success'" size="small">
            {{ row.type === 'RETURN' ? '退货' : '销售' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="单号" min-width="150">
        <template #default="{ row }">
          <el-link type="primary" underline="never" @click="openBill(row)">{{ row.billNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.customerName || '—' }}</template>
      </el-table-column>
      <el-table-column label="品质" width="74" align="center">
        <template #default="{ row }">{{ row.qualityType || '—' }}</template>
      </el-table-column>
      <el-table-column label="数量" width="78" align="right">
        <template #default="{ row }">{{ fmtQty(row.qty) }}</template>
      </el-table-column>
      <el-table-column label="单价" width="96" align="right">
        <template #default="{ row }">{{ fmt(row.unitPrice) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }">
          <span :style="{ color: row.type === 'RETURN' ? 'var(--app-color-danger)' : 'var(--app-color-success)' }">{{ fmt(row.amount) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="warehouseName" label="仓库" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.warehouseName || '—' }}</template>
      </el-table-column>
    </el-table>
  </PageShell>
</template>
<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css） */
</style>
