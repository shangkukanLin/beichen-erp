<template>
  <div class="purchase-add">
    <el-card shadow="never" style="margin-top:16px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.supplierId" placeholder="请选择" filterable style="width:100%">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择" filterable style="width:100%">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName || w.name" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货日期">
              <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">退货明细</el-divider>
        <div style="margin-bottom:8px">
          <el-button type="primary" @click="addItem">添加明细</el-button>
        </div>
        <el-table :data="items" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="产品" min-width="200" prop="productId">
            <template #default="{ row }">
              <el-select v-model="row.productId" placeholder="选择产品" filterable remote :remote-method="loadProducts"
                style="width:100%" @change="(v: number) => onProductChange(v, row)">
                <el-option v-for="m in productOptions" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="现有库存" width="140" align="center">
            <template #default="{ row }">
              <span :style="{ color: (row._stock ?? 0) <= 0 ? 'red' : '' }">{{ row._stock ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="品质" width="90">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" size="small" style="width:100%">
                <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="退货数量" width="140">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="1" :step="1" :precision="0" controls-position="right" style="width:100%" @change="calcAmount" /></template>
          </el-table-column>
          <el-table-column label="单价" width="140">
            <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" @change="calcAmount" /></template>
          </el-table-column>
          <el-table-column label="金额" width="140" align="right">
            <template #default="{ row }">{{ ((Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="items.splice($index, 1)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-form>

      <div style="text-align:center;margin-top:24px">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'PurchaseReturnAdd' })
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useTabStore } from '@/stores/tabs'
import request from '@/utils/request'
import { getQualityTypes, type QualityOption } from '@/api/product'

interface ReturnItem {
  productId?: number
  qualityType?: string
  _stock?: number
  quantity?: number
  unitPrice?: number
  remark?: string
}

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()
const isEdit = !!route.query.id
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const suppliers = ref<any[]>([])
const warehouses = ref<any[]>([])
const qualityOptions = ref<QualityOption[]>([])
const productOptions = ref<any[]>([])
const items = ref<ReturnItem[]>([])

const form = reactive({
  supplierId: undefined as number | undefined,
  warehouseId: undefined as number | undefined,
  returnDate: new Date().toISOString().slice(0, 10) as string,
  remark: '' as string,
})

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择退货仓库', trigger: 'change' }],
}

function addItem() {
  items.value.push({ productId: undefined, qualityType: 'A', quantity: 1, unitPrice: 0, remark: '' })
}

async function loadProducts(query?: string) {
  try {
    const params: any = { pageSize: 100 }
    if (query) params.name = query
    const res = await request.get<any, any>('/product/page', { params })
    productOptions.value = res?.records || []
  } catch { productOptions.value = [] }
}

async function onProductChange(val: number, row: ReturnItem) {
  const m = productOptions.value.find((x: any) => x.id === val)
  if (m) {
    row.productId = m.id
  }
  // 查询该产品库存
  row._stock = undefined
  if (val) {
    try {
      const p: any = {}
      if (form.warehouseId) p.warehouseId = form.warehouseId
      p.productId = val
      const res = await request.get<any, any>('/warehouse/stock/page', { params: p })
      const arr = res?.records || []
      let total = 0
      if (arr.length > 0) {
        total = arr.reduce((s: number, x: any) => s + (Number(x.quantity) || 0), 0)
      }
      row._stock = total
    } catch { row._stock = undefined }
  }
}

function calcAmount() { /* 金额由模板计算 */ }

async function loadSuppliers() {
  try {
    const res = await request.get('/supplier/page', { params: { pageSize: 200, supplierType: 'product' } })
    suppliers.value = res?.records || []
  } catch { suppliers.value = [] }
}

async function loadWarehouses() {
  try {
    const res = await request.get('/warehouse/page', { params: { pageSize: 200 } })
    warehouses.value = res?.records || []
  } catch { warehouses.value = [] }
}

async function loadReturnData() {
  const id = Number(route.query.id)
  if (!id) return
  try {
    const order = await request.get(`/inventory/purchase-return/${id}`)
    if (order) {
      form.supplierId = order.supplierId
      form.warehouseId = order.warehouseId
      form.returnDate = order.returnDate
      form.remark = order.remark || ''
    }
    const its = await request.get(`/inventory/purchase-return/${id}/items`) || []
    items.value = (Array.isArray(its) ? its : (its?.records || [])).map((it: any) => ({
      productId: it.productId,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      amount: it.amount,
      remark: it.remark,
    }))
    // 查询每个明细产品的现有库存
    for (const item of items.value) {
      if (item.productId) {
        try {
          const p: any = { productId: item.productId }
          if (form.warehouseId) p.warehouseId = form.warehouseId
          const r = await request.get<any, any>('/warehouse/stock/page', { params: p })
          const arr = r?.records || []
          item._stock = arr.reduce((s: number, x: any) => s + (Number(x.quantity) || 0), 0)
        } catch { item._stock = undefined }
      }
    }
  } catch { /* */ }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    if (items.value.some(it => !it.productId)) { ElMessage.warning('请选择产品'); return }
    if (items.value.some(it => !it.quantity || Number(it.quantity) <= 0)) { ElMessage.warning('产品数量必须大于0'); return }
    const total = items.value.reduce((s, it) => s + (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0), 0)
    submitLoading.value = true
    try {
      const body = {
        supplierId: form.supplierId,
        warehouseId: form.warehouseId,
        returnDate: form.returnDate,
        remark: form.remark,
        totalAmount: total,
        items: items.value.map(it => ({
          productId: it.productId,
          qualityType: it.qualityType,
          quantity: it.quantity,
          unitPrice: it.unitPrice,
          amount: (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0),
          remark: it.remark,
        }))
      }
      if (isEdit) {
        await request.put(`/inventory/purchase-return/${Number(route.query.id)}`, body)
      } else {
        await request.post('/inventory/purchase-return', body)
      }
      ElMessage.success(isEdit ? '更新成功' : '新增成功')
      tabStore.removeTab(route.fullPath)
      router.push('/inventory/purchase-return')
    } catch (e: any) { ElMessage.error(e?.message || (isEdit ? '更新失败' : '新增失败')) }
    finally { submitLoading.value = false }
  })
}

function handleCancel() {
  tabStore.removeTab(route.fullPath)
  router.push('/inventory/purchase-return')
}

async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }

onMounted(() => {
  loadSuppliers()
  loadWarehouses()
  loadProducts()
  loadQualityTypes()
  if (isEdit) {
    tabStore.updateTabTitle(route.fullPath, '编辑成品退货单')
    document.title = '编辑成品退货单 - 北辰ERP管理系统'
    loadReturnData()
  }
})
</script>

<style scoped>

</style>
