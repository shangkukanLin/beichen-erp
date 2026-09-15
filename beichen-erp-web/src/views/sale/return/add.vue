<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <span>{{ isEdit ? '编辑销售退单' : '新增销售退单' }}</span>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户" prop="customerId">
              <RemoteSelect v-model="form.customerId" :fetch="fetchCustomers" placeholder="请选择客户" style="width: 100%"
                @change="onCustomerChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择仓库" style="width: 100%" />
              <div style="font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.4;">提示：销售退货只能退到售后仓</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联销售单">
              <RemoteSelect v-model="form.saleOrderId" :fetch="fetchSaleOrders" label-key="code" placeholder="选填，可追溯原销售单" style="width: 100%" :disabled="!form.customerId" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货日期" prop="returnDate">
              <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD"
                placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" style="max-width: 600px" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="是否收费">
              <el-switch v-model="form.chargeFlag" :active-value="1" :inactive-value="0"
                active-text="收费" inactive-text="不收费" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="收费类型" :required="form.chargeFlag === 1">
              <el-select v-model="form.chargeType" placeholder="请选择" clearable style="width:100%"
                :disabled="form.chargeFlag !== 1">
                <el-option v-for="o in chargeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="收费金额" :required="form.chargeFlag === 1">
              <el-input-number v-model="form.chargeAmount" :min="0" :precision="2" :step="10"
                controls-position="right" style="width:100%" :disabled="form.chargeFlag !== 1" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="收费说明">
              <el-input v-model="form.chargeReason" placeholder="选填，如：退货运费 / 服务费 / 品质差价"
                :disabled="form.chargeFlag !== 1" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">退货明细</el-divider>
        <el-table :data="form.items" border>
          <el-table-column label="SKU" width="130">
            <template #default="{ row }">
              <span v-if="row.sku">{{ row.sku }}</span>
              <span v-else style="color:var(--app-text-secondary)">自动生成</span>
            </template>
          </el-table-column>
          <el-table-column label="产品" min-width="220">
            <template #default="{ row }">
              <RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="请选择（可输SKU）" style="width: 100%"
                @change="(id: number) => onProductChange(row, id)" />
            </template>
          </el-table-column>
          <el-table-column label="品质等级" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="ProductQualityTypeTag[row.qualityType] || 'info'">{{ ProductQualityTypeLabel[row.qualityType] || '待分类' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可退数量" width="110" align="center">
            <template #default="{ row }">
              <span v-if="row.canReturn !== undefined">{{ row.canReturn }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="退货数量" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="2" :step="1" :max="row.canReturn !== undefined ? row.canReturn : undefined" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="1" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="130" align="right">
            <template #default="{ row }">{{ lineAmount(row) }}</template>
          </el-table-column>
          <el-table-column label="备注" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.remark" placeholder="选填" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 8px">
          <el-button type="primary" plain :icon="Plus" @click="addItem">添加明细</el-button>
          <el-button type="success" plain :icon="Download" :disabled="!form.saleOrderId" @click="loadFromSaleOrder">从销售单带入明细</el-button>
          <span style="margin-left: 16px">合计金额：<b>{{ totalAmount }}</b></span>
          <span style="margin-left: 16px; color: #909399; font-size: 12px">
            折损收款请在「退货整理单」上填写（整理后才知道 B/C/不良 各多少）
          </span>
        </div>
      </el-form>
      <div class="footer">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Download } from '@element-plus/icons-vue'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { ProductQualityTypeLabel, ProductQualityTypeTag, WarehouseType, SALE_RETURN_DIRTY_KEY,
  ExchangeChargeType, ExchangeChargeTypeLabel } from '@/api/enums'
import { productLabel } from '@/api/product'
import {
  getSaleOrder,
  getSaleReturn,
  getSaleReturnItems,
  createSaleReturn,
  updateSaleReturn,
  getSaleReturnSaleOrders,
  getSaleReturnSaleOrderItems,
} from '@/api/sale'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const saving = ref(false)
const isEdit = ref(false)

// Odoo 风格：下拉框展开/搜索时实时查库（不预缓存全量）
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseType: WarehouseType.AFTER_SALE } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })
const fetchSaleOrders = (kw: string) => request.get('/sale/return/sale-orders', { params: { pageSize: 200, customerId: form.customerId } })

