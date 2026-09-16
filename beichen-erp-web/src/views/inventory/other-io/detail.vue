<script setup lang="ts">
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { IoType, IoTypeLabel, WarehouseCategory, INVENTORY_OTHER_IO_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id) || 0
const loading = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
const warehouses = ref<any[]>([])
const products = ref<any[]>([])
const qualityOptions = ref<QualityOption[]>([])

/** 草稿（未审核）态直接可编辑，无需再点「编辑」 */
const isDraft = computed(() => detail.value.status === DocStatus.DRAFT)
const saving = ref(false)

// ===== 草稿态编辑表单 =====
const editForm = reactive({ warehouseId: undefined as any, ioType: '', ioDate: '', remark: '' })
const editItems = ref<any[]>([])
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })

// 出库单才需要看库存：入库不消耗库存，不展示也不校验
const isOut = computed(() => editForm.ioType === IoType.OUT)

function addEditItem() {
  editItems.value.push({ productId: undefined, productName: '', unit: '', qualityType: 'A', quantity: undefined, remark: '', stockQty: undefined })
}
function removeEditItem(i: number) { editItems.value.splice(i, 1) }

/**
 * 查询该成品在所选仓库、所选品质下的可用库存（仅出库单需要）。
 * 仓库 / 成品 / 品质三者任一变化都要重新查。
 */
async function loadStock(row: any) {
  row.stockQty = undefined
  if (!isOut.value || !editForm.warehouseId || !row.productId) return
  const qt = row.qualityType || 'A'
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: editForm.warehouseId, productId: row.productId, stockType: 'PRODUCT', pageSize: 100 }
    })
    const recs: any[] = r?.records || []
    const hit = recs.find((x: any) => String(x.qualityType) === String(qt))
    row.stockQty = hit ? Number(hit.quantity) : 0
  } catch { row.stockQty = undefined }
}
function onWarehouseChange() { editItems.value.forEach(loadStock) }
function onIoTypeChange() { editItems.value.forEach(loadStock) }
/** 出库数量超过可用库存 */
function overStock(row: any) {
  return isOut.value && row.stockQty != null && Number(row.quantity) > 0 && Number(row.quantity) > row.stockQty
}
/** 选中成品后带出单位 */
function onProductPick(row: any, idx: number) {
  const it = editItems.value[idx]
  if (!row) { it.productId = undefined; it.productName = ''; it.unit = ''; return }
  it.productId = row.id
  it.productName = row.name || row.productName || ''
  it.unit = row.unit || ''
  loadStock(it)
}
/** 用单据数据回填编辑表单（明细带出单位靠本地产品缓存） */
function fillEditForm() {
  editForm.warehouseId = detail.value.warehouseId
  editForm.ioType = detail.value.ioType || IoType.IN
  editForm.ioDate = detail.value.ioDate ? String(detail.value.ioDate).slice(0, 10) : ''
  editForm.remark = detail.value.remark || ''
  editItems.value = items.value.map((i: any) => ({
    productId: i.productId,
    productName: i.productId != null ? getProdName(i.productId) : '',
    unit: getProdUnit(i.productId),
    qualityType: i.qualityType || 'A',
    quantity: i.quantity,
    remark: i.remark || '',
    stockQty: undefined
  }))
  if (editItems.value.length === 0) addEditItem()
  if (isOut.value) editItems.value.forEach(loadStock)
}

function getWhName(wid: number) {
  return warehouses.value.find((w: any) => w.id === wid)?.warehouseName || '-'
}
function getProdName(pid: number | undefined) {
  if (pid == null) return '-'
  return products.value.find((p: any) => p.id === pid)?.name || '-'
}
function getProdUnit(pid: number | undefined) {
  if (pid == null) return '-'
  return products.value.find((p: any) => p.id === pid)?.unit || '-'
}
function qualityLabel(q: string | undefined) {
  if (q == null) return '-'
  return qualityOptions.value.find((o: any) => o.value === q)?.label || q
}
function statusLabel(s: string) {
  return DocStatusLabel[s] || s || '-'
}
function statusTag(s: string): any {
  return DocStatusTag[s] || 'warning'
}

async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseCategory: WarehouseCategory.INVENTORY } })
    warehouses.value = r?.records || []
  } catch {}
}
async function loadProducts() {
  try {
    const r = await request.get<any, any>('/product/page', { params: { pageSize: 500 } })
    products.value = r?.records || []
  } catch {}
}
async function loadQualityTypes() {
  try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] }
}
async function loadDetail() {
  loading.value = true
  try {
    const io = await request.get<any, any>(`/inventory/other/${id}`)
    detail.value = io || {}
    const its = await request.get<any, any>(`/inventory/other/${id}/items`)
    items.value = Array.isArray(its) ? its : []
    // 明细要带出单位，依赖产品缓存，先确保已加载
    if (products.value.length === 0) await loadProducts()
    if (isDraft.value) fillEditForm()
  } finally { loading.value = false }
}

