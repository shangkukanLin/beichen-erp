<script setup lang="ts">
import { localDate } from '@/utils/date'
import { StockChangeTypeLabel, stockChangeTypeTag } from '@/api/enums'
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import * as XLSX from 'xlsx'

const route = useRoute(); const router = useRouter()
const warehouseId = route.params.wid
const productId = route.params.pid
const records = ref<any[]>([])
const loading = ref(false)
const pagination = ref({ pageNum: 1, pageSize: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/warehouse/stock/log', {
      params: { warehouseId, productId, pageNum: pagination.value.pageNum, pageSize: pagination.value.pageSize }
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
      other_io: '/inventory/other-io/add?id=',
      outsource_other_io: '/outsource/other-io/add?id=',
      purchase: '/inventory/purchase/detail/',
      sale: '/inventory/sale/edit?id='
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

/** 品质展示文案（与页面一致：DEFECT 显示"不良"） */
function qualityText(q: any) { return q === 'DEFECT' ? '不良' : (q || '—') }

/**
 * 导出 Excel（**全量**）：按当前仓库/产品重新请求该组合的**全部**流水（pageNum=1、pageSize=9999），
 * 不受列表分页限制；列与页面一致，数量按数值写入（整数不带小数）。
 * 请求失败时退回当前页已加载数据，保证导出始终可用。
 */
async function exportHistory() {
  let data: any[] = records.value || []
  try {
    const res = await request.get<any, any>('/warehouse/stock/log', {
      params: { warehouseId, productId, pageNum: 1, pageSize: 9999 }
    })
    if (Array.isArray(res?.records)) data = res.records
  } catch { /* 拉取失败：退回当前页数据 */ }
  const cols = ['时间', '类型', '产品名称', '品质', '变更前', '变更数量', '变更后', '总数', '关联单号']
  const aoa: (string | number)[][] = [
    [`成品库存流水（导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 行）`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      fmtDateTime(r.createTime),
      StockChangeTypeLabel[r.changeType] || r.changeType || '',
      r.productName || '',
      qualityText(r.qualityType),
      Number(r.beforeQuantity ?? 0),
      Number(r.changeQuantity ?? 0),
      Number(r.afterQuantity ?? 0),
      Number(r.totalAfterStock ?? 0),
      r.relatedBillNo || '',
    ])
  })
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } }]
  ws['!cols'] = [{ wch: 20 }, { wch: 18 }, { wch: 24 }, { wch: 8 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 20 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '成品库存流水')
  XLSX.writeFile(wb, `成品库存流水_${localDate()}.xlsx`)
}

function handlePageChange() { loadData() }
function handleSizeChange() { pagination.value.pageNum = 1; loadData() }

onMounted(() => loadData())
</script>

<template>
  <div class="history-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button :icon="'Download'" @click="exportHistory">导出 Excel</el-button>
      </div>
      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column label="时间" width="110"><template #default="{row}">{{ $fmtDate(row.createTime) }}</template></el-table-column>
        <el-table-column label="类型" width="180" align="center">
          <template #default="{row}"><el-tag :type="stockChangeTypeTag(row.changeType)" size="small">{{ StockChangeTypeLabel[row.changeType] || row.changeType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="品质" width="70" align="center">
          <template #default="{row}">
            <el-tag :type="row.qualityType==='DEFECT'?'danger':row.qualityType==='B'?'warning':row.qualityType==='C'?'info':undefined" size="small">{{ row.qualityType==='DEFECT'?'不良':row.qualityType||'—' }}</el-tag>
          </template>
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
        <el-table-column label="总数" width="100" align="right">
          <template #default="{row}"><span style="font-weight:600;color:var(--app-color-primary)">{{ row.totalAfterStock }}</span></template>
        </el-table-column>
        <el-table-column label="关联单号" width="160" show-overflow-tooltip>
          <template #default="{row}"><el-button v-if="row.relatedBillNo" type="primary" link @click="handleCodeClick(row.relatedBillNo)">{{ row.relatedBillNo }}</el-button><span v-else style="color:var(--app-text-placeholder)">—</span></template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="handlePageChange" @size-change="handleSizeChange" /></div>
    </el-card>
  </div>
</template>

<style scoped>
.history-page { display:flex; flex-direction:column; gap:12px; }

.toolbar { display:flex; justify-content:flex-end; margin-bottom:8px; }

.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
