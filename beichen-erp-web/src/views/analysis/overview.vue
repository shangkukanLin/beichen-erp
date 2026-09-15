<script setup lang="ts">
import { ref, computed, onMounted, onActivated, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'

/**
 * 经营概览（经营分析）：**统计区间** KPI + 本年累计 + 资金与往来健康度 + 区间趋势图。
 * 2026-09-15：接入统一「统计区间」组件；KPI 与趋势改为**所选区间**（数据源 /finance/analysis/overview-kpi，
 * 与首页「经营总览」完全同源、同口径）；健康度为**时点快照**，仍取 /finance/analysis/summary。
 * 说明：区间可自定义后"环比"无统一定义，故按首页同样口径去掉了 ▲▼ 环比角标。
 */
const loading = ref(false)
const preset = ref('month')
const range = ref<[string, string] | null>(null)
const ov = ref<any>({ range: {}, kpi: {}, year: {}, series: {} })
const health = ref<any>({})
const trendEmpty = ref(false)
let trendChart: echarts.ECharts | null = null

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtN(v?: any) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function toneColor(tone?: string) {
  if (tone === 'green') return 'var(--app-color-success)'
  if (tone === 'red') return 'var(--app-color-danger)'
  if (tone === 'orange') return 'var(--app-color-warning)'
  if (tone === 'blue') return 'var(--app-color-primary)'
  return ''
}
/** 预设中文名（卡片标题前缀） */
const RANGE_LABELS: Record<string, string> = {
  yesterday: '昨日', today: '今日', week: '本周', month: '本月', quarter: '本季', year: '本年'
}
const rangePrefix = computed(() =>
  ov.value.range?.preset === 'custom' ? '所选区间' : (RANGE_LABELS[ov.value.range?.preset] || ''))

const kpiCards = computed(() => {
  const k = ov.value.kpi || {}
  const p = rangePrefix.value
  return [
    { label: p + '销售金额', value: fmtN(k.saleAmount), tone: 'green' },
    { label: p + '采购支出', value: fmtN(k.purchaseSpend), tone: 'orange' },
    { label: p + '费用支出', value: fmtN(k.expenseSpend), tone: 'orange' },
    { label: p + '净利润', value: fmtN(k.netProfit), tone: Number(k.netProfit) >= 0 ? 'green' : 'red' },
  ]
})
const ytdCards = computed(() => {
  const y = ov.value.year || {}
  return [
    { label: '本年累计销售金额', value: fmtN(y.saleAmount), tone: 'green' },
    { label: '本年累计采购支出', value: fmtN(y.purchaseSpend), tone: 'orange' },
    { label: '本年累计费用支出', value: fmtN(y.expenseSpend), tone: 'orange' },
    { label: '本年累计净利润', value: fmtN(y.netProfit), tone: Number(y.netProfit) >= 0 ? 'green' : 'red' },
  ]
})
const healthCards = computed(() => {
  const h = health.value || {}
  const overdue = Number(h.receivableOverdue) || 0
  return [
    { label: '资金总余额', value: fmtN(h.cashTotal), tone: 'blue', alert: '' },
    { label: '应收未收', value: fmtN(h.receivableUnpaid), tone: 'orange', alert: overdue > 0 ? '逾期 ' + fmtN(overdue) : '' },
    { label: '应付未付', value: fmtN(h.payableUnpaid), tone: 'red', alert: '' },
  ]
})

/**
 * 区间趋势图（与首页「经营总览」同一套口径与画法）：4 条 = 销售金额/采购支出/费用支出/净利润；
 * 粒度由后端决定（≤62 天按天、否则按月）；全为 0 时清图并显示占位。
 */
function renderTrendChart() {
  const el = document.getElementById('trendChart')
  if (!el) return
  const s = ov.value.series || {}
  const pts: any[] = s.points || []
  const byMonth = s.granularity === 'month'
  const KEYS = ['saleAmount', 'purchaseSpend', 'expenseSpend', 'netProfit']
  const num = (p: any, k: string) => Number(p[k]) || 0
  trendEmpty.value = pts.length === 0 || pts.every((p: any) => KEYS.every((k) => num(p, k) === 0))
  trendChart = trendChart || echarts.init(el)
  if (trendEmpty.value) { trendChart.clear(); return }
  // ⚠️ setOption 第二参必须 true（notMerge）：切区间/粒度会改变系列数，否则残留旧系列
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['销售金额', '采购支出', '费用支出', '净利润'], top: 0 },
    grid: { left: 60, right: 20, top: 44, bottom: 30 },
    xAxis: { type: 'category', data: pts.map((p: any) => (byMonth ? p.label : String(p.label).slice(5))) },
    yAxis: { type: 'value' },
    series: [
      { name: '销售金额', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#91cc75' }, data: pts.map((p: any) => num(p, 'saleAmount')) },
      { name: '采购支出', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#e6a23c' }, data: pts.map((p: any) => num(p, 'purchaseSpend')) },
      { name: '费用支出', type: 'line', smooth: true, itemStyle: { color: '#f56c6c' }, data: pts.map((p: any) => num(p, 'expenseSpend')) },
      { name: '净利润', type: 'line', smooth: true, itemStyle: { color: '#5470c6' }, data: pts.map((p: any) => num(p, 'netProfit')) },
    ],
  }, true)
  trendChart.resize()
}

