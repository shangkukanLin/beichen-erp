<script setup lang="ts">
import { ref, computed, onMounted, onActivated, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'
import StagnantPanel from '@/components/StagnantPanel.vue'
import { PRODUCT_ANALYSIS_FORMULA } from '@/utils/kpiFormula'
// 2026-09-20：饼图外侧标签与 tooltip 共用同一数值口径（与销售分析/进货分析同做法）
import { pieOutsideLabel, pieTooltip } from '@/utils/pieLabel'

/**
 * 产品分析（经营分析，2026-10-02 用户要求新增；位于「客户分析」之后）。
 *
 * <p><b>口径（用户 2026-10-02 拍板「冲减净额」）：</b>净销量 = 销售明细数量 − 退货明细数量；
 * 净销售额 = 销售明细金额 − 退货明细金额；毛利 = 净销售额 − 净成本（成本口径 B：Σ数量×产品移动加权成本价，
 * 退货侧同样冲回）。销售与退货**各按自己的建单日**归期，两端都取**明细口径** ⇒ 排行表各行相加 == KPI 合计。
 * 换货单不计入（换货不是新增销售）。以后端 `ProductAnalysisServiceImpl` 类注释为准。</p>
 *
 * <p><b>与「销售分析」的区别：</b>销售分析的产品排行**不冲减退货**、只看卖得好不好；本页一律给"净"的口径
 * （净销量/净销售额/毛利），并可按产品下钻到**销售单 + 退货单**明细。</p>
 */
const router = useRouter()
const preset = ref('month')
const range = ref<[string, string] | null>(null)
const loading = ref(false)
const EMPTY = {
  start: '', end: '', dates: [] as string[], saleAmounts: [] as number[], returnAmounts: [] as number[],
  netAmounts: [] as number[], summary: {} as Record<string, any>, byProduct: [] as any[],
}
const data = ref<any>({ ...EMPTY })

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtN(v?: any) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
/** 百分比展示（后端已 ×100、保留 2 位） */
function fmtPct(v?: any) { return (Number(v) || 0).toFixed(1) + '%' }
/** 数量展示：千分位、最多 2 位小数（去尾零） */
function fmtQty(v?: any) {
  if (v == null) return '0'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
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
    data.value = await request.get<any, any>('/product/analysis', { params }) || { ...EMPTY }
  } catch {
    data.value = { ...EMPTY }
  } finally { loading.value = false }
  // 图表需等容器渲染完再初始化，否则会按 0 宽高绘制
  await nextTick()
  setTimeout(renderCharts, 60)
}

// ===== 饼图（6 张）=====
/**
 * 「产品退货率 / 产品换货率」两张卡各自的**口径开关**（2026-10-02 用户要求，与销售分析同做法）：
 * `amount` = 按**金额**（退货额/换货额 ÷ 销售额）；`qty` = 按**件数**（退货件数/换出件数 ÷ **销售件数**）。
 * 两套数据由后端一次性返回 ⇒ 切换只换数据源、**不重发请求**。
 */
const retMetric = ref<'amount' | 'qty'>('amount')
const exchMetric = ref<'amount' | 'qty'>('amount')
/**
 * 分片一律取自后端 `byProduct`（**唯一数据源** ⇒ 饼图合计与排行表合计必然自洽，前端不重算金额）。
 * `positiveOnly`：净销售额/净销量/毛利在"退货 > 销售"时会是负数，饼图画不了负分片
 * （与进货分析的「负数净额不画分片」同一处理）⇒ 这类产品的负值不参与分片。
 */
