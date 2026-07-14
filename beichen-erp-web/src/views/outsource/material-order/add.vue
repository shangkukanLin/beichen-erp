<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter(); const route = useRoute()
const isEdit = ref(false)
const editId = route.params.id ? Number(route.params.id) : 0
const saving = ref(false)

const form = reactive({ supplierId: undefined as any, deliveryDate: '', remark: '' })
const items = ref<any[]>([])
const supplierOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const bomTypes = ref<string[]>([])
const itemTypes = ref<Record<number, string>>({})

async function loadOptions() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType: 'material', pageSize: 500 } }); supplierOptions.value = r?.records || [] } catch { }
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); materialOptions.value = r?.records || [] } catch { }
  try { const r = await request.get<any, any>('/dev/bom-type/enabled'); bomTypes.value = (r || []).map((t: any) => t.typeName) } catch { }
}

// 根据选择的类型筛选物料
function filteredMaterials(type: string) {
  if (!type) return materialOptions.value
  return materialOptions.value.filter((m: any) => m.materialType === type)
}

function addItem() { items.value.push({ materialType: '', materialId: undefined, materialName: '', unit: '', orderQuantity: 1, unitPrice: 0, remark: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onTypeChange(idx: number) {
  items.value[idx].materialId = undefined
  items.value[idx].materialName = ''
  items.value[idx].unit = ''
}
function onMatChange(idx: number, mid: number) {
  const m = materialOptions.value.find((v: any) => v.id === mid)
  if (m) { items.value[idx].materialName = m.materialName; items.value[idx].materialType = m.materialType; items.value[idx].unit = m.unit }
}

async function handleSubmit() {
  if (items.value.length === 0) { ElMessage.warning('请添加物料'); return }
  saving.value = true
  try {
    if (isEdit.value) { await request.put(`/outsource/material-order/${editId}`, { ...form, items: items.value }); ElMessage.success('已更新') }
    else { await request.post('/outsource/material-order', { ...form, items: items.value }); ElMessage.success('已创建') }
    router.replace('/outsource/material-order')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function initFromQuery() {
  const q = route.query
  console.log('[initFromQuery] query:', JSON.stringify(q))
  // 供应商：如果不在已加载选项中（可能不是 material 类型），主动拉取并加入选项
  if (q.supplierId) {
    form.supplierId = Number(q.supplierId)
    if (!supplierOptions.value.some((s: any) => s.id === form.supplierId)) {
      try {
        const sup = await request.get<any, any>(`/supplier/${form.supplierId}`)
        if (sup) supplierOptions.value.push(sup)
      } catch { }
    }
  }
  if (q.materialName) {
    let matType = (q.materialType as string) || ''
    let matId = q.materialId ? Number(q.materialId) : undefined
    // 如果 materialId 存在，用物料实际类型（确保 filteredMaterials 能匹配到）
    if (matId) {
      const exists = materialOptions.value.find((m: any) => m.id === matId)
      if (exists) matType = exists.materialType || matType
    } else {
      // materialId 未传时，按名称从已加载物料中查找
      const found = materialOptions.value.find((m: any) => m.materialName === q.materialName)
      if (found) { matId = found.id; matType = found.materialType || matType }
    }
    console.log('[initFromQuery] material item:', { matType, matId, materialName: q.materialName })
    items.value = [{
      materialType: matType,
      materialId: matId,
      materialName: q.materialName as string,
      unit: (q.unit as string) || '',
      orderQuantity: q.quantity ? Number(q.quantity) : 1,
      unitPrice: 0, remark: ''
    }]
  }
}

onActivated(() => { loadOptions() })
onMounted(async () => {
  await loadOptions()
  if (editId) {
    isEdit.value = true
    try {
      const r = await request.get<any, any>(`/outsource/material-order/${editId}`)
      if (r) {
        Object.assign(form, { supplierId: r.supplierId, deliveryDate: r.deliveryDate, remark: r.remark })
        items.value = (r.items || []).map((it: any) => ({ materialType: it.materialType, materialId: it.materialId, materialName: it.materialName, unit: it.unit, orderQuantity: it.orderQuantity, unitPrice: it.unitPrice, remark: it.remark }))
      }
    } catch { ElMessage.error('加载订单失败') }
  } else {
    await initFromQuery()
    if (items.value.length === 0) addItem()
  }
})
</script>

<template>
  <div class="add-page">
    <div class="page-header"><el-button @click="router.push('/outsource/material-order')">← 返回</el-button><span class="page-title">{{ isEdit ? '编辑物料订单' : '新增物料订单' }}</span></div>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">订单信息</span></template>
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="供应商"><el-select v-model="form.supplierId" filterable clearable style="width:100%"><el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="交期"><el-input v-model="form.deliveryDate" type="date" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-button type="primary" size="small" @click="addItem" style="margin-bottom:8px">+ 添加物料</el-button>
      <el-table :data="items" border size="small">
        <el-table-column label="类型" width="90">
          <template #default="{row,$index}">
            <el-select v-model="row.materialType" size="small" style="width:100%" @change="onTypeChange($index)">
              <el-option v-for="t in bomTypes" :key="t" :label="t" :value="t" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="物料" min-width="180">
          <template #default="{row,$index}">
            <el-select v-model="row.materialId" filterable size="small" style="width:100%" :disabled="!row.materialType" @change="(v:any)=>onMatChange($index,v)">
              <el-option v-for="m in filteredMaterials(row.materialType)" :key="m.id" :label="`${m.materialName} (${m.unit||''})`" :value="m.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="60"><template #default="{row}">{{ row.unit }}</template></el-table-column>
        <el-table-column label="数量" width="110"><template #default="{row}"><el-input v-model="row.orderQuantity" size="small" type="number" /></template></el-table-column>
        <el-table-column label="单价" width="100"><template #default="{row}"><el-input v-model="row.unitPrice" size="small" type="number" /></template></el-table-column>
        <el-table-column label="备注" min-width="100"><template #default="{row}"><el-input v-model="row.remark" size="small" /></template></el-table-column>
        <el-table-column label="操作" width="70" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <div style="margin-top:16px"><el-button type="primary" size="large" :loading="saving" @click="handleSubmit">保存</el-button><el-button size="large" @click="router.push('/outsource/material-order')">取消</el-button></div>
  </div>
</template>

<style scoped>
.add-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
</style>
