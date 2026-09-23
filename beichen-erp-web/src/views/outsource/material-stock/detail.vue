<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原页头「返回」按钮已删）；本页只读无操作 -->
  <PageShell back-fallback="/outsource/material-stock">
    <el-card shadow="never">

      <el-descriptions v-loading="loading" :column="3" border class="info">
        <el-descriptions-item label="物料名称">{{ summary?.materialName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="物料类型">{{ summary?.materialTypeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ summary?.unit || '—' }}</el-descriptions-item>
        <el-descriptions-item label="分布仓库">{{ rows.length }} 个</el-descriptions-item>
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
            <span class="hint">数量为该物料在该仓库下的良品 / 不良库存（物料品质只有这两档）</span>
            <el-button :icon="'Download'" size="small" @click="exportDetail">导出 Excel</el-button>
          </span>
        </div>
      </template>

      <el-table v-loading="tableLoading" :data="rows" border stripe :summary-method="summaries" show-summary>
        <el-table-column prop="warehouseName" label="仓库" min-width="150">
          <template #default="{ row }">
            <el-link v-if="row.warehouseId" type="primary" underline="never" @click="goHistory(row)">
              {{ row.warehouseName || '—' }}
            </el-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="良品" width="110" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyGood > 0" type="success" size="small">{{ fmt(row.qtyGood) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="不良" width="110" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyDefect > 0" type="danger" size="small">{{ fmt(row.qtyDefect) }}</el-tag>
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
              <span class="pct-text">{{ percent(row) }}%</span>
            </el-progress>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!tableLoading && rows.length === 0" description="该物料在物料仓暂无库存" />
    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import * as XLSX from 'xlsx'
import PageShell from '@/components/PageShell.vue'

const route = useRoute()
const router = useRouter()
const materialId = Number(route.params.id)

const loading = ref(false)
const tableLoading = ref(false)
const summary = ref<any>(null)
const rows = ref<any[]>([])

// 数量一律整数（与成品库存分布详情一致）
function fmt(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }
function totalQty(row: any) {
  return (Number(row?.qtyGood) || 0) + (Number(row?.qtyDefect) || 0)
}
function totalOf(row: any) { return row ? totalQty(row) : 0 }

/**
 * 仓库列点击进入「物料库存流水」。
 * 成品侧按 factoryId 分流到「委外仓库详情 / 成品仓库详情」；物料侧没有仓库详情页，
 * 而委外仓库详情与自有物料仓页的既有做法都是跳 `/outsource/material-history/:wid/:mid`
 * （见 outsource/warehouse-detail.vue 的「详细」按钮）⇒ 这里沿用同一约定，两种仓一致。
 */
function goHistory(row: any) {
  if (!row?.warehouseId) return
  router.push(`/outsource/material-history/${row.warehouseId}/${materialId}`)
}

/** 单个仓库占该物料总库存的比例；总库存为 0 时显示 0，避免除零 */
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
 * 列序：0 仓库 / 1 良品 / 2 不良 / 3 小计 / 4 占比
 */
function summaries({ columns }: any) {
  const keys = ['qtyGood', 'qtyDefect']
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i >= 1 && i <= 2) return fmt(sumBy(keys[i - 1]))
    if (i === 3) return fmt(rows.value.reduce((s, r) => s + totalQty(r), 0))
    if (i === 4) return rows.value.length ? '100%' : ''
    return ''
  })
}

/** 物料档案与跨仓库汇总：按物料聚合接口传 materialId 只返回该物料一行 */
async function loadSummary() {
  loading.value = true
  try {
    const res = await request.get<any, any>('/warehouse/stock/material-summary/page', {
      params: { pageNum: 1, pageSize: 1, materialId }
    })
    summary.value = (res?.records || [])[0] || null
  } catch { summary.value = null } finally { loading.value = false }
}

/** 各仓库分布：按（仓库×物料）聚合的物料库存接口，直接用 materialId 过滤 */
async function loadRows() {
  tableLoading.value = true
  try {
    const res = await request.get<any, any>('/warehouse/stock/material-stock/page', {
      params: { pageNum: 1, pageSize: 500, materialId }
    })
    rows.value = (res?.records || []).sort((a: any, b: any) => totalQty(b) - totalQty(a))
  } catch { rows.value = [] } finally { tableLoading.value = false }
}

/**
 * 导出"各仓库库存明细"为 Excel：列与页面一致（含占比），数量按数值写入（整数不带小数），
 * 抬头带物料档案与总库存，末尾附与页面一致的合计行。
 */
function exportDetail() {
  const data: any[] = rows.value || []
  const s = summary.value || {}
  const cols = ['仓库', '良品', '不良', '小计', '占比(%)']
  const aoa: (string | number)[][] = [
    [`物料库存分布 - ${s.materialTypeName || ''} ${s.materialName || ''}（导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 个仓库）`],
    [`单位：${s.unit || '—'}　总库存：${totalOf(s)}`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      r.warehouseName || '—',
      Number(r.qtyGood ?? 0), Number(r.qtyDefect ?? 0),
      Number(totalQty(r) ?? 0), percent(r),
    ])
  })
  aoa.push(['合计',
    Number(sumBy('qtyGood')), Number(sumBy('qtyDefect')),
    Number(data.reduce((t: number, r: any) => t + totalQty(r), 0)), data.length ? 100 : 0])
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: cols.length - 1 } },
  ]
  ws['!cols'] = [{ wch: 24 }, { wch: 10 }, { wch: 10 }, { wch: 12 }, { wch: 12 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '各仓库库存明细')
  XLSX.writeFile(wb, `物料库存分布_${s.materialName || ''}_${localDate()}.xlsx`)
}

onMounted(() => { loadSummary(); loadRows() })
</script>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .page / .card-header 局部样式已删除 */
.right { display: flex; align-items: center; gap: 12px; }
.title { font-weight: 600; }
.hint { font-size: var(--app-font-xs); color: #909399; }
/* 占比列：进度条后面的百分比文字（插槽自定义，避开组件按 stroke-width 算出的 16px 内联字号） */
.pct-text { font-size: var(--app-font-xs); margin-left: 8px; color: #606266; }
:deep(.el-progress__text) { min-width: 44px; }
.info { margin-bottom: 4px; }
:deep(.el-card__body) { padding: 16px; }
</style>