const pieDefs = computed(() => {
  const rows = data.value.byProduct || []
  const m = summary.value
  const name = (r: any) => r.productName || r.sku || '（未知）'
  const items = (key: string) => rows.map((r: any) => ({ name: name(r), value: r[key] }))
  return [
    {
      id: 'pieSaleAmount', label: '产品销售额', total: fmtN(m.saleAmount) + ' 元', unit: '元',
      formula: PRODUCT_ANALYSIS_FORMULA.pieSaleAmount, items: items('saleAmount'), positiveOnly: true,
    },
    {
      id: 'pieNetAmount', label: '产品净销售额', total: fmtN(m.netAmount) + ' 元', unit: '元',
      formula: PRODUCT_ANALYSIS_FORMULA.pieNetAmount, items: items('netAmount'), positiveOnly: true,
    },
    {
      id: 'pieNetQty', label: '产品净销量', total: fmtQty(m.netQty) + ' 件', unit: '件',
      formula: PRODUCT_ANALYSIS_FORMULA.pieNetQty, items: items('netQty'), positiveOnly: true,
    },
    {
      id: 'pieProfit', label: '产品毛利', total: fmtN(m.profit) + ' 元', unit: '元',
      formula: PRODUCT_ANALYSIS_FORMULA.pieProfit, items: items('profit'), positiveOnly: true,
    },
    {
      // 产品退货率：分片 = 各产品退货额（金额口径）/ 各产品退货件数（件数口径），标题 = 对应的**总**退货率
      id: 'pieReturnRate', label: '产品退货率',
      total: fmtPct(retMetric.value === 'qty' ? m.returnRateQty : m.returnRate),
      unit: retMetric.value === 'qty' ? '件' : '元',
      formula: retMetric.value === 'qty' ? PRODUCT_ANALYSIS_FORMULA.pieReturnRateQty : PRODUCT_ANALYSIS_FORMULA.pieReturnRate,
      items: items(retMetric.value === 'qty' ? 'returnQty' : 'returnAmount'), positiveOnly: true,
    },
    {
      // 产品换货率：分片 = 各产品换货额（明细 out_amount）/ 各产品换出件数（out_quantity），标题 = 总换货率
      id: 'pieExchangeRate', label: '产品换货率',
      total: fmtPct(exchMetric.value === 'qty' ? m.exchangeRateQty : m.exchangeRate),
      unit: exchMetric.value === 'qty' ? '件' : '元',
      formula: exchMetric.value === 'qty' ? PRODUCT_ANALYSIS_FORMULA.pieExchangeRateQty : PRODUCT_ANALYSIS_FORMULA.pieExchangeRate,
      items: items(exchMetric.value === 'qty' ? 'exchQty' : 'exchAmount'), positiveOnly: true,
    },
  ]
})

/** 饼图分片：前 8 名 + 「其它」（超过 8 片看不清） */
function top8(list: { name: string; value: any }[], positiveOnly = false) {
  let arr = (list || [])
    .map((x) => ({ name: x.name || '（未知）', value: Number(x.value) || 0 }))
    .filter((x) => (positiveOnly ? x.value > 0 : x.value !== 0))
    .sort((a, b) => b.value - a.value)
  if (arr.length <= 8) return arr
  const head = arr.slice(0, 8)
  const rest = arr.slice(8).reduce((s, x) => s + x.value, 0)
  head.push({ name: '其它', value: Number(rest.toFixed(2)) })
  return head
}

const pieRefs: Record<string, echarts.ECharts> = {}
const pieEmpty = ref<Record<string, boolean>>({})
let trendChart: echarts.ECharts | null = null

/** 渲染 4 张饼图；切区间重绘，setOption 第二参 true（notMerge）避免残留分片 */
function renderPies() {
  pieDefs.value.forEach((def) => {
    const el = document.getElementById(def.id)
    if (!el) return
    const items = top8(def.items, def.positiveOnly)
    pieEmpty.value[def.id] = items.length === 0
    pieRefs[def.id] = pieRefs[def.id] || echarts.init(el)
    const chart = pieRefs[def.id]
    if (items.length === 0) { chart.clear(); return }
    chart.setOption({
      // tooltip 与外侧标签共用 pieTooltip/pieOutsideLabel ⇒ 悬停与直接看到的是同一个数
      tooltip: { trigger: 'item', formatter: pieTooltip(def.unit) },
      legend: { show: false },
      series: [{
        // 半径与引出线**必须**与 .pie-grid 的两列宽度匹配（过大外侧两行标签会被画布裁切，
        // 由 verify-pie-outside-label.ps1 的 clipped=0 守着）
        type: 'pie', radius: ['28%', '50%'], center: ['50%', '50%'], avoidLabelOverlap: true,
        label: { show: true, position: 'outside', formatter: pieOutsideLabel(def.unit), fontSize: 11, lineHeight: 14 },
        labelLine: { show: true, length: 8, length2: 10 },
        data: items,
      }],
    }, true)
    chart.resize()
  })
}

