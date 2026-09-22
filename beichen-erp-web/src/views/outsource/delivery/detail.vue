<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DeliveryType, DeliveryTypeLabel, QualityType, QualityTypeLabel, WarehouseCategory, WarehouseType, OUTSOURCE_DELIVERY_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

const route = useRoute(); const router = useRouter()
const loading = ref(true); const saving = ref(false)
const uploadFile = ref<File | null>(null)

const form = reactive({ id: undefined as any, code: '', deliveryType: DeliveryType.DELIVERY as string, factoryId: undefined as any, factoryName: '', supplierId: undefined as any, supplierName: '', fromWarehouseId: undefined as any, toWarehouseId: undefined as any, fromWarehouseName: '', toWarehouseName: '', supplierDirect: 0, allowNegative: 0, logisticsCompany: '', logisticsNo: '', deliveryDate: '', contact: '', phone: '', remark: '', attachUrl: '', status: '', createByName: '', auditorName: '' })
/** 手工单据（发料/调拨）才允许编辑字段；收料/退不良为自动单据、退料已下线（仅历史查看） */
const isManualType = computed(() => form.deliveryType === DeliveryType.DELIVERY || form.deliveryType === DeliveryType.TRANSFER)
const items = ref<any[]>([])

// Odoo 风格：工厂 / 供应商实时查库
const fetchFactories = (kw: string) => request.get('/supplier/page', { params: { supplierType: 'factory', pageSize: 500, name: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
const outsourceWarehouses = ref<any[]>([]); const allWarehouses = ref<any[]>([]); const materialOptions = ref<any[]>([])
/**
 * 2026-09-16 流程重构后的可选仓库范围（与新增页一致）：
 * 发料 = 我方物料仓 → 该厂委外仓；调拨 = 物料相关仓库（我方物料仓 / 委外仓）之间互转
 */
const materialOwnWarehouses = computed(() => allWarehouses.value.filter((w: any) => w.warehouseCategory === WarehouseCategory.INVENTORY && w.warehouseType === WarehouseType.AUXILIARY))
const allOutsourceWarehouses = computed(() => allWarehouses.value.filter((w: any) => w.warehouseCategory === WarehouseCategory.OUTSOURCE))
const materialTransferWhs = computed(() => [...materialOwnWarehouses.value, ...allOutsourceWarehouses.value])
const targetTransferWhs = computed(() => materialTransferWhs.value.filter((w: any) => w.id !== form.fromWarehouseId))
const materialTypes = ref<any[]>([])
const uniqueTypes = computed(() => [...new Set(materialOptions.value.map((m: any) => m.materialTypeId).filter(Boolean))] as number[])
function materialsByType(type: number) { return materialOptions.value.filter((m: any) => m.materialTypeId === type) }
/** 行内下拉选项：按类型过滤；若当前已选物料不在其中（历史数据类型缺失等），附加该物料，避免 el-select 显示数字 ID */
function optionsForRow(row: any) {
  const base = materialsByType(row.materialTypeId)
  if (row.material_id && !base.some((m: any) => m.id === row.material_id)) {
    const cur = materialOptions.value.find((m: any) => m.id === row.material_id)
    if (cur) return [...base, cur]
  }
  return base
}
function typeName(id: number | undefined) { if (id == null) return '-'; const t = materialTypes.value.find((v: any) => v.id === id); return t ? t.typeName : (id as any) }

async function loadOptions() {
  try { const r=await request.get<any,any>('/warehouse/page',{params:{pageSize:500}}); allWarehouses.value=r?.records||[] } catch (e: any) { console.warn('加载仓库失败', e?.message || e) }
  try { const r=await request.get<any,any>('/outsource/material/page',{params:{pageSize:500}}); materialOptions.value=r?.records||[] } catch (e: any) { console.warn('加载物料失败', e?.message || e) }
  try { const r=await request.get<any,any>('/dev/material-type/enabled'); materialTypes.value=r||[] } catch (e: any) { console.warn('加载物料类型失败', e?.message || e) }
}

async function loadData() {
  // F7-134（2026-09-20）：原实现**无 try/finally**，`loading.value = false` 直接写在函数末尾 ⇒
  // 任一请求抛错时 `loading` 永远为 true ⇒ **页面一直转圈**（同模块 delivery/add.vue 均用 finally）。
  loading.value = true
  try {
    const d = await request.get<any,any>(`/outsource/delivery/${route.params.id}`)
    items.value = (await request.get<any,any>(`/outsource/delivery/${route.params.id}/items`) || []).map((i:any)=>({...i, material_id: i.materialId, material_name: i.materialName, materialTypeId: i.materialTypeId}))
    Object.assign(form, { id:d.id, code:d.code, deliveryType:d.deliveryType, factoryId:d.factoryId, factoryName:d.factoryName||'', supplierId:d.supplierId, supplierName:d.supplierName||'', fromWarehouseId:d.fromWarehouseId, toWarehouseId:d.toWarehouseId, fromWarehouseName:d.fromWarehouseName||'', toWarehouseName:d.toWarehouseName||'', supplierDirect:d.supplierDirect||0, allowNegative:d.allowNegative||0, logisticsCompany:d.logisticsCompany||'', logisticsNo:d.logisticsNo||'', deliveryDate:d.deliveryDate, contact:d.contact||'', phone:d.phone||'', remark:d.remark||'', attachUrl:d.attachUrl||'', status:d.status })
    if (form.factoryId) await loadOutsourceWarehouses(form.factoryId)
    // 补丁：确保选项列表包含当前值（本地 el-select 用；历史单据的仓库可能不在新范围内）
    if (form.fromWarehouseId && !allWarehouses.value.some((w:any)=>w.id===form.fromWarehouseId) && d.fromWarehouseName)
      allWarehouses.value.push({id:form.fromWarehouseId, warehouseName:d.fromWarehouseName, factoryId:d.factoryId ?? null, warehouseCategory:null, warehouseType:null})
    if (form.toWarehouseId && !allWarehouses.value.some((w:any)=>w.id===form.toWarehouseId) && d.toWarehouseName)
      allWarehouses.value.push({id:form.toWarehouseId, warehouseName:d.toWarehouseName, factoryId:d.factoryId ?? null, warehouseCategory:null, warehouseType:null})
  } finally { loading.value = false }
}

async function loadOutsourceWarehouses(fid:number){ try{const r=await request.get<any,any>('/warehouse/by-factory/'+fid);outsourceWarehouses.value=r||[]}catch(e: any){ console.warn('加载委外仓库失败', e?.message || e) } }
async function onFactoryChange(fid:number){ form.fromWarehouseId=undefined;form.toWarehouseId=undefined;await loadOutsourceWarehouses(fid);if(outsourceWarehouses.value.length>0){form.toWarehouseId=outsourceWarehouses.value[0].id} }

// 非草稿（已审核/已作废）只读，仅草稿可编辑
const readonly = computed(() => form.status !== DocStatus.DRAFT)

function addItem(){ items.value.push({material_id:undefined,material_name:'',materialTypeId:undefined,unit:'',quantity:undefined,qualityType:QualityType.GOOD}) }
function removeItem(i:number){ items.value.splice(i,1) }
function onTypeChange(idx:number){ items.value[idx].material_id=undefined;items.value[idx].material_name='';items.value[idx].unit='' }
function onMatSelect(idx:number,mid:number){ const m=materialOptions.value.find((v:any)=>v.id===mid); if(m){items.value[idx].material_name=m.materialName;items.value[idx].materialTypeId=m.materialTypeId;items.value[idx].unit=m.unit} }

async function handleSave() {
  if (form.deliveryType === DeliveryType.DELIVERY && !form.factoryId) { ElMessage.warning('请选择收货工厂'); return }
  if (!form.fromWarehouseId) { ElMessage.warning('请选择发出/来源仓库'); return }
  if (!form.toWarehouseId) { ElMessage.warning('请选择目标仓库'); return }
  if (form.fromWarehouseId === form.toWarehouseId) { ElMessage.warning('来源仓库与目标仓库不能相同'); return }
  if (items.value.length === 0) { ElMessage.warning('请添加物料'); return }
  const invalid = items.value.some((i: any) => !i.quantity || Number(i.quantity) <= 0)
  if (invalid) { ElMessage.warning('物料数量必须大于0'); return }
  saving.value = true
  try {
    if (uploadFile.value) { const fd = new FormData(); fd.append('file', uploadFile.value); const res = await request.post<any,string>('/dev/file/upload', fd); form.attachUrl = res as unknown as string }
    const body = { ...form, items: items.value }
    await request.put(`/outsource/delivery/${form.id}`, body)
    ElMessage.success('保存成功，库存已同步'); loadData(); sessionStorage.setItem(OUTSOURCE_DELIVERY_DIRTY_KEY, '1')
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
    ElMessage.success('附件已删除'); sessionStorage.setItem(OUTSOURCE_DELIVERY_DIRTY_KEY, '1')
    await loadData()
  } catch (e: any) {
    // F7-134（2026-09-20）：原为 `/* 取消 */` 空吞 ⇒ "用户取消"与"请求失败"不分。用户取消无需提示；
    // 请求失败由 request 拦截器统一提示，此处只做留痕（避免把失败误标成"取消"）。
    if (e !== 'cancel' && e !== 'close') { console.error('[delivery detail] 删除附件失败', e) }
  }
}

// 字典类只需加载一次
onMounted(()=>{ loadOptions() })
// 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
onActivated(()=>{ loadData() })
</script>

<template>
  <div class="detail-page">
    <el-card shadow="never" v-loading="loading">
      <el-form :model="form" label-width="90px" size="small" :disabled="readonly">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="状态"><el-tag :type="DocStatusTag[form.status] || 'info'">{{ DocStatusLabel[form.status] || form.status }}</el-tag></el-form-item></el-col>
          <!-- 类型只读：2026-09-16 流程重构后手工只支持 发料/调拨；收料/退不良为系统自动单、退料已下线（历史可查） -->
          <el-col :span="8"><el-form-item label="类型">
            <el-tag>{{ DeliveryTypeLabel[form.deliveryType] || form.deliveryType }}</el-tag>
            <span v-if="!isManualType" style="margin-left:6px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">系统自动生成 / 已下线（仅查看）</span>
          </el-form-item></el-col>
          <el-col :span="8" v-if="form.deliveryType===DeliveryType.DELIVERY"><el-form-item required label="收货工厂"><RemoteSelect v-model="form.factoryId" :fetch="fetchFactories" :preset="{ id: form.factoryId, name: form.factoryName }" :disabled="readonly" style="width:100%" placeholder="选择收货工厂" @pick="()=>onFactoryChange(form.factoryId)" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="日期"><el-input v-model="form.deliveryDate" type="date" /></el-form-item></el-col>
          <!-- 发料：发出仓 = 我方物料仓；目标仓 = 该厂委外仓 -->
          <el-col :span="8" v-if="form.deliveryType===DeliveryType.DELIVERY"><el-form-item label="发出仓库"><el-select v-model="form.fromWarehouseId" filterable style="width:100%"><el-option v-for="w in materialOwnWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="8" v-if="form.deliveryType===DeliveryType.DELIVERY"><el-form-item label="目标委外仓"><el-select v-model="form.toWarehouseId" filterable style="width:100%"><el-option v-for="w in (outsourceWarehouses.length ? outsourceWarehouses : allOutsourceWarehouses)" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <!-- 调拨：物料相关仓库之间互转（委外仓↔委外仓 / 委外仓→我方物料仓 / 我方物料仓↔我方物料仓） -->
          <el-col :span="8" v-if="form.deliveryType===DeliveryType.TRANSFER"><el-form-item label="来源仓库"><el-select v-model="form.fromWarehouseId" filterable style="width:100%"><el-option v-for="w in materialTransferWhs" :key="w.id" :label="`${w.warehouseName}（${w.factoryId?'委外仓':'我方物料仓'}）`" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="8" v-if="form.deliveryType===DeliveryType.TRANSFER"><el-form-item label="目标仓库"><el-select v-model="form.toWarehouseId" filterable style="width:100%"><el-option v-for="w in targetTransferWhs" :key="w.id" :label="`${w.warehouseName}（${w.factoryId?'委外仓':'我方物料仓'}）`" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="8" v-if="form.deliveryType===DeliveryType.TRANSFER"><el-form-item label="强制出库"><el-switch v-model="form.allowNegative" :active-value="1" :inactive-value="0" :disabled="readonly" /><span style="margin-left:6px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">允许来源仓扣成负数</span></el-form-item></el-col>
          <!-- 自动/历史单据：仓库只读展示 -->
          <el-col :span="16" v-if="!isManualType"><el-form-item label="仓库"><span>{{ form.fromWarehouseName || '-' }} → {{ form.toWarehouseName || '-' }}</span></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item></el-col>
          <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
          <el-col :span="8"><el-form-item label="制单人"><el-input :model-value="form.createByName || '—'" readonly /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="审核人"><el-input :model-value="form.auditorName || '—'" readonly /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 物料明细 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-button type="primary" size="small" :disabled="readonly" @click="addItem" style="margin-bottom:8px">+ 添加物料</el-button>
      <el-table :data="items" border size="small">
        <el-table-column label="物料类型" width="110"><template #default="{row,$index}"><el-select v-model="row.materialTypeId" filterable style="width:100%" clearable :disabled="readonly" @change="onTypeChange($index)"><el-option v-for="t in uniqueTypes" :key="t" :label="typeName(t)" :value="t" /></el-select></template></el-table-column>
        <el-table-column label="物料名称" min-width="130"><template #default="{row,$index}"><el-select v-model="row.material_id" filterable style="width:100%" :disabled="readonly || !row.materialTypeId" @change="(v:any)=>onMatSelect($index,v)"><el-option v-for="m in optionsForRow(row)" :key="m.id" :label="m.materialName" :value="m.id" /></el-select></template></el-table-column>
        <el-table-column label="单位" width="60"><template #default="{row}">{{row.unit}}</template></el-table-column>
        <el-table-column label="单价" width="90"><template #default="{row}"><el-input v-model="row.unitPrice" size="small" :disabled="readonly" /></template></el-table-column>
        <el-table-column label="数量" width="100"><template #default="{row}"><el-input-number v-model="row.quantity" :controls="false" :precision="0" :step="1" size="small" style="width:100%" :disabled="readonly" /></template></el-table-column>
        <el-table-column label="质量" width="90" align="center"><template #default="{row}"><el-select v-model="row.qualityType" size="small" style="width:100%" :disabled="readonly"><el-option :label="QualityTypeLabel[QualityType.GOOD]" :value="QualityType.GOOD" /><el-option :label="QualityTypeLabel[QualityType.DEFECT]" :value="QualityType.DEFECT" /></el-select></template></el-table-column>
        <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link :disabled="readonly" @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <!-- 物流信息 & 附件 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">物流信息 & 附件</span></template>
      <el-form :model="form" label-width="90px" size="small" :disabled="readonly">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="物流公司"><el-input v-model="form.logisticsCompany" placeholder="如顺丰" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物流单号"><el-input v-model="form.logisticsNo" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'var(--app-color-success)':'var(--app-border-color)', background: uploadFile?'#f0f9eb':'#fafafa' }">
        <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:var(--app-color-success);font-weight:600">📎 {{ uploadFile.name }}</span><el-button v-if="!readonly" type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
        <template v-else-if="form.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:var(--app-color-primary)">📎 已有附件</span><el-button type="primary" size="small" @click.stop="openAttach(form.attachUrl)">查看</el-button><el-button type="success" size="small"><a :href="form.attachUrl" download style="color:inherit;text-decoration:none">下载</a></el-button><el-button v-if="!readonly" type="danger" size="small" @click.stop="handleDeleteAttach">删除</el-button><span v-if="!readonly" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">可拖拽新文件替换</span></div></template>
        <template v-else-if="!readonly"><p style="color:var(--app-text-secondary);margin:0">拖拽文件到此处，或点击选择</p></template>
        <input v-if="!readonly && !form.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
    </el-card>

    <div style="margin-top:16px;display:flex;justify-content:flex-end"><el-button type="primary" size="large" :loading="saving" :disabled="readonly" @click="handleSave">保存并同步库存</el-button></div>
  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:12px; }

.drop-zone { position:relative; border:2px dashed var(--app-border-color); border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:var(--app-color-primary); background:#ecf5ff }
</style>
