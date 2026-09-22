<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import StatRange from '@/components/StatRange.vue'

/**
 * 客户分析（经营分析）：KPI + 客户明细（含退货、应收余额、账期）。
 * 行点「明细」下钻到该客户在区间的销售单（/analysis/customer/detail）。
 * 2026-09-15 用户要求：删除「客户销售额 TOP10」条形图卡片（前端图表 + 后端 `top` 字段一并清理）。
 */
const router = useRouter()
const preset = ref('month')
const range = ref<[string, string] | null>(null)
const loading = ref(false)
const data = ref<any>({ start: '', end: '', summary: {}, rows: [] })

function fmt(v?: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtN(v?: any) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function pct(part: number, total: number) {
  if (!total) return '0.0'
  return (Math.round((part / total) * 1000) / 10).toFixed(1)
}

const summary = computed(() => data.value.summary || {})

/**
 * 数值排序器（2026-09-22 用户要求：客户明细的数值列可点表头正/倒序）。
 * 用法：`sortable :sort-method="sortNum('amount')"`。数据一次性全量加载（无分页）⇒ 客户端排序即可，不动接口。
 * ⚠️ 必须自己按**数值**比较：本页金额/比率来自后端字符串（页面里到处 `Number(row.x)`），
 * 用默认排序会走字典序 ⇒ "9" 会排到 "10" 后面 ✗（本页多处直接 Number(...)，不能只依赖默认行为）。
 * 空值/异常值统一当 0；顺带容忍千分位（正常 raw 数据没有，防御性处理）。
 */
function sortNum(key: string) {
  const num = (v: any) => {
    const n = Number(String(v ?? 0).replace(/,/g, ''))
    return Number.isFinite(n) ? n : 0
  }
  return (a: any, b: any) => num(a?.[key]) - num(b?.[key])
}

async function loadData() {
  if (preset.value === 'custom' && !(range.value?.length === 2)) return
  loading.value = true
  try {
    const params: any = { preset: preset.value }
    if (preset.value === 'custom' && range.value?.length === 2) {
      params.start = range.value[0]; params.end = range.value[1]
    }
    data.value = await request.get<any, any>('/customer/analysis', { params }) || { start: '', end: '', summary: {}, rows: [] }
  } catch { data.value = { start: '', end: '', summary: {}, rows: [] } } finally { loading.value = false }
}
// 预设切换/日期变更的"清空 + 触发"逻辑已收口到 StatRange 组件，页面只需 loadData

function drill(row: any) {
  const query: any = { preset: preset.value, customerId: row.customerId, customerName: row.customerName }
  if (preset.value === 'custom' && range.value?.length === 2) {
    query.start = range.value[0]; query.end = range.value[1]
  }
  router.push({ path: '/analysis/customer/detail', query })
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
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
        <div class="stat-label">客户总数 / 本期活跃</div>
        <div class="stat-value sm">{{ summary.customerCount || 0 }} / <span style="color:var(--app-color-success)">{{ summary.activeCount || 0 }}</span></div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">本期新增客户</div>
        <div class="stat-value sm" style="color:var(--app-color-primary)">{{ summary.newCount || 0 }}</div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">销售额 / 退货额</div>
        <div class="stat-value sm"><span style="color:var(--app-color-success)">{{ fmtN(summary.totalAmount) }}</span> / <span style="color:var(--app-color-danger)">{{ fmtN(summary.returnAmount) }}</span></div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">利润（净额 − 成本）</div>
        <div class="stat-value sm" :style="{color: Number(summary.profit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)', fontWeight:'600'}">
          {{ fmtN(summary.profit) }}｜{{ fmt(summary.profitRate) }}%
        </div>
      </div>
      <div class="stat-card mini">
        <div class="stat-label">客均销售额（活跃客户）</div>
        <div class="stat-value sm">{{ fmtN(summary.avgAmount) }}</div>
      </div>
    </div>
    <div class="dim-tip">利润口径：净销售额 − 销售成本（成本按产品移动加权平均成本价 × 数量估算，退货退回的成本已冲回）。采购成本无法按客户归集、运营费用无法按客户分摊，故此处为毛利口径。</div>
    <!-- 客户明细（2026-09-15：原「客户销售额 TOP10」条形图卡片已按用户要求删除） -->
    <el-card shadow="never">
      <template #header>客户明细（点「明细」看该客户的销售单）</template>
      <!--
        全部列用 min-width（不用固定 width）：Element Plus 按 min-width 比例分摊剩余空间，
        窄屏刚好放下、宽屏自动铺满，不会出现横向滚动。序号与账期列已按需求移除。
      -->
      <el-table :data="data.rows" border stripe max-height="420">
        <el-table-column prop="customerCode" label="客户编码" min-width="78" show-overflow-tooltip/>
        <el-table-column prop="customerName" label="客户" min-width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- 点客户名进单客户分析（看该客户的拿货/品牌/利润/退货全貌）。
                 不再显示「新增」标签：种子数据首单集中在同一天，全员标记反而像脏数据。 -->
            <el-link type="primary" underline="never" @click="router.push(`/analysis/customer/${row.customerId}`)">{{ row.customerName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="销售额" min-width="75" align="right" sortable :sort-method="sortNum('amount')"><template #default="{row}"><span style="color:var(--app-color-success)">{{ fmt(row.amount) }}</span></template></el-table-column>
        <el-table-column label="退货额" min-width="71" align="right" sortable :sort-method="sortNum('returnAmount')"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.returnAmount) }}</span></template></el-table-column>
        <el-table-column label="净额" min-width="75" align="right" sortable :sort-method="sortNum('netAmount')"><template #default="{row}">{{ fmt(row.netAmount) }}</template></el-table-column>
        <!-- 成本按产品移动加权成本价估算（采购无法按客户归集），毛利=净额−成本 -->
        <el-table-column label="成本" min-width="71" align="right" sortable :sort-method="sortNum('cost')"><template #default="{row}"><span style="color:var(--app-color-warning)">{{ fmt(row.cost) }}</span></template></el-table-column>
        <el-table-column label="利润" min-width="75" align="right" sortable :sort-method="sortNum('profit')">
          <template #default="{row}">
            <span :style="{color: Number(row.profit)>=0?'var(--app-color-success)':'var(--app-color-danger)', fontWeight:'600'}">{{ fmt(row.profit) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="利润率" min-width="61" align="right" sortable :sort-method="sortNum('profitRate')">
          <template #default="{row}">
            <span :style="{color: Number(row.profitRate)>=0?'var(--app-color-success)':'var(--app-color-danger)'}">{{ fmt(row.profitRate) }}%</span>
          </template>
        </el-table-column>
        <!-- 占比列排序 = 按 amount 排（展示口径就是「该客户销售额 / 合计销售额」，两者同序） -->
        <el-table-column label="占比" min-width="51" align="right" sortable :sort-method="sortNum('amount')"><template #default="{row}">{{ pct(Number(row.amount), Number(summary.totalAmount)) }}%</template></el-table-column>
        <el-table-column label="订单数" min-width="51" align="center" sortable :sort-method="sortNum('orderCount')"><template #default="{row}">{{ row.orderCount }}</template></el-table-column>
        <el-table-column label="应收余额" min-width="75" align="right" sortable :sort-method="sortNum('unpaid')"><template #default="{row}"><span :style="{color: Number(row.unpaid)>0?'var(--app-color-warning)':'inherit'}">{{ fmt(row.unpaid) }}</span></template></el-table-column>
        <el-table-column prop="lastDate" label="最近成交" min-width="80" align="center"/>
        <el-table-column label="操作" min-width="62" align="center">
          <template #default="{row}">
            <el-button link type="primary" @click="drill(row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
<style scoped>
.p{display:flex;flex-direction:column}
.toolbar{display:flex;align-items:center;margin-bottom:12px}
.stat-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin-bottom:12px}
.dim-tip{font-size:var(--app-font-xs);color:var(--el-text-color-secondary);margin-bottom:12px}
.stat-card{background:var(--el-fill-color-light);border-radius:8px;padding:16px}
.stat-label{font-size:var(--app-font-xs);color:var(--el-text-color-secondary);margin-top:4px}
.stat-value.sm{font-size:var(--app-font-num-sm);font-weight:600}
</style>
