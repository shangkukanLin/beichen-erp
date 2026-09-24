<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { PURCHASE_ORDER_DIRTY_KEY, WarehouseCategory, WarehouseType } from '@/api/enums'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
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
  orderDate: localDate(),
  taxIncluded: 0,
  taxRate: 0,
  remark: ''
})
const items = ref<PurchaseOrderItem[]>([])

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供货商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }]
}

const fetchMaterials = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
// 2026-09-20（F7-149）：采购入库仓统一为**自有成品仓**（与 purchase/exchange/add.vue:185 同口径）——
// 原先不过滤 ⇒ 可把成品采进委外仓/辅料仓（后端原先也无校验）。
const fetchWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WarehouseType.FINISHED } })
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
  Object.assign(form, { id: undefined, supplierId: undefined, warehouseId: undefined, orderDate: localDate(), taxIncluded: 0, taxRate: 0, remark: '' })
  items.value = []
}

function handleAdd() {
  router.push('/inventory/purchase/add')
}

/** 2026-09-23 用户要求：编辑由 900px 弹框改为独立页（新增本就指向独立页 /inventory/purchase/add） */
async function handleEdit(row: PurchaseOrder) {
  router.push(`/inventory/purchase/edit/${row.id}`)
}

function addItem() {
  items.value.push({ productId: undefined, qualityType: 'A', materialName: '', unit: '', quantity: 0, unitPrice: 0, amount: 0, remark: '' })
}
function removeItem(index: number) {
  items.value.splice(index, 1)
}
function onMaterialChange(val: number, row: PurchaseOrderItem) {
  const m = materialOptions.value.find(x => x.id === val)
  if (m) {
    row.productId = m.id as number
    row.materialName = m.materialName
    row.unit = m.unit
  }
}
function itemAmount(row: PurchaseOrderItem) {
  const q = Number(row.quantity) || 0
  const p = Number(row.unitPrice) || 0
  return (q * p).toFixed(2)
}

// 税额拆分（单价含税口径）：应付总额不变，按税率从总额中拆出税额
const goodsTotal = computed(() => items.value.reduce((s, r) => s + (Number(r.quantity) || 0) * (Number(r.unitPrice) || 0), 0))
const taxAmount = computed(() => form.taxIncluded === 1 && Number(form.taxRate) > 0
  ? Math.round(goodsTotal.value * (Number(form.taxRate) / (100 + Number(form.taxRate))) * 100) / 100
  : 0)
const noTaxAmount = computed(() => Math.round((goodsTotal.value - taxAmount.value) * 100) / 100)
function onTaxSwitch(v: any) { form.taxIncluded = v ? 1 : 0; form.taxRate = v ? (form.taxRate || 13) : 0 }

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
        ElMessage.success('已更新')
      } else {
        await createPurchaseOrder(payload)
        ElMessage.success('已新增')
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
    ElMessage.success('已审核')
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
    ElMessage.success('已反审核，已回到草稿状态')
    loadData()
  } catch { /* 取消 */ }
}
function handleDetail(row: PurchaseOrder) {
  router.push(`/inventory/purchase/detail/${row.id}`)
}
/**
 * 售后退货 / 换货**快捷入口**（2026-09-21 用户口径：销售单列表要有退货换货快捷键 ⇒ 采购单列表同样要有）。
 * <p>带 fromOrder=采购单ID 跳到采购退货单 / 采购换货单的新增页；目标页会反查采购单带出供货商、单号与
 * 默认仓，并自动载入可退 / 可换明细（与采购单详情页的「发起退货 / 换货」完全一致）。</p>
 * <p>⚠️ 两点必须保持：①只有**已审核**采购单能发起 —— 未审核还没入库，没有可退/可换的量（后端可退量
 * 就是按已入库数量算的，换货还额外受可换量约束）②按钮必须 .stop —— 本列表有 @row-click=handleDetail，
 * 不阻止冒泡会被行点击抢去详情页（采购换货列表与销售单列表都踩过这个坑）。</p>
 */
function handleReturn(row: PurchaseOrder) { router.push(`/inventory/purchase-return/add?fromOrder=${row.id}`) }
function handleExchange(row: PurchaseOrder) { router.push(`/inventory/purchase-exchange/add?fromOrder=${row.id}`) }

function handleSupplierClick(id?: number) {
  if (id) router.push(`/supplier/detail/${id}`)
}
function handleWarehouseClick(id?: number) {
  if (id) router.push(`/inventory/warehouse/detail/${id}`)
}

function handleSizeChange(val: number) { pagination.pageSize = val; pagination.pageNum = 1; loadData() }
function handleCurrentChange(val: number) { pagination.pageNum = val; loadData() }

function statusType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined {
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

onActivated(() => {
  // 新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(PURCHASE_ORDER_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(PURCHASE_ORDER_DIRTY_KEY)
    loadData()
  }
})
onMounted(() => { loadSupplierOptions(); loadWarehouseOptions(); loadMaterials(); loadQualityTypes(); loadData() })

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="单号">
          <el-input v-model="query.code" placeholder="请输入单号" clearable @keyup.enter="handleQuery" style="width:160px" />
        </el-form-item>
        <el-form-item label="供货商">
          <RemoteSelect v-model="query.supplierId" :fetch="fetchSuppliers" placeholder="请选择" clearable style="width:160px" />
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
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1240px > 内容区 956px
           ⇒ 横向滚动 284px。收窄为合计 930px：单号/供货商/仓库/采购明细 改小 min-width（宽屏仍自动吃余量），
           日期/金额/状态 收窄，操作列 300→174（4 个 link 按钮实际只需 ~164px）。 -->
      <el-table v-loading="tableLoading" :data="tableData" border stripe @row-click="handleDetail">
        <el-table-column prop="code" label="单号" min-width="120" show-overflow-tooltip />
        <el-table-column label="供货商" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleSupplierClick(row.supplierId)">{{ supplierName(row.supplierId) }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="入库仓库" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleWarehouseClick(row.warehouseId)">{{ warehouseName(row.warehouseId) }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="orderDate" label="订单日期" width="100" align="center" />
        <el-table-column prop="itemsSummary" label="采购明细" min-width="130" show-overflow-tooltip />
        <el-table-column prop="totalAmount" label="总金额" width="108" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="78" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button>
            <!-- 售后快捷入口（2026-09-21 用户口径）：仅已审核采购单可发起，带 fromOrder 跳转 ⇒ 新增页自动带出供货商与可退/可换明细 -->
            <el-button v-if="row.status === PurchaseStatus.AUDITED" type="warning" link @click.stop="handleReturn(row)">退货</el-button>
            <el-button v-if="row.status === PurchaseStatus.AUDITED" type="warning" link @click.stop="handleExchange(row)">换货</el-button>
            <el-button v-if="row.status === PurchaseStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === PurchaseStatus.AUDITED" type="warning" link @click.stop="handleUnAudit(row)">反审核</el-button>
            <el-button v-if="row.status === PurchaseStatus.DRAFT" type="warning" link @click.stop="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === PurchaseStatus.DRAFT" type="danger" link @click.stop="handleCancel(row)">作废</el-button>
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

  </div>
</template>

<style scoped>
/* 根容器/卡片内边距已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body） */
.query-form { align-items: center; }
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
.sum-bar { margin-top: 12px; display: flex; justify-content: flex-end; gap: 24px; font-size: var(--app-font-base); color: var(--app-text-secondary); }
.sum-bar b { color: var(--app-text-primary); font-size: var(--app-font-num-sm); }
.tax-num { color: var(--app-color-danger); }
</style>
