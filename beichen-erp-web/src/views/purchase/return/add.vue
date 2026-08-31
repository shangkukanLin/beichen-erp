<template>
  <div class="purchase-add">
    <el-card shadow="never" style="margin-top:16px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-alert v-if="form.purchaseOrderCode" type="success" :closable="false" style="margin-bottom:12px"
          :title="`来源采购单：${form.purchaseOrderCode}（已自动带入采购明细，可修改）`" />
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="供货商" prop="supplierId">
              <RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" placeholder="请选择" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择" style="width:100%" />
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
          <el-button type="primary" :icon="'Plus'" @click="addItem">添加明细</el-button>
          <el-button type="success" :icon="'Download'" :disabled="!form.purchaseOrderId" @click="loadFromPurchaseOrder()">从采购单带入明细</el-button>
        </div>
        <el-table :data="items" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="SKU" width="130">
            <template #default="{ row }">
              <span v-if="row.sku">{{ row.sku }}</span>
              <span v-else style="color:var(--app-text-secondary)">自动生成</span>
            </template>
          </el-table-column>
          <el-table-column label="产品" min-width="200" prop="productId">
            <template #default="{ row }">
              <el-select v-model="row.productId" placeholder="选择产品（可输SKU）" filterable remote :remote-method="loadProducts"
                style="width:100%" @change="(v: number) => onProductChange(v, row)">
                <el-option v-for="m in productOptions" :key="m.id" :label="productLabel(m)" :value="m.id" />
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
          <el-table-column label="可退数量" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.canReturn !== undefined">{{ row.canReturn }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="退货数量" width="140">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="1" :step="1" :precision="0" :max="row.canReturn !== undefined ? row.canReturn : undefined" controls-position="right" style="width:100%" @change="calcAmount" /></template>
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
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { PURCHASE_RETURN_DIRTY_KEY, WarehouseCategory } from '@/api/enums'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useTabStore } from '@/stores/tabs'
import request from '@/utils/request'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { getPurchaseReturnPurchaseOrderItems } from '@/api/purchase'
import RemoteSelect from '@/components/RemoteSelect.vue'

interface ReturnItem {
  productId?: number
  sku?: string
  qualityType?: string
  purchaseOrderItemId?: number
  canReturn?: number
  _stock?: number
  quantity?: number
  unitPrice?: number
  remark?: string
}

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()
const isEdit = !!route.query.id
const fromOrder = Number(route.query.fromOrder || 0)
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const qualityOptions = ref<QualityOption[]>([])
const productOptions = ref<any[]>([])
const items = ref<ReturnItem[]>([])
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw, supplierType: 'product' } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })

const form = reactive({
  supplierId: undefined as number | undefined,
  warehouseId: undefined as number | undefined,
  purchaseOrderId: undefined as number | undefined,
  purchaseOrderCode: '' as string,
  returnDate: new Date().toISOString().slice(0, 10) as string,
  remark: '' as string,
})

/** 从采购单带入明细（含可退数量），退货仓库默认取采购单入库仓库 */
async function loadFromPurchaseOrder(orderId?: number) {
  const oid = orderId || form.purchaseOrderId
  if (!oid) { ElMessage.warning('未关联采购单'); return }
  try {
    const order: any = await request.get(`/inventory/purchase/${oid}`)
    if (order) {
      form.supplierId = order.supplierId
      form.warehouseId = order.warehouseId
      form.purchaseOrderId = order.id
      form.purchaseOrderCode = order.code
    }
    const rows = await getPurchaseReturnPurchaseOrderItems(oid)
    items.value = (rows || []).map((r: any) => ({
      productId: r.productId,
      qualityType: r.qualityType || 'A',
      purchaseOrderItemId: r.purchaseOrderItemId,
      canReturn: Number(r.canReturn),
      quantity: 0,
      unitPrice: Number(r.unitPrice || 0),
      remark: '',
    }))
    if (!items.value.length) ElMessage.info('该采购单暂无明细')
  } catch (e: any) { ElMessage.error(e?.message || '加载采购单失败') }
}

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供货商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择退货仓库', trigger: 'change' }],
}

function addItem() {
  items.value.push({ productId: undefined, qualityType: 'A', quantity: 1, unitPrice: 0, remark: '' })
}

async function loadProducts(query?: string) {
  try {
    const params: any = { pageSize: 100 }
    if (query) params.keyword = query
    const res = await request.get<any, any>('/product/page', { params })
    productOptions.value = res?.records || []
  } catch { productOptions.value = [] }
}

async function onProductChange(val: number, row: ReturnItem) {
  const m = productOptions.value.find((x: any) => x.id === val)
  if (m) {
    row.productId = m.id
    row.sku = m.sku || ''
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

async function loadReturnData() {
  const id = Number(route.query.id)
  if (!id) return
  try {
    const order = await request.get(`/inventory/purchase-return/${id}`)
    if (order) {
      form.supplierId = order.supplierId
      form.warehouseId = order.warehouseId
      form.purchaseOrderId = order.purchaseOrderId
      form.purchaseOrderCode = order.purchaseOrderCode || ''
      form.returnDate = order.returnDate
      form.remark = order.remark || ''
    }
    const its = await request.get(`/inventory/purchase-return/${id}/items`) || []
    items.value = (Array.isArray(its) ? its : (its?.records || [])).map((it: any) => ({
      productId: it.productId,
      qualityType: it.qualityType,
      purchaseOrderItemId: it.purchaseOrderItemId,
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
    // 关联采购单时：数量不能超过可退数量
    if (form.purchaseOrderId) {
      for (const it of items.value) {
        if (it.purchaseOrderItemId != null && it.canReturn !== undefined && (Number(it.quantity) || 0) > Number(it.canReturn)) {
          ElMessage.warning(`产品（${it.productId}）退货数量不能超过可退数量 ${it.canReturn}`)
          return
        }
      }
    }
    const total = items.value.reduce((s, it) => s + (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0), 0)
    submitLoading.value = true
    try {
      const body = {
        supplierId: form.supplierId,
        warehouseId: form.warehouseId,
        purchaseOrderId: form.purchaseOrderId,
        purchaseOrderCode: form.purchaseOrderCode,
        returnDate: form.returnDate,
        remark: form.remark,
        totalAmount: total,
        items: items.value.map(it => ({
          productId: it.productId,
          qualityType: it.qualityType,
          purchaseOrderItemId: it.purchaseOrderItemId,
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
      ElMessage.success(isEdit ? '更新成功' : '新增成功'); sessionStorage.setItem(PURCHASE_RETURN_DIRTY_KEY, '1')
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

// 顶栏"刷新数据"：重新加载品质下拉
async function handleRefreshData() { await loadQualityTypes() }
onMounted(() => {
  loadProducts()
  loadQualityTypes()
  if (fromOrder) {
    tabStore.updateTabTitle(route.fullPath, '从采购单开退货单')
    document.title = '从采购单开退货单 - 北辰ERP管理系统'
    loadFromPurchaseOrder(fromOrder)
  } else if (isEdit) {
    tabStore.updateTabTitle(route.fullPath, '编辑采购退货单')
    document.title = '编辑采购退货单 - 北辰ERP管理系统'
    loadReturnData()
  }
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))
</script>

<style scoped>

</style>
