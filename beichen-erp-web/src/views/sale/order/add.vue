<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { getAccountPage } from '@/api/finance'
import {
  AccountType, AccountTypeLabel, SettleType, SettleTypeLabel,
  ProductQualityTypeLabel, isSellableQuality,
} from '@/api/enums'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import SectionCard from '@/components/SectionCard.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import {
  getSaleOrder, getSaleOrderItems, createSaleOrder, updateSaleOrder, checkSaleOrderStock, SALE_ORDER_DIRTY_KEY,
  type SaleOrder, type SaleOrderItem
} from '@/api/sale'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()

/** 地址栏带 id 时为编辑态，否则为新增态 */
const editId = computed(() => {
  const id = route.query.id
  return id === undefined || id === '' ? null : Number(id)
})
const isEdit = computed(() => editId.value !== null)

const qualityOptions = ref<QualityOption[]>([])
const pageLoading = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<SaleOrder>({
  id: undefined, customerId: undefined, warehouseId: undefined,
  orderDate: localDate(),
  taxIncluded: 0, taxRate: 0, remark: '',
  // 结算方式（2026-09-18 按单记）：默认账期；选现金时必须指定收款账户
  settleType: SettleType.CREDIT, settleAccountId: undefined
})
const items = ref<SaleOrderItem[]>([])

/**
 * 统一「返回」的未保存拦截（2026-09-23 次级页面统一模板）：
 * - 基线在数据加载完成后建立（见 onMounted 末尾）⇒ 只有"用户改过"才算脏；
 * - 保存成功后 markClean() ⇒ 提交后跳列表不会被拦；
 * - 覆盖页内返回 / 页签 × / 侧栏切换 / 浏览器后退+刷新（useUnsavedGuard）。
 * ⚠️ 必须放在 form / items **之后**：watch 注册时会立即求值一次快照，放到前面会因 TDZ 静默失效。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))

// 库存不足确认弹窗
const stockCheckResult = ref<{ productName: string; unit: string; required: number; available: number; shortage: number; sufficient: boolean }[]>([])
const stockCheckVisible = ref(false)

const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择出库仓库', trigger: 'change' }]
}

// Odoo 风格：下拉框展开/搜索时实时查库（不预缓存全量）
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseType: 'FINISHED' } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })

// 本地轻量列表：用于选择产品后回填规格/单位（组件内维护，不依赖全局 store）
const products = ref<any[]>([])
async function loadProducts() { try { const res: any = await fetchProducts(''); products.value = res?.records || [] } catch { products.value = [] } }

function resetForm() {
  Object.assign(form, {
    id: undefined, customerId: undefined, warehouseId: undefined,
    orderDate: localDate(), taxIncluded: 0, taxRate: 0, remark: '',
    settleType: SettleType.CREDIT, settleAccountId: undefined
  })
  items.value = []
}

// ==================== 结算方式（2026-09-18 按单记：同一客户有时现金、有时账期） ====================
/** 现金结算：审核销售单后系统会自动生成一张**草稿**收款单（本页只负责选定收款账户） */
const isCash = computed(() => form.settleType === SettleType.CASH)
const accounts = ref<any[]>([])
/** 可选账户：只列启用的 */
const accountOptions = computed(() => accounts.value.filter((a: any) => a.status === undefined || a.status === 1))
async function loadAccounts() {
  try { const res: any = await getAccountPage({ pageSize: 200 }); accounts.value = res?.records || [] } catch { accounts.value = [] }
  // 账户列表是异步拉取的：若用户先切到「现金」、后列表才回来，这里补一次默认带出
  if (isCash.value && !form.settleAccountId) onSettleTypeChange()
}
/** 切到「现金」时若未选账户，默认带出现金账户（account_type=cash） */
function onSettleTypeChange() {
  if (!isCash.value) { form.settleAccountId = undefined; return }
  if (form.settleAccountId) return
  if (accountOptions.value.length === 0) return // 账户还没加载完，loadAccounts 回调里会再试
  const cash = accountOptions.value.find((a: any) => String(a.accountType || '').toLowerCase() === AccountType.CASH)
  if (cash) form.settleAccountId = cash.id
}
/** 结算方式开关（2026-09-18 用户要求：新增页用开关表示，开=现金，关=账期） */
function onSettleSwitch(v: any) {
  form.settleType = v ? SettleType.CASH : SettleType.CREDIT
  onSettleTypeChange()
}
function addItem() { items.value.push({ productId: undefined, qualityType: 'A', productName: '', unit: '', quantity: 0, unitPrice: 0, amount: 0, remark: '' }) }
function removeItem(index: number) { items.value.splice(index, 1) }
function onProductChange(val: number, row: SaleOrderItem) {
  const p = products.value.find(x => x.id === val)
  if (p) { row.productId = p.id as number; row.productName = p.name; row.sku = p.sku || ''; row.unit = p.unit }
  // 切换产品后自动刷新该行库存
  refreshRowStock(row)
}
function itemAmount(row: SaleOrderItem) { const q = Number(row.quantity) || 0; const p = Number(row.unitPrice) || 0; return (q * p).toFixed(2) }

