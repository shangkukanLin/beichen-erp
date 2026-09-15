<script setup lang="ts">
import { ref, computed, onMounted, onActivated, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'

/**
 * 单客户分析（经营分析 → 客户分析 → 点客户名进入）：
 * 客户档案 + 经营 KPI + 月度趋势 + 品牌分布 + 产品 TOP10 + 品质结构 + 三张明细表。
 * 利润口径与「客户分析」一致：净销售额 − 销售成本（成本按产品移动加权成本价估算，退货冲回）。
 */
const route = useRoute(); const router = useRouter()
const customerId = computed(() => Number(route.params.id) || 0)
const preset = ref('year')
const range = ref<[string, string] | null>(null)
const loading = ref(false)
const data = ref<any>({ start: '', end: '', customer: {}, summary: {}, months: [], monthAmounts: [], monthProfits: [], byProduct: [], byBrand: [], byQuality: [], returns: [] })

let trendChart: echarts.ECharts | null = null
let brandChart: echarts.ECharts | null = null
let productChart: echarts.ECharts | null = null
let qualityChart: echarts.ECharts | null = null

const cust = computed(() => data.value.customer || {})
const summary = computed(() => data.value.summary || {})

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtN(v?: any) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadData() {
  if (preset.value === 'custom' && !(range.value?.length === 2)) return
  loading.value = true
  try {
    const params: any = { preset: preset.value }
    if (preset.value === 'custom' && range.value?.length === 2) {
      params.start = range.value[0]; params.end = range.value[1]
    }
    data.value = await request.get<any, any>(`/customer/analysis/profile/${customerId.value}`, { params })
      || { start: '', end: '', customer: {}, summary: {}, months: [], monthAmounts: [], monthProfits: [], byProduct: [], byBrand: [], byQuality: [], returns: [] }
  } catch {
    data.value = { start: '', end: '', customer: {}, summary: {}, months: [], monthAmounts: [], monthProfits: [], byProduct: [], byBrand: [], byQuality: [], returns: [] }
  } finally { loading.value = false }
  await nextTick()
  renderCharts()
}
// 预设切换/日期变更的"清空 + 触发"逻辑已收口到 StatRange 组件，页面只需 loadData

function renderCharts() {
  // ① 月度趋势：销售额柱 + 利润线（双轴）
  const el1 = document.getElementById('custTrendChart')
  if (el1) {
    trendChart = trendChart || echarts.init(el1)
    const amounts = (data.value.monthAmounts || []).map(Number)
    const profits = (data.value.monthProfits || []).map(Number)
    const hasData = amounts.concat(profits).some((v: number) => v !== 0)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['销售额', '利润'], top: 0 },
      grid: { left: 70, right: 60, top: 44, bottom: 30 },
      xAxis: { type: 'category', data: data.value.months || [] },
      yAxis: [
        { type: 'value', max: hasData ? undefined : 100, name: '销售额' },
        { type: 'value', max: hasData ? undefined : 100, name: '利润' },
      ],
      series: [
        { name: '销售额', type: 'bar', barMaxWidth: 32, itemStyle: { color: '#91cc75' }, data: amounts },
        { name: '利润', type: 'line', smooth: true, yAxisIndex: 1, itemStyle: { color: '#5470c6' }, data: profits },
      ],
    })
    trendChart.resize()
  }
  // ② 品牌分布饼图
  const el2 = document.getElementById('custBrandChart')
  if (el2) {
    brandChart = brandChart || echarts.init(el2)
    const rows = data.value.byBrand || []
    brandChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { type: 'scroll', orient: 'vertical', right: 0, top: 10 },
      series: [{
        name: '品牌分布', type: 'pie', radius: ['35%', '62%'], center: ['38%', '52%'],
        avoidLabelOverlap: true, label: { show: false },
        data: rows.map((r: any) => ({ name: r.brandName || '未分类', value: Number(r.amount) })),
      }],
    })
    brandChart.resize()
  }
  // ③ 产品 TOP10 横向条形（销售额 + 利润 分组）
  const el3 = document.getElementById('custProductChart')
  if (el3) {
    productChart = productChart || echarts.init(el3)
    const top = (data.value.byProduct || []).slice(0, 10).slice().reverse()
    productChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['销售额', '利润'], top: 0 },
      grid: { left: 110, right: 60, top: 40, bottom: 30 },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: top.map((r: any) => r.productName || r.sku) },
      series: [
        { name: '销售额', type: 'bar', barMaxWidth: 12, itemStyle: { color: '#91cc75' }, data: top.map((r: any) => Number(r.amount)) },
        { name: '利润', type: 'bar', barMaxWidth: 12, itemStyle: { color: '#5470c6' }, data: top.map((r: any) => Number(r.profit)) },
      ],
    })
    productChart.resize()
  }
  // ④ 品质结构饼图
  const el4 = document.getElementById('custQualityChart')
  if (el4) {
    qualityChart = qualityChart || echarts.init(el4)
    const rows = data.value.byQuality || []
    qualityChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      series: [{
        name: '品质结构', type: 'pie', radius: '58%', center: ['50%', '46%'],
        data: rows.map((r: any) => ({ name: r.qualityType || '未分类', value: Number(r.amount) })),
      }],
    })
    qualityChart.resize()
  }
}

