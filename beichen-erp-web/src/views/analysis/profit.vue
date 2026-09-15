<script setup lang="ts">
import { localDate } from '@/utils/date'
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as XLSX from 'xlsx'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'

/**
 * 利润表（经营分析）：按天展示收入 / 支出 / 利润三列（成本并入支出）。
 * 成本口径 = 销售明细数量 × 产品当前移动加权成本价（与客户分析/销售分析同源）。
 * 点行或「详细」进入 /analysis/profit/detail/:date 看该天每一条单据记录。
 */
const router = useRouter()
const preset = ref('month')
const profitRange = ref<[string, string] | null>(null)
const pd = ref<any>({ start: '', end: '', summary: {}, rows: [] })
const loading = ref(false)

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function today() { return localDate() }

async function loadProfitDetail() {
  if (preset.value === 'custom' && !(profitRange.value?.length === 2)) return
  loading.value = true
  try {
    const params: any = { preset: preset.value }
    if (preset.value === 'custom' && profitRange.value?.length === 2) {
      params.start = profitRange.value[0]; params.end = profitRange.value[1]
    }
    pd.value = await request.get<any, any>('/finance/analysis/profit-detail', { params })
      || { start: '', end: '', summary: {}, rows: [] }
  } catch { pd.value = { start: '', end: '', summary: {}, rows: [] } } finally { loading.value = false }
}
// 预设切换/日期变更的"清空 + 触发"逻辑已收口到 StatRange 组件，页面只需 loadProfitDetail

// 按天行较多（本年最多 400 行），前端分页；切区间回第 1 页
const page = ref(1)
const pageSize = ref(15)
const pagedRows = computed(() => {
  const rows = pd.value.rows || []
  const from = (page.value - 1) * pageSize.value
  return rows.slice(from, from + pageSize.value)
})
watch([preset, profitRange], () => { page.value = 1 })

function goDetail(row: any) { router.push(`/analysis/profit/detail/${row.date}`) }

/** 底部合计行：三列口径 + 操作列留空 */
function summaries({ columns }: any) {
  const rows = pd.value.rows || []
  const sum = (k: string) => fmt(rows.reduce((s: number, r: any) => s + Number(r[k] || 0), 0))
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 1) return sum('revenue')
    if (i === 2) return sum('expenseTotal')
    if (i === 3) return sum('profit')
    return ''
  })
}

// 导出：按天行 + 合计（三列口径）
function exportProfit() {
  const rows = pd.value.rows || []
  const s = pd.value.summary || {}
  const aoa: (string | number)[][] = [['日期', '收入', '支出', '利润']]
  rows.forEach((r: any) => aoa.push([r.date, Number(r.revenue), Number(r.expenseTotal), Number(r.profit)]))
  aoa.push(['合计', Number(s.income) || 0, Number(s.expenseTotal) || 0, Number(s.profit) || 0])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(aoa), '利润表')
  XLSX.writeFile(wb, `利润表_${pd.value.start || today()}_${pd.value.end || ''}.xlsx`)
}

onMounted(() => { loadProfitDetail() })
onActivated(() => { loadProfitDetail() })
</script>
<template>
  <div class="p">
    <div class="toolbar">
      <!-- 统计区间：统一组件（2026-09-15 全站收口，本页为基准） -->
      <StatRange v-model:preset="preset" v-model:range="profitRange" @change="loadProfitDetail"/>
      <el-button type="primary" plain size="small" style="margin-left:12px" @click="exportProfit">导出 Excel</el-button>
    </div>
    <!-- 总计卡：区间合计（成本并入支出） -->
    <div class="stat-grid">
      <div class="stat-card mini">
        <div class="stat-label">收入（销售 − 退货 + 折损）</div>
        <div class="stat-value sm" style="color:var(--app-color-success)">{{ fmt(pd.summary?.income) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">支出（销售成本 {{ fmt(pd.summary?.cost) }} + 运营费用 {{ fmt(pd.summary?.expense) }}）</div>
        <div class="stat-value sm" style="color:var(--app-color-danger)">{{ fmt(pd.summary?.expenseTotal) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">利润（收入 − 支出）</div>
        <div class="stat-value sm" :style="{color: Number(pd.summary?.profit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)', fontWeight: '600'}">{{ fmt(pd.summary?.profit) }}</div>
      </div>
    </div>
    <el-table v-loading="loading" :data="pagedRows" border stripe size="small" show-summary :summary-method="summaries" @row-click="goDetail">
      <el-table-column prop="date" label="日期" width="120" align="center"/>
      <el-table-column label="收入" min-width="140" align="right"><template #default="{row}">{{ fmt(row.revenue) }}</template></el-table-column>
      <el-table-column label="支出" min-width="140" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.expenseTotal) }}</span></template></el-table-column>
      <el-table-column label="利润" min-width="140" align="right"><template #default="{row}"><span :style="{color: Number(row.profit)>=0?'var(--app-color-success)':'var(--app-color-danger)', fontWeight:'bold'}">{{ fmt(row.profit) }}</span></template></el-table-column>
      <el-table-column label="操作" width="90" align="center">
        <template #default="{row}">
          <el-button link type="primary" @click.stop="goDetail(row)">详细</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pg">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
        :page-sizes="[15, 30, 50, 100]" :total="pd.rows?.length || 0"
        layout="total, sizes, prev, pager, next" background size="small"/>
    </div>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.toolbar{display:flex;align-items:center;margin-bottom:12px}
.pg{margin-top:16px;display:flex;justify-content:flex-end}
.stat-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-label{font-size:12px;color:var(--el-text-color-secondary);margin-top:4px}
.stat-value.sm{font-size:16px;font-weight:600}
:deep(.el-table__row){cursor:pointer}
</style>
