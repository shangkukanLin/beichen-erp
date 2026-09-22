<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { WarehouseType, INVENTORY_RETURN_SORT_DIRTY_KEY } from '@/api/enums'
import {
  getReturnSort, getReturnSortItems, getReturnSortDefectStock,
  createReturnSort, updateReturnSort
} from '@/api/inventory'

const route = useRoute(); const router = useRouter()

/**
 * 新增（/inventory/return-sort/add）与编辑（/edit/:id）共用本页。
 *
 * <p>2026-09-22 用户要求：列表页的「整理待整理品」**不再用抽屉**，改为直接跳本页，预设参数走 **query**：
 * {@code warehouseId} = 源仓库（由来源批次决定）、{@code pendingIds} = 要整理的来源批次（逗号分隔，缺省 = 整仓）。</p>
 *
 * <p>保存后回列表：列表页在 DIRTY 标志下会同时重拉「待整理总览 + 整理单列表」（见 index.vue 的 onActivated）。</p>
 */

/** 新增（/inventory/return-sort/add）与编辑（/inventory/return-sort/edit/:id）共用本页 */
const id = computed(() => (route.params.id != null && route.params.id !== '' ? Number(route.params.id) : undefined))
const isNew = computed(() => id.value == null)

/** query 带来的源仓库（列表页「整理本仓 / 整理」跳过来时才有） */
const presetWarehouseId = computed<number | undefined>(() => {
  const raw = String(route.query.warehouseId ?? '').trim()
  if (!raw) return undefined
  const n = Number(raw)
  return Number.isNaN(n) ? undefined : n
})

const loading = ref(false)
const saving = ref(false)
/** 编辑模式的单号（标题展示用；新增时为空） */
const code = ref('')

const form = reactive({
  id: undefined as any,
  warehouseId: undefined as any,
  sortDate: localDate(),
  remark: ''
})
const items = ref<any[]>([])

/** 待整理库存超期预警阈值（天）：停留超过该天数则标红提示 */
const STAY_ALERT_DAYS = 3

// 源仓库：自有**成品仓**（2026-09-16 方案 A：原"售后仓"取消，退回品直接压在成品仓、品质 PENDING 待整理）
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })
// 2026-09-22 用户口径：分选后**默认回到源仓库** ⇒ 不再收集 A/B/C/不良 4 个目标入库仓
// （服务端 create/update 时把空的目标仓回填成源仓库；不良品仍靠品质 DEFECT 区分，不需要独立不良仓）

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

/** query 带来的要整理的来源批次（列表页点某一行「整理」时只有一个）；为空 = 整仓待整理库存 */
const presetPendingIds = computed<number[] | null>(() => {
  const raw = String(route.query.pendingIds ?? '').trim()
  if (!raw) return null
  const ids = raw.split(',').map((v) => Number(v)).filter((v) => !Number.isNaN(v))
  return ids.length > 0 ? ids : null
})
const presetPendingIdSet = computed(() => {
  const ids = presetPendingIds.value || []
  return ids.length > 0 ? new Set(ids.map((v) => Number(v))) : null
})

// 加载成品仓待整理库存（待整理品）
async function loadDefectStock() {
  if (!form.warehouseId) { ElMessage.warning('请先选择源仓库(成品仓)'); return }
  try {
    const all: any[] = await getReturnSortDefectStock(form.warehouseId)
    // 从列表页点某一行「整理」过来（query.pendingIds）：只带出该来源批次，避免把整仓都铺进页面。
    // 口径与「待整理」总览完全一致（后端同一套 FIFO 分配），这里只做筛选。
    const only = presetPendingIdSet.value
    const rows: any[] = only ? all.filter((r) => only.has(Number(r.pendingId))) : all
    for (const r of rows) {
      // 按待整理批次逐行展开：同一产品可来自多张退货单/换货单，各自独立成行以便追溯
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
      else ElMessage.info('该成品仓暂无待整理库存（待整理品）')
    }
  } catch { ElMessage.error('加载待整理库存失败') }
}

function removeItem(index: number) { items.value.splice(index, 1) }
function itemSum(it: any) { return Number(it.qtyA || 0) + Number(it.qtyB || 0) + Number(it.qtyC || 0) + Number(it.qtyDefect || 0) }
function isItemValid(it: any) { return it.totalQuantity > 0 && itemSum(it) === Number(it.totalQuantity) }

/** 已加载的数据标识（新增/编辑切换或换单时重新拉取，避免残留上一单数据） */
const loadedKey = ref('')

