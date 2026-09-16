<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">产品库存分布</span>
          <el-button :icon="'ArrowLeft'" @click="$router.back()">返回</el-button>
        </div>
      </template>

      <el-descriptions v-loading="loading" :column="4" border class="info">
        <el-descriptions-item label="SKU">{{ summary?.sku || '—' }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ summary?.productName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ summary?.unit || '—' }}</el-descriptions-item>
        <el-descriptions-item label="品牌">{{ summary?.brandName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="分布仓库">{{ rows.length }} 个</el-descriptions-item>
        <el-descriptions-item label="安全库存">
          <span v-if="summary?.safetyStock" :style="{ color: summary?.lowStock ? '#f56c6c' : '#67c23a' }">
            {{ fmt(summary.safetyStock) }}
            <span v-if="summary?.lowStock" class="low-tip">（总库存低于安全库存）</span>
          </span>
          <span v-else style="color:#999">未设置</span>
        </el-descriptions-item>
        <el-descriptions-item label="总库存">
          <strong>{{ fmt(totalOf(summary)) }}</strong>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span class="title">各仓库库存明细</span>
          <span class="right">
            <span class="hint">品质数量为该产品在该仓库下各品质档位的库存</span>
            <el-button :icon="'Download'" size="small" @click="exportDetail">导出 Excel</el-button>
          </span>
        </div>
      </template>

      <el-table v-loading="tableLoading" :data="rows" border stripe :summary-method="summaries" show-summary>
        <el-table-column prop="warehouseName" label="仓库" min-width="160">
          <template #default="{ row }">
            <el-link v-if="row.warehouseId" type="primary" underline="never" @click="goWarehouse(row)">
              {{ row.warehouseName || '—' }}
            </el-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="A规" width="100" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyA > 0" type="success" size="small">{{ fmt(row.qtyA) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="B规" width="100" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyB > 0" type="primary" size="small">{{ fmt(row.qtyB) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="C规" width="100" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyC > 0" type="warning" size="small">{{ fmt(row.qtyC) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="不良" width="100" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyDefect > 0" type="danger" size="small">{{ fmt(row.qtyDefect) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="待分类" width="100" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyPending > 0" type="info" size="small" title="压在售后仓、等待退货整理的库存">{{ fmt(row.qtyPending) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="小计" width="110" align="right">
          <template #default="{ row }"><strong>{{ fmt(totalQty(row)) }}</strong></template>
        </el-table-column>
        <!-- 百分比放进度条右侧（不用 text-inside）：条内文字会被 12px 的条高裁掉一半 -->
        <el-table-column label="占比" min-width="170">
          <template #default="{ row }">
            <el-progress :percentage="percent(row)" :stroke-width="10">
              <!-- 不用组件默认文字：它会按 stroke-width 算出 16px 内联字号（条内还展示不全），
                   这里用插槽自定义 12px 文字，显示在进度条后面 -->
              <span class="pct-text">{{ percent(row) }}%</span>
            </el-progress>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!tableLoading && rows.length === 0" description="该产品在成品仓暂无库存" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import * as XLSX from 'xlsx'

const route = useRoute()
const router = useRouter()
const productId = Number(route.params.id)

const loading = ref(false)
const tableLoading = ref(false)
const summary = ref<any>(null)
const rows = ref<any[]>([])

// 数量一律整数（2026-09-16）
function fmt(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }
function totalQty(row: any) {
  return (Number(row?.qtyA) || 0) + (Number(row?.qtyB) || 0) + (Number(row?.qtyC) || 0)
    + (Number(row?.qtyDefect) || 0) + (Number(row?.qtyPending) || 0)
}
function totalOf(row: any) { return row ? totalQty(row) : 0 }

/**
 * 仓库列点击进入仓库详情：按 factoryId 分流——
 * 有工厂的是委外仓（/outsource/warehouse/detail/:id），无工厂的是自有成品仓（/inventory/warehouse/detail/:id）
 */
function goWarehouse(row: any) {
  if (!row?.warehouseId) return
  router.push(row.factoryId
    ? `/outsource/warehouse/detail/${row.warehouseId}`
    : `/inventory/warehouse/detail/${row.warehouseId}`)
}

/** 单个仓库占该产品总库存的比例；总库存为 0 时显示 0，避免除零 */
function percent(row: any) {
  const total = totalQty(summary.value)
  if (!total) return 0
  return Math.round((totalQty(row) / total) * 10000) / 100
}

/** 各品质求和：key 对应后端返回的聚合字段 */
function sumBy(key: string) {
  return rows.value.reduce((s, r) => s + (Number(r[key]) || 0), 0)
}

/**
 * 表格底部合计行：按列索引取值（品质列用自定义插槽，没有 prop，只能按固定列序映射）
 * 列序：0 仓库 / 1 A规 / 2 B规 / 3 C规 / 4 不良 / 5 待分类 / 6 小计 / 7 占比
 */
function summaries({ columns }: any) {
  const keys = ['qtyA', 'qtyB', 'qtyC', 'qtyDefect', 'qtyPending']
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i >= 1 && i <= 5) return fmt(sumBy(keys[i - 1]))
    if (i === 6) return fmt(rows.value.reduce((s, r) => s + totalQty(r), 0))
    if (i === 7) return rows.value.length ? '100%' : ''
    return ''
  })
}

/** 产品档案与跨仓库汇总：按产品聚合接口传 productId 只返回该产品一行 */
async function loadSummary() {
  loading.value = true
  try {
    const res = await request.get<any, any>('/warehouse/stock/product-summary/page', {
      params: { pageNum: 1, pageSize: 1, productId }
    })
    summary.value = (res?.records || [])[0] || null
  } catch { summary.value = null } finally { loading.value = false }
}

/** 各仓库分布：按（仓库×产品）聚合的成品库存接口，直接用 productId 过滤 */
async function loadRows() {
  tableLoading.value = true
  try {
    const res = await request.get<any, any>('/warehouse/stock/product-stock/page', {
      params: { pageNum: 1, pageSize: 500, productId }
    })
    rows.value = (res?.records || []).sort((a: any, b: any) => totalQty(b) - totalQty(a))
  } catch { rows.value = [] } finally { tableLoading.value = false }
}

/**
 * 导出"各仓库库存明细"为 Excel：列与页面一致（含占比），数量按数值写入（整数不带小数），
 * 抬头带产品档案与总库存，末尾附与页面一致的合计行。
 */
function exportDetail() {
  const data: any[] = rows.value || []
  const s = summary.value || {}
  const cols = ['仓库', 'A规', 'B规', 'C规', '不良', '待分类', '小计', '占比(%)']
  const aoa: (string | number)[][] = [
    [`产品库存分布 - ${s.sku || ''} ${s.productName || ''}（导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 个仓库）`],
    [`品牌：${s.brandName || '—'}　单位：${s.unit || '—'}　总库存：${totalOf(s)}　安全库存：${s.safetyStock ? s.safetyStock : '未设置'}`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      r.warehouseName || '—',
      Number(r.qtyA ?? 0), Number(r.qtyB ?? 0), Number(r.qtyC ?? 0),
      Number(r.qtyDefect ?? 0), Number(r.qtyPending ?? 0),
      Number(totalQty(r) ?? 0), percent(r),
    ])
  })
  aoa.push(['合计',
    Number(sumBy('qtyA')), Number(sumBy('qtyB')), Number(sumBy('qtyC')),
    Number(sumBy('qtyDefect')), Number(sumBy('qtyPending')),
    Number(data.reduce((t: number, r: any) => t + totalQty(r), 0)), data.length ? 100 : 0])
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: cols.length - 1 } },
  ]
  ws['!cols'] = [{ wch: 24 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 12 }, { wch: 12 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '各仓库库存明细')
  XLSX.writeFile(wb, `产品库存分布_${s.sku || ''}_${localDate()}.xlsx`)
}

onMounted(() => { loadSummary(); loadRows() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.right { display: flex; align-items: center; gap: 12px; }
.title { font-weight: 600; }
.hint { font-size: 12px; color: #909399; }
.low-tip { font-size: 12px; }
/* 占比列：进度条后面的百分比文字（插槽自定义，避开组件按 stroke-width 算出的 16px 内联字号） */
.pct-text { font-size: 12px; margin-left: 8px; color: #606266; }
:deep(.el-progress__text) { min-width: 44px; }
.info { margin-bottom: 4px; }
:deep(.el-card__body) { padding: 16px; }
</style>