/** 产品表合计行 */
function productSummaries({ columns }: any) {
  const rows = data.value.byProduct || []
  const sum = (k: string) => fmt(rows.reduce((s: number, r: any) => s + Number(r[k] || 0), 0))
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 5) return sum('quantity')
    if (i === 6) return sum('amount')
    if (i === 7) return sum('cost')
    if (i === 8) return sum('profit')
    return ''
  })
}
/** 退货明细合计行 */
function returnSummaries({ columns }: any) {
  const rows = data.value.returns || []
  const sum = (k: string) => fmt(rows.reduce((s: number, r: any) => s + Number(r[k] || 0), 0))
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 5) return sum('quantity')
    if (i === 6) return sum('amount')
    return ''
  })
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
</script>
<template>
  <div class="p" v-loading="loading">
    <div class="toolbar">
      <el-button :icon="'Back'" size="small" @click="router.push('/analysis/customer')">返回</el-button>
      <span class="title">{{ cust.customerName || '客户' }} 分析</span>
      <span class="dim">{{ cust.customerCode }}｜{{ data.start }} ~ {{ data.end }}</span>
      <div class="spacer"/>
      <!-- 统计区间：统一组件（2026-09-15 全站收口；本页按用户要求补齐为完整 7 项） -->
      <StatRange v-model:preset="preset" v-model:range="range" @change="loadData"/>
    </div>

    <!-- ① 客户档案 -->
    <el-descriptions :column="6" border size="small" class="desc">
      <el-descriptions-item label="客户编码">{{ cust.customerCode || '—' }}</el-descriptions-item>
      <el-descriptions-item label="客户名称">{{ cust.customerName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="联系人">{{ cust.contact || '—' }}</el-descriptions-item>
      <el-descriptions-item label="电话">{{ cust.phone || '—' }}</el-descriptions-item>
      <el-descriptions-item label="账期(天)">{{ cust.creditPeriod ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="应收余额">
        <span :style="{color: Number(cust.unpaid)>0?'var(--app-color-warning)':'inherit'}">{{ fmtN(cust.unpaid) }}</span>
      </el-descriptions-item>
    </el-descriptions>

    <!-- ② 经营 KPI -->
    <div class="stat-grid">
      <div class="stat-card mini">
        <div class="stat-label">销售额</div>
        <div class="stat-value sm" style="color:var(--app-color-success)">{{ fmtN(summary.amount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">退货额（退货率 {{ fmt(summary.returnRate) }}%）</div>
        <div class="stat-value sm" style="color:var(--app-color-danger)">{{ fmtN(summary.returnAmount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">净销售额</div>
        <div class="stat-value sm">{{ fmtN(summary.netAmount) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">成本</div>
        <div class="stat-value sm" style="color:var(--app-color-warning)">{{ fmtN(summary.cost) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">利润（利润率 {{ fmt(summary.profitRate) }}%）</div>
        <div class="stat-value sm" :style="{color: Number(summary.profit)>=0?'var(--app-color-success)':'var(--app-color-danger)', fontWeight:'600'}">{{ fmtN(summary.profit) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">订单数 / 折损收款</div>
        <div class="stat-value sm">{{ summary.orderCount || 0 }} 单 / {{ fmtN(summary.lossAmount) }}</div>
      </div>
    </div>

    <!-- ③ 图表 -->
    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never"><template #header>月度趋势（销售额 / 利润）</template><div id="custTrendChart" class="chart"/></el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never"><template #header>品牌分布（按销售额）</template><div id="custBrandChart" class="chart"/></el-card>
      </el-col>
    </el-row>
    <el-row :gutter="12" style="margin-top:12px">
      <el-col :span="14">
        <el-card shadow="never"><template #header>产品 TOP10（销售额 / 利润）</template><div id="custProductChart" class="chart"/></el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never"><template #header>品质结构（按销售额）</template><div id="custQualityChart" class="chart"/></el-card>
      </el-col>
    </el-row>

    <!-- ④ 按产品明细 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>拿货明细（按产品）</template>
      <el-table :data="data.byProduct" border stripe size="small" show-summary :summary-method="productSummaries" max-height="380">
        <el-table-column prop="sku" label="SKU" min-width="100" show-overflow-tooltip/>
        <el-table-column prop="productName" label="产品" min-width="110" show-overflow-tooltip/>
        <el-table-column prop="brandName" label="品牌" min-width="90" show-overflow-tooltip/>
        <el-table-column label="规格/型号" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.spec || row.model || '—' }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" min-width="56" align="center"/>
        <el-table-column label="数量" min-width="80" align="right"><template #default="{row}">{{ fmt(row.quantity) }}</template></el-table-column>
        <el-table-column label="销售额" min-width="90" align="right"><template #default="{row}"><span style="color:var(--app-color-success)">{{ fmt(row.amount) }}</span></template></el-table-column>
        <el-table-column label="成本" min-width="88" align="right"><template #default="{row}"><span style="color:var(--app-color-warning)">{{ fmt(row.cost) }}</span></template></el-table-column>
        <el-table-column label="利润" min-width="90" align="right">
          <template #default="{row}"><span :style="{color: Number(row.profit)>=0?'var(--app-color-success)':'var(--app-color-danger)', fontWeight:'600'}">{{ fmt(row.profit) }}</span></template>
        </el-table-column>
        <el-table-column label="利润率" min-width="74" align="right"><template #default="{row}">{{ fmt(row.profitRate) }}%</template></el-table-column>
        <el-table-column label="退货量" min-width="76" align="right"><template #default="{row}"><span :style="{color: Number(row.returnQty)>0?'var(--app-color-danger)':'inherit'}">{{ fmt(row.returnQty) }}</span></template></el-table-column>
        <el-table-column label="退货率" min-width="74" align="right"><template #default="{row}">{{ fmt(row.returnRate) }}%</template></el-table-column>
      </el-table>
    </el-card>

    <!-- ⑤ 按品牌汇总 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>品牌汇总</template>
      <el-table :data="data.byBrand" border stripe size="small" max-height="260">
        <el-table-column prop="brandName" label="品牌" min-width="140" show-overflow-tooltip/>
        <el-table-column label="数量" min-width="90" align="right"><template #default="{row}">{{ fmt(row.quantity) }}</template></el-table-column>
        <el-table-column label="销售额" min-width="120" align="right"><template #default="{row}"><span style="color:var(--app-color-success)">{{ fmt(row.amount) }}</span></template></el-table-column>
        <el-table-column label="成本" min-width="120" align="right"><template #default="{row}">{{ fmt(row.cost) }}</template></el-table-column>
        <el-table-column label="利润" min-width="120" align="right"><template #default="{row}"><span :style="{color: Number(row.profit)>=0?'var(--app-color-success)':'var(--app-color-danger)', fontWeight:'600'}">{{ fmt(row.profit) }}</span></template></el-table-column>
      </el-table>
    </el-card>

    <!-- ⑥ 退货明细 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>退货明细</template>
      <el-table :data="data.returns" border stripe size="small" show-summary :summary-method="returnSummaries" max-height="320">
        <el-table-column label="退单号" min-width="140">
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click="router.push(`/sale/return/detail/${row.billId}`)">{{ row.billNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="date" label="日期" min-width="100" align="center"/>
        <el-table-column prop="sku" label="SKU" min-width="100" show-overflow-tooltip/>
        <el-table-column prop="productName" label="产品" min-width="110" show-overflow-tooltip/>
        <el-table-column prop="qualityType" label="品质" min-width="70" align="center"/>
        <el-table-column label="数量" min-width="76" align="right"><template #default="{row}">{{ fmt(row.quantity) }}</template></el-table-column>
        <el-table-column label="金额" min-width="90" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.amount) }}</span></template></el-table-column>
        <el-table-column prop="chargeType" label="收费类型" min-width="90" align="center"/>
        <el-table-column label="折损收款" min-width="88" align="right"><template #default="{row}">{{ fmt(row.lossAmount) }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip/>
      </el-table>
    </el-card>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.toolbar{display:flex;align-items:center;gap:12px;margin-bottom:12px;flex-wrap:wrap}
.spacer{flex:1}
.title{font-weight:600;font-size:15px}
.dim{font-size:12px;color:var(--el-text-color-secondary)}
.desc{margin-bottom:12px}
.stat-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:12px;margin-bottom:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:14px}
.stat-label{font-size:12px;color:var(--el-text-color-secondary);margin-top:4px}
.stat-value.sm{font-size:16px;font-weight:600}
.chart{width:100%;height:280px}
</style>
