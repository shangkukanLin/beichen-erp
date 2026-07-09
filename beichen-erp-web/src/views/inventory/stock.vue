<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import request from '@/utils/request'
import { getMaterialPage, type Material } from '@/api/material'
import {
  getWarehouseOptions,
  getStockPage, getStockLog,
  getStockTakePage, getStockTake, getStockTakeItems, createStockTake, updateStockTake, auditStockTake, cancelStockTake,
  getTransferPage, getTransfer, getTransferItems, createTransfer, updateTransfer, auditTransfer, cancelTransfer,
  getOtherPage, getOther, getOtherItems, createOther, updateOther, auditOther, cancelOther,
  type StockRow, type StockLogRow, type StockTake, type StockTakeItem, type Transfer, type TransferItem, type OtherIo, type OtherIoItem
} from '@/api/inventory'

const activeTab = ref('stock')

const warehouses = ref<{ id: number; warehouseName: string }[]>([])
const materialOptions = ref<Material[]>([])

const statusOptions = [
  { label: '草稿', value: '草稿' },
  { label: '已审核', value: '已审核' },
  { label: '已作废', value: '已作废' }
]
const changeTypeOptions = [
  '采购入库', '销售出库', '其他入库', '其他出库', '调拨入', '调拨出', '盘点溢', '盘点损'
].map(t => ({ label: t, value: t }))
const ioTypeOptions = [
  { label: '其他入库', value: '其他入库' },
  { label: '其他出库', value: '其他出库' }
]

async function loadWarehouses() {
  try { const res = await getWarehouseOptions(); warehouses.value = res?.records || [] } catch { warehouses.value = [] }
}
async function loadMaterials(keyword?: string) {
  try { const res = await getMaterialPage({ pageNum: 1, pageSize: 100, name: keyword || '' }); materialOptions.value = res?.records || [] } catch { materialOptions.value = [] }
}
function warehouseName(id?: number) {
  const w = warehouses.value.find(x => x.id === id); return w ? w.warehouseName : ''
}
function statusType(s?: string) {
  if (s === '草稿') return 'info'
  if (s === '已审核') return 'success'
  if (s === '已作废') return 'danger'
  return ''
}
function fmt(v?: number) { return v === undefined || v === null ? '0.0000' : Number(v).toFixed(4) }

// ============ 库存查询 ============
const stockQuery = reactive({ warehouseId: undefined as number | undefined, productName: '' })
const stockPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const stockLoading = ref(false)
const stockData = ref<StockRow[]>([])
async function loadStock() {
  stockLoading.value = true
  try {
    const params: any = { pageNum: stockPage.pageNum, pageSize: stockPage.pageSize }
    if (stockQuery.warehouseId) params.warehouseId = stockQuery.warehouseId
    if (stockQuery.productName) params.productName = stockQuery.productName
    const res = await getStockPage(params)
    stockData.value = res?.records || []
    stockPage.total = res?.total || 0
  } catch { stockData.value = [] } finally { stockLoading.value = false }
}
function stockQuery_() { stockPage.pageNum = 1; loadStock() }
function stockReset() { stockQuery.warehouseId = undefined; stockQuery.productName = ''; stockPage.pageNum = 1; loadStock() }

// ============ 库存流水 ============
const logQuery = reactive({ warehouseId: undefined as number | undefined, materialName: '', changeType: '', dateRange: [] as string[] })
const logPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const logLoading = ref(false)
const logData = ref<StockLogRow[]>([])
async function loadLog() {
  logLoading.value = true
  try {
    const params: any = { pageNum: logPage.pageNum, pageSize: logPage.pageSize }
    if (logQuery.warehouseId) params.warehouseId = logQuery.warehouseId
    if (logQuery.materialName) params.materialName = logQuery.materialName
    if (logQuery.changeType) params.changeType = logQuery.changeType
    if (logQuery.dateRange && logQuery.dateRange.length === 2) { params.startDate = logQuery.dateRange[0]; params.endDate = logQuery.dateRange[1] }
    const res = await getStockLog(params)
    logData.value = res?.records || []
    logPage.total = res?.total || 0
  } catch { logData.value = [] } finally { logLoading.value = false }
}
function logQuery_() { logPage.pageNum = 1; loadLog() }
function logReset() { logQuery.warehouseId = undefined; logQuery.materialName = ''; logQuery.changeType = ''; logQuery.dateRange = []; logPage.pageNum = 1; loadLog() }

