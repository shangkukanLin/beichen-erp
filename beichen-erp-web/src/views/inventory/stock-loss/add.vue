<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作（保存） -->
  <PageShell :title="isEdit ? '编辑成品报损单' : '新增成品报损单'" back-fallback="/inventory/stock-loss">
    <template #actions>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>

    <el-card shadow="never">
      <el-form :model="form" label-width="var(--app-label-width)" class="head-form">
        <el-form-item label="仓库" required>
          <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses"
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
        <!-- 损失承担方（2026-09-29 用户口径「报损需要走财务流程」）：决定审核后的财务落点 -->
        <el-form-item label="损失承担方">
          <el-select v-model="form.liableParty" style="width:200px">
            <el-option v-for="p in liableParties" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
          <span class="hint">
            {{ form.liableParty === LiableParty.SUPPLIER
              ? '审核后生成对供应商的应收（索赔），反审核冲回'
              : '审核后生成「报损损失」费用单（非资金：不扣账户、不写资金流水）' }}
          </span>
        </el-form-item>
        <el-form-item v-if="form.liableParty === LiableParty.SUPPLIER" label="承担方" required>
          <RemoteSelect v-model="form.liableSupplierId" :fetch="fetchSuppliers"
            :label-key="(row:any)=>row.name" placeholder="选择供应商/加工厂" style="width:220px" domain="supplier" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" style="width:320px" />
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
            <RemoteSelect v-model="row.productId" :fetch="fetchProducts"
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
            <span v-else style="color:#999">—</span>
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
  </PageShell>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import { getQualityTypes, type QualityOption } from '@/api/product'
import { WarehouseCategory, WarehouseType, ProductQualityType, LossReasonLabel, LiableParty, LiablePartyLabel, codeLabelOptions, INVENTORY_STOCK_LOSS_DIRTY_KEY } from '@/api/enums'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()
const editId = computed(() => Number(route.params.id) || 0)
const isEdit = computed(() => editId.value > 0)

const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
    .then((res: any) => {
      res.records = (res.records || []).filter((w: any) => w.warehouseType !== WarehouseType.AUXILIARY)
      return res
    })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })
/** 承担方下拉（承担方=供应商/加工厂承担时用）：复用往来单位主数据 */
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })

const qualityOptions = ref<QualityOption[]>([])
const lossReasons = ref<any[]>([])
const saving = ref(false)
/** 损失承担方选项（2026-09-29 报损走财务流程） */
const liableParties = computed(() => Object.entries(LiablePartyLabel).map(([value, label]) => ({ value, label })))

const form = reactive({
  warehouseId: undefined as number | undefined,
  lossDate: localDate(),
  lossReason: undefined as string | undefined,
  remark: '',
  // 2026-09-29（用户口径「报损需要走财务流程」）：承担方决定审核后的**财务落点** ——
  //   INTERNAL 内部损失 ⇒ 生成「报损损失」费用单（非资金）；SUPPLIER ⇒ 生成对供应商的应收（索赔）
  liableParty: LiableParty.INTERNAL as string,
  liableSupplierId: undefined as number | undefined
})
const items = ref<any[]>([])
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / items **之后**：watch 注册时会立即求值一次快照，放前面会因 TDZ 静默失效。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))

