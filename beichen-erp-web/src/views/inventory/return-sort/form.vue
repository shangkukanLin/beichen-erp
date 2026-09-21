<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { WarehouseType, AfterSaleSourceType, AfterSaleSourceTypeLabel, INVENTORY_RETURN_SORT_DIRTY_KEY } from '@/api/enums'
import {
  getReturnSort, getReturnSortItems, getReturnSortDefectStock,
  createReturnSort, updateReturnSort
} from '@/api/inventory'

const route = useRoute(); const router = useRouter()

/**
 * 嵌入模式（2026-09-19 退货整理页优化）：「待整理」总览点行「整理」时在抽屉里复用本表单。
 * presetWarehouseId = 源仓库（由来源批次决定），presetPendingIds = 要整理的来源批次（空 = 整仓）。
 * 保存后 emit 'saved'（父页刷新总览与整理单列表），不再跳路由。
 */
const props = defineProps<{
  embedded?: boolean
  presetWarehouseId?: number | null
  presetPendingIds?: number[] | null
}>()
const emit = defineEmits<{ (e: 'saved', id?: number): void; (e: 'cancel'): void }>()

/** 新增（/inventory/return-sort/add）与编辑（/inventory/return-sort/edit/:id）共用本页 */
const id = computed(() => (route.params.id != null && route.params.id !== '' ? Number(route.params.id) : undefined))
const isNew = computed(() => id.value == null)

const loading = ref(false)
const saving = ref(false)
/** 编辑模式的单号（标题展示用；新增时为空） */
const code = ref('')

const form = reactive({
  id: undefined as any,
  warehouseId: undefined as any,
  sortDate: localDate(),
  targetWarehouseA: undefined as any,
  targetWarehouseB: undefined as any,
  targetWarehouseC: undefined as any,
  targetWarehouseDefect: undefined as any,
  remark: ''
})
const items = ref<any[]>([])

/** 待整理库存超期预警阈值（天）：停留超过该天数则标红提示 */
const STAY_ALERT_DAYS = 3

// 源仓库：自有**成品仓**（2026-09-16 方案 A：原"售后仓"取消，退回品直接压在成品仓、品质 PENDING 待分类）
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })
// 目标入库仓（A/B/C/不良）：一律自有**成品仓** —— 不良品改用**品质 DEFECT** 区分，不再需要独立"不良仓"
const fetchFinishedWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })

const warehouseOptions = ref<any[]>([])

function resetForm() {
  Object.assign(form, {
    id: undefined, warehouseId: undefined,
    sortDate: localDate(),
    targetWarehouseA: undefined, targetWarehouseB: undefined,
    targetWarehouseC: undefined, targetWarehouseDefect: undefined,
    remark: ''
  })
  items.value = []
}

// 默认目标仓库：A/B/C/不良 都取第一个成品仓（2026-09-16 方案 A：不良品靠品质 DEFECT 区分，无独立不良仓）
function applyTargetDefaults() {
  const fin = warehouseOptions.value.find((w: any) => w.warehouseType === WarehouseType.FINISHED)
  form.targetWarehouseA = fin?.id
  form.targetWarehouseB = fin?.id
  form.targetWarehouseC = fin?.id
  form.targetWarehouseDefect = fin?.id
}

/** 嵌入模式要带出的来源批次（来自「待整理」总览）；为空 = 整仓待整理库存 */
const presetPendingIdSet = computed(() => {
  const ids = props.presetPendingIds || []
  return ids.length > 0 ? new Set(ids.map((v) => Number(v))) : null
})

