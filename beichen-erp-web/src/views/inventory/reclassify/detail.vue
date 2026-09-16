<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { WarehouseCategory, INVENTORY_RECLASSIFY_DIRTY_KEY } from '@/api/enums'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { getReclassify, getReclassifyItems, updateReclassify, auditReclassify, unAuditReclassify, cancelReclassify } from '@/api/inventory'
import RemoteSelect from '@/components/RemoteSelect.vue'

const route = useRoute(); const router = useRouter()
// 用 computed 取路由参数：keep-alive 会复用组件，从单据 A 跳到单据 B 时 route.params.id 会变，
// 若在 setup 阶段固化成常量，会一直显示第一次进入的那张单据
const id = computed(() => Number(route.params.id) || 0)
const loading = ref(false)
const saving = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
const warehouses = ref<any[]>([])
const products = ref<any[]>([])
const qualityOptions = ref<QualityOption[]>([])

/** 草稿（未审核）态直接可编辑，无需再点「编辑」 */
const isDraft = computed(() => detail.value.status === DocStatus.DRAFT)

// ===== 草稿态编辑表单 =====
const editForm = reactive({ warehouseId: undefined as any, reclassifyDate: '', remark: '' })
const editItems = ref<any[]>([])
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 50, keyword: kw } })

function addEditItem() {
  editItems.value.push({ productId: undefined, productName: '', sku: '', unit: '', fromQuality: 'A', toQuality: 'B', quantity: undefined, stockQty: undefined })
}
function removeEditItem(i: number) { editItems.value.splice(i, 1) }
function onProductPick(p: any, row: any) {
  if (!p) { row.productId = undefined; row.productName = ''; row.sku = ''; row.unit = ''; return }
  row.productId = p.id
  row.productName = p.name || p.productName || ''
  row.sku = p.sku || ''
  row.unit = p.unit || ''
  loadStock(row)
}

/** 重分类只扣「原品质」，所以库存展示与校验都针对 fromQuality */
async function loadStock(row: any) {
  row.stockQty = undefined
  if (!editForm.warehouseId || !row.productId || !row.fromQuality) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: editForm.warehouseId, productId: row.productId, stockType: 'PRODUCT', pageSize: 100 }
    })
    const recs: any[] = r?.records || []
    const hit = recs.find((x: any) => String(x.qualityType) === String(row.fromQuality))
    row.stockQty = hit ? Number(hit.quantity) : 0
  } catch { row.stockQty = undefined }
}
function onWarehouseChange() { editItems.value.forEach(loadStock) }
function overStock(row: any) {
  return row.stockQty != null && Number(row.quantity) > 0 && Number(row.quantity) > row.stockQty
}

function fillEditForm() {
  editForm.warehouseId = detail.value.warehouseId
  editForm.reclassifyDate = detail.value.reclassifyDate ? String(detail.value.reclassifyDate).slice(0, 10) : ''
  editForm.remark = detail.value.remark || ''
  editItems.value = items.value.map((i: any) => ({
    productId: i.productId,
    productName: i.productName || getProdName(i.productId),
    sku: i.sku || '',
    unit: i.unit || getProdUnit(i.productId),
    fromQuality: i.fromQuality || 'A',
    toQuality: i.toQuality || 'B',
    quantity: i.quantity,
    stockQty: undefined
  }))
  if (editItems.value.length === 0) addEditItem()
  editItems.value.forEach(loadStock)
}

function getWhName(wid?: number) {
  return warehouses.value.find((w: any) => w.id === wid)?.warehouseName || '-'
}
function getProdName(pid?: number) {
  if (pid == null) return '-'
  return products.value.find((p: any) => p.id === pid)?.name || '-'
}
function getProdUnit(pid?: number) {
  if (pid == null) return '-'
  return products.value.find((p: any) => p.id === pid)?.unit || '-'
}
function qualityLabel(q?: string) {
  if (q == null) return '-'
  return qualityOptions.value.find((o: any) => o.value === q)?.label || q
}
function statusLabel(s: string) { return DocStatusLabel[s] || s || '-' }
function statusTag(s: string): any { return DocStatusTag[s] || 'warning' }

