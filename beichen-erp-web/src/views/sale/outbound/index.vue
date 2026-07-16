<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { getMaterialPage, type Material } from '@/api/material'
import { listCustomers, type Customer } from '@/api/customer'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'

const router = useRouter()
import {
  getSaleOutboundPage, getSaleOutboundItems, createSaleOutbound, updateSaleOutbound, auditSaleOutbound, cancelSaleOutbound,
  type SaleOutbound, type SaleOutboundItem
} from '@/api/sale'

const query = reactive({ code: '', customerId: '' as string | number, status: '' as string })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<SaleOutbound[]>([])

const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '已审核', value: '已审核' },
  { label: '已作废', value: '已作废' }
]

const customers = ref<Customer[]>([])
const warehouses = ref<{ id: number; warehouseName: string }[]>([])
const materialOptions = ref<Material[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增销售出库')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SaleOutbound>({ orderId: undefined, customerId: undefined, warehouseId: undefined, outboundDate: '', remark: '' })
const items = ref<SaleOutboundItem[]>([])

const detailVisible = ref(false)
const detailData = ref<SaleOutbound>({})
const detailItems = ref<SaleOutboundItem[]>([])

const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择出库仓库', trigger: 'change' }]
}

async function loadCustomers() { try { const res = await listCustomers(); customers.value = res || [] } catch { customers.value = [] } }
async function loadWarehouses() { try { const res = await request.get('/inventory/warehouse/page', { params: { pageSize: 200 } }); warehouses.value = res?.records || [] } catch { warehouses.value = [] } }
async function loadMaterials(keyword?: string) { try { const res = await getMaterialPage({ pageNum: 1, pageSize: 100, name: keyword || '' }); materialOptions.value = res?.records || [] } catch { materialOptions.value = [] } }

async function loadData() {
  tableLoading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.customerId !== '' && query.customerId !== null) params.customerId = query.customerId
    if (query.status) params.status = query.status
    const res = await getSaleOutboundPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = []; pagination.total = 0 } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.customerId = ''; query.status = ''; pagination.pageNum = 1; loadData() }
function resetForm() { Object.assign(form, { id: undefined, orderId: undefined, customerId: undefined, warehouseId: undefined, outboundDate: '', remark: '' }); items.value = [] }
function handleAdd() { resetForm(); dialogTitle.value = '新增销售出库'; dialogVisible.value = true; formRef.value?.clearValidate() }
async function handleEdit(row: SaleOutbound) {
  resetForm(); Object.assign(form, row); dialogTitle.value = '编辑销售出库'; dialogVisible.value = true; formRef.value?.clearValidate()
  try { const res = await getSaleOutboundItems(row.id as number); items.value = res || [] } catch { items.value = [] }
}
function addItem() { items.value.push({ materialId: undefined, materialName: '', spec: '', unit: '', quantity: 0, unitPrice: 0, amount: 0, remark: '' }) }
function removeItem(index: number) { items.value.splice(index, 1) }
function onMaterialChange(val: number, row: SaleOutboundItem) {
  const m = materialOptions.value.find(x => x.id === val)
  if (m) { row.materialId = m.id as number; row.materialCode = m.code; row.materialName = m.name; row.spec = m.spec; row.unit = m.unit }
}
function itemAmount(row: SaleOutboundItem) { const q = Number(row.quantity) || 0; const p = Number(row.unitPrice) || 0; return (q * p).toFixed(2) }

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    submitLoading.value = true
    try {
      const payload = { outbound: { ...form }, items: items.value }
      if (form.id) { await updateSaleOutbound(form.id as number, payload); ElMessage.success('修改成功') }
      else { await createSaleOutbound(payload); ElMessage.success('新增成功') }
      dialogVisible.value = false; loadData()
    } catch { } finally { submitLoading.value = false }
  })
}
async function handleAudit(row: SaleOutbound) {
  try {
    await ElMessageBox.confirm(`确认审核销售出库「${row.code}」？审核后将扣减库存并生成应收。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditSaleOutbound(row.id as number); ElMessage.success('审核成功，已扣减库存并生成应收'); loadData()
  } catch { }
}
async function handleCancel(row: SaleOutbound) {
  try {
    await ElMessageBox.confirm(`确认作废销售出库「${row.code}」？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelSaleOutbound(row.id as number); ElMessage.success('已作废'); loadData()
  } catch { }
}
async function handleDetail(row: SaleOutbound) {
  detailData.value = { ...row }
  try { const res = await getSaleOutboundItems(row.id as number); detailItems.value = res || [] } catch { detailItems.value = [] }
  detailVisible.value = true
}
function handleSizeChange(val: number) { pagination.pageSize = val; pagination.pageNum = 1; loadData() }
function handleCurrentChange(val: number) { pagination.pageNum = val; loadData() }
function statusType(s?: string) { if (s === '草稿') return 'info'; if (s === '已审核') return 'success'; if (s === '已作废') return 'danger'; return '' }
function customerName(id?: number) { const c = customers.value.find(x => x.id === id); return c ? c.name : '' }
function warehouseName(id?: number) { const w = warehouses.value.find(x => x.id === id); return w ? w.warehouseName : '' }
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

onMounted(() => { loadCustomers(); loadWarehouses(); loadMaterials(); loadData() })
onActivated(() => { loadCustomers(); loadWarehouses(); loadMaterials(); loadData() })
</script>

<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="单号">
          <el-input v-model="query.code" placeholder="请输入单号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="query.customerId" placeholder="请选择" clearable filterable style="width:160px">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
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
        <el-table-column label="客户" min-width="140">
          <template #default="{ row }">{{ customerName(row.customerId) }}</template>
        </el-table-column>
        <el-table-column label="出库仓库" min-width="120">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="outboundDate" label="出库日期" width="120" align="center" />
        <el-table-column prop="totalAmount" label="总金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="center" fixed="right">
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
            <el-form-item label="客户" prop="customerId">
              <el-select v-model="form.customerId" placeholder="请选择" filterable style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.customerId = undefined; router.push('/inventory/customer'); return } }">
                <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出库仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择" filterable style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.warehouseId = undefined; router.push('/inventory/warehouse'); return } }">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出库日期">
              <el-date-picker v-model="form.outboundDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">明细</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" :icon="'Plus'" @click="addItem">添加明细</el-button></div>
        <el-table :data="items" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="物料" min-width="180">
            <template #default="{ row }">
              <el-select v-model="row.materialId" placeholder="选择物料" filterable remote :remote-method="loadMaterials"
                style="width:100%" @change="(v: number) => { if (v === ADD_MARKER) { row.materialId = undefined; router.push('/material'); return } onMaterialChange(v, row) }">
                <el-option v-for="m in materialOptions" :key="m.id" :label="`${m.name}(${m.code})`" :value="m.id" />
                <el-option label="+ 新增" :value="ADD_MARKER" />
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

    <el-drawer v-model="detailVisible" title="销售出库详情" size="60%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ detailData.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(detailData.status)">{{ detailData.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="客户">{{ customerName(detailData.customerId) }}</el-descriptions-item>
        <el-descriptions-item label="出库仓库">{{ warehouseName(detailData.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="出库日期">{{ detailData.outboundDate }}</el-descriptions-item>
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