// 加载成品仓待整理库存（待分类品）
async function loadDefectStock() {
  if (!form.warehouseId) { ElMessage.warning('请先选择源仓库(成品仓)'); return }
  try {
    const all: any[] = await getReturnSortDefectStock(form.warehouseId)
    // 嵌入模式（总览点行「整理」）：只带出该来源批次，避免把整仓都铺进抽屉。
    // 口径与「待整理」总览完全一致（后端同一套 FIFO 分配），这里只做筛选。
    const only = presetPendingIdSet.value
    const rows: any[] = only ? all.filter((r) => only.has(Number(r.pendingId))) : all
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
          productId: r.productId, productName: r.productName, unit: r.unit,
          totalQuantity: qty, available: qty,
          batchQuantity: Number(r.totalQuantity) || 0, sortedQuantity: Number(r.sortedQuantity) || 0,
          stayDays: Number(r.stayDays) || 0,
          qtyA: 0, qtyB: 0, qtyC: 0, qtyDefect: 0
        })
      }
    }
    if (rows.length === 0) {
      if (only) ElMessage.warning('该来源批次已无可整理数量（可能已被整理），请返回「待整理」刷新后重试')
      else ElMessage.info('该成品仓暂无待整理库存（待分类品）')
    }
  } catch { ElMessage.error('加载待整理库存失败') }
}

function removeItem(index: number) { items.value.splice(index, 1) }
function itemSum(it: any) { return Number(it.qtyA || 0) + Number(it.qtyB || 0) + Number(it.qtyC || 0) + Number(it.qtyDefect || 0) }
function isItemValid(it: any) { return it.totalQuantity > 0 && itemSum(it) === Number(it.totalQuantity) }

/** 已加载的数据标识（新增/编辑切换或换单时重新拉取，避免残留上一单数据） */
const loadedKey = ref('')

async function init() {
  // 嵌入模式（抽屉）与路由页面共用一个组件：key 带上预填参数，换批次时重新带出明细
  const key = id.value != null
    ? `edit-${id.value}`
    : (props.embedded ? `embed-${props.presetWarehouseId ?? ''}-${(props.presetPendingIds || []).join(',')}` : 'add')
  if (loadedKey.value === key) return
  loadedKey.value = key

  resetForm()
  code.value = ''
  await loadWarehouses()
  if (props.embedded) {
    // 源仓库与要整理的来源批次由「待整理」总览带过来；A/B/C/不良 目标仓仍取默认成品仓
    form.warehouseId = props.presetWarehouseId ?? undefined
    applyTargetDefaults()
    if (form.warehouseId) await loadDefectStock()
    return
  }
  if (id.value == null) { applyTargetDefaults(); return }

  loading.value = true
  try {
    const io = await getReturnSort(id.value)
    code.value = io.code || ''
    Object.assign(form, {
      id: io.id, warehouseId: io.warehouseId, sortDate: io.sortDate,
      targetWarehouseA: io.targetWarehouseA, targetWarehouseB: io.targetWarehouseB,
      targetWarehouseC: io.targetWarehouseC, targetWarehouseDefect: io.targetWarehouseDefect,
      remark: io.remark
    })
    const its = await getReturnSortItems(id.value)
    items.value = (its || []).map((it: any) => ({
      pendingId: it.pendingId,
      sourceType: it.sourceType, sourceCode: it.sourceCode, sourceDate: it.sourceDate,
      productId: it.productId, productName: it.productName, unit: it.unit,
      sku: it.sku || '',
      totalQuantity: Number(it.totalQuantity), qtyA: Number(it.qtyA), qtyB: Number(it.qtyB),
      qtyC: Number(it.qtyC), qtyDefect: Number(it.qtyDefect)
    }))
    // 2026-09-20（F7-175）：编辑态补齐来源批次信息。
    // available/batchQuantity/sortedQuantity/stayDays 不在 return_sort_item 上（属来源批次 after_sale_pending），
    // 原先编辑态全缺 ⇒ ① handleSave 的「超过可用库存」校验恒不生效（available 为 undefined）
    // ② 模板「批次量/已整理」「停留天数」列在编辑态空白。这里用**与新增态同一个接口**（后端同一套 FIFO 分配）按 pendingId 回填。
    await fillBatchInfo()
  } catch { ElMessage.error('获取详情失败') } finally { loading.value = false }
}

