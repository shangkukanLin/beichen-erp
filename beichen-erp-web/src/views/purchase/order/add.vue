<template>
  <div class="purchase-add">
    <el-card shadow="never">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.supplierId" placeholder="请选择" filterable style="width:100%">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入库仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择" filterable style="width:100%">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订单日期">
              <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税率(%)">
              <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">产品明细</el-divider>
        <div style="margin-bottom:8px">
          <el-button type="primary" @click="addItem">添加明细</el-button>
        </div>
        <el-table :data="items" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="产品" min-width="180">
            <template #default="{ row }">
              <el-select v-model="row.productId" placeholder="选择产品" filterable remote :remote-method="loadMaterials"
                style="width:100%" @change="(v: number) => onMaterialChange(v, row)">
                <el-option v-for="m in materialOptions" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="spec" label="规格" width="100" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="数量" width="120">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :step="1" :precision="0" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ ((Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="items.splice($index, 1)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-form>

      <div style="text-align:center;margin-top:24px">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">提交</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'PurchaseAdd' })
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import type { PurchaseOrder, PurchaseOrderItem } from '@/api/purchase'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const suppliers = ref<any[]>([])
const warehouses = ref<any[]>([])
const materialOptions = ref<any[]>([])
const items = ref<PurchaseOrderItem[]>([])

const form = reactive<PurchaseOrder>({
  supplierId: undefined,
  warehouseId: undefined,
  orderDate: new Date().toISOString().slice(0, 10),
  taxIncluded: 0,
  taxRate: 0,
  remark: ''
})

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }],
}

function addItem() {
  items.value.push({ productId: undefined, materialName: '', spec: '', unit: '', quantity: 0, unitPrice: 0, amount: 0, remark: '' })
}

async function loadMaterials(query?: string) {
  try {
    const params: any = { pageSize: 100 }
    if (query) params.name = query
    const res = await request.get<any, any>('/product/page', { params })
    materialOptions.value = res?.records || []
  } catch { materialOptions.value = [] }
}

function onMaterialChange(val: number, row: PurchaseOrderItem) {
  const m = materialOptions.value.find((x: any) => x.id === val)
  if (m) {
    row.productId = m.id
    row.materialName = m.name
    row.spec = m.spec
    row.unit = m.unit
  }
}

async function loadSuppliers() {
  try {
    const res = await request.get('/supplier/page', { params: { pageSize: 200, supplierType: 'product' } })
    suppliers.value = res?.records || []
  } catch { suppliers.value = [] }
}

async function loadWarehouses() {
  try {
    const res = await request.get('/inventory/warehouse/page', { params: { pageSize: 200 } })
    warehouses.value = res?.records || []
  } catch { warehouses.value = [] }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    if (items.value.some(it => !it.productId)) { ElMessage.warning('请选择产品'); return }
    if (items.value.some(it => !it.quantity || Number(it.quantity) <= 0)) { ElMessage.warning('产品数量必须大于0'); return }
    submitLoading.value = true
    try {
      const body = {
        ...form,
        items: items.value.map(it => ({
          productId: it.productId,
          materialName: it.materialName,
          spec: it.spec,
          unit: it.unit,
          quantity: it.quantity,
          unitPrice: it.unitPrice,
          remark: it.remark,
        }))
      }
      await request.post('/inventory/purchase', body)
      ElMessage.success('新增成功')
      router.back()
    } catch (e: any) { ElMessage.error(e?.message || '新增失败') }
    finally { submitLoading.value = false }
  })
}

onMounted(() => {
  loadSuppliers()
  loadWarehouses()
  loadMaterials()
})
</script>

<style scoped>

</style>
