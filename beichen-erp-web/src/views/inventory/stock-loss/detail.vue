<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作 -->
  <PageShell title="成品报损单详情" :loading="loading" back-fallback="/inventory/stock-loss">
    <template #actions>
      <template v-if="head">
        <!-- 草稿：保存(主) + 审核 + 作废（2026-09-24 用户口径：草稿态就地编辑，不再跳独立编辑页） -->
        <el-button v-if="isDraft" type="primary" :loading="saving" @click="onSave">保存</el-button>
        <el-button v-perm="'stock:stock-loss'" v-if="isDraft" type="success" @click="onAudit">审核</el-button>
        <el-button v-perm="'stock:stock-loss'" v-if="isAudited" type="warning" @click="onUnAudit">反审核</el-button>
        <el-button v-perm="'stock:stock-loss'" v-if="isDraft" type="danger" @click="onCancel">作废</el-button>
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
            <RemoteSelect v-model="form.warehouseId" add-route="/inventory/warehouse" :fetch="fetchWarehouses"
              :label-key="(row:any)=>row.warehouseName" placeholder="选择报损仓库" style="width:240px"
              @change="onWarehouseChange" domain="warehouse" />
          </el-form-item>
          <el-form-item label="报损日期">
            <el-date-picker v-model="form.lossDate" type="date" value-format="YYYY-MM-DD" style="width:180px" />
          </el-form-item>
          <el-form-item label="报损原因">
            <el-select v-model="form.lossReason" placeholder="请选择" clearable style="width:180px">
              <el-option v-for="r in lossReasons" :key="r.code" :label="r.label" :value="r.code" />
            </el-select>
          </el-form-item>
          <!-- 损失承担方（2026-09-29 用户口径「报损需要走财务流程」）：与 add.vue 同款，决定审核后的财务落点 -->
          <el-form-item label="损失承担方">
            <el-select v-model="form.liableParty" style="width:200px">
              <el-option v-for="p in liableParties" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
            <span class="hint">
              {{ form.liableParty === LiableParty.SUPPLIER
                ? '审核后生成对供应商的应收（索赔）'
                : '审核后生成「报损损失」费用单（非资金）' }}
            </span>
          </el-form-item>
          <el-form-item v-if="form.liableParty === LiableParty.SUPPLIER" label="承担方" required>
            <RemoteSelect v-model="form.liableSupplierId" add-route="/supplier/manage/add" :fetch="fetchSuppliers"
              :label-key="(row:any)=>row.name" placeholder="选择供应商/加工厂" style="width:220px" domain="supplier" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" placeholder="选填" style="width:320px" />
          </el-form-item>
          <!-- 制单人只读（2026-09-23 口径：单据显示制单人；制单人不可改） -->
          <el-form-item label="制单人">
            <span>{{ head?.createByName || '—' }}</span>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="title">报损明细</span>
            <el-button type="primary" :icon="'Plus'" @click="addItem">添加产品</el-button>
          </div>
        </template>
        <el-table :data="items" border stripe>
          <el-table-column label="产品" min-width="200">
            <template #default="{ row }">
              <RemoteSelect v-model="row.productId" add-route="/product/add" :fetch="fetchProducts"
                :label-key="(r:any)=>r.name || r.productName" placeholder="选择产品" style="width:100%"
                @pick="(rows:any[])=>onProductPick(rows[0], row)" domain="product" />
            </template>
          </el-table-column>
          <el-table-column label="SKU" width="130">
            <template #default="{ row }">{{ row.sku || '—' }}</template>
          </el-table-column>
          <el-table-column label="单位" width="70" align="center">
            <template #default="{ row }">{{ row.unit || '—' }}</template>
          </el-table-column>
          <el-table-column label="品质" width="110">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" size="small" style="width:100%" @change="loadStock(row)">
                <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
              </el-select>
            </template>
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
        <el-descriptions :column="3" border>
          <el-descriptions-item label="报损单号">{{ head?.code || '—' }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ head?.warehouseName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="报损日期">{{ head?.lossDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="报损原因">
            {{ LossReasonLabel[head?.lossReason] || head?.lossReason || '—' }}
          </el-descriptions-item>
          <!-- 2026-09-29（报损走财务流程）：承担方 + 财务影响（落到哪张凭证） -->
          <el-descriptions-item label="损失承担方">
            {{ LiablePartyLabel[head?.liableParty || 'INTERNAL'] || head?.liableParty || '—' }}
            <span v-if="head?.liableSupplierName">（{{ head.liableSupplierName }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="财务影响">
            <span v-if="head?.financeInfo">{{ head.financeInfo }}</span>
            <span v-else class="muted">—（未生成凭证）</span>
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
          <el-table-column prop="productName" label="产品名称" min-width="160" />
          <el-table-column prop="sku" label="SKU" width="130" />
          <el-table-column label="单位" width="70" align="center">
            <template #default="{ row }">{{ row.unit || '—' }}</template>
          </el-table-column>
          <el-table-column label="品质" width="90" align="center">
            <template #default="{ row }">{{ ProductQualityTypeLabel[row.qualityType] || row.qualityType || '—' }}</template>
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

  </PageShell>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { getQualityTypes, type QualityOption } from '@/api/product'
import {
  DocStatus, DocStatusLabel, DocStatusTag, LossReasonLabel, LiableParty, LiablePartyLabel, ProductQualityTypeLabel,
  WarehouseCategory, WarehouseType, ProductQualityType, codeLabelOptions, INVENTORY_STOCK_LOSS_DIRTY_KEY
} from '@/api/enums'
import { invalidate } from '@/utils/dataFreshness'

/**
 * 成品报损单详情（2026-09-24 用户口径：草稿态就地可编辑）
 *
 * 结构对齐采购单/销售单详情：`head` = 只读快照，`form`/`items` = 可编辑副本（草稿态才拉）。
 * 草稿分支的字段、校验、库存联动与 add.vue 完全一致（同一套 payload：{ loss, items }）；
 * 已审核/已作废分支保留原只读 el-descriptions + 明细表。
 * 未加"未保存拦截"：详情页由列表点进来，改错了可直接保存或离开；拦截留给独立新增页（add.vue）。
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
  remark: '',
  // 2026-09-29（用户口径「报损需要走财务流程」）：承担方决定审核后的财务落点（内部⇒损失费用单；供应商⇒索赔应收）
  liableParty: LiableParty.INTERNAL as string,
  liableSupplierId: undefined as number | undefined
})
/** 损失承担方选项 */
const liableParties = computed(() => Object.entries(LiablePartyLabel).map(([value, label]) => ({ value, label })))
const items = ref<any[]>([])

const isDraft = computed(() => head.value?.status === DocStatus.DRAFT)
const isAudited = computed(() => head.value?.status === DocStatus.AUDITED)

const qualityOptions = ref<QualityOption[]>([])
const lossReasons = ref<any[]>([])

const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
    .then((res: any) => {
      res.records = (res.records || []).filter((w: any) => w.warehouseType !== WarehouseType.AUXILIARY)
      return res
    })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })
/** 承担方下拉（承担方=供应商/加工厂承担时用） */
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })

// 数量一律整数（2026-09-16）
function fmtQty(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }
function money(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
const totalAmount = computed(() => items.value.reduce((s, r) => s + (Number(r.amount) || 0), 0))

function newItem() {
  return {
    productId: undefined, sku: '', productName: '', unit: '',
    qualityType: ProductQualityType.A as string, quantity: undefined, unitPrice: undefined,
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
/** 选产品后带出档案信息与成本价（单价可改），并查该仓库该品质的可用库存 */
async function onProductPick(p: any, row: any) {
  if (!p) return
  row.productId = p.id
  row.sku = p.sku || ''
  row.productName = p.name || ''
  row.unit = p.unit || ''
  if (row.unitPrice == null) row.unitPrice = Number(p.costPrice) || Number(p.lastInPrice) || 0
  calcAmount(row)
  loadStock(row)
}
/** 可用库存：仓库 + 产品 + 品质三者任一变化都要重查（报损是出库行为，超库存标红；后端审核还会再校验） */
async function loadStock(row: any) {
  row.stockQty = undefined
  if (!form.warehouseId || !row.productId) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.warehouseId, productId: row.productId, stockType: 'PRODUCT', pageSize: 100 }
    })
    const hit = (r?.records || []).find((x: any) => String(x.qualityType) === String(row.qualityType))
    row.stockQty = hit ? Number(hit.quantity) : 0
  } catch { row.stockQty = undefined }
}
function onWarehouseChange() { items.value.forEach(loadStock) }
function overStock(row: any) {
  return row.stockQty != null && Number(row.quantity) > 0 && Number(row.quantity) > row.stockQty
}