async function loadData() {
  if (preset.value === 'custom' && !(range.value?.length === 2)) return
  loading.value = true
  try {
    const params: any = { preset: preset.value }
    if (preset.value === 'custom' && range.value?.length === 2) {
      params.start = range.value[0]; params.end = range.value[1]
    }
    const [kpi, sum] = await Promise.all([
      request.get<any, any>('/finance/analysis/overview-kpi', { params }),
      request.get<any, any>('/finance/analysis/summary'),
    ])
    ov.value = kpi || { range: {}, kpi: {}, year: {}, series: {} }
    health.value = (sum && sum.health) || {}
  } catch {
    ov.value = { range: {}, kpi: {}, year: {}, series: {} }
    health.value = {}
  } finally { loading.value = false }
  await nextTick()
  renderTrendChart()
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
</script>
<template>
  <div class="p" v-loading="loading">
    <!-- 统计区间：统一组件 StatRange（2026-09-15 全站收口；KPI 与趋势均跟随所选区间） -->
    <div style="margin-bottom:12px">
      <StatRange v-model:preset="preset" v-model:range="range" @change="loadData"/>
    </div>
    <!-- ① 主指标（所选区间） -->
    <div class="stat-grid">
      <div class="stat-card kpi" v-for="k in kpiCards" :key="k.label">
        <div class="stat-label">{{ k.label }}</div>
        <div class="stat-value" :style="{ color: toneColor(k.tone) }">{{ k.value }}</div>
      </div>
    </div>
    <!-- ② 本年累计 -->
    <div class="stat-grid sec" style="grid-template-columns:repeat(4,1fr)">
      <div class="stat-card mini" v-for="y in ytdCards" :key="y.label">
        <div class="stat-label">{{ y.label }}</div>
        <div class="stat-value sm" :style="{ color: toneColor(y.tone) }">{{ y.value }}</div>
      </div>
    </div>
    <!-- ③ 资金与往来健康度 -->
    <div class="stat-grid sec" style="grid-template-columns:repeat(3,1fr)">
      <div class="stat-card mini" v-for="h in healthCards" :key="h.label">
        <div class="stat-label">
          {{ h.label }}
          <el-tag v-if="h.alert" type="danger" size="small" effect="dark" style="margin-left:4px">{{ h.alert }}</el-tag>
        </div>
        <div class="stat-value sm" :style="{ color: toneColor(h.tone) }">{{ h.value }}</div>
      </div>
    </div>
    <!-- ④ 区间趋势（跟随统计区间；全为 0 时给占位提示） -->
    <div class="dim" v-if="trendEmpty" style="margin-bottom:6px">该区间暂无数据</div>
    <div id="trendChart" class="chart-sm"/>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.stat-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-value{font-size:22px;font-weight:600}
.stat-label{font-size:12px;color:var(--el-text-color-secondary);margin-top:4px}
.chart-sm{width:100%;height:180px;margin-top:12px}
.sec{margin-top:12px}
.stat-card.kpi{border-top:3px solid var(--el-color-primary-light-5)}
.stat-value.sm{font-size:16px;font-weight:600}
.stat-sub{margin-top:6px;font-size:12px;display:flex;gap:8px;align-items:center}
.stat-sub .dim{color:var(--el-text-color-secondary)}
.chg.good{color:var(--app-color-success);font-weight:600}
.chg.bad{color:var(--app-color-danger);font-weight:600}
.chg.dim{color:var(--el-text-color-secondary)}
</style>