/** 区间销售额 / 退货额 / 净销售额 的按天折线（缺日由后端补 0 ⇒ x 轴连续） */
function renderTrend() {
  const el = document.getElementById('pvTrend')
  if (!el) return
  const d = data.value
  trendChart = trendChart || echarts.init(el)
  trendChart.setOption({
    tooltip: { trigger: 'axis', valueFormatter: (v: any) => fmtN(v) },
    legend: { data: ['销售额', '退货额', '净销售额'], top: 0 },
    grid: { left: 8, right: 18, top: 36, bottom: 4, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: d.dates || [] },
    yAxis: { type: 'value' },
    series: [
      { name: '销售额', type: 'line', smooth: true, showSymbol: false, data: d.saleAmounts || [] },
      { name: '退货额', type: 'line', smooth: true, showSymbol: false, data: d.returnAmounts || [] },
      { name: '净销售额', type: 'line', smooth: true, showSymbol: false, data: d.netAmounts || [] },
    ],
  }, true)
  trendChart.resize()
}

/** 区间内完全没有销售与退货 ⇒ 趋势图显示空态（不画全 0 的三条线） */
const trendEmpty = computed(() =>
  (data.value.saleAmounts || []).every((v: any) => !Number(v))
  && (data.value.returnAmounts || []).every((v: any) => !Number(v)))

function renderCharts() { renderTrend(); renderPies() }

/** 下钻：带产品与区间参数跳「该产品的销售/退货单明细」页 */
function drill(row: any) {
  const query: any = { preset: preset.value, productId: row.productId, productName: row.productName }
  if (preset.value === 'custom' && range.value?.length === 2) {
    query.start = range.value[0]; query.end = range.value[1]
  }
  router.push({ path: '/analysis/product/detail', query })
}

/** 排行合计行：净销量(2) / 销售额(3) / 退货额(4) / 净销售额(5) / 毛利(6) */
function productSummary({ columns }: any) {
  const rows = data.value.byProduct || []
  const sum = (k: string) => rows.reduce((s: number, r: any) => s + Number(r[k] || 0), 0)
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 2) return fmtQty(sum('netQty'))
    if (i === 3) return fmt(sum('saleAmount'))
    if (i === 4) return fmt(sum('returnAmount'))
    if (i === 5) return fmt(sum('netAmount'))
    if (i === 6) return fmt(sum('profit'))
    return ''
  })
}

// ===== 滞销与呆滞块（共用组件）=====
/**
 * 面板自身不在 mounted 里拉数据 ⇒ 由本页驱动（见组件说明：否则父页面与组件各拉一次 = 首屏白拉两遍）。
 * ⚠️ 本页是 keep-alive 页：首次进入 mounted → activated **各触发一次**（上方 loadData 因此本来就拉两遍），
 * 这里用 firstLoad 记账，保证滞销面板首屏只拉一次。
 */
const stagnantRef = ref<any>(null)
let stagnantFirstLoad = true
/** 面板里的产品行点击 → 产品主数据详情（本页没有"仓库分布"上下文，点行与点产品名都进产品详情） */
function stagnantDetail(row: any) { if (row?.productId) router.push(`/product/detail/${row.productId}`) }