/** 合计行（只读分支）：数量/金额求和（列序固定：0产品 1SKU 2单位 3品质 4数量 5单价 6金额 7备注） */
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
  try {
    head.value = await request.get<any, any>(`/inventory/stock-loss/${id}`)
    const its = await request.get<any, any>(`/inventory/stock-loss/${id}/items`) || []
    readonlyItems.value = its
    // 草稿态才有编辑副本；白名单回填（提交体只带 loss + items，见 onSave）
    if (head.value?.status === DocStatus.DRAFT) {
      form.warehouseId = head.value.warehouseId
      form.lossDate = head.value.lossDate || ''
      form.lossReason = head.value.lossReason || undefined
      form.remark = head.value.remark || ''
      // 2026-09-29：承担方回填（历史单无值 ⇒ 内部损失）
      form.liableParty = head.value.liableParty || LiableParty.INTERNAL
      form.liableSupplierId = head.value.liableSupplierId || undefined
      items.value = its.map((i: any) => ({
        productId: i.productId, sku: i.sku || '', productName: i.productName || '',
        unit: i.unit || '', qualityType: i.qualityType || ProductQualityType.A,
        quantity: i.quantity, unitPrice: i.unitPrice, amount: i.amount,
        remark: i.remark || '', stockQty: undefined
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
  const valid = items.value.filter((i: any) => i.productId && Number(i.quantity) > 0)
  if (valid.length === 0) { ElMessage.warning('请添加报损明细（数量需大于 0）'); return }
  for (const it of valid) {
    if (overStock(it)) { ElMessage.warning('存在明细的报损数量超出可用库存，请调整'); return }
  }
  // 2026-09-29（报损走财务流程）：承担方=供应商/加工厂时必须指定承担方（后端同样校验）
  if (form.liableParty === LiableParty.SUPPLIER && !form.liableSupplierId) {
    ElMessage.warning('损失由供应商/加工厂承担时，请选择承担方'); return
  }
  saving.value = true
  try {
    await request.put(`/inventory/stock-loss/${id}`, {
      loss: {
        warehouseId: form.warehouseId,
        lossDate: form.lossDate,
        lossReason: form.lossReason,
        remark: form.remark,
        liableParty: form.liableParty,
        liableSupplierId: form.liableParty === LiableParty.SUPPLIER ? form.liableSupplierId : null
      },
      items: valid.map((i: any) => ({
        productId: i.productId,
        qualityType: i.qualityType,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
        remark: i.remark
      }))
    })
    invalidate('stockLoss')
    ElMessage.success('已保存')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { saving.value = false }
}

async function onAudit() {
  try {
    await ElMessageBox.confirm('确认审核？审核后将扣减对应仓库的库存。', '审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/stock-loss/${id}/audit`)
    ElMessage.success('已审核，库存已扣减')
    invalidate('stockLoss')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function onUnAudit() {
  try {
    await ElMessageBox.confirm('确认反审核？反审核后库存将加回。', '反审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/stock-loss/${id}/un-audit`)
    ElMessage.success('已反审核，库存已加回')
    invalidate('stockLoss')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确认作废该报损单？', '作废确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/stock-loss/${id}/cancel`)
    ElMessage.success('已作废')
    invalidate('stockLoss')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(async () => {
  try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] }
  lossReasons.value = codeLabelOptions(LossReasonLabel) // 2026-09-14：前端枚举映射（后端接口已只回 code）
  await load()
})
</script>

<style scoped>
/* 页头/操作区已统一到全局骨架（PageShell + styles/page.css） */
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
