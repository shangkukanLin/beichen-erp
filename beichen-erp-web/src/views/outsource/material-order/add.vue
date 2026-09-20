<script setup lang="ts">
// 2026-09-20（F7-159）：显式声明组件名（便于 DevTools 辨认与将来按名 exclude）
defineOptions({ name: 'OutsourceMaterialOrderAdd' })
import { reactive, ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { OrderType, OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY } from '@/api/enums'

const router = useRouter(); const route = useRoute()
const tabStore = useTabStore()
const isEdit = ref(false)
const editId = route.params.id ? Number(route.params.id) : 0
const saving = ref(false)

const form = reactive({ orderType: OrderType.PURCHASE as string, supplierId: undefined as any, targetWarehouseId: undefined as any, deliveryDate: '', remark: '' })
const items = ref<any[]>([])
const supplierOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const materialTypes = ref<any[]>([])
const itemTypes = ref<Record<number, string>>({})

// Odoo 风格：下拉框实时查库
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
const fetchMaterials = (kw: string) => request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw } })

async function loadSuppliers() {
  // F7-136（2026-09-20）：补 try/catch —— 同文件其它三个 load 都有，唯独此处没有（失败会中断 loadOptions 后续加载）。
  try {
    const r = await request.get<any, any>('/supplier/page', { params: { pageSize: 500 } }); supplierOptions.value = r?.records || []
  } catch (e: any) { console.warn('加载供应商失败', e?.message || e) }
}

async function loadOptions() {
  await loadSuppliers()
  const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); materialOptions.value = r?.records || []
  // F7-129（2026-09-20）：加载失败不再静默 —— 留痕，避免"空下拉"被误认为"没有数据"
  try { const r = await request.get<any, any>('/dev/material-type/enabled'); materialTypes.value = r || [] } catch (e: any) { console.warn('加载物料类型失败', e?.message || e) }
}

function onOrderTypeChange() {
  form.supplierId = undefined
}

// 根据选择的类型(materialTypeId)筛选物料
function filteredMaterials(type: number) {
  if (!type) return materialOptions.value
  return materialOptions.value.filter((m: any) => m.materialTypeId === type)
}
function typeName(id: number | undefined) {
  if (id == null) return '-'
  const t = materialTypes.value.find((v: any) => v.id === id)
  return t ? t.typeName : (id as any)
}

