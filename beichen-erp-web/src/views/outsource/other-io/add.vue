<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const route = useRoute(); const router = useRouter()
const editId = Number(route.query.id) || 0
const warehouses = ref<any[]>([])
const materialOptions = ref<any[]>([])
const saving = ref(false)
const form = reactive({ warehouseId: undefined as any, ioType: '入库', ioDate: new Date().toISOString().slice(0,10), remark: '' })
const items = ref<any[]>([{ materialId: undefined, materialName: '', materialType: '', unit: '', unit_price: '', quantity: undefined, remark: '' }])

const uniqueTypes = computed(() => [...new Set(materialOptions.value.map((m: any) => m.materialType).filter(Boolean))] as string[])
function materialsByType(type: string) { return materialOptions.value.filter((m: any) => m.materialType === type) }

async function loadWarehouses() {
  try {
    const [r1, r2] = await Promise.all([
      request.get<any,any>('/outsource/warehouse/page', {params:{pageSize:200}}),
      request.get<any,any>('/inventory/warehouse/page', {params:{pageSize:200}})
    ]);
    warehouses.value = [
      ...(r1?.records || []).map((w:any) => ({ ...w, _type: '委外仓' })),
      ...(r2?.records || []).map((w:any) => ({ ...w, _type: '我方仓' }))
    ]
  } catch {}
}
async function loadMaterials() {
  try { const r = await request.get<any,any>('/outsource/material/page',{params:{pageSize:500}}); materialOptions.value = r?.records||[] } catch {}
}
function onTypeChange(idx: number) { items.value[idx].materialId = undefined; items.value[idx].materialName = ''; items.value[idx].unit = ''; items.value[idx].unit_price = '' }
function onMatSelect(idx: number, matId: number) {
  const m = materialOptions.value.find((v:any)=>v.id===matId)
  if (m) { items.value[idx].materialName=m.materialName; items.value[idx].materialType=m.materialType; items.value[idx].unit=m.unit }
  // 自动查询加权平均单价（需先选择仓库）
  if (m && form.warehouseId) {
    const wh = warehouses.value.find((w:any)=>w.id===form.warehouseId)
    // 委外仓用加工厂ID查加权单价，我方仓不自动查
    if (wh?._type === '委外仓' && wh?.factoryId) {
      request.get<any,any>('/outsource/delivery/material-weighted-price', { params: { factoryId: wh.factoryId, materialName: m.materialName } }).then((r: any) => {
        if (r) items.value[idx].unit_price = r
      }).catch(() => {})
    }
  }
}
function addItem() { items.value.push({ materialId: undefined, materialName: '', materialType: '', unit: '', unit_price: '', quantity: undefined, remark: '' }) }
function removeItem(i: number) { items.value.splice(i,1) }

async function loadDetail() {
  if (!editId) return
  try {
    const io = await request.get<any,any>(`/outsource/other-io/${editId}`)
    form.warehouseId = io.warehouseId; form.ioType = io.ioType
    form.ioDate = io.ioDate; form.remark = io.remark||''
    const its = await request.get<any,any>(`/outsource/other-io/${editId}/items`)
    if (Array.isArray(its)) items.value = its.map((i:any)=>({ materialId: i.materialId, materialName: i.materialName, materialType: i.materialType, unit: i.unit, unit_price: i.unitPrice||'', quantity: i.quantity, remark: i.remark||'' }))
  } catch (e: any) { ElMessage.error(e?.message||'加载失败') }
}

async function handleSubmit() {
  if (!form.warehouseId) { ElMessage.warning('请选择仓库'); return }
  const validItems = items.value.filter((i:any)=>i.quantity && Number(i.quantity)>0)
  if (validItems.length===0) { ElMessage.warning('请添加物料明细'); return }
  saving.value = true
  try {
    const body: any = { ...form, items: validItems }
    if (editId) {
      await request.put(`/outsource/other-io/${editId}`, body); ElMessage.success('已更新')
    } else {
      await request.post('/outsource/other-io', body); ElMessage.success('已创建')
      Object.assign(form, { warehouseId: undefined, ioType: '入库', ioDate: new Date().toISOString().slice(0,10), remark: '' })
      items.value = [{ materialId: undefined, materialName: '', materialType: '', unit: '', unit_price: '', quantity: undefined, remark: '' }]
      ;(window as any).__otherIoNeedRefresh = true
    }
    router.push('/outsource/other-io')
  } catch (e: any) { ElMessage.error(e?.message||'保存失败') } finally { saving.value = false }
}

onMounted(()=>{ loadWarehouses(); loadMaterials(); loadDetail() })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <div><span style="font-size:18px;font-weight:600">{{ editId?'编辑':'新增' }}物料其他出入库</span></div>
    <el-card shadow="never">
      <el-form :model="form" label-width="80px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="仓库"><el-select v-model="form.warehouseId" filterable style="width:100%"><el-option v-for="w in warehouses" :key="w.id+'@'+w._type" :label="`${w.warehouseName}（${w._type}）`" :value="w.id"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="类型"><el-select v-model="form.ioType" style="width:100%"><el-option label="入库" value="入库"/><el-option label="出库" value="出库"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="日期"><el-input v-model="form.ioDate" type="date"/></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注"/></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-button type="primary" size="small" @click="addItem" style="margin-bottom:8px">+ 添加物料</el-button>
      <el-table :data="items" border size="small">
        <el-table-column label="物料类型" width="110"><template #default="{row,$index}"><el-select v-model="row.materialType" filterable style="width:100%" clearable @change="onTypeChange($index)"><el-option v-for="t in uniqueTypes" :key="t" :label="t" :value="t"/></el-select></template></el-table-column>
        <el-table-column label="物料名称" min-width="140"><template #default="{row,$index}"><el-select v-model="row.materialId" filterable style="width:100%" :disabled="!row.materialType" @change="(v:any)=>onMatSelect($index,v)"><el-option v-for="m in materialsByType(row.materialType)" :key="m.id" :label="m.materialName" :value="m.id"/></el-select></template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="{row}">{{ row.unit }}</template></el-table-column>
        <el-table-column label="单价" width="100"><template #default="{row}"><el-input v-model="row.unit_price" size="small" placeholder="单价"/></template></el-table-column>
        <el-table-column label="数量" width="110"><template #default="{row}"><el-input v-model="row.quantity" size="small" type="number"/></template></el-table-column>
        <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <div style="display:flex;gap:12px;justify-content:center"><el-button @click="router.push('/outsource/other-io')">取消</el-button><el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button></div>
  </div>
</template>
