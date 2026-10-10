<script setup lang="ts">
import { localDate } from '@/utils/date'
import { StockChangeTypeLabel, stockChangeTypeTag } from '@/api/enums'
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import * as XLSX from 'xlsx'
import PageShell from '@/components/PageShell.vue'

const route = useRoute(); const router = useRouter()
const warehouseId = Number(route.params.wid)
const materialId = Number(route.params.mid)
const records = ref<any[]>([])
const loading = ref(false)
const pagination = ref({ pageNum: 1, pageSize: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/warehouse/stock/material-history', {
      params: { warehouseId: warehouseId || undefined, materialId: materialId || undefined, pageNum: pagination.value.pageNum, pageSize: pagination.value.pageSize }
    })
    records.value = r?.records || []
    pagination.value.total = r?.total || 0
  } finally { loading.value = false }
}

async function handleCodeClick(code: string) {
  if (!code) return
  try {
    const r = await request.get<any, any>('/common/resolve-code', { params: { code } })
    if (!r?.type) { ElMessage.info('未找到关联单据'); return }
    const map: Record<string, string> = {
      order: '/outsource/order/detail/',
      material_order: '/outsource/material-order/detail/',
      delivery: '/outsource/delivery/detail/',
      other_io: '/inventory/other-io/detail/',
      outsource_other_io: '/outsource/other-io/detail/'
    }
    const path = map[r.type]
    if (path) router.push(path + r.id)
    else ElMessage.info('不支持的关联单据类型')
  } catch { ElMessage.error('查询失败') }
}

/** 时间格式化（脚本内导出用；模板里的 $fmtDate 是全局属性，脚本中取不到） */
function fmtDateTime(v: any) {
  if (!v) return ''
  const d = new Date(String(v).replace(' ', 'T'))
  if (isNaN(d.getTime())) return String(v)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/**
 * 导出 Excel（**全量**）：按当前仓库/物料重新请求该组合的**全部**流水（pageNum=1、pageSize=9999），
 * 不受列表分页限制；列与页面一致，数量按数值写入（整数不带小数）。
 * 请求失败时退回当前页已加载数据，保证导出始终可用。
 */
async function exportHistory() {
  let data: any[] = records.value || []
  try {
    const res = await request.get<any, any>('/warehouse/stock/material-history', {
      params: { warehouseId: warehouseId || undefined, materialId: materialId || undefined, pageNum: 1, pageSize: 9999 }
    })
    if (Array.isArray(res?.records)) data = res.records
  } catch { /* 拉取失败：退回当前页数据 */ }
  const cols = ['时间', '关联单号', '类型', '物料名称', '变更前', '变更数量', '变更后']
  const aoa: (string | number)[][] = [
    [`物料库存流水（导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 行）`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      fmtDateTime(r.createTime),
      r.relatedOrderCode || '',
      StockChangeTypeLabel[r.changeType] || r.changeType || '',
      r.materialName || '',
      Number(r.beforeQuantity ?? 0),
      Number(r.changeQuantity ?? 0),
      Number(r.afterQuantity ?? 0),
    ])
  })
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } }]
  ws['!cols'] = [{ wch: 20 }, { wch: 20 }, { wch: 18 }, { wch: 24 }, { wch: 10 }, { wch: 10 }, { wch: 10 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '物料库存流水')
  XLSX.writeFile(wb, `物料库存流水_${localDate()}.xlsx`)
}

function handlePageChange() { loadData() }
function handleSizeChange() { pagination.value.pageNum = 1; loadData() }

onMounted(() => loadData())
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回去「所属仓库详情」（本页由仓库详情进入）；只读，导出留卡内 -->
  <PageShell :back-fallback="`/inventory/warehouse/detail/${route.params.wid}`">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button :icon="'Download'" @click="exportHistory">导出 Excel</el-button>
      </div>
      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column label="时间" width="110"><template #default="{row}">{{ $fmtDate(row.createTime) }}</template></el-table-column>
        <el-table-column label="关联单号" width="180">
          <template #default="{row}"><el-button v-if="row.relatedOrderCode" type="primary" link @click="handleCodeClick(row.relatedOrderCode)">{{ row.relatedOrderCode }}</el-button><span v-else style="color:var(--app-text-placeholder)">—</span></template>
        </el-table-column>
        <el-table-column label="类型" width="190" align="center">
          <template #default="{row}"><el-tag :type="stockChangeTypeTag(row.changeType)" size="small">{{ StockChangeTypeLabel[row.changeType] || row.changeType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="物料名称" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ $mLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="变更前" width="100" align="right">
          <template #default="{row}"><span style="font-weight:500">{{ row.beforeQuantity }}</span></template>
        </el-table-column>
        <el-table-column label="变更数量" width="100" align="right">
          <template #default="{row}"><span :style="{color: Number(row.changeQuantity)<0?'var(--app-color-danger)':'var(--app-color-success)',fontWeight:600}">{{ Number(row.changeQuantity)>0?'+':'' }}{{ row.changeQuantity }}</span></template>
        </el-table-column>
        <el-table-column label="变更后" width="100" align="right">
          <template #default="{row}"><span :style="{color: Number(row.afterQuantity)<0?'var(--app-color-danger)':'',fontWeight:600}">{{ row.afterQuantity }}</span></template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSizeChange" @current-change="handlePageChange" />
      </div>
    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .history-page 局部样式已删除（.toolbar/.pagination 仍在用） */

.toolbar { display:flex; justify-content:flex-end; margin-bottom:8px; }

/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
</style>
