<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'
import {
  getSaleOrder, getSaleOrderItems, updateSaleOrder, auditSaleOrder, cancelSaleOrder, unAuditSaleOrder, checkSaleOrderStock, SALE_ORDER_DIRTY_KEY,
  getSaleReturnPage, getSaleExchangePage, SaleReturnStatus, SaleReturnStatusLabel,
  type SaleOrder, type SaleOrderItem
} from '@/api/sale'
const route = useRoute()
const router = useRouter()
const orderId = Number(route.params.id)
const qualityOptions = ref<QualityOption[]>([])

const head = ref<SaleOrder>({})
const items = ref<SaleOrderItem[]>([])
const loading = ref(false)

const isDraft = computed(() => head.value.status === DocStatus.DRAFT)

// Odoo 风格：下拉框展开/搜索时实时查库
const customers = ref<any[]>([])
const warehouses = ref<any[]>([])
const products = ref<any[]>([])
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseType: '成品仓' } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })

async function loadCustomers() { try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] } }
async function loadWarehouses() { try { const r: any = await fetchWarehouses(''); warehouses.value = r?.records || [] } catch { warehouses.value = [] } }
async function loadProducts(keyword?: string) { try { const res: any = await fetchProducts(keyword || ''); products.value = res?.records || [] } catch { products.value = [] } }

/**
 * 并发安全的字典预加载：多个调用方同时请求时复用同一个 in-flight Promise，只发一次请求。
 * 明细翻译依赖产品字典，若字典未就绪就渲染会导致产品列空白（竞态），故渲染前必须等待。
 */
let productsPromise: Promise<void> | null = null
function ensureProducts(): Promise<void> {
  if (products.value.length > 0) return Promise.resolve()
  if (!productsPromise) {
    productsPromise = loadProducts().finally(() => { productsPromise = null })
  }
  return productsPromise
}

// ===== 草稿编辑表单 =====
const formRef = ref<FormInstance>()
const form = reactive<SaleOrder>({ customerId: undefined, warehouseId: undefined, orderDate: '', taxIncluded: 0, taxRate: 0, remark: '' })
const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择出库仓库', trigger: 'change' }]
}
const submitLoading = ref(false)

// 库存检查相关
const stockCheckResult = ref<{ productName: string; spec: string; unit: string; required: number; available: number; shortage: number; sufficient: boolean }[]>([])
const stockCheckVisible = ref(false)
const pendingSubmit = ref(false)

function onProductChange(val: number, row: SaleOrderItem) {
  const p = products.value.find(x => x.id === val)
  if (p) { row.productId = p.id as number; row.productName = p.name; row.sku = p.sku || ''; row.spec = p.spec; row.unit = p.unit }
  // 切换产品后自动刷新该行库存
  refreshRowStock(row)
}
function addItem() { items.value.push({ productId: undefined, qualityType: 'A', productName: '', spec: '', unit: '', quantity: 0, unitPrice: 0, amount: 0, remark: '' }) }
function removeItem(index: number) { items.value.splice(index, 1) }
function itemAmount(row: SaleOrderItem) { const q = Number(row.quantity) || 0; const p = Number(row.unitPrice) || 0; return (q * p).toFixed(2) }

// 税额拆分（单价含税口径）：应付总额不变，按税率从总额中拆出税额
const goodsTotal = computed(() => items.value.reduce((s, r) => s + (Number(r.quantity) || 0) * (Number(r.unitPrice) || 0), 0))
const taxAmount = computed(() => form.taxIncluded === 1 && Number(form.taxRate) > 0
  ? Math.round(goodsTotal.value * (Number(form.taxRate) / (100 + Number(form.taxRate))) * 100) / 100
  : 0)
const noTaxAmount = computed(() => Math.round((goodsTotal.value - taxAmount.value) * 100) / 100)
function onTaxSwitch(v: any) { form.taxIncluded = v ? 1 : 0; form.taxRate = v ? (form.taxRate || 13) : 0 }

