<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { getQualityTypes, type QualityOption } from '@/api/product'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const qualityOptions = ref<QualityOption[]>([])
import {
  getPurchaseOrderPage,
  getPurchaseOrderItems,
  createPurchaseOrder,
  updatePurchaseOrder,
  auditPurchaseOrder,
  cancelPurchaseOrder,
  unAuditPurchaseOrder,
  getOutsourceMaterialPage,
  type PurchaseOrder,
  type PurchaseOrderItem,
  type OutsourceMaterialOption,
  PurchaseStatus,
  PurchaseStatusLabel
} from '@/api/purchase'

const query = reactive({ code: '', supplierId: '' as string | number, status: '' as string | number })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<PurchaseOrder[]>([])

const statusOptions = [
  { label: PurchaseStatusLabel[PurchaseStatus.DRAFT], value: PurchaseStatus.DRAFT },
  { label: PurchaseStatusLabel[PurchaseStatus.AUDITED], value: PurchaseStatus.AUDITED },
  { label: PurchaseStatusLabel[PurchaseStatus.CANCELLED], value: PurchaseStatus.CANCELLED }
]
function statusLabel(s?: number) {
  return s != null ? (PurchaseStatusLabel[s] || '') : ''
}

const materialOptions = ref<OutsourceMaterialOption[]>([])
const supplierOptions = ref<{ id: number; name: string }[]>([])
const warehouseOptions = ref<{ id: number; warehouseName: string }[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增采购单')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<PurchaseOrder>({
  supplierId: undefined,
  warehouseId: undefined,
  orderDate: new Date().toISOString().slice(0, 10),
  taxIncluded: 0,
  taxRate: 0,
  remark: ''
})
const items = ref<PurchaseOrderItem[]>([])

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }]
}

const fetchMaterials = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })
const loadMaterials = async (keyword?: string) => {
  try {
    const res = await getOutsourceMaterialPage({ pageNum: 1, pageSize: 100, materialName: keyword || '' })
    materialOptions.value = res?.records || []
  } catch { materialOptions.value = [] }
}
const loadSupplierOptions = async () => { try { const r: any = await fetchSuppliers(''); supplierOptions.value = r?.records || [] } catch { supplierOptions.value = [] } }
const loadWarehouseOptions = async () => { try { const r: any = await fetchWarehouses(''); warehouseOptions.value = r?.records || [] } catch { warehouseOptions.value = [] } }

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
  Object.assign(form, { id: undefined, supplierId: undefined, warehouseId: undefined, orderDate: new Date().toISOString().slice(0, 10), taxIncluded: 0, taxRate: 0, remark: '' })
  items.value = []
}

function handleAdd() {
  router.push('/inventory/purchase/add')
}

async function handleEdit(row: PurchaseOrder) {
  resetForm()
  Object.assign(form, row)
  dialogTitle.value = '编辑成品采购单'
  dialogVisible.value = true
  formRef.value?.clearValidate()
  try {
    const res = await getPurchaseOrderItems(row.id as number)
    items.value = res || []
  } catch { items.value = [] }
}

function addItem() {
  items.value.push({ productId: undefined, qualityType: 'A', materialName: '', spec: '', unit: '', quantity: 0, unitPrice: 0, amount: 0, remark: '' })
}
function removeItem(index: number) {
  items.value.splice(index, 1)
}
function onMaterialChange(val: number, row: PurchaseOrderItem) {
  const m = materialOptions.value.find(x => x.id === val)
  if (m) {
    row.productId = m.id as number
    row.materialName = m.materialName
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
    await ElMessageBox.confirm(`确认审核采购单「${row.code}」？审核后将直接入库并生成应付。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditPurchaseOrder(row.id as number)
    ElMessage.success('审核成功')
    loadData()
  } catch { /* 取消 */ }
}
async function handleCancel(row: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认作废采购单「${row.code}」？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelPurchaseOrder(row.id as number)
    ElMessage.success('已作废')
    loadData()
  } catch { /* 取消 */ }
}
async function handleUnAudit(row: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认反审核采购单「${row.code}」？反审核后将冲回库存、清除应付台账，单据回到草稿状态。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await unAuditPurchaseOrder(row.id as number)
    ElMessage.success('反审核成功，已回到草稿状态')
    loadData()
  } catch { /* 取消 */ }
}
function handleDetail(row: PurchaseOrder) {
  router.push(`/inventory/purchase/detail/${row.id}`)
}

function handleSupplierClick(id?: number) {
  if (id) router.push(`/supplier/detail/${id}`)
}
function handleWarehouseClick(id?: number) {
  if (id) router.push(`/inventory/warehouse/detail/${id}`)
}

function handleSizeChange(val: number) { pagination.pageSize = val; pagination.pageNum = 1; loadData() }
function handleCurrentChange(val: number) { pagination.pageNum = val; loadData() }

function statusType(s?: number): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined {
  if (s === PurchaseStatus.DRAFT) return 'info'
  if (s === PurchaseStatus.AUDITED) return 'success'
  if (s === PurchaseStatus.CANCELLED) return 'danger'
  return undefined
}
function supplierName(id?: number) {
  const s = supplierOptions.value.find(x => x.id === id)
  return s ? s.name : ''
}
function warehouseName(id?: number) {
  const w = warehouseOptions.value.find(x => x.id === id)
  return w ? w.warehouseName : ''
}
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }

onMounted(() => { loadSupplierOptions(); loadWarehouseOptions(); loadMaterials(); loadQualityTypes(); loadData() })

</script>

<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="单号">
          <el-input v-model="query.code" placeholder="请输入单号" clearable @keyup.enter="handleQuery" style="width:160px" />
        </el-form-item>
        <el-form-item label="供应商">
          <RemoteSelect v-model="query.supplierId" :fetch="fetchSuppliers" placeholder="请选择" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="请选择" clearable style="width:120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="tableLoading" :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="code" label="单号" min-width="150" />
        <el-table-column label="供应商" min-width="140">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleSupplierClick(row.supplierId)">{{ supplierName(row.supplierId) }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="入库仓库" min-width="120">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleWarehouseClick(row.warehouseId)">{{ warehouseName(row.warehouseId) }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="orderDate" label="订单日期" width="120" align="center" />
        <el-table-column prop="itemsSummary" label="采购明细" min-width="200" show-overflow-tooltip />
        <el-table-column prop="totalAmount" label="总金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.status === PurchaseStatus.DRAFT" type="success" link @click="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === PurchaseStatus.AUDITED" type="warning" link @click="handleUnAudit(row)">反审核</el-button>
            <el-button v-if="row.status === PurchaseStatus.DRAFT" type="warning" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === PurchaseStatus.DRAFT" type="danger" link @click="handleCancel(row)">作废</el-button>
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
              <RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.supplierId = undefined; router.push('/supplier/manage'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入库仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.warehouseId = undefined; router.push('/inventory/warehouse'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
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
          <el-table-column label="产品" min-width="180">
            <template #default="{ row, $index }">
              <RemoteSelect v-model="row.productId" :fetch="fetchMaterials" label-key="name" placeholder="选择物料" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.productId = undefined; router.push('/material'); return } onMaterialChange(v, row) }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </template>
          </el-table-column>
          <el-table-column prop="spec" label="规格" width="100" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="品质" width="90">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" size="small" style="width:100%">
                <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :step="1" :precision="0" controls-position="right" style="width:100%" /></template>
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
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding: 16px; }
.query-form { align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
