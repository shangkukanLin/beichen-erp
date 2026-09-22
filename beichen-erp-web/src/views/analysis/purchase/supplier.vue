<script setup lang="ts">
import { ref, computed, onMounted, onActivated, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SourceBillDetailRoute } from '@/api/enums'
import * as echarts from 'echarts'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'

/**
 * 单供货商分析（进货分析页点供货商名进入，2026-09-22 用户要求）：
 * 供货商档案 + 区间 KPI（采购金额/采购退货/净采购额/采购单数/单均）+ 月度趋势 + 采购产品 TOP + 该供货商单据明细。
 *
 * <p>口径与「进货分析」**完全同源**（后端同一套 mapper 查询 + 同一区间解析/防滥用截断）⇒
 * 本页净采购额/单数与列表行、以及列表页上方 KPI 可逐项对账。</p>
 * <p>供应商为「（未指定供货商）」(id=0) 或已被删除时，档案退化为单据里带的名称，页面照常可用。</p>
 */
const route = useRoute(); const router = useRouter()
const supplierId = computed(() => Number(route.params.id) || 0)
// 区间由列表页带过来（与列表同窗口）；直接访问/刷新时退回「本年」（单供货商通常要看长期合作曲线）
const preset = ref(String(route.query.preset || 'year'))
const range = ref<[string, string] | null>(
  route.query.start && route.query.end ? [String(route.query.start), String(route.query.end)] : null)
const loading = ref(false)
const data = ref<any>({ start: '', end: '', supplier: {}, summary: {}, months: [], monthAmounts: [], monthReturns: [], byProduct: [], details: [] })
let trendChart: echarts.ECharts | null = null

const su = computed(() => data.value.supplier || {})
const sum = computed(() => data.value.summary || {})
const byProduct = computed<any[]>(() => data.value.byProduct || [])
const details = computed<any[]>(() => data.value.details || [])
const chartEmpty = computed(() => {
  const a = (data.value.monthAmounts || []).map(Number)
  const r = (data.value.monthReturns || []).map(Number)
  return a.concat(r).every((v: number) => v === 0)
})

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtN(v?: any) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtInt(v?: any) { return String(Math.round(Number(v) || 0)) }
function fmtQty(v?: any) {
  if (v == null) return '0'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
/** 产品占比：该产品净额 / 本供货商净采购额（可能为负，仅作参考） */
function productShare(row: any) {
  const total = Number(sum.value.netPurchase) || 0
  if (!total) return '—'
  return ((Number(row.amount) || 0) / total * 100).toFixed(1) + '%'
}

const kpiCards = computed(() => {
  const s = sum.value
  return [
    { label: '采购金额', value: fmtN(s.purchaseAmount), tone: 'orange', unit: '' },
    { label: '采购退货', value: fmtN(s.returnAmount), tone: 'red', unit: '' },
    { label: '净采购额', value: fmtN(s.netPurchase), tone: 'blue', unit: '' },
    { label: '采购单数', value: fmtInt(s.orderCount), tone: 'green', unit: '单' },
    { label: '单均金额', value: fmtN(s.avgAmount), tone: 'gray', unit: '' },
  ]
})
function toneColor(tone?: string) {
  if (tone === 'green') return 'var(--app-color-success)'
  if (tone === 'red') return 'var(--app-color-danger)'
  if (tone === 'orange') return 'var(--app-color-warning)'
  if (tone === 'blue') return 'var(--app-color-primary)'
  return ''
}

async function loadData() {
  if (preset.value === 'custom' && !(range.value?.length === 2)) return
  loading.value = true
  try {
    const params: any = { preset: preset.value }
    if (preset.value === 'custom' && range.value?.length === 2) {
      params.start = range.value[0]; params.end = range.value[1]
    }
    data.value = await request.get<any, any>(`/finance/analysis/purchase-supplier/${supplierId.value}`, { params })
      || { start: '', end: '', supplier: {}, summary: {}, months: [], monthAmounts: [], monthReturns: [], byProduct: [], details: [] }
  } catch {
    data.value = { start: '', end: '', supplier: {}, summary: {}, months: [], monthAmounts: [], monthReturns: [], byProduct: [], details: [] }
  } finally { loading.value = false }
  await nextTick()
  renderChart()
}

/** 月度趋势：采购金额/采购退货（柱）+ 净采购额（线） */
function renderChart() {
  const el = document.getElementById('supTrendChart')
  if (!el) return
  const months: string[] = data.value.months || []
  const amounts = (data.value.monthAmounts || []).map(Number)
  const returns = (data.value.monthReturns || []).map(Number)
  trendChart = trendChart || echarts.init(el)
  if (chartEmpty.value) { trendChart.clear(); return }
  // ⚠️ setOption 第二参 true（notMerge）：切区间会改变点数，否则残留旧数据
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['采购金额', '采购退货', '净采购额'], top: 0 },
    grid: { left: 70, right: 20, top: 44, bottom: 30 },
    xAxis: { type: 'category', data: months },
    yAxis: { type: 'value' },
    series: [
      { name: '采购金额', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#e6a23c' }, data: amounts },
      { name: '采购退货', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#f56c6c' }, data: returns },
      {
        name: '净采购额', type: 'line', smooth: true, itemStyle: { color: '#5470c6' },
        data: months.map((_m: string, i: number) => Number((amounts[i] - returns[i]).toFixed(2))),
      },
    ],
  }, true)
  trendChart.resize()
}

