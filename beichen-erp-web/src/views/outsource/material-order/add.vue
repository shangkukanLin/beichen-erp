<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter(); const route = useRoute()
const tabStore = useTabStore()
const isEdit = ref(false)
const editId = route.params.id ? Number(route.params.id) : 0
const saving = ref(false)

const form = reactive({ orderType: '采购', supplierId: undefined as any, targetWarehouseId: undefined as any, deliveryDate: '', remark: '' })
const items = ref<any[]>([])
const supplierOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const bomTypes = ref<any[]>([])
const itemTypes = ref<Record<number, string>>({})

// Odoo 风格：下拉框实时查库
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
const fetchMaterials = (kw: string) => request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw } })

async function loadSuppliers() {
  const r = await request.get<any, any>('/supplier/page', { params: { pageSize: 500 } }); supplierOptions.value = r?.records || []
}

async function loadOptions() {
  await loadSuppliers()
  const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); materialOptions.value = r?.records || []
  try { const r = await request.get<any, any>('/dev/bom-type/enabled'); bomTypes.value = r || [] } catch { }
}

function onOrderTypeChange() {
  form.supplierId = undefined
}

// 根据选择的类型(bomTypeId)筛选物料
function filteredMaterials(type: number) {
  if (!type) return materialOptions.value
  return materialOptions.value.filter((m: any) => m.bomTypeId === type)
}
function typeName(id: number | undefined) {
  if (id == null) return '-'
  const t = bomTypes.value.find((v: any) => v.id === id)
  return t ? t.typeName : (id as any)
}

