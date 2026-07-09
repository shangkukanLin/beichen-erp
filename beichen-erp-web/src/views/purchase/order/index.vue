<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { getMaterialPage, type Material } from '@/api/material'
import {
  getPurchaseOrderPage,
  getPurchaseOrderItems,
  createPurchaseOrder,
  updatePurchaseOrder,
  auditPurchaseOrder,
  cancelPurchaseOrder,
  type PurchaseOrder,
  type PurchaseOrderItem
} from '@/api/purchase'

const query = reactive({ code: '', supplierId: '' as string | number, status: '' as string })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<PurchaseOrder[]>([])

const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '已审核', value: '已审核' },
  { label: '已入库', value: '已入库' },
  { label: '已作废', value: '已作废' }
]

const suppliers = ref<{ id: number; name: string }[]>([])
const warehouses = ref<{ id: number; warehouseName: string }[]>([])
const materialOptions = ref<Material[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增采购订单')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<PurchaseOrder>({
  supplierId: undefined,
  warehouseId: undefined,
  orderDate: '',
  taxIncluded: 0,
  taxRate: 0,
  remark: ''
})
const items = ref<PurchaseOrderItem[]>([])

const detailVisible = ref(false)
const detailData = ref<PurchaseOrder>({})
const detailItems = ref<PurchaseOrderItem[]>([])

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }]
}

async function loadSuppliers() {
  try {
    const res = await request.get('/supplier/page', { params: { pageSize: 200 } })
    suppliers.value = res?.records || []
  } catch { suppliers.value = [] }
}
async function loadWarehouses() {
  try {
    const res = await request.get('/inventory/warehouse/page', { params: { pageSize: 200 } })
    warehouses.value = res?.records || []
  } catch { warehouses.value = [] }
}
async function loadMaterials(keyword?: string) {
  try {
    const res = await getMaterialPage({ pageNum: 1, pageSize: 100, name: keyword || '' })
    materialOptions.value = res?.records || []
  } catch { materialOptions.value = [] }
}

async function loadData() {
  tableLoading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.supplierId !== '' && query.supplierId !== null) params.supplierId = query.supplierId
    if (query.status) params.status = query.status
    const res = await getPurchaseOrderPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch {
    tableData.value = []
    pagination.total = 0
  } finally {
    tableLoading.value = false
  }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.supplierId = ''; query.status = ''; pagination.pageNum = 1; loadData() }

function resetForm() {
  Object.assign(form, { id: undefined, supplierId: undefined, warehouseId: undefined, orderDate: '', taxIncluded: 0, taxRate: 0, remark: '' })
  items.value = []
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增采购订单'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleEdit(row: PurchaseOrder) {
  resetForm()
  Object.assign(form, row)
  dialogTitle.value = '编辑采购订单'
  dialogVisible.value = true
  formRef.value?.clearValidate()
  try {
    const res = await getPurchaseOrderItems(row.id as number)
    items.value = res || []
  } catch { items.value = [] }
}

function addItem() {
  items.value.push({ materialId: undefined, materialName: '', spec: '', unit: '', quantity: 0, unitPrice: 0, amount: 0, remark: '' })
}
function removeItem(index: number) {
  items.value.splice(index, 1)
}
function onMaterialChange(val: number, row: PurchaseOrderItem) {
  const m = materialOptions.value.find(x => x.id === val)
  if (m) {
    row.materialId = m.id as number
    row.materialCode = m.code
    row.materialName = m.name
    row.spec = m.spec
    row.unit = m.unit
  }
}
function itemAmount(row: PurchaseOrderItem) {
  const q = Number(row.quantity) || 0
  const p = Number(row.unitPrice) || 0
  return (q * p).toFixed(2)
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    submitLoading.value = true
    try {
      const payload = { order: { ...form }, items: items.value }
      if (form.id) {
        await updatePurchaseOrder(form.id as number, payload)
        ElMessage.success('修改成功')
      } else {
        await createPurchaseOrder(payload)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } catch { /* 拦截器已提示 */ } finally { submitLoading.value = false }
  })
}

async function handleAudit(row: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认审核采购订单「${row.code}」？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditPurchaseOrder(row.id as number)
    ElMessage.success('审核成功')
    loadData()
  } catch { /* 取消 */ }
}
async function handleCancel(row: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认作废采购订单「${row.code}」？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelPurchaseOrder(row.id as number)
    ElMessage.success('已作废')
    loadData()
  } catch { /* 取消 */ }
}
async function handleDetail(row: PurchaseOrder) {
  detailData.value = { ...row }
  try {
    const res = await getPurchaseOrderItems(row.id as number)
    detailItems.value = res || []
  } catch { detailItems.value = [] }
  detailVisible.value = true
}

function handleSizeChange(val: number) { pagination.pageSize = val; pagination.pageNum = 1; loadData() }
function handleCurrentChange(val: number) { pagination.pageNum = val; loadData() }

function statusType(s?: string) {
  if (s === '草稿') return 'info'
  if (s === '已审核' || s === '已入库') return 'success'
  if (s === '已作废') return 'danger'
  return ''
}
function supplierName(id?: number) {
  const s = suppliers.value.find(x => x.id === id)
  return s ? s.name : ''
}
function warehouseName(id?: number) {
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : ''
}
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

onMounted(() => { loadSuppliers(); loadWarehouses(); loadMaterials(); loadData() })
</script>

<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="单号">
          <el-input v-model="query.code" placeholder="请输入单号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="query.supplierId" placeholder="请选择" clearable filterable style="width:160px">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="请选择" clearable style="width:120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="tableLoading" :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="code" label="单号" min-width="150" />
        <el-table-column label="供应商" min-width="140">
          <template #default="{ row }">{{ supplierName(row.supplierId) }}</template>
        </el-table-column>
        <el-table-column label="入库仓库" min-width="120">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="orderDate" label="订单日期" width="120" align="center" />
        <el-table-column prop="totalAmount" label="总金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.status === '草稿'" type="success" link @click="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === '草稿'" type="warning" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === '草稿'" type="danger" link @click="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px" :close-on-click-modal="false" @open="loadMaterials()">
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

        <el-divider content-position="left">明细</el-divider>
        <div style="margin-bottom:8px">
          <el-button type="primary" :icon="'Plus'" @click="addItem">添加明细</el-button>
        </div>
        <el-table :data="items" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="物料" min-width="180">
            <template #default="{ row, $index }">
              <el-select v-model="row.materialId" placeholder="选择物料" filterable remote :remote-method="loadMaterials"
                style="width:100%" @change="(v:number)=>onMaterialChange(v, row)">
                <el-option v-for="m in materialOptions" :key="m.id" :label="`${m.name}(${m.code})`" :value="m.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="spec" label="规格" width="100" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="数量" width="120">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="2" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ itemAmount(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="采购订单详情" size="60%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ detailData.code }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detailData.status)">{{ detailData.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="供应商">{{ supplierName(detailData.supplierId) }}</el-descriptions-item>
        <el-descriptions-item label="入库仓库">{{ warehouseName(detailData.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="订单日期">{{ detailData.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="总金额">{{ fmt(detailData.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailData.remark }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">明细</el-divider>
      <el-table :data="detailItems" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="spec" label="规格" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding: 16px; }
.query-form { display: flex; flex-wrap: wrap; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
