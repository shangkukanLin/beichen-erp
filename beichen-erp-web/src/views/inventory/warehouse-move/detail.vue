<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { WarehouseCategory, INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY } from '@/api/enums'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'

const route = useRoute()
// 用 computed 取路由参数：keep-alive 会复用组件，从单据 A 跳到 B 时 route.params.id 会变
const id = computed(() => Number(route.params.id) || 0)
const loading = ref(false)
const saving = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
// 2026-09-20（F7-177）：详情只回显移出/移入 2 个仓库名 ⇒ 按 id 单取（原为 pageSize=500 全量拉 + 前端 find）
const fromWarehouseName = ref('-')
const toWarehouseName = ref('-')
const products = ref<any[]>([])
const qualityOptions = ref<QualityOption[]>([])

/** 草稿（未审核）态直接可编辑，无需再点「编辑」 */
const isDraft = computed(() => detail.value.status === DocStatus.DRAFT)

// ===== 草稿态编辑表单 =====
const editForm = reactive({
  fromWarehouseId: undefined as any,
  toWarehouseId: undefined as any,
  moveDate: '',
  remark: ''
})
const editItems = ref<any[]>([])
/**
 * 未保存拦截（2026-09-23 统一模板）：本页草稿态**可直接编辑并保存**，属"能改数据"，
 * 故同样接入守卫（避免改了一半点返回静默丢失）。
 * ⚠️ 必须写在 detail / editForm / editItems **之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline } = useUnsavedGuard(() => ({
  detail: detail.value, items: items.value, editForm, editItems: editItems.value
}))
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })

function addEditItem() {
  editItems.value.push({ productId: undefined, sku: '', unit: '', qualityType: 'A', quantity: 1, stockQty: undefined, remark: '' })
}
function removeEditItem(i: number) { editItems.value.splice(i, 1) }

function onProductPick(p: any, row: any) {
  if (!p) { row.productId = undefined; row.sku = ''; row.unit = ''; return }
  row.productId = p.id
  row.sku = p.sku || ''
  row.unit = p.unit || ''
  loadStock(row)
}

/** 移仓从「移出仓」扣减，所以库存展示与校验都针对移出仓 + 该行品质 */
async function loadStock(row: any) {
  row.stockQty = undefined
  if (!editForm.fromWarehouseId || !row.productId) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: editForm.fromWarehouseId, productId: row.productId, stockType: 'PRODUCT', pageSize: 100 }
    })
    const recs: any[] = r?.records || []
    const hit = recs.find((x: any) => String(x.qualityType) === String(row.qualityType || 'A'))
    row.stockQty = hit ? Number(hit.quantity) : 0
  } catch { row.stockQty = undefined }
}
function onFromWarehouseChange() { editItems.value.forEach(loadStock) }
/** 移仓数量超过移出仓该品质的可用库存 */
function overStock(row: any) {
  return row.stockQty != null && Number(row.quantity) > 0 && Number(row.quantity) > row.stockQty
}

function fillEditForm() {
  editForm.fromWarehouseId = detail.value.fromWarehouseId
  editForm.toWarehouseId = detail.value.toWarehouseId
  editForm.moveDate = detail.value.moveDate ? String(detail.value.moveDate).slice(0, 10) : ''
  editForm.remark = detail.value.remark || ''
  editItems.value = items.value.map((i: any) => ({
    productId: i.productId,
    sku: i.sku || '',
    unit: i.unit || getProdUnit(i.productId),
    qualityType: i.qualityType || 'A',
    quantity: i.quantity,
    stockQty: undefined,
    remark: i.remark || ''
  }))
  if (editItems.value.length === 0) addEditItem()
  editItems.value.forEach(loadStock)
}

/** 按 id 取仓库名（详情单条展示；列表页做多行映射时才全量拉） */
async function loadWarehouseNames() {
  const pick = async (wid?: number) => {
    if (!wid) return '-'
    try { const w: any = await request.get(`/warehouse/${wid}`); return w?.warehouseName || w?.name || '-' }
    catch { return '-' }
  }
  fromWarehouseName.value = await pick(detail.value?.fromWarehouseId)
  toWarehouseName.value = await pick(detail.value?.toWarehouseId)
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
    const mv = await request.get<any, any>(`/inventory/warehouse-move/${id.value}`)
    detail.value = mv || {}
    const its = await request.get<any, any>(`/inventory/warehouse-move/${id.value}/items`)
    items.value = Array.isArray(its) ? its : []
    if (products.value.length === 0) await loadProducts()
    if (isDraft.value) fillEditForm()
    await loadWarehouseNames()
  } finally { loading.value = false }
  // 数据加载完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改；
  // 审核/保存后本函数会重跑 ⇒ 基线自动重置，不会误报）
  takeBaseline()
}