// 税额拆分（单价含税口径）：应付总额不变，按税率从总额中拆出税额
const goodsTotal = computed(() => items.value.reduce((s, r) => s + (Number(r.quantity) || 0) * (Number(r.unitPrice) || 0), 0))
const taxAmount = computed(() => form.taxIncluded === 1 && Number(form.taxRate) > 0
  ? Math.round(goodsTotal.value * (Number(form.taxRate) / (100 + Number(form.taxRate))) * 100) / 100
  : 0)
const noTaxAmount = computed(() => Math.round((goodsTotal.value - taxAmount.value) * 100) / 100)
function onTaxSwitch(v: any) { form.taxIncluded = v ? 1 : 0; form.taxRate = v ? (form.taxRate || 13) : 0 }

// ==================== 明细行可用库存 ====================
/**
 * 出库仓库的成品库存快照：key = `${productId}_${qualityType}` → 可用数量。
 * 一次请求拉全仓库存，产品/品质切换时本地直接取值（无网络往返），
 * 仅切换仓库或点「刷新库存」时重新拉取。
 */
const stockMap = ref<Record<string, number>>({})
const stockLoading = ref(false)

/**
 * 拉取出库仓库的成品库存快照。
 * @param silent true=静默（切换仓库自动触发，不弹提示）；false=手动点按钮，弹「库存已刷新」提示
 */
async function loadWarehouseStock(silent = false) {
  stockMap.value = {}
  if (!form.warehouseId) return
  stockLoading.value = true
  try {
    const res: any = await request.get('/warehouse/stock/page', {
      params: { pageSize: 500, warehouseId: form.warehouseId, stockType: 'PRODUCT' }
    })
    const map: Record<string, number> = {}
    for (const s of res?.records || []) {
      if (s.productId == null) continue
      map[`${s.productId}_${s.qualityType || 'A'}`] = Number(s.quantity || 0)
    }
    stockMap.value = map
    if (!silent) ElMessage.success('库存已刷新')
  } catch { stockMap.value = {} } finally { stockLoading.value = false }
}

/**
 * 精确刷新单行的库存（回源查「仓库 + 产品 + 品质」这一组合）。
 * <p>本地 stockMap 是全仓库存快照，可能受分页上限限制未覆盖全部产品、或数据已过期；
 * 因此切换产品 / 品质时按行精确查一次，保证库存列始终是服务端最新值。</p>
 */
async function refreshRowStock(row: SaleOrderItem) {
  if (!form.warehouseId || !row.productId) return
  const qt = row.qualityType || 'A'
  try {
    const res: any = await request.get('/warehouse/stock/page', {
      params: { pageSize: 1, warehouseId: form.warehouseId, productId: row.productId, qualityType: qt, stockType: 'PRODUCT' }
    })
    const rec = res?.records?.[0]
    // 整包替换以触发响应式（stockMap 是 ref，直接改属性也能触发，但整包替换最稳妥）
    stockMap.value = { ...stockMap.value, [`${row.productId}_${qt}`]: Number(rec?.quantity || 0) }
  } catch { /* 查询失败保留旧值，不阻塞录入 */ }
}

/** 明细行可用库存：未选出库仓库或产品时返回 null（表示未知，显示占位符） */
function rowStock(row: SaleOrderItem): number | null {
  if (!form.warehouseId || !row.productId) return null
  return stockMap.value[`${row.productId}_${row.qualityType || 'A'}`] ?? 0
}

