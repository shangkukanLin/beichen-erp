<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { WarehouseType, AfterSaleSourceType, AfterSaleSourceTypeLabel } from '@/api/enums'
import {
  getReturnSortPage, getReturnSort, getReturnSortItems, getReturnSortDefectStock,
  createReturnSort, updateReturnSort, auditReturnSort, cancelReturnSort, deleteReturnSort,
  type ReturnSortItem
} from '@/api/inventory'

const query = reactive({ code: '', status: '' as string | number, warehouseId: '' as string | number })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<any[]>([])
const warehouseOptions = ref<any[]>([])

// 源仓库：只允许选售后仓
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.AFTER_SALE } })
// 目标仓库：自有仓（成品仓/不良仓等）
const fetchTargetWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY' } })
// A规/B规/C规 目标仓：必须为成品仓（与后端校验一致）
const fetchFinishedWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })

// 列表
async function loadData() {
  tableLoading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.status) params.status = query.status
    if (query.warehouseId) params.warehouseId = query.warehouseId
    const res = await getReturnSortPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = []; pagination.total = 0 } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.status = ''; query.warehouseId = ''; pagination.pageNum = 1; loadData() }

// 弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('新增退货整理')
const submitLoading = ref(false)
const form = reactive({
  id: undefined as any,
  warehouseId: undefined as any,
  sortDate: new Date().toISOString().slice(0, 10),
  targetWarehouseA: undefined as any,
  targetWarehouseB: undefined as any,
  targetWarehouseC: undefined as any,
  targetWarehouseDefect: undefined as any,
  /** 折损收款：整理后 B/C/不良品的折损，向客户收取，审核后生成一条正向应收（单号 -LOSS） */
  lossAmount: 0,
  lossRemark: '',
  remark: ''
})
const items = ref<ReturnSortItem[]>([])
/** 待整理库存超期预警阈值（天）：停留超过该天数则标红提示 */
const STAY_ALERT_DAYS = 3

function resetForm() {
  Object.assign(form, {
    id: undefined, warehouseId: undefined,
    sortDate: new Date().toISOString().slice(0, 10),
    targetWarehouseA: undefined, targetWarehouseB: undefined,
    targetWarehouseC: undefined, targetWarehouseDefect: undefined,
    lossAmount: 0, lossRemark: '', remark: ''
  })
  items.value = []
}

// 默认目标仓库：A/B/C 取第一个成品仓，不良取第一个不良仓
function applyTargetDefaults() {
  const fin = warehouseOptions.value.find((w: any) => w.warehouseType === WarehouseType.FINISHED)
  const def = warehouseOptions.value.find((w: any) => w.warehouseType === WarehouseType.DEFECT)
  form.targetWarehouseA = fin?.id
  form.targetWarehouseB = fin?.id
  form.targetWarehouseC = fin?.id
  form.targetWarehouseDefect = def?.id
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增退货整理'
  applyTargetDefaults()
  dialogVisible.value = true
}

async function handleEdit(row: any) {
  resetForm()
  dialogTitle.value = '编辑退货整理'
  try {
    const io = await getReturnSort(row.id)
    Object.assign(form, {
      id: io.id, warehouseId: io.warehouseId, sortDate: io.sortDate,
      targetWarehouseA: io.targetWarehouseA, targetWarehouseB: io.targetWarehouseB,
      targetWarehouseC: io.targetWarehouseC, targetWarehouseDefect: io.targetWarehouseDefect,
      lossAmount: Number(io.lossAmount || 0), lossRemark: io.lossRemark || '',
      remark: io.remark
    })
    const its = await getReturnSortItems(row.id)
    items.value = (its || []).map((it: any) => ({
      pendingId: it.pendingId,
      productId: it.productId, productName: it.productName, spec: it.spec, unit: it.unit,
      totalQuantity: Number(it.totalQuantity), qtyA: Number(it.qtyA), qtyB: Number(it.qtyB),
      qtyC: Number(it.qtyC), qtyDefect: Number(it.qtyDefect)
    }))
  } catch { ElMessage.error('获取详情失败') }
  dialogVisible.value = true
}

