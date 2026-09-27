<script setup lang="ts">
import { ref, computed, onMounted, onActivated, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'
import { PURCHASE_ANALYSIS_FORMULA } from '@/utils/kpiFormula'
// 2026-09-20：饼图外侧标签与 tooltip 共用同一数值口径（原先 label 只有「名称+占比」，数值只能悬停看）
import { pieOutsideLabel, pieTooltip } from '@/utils/pieLabel'

/**
 * 进货分析（经营分析，2026-09-15 新增）：**所选区间**的采购 KPI + 趋势图 + **两个饼图** + **供货商分析列表**
 * （2026-09-22 用户要求：点供货商名进入「单供货商分析」下钻页）。
 * 数据源 `/finance/analysis/purchase-analysis`（与首页「经营分析」/「经营概览」的采购口径同源）。
 * 口径（2026-09-15 全站统一**建单日**归期）：采购金额 = 已审核采购单（create_time）；采购退货 = 已审核采购退货（create_time）；
 *      净采购额 = 采购金额 − 采购退货；采购单数 = 已审核采购单笔数。
 * 2026-09-15（第二轮）：新增两个饼图（**采购成品** / **委外加工**，分开两张、各按产品分片），
 *      放在明细/供货商卡上方；每张卡各有「金额 / 件数」switch。
 * 2026-09-21（仅文案）：卡标题 直接采购成品→采购成品、委外加工成品入库→委外加工；
 *      明细卡标题 采购单据明细（下钻）→采购单据明细。口径/取数/接口零改动。
 * 2026-09-22（用户要求）：**第 ④ 块由「采购单据明细」表改为「供货商分析」列表**
 *      （供货商 / 采购金额 / 采购退货 / 净采购额 / 采购单数 / 占比，抬头保留"合计=净采购额"对账），
 *      点供货商名下钻到「单供货商分析」；后端同一接口新增 `suppliers[]`（与 KPI 同源 ⇒ 各行净额合计 = 净采购额）。
 * 注：采购入库属资产、不计入损益，本页只做采购视角统计，不参与利润/成本。
 */
const router = useRouter()
const loading = ref(false)
const preset = ref('month')
const range = ref<[string, string] | null>(null)
const data = ref<any>({ range: {}, kpi: {}, series: {}, suppliers: [] })
const chartEmpty = ref(false)
let chart: echarts.ECharts | null = null

function fmtN(v?: any) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtInt(v?: any) { return String(Math.round(Number(v) || 0)) }
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
  data.value.range?.preset === 'custom' ? '所选区间' : (RANGE_LABELS[data.value.range?.preset] || ''))
/** 实际生效的区间（显示在选择器右侧便于核对） */
const rangeText = computed(() => {
  const r = data.value.range || {}
  return r.start ? `${r.start} ~ ${r.end}` : ''
})

const kpiCards = computed(() => {
  const k = data.value.kpi || {}
  const p = rangePrefix.value
  return [
    { label: p + '采购金额', value: fmtN(k.purchaseAmount), tone: 'orange', unit: '' },
    { label: p + '采购退货', value: fmtN(k.purchaseReturnAmount), tone: 'red', unit: '' },
    { label: p + '净采购额', value: fmtN(k.netPurchase), tone: 'blue', unit: '' },
    { label: p + '采购单数', value: fmtInt(k.purchaseOrderCount), tone: 'green', unit: '单' },
  ]
})

/**
 * 供货商分析列表（2026-09-22 用户要求：**替代原「采购单据明细」表**）：
 * 按供货商聚合所选区间的采购/退货/净额/单数，点供货商名进入「单供货商分析」。
 * 后端与上方 KPI 同源 ⇒ 各行净额合计必然等于 KPI 净采购额（列表抬头把这条对账关系写出来）。
 */