/** 该行订购数量是否超过可用库存（超卖预警，保存时后端仍会二次校验） */
function isRowOverstock(row: SaleOrderItem): boolean {
  const s = rowStock(row)
  return s !== null && (Number(row.quantity) || 0) > s
}

// 切换出库仓库时自动静默刷新库存快照（手动刷新用「刷新库存」按钮）
watch(() => form.warehouseId, () => { loadWarehouseStock(true) })

// ==================== 加载（编辑态回填） ====================
async function loadEdit(id: number) {
  pageLoading.value = true
  try {
    const [h, its]: any = await Promise.all([getSaleOrder(id), getSaleOrderItems(id)])
    Object.assign(form, {
      id: h?.id,
      customerId: h?.customerId,
      warehouseId: h?.warehouseId,
      orderDate: h?.orderDate ? String(h.orderDate).slice(0, 10) : '',
      taxIncluded: h?.taxIncluded ?? 0,
      taxRate: h?.taxRate ?? 0,
      remark: h?.remark ?? '',
      settleType: h?.settleType || SettleType.CREDIT,
      settleAccountId: h?.settleAccountId ?? undefined
    })
    items.value = (its || []).map((it: any) => ({ ...it }))
    // 逐行精确校准库存：单据可能开单已久，全量快照之外再回源查一次
    items.value.forEach(refreshRowStock)
  } catch {
    ElMessage.error('加载销售单失败')
  } finally {
    pageLoading.value = false
  }
}

// ==================== 保存 ====================
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条产品'); return }
    for (let i = 0; i < items.value.length; i++) {
      const it = items.value[i]
      if (Number(it.quantity) <= 0) { ElMessage.warning(`第 ${i + 1} 行数量必须大于 0`); return }
      if (Number(it.unitPrice) <= 0) { ElMessage.warning(`第 ${i + 1} 行单价必须大于 0`); return }
    }
    // 库存检查：存在缺货时弹确认，用户确认后仍可保存
    if (form.warehouseId) {
      try {
        const res = await checkSaleOrderStock({ warehouseId: form.warehouseId, items: items.value })
        if (res && res.length > 0 && res.some(r => !r.sufficient)) {
          stockCheckResult.value = res
          stockCheckVisible.value = true
          return
        }
      } catch { /* 检查失败不阻塞 */ }
    }
    await doSubmit()
  })
}

async function doSubmit() {
  // 2026-09-21（用户口径）：销售单明细的品质**不能有不良品和待整理** ⇒ 只允许 A规/B规/C规。
  // 下拉已过滤；这里再拦一道（编辑历史草稿可能带旧值），后端保存/审核两处有同一口径兜底。
  const badQuality = items.value.find(i => !isSellableQuality(i.qualityType))
  if (badQuality) {
    ElMessage.warning(`产品「${badQuality.productName || badQuality.productId}」的品质是「`
      + `${ProductQualityTypeLabel[String(badQuality.qualityType || '')] || badQuality.qualityType}」：`
      + `销售单只能卖 A规/B规/C规 良品，不能是不良品或待整理`)
    return
  }
  // 现金结算必须给出收款账户（后端 normalizeSettle 同样校验，这里先给友好提示）
  if (isCash.value && !form.settleAccountId) { ElMessage.warning('结算方式为「现金」时请选择收款账户'); return }
  submitLoading.value = true
  try {
    const payload = { order: { ...form }, items: items.value }
    if (editId.value !== null) { await updateSaleOrder(editId.value, payload); ElMessage.success('修改成功') }
    else { await createSaleOrder(payload); ElMessage.success('新增成功') }
    sessionStorage.setItem(SALE_ORDER_DIRTY_KEY, '1')
    // 保存成功 ⇒ 先清脏标记（否则离开时会被未保存确认拦住），再关掉本次录入的页签并回列表
    markClean()
    tabStore.closeTabAndBack(route.path)
    router.push('/inventory/sale')
  } catch { } finally { submitLoading.value = false }
}

function confirmStockProceed() { stockCheckVisible.value = false; doSubmit() }
function confirmStockCancel() { stockCheckVisible.value = false }