// 加载售后仓待整理库存（待分类品）
async function loadDefectStock() {
  if (!form.warehouseId) { ElMessage.warning('请先选择源仓库(售后仓)'); return }
  try {
    const rows: any[] = await getReturnSortDefectStock(form.warehouseId)
    for (const r of rows) {
      // 按待整理批次逐行展开：同一产品可来自多张退单/换货单，各自独立成行以便追溯
      const exist = r.pendingId
        ? items.value.find((it) => it.pendingId === r.pendingId)
        : items.value.find((it) => it.productId === r.productId)
      const qty = Number(r.quantity) || 0
      if (exist) { exist.totalQuantity = qty; exist.available = qty; exist.stayDays = Number(r.stayDays) || 0 }
      else {
        items.value.push({
          pendingId: r.pendingId,
          sourceType: r.sourceType, sourceCode: r.sourceCode, sourceDate: r.sourceDate,
          sku: r.sku || '',
          productId: r.productId, productName: r.productName, spec: r.spec, unit: r.unit,
          totalQuantity: qty, available: qty,
          batchQuantity: Number(r.totalQuantity) || 0, sortedQuantity: Number(r.sortedQuantity) || 0,
          stayDays: Number(r.stayDays) || 0,
          qtyA: 0, qtyB: 0, qtyC: 0, qtyDefect: 0
        })
      }
    }
    if (rows.length === 0) ElMessage.info('该售后仓暂无待整理库存（待分类品）')
  } catch { ElMessage.error('加载待整理库存失败') }
}

function removeItem(index: number) { items.value.splice(index, 1) }
function itemSum(it: any) { return Number(it.qtyA || 0) + Number(it.qtyB || 0) + Number(it.qtyC || 0) + Number(it.qtyDefect || 0) }
function isItemValid(it: any) { return it.totalQuantity > 0 && itemSum(it) === Number(it.totalQuantity) }

async function handleSubmit() {
  if (!form.warehouseId) { ElMessage.warning('请选择源仓库(售后仓)'); return }
  if (!form.targetWarehouseA || !form.targetWarehouseB || !form.targetWarehouseC || !form.targetWarehouseDefect) {
    ElMessage.warning('请选择 A规/B规/C规/不良 的目标入库仓库'); return
  }
  if (items.value.length === 0) { ElMessage.warning('请先加载售后仓待整理库存并录入分选数量'); return }
  if (Number(form.lossAmount) < 0) { ElMessage.warning('折损收款金额不能为负数'); return }
  for (const it of items.value) {
    if (!it.totalQuantity || it.totalQuantity <= 0) { ElMessage.warning(`产品「${it.productName || it.productId}」待整理数量必须大于0`); return }
    if (it.available != null && it.totalQuantity > it.available) {
      ElMessage.warning(`产品「${it.productName || it.productId}」待整理数量(${it.totalQuantity})超过售后仓可用待分类库存(${it.available})`)
      return
    }
    if (itemSum(it) !== Number(it.totalQuantity)) {
      ElMessage.warning(`产品「${it.productName || it.productId}」分选数量之和(${itemSum(it)})必须等于待整理数量(${it.totalQuantity})`)
      return
    }
  }
  submitLoading.value = true
  try {
    const data = { ...form, items: items.value }
    if (form.id) { await updateReturnSort(form.id, data); ElMessage.success('修改成功') }
    else { await createReturnSort(data); ElMessage.success('新增成功') }
    dialogVisible.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitLoading.value = false }
}

async function handleAudit(row: any) {
  const loss = Number(row.lossAmount) || 0
  const lossTip = loss > 0 ? `\n并将生成一条向客户收取的折损应收 ${loss.toFixed(2)} 元（台账单号 ${row.code}-LOSS）。` : ''
  try {
    await ElMessageBox.confirm(`确认审核单号「${row.code}」？审核后将从售后仓扣减待分类品并分品质入库（A/B/C 入成品仓）。${lossTip}`, '审核确认', { type: 'warning' })
    await auditReturnSort(row.id)
    ElMessage.success('审核成功')
    loadData()
  } catch { /* 取消 */ }
}
async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核单号「${row.code}」？反审核后将逆向恢复库存。`, '反审核确认', { type: 'warning' })
    await cancelReturnSort(row.id)
    ElMessage.success('已反审核')
    loadData()
  } catch { /* 取消 */ }
}
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除草稿单「${row.code}」？`, '删除确认', { type: 'warning' })
    await deleteReturnSort(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* 取消 */ }
}