// ==================== 售后记录（该销售单发起的退货单 / 换货单） ====================
const afterSaleTab = ref('return')
const returns = ref<any[]>([])
const exchanges = ref<any[]>([])

/** 拉取关联本销售单的退货单与换货单（草稿单不会有售后记录，仍统一查询以便展示空态） */
async function loadAfterSales() {
  try {
    const [r, e]: any = await Promise.all([
      getSaleReturnPage({ saleOrderId: orderId, pageNum: 1, pageSize: 100 }),
      getSaleExchangePage({ saleOrderId: orderId, pageNum: 1, pageSize: 100 })
    ])
    returns.value = r?.records || []
    exchanges.value = e?.records || []
  } catch { returns.value = []; exchanges.value = [] }
}

/** 发起退货 / 换货：带 saleOrderId 跳转，目标页会自动预填来源销售单与明细 */
function goReturn() { router.push(`/sale/return/add?saleOrderId=${orderId}`) }
function goExchange() { router.push(`/sale/exchange/add?saleOrderId=${orderId}`) }

function returnStatusLabel(s: string) { return SaleReturnStatusLabel[String(s)] || s || '-' }

// ==================== 明细行可用库存（草稿编辑态） ====================
/**
 * 用于查库存的仓库：草稿态跟随表单（用户可实时改仓库），只读态用单据本身的出库仓库。
 * 库存只在草稿编辑态展示——已审核单据的明细是历史快照，显示"当前库存"会产生歧义。
 */
const stockWarehouseId = computed(() => (isDraft.value ? form.warehouseId : head.value.warehouseId))

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
  const whId = stockWarehouseId.value
  if (!whId) return
  stockLoading.value = true
  try {
    const res: any = await request.get('/warehouse/stock/page', {
      params: { pageSize: 500, warehouseId: whId, stockType: 'PRODUCT' }
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
  const whId = stockWarehouseId.value
  if (!whId || !row.productId) return
  const qt = row.qualityType || 'A'
  try {
    const res: any = await request.get('/warehouse/stock/page', {
      params: { pageSize: 1, warehouseId: whId, productId: row.productId, qualityType: qt, stockType: 'PRODUCT' }
    })
    const rec = res?.records?.[0]
    // 整包替换以触发响应式（stockMap 是 ref，直接改属性也能触发，但整包替换最稳妥）
    stockMap.value = { ...stockMap.value, [`${row.productId}_${qt}`]: Number(rec?.quantity || 0) }
  } catch { /* 查询失败保留旧值，不阻塞录入 */ }
}

/** 明细行可用库存：未选出库仓库或产品时返回 null（表示未知，显示占位符） */
function rowStock(row: SaleOrderItem): number | null {
  if (!stockWarehouseId.value || !row.productId) return null
  return stockMap.value[`${row.productId}_${row.qualityType || 'A'}`] ?? 0
}

/** 该行订购数量是否超过可用库存（超卖预警，保存时后端仍会二次校验） */
function isRowOverstock(row: SaleOrderItem): boolean {
  const s = rowStock(row)
  return s !== null && (Number(row.quantity) || 0) > s
}

// 切换出库仓库时自动静默刷新库存快照（仅草稿编辑态需要）
watch(stockWarehouseId, () => { if (isDraft.value) loadWarehouseStock(true) })

function statusType(s?: string) { return DocStatusTag[s || ''] || '' }
function customerName(id?: number) { const c = customers.value.find(x => x.id === id); return c ? c.name : '' }
function warehouseName(id?: number) { const w = warehouses.value.find(x => x.id === id); return w ? w.warehouseName : '' }
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

async function loadData() {
  loading.value = true
  try {
    // 明细仅返回 productId，名称/规格/单位由后端回填；此处同时备好本地字典做兜底（历史数据或产品已删除时）
    await ensureProducts()
    const [h, its]: any = await Promise.all([getSaleOrder(orderId), getSaleOrderItems(orderId)])
    head.value = h || {}
    items.value = (its || []).map((it: any) => {
      const p = products.value.find(x => x.id === it.productId)
      return {
        ...it,
        productName: it.productName || (p ? p.name : ''),
        spec: it.spec || (p ? p.spec : ''),
        unit: it.unit || (p ? p.unit : '')
      }
    })
    // 草稿态：将订单头同步到编辑表单
    Object.assign(form, {
      id: head.value.id, customerId: head.value.customerId, warehouseId: head.value.warehouseId,
      orderDate: head.value.orderDate, taxIncluded: head.value.taxIncluded, taxRate: head.value.taxRate, remark: head.value.remark
    })
    // 逐行精确校准库存：单据可能开单已久，全量快照之外再回源查一次
    if (isDraft.value) items.value.forEach(refreshRowStock)
    // 售后记录：该销售单发起的退货单与换货单
    await loadAfterSales()
  } catch { } finally { loading.value = false }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条产品'); return }
    // 明细校验：数量与单价必须大于0
    for (let i = 0; i < items.value.length; i++) {
      const it = items.value[i]
      if (Number(it.quantity) <= 0) { ElMessage.warning(`第 ${i + 1} 行数量必须大于 0`); return }
      if (Number(it.unitPrice) <= 0) { ElMessage.warning(`第 ${i + 1} 行单价必须大于 0`); return }
    }
    // 库存检查
    if (form.warehouseId) {
      try {
        const res = await checkSaleOrderStock({ warehouseId: form.warehouseId, items: items.value })
        if (res && res.length > 0) {
          const hasShortage = res.some(r => !r.sufficient)
          if (hasShortage) { stockCheckResult.value = res; stockCheckVisible.value = true; pendingSubmit.value = true; return }
        }
      } catch { /* 检查失败不阻塞 */ }
    }
    await doSubmit()
  })
}
async function doSubmit() {
  submitLoading.value = true
  try {
    const payload = { order: { ...form }, items: items.value }
    await updateSaleOrder(orderId, payload)
    ElMessage.success('保存成功')
    sessionStorage.setItem(SALE_ORDER_DIRTY_KEY, '1')
    await loadData()
  } catch { } finally { submitLoading.value = false }
}
function confirmStockProceed() { stockCheckVisible.value = false; pendingSubmit.value = false; doSubmit() }
function confirmStockCancel() { stockCheckVisible.value = false; pendingSubmit.value = false }

