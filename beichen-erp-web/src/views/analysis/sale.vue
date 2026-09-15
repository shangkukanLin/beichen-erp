<script setup lang="ts">
import { ref, computed, onMounted, onActivated, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'

/**
 * 销售分析（经营分析）：销售额趋势 + 产品排行 + 仓库分布。
 * 口径：销售额=已审核销售单（审核日归期）；退货额=已审核销售退单；净销售额=销售额-退货额。
 * 排行行点「明细」下钻到该维度的销售单列表（/analysis/sale/detail）。
 */
const router = useRouter()
const preset = ref('month')
const range = ref<[string, string] | null>(null)
const loading = ref(false)
const data = ref<any>({ start: '', end: '', summary: {}, dates: [], amounts: [], returns: [], byProduct: [], byWarehouse: [] })
let trendChart: echarts.ECharts | null = null

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtN(v?: any) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function pct(part: number, total: number) {
  if (!total) return '0.0'
  return (Math.round((part / total) * 1000) / 10).toFixed(1)
}

const summary = computed(() => data.value.summary || {})

async function loadData() {
  if (preset.value === 'custom' && !(range.value?.length === 2)) return
  loading.value = true
  try {
    const params: any = { preset: preset.value }
    if (preset.value === 'custom' && range.value?.length === 2) {
      params.start = range.value[0]; params.end = range.value[1]
    }
    data.value = await request.get<any, any>('/sale/analysis', { params })
      || { start: '', end: '', summary: {}, dates: [], amounts: [], returns: [], byProduct: [], byWarehouse: [] }
  } catch {
    data.value = { start: '', end: '', summary: {}, dates: [], amounts: [], returns: [], byProduct: [], byWarehouse: [] }
  } finally { loading.value = false }
  await nextTick()
  renderChart()
}

// 预设切换/日期变更的"清空 + 触发"逻辑已收口到 StatRange 组件，页面只需 loadData

function renderChart() {
  const el = document.getElementById('saleTrendChart')
  if (!el) return
  trendChart = trendChart || echarts.init(el)
  const amounts = (data.value.amounts || []).map(Number)
  const returns = (data.value.returns || []).map(Number)
  const hasData = amounts.concat(returns).some((v: number) => v !== 0)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['销售额', '退货额'], top: 0 },
    grid: { left: 70, right: 20, top: 44, bottom: 30 },
    xAxis: { type: 'category', data: data.value.dates || [] },
    yAxis: { type: 'value', max: hasData ? undefined : 100 },
    series: [
      { name: '销售额', type: 'bar', itemStyle: { color: '#91cc75' }, data: amounts },
      { name: '退货额', type: 'bar', itemStyle: { color: '#ee6666' }, data: returns },
    ],
  })
  trendChart.resize()
}

/** 下钻：带维度参数跳销售单明细页 */
function drill(params: Record<string, any>) {
  const query: any = { preset: preset.value, ...params }
  if (preset.value === 'custom' && range.value?.length === 2) {
    query.start = range.value[0]; query.end = range.value[1]
  }
  router.push({ path: '/analysis/sale/detail', query })
}

function productSummary({ columns }: any) {
  const rows = data.value.byProduct || []
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 2) return fmt(rows.reduce((s: number, r: any) => s + Number(r.quantity || 0), 0))
    if (i === 3) return fmt(rows.reduce((s: number, r: any) => s + Number(r.amount || 0), 0))
    return ''
  })
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
watch([preset, range], () => {})
</script>
<template>
  <div class="p" v-loading="loading">
    <div class="toolbar">
      <!-- 统计区间：统一组件（2026-09-15 全站收口） -->
      <StatRange v-model:preset="preset" v-model:range="range" @change="loadData"/>
    </div>
    <!-- KPI -->
    <div class="stat-grid">
      <div class="stat-card mini">
        <div class="stat-label">销售额</div>
        <div class="stat-value sm" style="color:var(--app-color-success)">{{ fmtN(summary.amount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">退货额</div>
        <div class="stat-value sm" style="color:var(--app-color-danger)">{{ fmtN(summary.returnAmount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">净销售额（销售 − 退货）</div>
        <div class="stat-value sm" style="font-weight:600">{{ fmtN(summary.netAmount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">订单数 / 客单价</div>
        <div class="stat-value sm">{{ summary.orderCount || 0 }} 单 / {{ fmtN(summary.avgOrder) }}</div>
      </div>
    </div>
    <div id="saleTrendChart" class="chart"/>
    <el-row :gutter="12" style="margin-top:12px">
      <!-- 产品排行 -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>产品销售额排行（点「明细」看该产品的销售单）</template>
          <el-table :data="data.byProduct" border stripe size="small" max-height="360" show-summary :summary-method="productSummary">
            <el-table-column prop="sku" label="SKU" width="120" show-overflow-tooltip/>
            <el-table-column prop="productName" label="产品" min-width="120" show-overflow-tooltip/>
            <el-table-column label="数量" width="90" align="right"><template #default="{row}">{{ fmt(row.quantity) }}</template></el-table-column>
            <el-table-column label="金额" width="120" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
            <el-table-column label="占比" width="80" align="right"><template #default="{row}">{{ pct(Number(row.amount), Number(summary.amount)) }}%</template></el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{row}">
                <el-button link type="primary" @click="drill({ productId: row.productId, productName: row.productName })">明细</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <!-- 仓库分布 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>仓库销售分布（点「明细」看该仓库的销售单）</template>
          <el-table :data="data.byWarehouse" border stripe size="small" max-height="360">
            <el-table-column prop="warehouseName" label="仓库" min-width="120" show-overflow-tooltip/>
            <el-table-column label="订单数" width="80" align="center"><template #default="{row}">{{ row.orderCount }}</template></el-table-column>
            <el-table-column label="金额" width="120" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{row}">
                <el-button link type="primary" @click="drill({ warehouseId: row.warehouseId, warehouseName: row.warehouseName })">明细</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.toolbar{display:flex;align-items:center;margin-bottom:12px}
.stat-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-label{font-size:12px;color:var(--el-text-color-secondary);margin-top:4px}
.stat-value.sm{font-size:16px;font-weight:600}
.chart{width:100%;height:300px}
</style>
