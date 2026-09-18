<script setup lang="ts">
import { localDate } from '@/utils/date'
import { ref, computed, onMounted, onActivated, nextTick } from 'vue'
import * as echarts from 'echarts'
import * as XLSX from 'xlsx'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'

/** 税务分析（经营分析）：发票汇总 + 已税/未税趋势 + 按月明细 + 导出 */
const loading = ref(false)
// 统计区间（2026-09-15 统一组件）：税务天然按月统计，故只提供 本月/本季/本年/自定义，默认「本年」
const preset = ref('year')
const range = ref<[string, string] | null>(null)
const tax = ref<any>({ months: [], rows: [], summary: {} })
let taxChart: echarts.ECharts | null = null

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
function today() { return localDate() }

const invoiceSummary = computed(() => {
  const i = tax.value.invoice || {}
  return { saleTax: i.saleTax, saleAmount: i.saleAmount, saleCount: i.saleCount || 0, purchaseTax: i.purchaseTax, purchaseAmount: i.purchaseAmount, purchaseCount: i.purchaseCount || 0, payable: i.payable }
})
const taxCards = computed(() => {
  const s = tax.value.summary || {}
  return [
    { label: '销售（销项）', taxed: s.saleTaxed, tax: s.saleTaxedTax, untaxed: s.saleUntaxed, rate: s.saleRate, tone: 'green' },
    { label: '采购（进项）', taxed: s.purchaseTaxed, tax: s.purchaseTaxedTax, untaxed: s.purchaseUntaxed, rate: s.purchaseRate, tone: 'blue' },
    { label: '委外加工', taxed: s.outsourceTaxed, tax: s.outsourceTaxedTax, untaxed: s.outsourceUntaxed, rate: s.outsourceRate, tone: 'orange' },
  ]
})

function renderTaxChart() {
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

async function loadData() {
  if (preset.value === 'custom' && !(range.value?.length === 2)) return
  loading.value = true
  try {
    const params: any = { preset: preset.value }
    if (preset.value === 'custom' && range.value?.length === 2) {
      params.start = range.value[0]; params.end = range.value[1]
    }
    tax.value = await request.get<any, any>('/finance/analysis/tax', { params }) || { months: [], rows: [], summary: {} }
  } catch { tax.value = { months: [], rows: [], summary: {} } } finally { loading.value = false }
  await nextTick()
  renderTaxChart()
}

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
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(aoa), '税务分析')
  XLSX.writeFile(wb, `税务分析_${today()}.xlsx`)
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
</script>
<template>
  <div class="p" v-loading="loading">
    <!-- 统计区间：统一组件 StatRange（2026-09-15 全站收口；税务按月，故只给 本月/本季/本年/自定义） -->
    <div style="margin-bottom:12px">
      <StatRange v-model:preset="preset" v-model:range="range"
        :presets="['month','quarter','year','custom']" @change="loadData"/>
    </div>
    <!-- 发票汇总 -->
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
    <!-- 区间合计 -->
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
    <div class="toolbar">
      <span class="tip">说明：已税=单据打开「含税」开关（总金额中已按税率拆出税额）；未税=未打开开关，不做税额拆分。历史单据未开含税的全部计入未税。销售/采购退货已按其原单的含税标记与税率冲减销项/进项。</span>
      <el-button type="primary" plain size="small" @click="exportTax">导出 Excel</el-button>
    </div>
    <div id="taxChart" class="chart"/>
    <el-table :data="tax.rows" border stripe style="margin-top:12px">
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
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.stat-grid{display:grid;gap:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-label{font-size:var(--app-font-xs);color:var(--el-text-color-secondary);margin-top:4px}
.stat-value.sm{font-size:var(--app-font-num-sm);font-weight:600}
.stat-sub{margin-top:6px;font-size:var(--app-font-xs);display:flex;gap:8px;align-items:center}
.stat-sub .dim{color:var(--el-text-color-secondary)}
.toolbar{margin:8px 0;display:flex;align-items:center;justify-content:space-between}
.tip{font-size:var(--app-font-xs);color:var(--el-text-color-secondary)}
.chart{width:100%;height:320px}
</style>
