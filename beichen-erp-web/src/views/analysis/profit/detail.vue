<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { BizTypeLabel, ExpenseTypeLabel } from '@/api/enums'

/**
 * 利润明细钻取页：某一天利润表数字背后的每一条单据记录。
 * 从财务分析 → 利润表行「详细」进入，路由参数 date = yyyy-MM-dd。
 * keep-alive 复用组件，路由参数必须用 computed 取。
 *
 * 明细按大类分 3 个 TAB（收入 / 销售成本 / 运营费用），与顶部三张汇总卡对应；
 * TAB 标题带小计金额；数据仍由明细接口一次返回，前端按 category 分组。
 */
const route = useRoute(); const router = useRouter()
const date = computed(() => String(route.params.date || ''))
const loading = ref(false)
const detail = ref<any>({ date: '', summary: {}, records: [] })
const activeTab = ref('REVENUE')

/** 类型展示配色：收入类绿、成本/费用红、退货冲减橙 */
const TagType: Record<string, 'success' | 'danger' | 'warning' | 'info' | 'primary'> = {
  SALE: 'success', LOSS_INCOME: 'success', SALE_RETURN: 'warning',
  SALE_COST: 'danger', SALE_RETURN_COST: 'warning', EXPENSE: 'danger',
}
function tagType(bizType?: string) { return TagType[bizType || ''] || 'info' }
/**
 * 类型显示文案（2026-09-14：后端只回 code，中文一律前端映射）
 * - 主类型 bizType（SALE/SALE_RETURN/…）→ BizTypeLabel
 * - 费用行细分 subType 存费用类型 code（OFFICE/RENT/…）→ ExpenseTypeLabel
 */
function typeText(r: any) {
  if (r.bizType === 'EXPENSE') return '费用-' + (ExpenseTypeLabel[r.subType] || r.subType || '')
  return BizTypeLabel[r.bizType] || r.bizType || ''
}
function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
/** 金额正负与类别联合决定颜色：增加支出/减少收入为红，反之为绿 */
function amountColor(r: any) {
  const neg = Number(r.amount) < 0
  if (r.category === 'REVENUE') return neg ? 'var(--app-color-danger)' : 'var(--app-color-success)'
  return neg ? 'var(--app-color-success)' : 'var(--app-color-danger)'
}

const records = computed(() => detail.value.records || [])
/** 按大类分组：REVENUE=收入 / COST=销售成本 / EXPENSE=运营费用 */
function byCategory(cat: string) {
  return records.value.filter((r: any) => r.category === cat)
}
const revenueRows = computed(() => byCategory('REVENUE'))
const costRows = computed(() => byCategory('COST'))
const expenseRows = computed(() => byCategory('EXPENSE'))
function sumOf(rows: any[]) { return rows.reduce((s, r) => s + Number(r.amount || 0), 0) }

/**
 * 单号点击进入对应单据详情：按单据类型拼详情页路由。
 * 费用单（finance_expense）没有独立详情页，退化为跳费用管理列表。
 */
const DetailPath: Record<string, (id: any) => string> = {
  SALE: id => `/inventory/sale/detail/${id}`,
  SALE_COST: id => `/inventory/sale/detail/${id}`,  // 销售出库成本出自销售单本身
  SALE_RETURN: id => `/sale/return/detail/${id}`,
  SALE_RETURN_COST: id => `/sale/return/detail/${id}`,
  LOSS_INCOME: id => `/sale/return/detail/${id}`,   // 折损收款出自销售退单，看退单详情
  EXPENSE: () => '/finance/expense',
}
function goBill(row: any) {
  if (!row.billId) return
  const build = DetailPath[row.bizType]
  if (build) router.push(build(row.billId))
}

/** TAB 定义：标题带小计，数量角标提示单据条数 */
const tabs = computed(() => [
  { name: 'REVENUE', label: `收入 ${fmt(sumOf(revenueRows.value))}`, rows: revenueRows.value },
  { name: 'COST', label: `销售成本 ${fmt(sumOf(costRows.value))}`, rows: costRows.value },
  { name: 'EXPENSE', label: `运营费用 ${fmt(sumOf(expenseRows.value))}`, rows: expenseRows.value },
])
const activeRows = computed(() => tabs.value.find(t => t.name === activeTab.value)?.rows || [])

