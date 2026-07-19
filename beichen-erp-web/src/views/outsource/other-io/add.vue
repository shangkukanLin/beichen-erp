<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const route = useRoute(); const router = useRouter()
const editId = Number(route.query.id) || 0
const warehouses = ref<any[]>([])
const materialOptions = ref<any[]>([])
const saving = ref(false)
const form = reactive({ warehouseId: undefined as any, ioType: '入库', ioDate: new Date().toISOString().slice(0,10), remark: '' })
const items = ref<any[]>([{ materialId: undefined, materialName: '', materialType: '', unit: '', quantity: undefined, remark: '' }])

async function loadWarehouses() {
  try { const r = await request.get<any,any>('/outsource/warehouse/page',{params:{pageSize:200}}); warehouses.value = r?.records||[] } catch {}
}
async function loadMaterials() {
  try { const r = await request.get<any,any>('/outsource/material/page',{params:{pageSize:500}}); materialOptions.value = r?.records||[] } catch {}
}
function onMatSelect(idx: number, matId: number) {
  const m = materialOptions.value.find((v:any)=>v.id===matId)
  if (m) { items.value[idx].materialName=m.materialName; items.value[idx].materialType=m.materialType; items.value[idx].unit=m.unit }
}
function addItem() { items.value.push({ materialId: undefined, materialName: '', materialType: '', unit: '', quantity: undefined, remark: '' }) }
function removeItem(i: number) { items.value.splice(i,1) }

async function loadDetail() {
  if (!editId) return
  try {
    const io = await request.get<any,any>(`/outsource/other-io/${editId}`)
    form.warehouseId = io.warehouseId; form.ioType = io.ioType
    form.ioDate = io.ioDate; form.remark = io.remark||''
    const its = await request.get<any,any>(`/outsource/other-io/${editId}/items`)
    if (Array.isArray(its)) items.value = its.map((i:any)=>({ materialId: i.materialId, materialName: i.materialName, materialType: i.materialType, unit: i.unit, quantity: i.quantity, remark: i.remark||'' }))
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
          <el-col :span="8"><el-form-item label="仓库"><el-select v-model="form.warehouseId" filterable style="width:100%"><el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseName}（${w.factoryName||''}）`" :value="w.id"/></el-select></el-form-item></el-col>
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
        <el-table-column label="物料" min-width="180"><template #default="{row,$index}"><el-select v-model="row.materialId" filterable style="width:100%" @change="(v:any)=>onMatSelect($index,v)"><el-option v-for="m in materialOptions" :key="m.id" :label="`${m.materialType||''} ${m.materialName}`" :value="m.id"/></el-select></template></el-table-column>
        <el-table-column label="类型" width="80"><template #default="{row}">{{ row.materialType }}</template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="{row}">{{ row.unit }}</template></el-table-column>
        <el-table-column label="数量" width="110"><template #default="{row}"><el-input v-model="row.quantity" size="small" type="number"/></template></el-table-column>
        <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <div style="display:flex;gap:12px;justify-content:center"><el-button @click="router.push('/outsource/other-io')">取消</el-button><el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button></div>
  </div>
</template>
