<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <span>{{ isEdit ? '编辑销售退货单' : '新增销售退货单' }}</span>
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

        <el-divider content-position="left">退货明细（退回均为不良品）</el-divider>
        <el-table :data="form.items" border>
          <el-table-column label="产品" min-width="220">
            <template #default="{ row }">
              <RemoteSelect v-model="row.productId" :fetch="fetchProducts" placeholder="请选择" style="width: 100%"
                @change="(id: number) => onProductChange(row, id)" />
            </template>
          </el-table-column>
          <el-table-column label="品质等级" width="110" align="center">
            <template #default>
              <el-tag type="danger">不良品</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="退货数量" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="2" :step="1" style="width: 100%" />
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
          <span style="margin-left: 16px">合计金额：<b>{{ totalAmount }}</b></span>
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
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import {
  getSaleReturn,
  getSaleReturnItems,
  createSaleReturn,
  updateSaleReturn,
} from '@/api/sale'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const saving = ref(false)
const isEdit = ref(false)

// Odoo 风格：下拉框展开/搜索时实时查库（不预缓存全量）
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })

// 列表/拼装用的本地轻量列表（组件内维护，不再依赖全局 optionsStore）
const customers = ref<{ id: number; name: string }[]>([])
const warehouses = ref<{ id: number; warehouseName: string }[]>([])
const products = ref<{ id: number; name: string }[]>([])
async function loadCustomers() { try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] } }
async function loadWarehouses() { try { const r: any = await fetchWarehouses(''); warehouses.value = r?.records || [] } catch { warehouses.value = [] } }
async function loadProducts() { try { const r: any = await fetchProducts(''); products.value = r?.records || [] } catch { products.value = [] } }

const form = reactive({
  id: undefined as number | undefined,
  customerId: undefined as number | undefined,
  customerName: '',
  warehouseId: undefined as number | undefined,
  returnDate: '',
  remark: '',
  items: [] as any[],
})

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
  form.items.push({ productId: undefined, quantity: 1, unitPrice: 0, remark: '' })
}
function removeItem(i: number) {
  form.items.splice(i, 1)
}

function onCustomerChange(id: number) {
  const c = customers.value.find((x) => x.id === id)
  form.customerName = c ? c.name : ''
}
function onProductChange(row: any, id: number) {
  const p = products.value.find((x) => x.id === id)
  row.productName = p ? p.name : ''
}

async function loadEdit(id: number) {
  const head = await getSaleReturn(id)
  Object.assign(form, {
    id: head.id,
    customerId: head.customerId,
    customerName: head.customerName,
    warehouseId: head.warehouseId,
    returnDate: head.returnDate,
    remark: head.remark,
  })
  const items = await getSaleReturnItems(id)
  form.items = (items || []).map((it) => ({
    productId: it.productId,
    productName: it.productName,
    quantity: it.quantity,
    unitPrice: it.unitPrice,
    remark: it.remark,
  }))
}

function buildPayload() {
  return {
    customerId: form.customerId,
    warehouseId: form.warehouseId,
    returnDate: form.returnDate,
    remark: form.remark,
    items: form.items.map((it) => ({
      productId: it.productId,
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
  saving.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await updateSaleReturn(form.id!, payload)
      ElMessage.success('保存成功')
    } else {
      await createSaleReturn(payload)
      ElMessage.success('新增成功')
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
  } else {
    form.returnDate = new Date().toISOString().slice(0, 10)
    addItem()
  }
})
</script>

<style scoped>
.footer { margin-top: 20px; text-align: right; }
</style>
