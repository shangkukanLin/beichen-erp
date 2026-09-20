<script setup lang="ts">
import { ref, computed, onMounted, onActivated, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'
import { SALE_ANALYSIS_FORMULA } from '@/utils/kpiFormula'

/**
 * 销售分析（经营分析）：6 项区间指标 + 产品排行 + 仓库分布。
 * 口径（2026-09-15 全站统一**建单日**归期）：销售额=已审核销售单（create_time）；退货额=已审核销售退单（create_time）；净销售额=销售额-退货额。
 * 2026-09-15 用户要求：去掉原「销售额/退货额」柱状图（后端 `dates/amounts/returns` 字段仍保留、前端不再使用），
 * 换成 6 项指标：产品销量/产品利润、客户销量/客户利润、客户退货率/客户换货率（每项带悬停问号公式）。
 * 排行行点「明细」下钻到该维度的销售单列表（/analysis/sale/detail）。
 */
const router = useRouter()
const preset = ref('month')
const range = ref<[string, string] | null>(null)
const loading = ref(false)
const data = ref<any>({ start: '', end: '', summary: {}, metrics: {}, byProduct: [], byWarehouse: [] })

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtN(v?: any) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function pct(part: number, total: number) {
  if (!total) return '0.0'
  return (Math.round((part / total) * 1000) / 10).toFixed(1)
}
/** 百分比展示（后端已 ×100、保留 2 位；2026-09-15 由 1 位改 2 位，避免 0.06% 显示成 0.1%） */
function fmtPct(v?: any) { return (Number(v) || 0).toFixed(2) + '%' }
/** 数量展示：千分位、最多 2 位小数（去尾零，件数不显示 .00） */
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
    data.value = await request.get<any, any>('/sale/analysis', { params })
      || { start: '', end: '', summary: {}, dates: [], amounts: [], returns: [], byProduct: [], byWarehouse: [] }
  } catch {
    data.value = { start: '', end: '', summary: {}, dates: [], amounts: [], returns: [], byProduct: [], byWarehouse: [] }
  } finally { loading.value = false }
  // 饼图需等容器渲染完再初始化，否则会按 0 宽高绘制
  await nextTick()
  setTimeout(renderPies, 60)
}

// 预设切换/日期变更的"清空 + 触发"逻辑已收口到 StatRange 组件，页面只需 loadData

/**
 * 6 个饼图定义（2026-09-15 用户要求：6 项指标做成饼图，替换原数字卡/柱状图）
 * 产品销量/产品利润（按产品分片）· 客户销量/客户利润（按客户分片）
 * · 客户退货率/客户换货率（按**各客户退货额/换货额占总退货额/总换货额的比例**分片，标题显示总比率）
 * 每张图标题带合计值，标签后有问号可悬停看计算公式；只显示前 8 名，其余合并为「其它」。
 */
/**
 * 「客户退货率 / 客户换货率」卡片各自的**口径开关**（2026-09-15 用户要求，两张卡各一个独立 switch）：
 * `amount` = 按**金额**（退货额 / 换货额 ÷ 销售额）；`qty` = 按**产品件数**（退货件数 / 换出件数 ÷ 销售件数）。
 * 两套数据由后端**一次性返回**，切换只换数据源、**不重发请求**。
 */
const retMetric = ref<'amount' | 'qty'>('amount')
const exchMetric = ref<'amount' | 'qty'>('amount')

const pieDefs = computed(() => {
  const d = data.value
  const m = d.metrics || {}
  const byProduct = d.byProduct || []
  const byCustomer = d.byCustomer || []
  const name = (r: any) => r.productName || r.sku || '（未知）'
  return [
    {
      id: 'pieProductQty', label: '产品销量', total: fmtQty(m.productQuantity) + ' 件', unit: '件',
      formula: SALE_ANALYSIS_FORMULA.productQuantity,
      items: byProduct.map((r: any) => ({ name: name(r), value: r.quantity }))
    },
    {
      id: 'pieProductProfit', label: '产品利润', total: fmtN(m.productProfit) + ' 元', unit: '元',
      formula: SALE_ANALYSIS_FORMULA.productProfit,
      items: byProduct.map((r: any) => ({ name: name(r), value: r.profit }))
    },
    {
      id: 'pieCustomerQty', label: '客户销量', total: fmtQty(m.productQuantity) + ' 件', unit: '件',
      formula: SALE_ANALYSIS_FORMULA.customerCount,
      items: byCustomer.map((r: any) => ({ name: r.customerName || '（未知）', value: r.quantity }))
    },
    {
      id: 'pieCustomerProfit', label: '客户利润', total: fmtN(m.productProfit) + ' 元', unit: '元',
      formula: SALE_ANALYSIS_FORMULA.topCustomerProfit,
      items: byCustomer.map((r: any) => ({ name: r.customerName || '（未知）', value: r.profit }))
    },
    {
      // 客户退货率：分片 = 各客户退货额（金额口径）/ 各客户退货件数（件数口径），标题 = 对应的总退货率
      id: 'pieReturn', label: '客户退货率',
      total: fmtPct(retMetric.value === 'qty' ? m.returnRateQty : m.returnRate),
      unit: retMetric.value === 'qty' ? '件' : '%',
      formula: retMetric.value === 'qty' ? SALE_ANALYSIS_FORMULA.returnRateQty : SALE_ANALYSIS_FORMULA.returnRate,
      items: (retMetric.value === 'qty' ? (d.returnByCustomerQty || []) : (d.returnByCustomer || []))
        .map((r: any) => ({ name: r.name || '（未知）', value: r.value }))
    },
    {
      // 客户换货率：分片 = 各客户换货额（金额口径）/ 各客户换出件数（件数口径），标题 = 对应的总换货率
      id: 'pieExchange', label: '客户换货率',
      total: fmtPct(exchMetric.value === 'qty' ? m.exchangeRateQty : m.exchangeRate),
      unit: exchMetric.value === 'qty' ? '件' : '%',
      formula: exchMetric.value === 'qty' ? SALE_ANALYSIS_FORMULA.exchangeRateQty : SALE_ANALYSIS_FORMULA.exchangeRate,
      items: (exchMetric.value === 'qty' ? (d.exchangeByCustomerQty || []) : (d.exchangeByCustomer || []))
        .map((r: any) => ({ name: r.name || '（未知）', value: r.value }))
    },
  ]
})
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
const pieRefs: Record<string, echarts.ECharts> = {}
/** 无数据占位（每个饼图各自判断） */
const pieEmpty = ref<Record<string, boolean>>({})