async function loadWarehouses() {
  try { const res: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY' } }); warehouseOptions.value = res?.records || [] } catch { warehouseOptions.value = [] }
}
function warehouseName(id?: number) { const w = warehouseOptions.value.find((x: any) => x.id === id); return w ? w.warehouseName : '' }

const route = useRoute()
// 从库存流水点击关联单号跳转：定位当前页单据并打开详情弹窗
function openFromStockLog() {
  const billId = route.query.billId
  if (!billId) return
  const row = tableData.value.find((r: any) => r.id === Number(billId))
  if (row) handleEdit(row)
}
onMounted(async () => { await loadData(); loadWarehouses(); openFromStockLog() })
</script>

<template>
  <div>
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="单号" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="源仓库">
          <RemoteSelect v-model="query.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option :label="DocStatusLabel[DocStatus.DRAFT]" :value="DocStatus.DRAFT" /><el-option :label="DocStatusLabel[DocStatus.AUDITED]" :value="DocStatus.AUDITED" /><el-option :label="DocStatusLabel[DocStatus.CANCELLED]" :value="DocStatus.CANCELLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="handleAdd">新增退货整理</el-button>
      </div>
      </div>
    </el-card>

    <el-card style="margin-top:12px">
      <el-table :data="tableData" border v-loading="tableLoading" row-key="id" @row-click="(row:any)=>{ row.status===DocStatus.DRAFT && handleEdit(row) }">
        <el-table-column prop="code" label="单号" width="170" />
        <el-table-column label="源仓库" width="130">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column label="A规入库仓" width="120">
          <template #default="{ row }">{{ warehouseName(row.targetWarehouseA) }}</template>
        </el-table-column>
        <el-table-column label="B规入库仓" width="120">
          <template #default="{ row }">{{ warehouseName(row.targetWarehouseB) }}</template>
        </el-table-column>
        <el-table-column label="C规入库仓" width="120">
          <template #default="{ row }">{{ warehouseName(row.targetWarehouseC) }}</template>
        </el-table-column>
        <el-table-column label="不良入库仓" width="120">
          <template #default="{ row }">{{ warehouseName(row.targetWarehouseDefect) }}</template>
        </el-table-column>
        <el-table-column label="折损收款" width="110" align="right">
          <template #default="{ row }">
            <span v-if="Number(row.lossAmount) > 0" style="color:#e6a23c;font-weight:600">{{ Number(row.lossAmount).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortDate" label="整理日期" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status]||'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === DocStatus.DRAFT" type="primary" link @click.stop="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="danger" link @click.stop="handleDelete(row)">删除</el-button>
            <el-button v-if="row.status === DocStatus.AUDITED" type="warning" link @click.stop="handleCancel(row)">反审核</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="1080px" :close-on-click-modal="false" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="源仓库" required>
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择售后仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="整理日期">
              <el-input v-model="form.sortDate" type="date" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注" /></el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="A规入库仓" required>
              <RemoteSelect v-model="form.targetWarehouseA" :fetch="fetchFinishedWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="B规入库仓" required>
              <RemoteSelect v-model="form.targetWarehouseB" :fetch="fetchFinishedWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="C规入库仓" required>
              <RemoteSelect v-model="form.targetWarehouseC" :fetch="fetchFinishedWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="不良入库仓" required>
              <RemoteSelect v-model="form.targetWarehouseDefect" :fetch="fetchTargetWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择仓库" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 折损收款：整理后才知道 B/C/不良 各多少，因此金额挂在本单而非销售退单 -->
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="折损金额">
              <el-input-number v-model="form.lossAmount" :min="0" :precision="2" :step="10"
                controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item label="折损说明">
              <el-input v-model="form.lossRemark" placeholder="选填，如：C规 5 台按残值折损；审核后生成应收向客户收取" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">整理明细（待整理数量 = A + B + C + 不良）</el-divider>
      <div style="margin-bottom:8px">
        <el-button type="primary" :icon="'Download'" :disabled="!form.warehouseId" @click="loadDefectStock">加载售后仓待整理库存</el-button>
      </div>
      <el-table :data="items" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="来源单据" width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.sourceType === AfterSaleSourceType.SALE_EXCHANGE ? 'warning' : 'info'" style="margin-right:4px">
              {{ AfterSaleSourceTypeLabel[row.sourceType] || '-' }}
            </el-tag>
            {{ row.sourceCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="来源日期" width="110">
          <template #default="{ row }">{{ row.sourceDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sku" label="SKU" width="130" />
        <el-table-column prop="productName" label="产品" min-width="160" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" width="110" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="totalQuantity" label="待整理数量" width="110" align="center">
          <template #default="{ row }"><b>{{ row.totalQuantity }}</b></template>
        </el-table-column>
        <el-table-column label="批次量/已整理" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.pendingId">{{ row.batchQuantity ?? 0 }} / {{ row.sortedQuantity ?? 0 }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="停留天数" width="100" align="center">
          <template #default="{ row }">
            <span :style="row.stayDays > STAY_ALERT_DAYS ? 'color:#f56c6c;font-weight:bold' : ''">
              {{ row.stayDays ?? '-' }}
              <span v-if="row.stayDays > STAY_ALERT_DAYS" :title="`已超过 ${STAY_ALERT_DAYS} 天未整理`">超期</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="A数量" width="110">
          <template #default="{ row }"><el-input-number v-model="row.qtyA" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="B数量" width="110">
          <template #default="{ row }"><el-input-number v-model="row.qtyB" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="C数量" width="110">
          <template #default="{ row }"><el-input-number v-model="row.qtyC" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="不良数量" width="110">
          <template #default="{ row }"><el-input-number v-model="row.qtyDefect" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="校验" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="isItemValid(row) ? 'success' : 'danger'" size="small">{{ isItemValid(row) ? '✓ 相等' : `合计${itemSum(row)}` }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
