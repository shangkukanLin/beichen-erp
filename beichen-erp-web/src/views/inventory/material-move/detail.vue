<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/inventory/material-move">
    <template #actions>
      <!-- 草稿态：就地编辑 + 保存（与成品移仓单一致，不再跳转新增页） -->
      <el-button v-if="isDraft" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      <el-button v-perm="'stock:material-move'" v-if="isDraft" type="success" @click="handleAudit">审核</el-button>
      <el-button v-perm="'stock:material-move'" v-if="isDraft" type="danger" @click="handleCancel">作废</el-button>
      <el-button v-perm="'stock:material-move'" v-if="isAudited" type="warning" @click="handleUnAudit">反审核</el-button>
    </template>

    <el-card shadow="never" class="query-card">
      <template #header><span style="font-weight:600">移仓信息</span></template>

      <!-- 草稿态：可编辑表单 -->
      <el-form v-if="isDraft" :model="form" label-width="var(--app-label-width)" size="small">
        <el-row :gutter="12">
          <el-col :span="6"><el-form-item label="单号"><el-input :model-value="form.code" disabled /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="移仓日期" required><el-input v-model="form.moveDate" type="date" /></el-form-item></el-col>
          <el-col :span="6">
            <el-form-item label="移出仓库" required>
              <el-select v-model="form.fromWarehouseId" filterable style="width:100%">
                <el-option v-for="w in moveWarehouses" :key="w.id" :label="whLabel(w)" :value="w.id" />
              
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/inventory/warehouse')">+ 鏂板</div></template>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="移入仓库" required>
              <el-select v-model="form.toWarehouseId" filterable style="width:100%">
                <el-option v-for="w in targetWarehouses" :key="w.id" :label="whLabel(w)" :value="w.id" />
              
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/inventory/warehouse')">+ 鏂板</div></template>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <!-- 非草稿：只读描述 -->
      <el-descriptions v-else :column="3" border size="small">
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
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">移仓明细</span>
          <el-button v-if="isDraft" type="primary" size="small" :icon="'Plus'" @click="addRow">添加物料</el-button>
        </div>
      </template>

      <!-- 草稿态：可编辑明细 -->
      <template v-if="isDraft">
        <el-table :data="items" border stripe size="small">
          <el-table-column label="物料" min-width="220">
            <template #default="{ row, $index }">
              <el-select v-model="row.materialId" filterable placeholder="选择物料" style="width:100%" @change="() => onMaterialChange($index)">
                <el-option v-for="m in materialOptions" :key="m.id" :label="m.materialName" :value="m.id" />
              
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/outsource/material-info')">+ 鏂板</div></template>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="70" align="center">
            <template #default="{ row }">{{ unitOf(row.materialId) }}</template>
          </el-table-column>
          <el-table-column label="品质" width="120">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" size="small" style="width:100%">
                <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="移出仓可用库存" width="140" align="center">
            <template #default="{ row }"><span :style="row.stock !== '' && Number(row.stock) < Number(row.quantity) ? 'color:var(--el-color-danger)' : ''">{{ row.stock === '' ? '—' : row.stock }}</span></template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="{ row }"><el-input v-model="row.quantity" type="number" size="small" /></template>
          </el-table-column>
          <el-table-column label="备注" min-width="140">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="items.splice($index, 1)">删除</el-button></template>
          </el-table-column>
        </el-table>
        <div style="padding:8px 0;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
          移仓只搬运库存、不改加权价；审核后从移出仓扣减、向移入仓增加。库存不足时审核会被拒绝（本单据不提供强制出库）。
          <br>「品质」为单据留痕：物料库存不区分品质（按良品扣减），该字段不参与库存写入。
        </div>
      </template>

      <!-- 非草稿：只读明细 -->
      <el-table v-else :data="items" border stripe size="small">
        <el-table-column label="物料" min-width="240">
          <template #default="{ row }">
            <el-link v-if="row.materialId" type="primary" underline="never" @click.stop="$router.push(`/outsource/material-stock/detail/${row.materialId}`)">{{ row.materialName || ('物料#' + row.materialId) }}</el-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="90" align="center">
          <template #default="{ row }">{{ row.unit || '—' }}</template>
        </el-table-column>
        <el-table-column label="品质" width="100" align="center">
          <template #default="{ row }"><el-tag size="small">{{ row.qualityType || 'A' }}</el-tag></template>
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
import { useUnsavedGuard } from '@/composables/usePageBack'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { WarehouseCategory, WarehouseType, INVENTORY_MATERIAL_MOVE_DIRTY_KEY } from '@/api/enums'
import { invalidate } from '@/utils/dataFreshness'

defineOptions({ name: 'InventoryMaterialMoveDetail' })

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id))
const loading = ref(false)
const saving = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
const allWarehouses = ref<any[]>([])
const materialOptions = ref<any[]>([])

/**
 * 草稿态就地编辑（2026-09-24 用户要求，与成品移仓单一致）：
 * 表单只取可编辑字段；单号/审核人/制单人等由详情接口带入，保存时只提交表单 + 明细。
 */
const form = ref<any>({ id: undefined, code: '', moveDate: '', fromWarehouseId: undefined, toWarehouseId: undefined, remark: '' })

const isDraft = computed(() => detail.value.status === DocStatus.DRAFT)
const isAudited = computed(() => detail.value.status === DocStatus.AUDITED)

/** 品质分级（与成品移仓同口径 A/B/C/DEFECT；仅单据留痕，不参与库存） */
const qualityOptions = [
  { value: 'A', label: 'A（良品）' },
  { value: 'B', label: 'B' },
  { value: 'C', label: 'C' },
  { value: 'DEFECT', label: '不良品' }
]

