<script setup lang="ts">
import { localDate } from '@/utils/date'
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { IoType, IoTypeLabel, WarehouseCategory, OUTSOURCE_OTHER_IO_DIRTY_KEY } from '@/api/enums'
import { DocStatus } from '@/api/common'

const route = useRoute(); const router = useRouter()
const editId = Number(route.params.id) || 0
const warehouses = ref<any[]>([])
const materialOptions = ref<any[]>([])
const materialTypes = ref<any[]>([])
const saving = ref(false)
const loading = ref(false)
const form = reactive({ warehouseId: undefined as any, ioType: IoType.IN, ioDate: localDate(), remark: '' })
const items = ref<any[]>([])

async function loadWarehouses() {
  try { const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500 } }); warehouses.value = (r?.records || []).map((w: any) => ({ ...w, _type: w.warehouseCategory === WarehouseCategory.INVENTORY ? '我方仓' : '委外仓' })) } catch { warehouses.value = [] }
}
async function loadMaterials() {
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); materialOptions.value = r?.records || [] } catch { materialOptions.value = [] }
}
async function loadMaterialTypes() {
  try { const r = await request.get<any, any>('/dev/material-type/enabled'); materialTypes.value = r || [] } catch { materialTypes.value = [] }
}
const uniqueTypes = computed(() => [...new Set(materialOptions.value.map((m: any) => m.materialTypeId).filter(Boolean))] as number[])
function materialsByType(type: number) { return materialOptions.value.filter((m: any) => m.materialTypeId === type) }
function typeName(id: number | undefined) { if (id == null) return '-'; const t = materialTypes.value.find((v: any) => v.id === id); return t ? t.typeName : (id as any) }

function onTypeChange(idx: number) { items.value[idx].materialId = undefined; items.value[idx].unit = ''; items.value[idx].unit_price = '' }
function onMatSelect(idx: number, matId: number) {
  const m = materialOptions.value.find((v: any) => v.id === matId)
  if (m) { items.value[idx].materialTypeId = m.materialTypeId; items.value[idx].unit = m.unit }
  // 委外仓选物料自动查加权平均单价
  if (m && form.warehouseId) {
    const wh = warehouses.value.find((w: any) => w.id === form.warehouseId)
    if (wh?._type === '委外仓' && wh?.factoryId) {
      request.get<any, any>('/outsource/delivery/material-weighted-price', { params: { factoryId: wh.factoryId, materialId: m.id } }).then((r: any) => {
        if (r) items.value[idx].unit_price = r
      }).catch(() => {})
    }
  }
}
function addItem() { items.value.push({ materialId: undefined, materialTypeId: undefined, unit: '', unit_price: '', quantity: undefined, remark: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }

async function loadDetail() {
  if (!editId) { ElMessage.error('缺少单据ID'); router.push('/outsource/other-io'); return }
  loading.value = true
  try {
    const io = await request.get<any, any>(`/outsource/other-io/${editId}`)
    if (!io) { ElMessage.error('单据不存在'); router.push('/outsource/other-io'); return }
    if (io.status !== DocStatus.DRAFT) { ElMessage.warning('仅草稿状态可编辑'); router.push(`/outsource/other-io/detail/${editId}`); return }
    form.warehouseId = io.warehouseId; form.ioType = io.ioType
    form.ioDate = io.ioDate; form.remark = io.remark || ''
    const its = await request.get<any, any>(`/outsource/other-io/${editId}/items`)
    items.value = Array.isArray(its)
      ? its.map((i: any) => ({ materialId: i.materialId, materialTypeId: i.materialTypeId, unit: i.unit, unit_price: i.unitPrice ?? '', quantity: i.quantity, remark: i.remark || '' }))
      : []
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}

async function handleSubmit() {
  if (!form.warehouseId) { ElMessage.warning('请选择仓库'); return }
  const validItems = items.value.filter((i: any) => i.quantity && Number(i.quantity) > 0)
  if (validItems.length === 0) { ElMessage.warning('请添加物料明细'); return }
  saving.value = true
  try {
    const body: any = { ...form, items: validItems }
    await request.put(`/outsource/other-io/${editId}`, body)
    ElMessage.success('已更新'); sessionStorage.setItem(OUTSOURCE_OTHER_IO_DIRTY_KEY, '1')
    router.push('/outsource/other-io')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

// 顶栏"刷新数据"：重新加载仓库/物料/类型下拉
async function handleRefreshData() { await Promise.all([loadWarehouses(), loadMaterials(), loadMaterialTypes()]) }
onMounted(() => { loadWarehouses(); loadMaterials(); loadMaterialTypes(); loadDetail(); window.addEventListener('refresh:dropdown-data', handleRefreshData) })
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never" v-loading="loading">
      <el-form :model="form" label-width="80px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item required label="仓库"><el-select v-model="form.warehouseId" filterable style="width:100%"><el-option v-for="w in warehouses" :key="w.id+'@'+w._type" :label="`${w.warehouseName}（${w._type}）`" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="类型"><el-select v-model="form.ioType" style="width:100%"><el-option :label="IoTypeLabel[IoType.IN]" :value="IoType.IN"/><el-option :label="IoTypeLabel[IoType.OUT]" :value="IoType.OUT"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="日期"><el-input v-model="form.ioDate" type="date"/></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注"/></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-button type="primary" size="small" @click="addItem" style="margin-bottom:8px">+ 添加物料</el-button>
      <el-table :data="items" border size="small">
        <el-table-column label="物料类型" width="130"><template #default="{row,$index}"><el-select v-model="row.materialTypeId" filterable style="width:100%" clearable @change="onTypeChange($index)"><el-option v-for="t in uniqueTypes" :key="t" :label="typeName(t)" :value="t"/></el-select></template></el-table-column>
        <el-table-column label="物料名称" min-width="160"><template #default="{row,$index}"><el-select v-model="row.materialId" filterable style="width:100%" :disabled="!row.materialTypeId" @change="(v:any)=>onMatSelect($index,v)"><el-option v-for="m in materialsByType(row.materialTypeId)" :key="m.id" :label="m.materialName" :value="m.id"/></el-select></template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="{row}">{{ row.unit }}</template></el-table-column>
        <el-table-column label="单价" width="110"><template #default="{row}"><el-input v-model="row.unit_price" size="small" placeholder="单价"/></template></el-table-column>
        <el-table-column label="数量" width="110"><template #default="{row}"><el-input v-model="row.quantity" size="small" type="number"/></template></el-table-column>
        <el-table-column label="操作" width="70" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <div style="display:flex;gap:12px;justify-content:center"><el-button @click="router.push('/outsource/other-io')">取消</el-button><el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button></div>
  </div>
</template>