function addItem() { items.value.push({ bomTypeId: undefined, materialId: undefined, materialName: '', unit: '', orderQuantity: 1, unitPrice: 0, remark: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onTypeChange(idx: number) {
  items.value[idx].materialId = undefined
  items.value[idx].materialName = ''
  items.value[idx].unit = ''
}
function onMatChange(idx: number, mid: number) {
  const m = materialOptions.value.find((v: any) => v.id === mid)
  if (m) { items.value[idx].materialName = m.materialName; items.value[idx].bomTypeId = m.bomTypeId; items.value[idx].unit = m.unit }
}

function handleCancel() {
  tabStore.removeTab(route.fullPath)
  router.back()
}

async function handleSubmit() {
  if (items.value.length === 0) { ElMessage.warning('请添加物料'); return }
  saving.value = true
  try {
    if (isEdit.value) { await request.put(`/outsource/material-order/${editId}`, { ...form, items: items.value }); ElMessage.success('已更新') }
    else {
      await request.post('/outsource/material-order', { ...form, items: items.value }); ElMessage.success('已创建')
      // 重置表单，避免 keep-alive 缓存残留数据
      Object.assign(form, { orderType: '采购', supplierId: undefined, targetWarehouseId: undefined, deliveryDate: '', remark: '' })
      items.value = []
      onOrderTypeChange()
    }
    tabStore.removeTab(route.fullPath)
    if (isEdit.value) { router.replace(`/outsource/material-order/detail/${editId}`) }
    else { router.replace('/outsource/material-order') }
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function initFromQuery() {
  const q = route.query
  console.log('[initFromQuery] query:', JSON.stringify(q))
  if (q.orderType === '委外') {
    form.orderType = '委外'
    await loadSuppliers()
  }
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
    let matTypeId = q.bomTypeId ? Number(q.bomTypeId) : undefined
    let matId = q.materialId ? Number(q.materialId) : undefined
    // 如果 materialId 存在，用物料实际类型（确保 filteredMaterials 能匹配到）
    if (matId) {
      const exists = materialOptions.value.find((m: any) => m.id === matId)
      if (exists) matTypeId = exists.bomTypeId ?? matTypeId
    } else {
      // materialId 未传时，按名称从已加载物料中查找
      const found = materialOptions.value.find((m: any) => m.materialName === q.materialName)
      if (found) { matId = found.id; matTypeId = found.bomTypeId ?? matTypeId }
    }
    console.log('[initFromQuery] material item:', { matTypeId, matId, materialName: q.materialName })
    items.value = [{
      bomTypeId: matTypeId,
      materialId: matId,
      materialName: q.materialName as string,
      unit: (q.unit as string) || '',
      orderQuantity: q.quantity ? Number(q.quantity) : 1,
      unitPrice: 0, remark: ''
    }]
  }
}

// 重置为空白表单（供 keep-alive 缓存恢复时清空上次填写信息）
function resetForm() {
  Object.assign(form, { orderType: '采购', supplierId: undefined, targetWarehouseId: undefined, deliveryDate: '', remark: '' })
  items.value = []
  addItem()
}

onActivated(async () => {
  await loadOptions()
  // keep-alive 缓存恢复：非编辑模式需按来源重新初始化（避免残留上次填写信息）
  if (!editId) {
    const hasQuery = !!route.query.materialId || !!route.query.supplierId || !!route.query.bomTypeId
    if (hasQuery) {
      await initFromQuery()
      if (items.value.length === 0) addItem()
    } else {
      resetForm()
    }
  }
})
onMounted(async () => {
  await loadOptions()
  if (editId) {
    isEdit.value = true
    try {
      const r = await request.get<any, any>(`/outsource/material-order/${editId}`)
      if (r) {
        Object.assign(form, { orderType: r.orderType || '采购', supplierId: r.supplierId, targetWarehouseId: r.targetWarehouseId, deliveryDate: r.deliveryDate, remark: r.remark })
        await loadSuppliers()
        items.value = (r.items || []).map((it: any) => ({ bomTypeId: it.bomTypeId, materialId: it.materialId, materialName: it.materialName, unit: it.unit, orderQuantity: it.orderQuantity, unitPrice: it.unitPrice, remark: it.remark }))
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
    <el-card shadow="never">
      <template #header><span style="font-weight:600">订单信息</span></template>
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="订单类型">
            <el-radio-group v-model="form.orderType" @change="onOrderTypeChange">
              <el-radio value="采购">采购</el-radio>
              <el-radio value="委外">委外</el-radio>
            </el-radio-group>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="form.orderType==='委外'?'加工厂':'供应商'">
            <RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" clearable style="width:100%" placeholder="选择供应商">
              <el-option label="+ 新增" :value="ADD_MARKER" @click="router.push('/supplier/manage')" />
            </RemoteSelect>
          </el-form-item></el-col>
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
            <el-select v-model="row.bomTypeId" size="small" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.bomTypeId = undefined; router.push('/dev/bom-type'); return } onTypeChange($index) }">
              <el-option v-for="t in bomTypes" :key="t.id" :label="t.typeName" :value="t.id" />
              <el-option label="+ 新增" :value="ADD_MARKER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="物料名称" min-width="180">
          <template #default="{row,$index}">
            <el-select v-model="row.materialId" filterable size="small" style="width:100%" :disabled="!row.bomTypeId" @change="(v: any) => { if (v === ADD_MARKER) { row.materialId = undefined; router.push('/material'); return } onMatChange($index, v) }">
              <el-option v-for="m in filteredMaterials(row.bomTypeId)" :key="m.id" :label="m.materialName" :value="m.id" />
              <el-option label="+ 新增" :value="ADD_MARKER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="60"><template #default="{row}">{{ row.unit }}</template></el-table-column>
        <el-table-column label="数量" width="110"><template #default="{row}"><el-input v-model="row.orderQuantity" size="small" type="number" /></template></el-table-column>
        <el-table-column :label="form.orderType==='委外'?'加工费单价':'单价'" width="100"><template #default="{row}"><el-input v-model="row.unitPrice" size="small" type="number" /></template></el-table-column>
        <el-table-column label="备注" min-width="100"><template #default="{row}"><el-input v-model="row.remark" size="small" /></template></el-table-column>
        <el-table-column label="操作" width="70" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <div style="margin-top:16px"><el-button type="primary" size="large" :loading="saving" @click="handleSubmit">保存</el-button><el-button size="large" @click="handleCancel">取消</el-button></div>
  </div>
</template>

<style scoped>
.add-page { display:flex; flex-direction:column; gap:12px; }

</style>
