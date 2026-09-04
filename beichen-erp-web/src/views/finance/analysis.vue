<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import * as XLSX from 'xlsx'
import request from '@/utils/request'

// 财务分析：经营概览 / 利润表 / 资金趋势 / 应收应付账龄
const tab = ref('summary')
const months = ref(12)
const dateRange = ref<[string, string] | null>(null)
const summary = ref<any>({})
const profit = ref<any>({ months: [], rows: [] })
const cash = ref<any>({ months: [], income: [], expense: [], net: [], accounts: [] })
const aging = ref<any>({})
const tax = ref<any>({ months: [], rows: [], summary: {} })
let profitChart: echarts.ECharts | null = null
let cashChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null
let taxChart: echarts.ECharts | null = null

async function loadAll() {
  try {
    // 利润表：选了日期区间按区间查，否则按近 N 个月
    const profitParams: any = dateRange.value && dateRange.value[0] && dateRange.value[1]
      ? { start: dateRange.value[0], end: dateRange.value[1] }
      : { months: months.value }
    const [s, p, c, a, t] = await Promise.all([
      request.get<any, any>('/finance/analysis/summary'),
      request.get<any, any>('/finance/analysis/profit', { params: profitParams }),
      request.get<any, any>('/finance/analysis/cash-trend', { params: { months: months.value } }),
      request.get<any, any>('/finance/analysis/aging'),
      request.get<any, any>('/finance/analysis/tax', { params: profitParams }),
    ])
    summary.value = s || {}
    profit.value = p || { months: [], rows: [] }
    cash.value = c || { months: [], income: [], expense: [], net: [], accounts: [] }
    aging.value = a || {}
    tax.value = t || { months: [], rows: [], summary: {} }
  } catch {}
  await nextTick()
  renderCharts()
}
// Tab 切换瞬间容器尺寸刚从隐藏恢复，延迟渲染避免按 0 宽度布局（legend 会竖排挤压）
function onTabChange() {
  nextTick(() => setTimeout(() => renderCharts(), 60))
}

// 利润表：快捷月数与自定义区间互斥（选其一即清另一个）
function onPresetChange() { dateRange.value = null; loadAll() }
function onRangeChange() { if (dateRange.value && dateRange.value[0] && dateRange.value[1]) loadAll() }

function renderCharts() {
  const rows = profit.value.rows || []
  const pm = rows.map((r: any) => r.month)
  const el1 = document.getElementById('profitChart')
  if (el1) {
    profitChart = profitChart || echarts.init(el1)
    // 数据全为 0 时固定 y 轴上限，避免出现 0~1 的刻度导致文字与轴重叠
    const pv = rows.flatMap((r: any) => [Number(r.revenue), Number(r.cost), Number(r.grossProfit), Number(r.netProfit)])
    const hasData = pv.some((v: number) => v !== 0)
    profitChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['营业收入', '营业成本', '毛利', '净利润'], top: 0 },
      grid: { left: 60, right: 20, top: 44, bottom: 30 },
      xAxis: { type: 'category', data: pm },
      yAxis: { type: 'value', max: hasData ? undefined : 100 },
      series: [
        { name: '营业收入', type: 'line', smooth: true, data: rows.map((r: any) => Number(r.revenue)) },
        { name: '营业成本', type: 'line', smooth: true, data: rows.map((r: any) => Number(r.cost)) },
        { name: '毛利', type: 'line', smooth: true, data: rows.map((r: any) => Number(r.grossProfit)) },
        { name: '净利润', type: 'line', smooth: true, data: rows.map((r: any) => Number(r.netProfit)) },
      ],
    })
    profitChart.resize()
  }
  const el2 = document.getElementById('cashChart')
  if (el2) {
    cashChart = cashChart || echarts.init(el2)
    const cv = (cash.value.income || []).concat(cash.value.expense || [], cash.value.net || []).map(Number)
    const cHas = cv.some((v: number) => v !== 0)
    cashChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['资金收入', '资金支出', '净现金流'], top: 0 },
      grid: { left: 60, right: 20, top: 44, bottom: 30 },
      xAxis: { type: 'category', data: cash.value.months || [] },
      yAxis: { type: 'value', max: cHas ? undefined : 100 },
      series: [
        { name: '资金收入', type: 'bar', data: (cash.value.income || []).map(Number) },
        { name: '资金支出', type: 'bar', data: (cash.value.expense || []).map(Number) },
        { name: '净现金流', type: 'line', smooth: true, data: (cash.value.net || []).map(Number) },
      ],
    })
    cashChart.resize()
  }
  renderTrendChart()
  renderTaxChart()
}