/** 单据明细行 → 对应单据详情页（采购单 / 采购退货单），与列表页同一集中映射 */
function goBill(row: any) {
  if (!row?.billId) return
  const base = SourceBillDetailRoute[row.billType] || SourceBillDetailRoute.PURCHASE_ORDER
  router.push(`${base}/${row.billId}`)
}
/** 返回进货分析（不带 query ⇒ 回到该页默认区间；列表页的区间不会因此被改） */
function goBack() { router.push('/analysis/purchase') }

onMounted(() => { loadData() })
onActivated(() => { loadData() })
// keep-alive 反复进出会累积 ECharts 实例 ⇒ 卸载时释放
onUnmounted(() => { trendChart?.dispose(); trendChart = null })
</script>
<template>
  <div class="p" v-loading="loading">
    <div class="toolbar">
      <el-button :icon="'Back'" size="small" @click="goBack">返回进货分析</el-button>
      <span class="title">{{ su.name || '供货商' }} · 采购分析</span>
      <span class="dim">
        <template v-if="su.code">编号 {{ su.code }} ｜ </template>
        <template v-if="su.supplySku">供货SKU {{ su.supplySku }} ｜ </template>
        <template v-if="su.contact">联系人 {{ su.contact }} ｜ </template>
        <template v-if="su.phone">电话 {{ su.phone }} ｜ </template>
        <template v-if="su.address">{{ su.address }} ｜ </template>
        {{ data.start }} ~ {{ data.end }}
      </span>
    </div>

    <StatRange v-model:preset="preset" v-model:range="range" @change="loadData"/>
    <div class="dim" v-if="data.truncated" style="margin-bottom:6px">
      区间过长已截断到 {{ data.maxDays }} 天（以结束日为准）
    </div>

    <!-- ① KPI（与列表行同源，可逐项对账） -->
    <div class="stat-grid">
      <div class="stat-card kpi" v-for="k in kpiCards" :key="k.label">
        <div class="stat-label">{{ k.label }}</div>
        <div class="stat-value" :style="{ color: toneColor(k.tone) }">
          {{ k.value }}<span v-if="k.unit" class="unit">{{ k.unit }}</span>
        </div>
      </div>
    </div>

    <!-- ② 月度趋势 -->
    <el-card shadow="never" class="section-card">
      <template #header><div class="card-head"><span class="card-title">月度采购趋势</span></div></template>
      <div class="dim" v-if="chartEmpty" style="margin-bottom:6px">该区间暂无数据</div>
      <div id="supTrendChart" class="chart"/>
    </el-card>

    <!-- ③ 采购产品 TOP（净额 = 采购 − 采购退货） -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">采购产品</span>
          <span class="dim">净额口径（采购 − 采购退货），与两个饼图同源</span>
        </div>
      </template>
      <el-table :data="byProduct" border stripe size="small" max-height="320" empty-text="该区间无采购明细">
        <el-table-column prop="productName" label="产品" min-width="180" show-overflow-tooltip/>
        <el-table-column label="数量" width="120" align="right">
          <template #default="{ row }">{{ fmtQty(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="净额" width="150" align="right">
          <template #default="{ row }"><b>{{ fmtN(row.amount) }}</b></template>
        </el-table-column>
        <el-table-column label="占净采购额" width="120" align="right">
          <template #default="{ row }">{{ productShare(row) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ④ 该供货商的单据明细（点单号进单据详情） -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">单据明细</span>
          <span class="dim">共 {{ details.length }} 单；点单号进入单据详情</span>
        </div>
      </template>
      <el-table :data="details" border stripe size="small" max-height="420" empty-text="该区间无单据">
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.billType === 'PURCHASE_RETURN' ? 'danger' : 'warning'" size="small">
              {{ row.billType === 'PURCHASE_RETURN' ? '采购退货' : '采购单' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单号" min-width="170">
          <template #default="{ row }"><span class="bill-link" @click="goBill(row)">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="date" label="日期" width="110"/>
        <el-table-column label="金额" width="150" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.billType === 'PURCHASE_RETURN' ? 'var(--app-color-danger)' : 'var(--app-color-warning)' }">
              {{ row.billType === 'PURCHASE_RETURN' ? '-' : '+' }}{{ fmtN(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip/>
      </el-table>
    </el-card>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.toolbar{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin-bottom:10px}
.title{font-weight:600;font-size:var(--app-font-md)}
.dim{font-size:var(--app-font-xs);color:var(--el-text-color-secondary)}
.stat-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin:10px 0 4px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-value{font-size:var(--app-font-num);font-weight:600}
.stat-label{font-size:var(--app-font-xs);color:var(--el-text-color-secondary);margin-top:4px}
.stat-card.kpi{border-top:3px solid var(--el-color-primary-light-5)}
.unit{font-size:var(--app-font-xs);margin-left:2px;font-weight:400;color:var(--el-text-color-secondary)}
.chart{width:100%;height:220px}
.section-card{margin-top:12px}
.card-head{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px}
.card-title{font-weight:600}
.bill-link{color:var(--el-color-primary);cursor:pointer}
.bill-link:hover{text-decoration:underline}
@media (max-width: 1000px){ .stat-grid{grid-template-columns:repeat(2,1fr)} }
</style>
