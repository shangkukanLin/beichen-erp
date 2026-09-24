<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端=返回 → 标题 → 右端=操作（保存） -->
  <PageShell :title="isEdit ? '编辑物料移仓单' : '新增物料移仓单'" back-fallback="/inventory/material-move">
    <template #actions>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
    </template>

    <el-card shadow="never" class="query-card">
      <template #header><span style="font-weight:600">移仓信息</span></template>
      <el-form :model="form" label-width="var(--app-label-width)" size="small">
        <el-row :gutter="12">
          <el-col :span="6"><el-form-item label="移仓日期" required><el-input v-model="form.moveDate" type="date" /></el-form-item></el-col>
          <el-col :span="6">
            <el-form-item label="移出仓库" required>
              <el-select v-model="form.fromWarehouseId" filterable placeholder="我方物料仓/委外仓" style="width:100%">
                <el-option v-for="w in moveWarehouses" :key="w.id" :label="whLabel(w)" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="移入仓库" required>
              <el-select v-model="form.toWarehouseId" filterable placeholder="我方物料仓/委外仓" style="width:100%">
                <el-option v-for="w in targetWarehouses" :key="w.id" :label="whLabel(w)" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">移仓明细</span>
          <el-button type="primary" size="small" :icon="'Plus'" @click="addRow">添加物料</el-button>
        </div>
      </template>
      <el-table :data="items" border stripe size="small">
        <el-table-column label="物料" min-width="240">
          <template #default="{ row, $index }">
            <el-select v-model="row.materialId" filterable placeholder="选择物料" style="width:100%" @change="() => onMaterialChange($index)">
              <el-option v-for="m in materialOptions" :key="m.id" :label="m.materialName" :value="m.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="80" align="center">
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
        <el-table-column label="数量" width="140">
          <template #default="{ row }"><el-input v-model="row.quantity" type="number" size="small" /></template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }"><el-button type="danger" link @click="items.splice($index, 1)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <div style="padding:8px 0;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
        移仓只搬运库存、不改加权价；审核后从移出仓扣减、向移入仓增加。库存不足时审核会被拒绝（本单据不提供强制出库）。
      </div>
    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { WarehouseCategory, WarehouseType, INVENTORY_MATERIAL_MOVE_DIRTY_KEY } from '@/api/enums'
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'

defineOptions({ name: 'InventoryMaterialMoveAdd' })

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()

const editId = computed(() => (route.query.id ? Number(route.query.id) : undefined))
const isEdit = computed(() => !!editId.value)
const submitLoading = ref(false)

const form = reactive<any>({ id: undefined, moveDate: localDate(), fromWarehouseId: undefined, toWarehouseId: undefined, remark: '' })
const items = ref<any[]>([])

const allWarehouses = ref<any[]>([])
function isMaterialPool(w: any) {
  return w?.warehouseCategory === WarehouseCategory.OUTSOURCE || w?.warehouseType === WarehouseType.AUXILIARY
}
const moveWarehouses = computed(() => allWarehouses.value.filter(isMaterialPool))
const targetWarehouses = computed(() => moveWarehouses.value.filter((w: any) => w.id !== form.fromWarehouseId))
function whLabel(w: any) {
  const kind = w?.warehouseCategory === WarehouseCategory.OUTSOURCE ? '委外仓' : '我方物料仓'
  return `${w.warehouseName}（${kind}）`
}

const materialOptions = ref<any[]>([])
function unitOf(id?: number) { const m = materialOptions.value.find((x: any) => x.id === id); return m ? (m.unit || '—') : '—' }

/** 品质分级（与成品移仓同口径 A/B/C/DEFECT；仅单据留痕，不参与库存写入） */
const qualityOptions = [
  { value: 'A', label: 'A（良品）' },
  { value: 'B', label: 'B' },
  { value: 'C', label: 'C' },
  { value: 'DEFECT', label: '不良品' }
]

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
function onMaterialChange(idx: number) { items.value[idx].quantity = items.value[idx].quantity || ''; loadStock(idx) }
/** 查询该物料在移出仓的可用库存，帮助用户判断够不够（与成品移仓同口径，只做提示不做拦截） */
async function loadStock(idx: number) {
  const row = items.value[idx]
  row.stock = ''
  if (!form.fromWarehouseId || !row.materialId) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.fromWarehouseId, materialId: row.materialId, stockType: 'MATERIAL', pageSize: 100 }
    })
    const recs = r?.records || []
    row.stock = recs.reduce((s: number, it: any) => s + (Number(it.quantity) || 0), 0)
  } catch { row.stock = '' }
}

async function loadForEdit() {
  if (!editId.value) return
  const r = await request.get<any, any>(`/inventory/material-move/${editId.value}`)
  form.id = r.id
  form.fromWarehouseId = r.fromWarehouseId
  form.toWarehouseId = r.toWarehouseId
  form.moveDate = r.moveDate
  form.remark = r.remark
  const its = await request.get<any, any>(`/inventory/material-move/${editId.value}/items`)
  items.value = (its || []).map((it: any) => ({ materialId: it.materialId, qualityType: it.qualityType || 'A', quantity: it.quantity, remark: it.remark, stock: '' }))
  for (let i = 0; i < items.value.length; i++) await loadStock(i)
}

/** 未保存拦截：本页可改数据 ⇒ 接守卫（写在 form/items 之后，避免 TDZ 静默失效） */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))

onMounted(async () => {
  await loadWarehouses()
  await loadMaterials()
  if (isEdit.value) await loadForEdit()
  takeBaseline()
})

function validate(): string {
  if (!form.moveDate) return '请选择移仓日期'
  if (!form.fromWarehouseId) return '请选择移出仓库'
  if (!form.toWarehouseId) return '请选择移入仓库'
  if (form.fromWarehouseId === form.toWarehouseId) return '移出与移入仓库不能相同'
  if (!items.value.length) return '请添加移仓明细'
  for (let i = 0; i < items.value.length; i++) {
    const it = items.value[i]
    if (!it.materialId) return `第 ${i + 1} 行未选择物料`
    const q = Number(it.quantity)
    if (!Number.isFinite(q) || q <= 0) return `第 ${i + 1} 行数量必须大于 0`
  }
  return ''
}

async function handleSubmit() {
  const err = validate()
  if (err) { ElMessage.warning(err); return }
  submitLoading.value = true
  try {
    const payload = {
      move: { ...form },
      items: items.value.map((it: any) => ({ materialId: it.materialId, qualityType: it.qualityType, quantity: Number(it.quantity), remark: it.remark }))
    }
    if (isEdit.value) await request.put(`/inventory/material-move/${editId.value}`, payload)
    else await request.post('/inventory/material-move', payload)
    ElMessage.success('已保存')
    sessionStorage.setItem(INVENTORY_MATERIAL_MOVE_DIRTY_KEY, '1')
    markClean()
    tabStore.closeTabAndBack(route.path)
    router.push('/inventory/material-move')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { submitLoading.value = false }
}
</script>

<style scoped>
/* 页头/卡片内边距/分页样式均已统一到全局（PageShell + styles/page.css） */
</style>