// 税务分析：按月堆叠柱（销售已税 vs 未税）+ 已税占比趋势线
function renderTaxChart() {
  if (tab.value !== 'tax') return
  const el = document.getElementById('taxChart')
  if (!el) return
  taxChart = taxChart || echarts.init(el)
  const rows = tax.value.rows || []
  const tv = rows.flatMap((r: any) => [Number(r.saleTaxed), Number(r.saleUntaxed), Number(r.purchaseTaxed), Number(r.purchaseUntaxed)])
  taxChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['已税销售额', '未税销售额', '已税占比'], top: 0 },
    grid: { left: 60, right: 60, top: 44, bottom: 30 },
    xAxis: { type: 'category', data: rows.map((r: any) => r.month) },
    yAxis: [
      { type: 'value', max: tv.some((v: number) => v !== 0) ? undefined : 100, name: '金额' },
      { type: 'value', max: 100, min: 0, axisLabel: { formatter: '{value}%' }, name: '占比' },
    ],
    series: [
      { name: '已税销售额', type: 'bar', stack: 'sale', itemStyle: { color: '#5470c6' }, data: rows.map((r: any) => Number(r.saleTaxed)) },
      { name: '未税销售额', type: 'bar', stack: 'sale', itemStyle: { color: '#91cc75' }, data: rows.map((r: any) => Number(r.saleUntaxed)) },
      { name: '已税占比', type: 'line', smooth: true, yAxisIndex: 1, itemStyle: { color: '#fac858' }, data: rows.map((r: any) => Number(r.saleRate)) },
    ],
  })
  taxChart.resize()
}

// 概览页：近 6 月经营趋势迷你图（销售额柱 + 净利润/净现金流线）
function renderTrendChart() {
  if (tab.value !== 'summary') return
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
function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
// 千分位格式（概览卡片用）
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

// 环比变化：goodDir=true 表示"上升为改善"（收入类）；false 表示"下降为改善"（成本费用类）
function chg(cur: any, prev: any, goodDir: boolean) {
  const c = Number(cur) || 0, p = Number(prev) || 0
  if (p === 0) return { text: '', cls: 'dim' }
  const rate = Math.round(((c - p) / Math.abs(p)) * 1000) / 10
  if (rate === 0) return { text: '持平', cls: 'dim' }
  const up = rate > 0
  const good = goodDir ? up : !up
  return { text: (up ? '▲' : '▼') + Math.abs(rate) + '%', cls: good ? 'good' : 'bad' }
}

// ① 主指标卡（本月 + 环比 + 副标题）
const kpiCards = computed(() => {
  const cur = summary.value.cur || {}, prev = summary.value.prev || {}
  return [
    { label: '本月销售额', value: fmtN(cur.revenue), chg: chg(cur.revenue, prev.revenue, true), sub: '上月 ' + fmtN(prev.revenue), tone: 'green' },
    { label: '本月毛利', value: fmtN(cur.grossProfit), chg: chg(cur.grossProfit, prev.grossProfit, true), sub: '毛利率 ' + fmt(cur.grossRate) + '%', tone: 'blue' },
    { label: '本月净利润', value: fmtN(cur.netProfit), chg: chg(cur.netProfit, prev.netProfit, true), sub: '上月 ' + fmtN(prev.netProfit), tone: Number(cur.netProfit) >= 0 ? 'green' : 'red' },
    { label: '本月净现金流', value: fmtN(summary.value.curCashNet), chg: chg(summary.value.curCashNet, summary.value.prevCashNet, true), sub: '上月 ' + fmtN(summary.value.prevCashNet), tone: Number(summary.value.curCashNet) >= 0 ? 'green' : 'red' },
  ]
})

// ② 本年累计
const ytdCards = computed(() => {
  const y = summary.value.ytd || {}
  return [
    { label: '累计销售额', value: fmtN(y.revenue), tone: 'green' },
    { label: '累计毛利', value: fmtN(y.grossProfit), tone: 'blue' },
    { label: '累计费用', value: fmtN(y.expense), tone: 'orange' },
    { label: '累计净利润', value: fmtN(y.netProfit), tone: Number(y.netProfit) >= 0 ? 'green' : 'red' },
  ]
})

// ③ 资金与往来健康度（逾期应收 > 0 时红色预警）
const healthCards = computed(() => {
  const h = summary.value.health || {}
  const overdue = Number(h.receivableOverdue) || 0
  return [
    { label: '资金总余额', value: fmtN(h.cashTotal), tone: 'blue', alert: '' },
    { label: '应收未收', value: fmtN(h.receivableUnpaid), tone: 'orange', alert: overdue > 0 ? '逾期 ' + fmtN(overdue) : '' },
    { label: '应付未付', value: fmtN(h.payableUnpaid), tone: 'red', alert: '' },
  ]
})
// 发票汇总（税务口径，全部已登记发票，与区间选择无关）
const invoiceSummary = computed(() => {
  const i = tax.value.invoice || {}
  return { saleTax: i.saleTax, saleAmount: i.saleAmount, saleCount: i.saleCount || 0, purchaseTax: i.purchaseTax, purchaseAmount: i.purchaseAmount, purchaseCount: i.purchaseCount || 0, payable: i.payable }
})

// 税务分析：3 张汇总卡（区间合计，已税金额+税额+未税+占比）
const taxCards = computed(() => {
  const s = tax.value.summary || {}
  return [
    { label: '销售（销项）', taxed: s.saleTaxed, tax: s.saleTaxedTax, untaxed: s.saleUntaxed, rate: s.saleRate, tone: 'green' },
    { label: '采购（进项）', taxed: s.purchaseTaxed, tax: s.purchaseTaxedTax, untaxed: s.purchaseUntaxed, rate: s.purchaseRate, tone: 'blue' },
    { label: '委外加工', taxed: s.outsourceTaxed, tax: s.outsourceTaxedTax, untaxed: s.outsourceUntaxed, rate: s.outsourceRate, tone: 'orange' },
  ]
})

// ==================== 导出 Excel（SheetJS，纯前端） ====================
function today() { return new Date().toISOString().slice(0, 10) }
function saveBook(ws: XLSX.WorkSheet, sheet: string, file: string) {
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, sheet)
  XLSX.writeFile(wb, file)
}