// 列表/拼装用的本地轻量列表（组件内维护，不再依赖全局 optionsStore）
const customers = ref<{ id: number; name: string }[]>([])
const warehouses = ref<{ id: number; warehouseName: string }[]>([])
const products = ref<{ id: number; name: string; sku?: string }[]>([])
async function loadCustomers() { try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] } }
async function loadWarehouses() { try { const r: any = await fetchWarehouses(''); warehouses.value = r?.records || [] } catch { warehouses.value = [] } }
async function loadProducts() { try { const r: any = await fetchProducts(''); products.value = r?.records || [] } catch { products.value = [] } }

const form = reactive({
  id: undefined as number | undefined,
  customerId: undefined as number | undefined,
  customerName: '',
  warehouseId: undefined as number | undefined,
  saleOrderId: undefined as number | undefined,
  saleOrderCode: '',
  returnDate: '',
  chargeFlag: 0,
  chargeType: '' as string,
  chargeAmount: 0,
  chargeReason: '',
  remark: '',
  items: [] as any[],
})

const chargeTypeOptions = computed(() =>
  Object.values(ExchangeChargeType).map((v) => ({
    value: v, label: ExchangeChargeTypeLabel[v] || v
  }))
)

const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择退货仓库', trigger: 'change' }],
  returnDate: [{ required: true, message: '请选择退货日期', trigger: 'change' }],
}

const totalAmount = computed(() => {
  const sum = (form.items || []).reduce((acc, it) => acc + (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0), 0)
  return sum.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
})

function lineAmount(row: any) {
  const v = (Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function addItem() {
  form.items.push({ productId: undefined, quantity: 1, unitPrice: 0, remark: '', qualityType: 'PENDING' })
}
function removeItem(i: number) {
  form.items.splice(i, 1)
}

function onCustomerChange(id: number) {
  const c = customers.value.find((x) => x.id === id)
  form.customerName = c ? c.name : ''
  // 客户变更后，原销售单与明细失效，清空
  form.saleOrderId = undefined
  form.saleOrderCode = ''
  form.items = []
  addItem()
}

/** 从销售单带入明细（含可退数量） */
async function loadFromSaleOrder() {
  if (!form.saleOrderId) { ElMessage.warning('请先选择销售单'); return }
  try {
    const rows = await getSaleReturnSaleOrderItems(form.saleOrderId)
    form.items = (rows || []).map((r) => ({
      productId: r.productId,
      productName: r.productName,
      saleOrderItemId: r.saleOrderItemId,
      canReturn: Number(r.canReturn),
      quantity: 0,
      unitPrice: Number(r.unitPrice || 0),
      remark: '',
      qualityType: 'PENDING',
    }))
    if (!form.items.length) ElMessage.info('该销售单暂无明细')
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '加载销售单明细失败')
  }
}
function onProductChange(row: any, id: number) {
  const p = products.value.find((x) => x.id === id)
  row.productName = p ? p.name : ''
  row.sku = p ? (p.sku || '') : ''
}

/**
 * 从销售单详情「退货」按钮跳转过来时（?saleOrderId=xxx）：
 * 反查销售单带出客户与单号，并自动载入可退明细，省去手工选择与录入。
 */
async function initFromSaleOrder(saleOrderId: number) {
  try {
    const so: any = await getSaleOrder(saleOrderId)
    if (!so) { ElMessage.warning('来源销售单不存在'); return }
    form.saleOrderId = so.id
    form.saleOrderCode = so.code || ''
    form.customerId = so.customerId
    // 退货必须入售后仓，与销售单的出库仓不同，故仓库不预填，由用户选择
    await loadFromSaleOrder()
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '加载来源销售单失败')
  }
}

