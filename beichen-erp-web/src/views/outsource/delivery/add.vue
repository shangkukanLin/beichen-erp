<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()
const saving = ref(false)
const form = reactive({
  deliveryType: '发料', factoryId: undefined as any, supplierId: undefined as any,
  fromWarehouseId: undefined as any, toWarehouseId: undefined as any,
  logisticsCompany: '', logisticsNo: '', attachUrl: '',
  deliveryDate: new Date().toISOString().split('T')[0], contact: '', phone: '', remark: ''
})
const factoryOptions = ref<any[]>([])
const outsourceWarehouses = ref<any[]>([])
const targetOutsourceWarehouses = ref<any[]>([])
const inventoryWarehouses = ref<any[]>([])
const allOutsourceWarehouses = ref<any[]>([])
const combinedFromWhs = computed(() => [...inventoryWarehouses.value, ...allOutsourceWarehouses.value])
const materialOptions = ref<any[]>([])
const items = ref<any[]>([])
const uploadFile = ref<File | null>(null)

const uniqueTypes = computed(() => [...new Set(materialOptions.value.map((m: any) => m.materialType).filter(Boolean))] as string[])
function materialsByType(type: string) { return materialOptions.value.filter((m: any) => m.materialType === type) }

async function loadAllOutsourceWarehouses() {
  try { const r = await request.get<any,any>('/outsource/warehouse/page',{params:{pageSize:500}}); allOutsourceWarehouses.value = r?.records||[] } catch {}
}
async function loadFactories() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType:'factory', pageSize:200 } }); factoryOptions.value = r?.records || [] } catch {}
}
async function loadWarehouses(factoryId: number) {
  try { const r = await request.get<any, any>('/outsource/delivery/warehouses/by-factory/' + factoryId); outsourceWarehouses.value = r || [] } catch { outsourceWarehouses.value = [] }
}
async function loadTargetWarehouses(factoryId: number) {
  try { const r = await request.get<any, any>('/outsource/delivery/warehouses/by-factory/' + factoryId); targetOutsourceWarehouses.value = r || [] } catch { targetOutsourceWarehouses.value = [] }
}
async function loadInventoryWarehouses() {
  try { const r = await request.get<any, any>('/outsource/delivery/warehouses/inventory'); inventoryWarehouses.value = r || [] } catch {}
}
async function loadMaterials() {
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize:500 } }); materialOptions.value = r?.records || [] } catch {}
}

function onTypeChange() { form.factoryId = undefined; form.supplierId = undefined; form.fromWarehouseId = undefined; form.toWarehouseId = undefined; outsourceWarehouses.value = []; targetOutsourceWarehouses.value = [] }
async function onFactoryChange(id: number) {
  form.fromWarehouseId = undefined; form.toWarehouseId = undefined; outsourceWarehouses.value = []
  if (id) { await loadWarehouses(id); if (outsourceWarehouses.value.length > 0) {
    if (form.deliveryType === '发料') form.toWarehouseId = outsourceWarehouses.value[0].id
    else form.fromWarehouseId = outsourceWarehouses.value[0].id
  }}
}
async function onTargetFactoryChange(id: number) {
  form.toWarehouseId = undefined; targetOutsourceWarehouses.value = []
  if (id) { await loadTargetWarehouses(id); if (targetOutsourceWarehouses.value.length > 0) form.toWarehouseId = targetOutsourceWarehouses.value[0].id }
}