async function handleSave() {
  if (!editForm.fromWarehouseId) { ElMessage.warning('请选择移出仓库'); return }
  if (!editForm.toWarehouseId) { ElMessage.warning('请选择移入仓库'); return }
  if (editForm.fromWarehouseId === editForm.toWarehouseId) { ElMessage.warning('移出与移入仓库不能相同'); return }
  if (editItems.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
  for (const it of editItems.value) {
    if (!it.productId) { ElMessage.warning('请选择产品'); return }
    if (!(Number(it.quantity) > 0)) { ElMessage.warning('移仓数量必须大于 0'); return }
    if (it.stockQty != null && Number(it.quantity) > it.stockQty) {
      ElMessage.warning(`产品「${getProdName(it.productId)}」移仓数量 ${it.quantity} 超过移出仓可用库存 ${it.stockQty}，无法保存`)
      return
    }
  }
  saving.value = true
  try {
    await request.put(`/inventory/warehouse-move/${id.value}`, {
      move: { ...editForm, id: id.value },
      items: editItems.value.map((it: any) => ({
        productId: it.productId,
        qualityType: it.qualityType || 'A',
        quantity: Number(it.quantity),
        remark: it.remark || ''
      }))
    })
    ElMessage.success('已保存')
    sessionStorage.setItem(INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY, '1')
    await loadDetail()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

/**
 * 审核 / 反审核 / 作废：操作后刷新本页并置脏标志，
 * 返回列表时列表会重新拉取，不会出现「详情已审核、列表还显示未审核」。
 */
async function afterStatusChange() {
  sessionStorage.setItem(INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY, '1')
  await loadDetail()
}
async function handleAudit() {
  try { await ElMessageBox.confirm('确认审核？将从移出仓扣减库存并增加到移入仓。', '审核确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/inventory/warehouse-move/${id.value}/audit`); ElMessage.success('审核成功'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function handleUnAudit() {
  try { await ElMessageBox.confirm('确认反审核？将退回移出仓库存并从移入仓扣回。', '反审核确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/inventory/warehouse-move/${id.value}/un-audit`); ElMessage.success('反审核成功'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废该移仓单？', '作废确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/inventory/warehouse-move/${id.value}/cancel`); ElMessage.success('已作废'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(() => { loadProducts(); loadQualityTypes() })
// keep-alive 缓存下再次进入会复用组件，onMounted 不再触发
onActivated(() => { loadDetail() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头最左=主操作（保存）→ 标题 → 右侧=次要动作+返回 -->
  <PageShell :loading="loading" back-fallback="/inventory/warehouse-move">
    <template #leading>
      <el-button v-if="isDraft" type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
    <template #actions>
      <el-button type="success" size="small" v-if="detail.status===DocStatus.DRAFT" @click="handleAudit">审核</el-button>
      <el-button type="warning" size="small" v-if="detail.status===DocStatus.AUDITED" @click="handleUnAudit">反审核</el-button>
      <el-button type="danger" size="small" v-if="detail.status===DocStatus.DRAFT" @click="handleCancel">作废</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">单据信息</span>
          <div style="display:flex;align-items:center;gap:8px">
            <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
          </div>
        </div>
      </template>

      <el-form v-if="isDraft" :model="editForm" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="移出仓库" required>
              <RemoteSelect v-model="editForm.fromWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="请选择" style="width:100%" @update:modelValue="onFromWarehouseChange" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="移入仓库" required>
              <RemoteSelect v-model="editForm.toWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="请选择" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="移仓日期"><el-input v-model="editForm.moveDate" type="date" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <el-descriptions v-else :column="3" border>
        <el-descriptions-item label="单号">{{ detail.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="移出仓库">{{ fromWarehouseName }}</el-descriptions-item>
        <el-descriptions-item label="移入仓库">{{ toWarehouseName }}</el-descriptions-item>
        <el-descriptions-item label="移仓日期">{{ detail.moveDate ? $fmtDate(detail.moveDate) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">移仓明细</span>
          <el-button v-if="isDraft" type="primary" size="small" @click="addEditItem">+ 添加明细</el-button>
        </div>
      </template>

      <!-- 草稿：可编辑明细（带移出仓可用库存） -->
      <el-table v-if="isDraft" :data="editItems" border size="small">
        <el-table-column label="SKU" width="130">
          <template #default="{ row }">
            <span v-if="row.sku">{{ row.sku }}</span>
            <span v-else style="color:var(--app-text-placeholder)">自动生成</span>
          </template>
        </el-table-column>
        <el-table-column label="产品" min-width="200">
          <template #default="{ row }">
            <RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="选择产品（可输SKU）" style="width:100%" @pick="(rows:any[])=>onProductPick(rows[0],row)" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="70">
          <template #default="{ row }">{{ row.unit || '-' }}</template>
        </el-table-column>
        <el-table-column label="品质" width="100">
          <template #default="{ row }">
            <el-select v-model="row.qualityType" size="small" style="width:100%" @change="loadStock(row)">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="移出仓可用库存" width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.stockQty != null" :style="overStock(row) ? 'color:#f56c6c;font-weight:600' : ''">{{ Number(row.stockQty) }}</span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="移仓数量" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" />
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
        <el-table-column label="产品" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.productName || getProdName(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="单位" width="70">
          <template #default="{ row }">{{ row.unit || getProdUnit(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="品质" width="90" align="center">
          <template #default="{ row }">{{ qualityLabel(row.qualityType) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="110" align="right" />
      </el-table>
    </el-card>

  </PageShell>
</template>
