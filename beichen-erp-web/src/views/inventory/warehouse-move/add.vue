<template>
  <div class="page">
    <el-card shadow="never" style="margin-top:16px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="移出仓库" prop="fromWarehouseId">
              <RemoteSelect v-model="form.fromWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="请选择" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="移入仓库" prop="toWarehouseId">
              <RemoteSelect v-model="form.toWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="请选择" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="移仓日期">
              <el-date-picker v-model="form.moveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">移仓明细</el-divider>
        <div style="margin-bottom:8px">
          <el-button type="primary" @click="addItem">添加明细</el-button>
        </div>
        <el-table :data="items" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="产品" min-width="220">
            <template #default="{ row }">
              <RemoteSelect v-model="row.productId" :fetch="fetchProducts" placeholder="选择产品" style="width:100%"
                @pick="(rows:any[]) => onProductPick(rows[0], row)" />
            </template>
          </el-table-column>
          <el-table-column label="规格" width="100">
            <template #default="{ row }">{{ row._spec || '' }}</template>
          </el-table-column>
          <el-table-column label="单位" width="70">
            <template #default="{ row }">{{ row._unit || '' }}</template>
          </el-table-column>
          <el-table-column label="品质" width="90">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" size="small" style="width:100%">
                <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="现有库存" width="110" align="right">
            <template #default="{ row }">{{ row._stock != null ? row._stock : '-' }}</template>
          </el-table-column>
          <el-table-column label="移仓数量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="1" :precision="0" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="items.splice($index, 1)">删除</el-button></template>
          </el-table-column>
        </el-table>

        <div style="text-align:center;margin-top:24px">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { WarehouseCategory } from '@/api/enums'
defineOptions({ name: 'InventoryWarehouseMoveAdd' })

import { reactive, ref, watch, onMounted, onBeforeUnmount, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useTabStore } from '@/stores/tabs'
import request from '@/utils/request'
import { getQualityTypes, type QualityOption } from '@/api/product'
import RemoteSelect from '@/components/RemoteSelect.vue'

interface MoveItem {
  productId?: number
  qualityType?: string
  _spec?: string
  _unit?: string
  _stock?: number
  quantity?: number
  remark?: string
}

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()

const editId = ref<number | undefined>(route.query.id ? Number(route.query.id) : undefined)
const isEdit = ref(!!editId.value)

const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  fromWarehouseId: undefined as number | undefined,
  toWarehouseId: undefined as number | undefined,
  moveDate: new Date().toISOString().slice(0, 10) as string,
  remark: '' as string,
})
const items = ref<MoveItem[]>([])
const rules: FormRules = {
  fromWarehouseId: [{ required: true, message: '请选择移出仓库', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择移入仓库', trigger: 'change' }]
}

const qualityOptions = ref<QualityOption[]>([])
const productOptions = ref<any[]>([])

const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })

async function loadProducts(query?: string) {
  try {
    const params: any = { pageSize: 100 }
    if (query) params.name = query
    const res = await fetchProducts(query || '')
    productOptions.value = res?.records || []
  } catch { productOptions.value = [] }
}

async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }

function onProductPick(p: any, row: MoveItem) {
  if (!p) return
  row.productId = p.id
  row._spec = p.spec
  row._unit = p.unit
  // 查询该产品在移出仓库的现有库存
  if (p.id && form.fromWarehouseId) {
    request.get<any, any>('/warehouse/stock/page', { params: { productId: p.id, warehouseId: form.fromWarehouseId, pageSize: 1 } })
      .then(r => { row._stock = (r?.records || [])[0]?.quantity || 0 })
      .catch(() => { row._stock = undefined })
  }
}

// 刷新所有明细行的库存
async function refreshStock() {
  for (const item of items.value) {
    if (item.productId && form.fromWarehouseId) {
      try {
        const r = await request.get<any, any>('/warehouse/stock/page', { params: { productId: item.productId, warehouseId: form.fromWarehouseId, pageSize: 1 } })
        item._stock = (r?.records || [])[0]?.quantity || 0
      } catch { item._stock = undefined }
    } else {
      item._stock = undefined
    }
  }
}

function addItem() { items.value.push({ productId: undefined, qualityType: 'A', quantity: 1 }) }

function resetForm() {
  Object.assign(form, { id: undefined, fromWarehouseId: undefined, toWarehouseId: undefined, moveDate: new Date().toISOString().slice(0,10), remark: '' })
  items.value = []
  formRef.value?.clearValidate()
}

async function loadMoveData() {
  if (!editId.value) return
  try {
    const r = await request.get<any, any>(`/inventory/warehouse-move/${editId.value}`)
    form.id = r.id
    form.fromWarehouseId = r.fromWarehouseId
    form.toWarehouseId = r.toWarehouseId
    form.moveDate = r.moveDate
    form.remark = r.remark
    const its = await request.get<any, any>(`/inventory/warehouse-move/${editId.value}/items`)
    items.value = (its || []).map((it: any) => ({
      productId: it.productId,
      qualityType: it.qualityType,
      _spec: it.spec,
      _unit: it.unit,
      quantity: it.quantity,
      remark: it.remark,
    }))
    await refreshStock()
  } catch { ElMessage.error('加载移仓单失败') }
}

watch(() => route.query.id, (newId) => {
  editId.value = newId ? Number(newId) : undefined
  isEdit.value = !!editId.value
  resetForm()
  if (isEdit.value) {
    tabStore.updateTabTitle(route.fullPath, '编辑移仓单')
    document.title = '编辑移仓单 - 北辰ERP管理系统'
    loadMoveData()
  }
})

// 移出仓库变化时刷新所有明细的库存
watch(() => form.fromWarehouseId, () => {
  if (items.value.length > 0) refreshStock()
})

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.fromWarehouseId === form.toWarehouseId) { ElMessage.warning('移出与移入仓库不能相同'); return }
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    if (items.value.some(it => !it.productId)) { ElMessage.warning('请选择产品'); return }
    submitLoading.value = true
    try {
      const payload = { move: { ...form }, items: items.value }
      if (isEdit.value) await request.put(`/inventory/warehouse-move/${editId.value}`, payload)
      else await request.post('/inventory/warehouse-move', payload)
      ElMessage.success('保存成功')
      resetForm()
      tabStore.removeTab(route.fullPath)
      router.push('/inventory/warehouse-move')
    } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
    finally { submitLoading.value = false }
  })
}

function handleCancel() {
  resetForm()
  tabStore.removeTab(route.fullPath)
  router.push('/inventory/warehouse-move')
}

onMounted(() => {
  loadProducts()
  loadQualityTypes()
  if (isEdit.value) {
    tabStore.updateTabTitle(route.fullPath, '编辑移仓单')
    document.title = '编辑移仓单 - 北辰ERP管理系统'
    loadMoveData()
  }
})

onActivated(() => {
  // 每次激活时根据路由重置状态
  editId.value = route.query.id ? Number(route.query.id) : undefined
  isEdit.value = !!editId.value
  resetForm()
  if (isEdit.value) {
    tabStore.updateTabTitle(route.fullPath, '编辑移仓单')
    document.title = '编辑移仓单 - 北辰ERP管理系统'
    loadMoveData()
  }
})

onBeforeUnmount(() => {
  resetForm()
})
</script>

<style scoped>
.page { padding: 0; }
</style>
