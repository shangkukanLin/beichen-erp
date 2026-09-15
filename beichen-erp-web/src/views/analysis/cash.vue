<script setup lang="ts">
import { ref, onMounted, onActivated, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { accountTypeLabel } from '@/api/enums'

/** 资金与往来（经营分析）：资金收支趋势 + 账户余额 + 应收应付账龄 + 主体往来统计 */
const loading = ref(false)
const months = ref(12)
const cash = ref<any>({ months: [], income: [], expense: [], net: [], accounts: [] })
const aging = ref<any>({})
const subject = ref<any>({})
let cashChart: echarts.ECharts | null = null

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }

function renderCashChart() {
  const el = document.getElementById('cashChart')
  if (!el) return
  cashChart = cashChart || echarts.init(el)
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

async function loadData() {
  loading.value = true
  try {
    const [c, a, sub] = await Promise.all([
      request.get<any, any>('/finance/analysis/cash-trend', { params: { months: months.value } }),
      request.get<any, any>('/finance/analysis/aging'),
      request.get<any, any>('/finance/analysis/subject'),
    ])
    cash.value = c || { months: [], income: [], expense: [], net: [], accounts: [] }
    aging.value = a || {}
    subject.value = sub || {}
  } catch {
    cash.value = { months: [], income: [], expense: [], net: [], accounts: [] }
    aging.value = {}; subject.value = {}
  } finally { loading.value = false }
  await nextTick()
  renderCashChart()
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
</script>
<template>
  <div class="p" v-loading="loading">
    <div id="cashChart" class="chart"/>
    <el-table :data="cash.accounts" border stripe size="small" style="margin-top:12px">
      <el-table-column prop="name" label="账户名称" min-width="140"/>
      <el-table-column prop="type" label="类型" width="100" align="center"><template #default="{row}"><el-tag size="small">{{ accountTypeLabel(row.type) }}</el-tag></template></el-table-column>
      <el-table-column prop="balance" label="余额" width="150" align="right"><template #default="{row}">{{ fmt(row.balance) }}</template></el-table-column>
    </el-table>

    <el-row :gutter="12" style="margin-top:12px">
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

    <!-- 主体往来统计 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>主体往来统计（按往来主体类型）</template>
      <div class="stat-grid" style="grid-template-columns:repeat(5,1fr);margin-bottom:12px">
        <div class="stat-card mini"><div class="stat-label">供货商应付（未付）</div><div class="stat-value sm">{{ fmt(subject.payableByType?.product) }}</div></div>
        <div class="stat-card mini"><div class="stat-label">加工厂应付（未付）</div><div class="stat-value sm">{{ fmt(subject.payableByType?.factory) }}</div></div>
        <div class="stat-card mini"><div class="stat-label">辅料商应付（未付）</div><div class="stat-value sm">{{ fmt(subject.payableByType?.material) }}</div></div>
        <div class="stat-card mini"><div class="stat-label">方案商应付（未付）</div><div class="stat-value sm">{{ fmt(subject.payableByType?.solution) }}</div></div>
        <div class="stat-card mini"><div class="stat-label">其他主体应付（未付）</div><div class="stat-value sm">{{ fmt(subject.payableByType?.other) }}</div></div>
      </div>
      <div class="stat-grid" style="grid-template-columns:repeat(2,1fr)">
        <div class="stat-card mini">
          <div class="stat-label">供应商应收（未收，应付转应收）</div>
          <div class="stat-value sm" style="color:var(--app-color-warning)">{{ fmt(subject.supplierReceivableUnpaid) }}</div>
          <div class="stat-sub"><span class="dim">退货/超损扣款已转应收、待对方付款的金额</span></div>
        </div>
        <div class="stat-card mini">
          <div class="stat-label">报损损失（已审核报损单）</div>
          <div class="stat-value sm" style="color:var(--app-color-danger)">{{ fmt(subject.lossTotal) }}</div>
          <div class="stat-sub"><span class="dim">成品报损 + 委外物料报损（按报损时成本计）</span></div>
        </div>
      </div>
    </el-card>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.chart{width:100%;height:320px}
.stat-grid{display:grid;gap:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-value{font-size:20px;font-weight:600}
.stat-label{font-size:12px;color:var(--el-text-color-secondary);margin-top:4px}
.stat-value.sm{font-size:16px;font-weight:600}
.stat-sub{margin-top:6px;font-size:12px;display:flex;gap:8px;align-items:center}
.stat-sub .dim{color:var(--el-text-color-secondary)}
</style>