function addItem() { items.value.push({ materialTypeId: undefined, materialId: undefined, materialName: '', unit: '', orderQuantity: 1, unitPrice: 0, remark: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onTypeChange(idx: number) {
  items.value[idx].materialId = undefined
  items.value[idx].materialName = ''
  items.value[idx].unit = ''
}
function onMatChange(idx: number, mid: number) {
  const m = materialOptions.value.find((v: any) => v.id === mid)
  if (m) { items.value[idx].materialName = m.materialName; items.value[idx].materialTypeId = m.materialTypeId; items.value[idx].unit = m.unit }
}

function handleCancel() {
  tabStore.removeTab(route.fullPath)
  router.back()
}

async function handleSubmit() {
  if (items.value.length === 0) { ElMessage.warning('请添加物料'); return }
  const label = form.orderType === OrderType.OUTSOURCE ? '加工厂' : '供应商'
  if (!form.supplierId) { ElMessage.warning(`请选择${label}`); return }
  saving.value = true
  try {
    if (isEdit.value) { await request.put(`/outsource/material-order/${editId}`, { ...form, items: items.value }); ElMessage.success('已更新') }
    else {
      await request.post('/outsource/material-order', { ...form, items: items.value }); ElMessage.success('已创建')
      // 重置表单，避免 keep-alive 缓存残留数据
      Object.assign(form, { orderType: OrderType.PURCHASE, supplierId: undefined, targetWarehouseId: undefined, deliveryDate: '', remark: '' })
      items.value = []
      onOrderTypeChange()
    }
    tabStore.removeTab(route.fullPath)
    sessionStorage.setItem(OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY, '1')
    if (isEdit.value) { router.replace(`/outsource/material-order/detail/${editId}`) }
    else { router.replace('/outsource/material-order') }
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function initFromQuery() {
  const q = route.query
  if (q.orderType === OrderType.OUTSOURCE) {
    form.orderType = OrderType.OUTSOURCE
    await loadSuppliers()
  }
  // 供应商：如果不在已加载选项中（可能不是 material 类型），主动拉取并加入选项
  if (q.supplierId) {
    form.supplierId = Number(q.supplierId)
    if (!supplierOptions.value.some((s: any) => s.id === form.supplierId)) {
      try {
        const sup = await request.get<any, any>(`/supplier/${form.supplierId}`)
        if (sup) supplierOptions.value.push(sup)
      } catch (e: any) { console.warn('加载供应商失败', e?.message || e) }   // F7-129：不再静默
    }
  }
  if (q.materialName) {
    let matTypeId = q.materialTypeId ? Number(q.materialTypeId) : undefined
    let matId = q.materialId ? Number(q.materialId) : undefined
    // 如果 materialId 存在，用物料实际类型（确保 filteredMaterials 能匹配到）
    if (matId) {
      const exists = materialOptions.value.find((m: any) => m.id === matId)
      if (exists) matTypeId = exists.materialTypeId ?? matTypeId
    } else {
      // materialId 未传时，按名称从已加载物料中查找
      const found = materialOptions.value.find((m: any) => m.materialName === q.materialName)
      if (found) { matId = found.id; matTypeId = found.materialTypeId ?? matTypeId }
    }
    items.value = [{
      materialTypeId: matTypeId,
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
  Object.assign(form, { orderType: OrderType.PURCHASE, supplierId: undefined, targetWarehouseId: undefined, deliveryDate: '', remark: '' })
  items.value = []
  addItem()
}


// 顶栏"刷新数据"：重新加载供应商/物料/类型下拉
async function handleRefreshData() { await loadOptions() }
onMounted(async () => {
  await loadOptions()
  if (editId) {
    isEdit.value = true
    try {
      const r = await request.get<any, any>(`/outsource/material-order/${editId}`)
      if (r) {
        Object.assign(form, { orderType: r.orderType || OrderType.PURCHASE, supplierId: r.supplierId, targetWarehouseId: r.targetWarehouseId, deliveryDate: r.deliveryDate, remark: r.remark })
        await loadSuppliers()
        items.value = (r.items || []).map((it: any) => ({ materialTypeId: it.materialTypeId, materialId: it.materialId, materialName: it.materialName, unit: it.unit, orderQuantity: it.orderQuantity, unitPrice: it.unitPrice, remark: it.remark }))
      }
    } catch { ElMessage.error('加载订单失败') }
  } else {
    await initFromQuery()
    if (items.value.length === 0) addItem()
  }
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))
</script>

<template>
  <div class="add-page">
    <el-card shadow="never">
      <template #header><span style="font-weight:600">订单信息</span></template>
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="订单类型">
            <el-radio-group v-model="form.orderType" @change="onOrderTypeChange">
              <el-radio :value="OrderType.PURCHASE">采购</el-radio>
              <el-radio :value="OrderType.OUTSOURCE">委外</el-radio>
            </el-radio-group>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item required :label="form.orderType===OrderType.OUTSOURCE?'加工厂':'供应商'">
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
            <el-select v-model="row.materialTypeId" size="small" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.materialTypeId = undefined; router.push('/dev/material-type'); return } onTypeChange($index) }">
              <el-option v-for="t in materialTypes" :key="t.id" :label="t.typeName" :value="t.id" />
              <el-option label="+ 新增" :value="ADD_MARKER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="物料名称" min-width="180">
          <template #default="{row,$index}">
            <el-select v-model="row.materialId" filterable size="small" style="width:100%" :disabled="!row.materialTypeId" @change="(v: any) => { if (v === ADD_MARKER) { row.materialId = undefined; router.push('/product/add'); return } onMatChange($index, v) }">
              <el-option v-for="m in filteredMaterials(row.materialTypeId)" :key="m.id" :label="m.materialName" :value="m.id" />
              <el-option label="+ 新增" :value="ADD_MARKER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="60"><template #default="{row}">{{ row.unit }}</template></el-table-column>
        <el-table-column label="数量" width="110"><template #default="{row}"><el-input-number v-model="row.orderQuantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
        <el-table-column :label="form.orderType===OrderType.OUTSOURCE?'加工费单价':'单价'" width="100"><template #default="{row}"><el-input v-model="row.unitPrice" size="small" type="number" /></template></el-table-column>
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