async function loadDetail() {
  if (!date.value) return
  loading.value = true
  try {
    const r = await request.get<any, any>('/finance/analysis/profit-detail/records', { params: { date: date.value } })
    detail.value = r || { date: '', summary: {}, records: [] }
  } catch { detail.value = { date: '', summary: {}, records: [] } }
  finally { loading.value = false }
}

/** 当前 TAB 的底部合计行：金额列显示该 TAB 小计，其余留空 */
function summaries({ columns }: any) {
  const total = sumOf(activeRows.value)
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 4) return fmt(total)
    return ''
  })
}

onMounted(() => { loadDetail() })
// keep-alive 缓存下再次进入（换日期）不会触发 onMounted
onActivated(() => { loadDetail() })
</script>
<template>
  <div class="p">
    <div class="toolbar" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <div>
        <el-button :icon="'Back'" size="small" @click="router.push('/analysis/profit')">返回</el-button>
        <span style="margin-left:12px;font-weight:600;font-size:15px">{{ detail.date || date }} 利润明细</span>
      </div>
    </div>
    <!-- 当日汇总（与利润表该行数字一致） -->
    <div class="stat-grid" style="grid-template-columns:repeat(3,1fr);margin-bottom:12px">
      <div class="stat-card mini">
        <div class="stat-label">收入（销售 − 退货 + 折损）</div>
        <div class="stat-value sm" style="color:var(--app-color-success)">{{ fmt(detail.summary?.income) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">支出（销售成本 {{ fmt(detail.summary?.cost) }} + 运营费用 {{ fmt(detail.summary?.expense) }}）</div>
        <div class="stat-value sm" style="color:var(--app-color-danger)">{{ fmt(detail.summary?.expenseTotal) }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">利润（收入 − 支出）</div>
        <div class="stat-value sm" :style="{color: Number(detail.summary?.profit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)', fontWeight: '600'}">{{ fmt(detail.summary?.profit) }}</div>
      </div>
    </div>
    <el-card shadow="never" class="table-card">
      <!-- 按大类分 TAB：标题带小计金额，括号为单据条数 -->
      <el-tabs v-model="activeTab">
        <el-tab-pane v-for="t in tabs" :key="t.name" :name="t.name">
          <template #label>
            <span>{{ t.label }}</span>
            <el-tag size="small" effect="plain" style="margin-left:6px">{{ t.rows.length }}</el-tag>
          </template>
          <el-table v-loading="loading" :data="t.rows" border stripe size="small" show-summary :summary-method="summaries">
            <el-table-column label="类型" width="130" align="center">
              <template #default="{ row }"><el-tag :type="tagType(row.bizType)" size="small">{{ typeText(row) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="billNo" label="单号" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link v-if="row.billId" type="primary" underline="never" @click="goBill(row)">{{ row.billNo }}</el-link>
                <span v-else>{{ row.billNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="partner" label="往来单位" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.partner || '—' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column label="金额" width="130" align="right">
              <template #default="{ row }">
                <span :style="{ color: amountColor(row), fontWeight: '600' }">{{ Number(row.amount) < 0 ? '-' : '' }}{{ fmt(Math.abs(Number(row.amount))) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="说明" width="110" align="center">
              <template #default="{ row }">
                <span v-if="Number(row.amount) >= 0" style="color:var(--el-text-color-secondary)">—</span>
                <el-tag v-else type="success" size="small" effect="plain">冲减</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <div class="dim-tip">单据口径与利润表聚合一致，负数为冲减（退货冲减收入 / 退货冲回成本）；成本 = 销售明细数量 × 产品当前移动加权成本价</div>
    </el-card>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.stat-grid{display:grid;gap:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-label{font-size:12px;color:var(--el-text-color-secondary);margin-top:4px}
.stat-value.sm{font-size:16px;font-weight:600}
.dim-tip{font-size:12px;color:var(--el-text-color-secondary);margin-top:8px}
</style>