// ============ 盘点 ============
const takeQuery = reactive({ code: '', warehouseId: undefined as number | undefined, status: '' })
const takePage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const takeLoading = ref(false)
const takeData = ref<StockTake[]>([])
async function loadTake() {
  takeLoading.value = true
  try {
    const params: any = { pageNum: takePage.pageNum, pageSize: takePage.pageSize }
    if (takeQuery.code) params.code = takeQuery.code
    if (takeQuery.warehouseId) params.warehouseId = takeQuery.warehouseId
    if (takeQuery.status) params.status = takeQuery.status
    const res = await getStockTakePage(params)
    takeData.value = res?.records || []
    takePage.total = res?.total || 0
  } catch { takeData.value = [] } finally { takeLoading.value = false }
}
function takeQuery_() { takePage.pageNum = 1; loadTake() }
function takeReset() { takeQuery.code = ''; takeQuery.warehouseId = undefined; takeQuery.status = ''; takePage.pageNum = 1; loadTake() }

const takeDialog = ref(false)
const takeTitle = ref('新增盘点单')
const takeFormRef = ref<FormInstance>()
const takeForm = reactive<StockTake>({ warehouseId: undefined, takeDate: '', remark: '' })
const takeItems = ref<StockTakeItem[]>([])
const takeSubmitLoading = ref(false)
const takeRules = { warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }] }
function resetTakeForm() { Object.assign(takeForm, { id: undefined, warehouseId: undefined, takeDate: '', remark: '' }); takeItems.value = [] }
function handleTakeAdd() { resetTakeForm(); takeTitle.value = '新增盘点单'; takeDialog.value = true; takeFormRef.value?.clearValidate() }
async function handleTakeEdit(row: StockTake) {
  resetTakeForm(); Object.assign(takeForm, row); takeTitle.value = '编辑盘点单'; takeDialog.value = true; takeFormRef.value?.clearValidate()
  try { takeItems.value = await getStockTakeItems(row.id as number) } catch { takeItems.value = [] }
}
function addTakeItem() { takeItems.value.push({ materialId: undefined, materialName: '', spec: '', unit: '', actualQuantity: 0, remark: '' }) }
function removeTakeItem(i: number) { takeItems.value.splice(i, 1) }
function onMaterialTake(v: number, row: StockTakeItem) {
  const m = materialOptions.value.find(x => x.id === v)
  if (m) { row.materialId = m.id as number; row.materialName = m.name; row.spec = m.spec; row.unit = m.unit }
}
async function submitTake() {
  if (!takeFormRef.value) return
  await takeFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (takeItems.value.length === 0) { ElMessage.warning('请至少添加一条盘点明细'); return }
    takeSubmitLoading.value = true
    try {
      const payload = { take: { ...takeForm }, items: takeItems.value }
      if (takeForm.id) await updateStockTake(takeForm.id as number, payload)
      else await createStockTake(payload)
      ElMessage.success('保存成功'); takeDialog.value = false; loadTake()
    } catch { } finally { takeSubmitLoading.value = false }
  })
}
async function auditTake(row: StockTake) {
  try { await ElMessageBox.confirm(`确认审核盘点单「${row.code}」？将自动调整库存`, '提示', { type: 'warning' })
    await auditStockTake(row.id as number); ElMessage.success('审核成功'); loadTake() } catch { }
}
async function cancelTake(row: StockTake) {
  try { await ElMessageBox.confirm(`确认作废盘点单「${row.code}」？`, '提示', { type: 'warning' })
    await cancelStockTake(row.id as number); ElMessage.success('已作废'); loadTake() } catch { }
}
const takeDetailVisible = ref(false)
const takeDetail = ref<StockTake>({})
const takeDetailItems = ref<StockTakeItem[]>([])
async function takeDetail_(row: StockTake) {
  takeDetail.value = { ...row }
  try { takeDetailItems.value = await getStockTakeItems(row.id as number) } catch { takeDetailItems.value = [] }
  takeDetailVisible.value = true
}