async function handleAudit() {
  try {
    await ElMessageBox.confirm(`确认审核销售单「${head.value.code}」？审核后将直接出库并生成应收。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditSaleOrder(orderId); ElMessage.success('审核成功'); sessionStorage.setItem(SALE_ORDER_DIRTY_KEY, '1'); loadData()
  } catch { }
}
async function handleCancel() {
  try {
    await ElMessageBox.confirm(`确认作废销售单「${head.value.code}」？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelSaleOrder(orderId); ElMessage.success('已作废'); sessionStorage.setItem(SALE_ORDER_DIRTY_KEY, '1'); loadData()
  } catch { }
}
async function handleUnAudit() {
  try {
    await ElMessageBox.confirm(`确认反审核销售单「${head.value.code}」？将冲回出库与应收。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await unAuditSaleOrder(orderId); ElMessage.success('已反审核'); sessionStorage.setItem(SALE_ORDER_DIRTY_KEY, '1'); loadData()
  } catch { }
}

function goBack() { router.back() }
async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }

// 字典类（客户/仓库/产品/品质）只需加载一次
onMounted(() => { loadCustomers(); loadWarehouses(); ensureProducts(); loadQualityTypes() })

/**
 * 每次进入详情页都重新拉取单据数据。
 * <p>原因：layout 用 keep-alive 缓存页面组件，从列表返回或再次进入时组件被复用、
 * onMounted 不会再次触发。若单据数据只在 onMounted 加载，会停留在上次缓存的状态
 * ——例如在列表点「审核」后再进详情，仍显示缓存的草稿状态。</p>
 */
onActivated(() => { loadData() })
</script>

<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <div class="head-bar">
        <div class="title">
          <span class="title-text">销售单详情</span>
          <el-tag :type="statusType(head.status)" effect="plain">{{ DocStatusLabel[String(head.status)] || head.status }}</el-tag>
        </div>
        <div class="ops">
          <el-button @click="goBack">返回</el-button>
          <!-- 售后：仅已审核销售单可发起（后端 saleOrders 只返回已审核单据，售后锚定销售明细） -->
          <el-button v-if="head.status === DocStatus.AUDITED" type="warning" @click="goReturn">退货</el-button>
          <el-button v-if="head.status === DocStatus.AUDITED" type="warning" plain @click="goExchange">换货</el-button>
          <el-button v-if="head.status === DocStatus.DRAFT" type="success" @click="handleAudit">审核</el-button>
          <el-button v-if="head.status === DocStatus.AUDITED" type="warning" @click="handleUnAudit">反审核</el-button>
          <el-button v-if="head.status === DocStatus.DRAFT" type="danger" @click="handleCancel">作废</el-button>
        </div>
      </div>

      <!-- 草稿：可编辑 -->
      <template v-if="isDraft">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" style="margin-top:16px">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="客户" prop="customerId">
                <RemoteSelect v-model="form.customerId" :fetch="fetchCustomers" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.customerId = undefined; router.push('/inventory/customer'); return } }">
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </RemoteSelect>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="出库仓库" prop="warehouseId">
                <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.warehouseId = undefined; router.push('/inventory/warehouse'); return } }">
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </RemoteSelect>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="订单日期">
                <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="收税">
                <el-switch :model-value="form.taxIncluded === 1" @change="onTaxSwitch" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
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

          <el-divider content-position="left">产品明细</el-divider>
          <div style="margin-bottom:8px; display:flex; gap:8px; align-items:center">
            <el-button type="primary" :icon="'Plus'" @click="addItem">添加产品</el-button>
            <!-- 注意：必须写 loadWarehouseStock()，不带括号会把 MouseEvent 当作 silent 参数传入导致静默 -->
            <el-button :icon="'Refresh'" :loading="stockLoading" :disabled="!stockWarehouseId" @click="loadWarehouseStock()">刷新库存</el-button>
            <span v-if="!stockWarehouseId" style="color:#909399; font-size:12px">请先选择出库仓库，再刷新库存</span>
          </div>
          <el-table :data="items" border>
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column label="SKU" width="130">
              <template #default="{ row }">
                <span v-if="row.sku">{{ row.sku }}</span>
                <span v-else style="color:var(--app-text-secondary)">自动生成</span>
              </template>
            </el-table-column>
            <el-table-column label="产品" min-width="180">
              <template #default="{ row }">
                <RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="选择产品（可输SKU）"
                  :preset="{ id: row.productId, name: row.productName }"
                  style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.productId = undefined; router.push('/material'); return } onProductChange(v, row) }">
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </RemoteSelect>
              </template>
            </el-table-column>
            <el-table-column prop="spec" label="规格" width="100" />
            <el-table-column prop="unit" label="单位" width="70" />
            <el-table-column label="品质" width="90">
              <template #default="{ row }">
                <el-select v-model="row.qualityType" size="small" style="width:100%" @change="refreshRowStock(row)">
                  <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="可用库存" width="130" align="right">
              <template #default="{ row }">
                <span v-if="rowStock(row) === null" style="color:#c0c4cc" :title="stockWarehouseId ? '请选择产品' : '请先选择出库仓库'">-</span>
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
            <span>应付总额（含税）：<b>{{ goodsTotal.toFixed(2) }}</b></span>
            <template v-if="form.taxIncluded === 1">
              <span>税额（{{ form.taxRate }}%）： <b class="tax-num">{{ taxAmount.toFixed(2) }}</b></span>
              <span>不含税金额： <b>{{ noTaxAmount.toFixed(2) }}</b></span>
            </template>
          </div>
        </el-form>
        <div class="footer">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
        </div>
      </template>

      <!-- 非草稿：只读 -->
      <template v-else>
        <el-descriptions :column="2" border style="margin-top:16px">
          <el-descriptions-item label="单号">{{ head.code }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ customerName(head.customerId) }}</el-descriptions-item>
          <el-descriptions-item label="出库仓库">{{ warehouseName(head.warehouseId) }}</el-descriptions-item>
          <el-descriptions-item label="订单日期">{{ head.orderDate }}</el-descriptions-item>
          <el-descriptions-item label="收税">{{ head.taxIncluded === 1 ? '是（' + head.taxRate + '%）' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="总金额（含税）">{{ fmt(head.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="税额">{{ fmt(head.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ head.remark }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">产品明细</el-divider>
        <el-table :data="items" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="sku" label="SKU" width="130" />
          <el-table-column prop="productName" label="产品" min-width="140" />
          <el-table-column prop="spec" label="规格" width="100" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column prop="quantity" label="数量" width="90" align="right" />
          <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
          <el-table-column prop="amount" label="金额" width="100" align="right" />
        </el-table>
      </template>

      <!-- ===== 售后记录：本销售单发起的销售退单与销售换货单 ===== -->
      <template v-if="!isDraft">
        <el-divider content-position="left">售后记录</el-divider>
        <el-tabs v-model="afterSaleTab">
          <el-tab-pane :label="`销售退单 (${returns.length})`" name="return">
            <el-table :data="returns" border size="small" empty-text="暂无退货记录">
              <el-table-column prop="code" label="退单号" width="170" />
              <el-table-column prop="returnDate" label="退货日期" width="110" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'AUDITED' ? 'success' : (row.status === 'CANCELLED' ? 'info' : 'warning')">
                    {{ returnStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalAmount" label="退货金额" width="120" align="right">
                <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
              </el-table-column>
              <el-table-column prop="itemsSummary" label="退货概况" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ row.itemsSummary || '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push('/sale/return/detail/' + row.id)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`销售换货单 (${exchanges.length})`" name="exchange">
            <el-table :data="exchanges" border size="small" empty-text="暂无换货记录">
              <el-table-column prop="code" label="换货单号" width="170" />
              <el-table-column prop="exchangeDate" label="换货日期" width="110" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'AUDITED' ? 'success' : (row.status === 'CANCELLED' ? 'info' : 'warning')">
                    {{ DocStatusLabel[String(row.status)] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalAmount" label="换出货值" width="110" align="right">
                <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
              </el-table-column>
              <el-table-column label="收费" width="140" align="right">
                <template #default="{ row }">
                  <span v-if="Number(row.chargeFlag) === 1 && Number(row.chargeAmount) > 0" style="color:#e6a23c;font-weight:600">
                    {{ fmt(row.chargeAmount) }}
                  </span>
                  <span v-else style="color:#c0c4cc">不收费</span>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.remark || '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push('/sale/exchange/detail/' + row.id)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-card>

    <!-- 库存不足确认弹窗 -->
    <el-dialog v-model="stockCheckVisible" title="库存不足提醒" width="650px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        <template #title>以下产品的订单数量超过当前库存量，确认仍要继续保存订单吗？</template>
      </el-alert>
      <el-table :data="stockCheckResult.filter(r => !r.sufficient)" border>
        <el-table-column prop="productName" label="产品名称" min-width="140" />
        <el-table-column prop="spec" label="规格" width="100" />
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
        <el-button type="primary" @click="confirmStockProceed">仍然保存订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 0; }
.head-bar { display: flex; align-items: center; justify-content: space-between; }
.title { display: flex; align-items: center; gap: 8px; }
.title-text { font-size: 16px; font-weight: 600; }
.footer { margin-top: 16px; display: flex; justify-content: flex-end; gap: 12px; }
</style>
