<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const route = useRoute(); const router = useRouter()
const loading = ref(true); const saving = ref(false)
const uploadFile = ref<File | null>(null)

const form = reactive({ id: undefined as any, code: '', deliveryType: '发料', factoryId: undefined as any, fromWarehouseId: undefined as any, toWarehouseId: undefined as any, supplierDirect: 0, supplierId: undefined as any, logisticsCompany: '', logisticsNo: '', deliveryDate: '', contact: '', phone: '', remark: '', attachUrl: '', status: '' })
const items = ref<any[]>([])

const factoryOptions = ref<any[]>([]); const supplierOptions = ref<any[]>([])
const outsourceWarehouses = ref<any[]>([]); const inventoryWarehouses = ref<any[]>([]); const materialOptions = ref<any[]>([])
const uniqueTypes = computed(() => [...new Set(materialOptions.value.map((m: any) => m.materialType).filter(Boolean))] as string[])
function materialsByType(type: string) { return materialOptions.value.filter((m: any) => m.materialType === type) }

async function loadOptions() {
  try { const r=await request.get<any,any>('/supplier/page',{params:{supplierType:'factory',pageSize:200}}); factoryOptions.value=r?.records||[] } catch (e: any) { console.warn('加载工厂失败', e?.message || e) }
  try { const r=await request.get<any,any>('/supplier/page',{params:{pageSize:500}}); supplierOptions.value=r?.records||[] } catch (e: any) { console.warn('加载供应商失败', e?.message || e) }
  try { const r=await request.get<any,any>('/outsource/delivery/warehouses/inventory'); inventoryWarehouses.value=r||[] } catch (e: any) { console.warn('加载进销存仓库失败', e?.message || e) }
  try { const r=await request.get<any,any>('/outsource/material/page',{params:{pageSize:500}}); materialOptions.value=r?.records||[] } catch (e: any) { console.warn('加载物料失败', e?.message || e) }
}

async function loadData() {
  loading.value = true
  const d = await request.get<any,any>(`/outsource/delivery/${route.params.id}`)
  items.value = (await request.get<any,any>(`/outsource/delivery/${route.params.id}/items`) || []).map((i:any)=>({...i, material_id: i.materialId, material_name: i.materialName, material_type: i.materialType}))
  Object.assign(form, { id:d.id, code:d.code, deliveryType:d.deliveryType, factoryId:d.factoryId, fromWarehouseId:d.fromWarehouseId, toWarehouseId:d.toWarehouseId, supplierDirect:d.supplierDirect||0, supplierId:d.supplierId, logisticsCompany:d.logisticsCompany||'', logisticsNo:d.logisticsNo||'', deliveryDate:d.deliveryDate, contact:d.contact||'', phone:d.phone||'', remark:d.remark||'', attachUrl:d.attachUrl||'', status:d.status })
  if (form.factoryId) await loadOutsourceWarehouses(form.factoryId)
  // 补丁：确保选项列表包含当前值
  if (form.factoryId && !factoryOptions.value.some((f:any)=>f.id===form.factoryId) && d.factoryName) factoryOptions.value.push({id:form.factoryId, name:d.factoryName})
  if (form.supplierId && !supplierOptions.value.some((s:any)=>s.id===form.supplierId) && d.supplierName) supplierOptions.value.push({id:form.supplierId, name:d.supplierName})
  if (form.fromWarehouseId && !inventoryWarehouses.value.some((w:any)=>w.id===form.fromWarehouseId) && d.fromWarehouseName) inventoryWarehouses.value.push({id:form.fromWarehouseId, warehouseName:d.fromWarehouseName})
  if (form.toWarehouseId && !outsourceWarehouses.value.some((w:any)=>w.id===form.toWarehouseId) && d.toWarehouseName) outsourceWarehouses.value.push({id:form.toWarehouseId, warehouseName:d.toWarehouseName})
  loading.value = false
}

async function loadOutsourceWarehouses(fid:number){ try{const r=await request.get<any,any>('/outsource/delivery/warehouses/by-factory/'+fid);outsourceWarehouses.value=r||[]}catch(e: any){ console.warn('加载委外仓库失败', e?.message || e) } }
async function onFactoryChange(fid:number){ form.fromWarehouseId=undefined;form.toWarehouseId=undefined;await loadOutsourceWarehouses(fid);if(outsourceWarehouses.value.length>0){form.toWarehouseId=outsourceWarehouses.value[0].id} }

function addItem(){ items.value.push({material_id:undefined,material_name:'',material_type:'',unit:'',quantity:undefined,qualityType:'良品'}) }
function removeItem(i:number){ items.value.splice(i,1) }
function onTypeChange(idx:number){ items.value[idx].material_id=undefined;items.value[idx].material_name='';items.value[idx].unit='' }
function onMatSelect(idx:number,mid:number){ const m=materialOptions.value.find((v:any)=>v.id===mid); if(m){items.value[idx].material_name=m.materialName;items.value[idx].material_type=m.materialType;items.value[idx].unit=m.unit} }