/** 编辑态回填来源批次信息（口径与 loadDefectStock 一致，只补 4 个展示/校验字段；失败不阻塞编辑） */
async function fillBatchInfo() {
  const whId = form.warehouseId
  if (!whId || items.value.length === 0) return
  try {
    const all: any[] = await getReturnSortDefectStock(whId)
    const byPending = new Map<number, any>()
    for (const r of all) if (r.pendingId != null) byPending.set(Number(r.pendingId), r)
    for (const it of items.value) {
      const r = it.pendingId != null ? byPending.get(Number(it.pendingId)) : undefined
      if (!r) continue
      it.available = Number(r.quantity) || 0
      it.batchQuantity = Number(r.totalQuantity) || 0
      it.sortedQuantity = Number(r.sortedQuantity) || 0
      it.stayDays = Number(r.stayDays) || 0
    }
  } catch { /* 回填失败不影响编辑；后端审核时仍会校验库存 */ }
}

async function loadWarehouses() {
  try { const res: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY' } }); warehouseOptions.value = res?.records || [] } catch { warehouseOptions.value = [] }
}

async function handleSave() {
  if (!form.warehouseId) { ElMessage.warning('请选择源仓库(成品仓)'); return }
  if (!form.targetWarehouseA || !form.targetWarehouseB || !form.targetWarehouseC || !form.targetWarehouseDefect) {
    ElMessage.warning('请选择 A规/B规/C规/不良 的目标入库仓库'); return
  }
  if (items.value.length === 0) { ElMessage.warning('请先加载成品仓待整理库存并录入分选数量'); return }
  for (const it of items.value) {
    if (!it.totalQuantity || it.totalQuantity <= 0) { ElMessage.warning(`产品「${it.productName || it.productId}」待整理数量必须大于0`); return }
    if (it.available != null && it.totalQuantity > it.available) {
      ElMessage.warning(`产品「${it.productName || it.productId}」待整理数量(${it.totalQuantity})超过成品仓可用待分类库存(${it.available})`)
      return
    }
    if (itemSum(it) !== Number(it.totalQuantity)) {
      ElMessage.warning(`产品「${it.productName || it.productId}」分选数量之和(${itemSum(it)})必须等于待整理数量(${it.totalQuantity})`)
      return
    }
  }
  saving.value = true
  try {
    const data = { ...form, items: items.value }
    if (form.id) { await updateReturnSort(form.id, data); ElMessage.success('修改成功') }
    else { await createReturnSort(data); ElMessage.success('新增成功') }
    // 嵌入模式（抽屉）：不跳路由，交由父页刷新「待整理」总览与整理单列表
    if (props.embedded) { emit('saved', form.id); return }
    // 2026-09-20（F7-174）：独立页模式置脏标志，列表页 onActivated 时重新拉取（keep-alive 复用下 onMounted 不触发）
    sessionStorage.setItem(INVENTORY_RETURN_SORT_DIRTY_KEY, '1')
    router.push('/inventory/return-sort')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

function handleCancel() { if (props.embedded) { emit('cancel'); return } router.push('/inventory/return-sort') }
function handleBack() { if (props.embedded) { emit('cancel'); return } router.back() }

onMounted(() => { init() })
// keep-alive 缓存下再次进入会复用组件；新增/编辑路由切换也需重新加载
onActivated(() => { init() })
watch(() => route.fullPath, () => { init() })
</script>

<template>
  <div :class="props.embedded ? 'embedded-form' : 'app-container'">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isNew ? (props.embedded ? '整理待分类品（抽屉开单）' : '新增退货整理') : `编辑退货整理 — ${code}` }}</span>
          <div>
            <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
            <el-button @click="handleCancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="源仓库" required>
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择成品仓" style="width:100%" />
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
              <RemoteSelect v-model="form.targetWarehouseDefect" :fetch="fetchFinishedWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">整理明细（待整理数量 = A + B + C + 不良）</el-divider>
      <div style="margin-bottom:8px">
        <el-button type="primary" :icon="'Download'" :disabled="!form.warehouseId" @click="loadDefectStock">加载成品仓待整理库存</el-button>
      </div>
      <el-table :data="items" border>
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
    </el-card>

    <div style="text-align:center;margin-top:20px">
      <el-button @click="handleBack">返回</el-button>
    </div>
  </div>
</template>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
/* 嵌入抽屉（「待整理」总览点行「整理」）时去掉页面级留白 */
.embedded-form { padding: 0 4px 24px; }
</style>
