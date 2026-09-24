<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/outsource/stock-loss">
    <template #actions>
      <template v-if="head">
        <!-- 草稿：保存(主) + 审核 + 作废（2026-09-24 用户口径：草稿态就地编辑，不再跳独立编辑页） -->
        <el-button v-if="isDraft" type="primary" :loading="saving" @click="onSave">保存</el-button>
        <el-button v-if="isDraft" type="success" @click="onAudit">审核</el-button>
        <el-button v-if="isAudited" type="warning" @click="onUnAudit">反审核</el-button>
        <el-button v-if="isDraft" type="danger" @click="onCancel">作废</el-button>
      </template>
    </template>

    <!-- ============ 草稿：可编辑（与 add.vue 同款字段/校验；明细可增删、超库存标红） ============ -->
    <template v-if="isDraft">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="title">
              {{ head?.code || '—' }}
              <el-tag :type="DocStatusTag[head?.status] || 'info'" size="small" style="margin-left:8px">
                {{ DocStatusLabel[head?.status] || head?.status }}
              </el-tag>
              <span class="hint">（草稿态，可直接修改下方内容并保存）</span>
            </span>
          </div>
        </template>
        <el-form :model="form" label-width="var(--app-label-width)" class="head-form">
          <el-form-item label="仓库" required>
            <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses"
              :label-key="(row:any)=>row.warehouseName" placeholder="选择报损仓库" style="width:240px"
              @change="onWarehouseChange" />
          </el-form-item>
          <el-form-item label="报损日期">
            <el-date-picker v-model="form.lossDate" type="date" value-format="YYYY-MM-DD" style="width:180px" />
          </el-form-item>
          <el-form-item label="报损原因">
            <el-select v-model="form.lossReason" placeholder="请选择" clearable style="width:180px">
              <el-option v-for="r in lossReasons" :key="r.code" :label="r.label" :value="r.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" placeholder="选填" style="width:320px" />
          </el-form-item>
          <el-form-item label="制单人">
            <span>{{ head?.createByName || '—' }}</span>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="title">报损明细</span>
            <el-button type="primary" :icon="'Plus'" @click="addItem">添加物料</el-button>
          </div>
        </template>
        <el-table :data="items" border stripe>
          <el-table-column label="物料" min-width="200">
            <template #default="{ row }">
              <RemoteSelect v-model="row.materialId" :fetch="fetchMaterials"
                :label-key="(r:any)=>r.materialName" placeholder="选择物料" style="width:100%"
                @pick="(rows:any[])=>onMaterialPick(rows[0], row)" />
            </template>
          </el-table-column>
          <el-table-column label="物料类型" width="120">
            <template #default="{ row }">{{ row.materialTypeName || '—' }}</template>
          </el-table-column>
          <el-table-column label="规格" width="130">
            <template #default="{ row }">{{ row.spec || '—' }}</template>
          </el-table-column>
          <el-table-column label="单位" width="70" align="center">
            <template #default="{ row }">{{ row.unit || '—' }}</template>
          </el-table-column>
          <el-table-column label="可用库存" width="100" align="right">
            <template #default="{ row }">
              <span v-if="row.stockQty != null">{{ fmtQty(row.stockQty) }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="报损数量" width="120" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" :controls="false"
                size="small" style="width:100%" @change="() => calcAmount(row)" />
              <div v-if="overStock(row)" class="warn">超出可用库存</div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="110" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="4" :controls="false"
                size="small" style="width:100%" @change="() => calcAmount(row)" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="备注" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.remark" size="small" placeholder="选填" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="footer">
          <div class="total">合计报损金额：<strong>{{ money(totalAmount) }}</strong></div>
        </div>
      </el-card>
    </template>

    <!-- ============ 已审核 / 已作废：只读（原口径原样保留） ============ -->
    <template v-else>
      <el-card shadow="never">
        <el-descriptions v-loading="loading" :column="3" border>
          <el-descriptions-item label="报损单号">{{ head?.code || '—' }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ head?.warehouseName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="报损日期">{{ head?.lossDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="报损原因">
            {{ LossReasonLabel[head?.lossReason] || head?.lossReason || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="DocStatusTag[head?.status] || 'info'" size="small">
              {{ DocStatusLabel[head?.status] || head?.status || '—' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="报损金额">
            <strong>{{ money(head?.totalAmount) }}</strong>
          </el-descriptions-item>
          <!-- 制单人（2026-09-23 用户口径：单据详情显示「制单人 + 审核人」） -->
          <el-descriptions-item label="制单人">{{ head?.createByName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核人">{{ head?.auditorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ head?.auditTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="4">{{ head?.remark || '—' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="title">报损明细</span>
          </div>
        </template>
        <el-table v-loading="tableLoading" :data="readonlyItems" border stripe show-summary :summary-method="summaries">
          <el-table-column prop="materialName" label="物料名称" min-width="160" />
          <el-table-column prop="materialTypeName" label="物料类型" width="120">
            <template #default="{ row }">{{ row.materialTypeName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="spec" label="规格" width="130" />
          <el-table-column label="单位" width="70" align="center">
            <template #default="{ row }">{{ row.unit || '—' }}</template>
          </el-table-column>
          <el-table-column label="报损数量" width="110" align="right">
            <template #default="{ row }">{{ fmtQty(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="单价" width="110" align="right">
            <template #default="{ row }">{{ money(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="140">
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 操作按钮（保存/审核/反审核/作废）已统一上移到页头右侧（PageShell #actions） -->
  </PageShell>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, LossReasonLabel, codeLabelOptions, OUTSOURCE_STOCK_LOSS_DIRTY_KEY } from '@/api/enums'

/**
 * 物料报损单详情（2026-09-24 用户口径：草稿态就地可编辑）
 *
 * 与成品报损同款结构：`head` 只读快照 + `form`/`items` 可编辑副本（草稿态才建）。
 * 物料与成品的差异（与 add.vue 保持一致）：仓库取「委外仓 + 自有辅料仓」；明细是物料（materialId +
 * materialTypeId）；物料库存不分品质，按「仓库+物料」汇总可用量；payload items 不带品质。
 * 未加"未保存拦截"：详情页由列表点进来，拦截留给独立新增页（add.vue）。
 */
const route = useRoute()
const id = Number(route.params.id)

const loading = ref(false)
const tableLoading = ref(false)
const saving = ref(false)
/** 只读快照 */
const head = ref<any>(null)
/** 只读明细（已审核/已作废分支用） */
const readonlyItems = ref<any[]>([])
/** 可编辑副本（草稿分支用） */
const form = reactive({
  warehouseId: undefined as number | undefined,
  lossDate: '',
  lossReason: undefined as string | undefined,
  remark: ''
})
const items = ref<any[]>([])

const isDraft = computed(() => head.value?.status === DocStatus.DRAFT)
const isAudited = computed(() => head.value?.status === DocStatus.AUDITED)

const lossReasons = ref<any[]>([])
const materialTypes = ref<any[]>([])

/** 物料报损的仓库：委外仓与自有辅料仓（与 add.vue 一致） */
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })
    .then((res: any) => {
      res.records = (res.records || []).filter((w: any) =>
        w.warehouseCategory === 'OUTSOURCE' || w.warehouseType === 'AUXILIARY')
      return res
    })
const fetchMaterials = (kw: string) =>
  request.get('/outsource/material/page', { params: { pageSize: 100, materialName: kw } })

// 数量一律整数（2026-09-16）
function fmtQty(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }
function money(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
const totalAmount = computed(() => items.value.reduce((s, r) => s + (Number(r.amount) || 0), 0))

function newItem() {
  return {
    materialId: undefined, materialName: '', materialTypeId: undefined, materialTypeName: '',
    spec: '', unit: '', quantity: undefined, unitPrice: undefined,
    amount: 0, remark: '', stockQty: undefined as number | undefined
  }
}
function addItem() { items.value.push(newItem()) }
function removeItem(i: number) { items.value.splice(i, 1) }
function calcAmount(row: any) {
  const q = Number(row.quantity) || 0
  const price = Number(row.unitPrice) || 0
  row.amount = Math.round(q * price * 100) / 100
}
/** 选物料后带出档案信息与单价（最近进价优先，可改），并查该仓库可用库存 */
async function onMaterialPick(m: any, row: any) {
  if (!m) return
  row.materialId = m.id
  row.materialName = m.materialName || ''
  row.spec = m.spec || ''
  row.unit = m.unit || ''
  row.materialTypeId = m.materialTypeId
  row.materialTypeName = materialTypes.value.find((b: any) => b.id === m.materialTypeId)?.typeName || ''
  if (row.unitPrice == null) row.unitPrice = Number(m.lastInPrice) || Number(m.price) || 0
  calcAmount(row)
  loadStock(row)
}
/** 物料库存不区分品质，按 仓库+物料 汇总可用量 */
async function loadStock(row: any) {
  row.stockQty = undefined
  if (!form.warehouseId || !row.materialId) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.warehouseId, materialId: row.materialId, stockType: 'MATERIAL', pageSize: 100 }
    })
    const recs = r?.records || []
    row.stockQty = recs.length ? recs.reduce((s: number, x: any) => s + (Number(x.quantity) || 0), 0) : 0
  } catch { row.stockQty = undefined }
}
function onWarehouseChange() { items.value.forEach(loadStock) }
function overStock(row: any) {
  return row.stockQty != null && Number(row.quantity) > 0 && Number(row.quantity) > row.stockQty
}

/** 合计行（只读分支）：列序固定 0物料 1物料类型 2规格 3单位 4数量 5单价 6金额 7备注 */
function summaries({ columns }: any) {
  const sumQty = readonlyItems.value.reduce((s, r) => s + (Number(r.quantity) || 0), 0)
  const sumAmt = readonlyItems.value.reduce((s, r) => s + (Number(r.amount) || 0), 0)
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 4) return fmtQty(sumQty)
    if (i === 6) return money(sumAmt)
    return ''
  })
}

async function load() {
  loading.value = true
  tableLoading.value = true
  try {
    head.value = await request.get<any, any>(`/outsource/stock-loss/${id}`)
    const its = await request.get<any, any>(`/outsource/stock-loss/${id}/items`) || []
    readonlyItems.value = its
    if (head.value?.status === DocStatus.DRAFT) {
      form.warehouseId = head.value.warehouseId
      form.lossDate = head.value.lossDate || ''
      form.lossReason = head.value.lossReason || undefined
      form.remark = head.value.remark || ''
      items.value = its.map((i: any) => ({
        materialId: i.materialId, materialName: i.materialName || '',
        materialTypeId: i.materialTypeId, materialTypeName: i.materialTypeName || '',
        spec: i.spec || '', unit: i.unit || '', quantity: i.quantity,
        unitPrice: i.unitPrice, amount: i.amount, remark: i.remark || '', stockQty: undefined
      }))
      items.value.forEach(loadStock)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
    tableLoading.value = false
  }
}

/** 保存（与 add.vue 同一套校验与 payload；后端 update 自带「只有草稿可编辑」守卫） */
async function onSave() {
  if (!form.warehouseId) { ElMessage.warning('请选择仓库'); return }
  const valid = items.value.filter((i: any) => i.materialId && Number(i.quantity) > 0)
  if (valid.length === 0) { ElMessage.warning('请添加报损明细（数量需大于 0）'); return }
  for (const it of valid) {
    if (overStock(it)) { ElMessage.warning('存在明细的报损数量超出可用库存，请调整'); return }
  }
  saving.value = true
  try {
    await request.put(`/outsource/stock-loss/${id}`, {
      loss: {
        warehouseId: form.warehouseId,
        lossDate: form.lossDate,
        lossReason: form.lossReason,
        remark: form.remark
      },
      items: valid.map((i: any) => ({
        materialId: i.materialId,
        materialTypeId: i.materialTypeId,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
        remark: i.remark
      }))
    })
    sessionStorage.setItem(OUTSOURCE_STOCK_LOSS_DIRTY_KEY, '1')
    ElMessage.success('已保存')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { saving.value = false }
}

async function onAudit() {
  try {
    await ElMessageBox.confirm('确认审核？审核后将扣减对应仓库的物料库存。', '审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/stock-loss/${id}/audit`)
    ElMessage.success('已审核，库存已扣减')
    sessionStorage.setItem(OUTSOURCE_STOCK_LOSS_DIRTY_KEY, '1')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function onUnAudit() {
  try {
    await ElMessageBox.confirm('确认反审核？反审核后库存将加回。', '反审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/stock-loss/${id}/un-audit`)
    ElMessage.success('已反审核，库存已加回')
    sessionStorage.setItem(OUTSOURCE_STOCK_LOSS_DIRTY_KEY, '1')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确认作废该报损单？', '作废确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/stock-loss/${id}/cancel`)
    ElMessage.success('已作废')
    sessionStorage.setItem(OUTSOURCE_STOCK_LOSS_DIRTY_KEY, '1')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(async () => {
  lossReasons.value = codeLabelOptions(LossReasonLabel) // 2026-09-14：前端枚举映射（后端接口已只回 code）
  try {
    materialTypes.value = await request.get<any, any>('/dev/material-type/page', { params: { pageSize: 200 } }).then((r: any) => r?.records || [])
  } catch { materialTypes.value = [] }
  await load()
})
</script>

<style scoped>
/* 页头/根容器/操作条已统一到全局骨架（PageShell + styles/page.css） */
.title { font-weight: 600; }
.head-form { display: flex; flex-wrap: wrap; }
.head-form :deep(.el-form-item) { margin-bottom: 8px; }
.hint { color: var(--app-text-secondary); font-size: var(--app-font-xs); }
.muted { color: var(--app-text-placeholder); }
.warn { color: #f56c6c; font-size: var(--app-font-xs); line-height: 16px; }
.footer { margin-top: 16px; display: flex; align-items: center; justify-content: space-between; }
.total { font-size: var(--app-font-base); }
.total strong { color: #f56c6c; font-size: var(--app-font-num-sm); }
:deep(.el-card__body) { padding: 16px; }
</style>