async function handleSave() {
  if (!form.factoryId) { ElMessage.warning('请选择收货工厂'); return }
  if (items.value.length === 0) { ElMessage.warning('请添加物料'); return }
  const invalid = items.value.some((i: any) => !i.quantity || Number(i.quantity) <= 0)
  if (invalid) { ElMessage.warning('物料数量必须大于0'); return }
  saving.value = true
  try {
    if (uploadFile.value) { const fd = new FormData(); fd.append('file', uploadFile.value); const res = await request.post<any,string>('/dev/file/upload', fd); form.attachUrl = res as unknown as string }
    const body = { ...form, items: items.value }
    await request.put(`/outsource/delivery/${form.id}`, body)
    ElMessage.success('保存成功，库存已同步'); loadData()
  } finally { saving.value = false }
}

function openAttach(url:string){ window.open(url + '?inline=true') }

function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }
async function handleDeleteAttach() {
  try {
    await ElMessageBox.confirm('确定删除附件吗？删除后将无法恢复。', '删除附件', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await request.delete(`/outsource/delivery/${form.id}/attach`)
    ElMessage.success('附件已删除')
    await loadData()
  } catch (e: any) { /* 取消 */ }
}

onMounted(()=>{ loadOptions(); loadData() })
</script>

<template>
  <div class="detail-page">
    <div class="page-header">
      <el-button @click="router.push('/outsource/delivery')">返回列表</el-button>
      <span class="page-title">{{ form.code || '收发单详情' }}</span>
      <el-tag :type="form.status==='已确认'?'success':'info'" size="small">{{ form.status }}</el-tag>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="类型"><el-select v-model="form.deliveryType" style="width:100%"><el-option label="发料" value="发料"/><el-option label="收料" value="收料"/><el-option label="退料" value="退料"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="收货工厂"><el-select v-model="form.factoryId" filterable style="width:100%" @change="(v:any)=>onFactoryChange(v)"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="日期"><el-input v-model="form.deliveryDate" type="date" /></el-form-item></el-col>
          <el-col :span="8" v-if="form.deliveryType==='发料'"><el-form-item label="供应商直发"><el-switch v-model="form.supplierDirect" :active-value="1" :inactive-value="0" /></el-form-item></el-col>
          <el-col :span="8" v-if="form.deliveryType==='发料' && form.supplierDirect"><el-form-item label="供应商"><el-select v-model="form.supplierId" filterable style="width:100%"><el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item></el-col>
          <el-col :span="8" v-if="form.deliveryType!=='发料' || !form.supplierDirect"><el-form-item label="来源仓库"><el-select v-model="form.fromWarehouseId" filterable style="width:100%"><el-option v-for="w in inventoryWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="8" v-if="form.deliveryType==='发料'"><el-form-item label="目标仓库"><el-select v-model="form.toWarehouseId" filterable style="width:100%" disabled><el-option v-for="w in outsourceWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 物料明细 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-button type="primary" size="small" @click="addItem" style="margin-bottom:8px">+ 添加物料</el-button>
      <el-table :data="items" border size="small">
        <el-table-column label="物料类型" width="110"><template #default="{row,$index}"><el-select v-model="row.material_type" filterable style="width:100%" clearable @change="onTypeChange($index)"><el-option v-for="t in uniqueTypes" :key="t" :label="t" :value="t" /></el-select></template></el-table-column>
        <el-table-column label="物料名称" min-width="130"><template #default="{row,$index}"><el-select v-model="row.material_id" filterable style="width:100%" :disabled="!row.material_type" @change="(v:any)=>onMatSelect($index,v)"><el-option v-for="m in materialsByType(row.material_type)" :key="m.id" :label="m.materialName" :value="m.id" /></el-select></template></el-table-column>
        <el-table-column label="单位" width="60"><template #default="{row}">{{row.unit}}</template></el-table-column>
        <el-table-column label="数量" width="100"><template #default="{row}"><el-input v-model="row.quantity" size="small" /></template></el-table-column>
        <el-table-column label="质量" width="90" align="center"><template #default="{row}"><el-select v-model="row.qualityType" size="small" style="width:100%"><el-option label="良品" value="良品" /><el-option label="不良品" value="不良品" /></el-select></template></el-table-column>
        <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <!-- 物流信息 & 附件 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">物流信息 & 附件</span></template>
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="物流公司"><el-input v-model="form.logisticsCompany" placeholder="如顺丰" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物流单号"><el-input v-model="form.logisticsNo" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
        <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">📎 {{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
        <template v-else-if="form.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:#409eff">📎 已有附件</span><el-button type="primary" size="small" @click.stop="openAttach(form.attachUrl)">查看</el-button><el-button type="success" size="small"><a :href="form.attachUrl" download style="color:inherit;text-decoration:none">下载</a></el-button><el-button type="danger" size="small" @click.stop="handleDeleteAttach">删除</el-button><span style="color:#909399;font-size:12px">可拖拽新文件替换</span></div></template>
        <template v-else><p style="color:#909399;margin:0">拖拽文件到此处，或点击选择</p></template>
        <input v-if="!form.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
    </el-card>

    <div style="margin-top:16px;display:flex;justify-content:flex-end"><el-button type="primary" size="large" :loading="saving" @click="handleSave">保存并同步库存</el-button></div>
  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:#409eff; background:#ecf5ff }
</style>