onMounted(() => { loadData() })
onActivated(() => { loadData() })
onMounted(() => { stagnantRef.value?.reload() })
onActivated(() => {
  if (stagnantFirstLoad) { stagnantFirstLoad = false; return }
  stagnantRef.value?.reload()
})
/** 口径开关切换 → 只重绘对应饼图（两套数据已在本地，不重新请求接口；与销售分析同做法） */
watch([retMetric, exchMetric], async () => {
  await nextTick()
  setTimeout(renderPies, 30)
})
// keep-alive 反复进出会累积 ECharts 实例 ⇒ 组件真正卸载时逐个释放（与销售分析同做法）
onUnmounted(() => {
  Object.values(pieRefs).forEach((c) => { try { c?.dispose() } catch { /* 已释放 */ } })
  Object.keys(pieRefs).forEach((k) => { delete pieRefs[k] })
  try { trendChart?.dispose() } catch { /* 已释放 */ }
  trendChart = null
})
</script>
<template>
  <div class="p" v-loading="loading">
    <div class="toolbar">
      <StatRange v-model:preset="preset" v-model:range="range" @change="loadData"/>
      <span class="hint">
        净额口径：净销量 / 净销售额 / 毛利 = 销售 − 退货（退货按退货单建单日归期；换货不计入）
      </span>
    </div>
    <!-- KPI（8 张 = 两整行）：销售 − 退货 = 净，逐项给出，便于与排行表逐行核对 -->
    <div class="stat-grid">
      <div class="stat-card mini">
        <div class="stat-label">
          销售额
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.saleAmount }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm" style="color:var(--app-color-success)">{{ fmtN(summary.saleAmount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">
          退货额
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.returnAmount }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm" style="color:var(--app-color-danger)">{{ fmtN(summary.returnAmount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">
          换货额
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.exchangeAmount }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm" style="color:var(--app-color-warning)">{{ fmtN(summary.exchangeAmount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">
          退货率（退货 ÷ 销售）
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.returnAmount }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm">{{ fmtPct(summary.returnRate) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">
          换货率（换货 ÷ 销售）
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.exchangeRate }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm">{{ fmtPct(summary.exchangeRate) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">
          净销售额（销售 − 退货）
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.netAmount }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm" style="font-weight:600">{{ fmtN(summary.netAmount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">
          净销量（件）
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.netQty }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm">{{ fmtQty(summary.netQty) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">
          动销产品数
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.productCount }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm">{{ summary.productCount || 0 }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">
          毛利
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.profit }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm" :style="{ color: Number(summary.profit) < 0 ? 'var(--app-color-danger)' : '' }">
          {{ fmtN(summary.profit) }}
        </div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">
          毛利率
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ PRODUCT_ANALYSIS_FORMULA.profitRate }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value sm">{{ fmtPct(summary.profitRate) }}</div>
      </div>
    </div>
    <!-- 趋势：区间内 销售额 / 退货额 / 净销售额 按天 -->
    <el-card shadow="never" class="trend-card">
      <template #header>销售额 / 退货额 / 净销售额（按天）</template>
      <!-- 空态用 v-show 叠加：图容器不能被 v-if 删掉（echarts 实例会挂在被删的 DOM 上，下次画不出来） -->
      <div class="trend-body">
        <div class="pie-empty" v-show="trendEmpty">该区间暂无数据</div>
        <div id="pvTrend" class="trend-chart"/>
      </div>
    </el-card>
    <!-- 4 张产品饼图：分片一律取后端 byProduct（唯一数据源）⇒ 合计与排行表一致 -->
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
            <!-- 退货率 / 换货率：各自一个口径开关（2026-10-02 用户要求：按金额 / 按产品件数），与销售分析同款 -->
            <el-switch v-if="p.id === 'pieReturnRate'" v-model="retMetric" active-value="qty" inactive-value="amount"
                       size="small" active-text="件数" inactive-text="金额" />
            <el-switch v-if="p.id === 'pieExchangeRate'" v-model="exchMetric" active-value="qty" inactive-value="amount"
                       size="small" active-text="件数" inactive-text="金额" />
            <span class="pie-total">{{ p.total }}</span>
          </span>
        </div>
        <div class="pie-body">
          <div class="pie-empty" v-show="pieEmpty[p.id]">该区间暂无数据</div>
          <div :id="p.id" class="pie-chart"/>
        </div>
      </div>
    </div>
    <!-- 产品净额排行：一行一产品，列宽合计控制在内容区内（不出现横向滚动条） -->
    <el-card shadow="never">
      <template #header>产品净额排行（销售 − 退货；点「明细」看该产品的销售单 / 退货单）</template>
      <el-table :data="data.byProduct" border stripe max-height="420" show-summary :summary-method="productSummary">
        <!-- SKU 是标识列（不许被省略号截断，scan-col-truncation.ps1 的规则）：原 width="84" 实测
             10 位编码（SKU-000003）需 110px ⇒ 2 行被截断（2026-10-02 跑守卫时发现，非本次新增缺陷）。
             改 min-width 让它在宽屏再多分一点余量（该表其余列是固定宽、由「产品」列吸收富余空间）。 -->
        <el-table-column prop="sku" label="SKU" min-width="112" show-overflow-tooltip/>
        <el-table-column prop="productName" label="产品" min-width="118" show-overflow-tooltip/>
        <el-table-column label="净销量" width="76" align="right">
          <template #default="{row}">{{ fmtQty(row.netQty) }}</template>
        </el-table-column>
        <el-table-column label="销售额" width="94" align="right">
          <template #default="{row}">{{ fmt(row.saleAmount) }}</template>
        </el-table-column>
        <el-table-column label="退货额" width="94" align="right">
          <template #default="{row}">
            <span :style="{ color: Number(row.returnAmount) > 0 ? 'var(--app-color-danger)' : '' }">{{ fmt(row.returnAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="净销售额" width="98" align="right">
          <template #default="{row}">
            <span style="font-weight:600">{{ fmt(row.netAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="毛利" width="90" align="right">
          <template #default="{row}">
            <span :style="{ color: Number(row.profit) < 0 ? 'var(--app-color-danger)' : '' }">{{ fmt(row.profit) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="毛利率" width="62" align="right">
          <template #default="{row}">{{ fmtPct(row.profitRate) }}</template>
        </el-table-column>
        <el-table-column label="占比" width="54" align="right">
          <template #default="{row}">{{ fmtPct(row.share) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="58" align="center">
          <template #default="{row}">
            <el-button link type="primary" @click="drill(row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!--
      滞销与呆滞（2026-10-02 用户要求：仓管页有了，经营页也要一份）—— 直接复用 components/StagnantPanel.vue，
      与「成品库存详情」页是同一份口径与同一张表（列宽预算也共用，两个页面内容区宽度相同）。
      ⚠️ 它与本页上方的 StatRange **无关**：滞销是"截至今天"的**时点**概念（有库存 × 多久没卖/没来货），
         不随分析区间变化；区间只影响上方的销售额/退货率/毛利那一套。
      判定天数可调（默认 15 天）：2026-10-09 起指「**距最后销售日**」的天数；**从未有过销售记录**的产品按
         **最近来货日**（采购入库 / 委外加工回货）起算，两者都没有的一律算滞销。
         原独立的「统计窗口（默认 90 天）」与「期间销量 / 周转天数」两列已按用户要求整体取消，不再有第二个天数。
    -->
    <StagnantPanel ref="stagnantRef" title="滞销与呆滞" @row-click="stagnantDetail" @product-click="stagnantDetail" />
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.toolbar{display:flex;align-items:center;gap:12px;margin-bottom:12px;flex-wrap:wrap}
.hint{font-size:var(--app-font-xs);color:var(--app-text-secondary)}
/* KPI 卡 10 张（销售/退货/换货/退货率/换货率/净额/净量/动销/毛利/毛利率）：
   用 auto-fit 让每行按可用宽度摆 4~5 张 —— 目标窗口（1200px，内容区约 894px）下 5 张一行、两整行，
   窄屏自动降列，避免卡片被压窄后数字折行。 */
.stat-grid{display:grid;grid-template-columns:repeat(auto-fit, minmax(164px, 1fr));gap:12px;margin-bottom:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-label{font-size:var(--app-font-xs);color:var(--el-text-color-secondary);margin-top:4px}
.stat-value.sm{font-size:var(--app-font-num-sm);font-weight:600}
/* 趋势折线：定高容器 + 空态叠加（与饼图同一套做法，避免空态把卡片撑高） */
.trend-card{margin-bottom:12px}
.trend-body{position:relative;height:260px}
.trend-chart{width:100%;height:100%}
/* 4 张饼图：两列（3 行 × 2 列 → 本页 2 行 × 2 列）。半径与引出线必须与列宽匹配，
   否则外侧两行标签（名称 / 数值（占比%））会被画布裁切 —— 由 verify-pie-outside-label.ps1 的 clipped=0 守着。 */
.pie-grid{display:grid;grid-template-columns:repeat(2, minmax(0, 1fr));gap:12px;margin-bottom:12px}
@media (max-width: 1200px){ .pie-grid{grid-template-columns:1fr} }
.pie-card{padding:12px 16px}
.pie-head{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;min-height:26px}
.pie-title{font-size:var(--app-font-base);font-weight:600}
.pie-total{font-size:var(--app-font-base);color:var(--el-text-color-secondary)}
.pie-body{position:relative;height:300px;margin-top:4px}
.pie-chart{width:100%;height:100%}
.pie-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:var(--app-font-xs);color:var(--el-text-color-secondary)}
</style>