function addItem() { items.value.push({ material_id: undefined, material_name: '', material_type: '', unit: '', unit_price: '', quantity: undefined, qualityType: '良品' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onTypeChangeMtl(idx: number) { items.value[idx].material_id = undefined; items.value[idx].material_name = ''; items.value[idx].unit = '' }
function onMaterialSelect(idx: number, mid: number) {
  const m = materialOptions.value.find((v: any) => v.id === mid)
  if (m) { items.value[idx].material_name = m.materialName; items.value[idx].material_type = m.materialType; items.value[idx].unit = m.unit; items.value[idx].material_id = m.id }
  // 自动查询加权平均单价
  if (form.factoryId && m) {
    request.get<any,any>('/outsource/delivery/material-weighted-price', { params: { factoryId: form.factoryId, materialName: m.materialName } }).then((r: any) => {
      if (r) items.value[idx].unit_price = r
    }).catch(() => {})
  }
}

function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }

async function handleSubmit() {
  if (!form.factoryId) { ElMessage.warning(form.deliveryType === '调拨' ? '请选择来源工厂' : '请选择工厂'); return }
  if (form.deliveryType === '调拨' && !form.supplierId) { ElMessage.warning('请选择目标工厂'); return }
  if (form.deliveryType === '发料' && !form.fromWarehouseId) { ElMessage.warning('请选择发出仓库'); return }
  if (form.deliveryType === '发料' && !form.toWarehouseId) { ElMessage.warning('请选择目标仓库'); return }
  if (form.deliveryType !== '发料' && !form.fromWarehouseId) { ElMessage.warning('请选择来源仓库'); return }
  if (form.deliveryType === '调拨' && !form.toWarehouseId) { ElMessage.warning('请选择目标仓库'); return }
  if (items.value.length === 0) { ElMessage.warning('请添加物料'); return }
  const invalid = items.value.some((i: any) => !i.quantity || Number(i.quantity) <= 0)
  if (invalid) { ElMessage.warning('物料数量必须大于0'); return }
  saving.value = true
  try {
    if (uploadFile.value) { const fd = new FormData(); fd.append('file', uploadFile.value); const res = await request.post<any, string>('/dev/file/upload', fd); form.attachUrl = res as unknown as string }
    await request.post('/outsource/delivery', { ...form, items: items.value })
    ElMessage.success('收发单已确认，库存已更新')
    ;(window as any).__deliveryNeedRefresh = true
    tabStore.removeTab(route.path)
    router.replace('/outsource/delivery')
  } finally { saving.value = false }
}

onMounted(() => { loadFactories(); loadAllOutsourceWarehouses(); loadMaterials(); loadInventoryWarehouses() })
</script>

<template>
  <div class="add-page">
    <div class="page-header"><span class="page-title">新增物料收发单</span></div>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">基础信息</span></template>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="6"><el-form-item label="类型"><el-select v-model="form.deliveryType" style="width:100%" @change="onTypeChange"><el-option label="发料" value="发料"/><el-option label="退料" value="退料"/><el-option label="调拨" value="调拨"/></el-select></el-form-item></el-col>
          <el-col :span="6"><el-form-item :label="form.deliveryType==='调拨'?'来源工厂':'收货工厂'"><el-select v-model="form.factoryId" filterable style="width:100%" @change="(v:any)=>{if(v===ADD_MARKER){form.factoryId=undefined;router.push('/supplier/manage');return};onFactoryChange(v)}"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType==='调拨'"><el-form-item label="目标工厂"><el-select v-model="form.supplierId" filterable style="width:100%" @change="(v:any)=>{if(v===ADD_MARKER){form.supplierId=undefined;router.push('/supplier/manage');return};onTargetFactoryChange(v)}"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="日期"><el-input v-model="form.deliveryDate" type="date" /></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType==='发料'"><el-form-item label="发出仓库"><el-select v-model="form.fromWarehouseId" filterable style="width:100%"><el-option v-for="w in combinedFromWhs" :key="w.id+'_'+w.warehouseName" :label="`${w.warehouseName}（${w.factoryId?'委外仓':'我方仓'}）`" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType==='发料'"><el-form-item label="目标委外仓库"><el-select v-model="form.toWarehouseId" style="width:100%" disabled><el-option v-for="w in outsourceWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType==='退料'"><el-form-item label="来源仓库"><el-select v-model="form.fromWarehouseId" filterable style="width:100%"><el-option v-for="w in combinedFromWhs" :key="w.id+'_'+w.warehouseName" :label="`${w.warehouseName}（${w.factoryId?'委外仓':'我方仓'}）`" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType==='退料'"><el-form-item label="目标仓库"><el-select v-model="form.toWarehouseId" filterable style="width:100%"><el-option v-for="w in combinedFromWhs" :key="w.id+'_'+w.warehouseName" :label="`${w.warehouseName}（${w.factoryId?'委外仓':'我方仓'}）`" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType==='调拨'"><el-form-item label="来源仓库"><el-select v-model="form.fromWarehouseId" filterable style="width:100%"><el-option v-for="w in combinedFromWhs" :key="w.id+'_'+w.warehouseName" :label="`${w.warehouseName}（${w.factoryId?'委外仓':'我方仓'}）`" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="6" v-if="form.deliveryType==='调拨'"><el-form-item label="目标仓库"><el-select v-model="form.toWarehouseId" filterable style="width:100%"><el-option v-for="w in combinedFromWhs" :key="w.id+'_'+w.warehouseName" :label="`${w.warehouseName}（${w.factoryId?'委外仓':'我方仓'}）`" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-button type="primary" size="small" @click="addItem" style="margin-bottom:8px">+ 添加物料</el-button>
      <el-table :data="items" border size="small">
        <el-table-column label="物料类型" width="110"><template #default="{row,$index}"><el-select v-model="row.material_type" filterable style="width:100%" clearable @change="onTypeChangeMtl($index)"><el-option v-for="t in uniqueTypes" :key="t" :label="t" :value="t" /></el-select></template></el-table-column>
        <el-table-column label="物料名称" min-width="140"><template #default="{row,$index}"><el-select v-model="row.material_id" filterable style="width:100%" :disabled="!row.material_type" @change="(v: any) => { if (v === ADD_MARKER) { row.material_id = undefined; router.push('/material'); return } onMaterialSelect($index, v) }"><el-option v-for="m in materialsByType(row.material_type)" :key="m.id" :label="m.materialName" :value="m.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="{row}">{{row.unit}}</template></el-table-column>
        <el-table-column label="单价" width="100"><template #default="{row}"><el-input v-model="row.unit_price" size="small" placeholder="单价" /></template></el-table-column>
        <el-table-column label="数量" width="120"><template #default="{row}"><el-input v-model="row.quantity" size="small" placeholder="数量" /></template></el-table-column>
        <el-table-column label="质量" width="90" align="center"><template #default="{row}"><el-select v-model="row.qualityType" size="small" style="width:100%"><el-option label="良品" value="良品" /><el-option label="不良品" value="不良品" /></el-select></template></el-table-column>
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
      <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
        <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">📎 {{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
        <template v-else><p style="color:#909399;margin:0">拖拽文件到此处，或点击选择</p></template>
        <input type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
    </el-card>

    <div style="margin-top:16px"><el-button type="primary" size="large" :loading="saving" @click="handleSubmit">提交并确认</el-button><el-button size="large" @click="router.push('/outsource/delivery')">取消</el-button></div>
  </div>
</template>

<style scoped>
.add-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:#409eff; background:#ecf5ff }
</style>
