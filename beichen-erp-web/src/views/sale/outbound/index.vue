<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import type { OutsourceMaterialOption } from '@/api/purchase'
import { getQualityTypes, type QualityOption } from '@/api/product'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const qualityOptions = ref<QualityOption[]>([])
import {
  getSaleOutboundPage, createSaleOutbound, auditSaleOutbound, cancelSaleOutbound,
  type SaleOutbound, type SaleOutboundItem
} from '@/api/sale'

const query = reactive({ code: '', customerId: '' as string | number, status: '' as string })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<SaleOutbound[]>([])

const statusOptions = [
  { label: DocStatusLabel[DocStatus.DRAFT], value: DocStatus.DRAFT },
  { label: DocStatusLabel[DocStatus.AUDITED], value: DocStatus.AUDITED },
  { label: DocStatusLabel[DocStatus.CANCELLED], value: DocStatus.CANCELLED }
]

// Odoo 风格：下拉框展开/搜索时实时查库（不预缓存全量）
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })
const fetchMaterials = (kw: string) => request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw } })

// 列表/详情显示与拼装用的本地轻量列表（组件内维护，不再依赖全局 optionsStore）
const customers = ref<any[]>([])
const warehouses = ref<any[]>([])
const materialOptions = ref<OutsourceMaterialOption[]>([])

async function loadCustomers() { try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] } }
async function loadWarehouses() { try { const r: any = await fetchWarehouses(''); warehouses.value = r?.records || [] } catch { warehouses.value = [] } }

const dialogVisible = ref(false)
const dialogTitle = ref('新增销售出库')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SaleOutbound>({ orderId: undefined, customerId: undefined, warehouseId: undefined, outboundDate: '', remark: '' })
const items = ref<SaleOutboundItem[]>([])

/* 2026-09-24：原只读详情抽屉（detailVisible/detailData/detailItems）已升级为独立路由页 /sale/outbound/detail/:id */

const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择出库仓库', trigger: 'change' }]
}

async function loadMaterials(keyword?: string) { try { const res: any = await fetchMaterials(keyword || ''); materialOptions.value = res?.records || [] } catch { materialOptions.value = [] } }

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
/* 2026-09-24（用户口径）：列表弹窗只保留「新增」；草稿编辑与撤销类的反审核都已收进详情页
   （/sale/outbound/detail/:id），故 handleEdit / handleUnAudit 一并删除。 */
function addItem() { items.value.push({ materialId: undefined, qualityType: 'A', materialName: '', spec: '', unit: '', quantity: 0, unitPrice: 0, amount: 0, remark: '' }) }
function removeItem(index: number) { items.value.splice(index, 1) }
function onMaterialChange(val: number, row: SaleOutboundItem) {
  const m = materialOptions.value.find(x => x.id === val)
  if (m) { row.materialId = m.id as number; row.materialName = m.materialName; row.spec = m.spec; row.unit = m.unit }
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
      // 2026-09-24：列表弹窗只保留「新增」（草稿编辑已收进详情页）⇒ update 分支删除
      await createSaleOutbound(payload); ElMessage.success('已新增')
      dialogVisible.value = false; loadData()
    } catch { } finally { submitLoading.value = false }
  })
}
async function handleAudit(row: SaleOutbound) {
  try {
    await ElMessageBox.confirm(`确认审核销售出库「${row.code}」？本单仅为出库凭证：不扣减库存（库存已在销售单审核时扣减）、不生成应收。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditSaleOutbound(row.id as number); ElMessage.success('已审核（出库凭证，不涉及库存）'); loadData()
  } catch { }
}
async function handleCancel(row: SaleOutbound) {
  try {
    await ElMessageBox.confirm(`确认作废销售出库「${row.code}」？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelSaleOutbound(row.id as number); ElMessage.success('已作废'); loadData()
  } catch { }
}
/** 详情（2026-09-24 由只读弹窗升级为独立页）：草稿态在详情页就地改+存，反审核也在那里 */
function goDetail(row: SaleOutbound) { router.push(`/sale/outbound/detail/${row.id}`) }
function handleSizeChange(val: number) { pagination.pageSize = val; pagination.pageNum = 1; loadData() }
function handleCurrentChange(val: number) { pagination.pageNum = val; loadData() }
function statusType(s?: string) { return DocStatusTag[s || ''] || '' }
function customerName(id?: number) { const c = customers.value.find(x => x.id === id); return c ? c.name : '' }
function warehouseName(id?: number) { const w = warehouses.value.find(x => x.id === id); return w ? w.warehouseName : '' }
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }

onMounted(() => { loadCustomers(); loadWarehouses(); loadMaterials(); loadQualityTypes(); loadData() })

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="单号">
          <el-input v-model="query.code" placeholder="请输入单号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="客户">
          <RemoteSelect v-model="query.customerId" :fetch="fetchCustomers" placeholder="请选择" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="请选择" clearable style="width:120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="tableLoading" :data="tableData" border stripe @row-click="goDetail">
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
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <!-- 2026-09-24（用户口径）：详情升级为独立页（草稿态可就地改+存）、编辑与反审核都收进详情 ⇒ 操作列 280→132。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">详情</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="danger" link @click.stop="handleCancel(row)">作废</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="var(--app-dialog-lg)" :close-on-click-modal="false" @open="loadMaterials()">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <RemoteSelect v-model="form.customerId" :fetch="fetchCustomers" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.customerId = undefined; router.push('/inventory/customer/add'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出库仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.warehouseId = undefined; router.push('/inventory/warehouse'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
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
          <el-table-column label="物料" min-width="180">
            <template #default="{ row }">
              <RemoteSelect v-model="row.materialId" :fetch="fetchMaterials" label-key="materialName" placeholder="选择物料"
                style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.materialId = undefined; router.push('/product/add'); return } onMaterialChange(v, row) }">
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
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ itemAmount(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center" fixed="right">
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
/* 根容器/卡片内边距已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body） */
.query-form { display: flex; flex-wrap: wrap; }
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
</style>