function isMaterialPool(w: any) {
  return w?.warehouseCategory === WarehouseCategory.OUTSOURCE || w?.warehouseType === WarehouseType.AUXILIARY
}
const moveWarehouses = computed(() => allWarehouses.value.filter(isMaterialPool))
const targetWarehouses = computed(() => moveWarehouses.value.filter((w: any) => w.id !== form.value.fromWarehouseId))
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
function unitOf(idv?: number) { const m = materialOptions.value.find((x: any) => x.id === idv); return m ? (m.unit || '—') : '—' }

async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } })
    allWarehouses.value = r?.records || []
  } catch { allWarehouses.value = [] }
}
async function loadMaterials() {
  try {
    const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } })
    materialOptions.value = r?.records || []
  } catch { materialOptions.value = [] }
}

function addRow() { items.value.push({ materialId: undefined, qualityType: 'A', quantity: '', remark: '', stock: '' }) }
function onMaterialChange(idx: number) { loadStock(idx) }
/** 查询该物料在移出仓的可用库存（与成品移仓同口径：只提示不拦截） */
async function loadStock(idx: number) {
  const row = items.value[idx]
  row.stock = ''
  if (!form.value.fromWarehouseId || !row.materialId) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.value.fromWarehouseId, materialId: row.materialId, stockType: 'MATERIAL', pageSize: 100 }
    })
    const recs = r?.records || []
    row.stock = recs.reduce((s: number, it: any) => s + (Number(it.quantity) || 0), 0)
  } catch { row.stock = '' }
}

/** 未保存拦截：仅草稿态可编辑 ⇒ 守卫只在草稿态生效（非草稿基线即刻建立，不会误报） */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form: form.value, items: items.value }))

async function loadAll() {
  loading.value = true
  try {
    const [d, its] = await Promise.all([
      request.get<any, any>(`/inventory/material-move/${id.value}`),
      request.get<any, any>(`/inventory/material-move/${id.value}/items`).catch(() => [])
    ])
    detail.value = d || {}
    form.value = {
      id: d?.id, code: d?.code || '', moveDate: d?.moveDate || '',
      fromWarehouseId: d?.fromWarehouseId, toWarehouseId: d?.toWarehouseId, remark: d?.remark || ''
    }
    items.value = (its || []).map((it: any) => ({
      materialId: it.materialId, qualityType: it.qualityType || 'A', quantity: it.quantity, remark: it.remark, stock: ''
    }))
    for (let i = 0; i < items.value.length; i++) await loadStock(i)
  } finally { loading.value = false }
  takeBaseline()
}

onActivated(async () => {
  await loadWarehouses()
  await loadMaterials()
  await loadAll()
})

function validate(): string {
  if (!form.value.moveDate) return '请选择移仓日期'
  if (!form.value.fromWarehouseId) return '请选择移出仓库'
  if (!form.value.toWarehouseId) return '请选择移入仓库'
  if (form.value.fromWarehouseId === form.value.toWarehouseId) return '移出与移入仓库不能相同'
  if (!items.value.length) return '请添加移仓明细'
  for (let i = 0; i < items.value.length; i++) {
    const it = items.value[i]
    if (!it.materialId) return `第 ${i + 1} 行未选择物料`
    const q = Number(it.quantity)
    if (!Number.isFinite(q) || q <= 0) return `第 ${i + 1} 行数量必须大于 0`
  }
  return ''
}

async function handleSave() {
  const err = validate()
  if (err) { ElMessage.warning(err); return }
  saving.value = true
  try {
    const payload = {
      move: { ...form.value },
      items: items.value.map((it: any) => ({ materialId: it.materialId, qualityType: it.qualityType, quantity: Number(it.quantity), remark: it.remark }))
    }
    await request.put(`/inventory/material-move/${id.value}`, payload)
    ElMessage.success('已保存')
    invalidate('materialMove')
    markClean()
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { saving.value = false }
}

async function handleAudit() {
  const err = validate()
  if (err) { ElMessage.warning('请先补全并保存：' + err); return }
  try {
    await ElMessageBox.confirm(`确认审核物料移仓单「${detail.value.code}」？将从移出仓扣减物料库存并增加到移入仓。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    // 草稿可能未保存就地修改 ⇒ 先保存再审核（避免"看到的与审核的不是同一份"）
    const payload = {
      move: { ...form.value },
      items: items.value.map((it: any) => ({ materialId: it.materialId, qualityType: it.qualityType, quantity: Number(it.quantity), remark: it.remark }))
    }
    await request.put(`/inventory/material-move/${id.value}`, payload)
    await request.put(`/inventory/material-move/${id.value}/audit`)
    ElMessage.success('已审核')
    invalidate('materialMove')
    markClean()
    await loadAll()
  } catch { /* 已由 request 拦截器提示 */ }
}

async function handleUnAudit() {
  try {
    await ElMessageBox.confirm(`确认反审核物料移仓单「${detail.value.code}」？将退回移出仓库存并从移入仓扣回。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/material-move/${id.value}/un-audit`)
    ElMessage.success('已反审核')
    invalidate('materialMove')
    await loadAll()
  } catch { /* 已由 request 拦截器提示 */ }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm(`确认作废物料移仓单「${detail.value.code}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/material-move/${id.value}/cancel`)
    ElMessage.success('已作废')
    invalidate('materialMove')
    markClean()
    await loadAll()
  } catch { /* 已由 request 拦截器提示 */ }
}
</script>

<style scoped>
/* 页头已统一到全局骨架（PageShell + styles/page.css） */
</style>