const suppliers = computed<any[]>(() => data.value.suppliers || [])
const page = ref(1)
const pageSize = 20
const pagedSuppliers = computed(() => suppliers.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const supplierTotal = computed(() => suppliers.value.length)
/** 供货商合计（与上方净采购额对账用） */
const supplierSum = computed(() => {
  let a = 0, r = 0, c = 0
  suppliers.value.forEach((x: any) => {
    a += Number(x.purchaseAmount) || 0
    r += Number(x.returnAmount) || 0
    c += Number(x.orderCount) || 0
  })
  return { purchase: a, ret: r, net: a - r, count: c }
})
/** 采购金额占比（该供货商采购额 / 全区间采购额） */
function shareOf(row: any) {
  const total = supplierSum.value.purchase
  if (!total) return '0.0%'
  return ((Number(row.purchaseAmount) || 0) / total * 100).toFixed(1) + '%'
}

/** 趋势图：采购金额/采购退货（柱）+ 净采购额（线），随统计区间联动（粒度由后端决定） */
function renderChart() {
  const el = document.getElementById('purchaseChart')
  if (!el) return
  const s = data.value.series || {}
  const pts: any[] = s.points || []
  const byMonth = s.granularity === 'month'
  const num = (p: any, k: string) => Number(p[k]) || 0
  chartEmpty.value = pts.length === 0
    || pts.every((p: any) => num(p, 'purchaseAmount') === 0 && num(p, 'purchaseReturnAmount') === 0)
  chart = chart || echarts.init(el)
  if (chartEmpty.value) { chart.clear(); return }
  // ⚠️ setOption 第二参必须 true（notMerge）：切区间/粒度会改变点数，否则残留旧数据
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['采购金额', '采购退货', '净采购额'], top: 0 },
    grid: { left: 70, right: 20, top: 44, bottom: 30 },
    xAxis: { type: 'category', data: pts.map((p: any) => (byMonth ? p.label : String(p.label).slice(5))) },
    yAxis: { type: 'value' },
    series: [
      { name: '采购金额', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#e6a23c' }, data: pts.map((p: any) => num(p, 'purchaseAmount')) },
      { name: '采购退货', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#f56c6c' }, data: pts.map((p: any) => num(p, 'purchaseReturnAmount')) },
      { name: '净采购额', type: 'line', smooth: true, itemStyle: { color: '#5470c6' }, data: pts.map((p: any) => num(p, 'netPurchase')) },
    ],
  }, true)
  chart.resize()
}

async function loadData() {
  if (preset.value === 'custom' && !(range.value?.length === 2)) return
  loading.value = true
  try {
    const params: any = { preset: preset.value }
    if (preset.value === 'custom' && range.value?.length === 2) {
      params.start = range.value[0]; params.end = range.value[1]
    }
    data.value = await request.get<any, any>('/finance/analysis/purchase-analysis', { params })
      || { range: {}, kpi: {}, series: {}, suppliers: [] }
  } catch {
    data.value = { range: {}, kpi: {}, series: {}, suppliers: [] }
  } finally { loading.value = false }
  page.value = 1
  await nextTick()
  renderChart()
  setTimeout(renderPies, 60)
}

/**
 * 两个饼图（2026-09-15 用户要求：采购成品 / 委外加工**分开两张**，放在「采购单据明细」上方）。
 * 两张都按**产品**分片；每张卡各有「金额 / 件数」switch —— 两套数据后端**一次返回**，切换不重发请求。
 * 口径：直接采购 = Σ采购明细 − Σ采购退货明细（净额）；委外 = Σ(收货数量 × 加工单价)，退不良负数自动冲减。
 */
const dpMetric = ref<'amount' | 'qty'>('amount')
const osMetric = ref<'amount' | 'qty'>('amount')