// 导出利润表：当前区间各月行 + 合计行（毛利率按合计重算）
function exportProfit() {
  const rows = profit.value.rows || []
  const t = rows.reduce((a: any, r: any) => ({
    revenue: a.revenue + Number(r.revenue), loss: a.loss + Number(r.loss),
    cost: a.cost + Number(r.cost), grossProfit: a.grossProfit + Number(r.grossProfit),
    expense: a.expense + Number(r.expense), netProfit: a.netProfit + Number(r.netProfit),
  }), { revenue: 0, loss: 0, cost: 0, grossProfit: 0, expense: 0, netProfit: 0 })
  const aoa: (string | number)[][] = [['月份', '营业收入', '折损', '营业成本', '毛利', '毛利率%', '费用', '净利润']]
  rows.forEach((r: any) => aoa.push([r.month, Number(r.revenue), Number(r.loss), Number(r.cost), Number(r.grossProfit), Number(r.grossRate), Number(r.expense), Number(r.netProfit)]))
  aoa.push(['合计', t.revenue, t.loss, t.cost, t.grossProfit, t.revenue ? Math.round((t.grossProfit / t.revenue) * 10000) / 100 : 0, t.expense, t.netProfit])
  saveBook(XLSX.utils.aoa_to_sheet(aoa), '利润表', `利润表_${today()}.xlsx`)
}