/** 渲染 6 个饼图；切区间重绘，setOption 第二参 true（notMerge）避免残留分片 */
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
      tooltip: {
        trigger: 'item',
        formatter: (p: any) =>
          `${p.marker}${p.name}<br/>${Number(p.value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}${
            def.unit === '%' ? '' : def.unit
          }（${p.percent}%）`,
      },
      legend: { show: false },
      series: [{
        type: 'pie', radius: ['42%', '68%'], center: ['50%', '52%'], avoidLabelOverlap: true,
        label: { formatter: '{b} {d}%', fontSize: 11 },
        labelLine: { length: 8, length2: 8 },
        data: items,
      }],
    }, true)
    chart.resize()
  })
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
/** 口径开关切换 → 只重绘对应饼图（数据已在本地，不重新请求接口） */
watch([retMetric, exchMetric], async () => {
  await nextTick()
  setTimeout(renderPies, 30)
})
// 2026-09-20（F7-188）：keep-alive 反复进出会累积 ECharts 实例（本页按科目有多张饼图，存在 pieRefs 里）
// ⇒ 组件真正卸载时逐个释放并清空键，避免下次进入拿到已失效实例。
onUnmounted(() => {
  Object.values(pieRefs).forEach((c) => { try { c?.dispose() } catch { /* 已释放 */ } })
  Object.keys(pieRefs).forEach((k) => { delete pieRefs[k] })
})
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
    <!-- 6 个饼图（2026-09-15 用户要求：6 项指标做成饼图，替换原柱状图/数字卡）；
         标题带合计值 + 问号悬停看计算公式；只显示前 8 名，其余合并「其它」 -->
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
            <!-- 客户退货率 / 客户换货率：各自一个口径开关（2026-09-15 用户要求：按金额 / 按产品件数） -->
            <el-switch v-if="p.id === 'pieReturn'" v-model="retMetric" active-value="qty" inactive-value="amount"
                       size="small" active-text="件数" inactive-text="金额" />
            <el-switch v-if="p.id === 'pieExchange'" v-model="exchMetric" active-value="qty" inactive-value="amount"
                       size="small" active-text="件数" inactive-text="金额" />
            <span class="pie-total">{{ p.total }}</span>
          </span>
        </div>
        <!-- 定高容器：空态用 v-show 叠加在图容器上（原来空态是两个 200px 块并存 → 卡片高出一倍）。
             注意不能用 v-if 让图容器消失 —— echarts 实例会挂在被删除的 DOM 上，下次有数据画不出来 -->
        <div class="pie-body">
          <div class="pie-empty" v-show="pieEmpty[p.id]">该区间暂无数据</div>
          <div :id="p.id" class="pie-chart"/>
        </div>
      </div>
    </div>
    <el-row :gutter="12" style="margin-top:12px">
      <!-- 产品排行 -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>产品销售额排行（点「明细」看该产品的销售单）</template>
          <el-table :data="data.byProduct" border stripe max-height="360" show-summary :summary-method="productSummary">
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
          <el-table :data="data.byWarehouse" border stripe max-height="360">
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
.stat-label{font-size:var(--app-font-xs);color:var(--el-text-color-secondary);margin-top:4px}
.stat-value.sm{font-size:var(--app-font-num-sm);font-weight:600}
/* 6 个饼图：2 列 × 3 行（2026-09-15 替换原柱状图/数字卡） */
.pie-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-bottom:12px}
.pie-card{padding:12px 16px}
/* min-height 固定表头高度：后两张卡多了「金额/件数」switch，不固定会比前 4 张高几像素（2026-09-15 卡片等高要求） */
.pie-head{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;min-height:26px}
.pie-title{font-size:var(--app-font-base);font-weight:600}
.pie-total{font-size:var(--app-font-base);color:var(--el-text-color-secondary)}
.pie-head-right{display:inline-flex;align-items:center;gap:10px;flex-wrap:wrap}
/* 2026-09-15 统一卡片高度：无论有无数据都是定高 200px 的 .pie-body
   —— 原先空态是「.pie-empty(200px) + 空 .pie-chart(200px)」两块并存，卡片比有数据时高约一倍 */
.pie-body{position:relative;height:200px;margin-top:4px}
.pie-chart{width:100%;height:100%}
.pie-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:var(--app-font-xs);color:var(--el-text-color-secondary)}
@media (max-width: 900px){ .pie-grid{grid-template-columns:1fr} }
</style>
