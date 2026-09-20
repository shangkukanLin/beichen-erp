<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import { DeliveryType, DeliveryTypeLabel, QualityType, QualityTypeLabel, WarehouseCategory, WarehouseType } from '@/api/enums'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()
const saving = ref(false)
const form = reactive({
  deliveryType: DeliveryType.DELIVERY as string, factoryId: undefined as any, supplierId: undefined as any,
  fromWarehouseId: undefined as any, toWarehouseId: undefined as any,
  // 强制出库（仅调拨）：来源仓库存不足时允许扣成负数；后端默认严格校验
  allowNegative: 0 as number,
  logisticsCompany: '', logisticsNo: '', attachUrl: '',
  deliveryDate: localDate(), contact: '', phone: '', remark: ''
})
const outsourceWarehouses = ref<any[]>([])
const targetOutsourceWarehouses = ref<any[]>([])
const inventoryWarehouses = ref<any[]>([])
const allWarehouses = ref<any[]>([])   // 全部仓库（组件本地，Odoo 风格）
const allOutsourceWarehouses = computed(() => allWarehouses.value.filter((w: any) => w.warehouseCategory === WarehouseCategory.OUTSOURCE))
/**
 * 2026-09-16 流程重构后的仓库范围：
 *  - 发料：发出仓 = **我方物料仓**（自有仓 + 辅料仓）；目标仓 = 所选工厂的委外仓
 *  - 调拨：两端都在「物料相关仓库」内互转（我方物料仓 / 委外仓），允许 委外↔委外、委外→我方、我方↔我方
 *    （我方仓 → 委外仓 属发料，后端会拦，故这里不提供该组合）
 */
const materialOwnWarehouses = computed(() =>
  inventoryWarehouses.value.filter((w: any) => w.warehouseType === WarehouseType.AUXILIARY))
const materialTransferWhs = computed(() => [...materialOwnWarehouses.value, ...allOutsourceWarehouses.value])
const targetTransferWhs = computed(() => materialTransferWhs.value.filter((w: any) => w.id !== form.fromWarehouseId))
const materialOptions = ref<any[]>([])
const materialTypes = ref<any[]>([])
const items = ref<any[]>([])
const uploadFile = ref<File | null>(null)

// Odoo 风格：工厂实时查库
const fetchFactories = (kw: string) => request.get('/supplier/page', { params: { supplierType: 'factory', pageSize: 500, name: kw } })

// 类型选择器按 materialTypeId 过滤；materialTypeId -> 类型名 映射供展示
const uniqueTypes = computed(() => [...new Set(materialOptions.value.map((m: any) => m.materialTypeId).filter(Boolean))] as number[])
function materialsByType(type: number) { return materialOptions.value.filter((m: any) => m.materialTypeId === type) }
function typeName(id: number | undefined) { if (id == null) return '-'; const t = materialTypes.value.find((v: any) => v.id === id); return t ? t.typeName : (id as any) }

async function loadWarehouses(factoryId: number) {
  try { const r = await request.get<any, any>('/warehouse/by-factory/' + factoryId); outsourceWarehouses.value = r || [] } catch { outsourceWarehouses.value = [] }
}
async function loadTargetWarehouses(factoryId: number) {
  try { const r = await request.get<any, any>('/warehouse/by-factory/' + factoryId); targetOutsourceWarehouses.value = r || [] } catch { targetOutsourceWarehouses.value = [] }
}
async function loadInventoryWarehouses() {
  // F7-129（2026-09-20）：加载失败不再静默 —— 留痕，避免"空下拉"被误认为"没有数据"
  try { const r = await request.get<any, any>('/warehouse/inventory'); inventoryWarehouses.value = r || [] } catch (e: any) { console.warn('加载仓库失败', e?.message || e) }
}
async function loadAllWarehouses() {
  try { const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500 } }); allWarehouses.value = r?.records || [] } catch { allWarehouses.value = [] }
}
async function loadMaterials() {
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); materialOptions.value = r?.records || [] } catch { materialOptions.value = [] }
}
async function loadMaterialTypes() {
  try { const r = await request.get<any, any>('/dev/material-type/enabled'); materialTypes.value = r || [] } catch { materialTypes.value = [] }
}
function onTypeChange() { form.factoryId = undefined; form.supplierId = undefined; form.fromWarehouseId = undefined; form.toWarehouseId = undefined; form.allowNegative = 0; outsourceWarehouses.value = []; targetOutsourceWarehouses.value = [] }
async function onFactoryChange(id: number) {
  form.toWarehouseId = undefined; outsourceWarehouses.value = []; targetOutsourceWarehouses.value = []
  if (!id) return
  await loadWarehouses(id)
  await loadTargetWarehouses(id)
  if (targetOutsourceWarehouses.value.length > 0) form.toWarehouseId = targetOutsourceWarehouses.value[0].id
}