async function loadEdit(id: number) {
  const head = await getSaleReturn(id)
  Object.assign(form, {
    id: head.id,
    customerId: head.customerId,
    customerName: head.customerName,
    warehouseId: head.warehouseId,
    saleOrderId: head.saleOrderId,
    saleOrderCode: head.saleOrderCode,
    returnDate: head.returnDate,
    chargeFlag: Number(head.chargeFlag || 0),
    chargeType: head.chargeType || '',
    chargeAmount: Number(head.chargeAmount || 0),
    chargeReason: head.chargeReason || '',
    remark: head.remark,
  })
  const items = await getSaleReturnItems(id)
  form.items = (items || []).map((it) => ({
    productId: it.productId,
    productName: it.productName,
    saleOrderItemId: it.saleOrderItemId,
    quantity: it.quantity,
    unitPrice: it.unitPrice,
    remark: it.remark,
    qualityType: it.qualityType,
  }))
  // 若关联了销售单，回填可退数量（用于数量上限提示）
  if (form.saleOrderId) {
    try {
      const rows = await getSaleReturnSaleOrderItems(form.saleOrderId)
      const map = Object.fromEntries((rows || []).map((r) => [r.saleOrderItemId, Number(r.canReturn)]))
      form.items.forEach((it) => { it.canReturn = it.saleOrderItemId != null ? map[it.saleOrderItemId] : undefined })
    } catch { /* 忽略 */ }
  }
}

function buildPayload() {
  return {
    customerId: form.customerId,
    warehouseId: form.warehouseId,
    saleOrderId: form.saleOrderId,
    saleOrderCode: form.saleOrderCode,
    returnDate: form.returnDate,
    chargeFlag: Number(form.chargeFlag) === 1 ? 1 : 0,
    chargeType: Number(form.chargeFlag) === 1 ? form.chargeType : '',
    chargeAmount: Number(form.chargeFlag) === 1 ? Number(form.chargeAmount) : 0,
    chargeReason: Number(form.chargeFlag) === 1 ? (form.chargeReason || '') : '',
    remark: form.remark,
    items: form.items.map((it) => ({
      productId: it.productId,
      saleOrderItemId: it.saleOrderItemId,
      qualityType: it.qualityType || 'PENDING',
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      amount: (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0),
      remark: it.remark,
    })),
  }
}

async function submit() {
  await formRef.value.validate()
  if (!form.items.length) {
    ElMessage.warning('请至少添加一条退货明细')
    return
  }
  // 关联销售单时：数量不能超过可退数量
  if (form.saleOrderId) {
    for (const it of form.items) {
      if (it.saleOrderItemId != null && it.canReturn !== undefined && (Number(it.quantity) || 0) > Number(it.canReturn)) {
        ElMessage.warning(`产品「${it.productName || it.productId}」退货数量不能超过可退数量 ${it.canReturn}`)
        return
      }
    }
  }
  // 收费校验：选择收费时必须指定类型且金额 > 0（后端会再校验一次，此处提前给提示）
  const charged = Number(form.chargeFlag) === 1
  if (charged) {
    if (!form.chargeType) { ElMessage.warning('已选择收费，请选择收费类型'); return }
    if (!(Number(form.chargeAmount) > 0)) { ElMessage.warning('已选择收费，收费金额必须大于 0'); return }
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await updateSaleReturn(form.id!, payload)
      ElMessage.success('保存成功'); sessionStorage.setItem(SALE_RETURN_DIRTY_KEY, '1')
    } else {
      await createSaleReturn(payload)
      ElMessage.success('新增成功'); sessionStorage.setItem(SALE_RETURN_DIRTY_KEY, '1')
    }
    router.push('/sale/return')
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/sale/return')
}

onMounted(async () => {
  loadCustomers()
  loadWarehouses()
  loadProducts()
  const id = route.query.id
  if (id) {
    isEdit.value = true
    await loadEdit(Number(id))
    return
  }
  form.returnDate = localDate()
  // 从销售单详情页「退货」跳转：预填来源销售单并自动带入可退明细
  const soId = route.query.saleOrderId
  if (soId !== undefined && soId !== '') {
    await initFromSaleOrder(Number(soId))
  }
  if (!form.items.length) addItem()
})
</script>

<style scoped>
.footer { margin-top: 20px; text-align: right; }
</style>
