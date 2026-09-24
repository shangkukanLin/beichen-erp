<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/inventory/material-move">
    <template #actions>
      <el-button v-if="isDraft" type="primary" @click="handleEdit">编辑</el-button>
      <el-button v-if="isDraft" type="success" @click="handleAudit">审核</el-button>
      <el-button v-if="isDraft" type="danger" @click="handleCancel">作废</el-button>
      <el-button v-if="isAudited" type="warning" @click="handleUnAudit">反审核</el-button>
    </template>

    <el-card shadow="never" class="query-card">
      <template #header><span style="font-weight:600">移仓信息</span></template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="单号">{{ detail.code || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.status)">{{ DocStatusLabel[detail.status] || detail.status || '—' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="移仓日期">{{ $fmtDate(detail.moveDate) }}</el-descriptions-item>
        <el-descriptions-item label="移出仓库">{{ warehouseName(detail.fromWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="移入仓库">{{ warehouseName(detail.toWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ $fmtDate(detail.createTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header><span style="font-weight:600">移仓明细</span></template>
      <el-table :data="items" border stripe size="small">
        <el-table-column label="物料" min-width="240">
          <template #default="{ row }">
            <el-link v-if="row.materialId" type="primary" underline="never" @click.stop="$router.push(`/outsource/material-stock/detail/${row.materialId}`)">{{ row.materialName || ('物料#' + row.materialId) }}</el-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="90" align="center">
          <template #default="{ row }">{{ row.unit || '—' }}</template>
        </el-table-column>
        <el-table-column label="数量" width="140" align="right">
          <template #default="{ row }">{{ fmtQty(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="200">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onActivated, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { WarehouseCategory, WarehouseType, INVENTORY_MATERIAL_MOVE_DIRTY_KEY } from '@/api/enums'

defineOptions({ name: 'InventoryMaterialMoveDetail' })

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id))
const loading = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
const allWarehouses = ref<any[]>([])

const isDraft = computed(() => detail.value.status === DocStatus.DRAFT)
const isAudited = computed(() => detail.value.status === DocStatus.AUDITED)

function whLabel(w: any) {
  const kind = w?.warehouseCategory === WarehouseCategory.OUTSOURCE ? '委外仓' : '我方物料仓'
  return `${w.warehouseName}（${kind}）`
}
function warehouseName(idv?: number) {
  const w = allWarehouses.value.find((x: any) => x.id === idv)
  return w ? whLabel(w) : (idv ? ('仓库#' + idv) : '—')
}
function statusType(s?: string) { return DocStatusTag[s || ''] || '' }
function fmtQty(v: any) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return String(Number(n.toFixed ? n.toFixed(0) : n))
}

async function loadAll() {
  loading.value = true
  try {
    const [w, d, its] = await Promise.all([
      request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } }).catch(() => null),
      request.get<any, any>(`/inventory/material-move/${id.value}`),
      request.get<any, any>(`/inventory/material-move/${id.value}/items`).catch(() => [])
    ])
    allWarehouses.value = w?.records || []
    detail.value = d || {}
    items.value = its || []
  } finally { loading.value = false }
}

onActivated(() => { loadAll() })

function handleEdit() { router.push({ path: '/inventory/material-move/add', query: { id: String(id.value) } }) }

async function handleAudit() {
  try {
    await ElMessageBox.confirm(`确认审核物料移仓单「${detail.value.code}」？将从移出仓扣减物料库存并增加到移入仓。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/material-move/${id.value}/audit`)
    ElMessage.success('审核成功')
    sessionStorage.setItem(INVENTORY_MATERIAL_MOVE_DIRTY_KEY, '1')
    loadAll()
  } catch { /* 已由 request 拦截器提示 */ }
}

async function handleUnAudit() {
  try {
    await ElMessageBox.confirm(`确认反审核物料移仓单「${detail.value.code}」？将退回移出仓库存并从移入仓扣回。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/material-move/${id.value}/un-audit`)
    ElMessage.success('反审核成功')
    sessionStorage.setItem(INVENTORY_MATERIAL_MOVE_DIRTY_KEY, '1')
    loadAll()
  } catch { /* 已由 request 拦截器提示 */ }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm(`确认作废物料移仓单「${detail.value.code}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/material-move/${id.value}/cancel`)
    ElMessage.success('已作废')
    sessionStorage.setItem(INVENTORY_MATERIAL_MOVE_DIRTY_KEY, '1')
    loadAll()
  } catch { /* 已由 request 拦截器提示 */ }
}
</script>

<style scoped>
/* 页头已统一到全局骨架（PageShell + styles/page.css） */
</style>