// ============ 调拨 ============
const transferQuery = reactive({ status: '', fromWarehouseId: undefined as number | undefined, toWarehouseId: undefined as number | undefined })
const transferPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const transferLoading = ref(false)
const transferData = ref<Transfer[]>([])
async function loadTransfer() {
  transferLoading.value = true
  try {
    const params: any = { pageNum: transferPage.pageNum, pageSize: transferPage.pageSize }
    if (transferQuery.status) params.status = transferQuery.status
    if (transferQuery.fromWarehouseId) params.fromWarehouseId = transferQuery.fromWarehouseId
    if (transferQuery.toWarehouseId) params.toWarehouseId = transferQuery.toWarehouseId
    const res = await getTransferPage(params)
    transferData.value = res?.records || []
    transferPage.total = res?.total || 0
  } catch { transferData.value = [] } finally { transferLoading.value = false }
}
function transferQuery_() { transferPage.pageNum = 1; loadTransfer() }
function transferReset() { transferQuery.status = ''; transferQuery.fromWarehouseId = undefined; transferQuery.toWarehouseId = undefined; transferPage.pageNum = 1; loadTransfer() }

const transferDialog = ref(false)
const transferTitle = ref('新增调拨单')
const transferFormRef = ref<FormInstance>()
const transferForm = reactive<Transfer>({ fromWarehouseId: undefined, toWarehouseId: undefined, transferDate: '', remark: '' })
const transferItems = ref<TransferItem[]>([])
const transferSubmitLoading = ref(false)
const transferRules = {
  fromWarehouseId: [{ required: true, message: '请选择调出仓库', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择调入仓库', trigger: 'change' }]
}
function resetTransferForm() { Object.assign(transferForm, { id: undefined, fromWarehouseId: undefined, toWarehouseId: undefined, transferDate: '', remark: '' }); transferItems.value = [] }
function handleTransferAdd() { resetTransferForm(); transferTitle.value = '新增调拨单'; transferDialog.value = true; transferFormRef.value?.clearValidate() }
async function handleTransferEdit(row: Transfer) {
  resetTransferForm(); Object.assign(transferForm, row); transferTitle.value = '编辑调拨单'; transferDialog.value = true; transferFormRef.value?.clearValidate()
  try { transferItems.value = await getTransferItems(row.id as number) } catch { transferItems.value = [] }
}
function addTransferItem() { transferItems.value.push({ materialId: undefined, materialName: '', spec: '', unit: '', quantity: 0, remark: '' }) }
function removeTransferItem(i: number) { transferItems.value.splice(i, 1) }
function onMaterialTransfer(v: number, row: TransferItem) {
  const m = materialOptions.value.find(x => x.id === v)
  if (m) { row.materialId = m.id as number; row.materialName = m.name; row.spec = m.spec; row.unit = m.unit }
}
async function submitTransfer() {
  if (!transferFormRef.value) return
  await transferFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (transferForm.fromWarehouseId === transferForm.toWarehouseId) { ElMessage.warning('调出与调入仓库不能相同'); return }
    if (transferItems.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    transferSubmitLoading.value = true
    try {
      const payload = { transfer: { ...transferForm }, items: transferItems.value }
      if (transferForm.id) await updateTransfer(transferForm.id as number, payload)
      else await createTransfer(payload)
      ElMessage.success('保存成功'); transferDialog.value = false; loadTransfer()
    } catch { } finally { transferSubmitLoading.value = false }
  })
}
async function auditTransfer_(row: Transfer) {
  try { await ElMessageBox.confirm(`确认审核调拨单「${row.code}」？将自动调出/调入库存`, '提示', { type: 'warning' })
    await auditTransfer(row.id as number); ElMessage.success('审核成功'); loadTransfer() } catch { }
}
async function cancelTransfer_(row: Transfer) {
  try { await ElMessageBox.confirm(`确认作废调拨单「${row.code}」？`, '提示', { type: 'warning' })
    await cancelTransfer(row.id as number); ElMessage.success('已作废'); loadTransfer() } catch { }
}
const transferDetailVisible = ref(false)
const transferDetail = ref<Transfer>({})
const transferDetailItems = ref<TransferItem[]>([])
async function transferDetail_(row: Transfer) {
  transferDetail.value = { ...row }
  try { transferDetailItems.value = await getTransferItems(row.id as number) } catch { transferDetailItems.value = [] }
  transferDetailVisible.value = true
}

// ============ 其他出入库 ============
const otherQuery = reactive({ status: '', warehouseId: undefined as number | undefined, ioType: '' })
const otherPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const otherLoading = ref(false)
const otherData = ref<OtherIo[]>([])
async function loadOther() {
  otherLoading.value = true
  try {
    const params: any = { pageNum: otherPage.pageNum, pageSize: otherPage.pageSize }
    if (otherQuery.status) params.status = otherQuery.status
    if (otherQuery.warehouseId) params.warehouseId = otherQuery.warehouseId
    if (otherQuery.ioType) params.ioType = otherQuery.ioType
    const res = await getOtherPage(params)
    otherData.value = res?.records || []
    otherPage.total = res?.total || 0
  } catch { otherData.value = [] } finally { otherLoading.value = false }
}
function otherQuery_() { otherPage.pageNum = 1; loadOther() }
function otherReset() { otherQuery.status = ''; otherQuery.warehouseId = undefined; otherQuery.ioType = ''; otherPage.pageNum = 1; loadOther() }

const otherDialog = ref(false)
const otherTitle = ref('新增其他出入库')
const otherFormRef = ref<FormInstance>()
const otherForm = reactive<OtherIo>({ warehouseId: undefined, ioType: '其他入库', ioDate: '', remark: '' })
const otherItems = ref<OtherIoItem[]>([])
const otherSubmitLoading = ref(false)
const otherRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  ioType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}
function resetOtherForm() { Object.assign(otherForm, { id: undefined, warehouseId: undefined, ioType: '其他入库', ioDate: '', remark: '' }); otherItems.value = [] }
function handleOtherAdd() { resetOtherForm(); otherTitle.value = '新增其他出入库'; otherDialog.value = true; otherFormRef.value?.clearValidate() }
async function handleOtherEdit(row: OtherIo) {
  resetOtherForm(); Object.assign(otherForm, row); otherTitle.value = '编辑其他出入库'; otherDialog.value = true; otherFormRef.value?.clearValidate()
  try { otherItems.value = await getOtherItems(row.id as number) } catch { otherItems.value = [] }
}
function addOtherItem() { otherItems.value.push({ materialId: undefined, materialName: '', spec: '', unit: '', quantity: 0, remark: '' }) }
function removeOtherItem(i: number) { otherItems.value.splice(i, 1) }
function onMaterialOther(v: number, row: OtherIoItem) {
  const m = materialOptions.value.find(x => x.id === v)
  if (m) { row.materialId = m.id as number; row.materialName = m.name; row.spec = m.spec; row.unit = m.unit }
}
async function submitOther() {
  if (!otherFormRef.value) return
  await otherFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (otherItems.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    otherSubmitLoading.value = true
    try {
      const payload = { otherIo: { ...otherForm }, items: otherItems.value }
      if (otherForm.id) await updateOther(otherForm.id as number, payload)
      else await createOther(payload)
      ElMessage.success('保存成功'); otherDialog.value = false; loadOther()
    } catch { } finally { otherSubmitLoading.value = false }
  })
}
async function auditOther_(row: OtherIo) {
  try { await ElMessageBox.confirm(`确认审核其他出入库单「${row.code}」？将自动变更库存`, '提示', { type: 'warning' })
    await auditOther(row.id as number); ElMessage.success('审核成功'); loadOther() } catch { }
}
async function cancelOther_(row: OtherIo) {
  try { await ElMessageBox.confirm(`确认作废其他出入库单「${row.code}」？`, '提示', { type: 'warning' })
    await cancelOther(row.id as number); ElMessage.success('已作废'); loadOther() } catch { }
}
const otherDetailVisible = ref(false)
const otherDetail = ref<OtherIo>({})
const otherDetailItems = ref<OtherIoItem[]>([])
async function otherDetail_(row: OtherIo) {
  otherDetail.value = { ...row }
  try { otherDetailItems.value = await getOtherItems(row.id as number) } catch { otherDetailItems.value = [] }
  otherDetailVisible.value = true
}