function addItem() { items.value.push({ material_id: undefined, material_name: '', materialTypeId: undefined, unit: '', unit_price: '', quantity: undefined, qualityType: QualityType.GOOD, stock: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onTypeChangeMtl(idx: number) { items.value[idx].material_id = undefined; items.value[idx].material_name = ''; items.value[idx].unit = '' }
function onMaterialSelect(idx: number, mid: number) {
  const m = materialOptions.value.find((v: any) => v.id === mid)
  if (m) { items.value[idx].material_name = m.materialName; items.value[idx].materialTypeId = m.materialTypeId; items.value[idx].unit = m.unit; items.value[idx].material_id = m.id }
  // 自动查询加权平均单价
  if (form.factoryId && m) {
    request.get<any,any>('/outsource/delivery/material-weighted-price', { params: { factoryId: form.factoryId, materialId: m.id } }).then((r: any) => {
      if (r) items.value[idx].unit_price = r
    }).catch(() => {})
  }
  loadStock(idx)
}

// 查询该物料在发出仓库的库存（汇总全部质量）
async function loadStock(idx: number) {
  const row = items.value[idx]
  if (!row) return
  row.stock = ''
  if (!form.fromWarehouseId || !row.material_id) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', { params: { warehouseId: form.fromWarehouseId, materialId: row.material_id, stockType: 'MATERIAL', pageSize: 100 } })
    const recs = r?.records || []
    row.stock = recs.reduce((s: number, it: any) => s + (Number(it.quantity) || 0), 0)
  } catch { row.stock = '' }
}
watch(() => form.fromWarehouseId, () => { items.value.forEach((_: any, i: number) => loadStock(i)) })

function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }

async function handleSubmit() {
  // 2026-09-16 流程重构：只有 发料 / 调拨 两种手工单据
  if (form.deliveryType === DeliveryType.DELIVERY) {
    if (!form.factoryId) { ElMessage.warning('请选择收货工厂'); return }
    if (!form.fromWarehouseId) { ElMessage.warning('请选择发出仓库（我方物料仓）'); return }
    if (!form.toWarehouseId) { ElMessage.warning('请选择目标委外仓'); return }
  } else {
    if (!form.fromWarehouseId) { ElMessage.warning('请选择来源仓库'); return }
    if (!form.toWarehouseId) { ElMessage.warning('请选择目标仓库'); return }
    if (form.fromWarehouseId === form.toWarehouseId) { ElMessage.warning('来源仓库与目标仓库不能相同'); return }
    const from = materialTransferWhs.value.find((w: any) => w.id === form.fromWarehouseId)
    const to = materialTransferWhs.value.find((w: any) => w.id === form.toWarehouseId)
    if (from && to && from.factoryId == null && to.factoryId != null) {
      ElMessage.warning('我方物料仓 → 委外仓 请使用「发料」类型'); return
    }
  }
  if (items.value.length === 0) { ElMessage.warning('请添加物料'); return }
  const invalid = items.value.some((i: any) => !i.quantity || Number(i.quantity) <= 0)
  if (invalid) { ElMessage.warning('物料数量必须大于0'); return }
  saving.value = true
  try {
    if (uploadFile.value) { const fd = new FormData(); fd.append('file', uploadFile.value); const res = await request.post<any, string>('/dev/file/upload', fd); form.attachUrl = res as unknown as string }
    await request.post('/outsource/delivery', { ...form, items: items.value })
    ElMessage.success('收发单已确认，库存已更新')
    tabStore.removeTab(route.path)
    router.replace('/outsource/delivery')
  } finally { saving.value = false }
}

// 点击顶栏"刷新数据"：重新加载仓库/物料/类型下拉（不丢失已填表单）
async function handleRefreshData() {
  await Promise.all([loadInventoryWarehouses(), loadAllWarehouses(), loadMaterials(), loadMaterialTypes()])
}
onMounted(() => {
  loadInventoryWarehouses(); loadAllWarehouses(); loadMaterials(); loadMaterialTypes()
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))
</script>

<template>
  <div class="add-page">
    <el-card shadow="never">
      <template #header><span style="font-weight:600">基础信息</span></template>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="16">
          <!-- 2026-09-16 流程重构：手工单据只有 发料 / 调拨（收料/退不良由物料订单自动生成、退料已下线） -->
          <el-col :span="6"><el-form-item label="类型"><el-select v-model="form.deliveryType" style="width:100%" @change="onTypeChange"><el-option :label="DeliveryTypeLabel[DeliveryType.DELIVERY]" :value="DeliveryType.DELIVERY"/><el-option :label="DeliveryTypeLabel[DeliveryType.TRANSFER]" :value="DeliveryType.TRANSFER"/></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType===DeliveryType.DELIVERY"><el-form-item required label="收货工厂">
            <RemoteSelect v-model="form.factoryId" :fetch="fetchFactories" style="width:100%" placeholder="选择工厂" @pick="()=>onFactoryChange(form.factoryId)">
              <el-option label="+ 新增" :value="ADD_MARKER" @click="router.push('/supplier/manage')" />
            </RemoteSelect>
          </el-form-item></el-col>
          <el-col :span="6"><el-form-item label="日期"><el-input v-model="form.deliveryDate" type="date" /></el-form-item></el-col>
          <!-- 发料：发出仓 = 我方物料仓（自有+辅料仓）；目标仓 = 所选工厂的委外仓（可多选其一） -->
          <el-col :span="6" v-if="form.deliveryType===DeliveryType.DELIVERY"><el-form-item required label="发出仓库"><el-select v-model="form.fromWarehouseId" filterable style="width:100%"><el-option v-for="w in materialOwnWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType===DeliveryType.DELIVERY"><el-form-item required label="目标委外仓"><el-select v-model="form.toWarehouseId" style="width:100%"><el-option v-for="w in targetOutsourceWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <!-- 调拨：两端都在「物料相关仓库」内互转（委外仓↔委外仓 / 委外仓→我方物料仓 / 我方物料仓↔我方物料仓） -->
          <el-col :span="6" v-if="form.deliveryType===DeliveryType.TRANSFER"><el-form-item required label="来源仓库"><el-select v-model="form.fromWarehouseId" filterable style="width:100%"><el-option v-for="w in materialTransferWhs" :key="w.id" :label="`${w.warehouseName}（${w.factoryId?'委外仓':'我方物料仓'}）`" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType===DeliveryType.TRANSFER"><el-form-item required label="目标仓库"><el-select v-model="form.toWarehouseId" filterable style="width:100%"><el-option v-for="w in targetTransferWhs" :key="w.id" :label="`${w.warehouseName}（${w.factoryId?'委外仓':'我方物料仓'}）`" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType===DeliveryType.TRANSFER"><el-form-item label="强制出库"><el-switch v-model="form.allowNegative" :active-value="1" :inactive-value="0" /><span style="margin-left:6px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">来源仓库存不足时允许扣成负数</span></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-button type="primary" size="small" @click="addItem" style="margin-bottom:8px">+ 添加物料</el-button>
      <el-table :data="items" border size="small">
        <el-table-column label="物料类型" width="110"><template #default="{row,$index}"><el-select v-model="row.materialTypeId" filterable style="width:100%" clearable @change="onTypeChangeMtl($index)"><el-option v-for="t in uniqueTypes" :key="t" :label="typeName(t)" :value="t" /></el-select></template></el-table-column>
        <el-table-column label="物料名称" min-width="140"><template #default="{row,$index}"><el-select v-model="row.material_id" filterable style="width:100%" :disabled="!row.materialTypeId" @change="(v: any) => { if (v === ADD_MARKER) { row.material_id = undefined; router.push('/product/add'); return } onMaterialSelect($index, v) }"><el-option v-for="m in materialsByType(row.materialTypeId)" :key="m.id" :label="m.materialName" :value="m.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="{row}">{{row.unit}}</template></el-table-column>
        <el-table-column label="发出仓库库存" width="110" align="right"><template #default="{row}"><span :style="{ color: row.stock !== '' && Number(row.quantity) > Number(row.stock) ? 'var(--app-color-danger)' : undefined }">{{ row.stock === '' ? '-' : row.stock }}</span></template></el-table-column>
        <el-table-column label="单价" width="100"><template #default="{row}"><el-input v-model="row.unit_price" size="small" placeholder="单价" /></template></el-table-column>
        <el-table-column label="数量" width="120"><template #default="{row}"><el-input v-model="row.quantity" size="small" placeholder="数量" /></template></el-table-column>
        <el-table-column label="质量" width="90" align="center"><template #default="{row}"><el-select v-model="row.qualityType" size="small" style="width:100%"><el-option :label="QualityTypeLabel[QualityType.GOOD]" :value="QualityType.GOOD" /><el-option :label="QualityTypeLabel[QualityType.DEFECT]" :value="QualityType.DEFECT" /></el-select></template></el-table-column>
        <el-table-column label="操作" width="70" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">物流 & 附件</span></template>
      <el-form :model="form" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="物流公司"><el-input v-model="form.logisticsCompany" placeholder="如顺丰" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物流单号"><el-input v-model="form.logisticsNo" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'var(--app-color-success)':'var(--app-border-color)', background: uploadFile?'#f0f9eb':'#fafafa' }">
        <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:var(--app-color-success);font-weight:600">📎 {{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
        <template v-else><p style="color:var(--app-text-secondary);margin:0">拖拽文件到此处，或点击选择</p></template>
        <input type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
    </el-card>

    <div style="margin-top:16px"><el-button type="primary" size="large" :loading="saving" @click="handleSubmit">提交并确认</el-button><el-button size="large" @click="router.push('/outsource/delivery')">取消</el-button></div>
  </div>
</template>

<style scoped>
.add-page { display:flex; flex-direction:column; gap:12px; }

.drop-zone { position:relative; border:2px dashed var(--app-border-color); border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:var(--app-color-primary); background:#ecf5ff }
</style>
