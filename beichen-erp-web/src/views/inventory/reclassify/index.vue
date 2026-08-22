<script setup lang="ts">
import { WarehouseCategory } from '@/api/enums'
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { getQualityTypes, type QualityOption } from '@/api/product'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import {
  getReclassifyPage, getReclassify, getReclassifyItems,
  createReclassify, updateReclassify,
  auditReclassify, cancelReclassify,
  type ReclassifyItem
} from '@/api/inventory'

const query = reactive({ code: '', status: '' as string | number, warehouseId: '' as string | number })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
const qualityOptions = ref<QualityOption[]>([])
const productOptions = ref<any[]>([])
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 50, keyword: kw } })

// 列表
async function loadData() {
  tableLoading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.status) params.status = query.status
    if (query.warehouseId) params.warehouseId = query.warehouseId
    const res = await getReclassifyPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = []; pagination.total = 0 } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.status = ''; query.warehouseId = ''; pagination.pageNum = 1; loadData() }

// 弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('新增品质重分类')
const submitLoading = ref(false)
const form = reactive({ id: undefined as any, warehouseId: undefined as any, reclassifyDate: new Date().toISOString().slice(0, 10), remark: '' })
const items = ref<ReclassifyItem[]>([])

function resetForm() {
  Object.assign(form, { id: undefined, warehouseId: undefined, reclassifyDate: new Date().toISOString().slice(0, 10), remark: '' })
  items.value = []
}
function handleAdd() { resetForm(); dialogTitle.value = '新增品质重分类'; dialogVisible.value = true }
async function handleEdit(row: any) {
  resetForm()
  dialogTitle.value = '编辑品质重分类'
  try {
    const io = await getReclassify(row.id)
    Object.assign(form, { id: io.id, warehouseId: io.warehouseId, reclassifyDate: io.reclassifyDate, remark: io.remark })
    const its = await getReclassifyItems(row.id)
    items.value = its || []
  } catch { ElMessage.error('获取详情失败') }
  dialogVisible.value = true
}

function addItem() {
  items.value.push({ productId: undefined, fromQuality: 'A', toQuality: 'B', quantity: 0 })
}
function removeItem(index: number) { items.value.splice(index, 1) }

async function onProductPick(p: any, row: any) {
  if (!p) return
  row.productId = p.id
  row.productName = p.name
  row.spec = p.spec
  row.unit = p.unit
}

async function loadProductOptions(query?: string) {
  try {
    const res = await fetchProducts(query || '')
    productOptions.value = res?.records || []
  } catch { productOptions.value = [] }
}

async function handleSubmit() {
  if (!form.warehouseId) { ElMessage.warning('请选择仓库'); return }
  if (items.value.length === 0) { ElMessage.warning('请添加重分类明细'); return }
  for (const it of items.value) {
    if (!it.productId) { ElMessage.warning('请选择产品'); return }
    if (!it.fromQuality || !it.toQuality) { ElMessage.warning('请选择品质'); return }
    if (it.fromQuality === it.toQuality) { ElMessage.warning('原品质和目标品质不能相同'); return }
    if (!it.quantity || it.quantity <= 0) { ElMessage.warning('数量必须大于0'); return }
  }
  submitLoading.value = true
  try {
    const data = { ...form, items: items.value }
    if (form.id) {
      await updateReclassify(form.id, data)
      ElMessage.success('修改成功')
    } else {
      await createReclassify(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitLoading.value = false }
}

async function handleAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认审核单号「${row.code}」？审核后库存将立即变更。`, '审核确认', { type: 'warning' })
    await auditReclassify(row.id)
    ElMessage.success('审核成功')
    loadData()
  } catch { /* 取消 */ }
}
async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核单号「${row.code}」？反审核后将逆向恢复库存。`, '反审核确认', { type: 'warning' })
    await cancelReclassify(row.id)
    ElMessage.success('已反审核')
    loadData()
  } catch { /* 取消 */ }
}

async function loadWarehouses() {
  try { const res = await request.get<any, any>('/warehouse/page', { params: { pageSize: 200, warehouseCategory: WarehouseCategory.INVENTORY } }); warehouseOptions.value = res?.records || [] } catch { warehouseOptions.value = [] }
}
async function loadQualityTypes() {
  try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] }
}

function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }
function warehouseName(id?: number) { const w = warehouseOptions.value.find((x: any) => x.id === id); return w ? w.warehouseName : '' }

onMounted(() => { loadData(); loadWarehouses(); loadQualityTypes(); loadProductOptions() })

</script>

<template>
  <div>
    <el-card>
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="单号" clearable /></el-form-item>
        <el-form-item label="仓库">
          <RemoteSelect v-model="query.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option :label="DocStatusLabel[DocStatus.DRAFT]" :value="DocStatus.DRAFT" /><el-option :label="DocStatusLabel[DocStatus.AUDITED]" :value="DocStatus.AUDITED" /><el-option :label="DocStatusLabel[DocStatus.CANCELLED]" :value="DocStatus.CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top:12px">
      <div style="margin-bottom:12px">
        <el-button type="success" @click="handleAdd">新增品质重分类</el-button>
      </div>
      <el-table :data="tableData" border v-loading="tableLoading" row-key="id">
        <el-table-column prop="code" label="单号" width="160" />
        <el-table-column label="仓库" width="140">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="reclassifyDate" label="日期" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status]||'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === DocStatus.DRAFT" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.AUDITED" type="danger" link @click="handleCancel(row)">反审核</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px" :close-on-click-modal="false" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="仓库" required>
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择仓库" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="日期">
              <el-input v-model="form.reclassifyDate" type="date" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">重分类明细</el-divider>
      <div style="margin-bottom:8px">
        <el-button type="primary" @click="addItem">添加明细</el-button>
      </div>
      <el-table :data="items" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="产品" min-width="200">
          <template #default="{ row }">
            <RemoteSelect v-model="row.productId" :fetch="fetchProducts" placeholder="搜索产品" style="width:100%" @pick="(rows:any[])=>onProductPick(rows[0],row)" />
          </template>
        </el-table-column>
        <el-table-column prop="spec" label="规格" width="100" />
        <el-table-column label="原品质" width="100">
          <template #default="{ row }">
            <el-select v-model="row.fromQuality" size="small" style="width:100%">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="目标品质" width="100">
          <template #default="{ row }">
            <el-select v-model="row.toQuality" size="small" style="width:100%">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
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