// 导出税务分析：当前区间各月行 + 合计行（占比按合计重算）
function exportTax() {
  const rows = tax.value.rows || []
  const keys = ['saleTaxed', 'saleTaxedTax', 'saleUntaxed', 'purchaseTaxed', 'purchaseTaxedTax', 'purchaseUntaxed', 'outsourceTaxed', 'outsourceTaxedTax', 'outsourceUntaxed'] as const
  const t: any = rows.reduce((a: any, r: any) => {
    keys.forEach(k => (a[k] = (a[k] || 0) + Number(r[k])))
    return a
  }, {})
  const pct = (p: number, total: number) => (total ? Math.round((p / total) * 10000) / 100 : 0)
  const aoa: (string | number)[][] = [[
    '月份', '销售-已税', '销售-销项税额', '销售-未税', '销售-已税占比%',
    '采购-已税', '采购-进项税额', '采购-未税', '采购-已税占比%',
    '委外-已税', '委外-税额', '委外-未税', '委外-已税占比%',
  ]]
  rows.forEach((r: any) => aoa.push([
    r.month, Number(r.saleTaxed), Number(r.saleTaxedTax), Number(r.saleUntaxed), Number(r.saleRate),
    Number(r.purchaseTaxed), Number(r.purchaseTaxedTax), Number(r.purchaseUntaxed), Number(r.purchaseRate),
    Number(r.outsourceTaxed), Number(r.outsourceTaxedTax), Number(r.outsourceUntaxed), Number(r.outsourceRate),
  ]))
  aoa.push([
    '合计', t.saleTaxed, t.saleTaxedTax, t.saleUntaxed, pct(t.saleTaxed, t.saleTaxed + t.saleUntaxed),
    t.purchaseTaxed, t.purchaseTaxedTax, t.purchaseUntaxed, pct(t.purchaseTaxed, t.purchaseTaxed + t.purchaseUntaxed),
    t.outsourceTaxed, t.outsourceTaxedTax, t.outsourceUntaxed, pct(t.outsourceTaxed, t.outsourceTaxed + t.outsourceUntaxed),
  ])
  saveBook(XLSX.utils.aoa_to_sheet(aoa), '税务分析', `税务分析_${today()}.xlsx`)
}
onMounted(() => { loadAll() })
</script>
<template>
  <div class="p">
    <el-card shadow="never" class="table-card">
      <el-tabs v-model="tab" @tab-change="onTabChange">
      <!-- Tab1 经营概览 -->
      <el-tab-pane label="经营概览" name="summary">
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
      </el-tab-pane>
      <!-- Tab2 利润表 -->
      <el-tab-pane label="利润表" name="profit">
        <div class="toolbar" style="margin-bottom:12px">
          <span style="margin-right:8px">统计区间</span>
          <el-select v-model="months" style="width:120px" @change="onPresetChange">
            <el-option :value="6" label="近 6 个月"/>
            <el-option :value="12" label="近 12 个月"/>
            <el-option :value="24" label="近 24 个月"/>
          </el-select>
          <span style="margin:0 8px">或自定义</span>
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="开始日期" end-placeholder="结束日期" style="width:260px"
            :clearable="true" @change="onRangeChange"/>
          <el-button type="primary" plain size="small" style="margin-left:12px" @click="exportProfit">导出 Excel</el-button>
        </div>
        <div id="profitChart" class="chart"/>
        <el-table :data="profit.rows" border stripe size="small" style="margin-top:12px">
          <el-table-column prop="month" label="月份" width="100" align="center"/>
          <el-table-column label="营业收入" width="120" align="right"><template #default="{row}">{{ fmt(row.revenue) }}</template></el-table-column>
          <el-table-column label="折损" width="90" align="right"><template #default="{row}"><span :style="{color: Number(row.loss)>0?'var(--app-color-success)':''}">{{ fmt(row.loss) }}</span></template></el-table-column>
          <el-table-column label="营业成本" width="120" align="right"><template #default="{row}">{{ fmt(row.cost) }}</template></el-table-column>
          <el-table-column label="毛利" width="120" align="right"><template #default="{row}"><span style="color:var(--app-color-success)">{{ fmt(row.grossProfit) }}</span></template></el-table-column>
          <el-table-column label="毛利率" width="90" align="right"><template #default="{row}">{{ fmt(row.grossRate) }}%</template></el-table-column>
          <el-table-column label="费用" width="120" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.expense) }}</span></template></el-table-column>
          <el-table-column label="净利润" width="130" align="right"><template #default="{row}"><span :style="{color: Number(row.netProfit)>=0?'var(--app-color-success)':'var(--app-color-danger)', fontWeight:'bold'}">{{ fmt(row.netProfit) }}</span></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <!-- Tab3 资金经营情况 -->
      <el-tab-pane label="资金趋势" name="cash">
        <div id="cashChart" class="chart"/>
        <el-table :data="cash.accounts" border stripe size="small" style="margin-top:12px">
          <el-table-column type="index" width="55" align="center"/>
          <el-table-column prop="name" label="账户名称" min-width="140"/>
          <el-table-column prop="type" label="类型" width="100" align="center"><template #default="{row}"><el-tag size="small">{{ row.type }}</el-tag></template></el-table-column>
          <el-table-column prop="balance" label="余额" width="150" align="right"><template #default="{row}">{{ fmt(row.balance) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
      <!-- Tab4 应收应付分析 -->
      <el-tab-pane label="应收应付分析" name="aging">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-card shadow="never" style="margin-bottom:12px">
              <template #header>应收账龄（未结清）</template>
              <div class="stat-grid" style="grid-template-columns:repeat(3,1fr)">
                <div class="stat-card"><div class="stat-value">{{ fmt(aging.receivable?.notDue) }}</div><div class="stat-label">未到期</div></div>
                <div class="stat-card"><div class="stat-value">{{ fmt(aging.receivable?.d30) }}</div><div class="stat-label">逾期 1-30 天</div></div>
                <div class="stat-card"><div class="stat-value">{{ fmt(Number(aging.receivable?.d60) + Number(aging.receivable?.d60p)) }}</div><div class="stat-label">逾期 30 天以上</div></div>
              </div>
              <div style="margin-top:8px">回款率：<b>{{ fmt(aging.receivable?.settledRate) }}%</b>（已收 {{ fmt(aging.receivable?.paid) }} / 应收 {{ fmt(aging.receivable?.total) }}）</div>
            </el-card>
            <el-card shadow="never">
              <template #header>客户欠款 TOP5</template>
              <el-table :data="aging.topCustomers" size="small" border>
                <el-table-column prop="name" label="客户" min-width="120"/>
                <el-table-column prop="unpaid" label="未收款" width="120" align="right"><template #default="{row}">{{ fmt(row.unpaid) }}</template></el-table-column>
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never" style="margin-bottom:12px">
              <template #header>应付账龄（未结清）</template>
              <div class="stat-grid" style="grid-template-columns:repeat(3,1fr)">
                <div class="stat-card"><div class="stat-value">{{ fmt(aging.payable?.notDue) }}</div><div class="stat-label">未到期</div></div>
                <div class="stat-card"><div class="stat-value">{{ fmt(aging.payable?.d30) }}</div><div class="stat-label">逾期 1-30 天</div></div>
                <div class="stat-card"><div class="stat-value">{{ fmt(Number(aging.payable?.d60) + Number(aging.payable?.d60p)) }}</div><div class="stat-label">逾期 30 天以上</div></div>
              </div>
              <div style="margin-top:8px">付款率：<b>{{ fmt(aging.payable?.settledRate) }}%</b>（已付 {{ fmt(aging.payable?.paid) }} / 应付 {{ fmt(aging.payable?.total) }}）</div>
            </el-card>
            <el-card shadow="never">
              <template #header>供应商应付 TOP5</template>
              <el-table :data="aging.topSuppliers" size="small" border>
                <el-table-column prop="name" label="供应商" min-width="120"/>
                <el-table-column prop="unpaid" label="未付款" width="120" align="right"><template #default="{row}">{{ fmt(row.unpaid) }}</template></el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
      <!-- Tab5 税务分析 -->
      <el-tab-pane label="税务分析" name="tax">
        <!-- 发票汇总（税务口径，全部已登记发票） -->
        <div class="stat-grid" style="grid-template-columns:repeat(3,1fr);margin-bottom:12px">
          <div class="stat-card mini">
            <div class="stat-label">销项税额（发票 <b>{{ invoiceSummary.saleCount }}</b> 张）</div>
            <div class="stat-value sm" style="color:var(--app-color-success)">{{ fmtN(invoiceSummary.saleTax) }}</div>
            <div class="stat-sub"><span class="dim">不含税 {{ fmtN(invoiceSummary.saleAmount) }}</span></div>
          </div>
          <div class="stat-card mini">
            <div class="stat-label">进项税额（发票 <b>{{ invoiceSummary.purchaseCount }}</b> 张）</div>
            <div class="stat-value sm" style="color:var(--app-color-warning)">{{ fmtN(invoiceSummary.purchaseTax) }}</div>
            <div class="stat-sub"><span class="dim">不含税 {{ fmtN(invoiceSummary.purchaseAmount) }}</span></div>
          </div>
          <div class="stat-card mini">
            <div class="stat-label">应纳增值税（销项 − 进项）</div>
            <div class="stat-value sm" :style="{color: Number(invoiceSummary.payable) >= 0 ? 'var(--app-color-danger)' : 'var(--app-color-primary)'}">{{ fmtN(invoiceSummary.payable) }}</div>
            <div class="stat-sub"><span class="dim">负数表示留抵（进项大于销项）</span></div>
          </div>
        </div>
        <!-- 区间合计汇总卡 -->
        <div class="stat-grid" style="grid-template-columns:repeat(3,1fr)">
          <div class="stat-card mini" v-for="c in taxCards" :key="c.label">
            <div class="stat-label">{{ c.label }} · 已税金额 <span :style="{ color: toneColor(c.tone), fontSize: '16px', fontWeight: '600' }">{{ fmtN(c.taxed) }}</span></div>
            <div class="stat-sub" style="margin-top:8px">
              <span class="dim">税额 {{ fmtN(c.tax) }}</span>
              <span class="dim">未税 {{ fmtN(c.untaxed) }}</span>
              <span class="dim">已税占比 <b style="color:var(--el-text-color-primary)">{{ fmt(c.rate) }}%</b></span>
            </div>
          </div>
        </div>
        <div class="toolbar" style="margin:8px 0;display:flex;align-items:center;justify-content:space-between">
          <span style="font-size:12px;color:var(--el-text-color-secondary)">
            说明：已税=单据打开「含税」开关（总金额中已按税率拆出税额）；未税=未打开开关，不做税额拆分。历史单据未开含税的全部计入未税。
          </span>
          <el-button type="primary" plain size="small" @click="exportTax">导出 Excel</el-button>
        </div>
        <div id="taxChart" class="chart"/>
        <!-- 按月明细（多级表头） -->
        <el-table :data="tax.rows" border stripe size="small" style="margin-top:12px">
          <el-table-column prop="month" label="月份" width="95" align="center" fixed="left"/>
          <el-table-column label="销售（销项）" align="center">
            <el-table-column label="已税" width="105" align="right"><template #default="{row}">{{ fmt(row.saleTaxed) }}</template></el-table-column>
            <el-table-column label="销项税额" width="105" align="right"><template #default="{row}">{{ fmt(row.saleTaxedTax) }}</template></el-table-column>
            <el-table-column label="未税" width="105" align="right"><template #default="{row}">{{ fmt(row.saleUntaxed) }}</template></el-table-column>
            <el-table-column label="已税占比" width="85" align="right"><template #default="{row}">{{ fmt(row.saleRate) }}%</template></el-table-column>
          </el-table-column>
          <el-table-column label="采购（进项）" align="center">
            <el-table-column label="已税" width="105" align="right"><template #default="{row}">{{ fmt(row.purchaseTaxed) }}</template></el-table-column>
            <el-table-column label="进项税额" width="105" align="right"><template #default="{row}">{{ fmt(row.purchaseTaxedTax) }}</template></el-table-column>
            <el-table-column label="未税" width="105" align="right"><template #default="{row}">{{ fmt(row.purchaseUntaxed) }}</template></el-table-column>
            <el-table-column label="已税占比" width="85" align="right"><template #default="{row}">{{ fmt(row.purchaseRate) }}%</template></el-table-column>
          </el-table-column>
          <el-table-column label="委外加工" align="center">
            <el-table-column label="已税" width="105" align="right"><template #default="{row}">{{ fmt(row.outsourceTaxed) }}</template></el-table-column>
            <el-table-column label="税额" width="105" align="right"><template #default="{row}">{{ fmt(row.outsourceTaxedTax) }}</template></el-table-column>
            <el-table-column label="未税" width="105" align="right"><template #default="{row}">{{ fmt(row.outsourceUntaxed) }}</template></el-table-column>
            <el-table-column label="已税占比" width="85" align="right"><template #default="{row}">{{ fmt(row.outsourceRate) }}%</template></el-table-column>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column;gap:12px}
.qf{display:flex;flex-wrap:wrap}
.pg{margin-top:16px;display:flex;justify-content:flex-end}
.stat-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-value{font-size:22px;font-weight:600}
.stat-label{font-size:12px;color:var(--el-text-color-secondary);margin-top:4px}
.chart{width:100%;height:320px}
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