async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 200, warehouseCategory: WarehouseCategory.INVENTORY } })
    warehouses.value = r?.records || []
  } catch { warehouses.value = [] }
}
async function loadProducts() {
  try {
    const r = await request.get<any, any>('/product/page', { params: { pageSize: 500 } })
    products.value = r?.records || []
  } catch { products.value = [] }
}
async function loadQualityTypes() {
  try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] }
}
async function loadDetail() {
  loading.value = true
  try {
    const rc = await getReclassify(id.value)
    detail.value = rc || {}
    const its = await getReclassifyItems(id.value)
    items.value = Array.isArray(its) ? its : []
    if (products.value.length === 0) await loadProducts()
    if (isDraft.value) fillEditForm()
  } finally { loading.value = false }
}

async function handleSave() {
  if (!editForm.warehouseId) { ElMessage.warning('请选择仓库'); return }
  if (editItems.value.length === 0) { ElMessage.warning('请添加重分类明细'); return }
  for (const it of editItems.value) {
    if (!it.productId) { ElMessage.warning('请选择产品'); return }
    if (!it.fromQuality || !it.toQuality) { ElMessage.warning('请选择品质'); return }
    if (it.fromQuality === it.toQuality) { ElMessage.warning('原品质和目标品质不能相同'); return }
    if (!(Number(it.quantity) > 0)) { ElMessage.warning('数量必须大于0'); return }
    if (it.stockQty != null && Number(it.quantity) > it.stockQty) {
      ElMessage.warning(`产品「${it.productName || it.productId}」原品质 ${it.fromQuality} 数量 ${it.quantity} 超过可用库存 ${it.stockQty}，无法保存`)
      return
    }
  }
  saving.value = true
  try {
    await updateReclassify(id.value, {
      ...editForm,
      items: editItems.value.map((it: any) => ({
        productId: it.productId,
        fromQuality: it.fromQuality,
        toQuality: it.toQuality,
        quantity: Number(it.quantity)
      }))
    })
    ElMessage.success('已保存')
    sessionStorage.setItem(INVENTORY_RECLASSIFY_DIRTY_KEY, '1')
    await loadDetail()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

/**
 * 审核 / 反审核：操作后刷新本页并置脏标志，
 * 返回列表时列表会重新拉取，不会出现「详情已审核、列表还显示未审核」。
 * E2 口径（2026-09-12）：反审核走 /un-audit（逆向库存并置 CANCELLED），
 * 作废走 /cancel（仅草稿）—— 两者语义拆开，不再由 cancel 兼任反审核。
 */
async function afterStatusChange() {
  sessionStorage.setItem(INVENTORY_RECLASSIFY_DIRTY_KEY, '1')
  await loadDetail()
}
async function handleAudit() {
  try { await ElMessageBox.confirm('确认审核？审核后库存将立即变更（原品质扣减、目标品质增加）', '审核确认', { type: 'warning' }) } catch { return }
  try { await auditReclassify(id.value); ElMessage.success('已审核'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
/** 反审核（已审核 → 逆向库存 + CANCELLED） */
async function handleUnAudit() {
  try { await ElMessageBox.confirm('确认反审核？将逆向恢复库存（恢复原品质、冲回目标品质）', '反审核确认', { type: 'warning' }) } catch { return }
  try { await unAuditReclassify(id.value); ElMessage.success('已反审核'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
/** 作废（仅草稿） */
async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废该草稿单？', '作废确认', { type: 'warning' }) } catch { return }
  try { await cancelReclassify(id.value); ElMessage.success('已作废'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(() => { loadWarehouses(); loadProducts(); loadQualityTypes() })
// keep-alive 缓存下再次进入会复用组件，onMounted 不再触发
onActivated(() => { loadDetail() })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">单据信息</span>
          <div style="display:flex;align-items:center;gap:8px">
            <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
            <el-button type="success" size="small" v-if="detail.status===DocStatus.DRAFT" @click="handleAudit">审核</el-button>
            <el-button type="warning" size="small" v-if="detail.status===DocStatus.AUDITED" @click="handleUnAudit">反审核</el-button>
            <el-button type="info" size="small" v-if="detail.status===DocStatus.DRAFT" @click="handleCancel">作废</el-button>
          </div>
        </div>
      </template>

      <el-form v-if="isDraft" :model="editForm" label-width="80px" size="small">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="仓库" required>
              <RemoteSelect v-model="editForm.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" style="width:100%" @update:modelValue="onWarehouseChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="日期"><el-input v-model="editForm.reclassifyDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="备注"><el-input v-model="editForm.remark" placeholder="备注" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <el-descriptions v-else :column="3" border>
        <el-descriptions-item label="单号">{{ detail.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ getWhName(detail.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.reclassifyDate ? $fmtDate(detail.reclassifyDate) : '-' }}</el-descriptions-item>
        <!-- 整理人=建单时登录的账户（历史单据无记录显示 —） -->
        <el-descriptions-item label="整理人">{{ detail.createByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">重分类明细</span>
          <el-button v-if="isDraft" type="primary" size="small" @click="addEditItem">+ 添加明细</el-button>
        </div>
      </template>

      <!-- 草稿：可编辑明细（带原品质可用库存） -->
      <el-table v-if="isDraft" :data="editItems" border size="small">
        <el-table-column label="SKU" width="130">
          <template #default="{ row }">
            <span v-if="row.sku">{{ row.sku }}</span>
            <span v-else style="color:var(--app-text-placeholder)">自动生成</span>
          </template>
        </el-table-column>
        <el-table-column label="产品" min-width="200">
          <template #default="{ row }">
            <RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="搜索产品（可输SKU）" style="width:100%" @pick="(rows:any[])=>onProductPick(rows[0],row)" />
          </template>
        </el-table-column>
        <el-table-column label="原品质" width="110">
          <template #default="{ row }">
            <el-select v-model="row.fromQuality" size="small" style="width:100%" @change="loadStock(row)">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="目标品质" width="110">
          <template #default="{ row }">
            <el-select v-model="row.toQuality" size="small" style="width:100%">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="原品质可用库存" width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.stockQty != null" :style="overStock(row) ? 'color:#f56c6c;font-weight:600' : ''">{{ Number(row.stockQty) }}</span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="130">
          <template #default="{ row }">
            <el-input v-model="row.quantity" size="small" type="number" />
            <div v-if="overStock(row)" style="color:#f56c6c;font-size:var(--app-font-xs);line-height:1.2;margin-top:2px">超出库存</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }"><el-button type="danger" link size="small" @click="removeEditItem($index)">删除</el-button></template>
        </el-table-column>
      </el-table>

      <!-- 非草稿：只读明细 -->
      <el-table v-else :data="items" border size="small">
        <el-table-column prop="sku" label="SKU" width="130">
          <template #default="{ row }">{{ row.sku || '-' }}</template>
        </el-table-column>
        <el-table-column label="产品" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.productName || getProdName(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="原品质" width="100" align="center">
          <template #default="{ row }">{{ qualityLabel(row.fromQuality) }}</template>
        </el-table-column>
        <el-table-column label="目标品质" width="100" align="center">
          <template #default="{ row }">{{ qualityLabel(row.toQuality) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="110" align="right" />
      </el-table>
    </el-card>

    <div style="display:flex;gap:12px;justify-content:center">
      <el-button v-if="isDraft" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      <el-button @click="router.push('/inventory/reclassify')">返回列表</el-button>
    </div>
  </div>
</template>