/**
 * 品质下拉（2026-09-21 用户口径）：**销售单明细的品质不能有不良品和待整理** ⇒
 * 只给 **A规/B规/C规**（`getQualityTypes()` 是含 DEFECT/PENDING 的全量字典，这里按销售口径过滤；
 * 后端 `SaleOrderServiceImpl#assertItemQualitySellable` 在保存与审核两处兜底同一口径）。
 */
async function loadQualityTypes() {
  try { qualityOptions.value = (await getQualityTypes()).filter(q => isSellableQuality(q.value)) }
  catch { qualityOptions.value = [] }
}

onMounted(async () => {
  loadProducts()
  loadQualityTypes()
  loadAccounts()
  if (editId.value !== null) await loadEdit(editId.value)
  else addItem()
  // 数据加载完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改）
  takeBaseline()
})
</script>

<template>
  <!-- 统一骨架（2026-09-23）：页头标题 + 右上「← 返回」由 PageShell 提供；内容分块用 SectionCard -->
  <PageShell :title="isEdit ? '编辑销售单' : '新增销售单'" :loading="pageLoading" back-fallback="/inventory/sale">
    <!-- 主操作「保存」放页头标题左边（2026-09-23 用户口径）；原底部「取消」已并入页头右上「返回」 -->
    <template #leading>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
    </template>

    <SectionCard title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="var(--app-label-width)">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户" prop="customerId">
              <RemoteSelect v-model="form.customerId" :fetch="fetchCustomers" placeholder="请选择" style="width:100%"
                @change="(v: any) => { if (v === ADD_MARKER) { form.customerId = undefined; router.push('/inventory/customer/add'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="出库仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择" style="width:100%"
                @change="(v: any) => { if (v === ADD_MARKER) { form.warehouseId = undefined; router.push('/inventory/warehouse'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="订单日期">
              <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <!-- 结算方式（2026-09-18 按单记，同日改开关；口径：现金 = **立刻到账即结算** / 账期 = 只挂应收） -->
          <el-col :span="8">
            <el-form-item label="结算方式">
              <el-switch :model-value="isCash" inline-prompt
                :active-text="SettleTypeLabel[SettleType.CASH]" :inactive-text="SettleTypeLabel[SettleType.CREDIT]"
                @change="onSettleSwitch" />
              <span style="margin-left:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ isCash ? '立即到账' : '挂应收' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="8" v-if="isCash">
            <el-form-item required label="收款账户">
              <el-select v-model="form.settleAccountId" filterable clearable placeholder="选择现金账户" style="width:100%">
                <el-option v-for="a in accountOptions" :key="a.id" :value="a.id"
                  :label="a.accountName + '（' + (AccountTypeLabel[String(a.accountType || '').toLowerCase()] || a.accountType || '') + '）'" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="含税">
              <el-switch :model-value="form.taxIncluded === 1" @change="onTaxSwitch" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="税率(%)">
              <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" :disabled="form.taxIncluded !== 1" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </SectionCard>

    <SectionCard title="产品明细">
      <template #extra>
        <div style="display:flex; gap:8px; align-items:center">
          <el-button type="primary" :icon="'Plus'" @click="addItem">添加产品</el-button>
          <!-- 注意：必须写 loadWarehouseStock()，不带括号会把 MouseEvent 当作 silent 参数传入导致静默 -->
          <el-button :icon="'Refresh'" :loading="stockLoading" :disabled="!form.warehouseId" @click="loadWarehouseStock()">
            刷新库存
          </el-button>
          <span v-if="!form.warehouseId" style="color:#909399; font-size:var(--app-font-xs)">请先选择出库仓库，再刷新库存</span>
        </div>
      </template>
      <el-table :data="items" border>
          <el-table-column label="SKU" width="130">
            <template #default="{ row }">
              <span v-if="row.sku">{{ row.sku }}</span>
              <span v-else style="color:var(--app-text-secondary)">自动生成</span>
            </template>
          </el-table-column>
          <el-table-column label="产品" min-width="180">
            <template #default="{ row }">
              <RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="选择产品（可输SKU）"
                :preset="{ id: row.productId, name: row.productName, sku: row.sku }"
                style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.productId = undefined; router.push('/product/add'); return } onProductChange(v, row) }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="品质" width="90">
            <template #default="{ row }">
              <!-- 2026-09-21：销售单只能卖良品 ⇒ 下拉只给 A规/B规/C规（不含不良品/待整理） -->
              <el-select v-model="row.qualityType" size="small" style="width:100%"
                :title="'销售单只能卖 A规/B规/C规 良品（不含不良品与待整理）'" @change="refreshRowStock(row)">
                <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="可用库存" width="130" align="right">
            <template #default="{ row }">
              <span v-if="rowStock(row) === null" style="color:#c0c4cc" :title="form.warehouseId ? '请选择产品' : '请先选择出库仓库'">-</span>
              <template v-else>
                <span :style="isRowOverstock(row) ? 'color:#f56c6c;font-weight:bold' : ''">{{ rowStock(row) }}</span>
                <el-tag v-if="isRowOverstock(row)" type="danger" size="small" effect="plain" style="margin-left:4px"
                  title="订购数量超过可用库存，保存时会弹出确认提示">不足</el-tag>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ itemAmount(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template>
          </el-table-column>
        </el-table>
        <div class="sum-bar">
          <div class="sum-item sum-main">
            <span class="sum-label">结算方式</span>
            <span class="sum-value">{{ SettleTypeLabel[form.settleType || 'CREDIT'] || '账期' }}{{ isCash ? '（审核后自动生成收款单）' : '' }}</span>
          </div>
          <div class="sum-item">
            <span class="sum-label">应收总额（含税）</span>
            <span class="sum-value">{{ goodsTotal.toFixed(2) }}</span>
          </div>
          <template v-if="form.taxIncluded === 1">
            <div class="sum-item">
              <span class="sum-label">税额（{{ form.taxRate }}%）</span>
              <span class="sum-value tax-num">{{ taxAmount.toFixed(2) }}</span>
            </div>
            <div class="sum-item">
              <span class="sum-label">不含税金额</span>
              <span class="sum-value">{{ noTaxAmount.toFixed(2) }}</span>
            </div>
          </template>
        </div>
    </SectionCard>

    <!-- 库存不足确认弹窗 -->
    <el-dialog v-model="stockCheckVisible" title="库存不足提醒" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        <template #title>以下产品的订单数量超过当前库存量，确认仍要继续保存订单吗？</template>
      </el-alert>
      <el-table :data="stockCheckResult.filter(r => !r.sufficient)" border>
        <el-table-column prop="productName" label="产品名称" min-width="140" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="订购数量" width="100" align="right">
          <template #default="{ row }">{{ row.required }}</template>
        </el-table-column>
        <el-table-column label="当前库存" width="100" align="right">
          <template #default="{ row }">{{ row.available }}</template>
        </el-table-column>
        <el-table-column label="缺口" width="100" align="right">
          <template #default="{ row }"><span style="color:red;font-weight:bold">{{ row.shortage }}</span></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="confirmStockCancel">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="confirmStockProceed">仍然保存订单</el-button>
      </template>
    </el-dialog>
  </PageShell>
</template>

<style scoped>
/* 页头 / 卡片头 / 底部操作条已统一到全局骨架（styles/page.css + PageShell + SectionCard），
   本页不再自写 .card-header / .footer —— 这正是"统一模板"要消除的重复。 */
/* 金额汇总：标签在上、数值在下，块间竖线分隔，避免多项挤在一行 */
.sum-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  padding: 12px 20px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.sum-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  min-width: 150px;
  padding: 0 20px;
}
.sum-item + .sum-item { border-left: 1px solid var(--el-border-color-lighter); }
.sum-item:first-child { padding-left: 0; }
.sum-item:last-child { padding-right: 0; }
.sum-label { font-size: var(--app-font-xs); color: var(--app-text-secondary); white-space: nowrap; }
.sum-value {
  font-size: var(--app-font-lg);
  font-weight: 600;
  line-height: 1.2;
  color: var(--app-text-primary);
  font-variant-numeric: tabular-nums;
}
.sum-main .sum-value { font-size: var(--app-font-num); color: var(--app-color-primary); }
.tax-num { color: var(--app-color-danger); }
</style>