async function handleSave() {
  if (!editForm.warehouseId) { ElMessage.warning('请选择仓库'); return }
  const validItems = editItems.value.filter((i: any) => Number(i.quantity) > 0)
  if (validItems.length === 0) { ElMessage.warning('请添加成品明细并填写数量'); return }
  if (validItems.some((i: any) => !i.productId)) { ElMessage.warning('存在未选择成品的明细行'); return }
  // 出库：数量不能超出该仓库该品质的可用库存（后端审核时还会再校验一次）
  if (isOut.value) {
    for (const it of validItems) {
      if (it.stockQty != null && Number(it.quantity) > it.stockQty) {
        ElMessage.warning(`成品「${it.productName || it.productId}」出库数量 ${it.quantity} 超过可用库存 ${it.stockQty}，无法保存`)
        return
      }
    }
  }
  saving.value = true
  try {
    await request.put(`/inventory/other/${id}`, {
      warehouseId: editForm.warehouseId,
      ioType: editForm.ioType,
      ioDate: editForm.ioDate,
      remark: editForm.remark,
      items: validItems.map((i: any) => ({
        productId: i.productId,
        quantity: Number(i.quantity),
        qualityType: i.qualityType || 'A',
        remark: i.remark || ''
      }))
    })
    ElMessage.success('已保存')
    sessionStorage.setItem(INVENTORY_OTHER_IO_DIRTY_KEY, '1')
    await loadDetail()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

/**
 * 审核/反审核/作废：操作后刷新本页状态，并置脏标志，
 * 这样返回列表时列表会重新拉取，不会出现「详情已审核、列表还显示未审核」。
 */
async function afterStatusChange() {
  sessionStorage.setItem(INVENTORY_OTHER_IO_DIRTY_KEY, '1')
  await loadDetail()
}
async function handleAudit() {
  try { await ElMessageBox.confirm('确认审核？审核后按明细增减库存', '审核确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/inventory/other/${id}/audit`); ElMessage.success('已审核'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function handleUnAudit() {
  try { await ElMessageBox.confirm('确认反审核？将逆向增减库存并回到草稿', '反审核确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/inventory/other/${id}/un-audit`); ElMessage.success('已反审核'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废？作废后不可恢复', '作废确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/inventory/other/${id}/cancel`); ElMessage.success('已作废'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

// 字典类只需加载一次
onMounted(() => { loadWarehouses(); loadProducts(); loadQualityTypes() })
// 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
onActivated(() => { loadDetail() })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">

    <!-- 草稿：直接可编辑的表单；已审核/已作废：只读信息 -->
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">单据信息</span>
          <div style="display:flex;align-items:center;gap:8px">
            <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
            <el-button type="success" size="small" v-if="detail.status===DocStatus.DRAFT" @click="handleAudit">审核</el-button>
            <el-button type="warning" size="small" v-if="detail.status===DocStatus.AUDITED" @click="handleUnAudit">反审核</el-button>
            <el-button type="danger" size="small" v-if="detail.status===DocStatus.DRAFT" @click="handleCancel">作废</el-button>
          </div>
        </div>
      </template>

      <el-form v-if="isDraft" :model="editForm" label-width="80px" size="small">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="仓库" required><RemoteSelect v-model="editForm.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" style="width:100%" @update:modelValue="onWarehouseChange" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="类型"><el-select v-model="editForm.ioType" style="width:100%" @change="onIoTypeChange"><el-option :label="IoTypeLabel[IoType.IN]" :value="IoType.IN"/><el-option :label="IoTypeLabel[IoType.OUT]" :value="IoType.OUT"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="日期"><el-input v-model="editForm.ioDate" type="date"/></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="editForm.remark" placeholder="备注"/></el-form-item>
      </el-form>

      <el-descriptions v-else :column="3" border>
        <el-descriptions-item label="单号">{{ detail.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ getWhName(detail.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ IoTypeLabel[detail.ioType] || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.ioDate ? $fmtDate(detail.ioDate) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">成品明细</span>
          <el-button v-if="isDraft" type="primary" size="small" @click="addEditItem">+ 添加成品</el-button>
        </div>
      </template>

      <!-- 草稿：可编辑明细 -->
      <el-table v-if="isDraft" :data="editItems" border size="small">
        <el-table-column label="成品名称" min-width="180">
          <template #default="{row,$index}">
            <RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="可输SKU搜索" style="width:100%" @pick="(rows:any[])=>onProductPick(rows[0],$index)" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="80">
          <template #default="{row}">{{ row.unit || '-' }}</template>
        </el-table-column>
        <el-table-column label="品质" width="100">
          <template #default="{row}">
            <el-select v-model="row.qualityType" size="small" style="width:100%" @change="loadStock(row)">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-if="isOut" label="可用库存" width="100" align="right">
          <template #default="{row}">
            <span v-if="row.stockQty != null" :style="overStock(row) ? 'color:#f56c6c;font-weight:600' : ''">{{ Number(row.stockQty) }}</span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template #default="{row}">
            <el-input v-model="row.quantity" size="small" type="number"/>
            <div v-if="overStock(row)" style="color:#f56c6c;font-size:var(--app-font-xs);line-height:1.2;margin-top:2px">超出库存</div>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{row}"><el-input v-model="row.remark" size="small"/></template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{$index}"><el-button type="danger" link size="small" @click="removeEditItem($index)">删除</el-button></template>
        </el-table-column>
      </el-table>

      <!-- 非草稿：只读明细 -->
      <el-table v-else :data="items" border size="small">
        <el-table-column label="成品名称" min-width="160" show-overflow-tooltip>
          <template #default="{row}">{{ getProdName(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="单位" width="80">
          <template #default="{row}">{{ getProdUnit(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="品质" width="90">
          <template #default="{row}">{{ qualityLabel(row.qualityType) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="110" align="right"/>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{row}">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <div style="display:flex;gap:12px;justify-content:center">
      <el-button v-if="isDraft" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      <el-button @click="router.push('/inventory/other-io')">返回列表</el-button>
    </div>
  </div>
</template>