function fmtQty(v?: any) {
  if (v == null) return '0'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
/** 饼图分片：前 8 名 + 「其它」（超过 8 片看不清） */
function top8(list: { name: string; value: any }[]) {
  const arr = (list || [])
    .map((x) => ({ name: x.name || '（未知）', value: Number(x.value) || 0 }))
    .filter((x) => x.value !== 0)
    .sort((a, b) => b.value - a.value)
  if (arr.length <= 8) return arr
  const head = arr.slice(0, 8)
  const rest = arr.slice(8).reduce((s, x) => s + x.value, 0)
  head.push({ name: '其它', value: Number(rest.toFixed(2)) })
  return head
}
const pieDefs = computed(() => {
  const d = data.value
  const t = d.pieTotals || {}
  const dpQty = dpMetric.value === 'qty'
  const osQty = osMetric.value === 'qty'
  return [
    {
      id: 'pieDirectPurchase', label: '采购成品', isQty: dpQty,
      total: dpQty ? fmtQty(t.directPurchaseQuantity) + ' 件' : fmtN(t.directPurchaseAmount) + ' 元',
      formula: dpQty ? PURCHASE_ANALYSIS_FORMULA.directPurchaseQty : PURCHASE_ANALYSIS_FORMULA.directPurchase,
      items: dpQty ? (d.directPurchaseByProductQty || []) : (d.directPurchaseByProduct || [])
    },
    {
      id: 'pieOutsourceIn', label: '委外加工', isQty: osQty,
      total: osQty ? fmtQty(t.outsourceInQuantity) + ' 件' : fmtN(t.outsourceInAmount) + ' 元',
      formula: osQty ? PURCHASE_ANALYSIS_FORMULA.outsourceInQty : PURCHASE_ANALYSIS_FORMULA.outsourceIn,
      items: osQty ? (d.outsourceInByProductQty || []) : (d.outsourceInByProduct || [])
    },
  ]
})
const pieRefs: Record<string, echarts.ECharts> = {}
const pieEmpty = ref<Record<string, boolean>>({})
/** 渲染 2 个饼图；切区间/切口径重绘，setOption 第二参 true（notMerge）避免残留分片 */
function renderPies() {
  pieDefs.value.forEach((def) => {
    const el = document.getElementById(def.id)
    if (!el) return
    const items = top8(def.items)
    pieEmpty.value[def.id] = items.length === 0
    pieRefs[def.id] = pieRefs[def.id] || echarts.init(el)
    const chart = pieRefs[def.id]
    if (items.length === 0) { chart.clear(); return }
    chart.setOption({
      // 2026-09-20：tooltip 与外侧标签共用 pieTooltip/pieOutsideLabel ⇒ 悬停与直接看到的是同一个数
      tooltip: { trigger: 'item', formatter: pieTooltip(def.isQty ? '件' : '元') },
      legend: { show: false },
      series: [{
        // 2026-09-22：饼半径收小（62%→50%）+ 引出线缩短（12/14→8/10）—— .pie-grid 改成左右两列后
        // 单卡宽度约减半（约 460px），不收小则外侧两行标签会被画布左右裁切（2026-09-20 正是因此改成 1 列）
        type: 'pie', radius: ['28%', '50%'], center: ['50%', '50%'], avoidLabelOverlap: true,
        // 外侧标签（两行）：名称 / 数值 单位（占比%）—— 不再需要悬停；单位随「金额/件数」开关整体重绘而切换
        label: { show: true, position: 'outside', formatter: pieOutsideLabel(def.isQty ? '件' : '元'), fontSize: 11, lineHeight: 14 },
        labelLine: { show: true, length: 8, length2: 10 },
        data: items,
      }],
    }, true)
    chart.resize()
  })
}
/** 口径开关切换 → 只重绘饼图（数据已在本地，不重新请求接口） */
watch([dpMetric, osMetric], async () => { await nextTick(); setTimeout(renderPies, 30) })

/**
 * 供货商行 → 单供货商分析页（2026-09-22 用户要求）。
 * **带上当前统计区间**（preset/start/end）⇒ 下钻页与列表页看的是同一段数据（与客户分析下钻同做法）。
 */
function goSupplier(row: any) {
  if (row?.supplierId == null) return
  const query: Record<string, string> = {}
  if (preset.value === 'custom' && range.value?.length === 2) {
    query.start = range.value[0]; query.end = range.value[1]
  } else {
    query.preset = preset.value
  }
  router.push({ path: `/analysis/purchase/supplier/${row.supplierId}`, query })
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
// 2026-09-20（F7-188）：keep-alive 反复进出会累积 ECharts 实例（本页有趋势图 chart + 多张饼图 pieRefs）
// ⇒ 卸载时统一释放并置空。
onUnmounted(() => {
  chart?.dispose(); chart = null
  Object.values(pieRefs).forEach((c) => { try { c?.dispose() } catch { /* 已释放 */ } })
  Object.keys(pieRefs).forEach((k) => { delete pieRefs[k] })
})
</script>
<template>
  <div class="page-list" v-loading="loading">
    <!-- 统计区间：统一组件 StatRange（KPI / 趋势 / 明细 均跟随所选区间） -->
    <div class="toolbar">
      <StatRange v-model:preset="preset" v-model:range="range" @change="loadData"/>
      <span class="range-text" v-if="rangeText">{{ rangeText }}</span>
    </div>
    <!-- ① 主指标（所选区间） -->
    <div class="stat-grid">
      <div class="stat-card kpi" v-for="k in kpiCards" :key="k.label">
        <div class="stat-label">{{ k.label }}</div>
        <div class="stat-value" :style="{ color: toneColor(k.tone) }">
          {{ k.value }}<span v-if="k.unit" class="unit">{{ k.unit }}</span>
        </div>
      </div>
    </div>
    <!-- ② 趋势（随区间联动；无数据给占位） -->
    <div class="dim" v-if="chartEmpty" style="margin-bottom:6px">该区间暂无数据</div>
    <div id="purchaseChart" class="chart"/>
    <!-- ③ 两个饼图（2026-09-15 用户要求：采购成品 / 委外加工 **分开两张**，放在明细卡上方）；
         每卡各有「金额 / 件数」switch，标题带合计值，问号悬停看公式，前 8 名 + 其它 -->
    <div class="pie-grid">
      <div class="stat-card pie-card" v-for="p in pieDefs" :key="p.id">
        <div class="pie-head">
          <span class="pie-title">
            {{ p.label }}
            <el-tooltip placement="top" effect="dark" :show-after="100">
              <template #content><div class="kpi-formula">{{ p.formula }}</div></template>
              <el-icon class="kpi-help"><QuestionFilled /></el-icon>
            </el-tooltip>
          </span>
          <span class="pie-head-right">
            <el-switch v-if="p.id === 'pieDirectPurchase'" v-model="dpMetric" active-value="qty" inactive-value="amount"
                       size="small" active-text="件数" inactive-text="金额" />
            <el-switch v-if="p.id === 'pieOutsourceIn'" v-model="osMetric" active-value="qty" inactive-value="amount"
                       size="small" active-text="件数" inactive-text="金额" />
            <span class="pie-total">{{ p.total }}</span>
          </span>
        </div>
        <!-- 定高容器：空态用 v-show 叠加在图容器上（不能用 v-if 删容器，否则 echarts 实例挂在已删 DOM 上） -->
        <div class="pie-body">
          <div class="pie-empty" v-show="pieEmpty[p.id]">该区间暂无数据</div>
          <div :id="p.id" class="pie-chart"/>
        </div>
      </div>
    </div>
    <!-- ④ 供货商分析（2026-09-22 用户要求：把原「采购单据明细」表换成供货商视角，点供货商名进入单个供货商分析） -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">供货商分析</span>
          <span class="dim">
            合计：采购 {{ fmtN(supplierSum.purchase) }} ｜ 退货 {{ fmtN(supplierSum.ret) }} ｜
            净采购 <b>{{ fmtN(supplierSum.net) }}</b>（应与上方「净采购额」一致）｜
            共 {{ supplierSum.count }} 单 ｜ 点供货商名进入该供货商的分析
          </span>
        </div>
      </template>
      <el-table :data="pagedSuppliers" border stripe empty-text="该区间暂无供货商">
        <el-table-column label="供货商" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><span class="bill-link" @click="goSupplier(row)">{{ row.supplier }}</span></template>
        </el-table-column>
        <el-table-column label="采购金额" width="150" align="right">
          <template #default="{ row }"><span style="color:var(--app-color-warning)">{{ fmtN(row.purchaseAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="采购退货" width="130" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.returnAmount) ? 'var(--app-color-danger)' : 'var(--el-text-color-secondary)' }">
              {{ fmtN(row.returnAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="净采购额" width="150" align="right">
          <template #default="{ row }"><b>{{ fmtN(row.netPurchase) }}</b></template>
        </el-table-column>
        <el-table-column prop="orderCount" label="采购单数" width="100" align="center"/>
        <el-table-column label="占比" width="90" align="right">
          <template #default="{ row }">{{ shareOf(row) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination" v-if="supplierTotal > pageSize">
        <el-pagination background layout="total, prev, pager, next, jumper" :total="supplierTotal"
          :page-size="pageSize" v-model:current-page="page"/>
      </div>
    </el-card>
  </div>
</template>
<style scoped>
/* 根容器已统一到全局（styles/page.css 的 .page-list） */
.toolbar{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:12px}
.range-text{font-size:var(--app-font-xs);color:var(--el-text-color-secondary)}
.stat-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-value{font-size:var(--app-font-num);font-weight:600}
.stat-label{font-size:var(--app-font-xs);color:var(--el-text-color-secondary);margin-top:4px}
.stat-card.kpi{border-top:3px solid var(--el-color-primary-light-5)}
.unit{font-size:var(--app-font-xs);margin-left:2px;font-weight:400;color:var(--el-text-color-secondary)}
.chart{width:100%;height:200px;margin:8px 0 12px}
/* 两个饼图（2026-09-15）：与销售分析同款「卡片等高 + 空态叠加」实现
   —— 空态与有数据态都是定高 .pie-body，卡片高度不会变
   2026-09-20 曾由「2 列」改「整行 1 列」—— 外侧要直接显示「数值 + 占比」，半行卡（约 420px）放不下会被裁切。
   2026-09-22（用户要求）：改回**左右两列**；同时把饼半径与引出线收小（见 renderPies），
   否则外侧两行标签在约 460px 的卡里仍会被画布裁切 ✗（与 profile.vue 同做法）。
   ⚠️ verify-pie-outside-label.ps1 用像素级 clipped 守着这一点。 */
.pie-grid{display:grid;grid-template-columns:repeat(2, minmax(0, 1fr));gap:12px;margin:4px 0 12px}
@media (max-width: 1200px){ .pie-grid{grid-template-columns:1fr} }
.pie-card{padding:12px 16px}
.pie-head{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;min-height:26px}
.pie-head-right{display:inline-flex;align-items:center;gap:10px;flex-wrap:wrap}
.pie-title{font-size:var(--app-font-base);font-weight:600}
.pie-total{font-size:var(--app-font-base);color:var(--el-text-color-secondary)}
/* 2026-09-20：200px → 300px —— 外侧标签是两行（名称 / 数值（占比%）），200px 高放不下最多 9 个分片的标签 */
.pie-body{position:relative;height:300px;margin-top:4px}
.pie-chart{width:100%;height:100%}
.pie-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:var(--app-font-xs);color:var(--el-text-color-secondary)}
.section-card{margin-top:4px}
.card-head{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px}
.card-title{font-weight:600}
.dim{font-size:var(--app-font-xs);color:var(--el-text-color-secondary)}
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
/* 窄屏：4 卡降为 2 列 */
@media (max-width: 1000px){ .stat-grid{grid-template-columns:repeat(2,1fr)} }
</style>
