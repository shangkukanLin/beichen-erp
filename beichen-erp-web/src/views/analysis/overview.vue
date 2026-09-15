<script setup lang="ts">
import { ref, computed, onMounted, onActivated, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'

/**
 * 经营概览（经营分析）：本月 KPI + 环比、本年累计、资金与往来健康度、近 6 月趋势。
 * 数据来自财务分析 summary 接口，纯只读展示。
 */
const loading = ref(false)
const summary = ref<any>({})
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
// 环比变化：goodDir=true 表示"上升为改善"（收入类）
function chg(cur: any, prev: any, goodDir: boolean) {
  const c = Number(cur) || 0, p = Number(prev) || 0
  if (p === 0) return { text: '', cls: 'dim' }
  const rate = Math.round(((c - p) / Math.abs(p)) * 1000) / 10
  if (rate === 0) return { text: '持平', cls: 'dim' }
  const up = rate > 0
  const good = goodDir ? up : !up
  return { text: (up ? '▲' : '▼') + Math.abs(rate) + '%', cls: good ? 'good' : 'bad' }
}

const kpiCards = computed(() => {
  const cur = summary.value.cur || {}, prev = summary.value.prev || {}
  return [
    { label: '本月销售额', value: fmtN(cur.revenue), chg: chg(cur.revenue, prev.revenue, true), sub: '上月 ' + fmtN(prev.revenue), tone: 'green' },
    { label: '本月毛利', value: fmtN(cur.grossProfit), chg: chg(cur.grossProfit, prev.grossProfit, true), sub: '毛利率 ' + fmt(cur.grossRate) + '%', tone: 'blue' },
    { label: '本月净利润', value: fmtN(cur.netProfit), chg: chg(cur.netProfit, prev.netProfit, true), sub: '上月 ' + fmtN(prev.netProfit), tone: Number(cur.netProfit) >= 0 ? 'green' : 'red' },
    { label: '本月净现金流', value: fmtN(summary.value.curCashNet), chg: chg(summary.value.curCashNet, summary.value.prevCashNet, true), sub: '上月 ' + fmtN(summary.value.prevCashNet), tone: Number(summary.value.curCashNet) >= 0 ? 'green' : 'red' },
  ]
})
const ytdCards = computed(() => {
  const y = summary.value.ytd || {}
  return [
    { label: '累计销售额', value: fmtN(y.revenue), tone: 'green' },
    { label: '累计毛利', value: fmtN(y.grossProfit), tone: 'blue' },
    { label: '累计费用', value: fmtN(y.expense), tone: 'orange' },
    { label: '累计净利润', value: fmtN(y.netProfit), tone: Number(y.netProfit) >= 0 ? 'green' : 'red' },
  ]
})
const healthCards = computed(() => {
  const h = summary.value.health || {}
  const overdue = Number(h.receivableOverdue) || 0
  return [
    { label: '资金总余额', value: fmtN(h.cashTotal), tone: 'blue', alert: '' },
    { label: '应收未收', value: fmtN(h.receivableUnpaid), tone: 'orange', alert: overdue > 0 ? '逾期 ' + fmtN(overdue) : '' },
    { label: '应付未付', value: fmtN(h.payableUnpaid), tone: 'red', alert: '' },
  ]
})

function renderTrendChart() {
  const el = document.getElementById('trendChart')
  if (!el) return
  trendChart = trendChart || echarts.init(el)
  const t = summary.value.trend || []
  const tv = t.flatMap((x: any) => [Number(x.revenue), Number(x.netProfit), Number(x.cashNet)])
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    // 迷你图高度有限不放 legend（系列含义悬停 tooltip 可见），避免文字挤压重叠
    grid: { left: 60, right: 20, top: 16, bottom: 24 },
    xAxis: { type: 'category', data: t.map((x: any) => x.month) },
    yAxis: { type: 'value', max: tv.some((v: number) => v !== 0) ? undefined : 100 },
    series: [
      { name: '销售额', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#91cc75' }, data: t.map((x: any) => Number(x.revenue)) },
      { name: '净利润', type: 'line', smooth: true, itemStyle: { color: '#5470c6' }, data: t.map((x: any) => Number(x.netProfit)) },
      { name: '净现金流', type: 'line', smooth: true, itemStyle: { color: '#ee6666' }, data: t.map((x: any) => Number(x.cashNet)) },
    ],
  })
  trendChart.resize()
}

async function loadData() {
  loading.value = true
  try {
    summary.value = await request.get<any, any>('/finance/analysis/summary') || {}
  } catch { summary.value = {} } finally { loading.value = false }
  await nextTick()
  renderTrendChart()
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
</script>
<template>
  <div class="p" v-loading="loading">
    <!-- ① 主指标（本月 + 环比） -->
    <div class="stat-grid">
      <div class="stat-card kpi" v-for="k in kpiCards" :key="k.label">
        <div class="stat-label">{{ k.label }}</div>
        <div class="stat-value" :style="{ color: toneColor(k.tone) }">{{ k.value }}</div>
        <div class="stat-sub">
          <span :class="'chg ' + k.chg.cls">{{ k.chg.text }}</span>
          <span class="dim">{{ k.sub }}</span>
        </div>
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
    <!-- ④ 近 6 月经营趋势 -->
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