onMounted(() => { loadWarehouses(); loadMaterials(); loadStock() })
</script>

<template>
  <div class="page">
    <el-card shadow="never" class="main-card">
      <el-tabs v-model="activeTab">
        <!-- 库存查询 -->
        <el-tab-pane label="库存查询" name="stock">
          <el-form :inline="true" :model="stockQuery" class="query-form">
            <el-form-item label="仓库">
              <el-select v-model="stockQuery.warehouseId" placeholder="全部" clearable filterable style="width:160px">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="产品">
              <el-input v-model="stockQuery.productName" placeholder="产品名称" clearable @keyup.enter="stockQuery_" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="'Search'" @click="stockQuery_">查询</el-button>
              <el-button :icon="'Refresh'" @click="stockReset">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="stockLoading" :data="stockData" border stripe>
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="仓库" min-width="140"><template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template></el-table-column>
            <el-table-column prop="productName" label="产品名称" min-width="180" />
            <el-table-column prop="quantity" label="库存数量" min-width="140" align="right">
              <template #default="{ row }">{{ fmt(row.quantity) }}</template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination v-model:current-page="stockPage.pageNum" v-model:page-size="stockPage.pageSize"
              :page-sizes="[10, 20, 50, 100]" :total="stockPage.total" layout="total, sizes, prev, pager, next, jumper"
              background @size-change="(v:number)=>{stockPage.pageSize=v;loadStock()}" @current-change="loadStock" />
          </div>
        </el-tab-pane>

        <!-- 库存流水 -->
        <el-tab-pane label="库存流水" name="log">
          <el-form :inline="true" :model="logQuery" class="query-form">
            <el-form-item label="仓库">
              <el-select v-model="logQuery.warehouseId" placeholder="全部" clearable filterable style="width:150px">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="产品">
              <el-input v-model="logQuery.materialName" placeholder="产品名称" clearable style="width:150px" @keyup.enter="logQuery_" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="logQuery.changeType" placeholder="全部" clearable style="width:130px">
                <el-option v-for="o in changeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期">
              <el-date-picker v-model="logQuery.dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始" end-placeholder="结束" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="'Search'" @click="logQuery_">查询</el-button>
              <el-button :icon="'Refresh'" @click="logReset">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="logLoading" :data="logData" border stripe>
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column prop="changeType" label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.changeQuantity > 0 ? 'success' : 'danger'">{{ row.changeType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="仓库" min-width="120"><template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template></el-table-column>
            <el-table-column prop="materialName" label="产品" min-width="150" />
            <el-table-column prop="spec" label="规格" width="100" />
            <el-table-column prop="changeQuantity" label="变动数量" width="120" align="right">
              <template #default="{ row }">
                <span :style="{ color: row.changeQuantity > 0 ? '#67C23A' : '#F56C6C' }">{{ row.changeQuantity > 0 ? '+' : '' }}{{ fmt(row.changeQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="beforeQuantity" label="变动前" width="120" align="right"><template #default="{ row }">{{ fmt(row.beforeQuantity) }}</template></el-table-column>
            <el-table-column prop="afterQuantity" label="变动后" width="120" align="right"><template #default="{ row }">{{ fmt(row.afterQuantity) }}</template></el-table-column>
            <el-table-column prop="relatedBillNo" label="关联单号" min-width="150" />
          </el-table>
          <div class="pagination">
            <el-pagination v-model:current-page="logPage.pageNum" v-model:page-size="logPage.pageSize"
              :page-sizes="[10, 20, 50, 100]" :total="logPage.total" layout="total, sizes, prev, pager, next, jumper"
              background @size-change="(v:number)=>{logPage.pageSize=v;loadLog()}" @current-change="loadLog" />
          </div>
        </el-tab-pane>

        <!-- 盘点 -->
        <el-tab-pane label="库存盘点" name="take">
          <el-form :inline="true" :model="takeQuery" class="query-form">
            <el-form-item label="单号"><el-input v-model="takeQuery.code" placeholder="单号" clearable @keyup.enter="takeQuery_" /></el-form-item>
            <el-form-item label="仓库">
              <el-select v-model="takeQuery.warehouseId" placeholder="全部" clearable filterable style="width:150px">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="takeQuery.status" placeholder="全部" clearable style="width:120px">
                <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="'Search'" @click="takeQuery_">查询</el-button>
              <el-button :icon="'Refresh'" @click="takeReset">重置</el-button>
              <el-button type="success" :icon="'Plus'" @click="handleTakeAdd">新增盘点</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="takeLoading" :data="takeData" border stripe>
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="code" label="单号" min-width="150" />
            <el-table-column label="仓库" min-width="140"><template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template></el-table-column>
            <el-table-column prop="takeDate" label="盘点日期" width="120" align="center" />
            <el-table-column label="状态" width="90" align="center"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="230" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="takeDetail_(row)">详情</el-button>
                <el-button v-if="row.status === '草稿'" type="success" link @click="auditTake(row)">审核</el-button>
                <el-button v-if="row.status === '草稿'" type="warning" link @click="handleTakeEdit(row)">编辑</el-button>
                <el-button v-if="row.status === '草稿'" type="danger" link @click="cancelTake(row)">作废</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination v-model:current-page="takePage.pageNum" v-model:page-size="takePage.pageSize"
              :page-sizes="[10, 20, 50, 100]" :total="takePage.total" layout="total, sizes, prev, pager, next, jumper"
              background @size-change="(v:number)=>{takePage.pageSize=v;loadTake()}" @current-change="loadTake" />
          </div>
        </el-tab-pane>

        <!-- 调拨 -->
        <el-tab-pane label="库存调拨" name="transfer">
          <el-form :inline="true" :model="transferQuery" class="query-form">
            <el-form-item label="状态">
              <el-select v-model="transferQuery.status" placeholder="全部" clearable style="width:120px">
                <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="调出仓">
              <el-select v-model="transferQuery.fromWarehouseId" placeholder="全部" clearable filterable style="width:150px">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="调入仓">
              <el-select v-model="transferQuery.toWarehouseId" placeholder="全部" clearable filterable style="width:150px">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="'Search'" @click="transferQuery_">查询</el-button>
              <el-button :icon="'Refresh'" @click="transferReset">重置</el-button>
              <el-button type="success" :icon="'Plus'" @click="handleTransferAdd">新增调拨</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="transferLoading" :data="transferData" border stripe>
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="code" label="单号" min-width="150" />
            <el-table-column label="调出仓库" min-width="140"><template #default="{ row }">{{ warehouseName(row.fromWarehouseId) }}</template></el-table-column>
            <el-table-column label="调入仓库" min-width="140"><template #default="{ row }">{{ warehouseName(row.toWarehouseId) }}</template></el-table-column>
            <el-table-column prop="transferDate" label="调拨日期" width="120" align="center" />
            <el-table-column label="状态" width="90" align="center"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="230" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="transferDetail_(row)">详情</el-button>
                <el-button v-if="row.status === '草稿'" type="success" link @click="auditTransfer_(row)">审核</el-button>
                <el-button v-if="row.status === '草稿'" type="warning" link @click="handleTransferEdit(row)">编辑</el-button>
                <el-button v-if="row.status === '草稿'" type="danger" link @click="cancelTransfer_(row)">作废</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination v-model:current-page="transferPage.pageNum" v-model:page-size="transferPage.pageSize"
              :page-sizes="[10, 20, 50, 100]" :total="transferPage.total" layout="total, sizes, prev, pager, next, jumper"
              background @size-change="(v:number)=>{transferPage.pageSize=v;loadTransfer()}" @current-change="loadTransfer" />
          </div>
        </el-tab-pane>

        <!-- 其他出入库 -->
        <el-tab-pane label="其他出入库" name="other">
          <el-form :inline="true" :model="otherQuery" class="query-form">
            <el-form-item label="状态">
              <el-select v-model="otherQuery.status" placeholder="全部" clearable style="width:120px">
                <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="仓库">
              <el-select v-model="otherQuery.warehouseId" placeholder="全部" clearable filterable style="width:150px">
                <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="otherQuery.ioType" placeholder="全部" clearable style="width:130px">
                <el-option v-for="o in ioTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="'Search'" @click="otherQuery_">查询</el-button>
              <el-button :icon="'Refresh'" @click="otherReset">重置</el-button>
              <el-button type="success" :icon="'Plus'" @click="handleOtherAdd">新增</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="otherLoading" :data="otherData" border stripe>
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="code" label="单号" min-width="150" />
            <el-table-column label="仓库" min-width="140"><template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template></el-table-column>
            <el-table-column label="类型" width="100" align="center"><template #default="{ row }"><el-tag :type="row.ioType === '其他入库' ? 'success' : 'warning'">{{ row.ioType }}</el-tag></template></el-table-column>
            <el-table-column prop="ioDate" label="日期" width="120" align="center" />
            <el-table-column label="状态" width="90" align="center"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="230" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="otherDetail_(row)">详情</el-button>
                <el-button v-if="row.status === '草稿'" type="success" link @click="auditOther_(row)">审核</el-button>
                <el-button v-if="row.status === '草稿'" type="warning" link @click="handleOtherEdit(row)">编辑</el-button>
                <el-button v-if="row.status === '草稿'" type="danger" link @click="cancelOther_(row)">作废</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination v-model:current-page="otherPage.pageNum" v-model:page-size="otherPage.pageSize"
              :page-sizes="[10, 20, 50, 100]" :total="otherPage.total" layout="total, sizes, prev, pager, next, jumper"
              background @size-change="(v:number)=>{otherPage.pageSize=v;loadOther()}" @current-change="loadOther" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 盘点 弹窗 -->
    <el-dialog v-model="takeDialog" :title="takeTitle" width="900px" :close-on-click-modal="false" @open="loadMaterials()">
      <el-form ref="takeFormRef" :model="takeForm" :rules="takeRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="仓库" prop="warehouseId">
            <el-select v-model="takeForm.warehouseId" placeholder="请选择" filterable style="width:100%">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
            </el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="盘点日期">
            <el-date-picker v-model="takeForm.takeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注">
            <el-input v-model="takeForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">盘点明细（账面数量为保存时系统自动取数）</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" :icon="'Plus'" @click="addTakeItem">添加明细</el-button></div>
        <el-table :data="takeItems" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="物料" min-width="200">
            <template #default="{ row }">
              <el-select v-model="row.materialId" placeholder="选择物料" filterable remote :remote-method="loadMaterials" style="width:100%" @change="(v:number)=>onMaterialTake(v, row)">
                <el-option v-for="m in materialOptions" :key="m.id" :label="`${m.name}(${m.code})`" :value="m.id" />
              </el-select></template></el-table-column>
          <el-table-column prop="spec" label="规格" width="100" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="实盘数量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.actualQuantity" :min="0" :precision="4" controls-position="right" style="width:100%" /></template></el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="removeTakeItem($index)">删除</el-button></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer><el-button @click="takeDialog = false">取消</el-button><el-button type="primary" :loading="takeSubmitLoading" @click="submitTake">确定</el-button></template>
    </el-dialog>
    <el-drawer v-model="takeDetailVisible" title="盘点单详情" size="55%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ takeDetail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(takeDetail.status)">{{ takeDetail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="仓库">{{ warehouseName(takeDetail.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="盘点日期">{{ takeDetail.takeDate }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ takeDetail.remark }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">盘点明细</el-divider>
      <el-table :data="takeDetailItems" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="spec" label="规格" width="100" />
        <el-table-column prop="bookQuantity" label="账面" width="110" align="right"><template #default="{ row }">{{ fmt(row.bookQuantity) }}</template></el-table-column>
        <el-table-column prop="actualQuantity" label="实盘" width="110" align="right"><template #default="{ row }">{{ fmt(row.actualQuantity) }}</template></el-table-column>
        <el-table-column prop="profitLossQuantity" label="盈亏" width="110" align="right"><template #default="{ row }"><span :style="{color: (row.profitLossQuantity||0) >= 0 ? '#67C23A' : '#F56C6C'}">{{ fmt(row.profitLossQuantity) }}</span></template></el-table-column>
      </el-table>
    </el-drawer>

    <!-- 调拨 弹窗 -->
    <el-dialog v-model="transferDialog" :title="transferTitle" width="900px" :close-on-click-modal="false" @open="loadMaterials()">
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="调出仓库" prop="fromWarehouseId">
            <el-select v-model="transferForm.fromWarehouseId" placeholder="请选择" filterable style="width:100%">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="调入仓库" prop="toWarehouseId">
            <el-select v-model="transferForm.toWarehouseId" placeholder="请选择" filterable style="width:100%">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="调拨日期">
            <el-date-picker v-model="transferForm.transferDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="transferForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">调拨明细</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" :icon="'Plus'" @click="addTransferItem">添加明细</el-button></div>
        <el-table :data="transferItems" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="物料" min-width="200">
            <template #default="{ row }">
              <el-select v-model="row.materialId" placeholder="选择物料" filterable remote :remote-method="loadMaterials" style="width:100%" @change="(v:number)=>onMaterialTransfer(v, row)">
                <el-option v-for="m in materialOptions" :key="m.id" :label="`${m.name}(${m.code})`" :value="m.id" /></el-select></template></el-table-column>
          <el-table-column prop="spec" label="规格" width="100" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="数量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="4" controls-position="right" style="width:100%" /></template></el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="removeTransferItem($index)">删除</el-button></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer><el-button @click="transferDialog = false">取消</el-button><el-button type="primary" :loading="transferSubmitLoading" @click="submitTransfer">确定</el-button></template>
    </el-dialog>
    <el-drawer v-model="transferDetailVisible" title="调拨单详情" size="55%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ transferDetail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(transferDetail.status)">{{ transferDetail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="调出仓库">{{ warehouseName(transferDetail.fromWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="调入仓库">{{ warehouseName(transferDetail.toWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="调拨日期">{{ transferDetail.transferDate }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ transferDetail.remark }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">调拨明细</el-divider>
      <el-table :data="transferDetailItems" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="spec" label="规格" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" label="数量" width="110" align="right"><template #default="{ row }">{{ fmt(row.quantity) }}</template></el-table-column>
      </el-table>
    </el-drawer>

    <!-- 其他出入库 弹窗 -->
    <el-dialog v-model="otherDialog" :title="otherTitle" width="900px" :close-on-click-modal="false" @open="loadMaterials()">
      <el-form ref="otherFormRef" :model="otherForm" :rules="otherRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="仓库" prop="warehouseId">
            <el-select v-model="otherForm.warehouseId" placeholder="请选择" filterable style="width:100%">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="类型" prop="ioType">
            <el-select v-model="otherForm.ioType" placeholder="请选择" style="width:100%">
              <el-option v-for="o in ioTypeOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="日期">
            <el-date-picker v-model="otherForm.ioDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="otherForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">明细</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" :icon="'Plus'" @click="addOtherItem">添加明细</el-button></div>
        <el-table :data="otherItems" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="物料" min-width="200">
            <template #default="{ row }">
              <el-select v-model="row.materialId" placeholder="选择物料" filterable remote :remote-method="loadMaterials" style="width:100%" @change="(v:number)=>onMaterialOther(v, row)">
                <el-option v-for="m in materialOptions" :key="m.id" :label="`${m.name}(${m.code})`" :value="m.id" /></el-select></template></el-table-column>
          <el-table-column prop="spec" label="规格" width="100" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="数量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="4" controls-position="right" style="width:100%" /></template></el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="removeOtherItem($index)">删除</el-button></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer><el-button @click="otherDialog = false">取消</el-button><el-button type="primary" :loading="otherSubmitLoading" @click="submitOther">确定</el-button></template>
    </el-dialog>
    <el-drawer v-model="otherDetailVisible" title="其他出入库详情" size="55%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ otherDetail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(otherDetail.status)">{{ otherDetail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="仓库">{{ warehouseName(otherDetail.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="类型"><el-tag :type="otherDetail.ioType === '其他入库' ? 'success' : 'warning'">{{ otherDetail.ioType }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="日期">{{ otherDetail.ioDate }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ otherDetail.remark }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">明细</el-divider>
      <el-table :data="otherDetailItems" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="spec" label="规格" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" label="数量" width="110" align="right"><template #default="{ row }">{{ fmt(row.quantity) }}</template></el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.main-card :deep(.el-card__body) { padding: 16px; }
.query-form { display: flex; flex-wrap: wrap; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