// 数量一律整数（2026-09-16）
function fmtQty(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }
function money(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
const totalAmount = computed(() =>
  items.value.reduce((s, r) => s + (Number(r.amount) || 0), 0))

function newItem() {
  return {
    productId: undefined, sku: '', productName: '', unit: '',
    qualityType: ProductQualityType.A as string, quantity: undefined, unitPrice: undefined,
    amount: 0, remark: '', stockQty: undefined as number | undefined
  }
}
function addItem() { items.value.push(newItem()) }
function removeItem(i: number) { items.value.splice(i, 1) }

/** 选产品后带出档案信息与成本价（单价可改），并查该仓库该品质的可用库存 */
async function onProductPick(p: any, row: any) {
  if (!p) return
  row.productId = p.id
  row.sku = p.sku || ''
  row.productName = p.name || ''
  row.unit = p.unit || ''
  // 单价优先取产品成本价，没有再取最近进价（后端保存时同样逻辑，这里只是提前展示）
  if (row.unitPrice == null) row.unitPrice = Number(p.costPrice) || Number(p.lastInPrice) || 0
  calcAmount(row)
  loadStock(row)
}

function calcAmount(row: any) {
  const q = Number(row.quantity) || 0
  const price = Number(row.unitPrice) || 0
  row.amount = Math.round(q * price * 100) / 100
}

/**
 * 查询可用库存：仓库 + 产品 + 品质三者任一变化都要重查。
 * 报损是出库行为，数量超过可用库存时标红（后端审核还会再校验一次）。
 */
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

async function loadDetail() {
  if (!editId.value) return
  try {
    const io = await request.get<any, any>(`/inventory/stock-loss/${editId.value}`)
    form.warehouseId = io.warehouseId
    form.lossDate = io.lossDate
    form.lossReason = io.lossReason || undefined
    form.remark = io.remark || ''
    // 2026-09-29：承担方回填（历史单无值 ⇒ 内部损失）
    form.liableParty = io.liableParty || LiableParty.INTERNAL
    form.liableSupplierId = io.liableSupplierId || undefined
    const its = await request.get<any, any>(`/inventory/stock-loss/${editId.value}/items`)
    items.value = (its || []).map((i: any) => ({
      productId: i.productId, sku: i.sku || '', productName: i.productName || '',
      unit: i.unit || '', qualityType: i.qualityType || ProductQualityType.A,
      quantity: i.quantity, unitPrice: i.unitPrice, amount: i.amount,
      remark: i.remark || '', stockQty: undefined
    }))
    items.value.forEach(loadStock)
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') }
}

async function handleSubmit() {
  if (!form.warehouseId) { ElMessage.warning('请选择仓库'); return }
  const valid = items.value.filter((i: any) => i.productId && Number(i.quantity) > 0)
  if (valid.length === 0) { ElMessage.warning('请添加报损明细（数量需大于 0）'); return }
  for (const it of valid) {
    if (overStock(it)) { ElMessage.warning('存在明细的报损数量超出可用库存，请调整'); return }
  }
  // 2026-09-29（报损走财务流程）：承担方=供应商/加工厂时必须指定承担方（后端 applyLiableParty 同样校验）
  if (form.liableParty === LiableParty.SUPPLIER && !form.liableSupplierId) {
    ElMessage.warning('损失由供应商/加工厂承担时，请选择承担方'); return
  }
  saving.value = true
  try {
    const payload = {
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
    }
    if (isEdit.value) await request.put(`/inventory/stock-loss/${editId.value}`, payload)
    else await request.post('/inventory/stock-loss', payload)
    ElMessage.success('已保存')
    // 2026-09-20（F7-174）：置脏标志，列表页 onActivated 时才重新拉取（否则 keep-alive 复用会让列表停在旧数据）
    invalidate('stockLoss')
    // 保存成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签回列表
    markClean()
    tabStore.closeTabAndBack(route.path)
    router.push('/inventory/stock-loss')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { saving.value = false }
}

function goBack() {
  // 取消返回：先清脏标记（否则离开会被未保存确认拦住），再关掉本页签
  markClean()
  tabStore.closeTabAndBack(route.path)
  router.push('/inventory/stock-loss')
}

onMounted(async () => {
  try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] }
  lossReasons.value = codeLabelOptions(LossReasonLabel) // 2026-09-14：前端枚举映射（后端接口已只回 code）
  if (isEdit.value) await loadDetail()
  else items.value = [newItem()]
  // 数据加载完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改）
  takeBaseline()
})
</script>

<style scoped>
/* 页头已统一到全局骨架（PageShell + styles/page.css） */
.head-form { display: flex; flex-wrap: wrap; }
.head-form :deep(.el-form-item) { margin-bottom: 8px; }
.warn { color: #f56c6c; font-size: var(--app-font-xs); line-height: 16px; }
.hint { color: var(--app-text-secondary); font-size: var(--app-font-xs); margin-left: 8px; }
.footer { margin-top: 16px; display: flex; align-items: center; justify-content: space-between; }
.total { font-size: var(--app-font-base); }
.total strong { color: #f56c6c; font-size: var(--app-font-num-sm); }
:deep(.el-card__body) { padding: 16px; }
</style>