async function init() {
  // key 带上 query 预设参数：从列表页换一个批次跳过来时也要重新带出明细
  const key = id.value != null
    ? `edit-${id.value}`
    : `add-${presetWarehouseId.value ?? ''}-${(presetPendingIds.value || []).join(',')}`
  if (loadedKey.value === key) return
  loadedKey.value = key

  resetForm()
  code.value = ''
  await loadWarehouses()
  if (id.value == null) {
    // 新增：源仓库与要整理的来源批次由列表页用 query 带过来；分选后按品质回到该源仓库（不再选目标仓）
    if (presetWarehouseId.value) {
      form.warehouseId = presetWarehouseId.value
      await loadDefectStock()
    }
    return
  }

  loading.value = true
  try {
    const io = await getReturnSort(id.value)
    code.value = io.code || ''
    Object.assign(form, {
      id: io.id, warehouseId: io.warehouseId, sortDate: io.sortDate,
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
  if (items.value.length === 0) { ElMessage.warning('请先加载成品仓待整理库存并录入分选数量'); return }
  for (const it of items.value) {
    if (!it.totalQuantity || it.totalQuantity <= 0) { ElMessage.warning(`产品「${it.productName || it.productId}」待整理数量必须大于0`); return }
    if (it.available != null && it.totalQuantity > it.available) {
      ElMessage.warning(`产品「${it.productName || it.productId}」待整理数量(${it.totalQuantity})超过成品仓可用待整理库存(${it.available})`)
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
    // 置脏标志：列表页 onActivated 会重拉「待整理总览 + 整理单列表」（keep-alive 复用下 onMounted 不触发）
    sessionStorage.setItem(INVENTORY_RETURN_SORT_DIRTY_KEY, '1')
    router.push('/inventory/return-sort')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

function handleCancel() { router.push('/inventory/return-sort') }
function handleBack() { router.back() }

onMounted(() => { init() })
// keep-alive 缓存下再次进入会复用组件；新增/编辑路由切换也需重新加载
onActivated(() => { init() })
watch(() => route.fullPath, () => { init() })
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isNew ? (presetWarehouseId ? '整理待整理品' : '新增退货整理') : `编辑退货整理 — ${code}` }}</span>
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
          <!-- 2026-09-22 用户口径：分选后**默认回到源仓库** ⇒ 不再让用户逐个选 A/B/C/不良 入库仓 -->
          <el-col :span="24">
            <el-form-item label="入库仓库">
              <span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">
                分选后按品质回到<b>源仓库</b>（A/B/C/不良 均入源仓，以品质区分），无需逐个选择
              </span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">整理明细（待整理数量 = A + B + C + 不良）</el-divider>
      <div style="margin-bottom:8px">
        <el-button type="primary" :icon="'Download'" :disabled="!form.warehouseId" @click="loadDefectStock">加载成品仓待整理库存</el-button>
      </div>
      <el-table :data="items" border>
        <!--
          列宽预算（2026-09-22 用户要求：明细列表一行显示完、且**表头不能被截断**）：
          Σ(min-width + width) = 150+80+98+118+84+64×3+84+80+56 = 942px ≤ 本页容器 963px ⇒ 不横向滚动；
          「来源单据」2026-09-22 起**只显示单据号**（去掉退货/换货类型标签）⇒ 150px 下单号完整可见；
          「产品」是 min-width 列 ⇒ 富余宽度全给它（实测约 101px），品名不至于被截。
          每条列宽都按**实测表头需要宽**定（探针：th .cell 的 scrollWidth 必须 ≤ clientWidth），
          曾被裁的表头与修前值：待整理数量 76→98 · 批次量/已整理 80→118 · 停留天数 76→84 ·
          A/C数量 60→64 · 不良数量 60→84 · 操作 48→56。
          历史：原先 Σ=1620px（溢 657px）；同日删掉冗余的「来源日期」列、把 A/B/C/不良 改成
          size=small + :controls=false，后又按用户要求去掉 SKU 与单位两列。
          **改这个表的列宽前请先加总，并确认表头没被裁**（跑 verify-returnsort-list-fit.ps1）。
        -->
        <!-- 来源单据（2026-09-22 用户要求）：**只显示单据号**，不再带「退货/换货」类型标签
             （类型标签占 ~40px，去掉后 150px 宽能完整显示单号，不再被截断；详情页也是只显示单号） -->
        <el-table-column label="来源单据" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="产品" min-width="80" show-overflow-tooltip />
        <el-table-column prop="totalQuantity" label="待整理数量" width="98" align="center">
          <template #default="{ row }"><b>{{ row.totalQuantity }}</b></template>
        </el-table-column>
        <el-table-column label="批次量/已整理" width="118" align="center">
          <template #default="{ row }">
            <span v-if="row.pendingId">{{ row.batchQuantity ?? 0 }} / {{ row.sortedQuantity ?? 0 }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="停留天数" width="84" align="center">
          <template #default="{ row }">
            <span :style="row.stayDays > STAY_ALERT_DAYS ? 'color:#f56c6c;font-weight:bold' : ''">
              {{ row.stayDays ?? '-' }}
              <span v-if="row.stayDays > STAY_ALERT_DAYS" :title="`已超过 ${STAY_ALERT_DAYS} 天未整理`">超期</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="A数量" width="64">
          <template #default="{ row }"><el-input-number v-model="row.qtyA" :min="0" :precision="0" size="small" :controls="false" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="B数量" width="64">
          <template #default="{ row }"><el-input-number v-model="row.qtyB" :min="0" :precision="0" size="small" :controls="false" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="C数量" width="64">
          <template #default="{ row }"><el-input-number v-model="row.qtyC" :min="0" :precision="0" size="small" :controls="false" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="不良数量" width="84">
          <template #default="{ row }"><el-input-number v-model="row.qtyDefect" :min="0" :precision="0" size="small" :controls="false" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="校验" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="isItemValid(row) ? 'success' : 'danger'" size="small">{{ isItemValid(row) ? '✓ 相等' : `合计${itemSum(row)}` }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="56" align="center">
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

</style>
