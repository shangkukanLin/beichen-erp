<script setup lang="ts">
// 加工退货记录详情（2026-09-23 用户要求：原「加工退货详情」580px 抽屉改为独立页面）
// —— 按 id 回源 `/outsource/order-delivery/return-defect/{id}/detail`（含落账明细），
//    台账行点击 / 行内「详情」按钮都跳到这里。
import { computed, ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_RETURN_ORDER_DIRTY_KEY } from '@/api/enums'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { useTabStore } from '@/stores/tabs'
import { applyPageTitle } from '@/utils/pageTitle'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<any>({})
const tabStore = useTabStore()

/**
 * 页头 / 页签 / 浏览器标题 / 卡片标题**四处同源**（2026-09-28 用户口径「加工退货子菜单列表的详情标题需要对齐」）。
 * <p>本页承载**两类红冲记录**（同一张表、同一个 delivery_type，只按**是否挂加工单**区分）⇒ 标题也随之分两态：
 *  有单红冲（挂加工单 = 把该单已收的货退回工厂，从「加工收退」的收货详细页发起）=「**加工退货详情**」；
 *  工厂售后（GTW-，不挂加工单 = 工厂责任的售后维修）=「**工厂售后详情**」。
 * 2026-09-29 用户口径「需要统一」：有单那态原叫「关联退货详情」（2026-09-28 随已下线的「关联退货」叶子起的名），
 *   现统一到**「加工退货」** —— 与录入页「加工退货（拆分还料）」、收货记录的类型标签「加工退货」/「加工退货规格」、
 *   以及本页路由 `meta.title`（加工退货详情）四处一致。</p>
 * <p>⚠️ 加载中（detail.id 还没回来）沿用路由 meta 名「加工退货详情」，数据回来后立刻改名 ——
 * 与 `return-order/detail.vue` 同款"故意不写 immediate"：拿不到数据时宁可用兜底名，也不要闪一个可能错的名字。</p>
 */
const pageTitleText = computed(() => {
  if (detail.value.id == null) return (route.meta.title as string) || '加工退货详情'
  return detail.value.orderId != null ? '加工退货详情' : '工厂售后详情'
})
watch(pageTitleText, (t) => { tabStore.updateTabTitle(route.path, t); applyPageTitle(t) })

/** 退货规格 code -> 中文（与列表、工厂售后弹窗同一口径） */
function specText(q?: string) {
  if (q === 'A') return 'A规'
  if (q === 'B') return 'B规'
  if (q === 'C') return 'C规'
  if (q === 'DEFECT') return '不良'
  return q || '-'
}
/**
 * 库存形态（与 /outsource/warehouse-detail 同口径）：工厂售后的成品以「成品（加工退货）」形态进加工厂委外仓。
 */
const StockFormLabel: Record<string, string> = {
  MATERIAL: '物料',
  PRODUCT_DEFECT: '成品（加工退货）',
  PRODUCT_REPAIR: '成品（维修退货）',
  MATERIAL_REPAIR: '物料（送修在厂）',
}

/**
 * 是否为「工厂售后·新口径」记录（2026-09-27 用户口径）——判定**只看实际流水**，不看 linked：
 * - 工厂售后（GTW-，2026-09-25 P1-1 起）：不拆 BOM、不冲应付，只扣成品 + 把成品以 PRODUCT_DEFECT 转入委外仓
 *   ⇒ `materials` 为空、`outsourceIn` 有值；
 * - 存量「独立 DEFECT 单」（旧逻辑，已停止新增）：虽也不关联加工单，但会还料 + 负应付、且**没有** PRODUCT_DEFECT 转移腿
 *   ⇒ `materials` 非空 ⇒ 不能被误判成新口径（否则会隐藏它的真实还料明细）。
 */
const isNoOrderNew = computed(() =>
  !detail.value.linked && (detail.value.materials || []).length === 0
)
function goOrder() {
  if (detail.value.orderId != null) router.push(`/outsource/order/detail/${detail.value.orderId}`)
}

async function load() {
  loading.value = true
  try {
    detail.value = (await request.get<any, any>(`/outsource/order-delivery/return-defect/${route.params.id}/detail`)) || {}
  } catch { detail.value = {} } finally { loading.value = false }
}

/**
 * ==================== 加工返回登记（2026-09-27 用户口径） ====================
 * 「加工返回单」不再是独立单据/独立菜单叶子：工厂修好送回时，**就在本页**登记返回，
 * 交互与「客户售后」详情页的「登记维修返回」完全一致 —— 登记即生效、可逐条撤销、有返回记录列表。
 * 账务：核销在厂成品（PRODUCT_DEFECT）+ 修好成品回我方仓 + 按实际用料扣委外仓料
 * + 料款生成对加工厂的**赔料应收** + 成本结转。
 * <p>2026-09-29 用户口径「送回时按实际用料 FIFO 生成对工厂的赔料应收，这个需要修改一下：登记返回的时候，
 * 可以填写具体价格，默认 FIFO 可修改」⇒ **单价改在登记时定**：后端按登记时点的默认 FIFO 价预填，
 * 用户可人工改写（`priceManual` 留痕），审核时用该**快照单价**算赔料应收；
 * 而**成本结转仍按审核时点的 FIFO**（人工定价不污染库存成本，两笔钱在后端分开算）。</p>
 */
const returns = ref<any[]>([])
const returnsLoading = ref(false)
/**
 * 已审核的返回记录（2026-09-28 用户口径「登记返回需要审核和反审核」）。
 * <p>登记只建**草稿**、审核才落账 ⇒ 已返回量/未返回量/「登记返回」入口都只认这些行。</p>
 */
const auditedReturns = computed(() => returns.value.filter((r: any) => r.status === DocStatus.AUDITED))
/** 已返回量 = Σ 本来源单**已审核**的返回记录（列表接口只返回本来源单） */
const returnedQty = computed(() => auditedReturns.value.reduce((s: number, r: any) => s + Math.abs(Number(r.quantity || 0)), 0))
/** 退货总量（台账记录里数量是负数 ⇒ 取绝对值） */
const sentQty = computed(() => Math.abs(Number(detail.value.quantity || 0)))
/** 未返回量 */
const unreturnedQty = computed(() => Math.max(0, sentQty.value - returnedQty.value))
/**
 * 能否登记返回：仅「工厂售后·新口径」（成品已以 PRODUCT_DEFECT 转入委外仓）+ 已审核 + 还有未返回。
 * 存量「独立 DEFECT 单」不进在厂 ⇒ 给它登记会撞「在厂成品不足」⇒ 直接不给入口。
 */
const canReturn = computed(() => isNoOrderNew.value && detail.value.settled === true
  && detail.value.status === DocStatus.AUDITED && unreturnedQty.value > 0)

async function loadReturns() {
  returnsLoading.value = true
  try {
    returns.value = (await request.get<any, any>(`/outsource/order-delivery/${route.params.id}/return-backs`)) || []
  } catch { returns.value = [] } finally { returnsLoading.value = false }
}

/** 回仓品质可选档（与工厂售后建单、加工返回弹窗同一口径） */
const specOptions = [
  { value: 'A', label: 'A规' }, { value: 'B', label: 'B规' },
  { value: 'C', label: 'C规' }, { value: 'DEFECT', label: '不良' }
]
const returnDlg = reactive({ visible: false, saving: false })
const returnForm = reactive({
  quantity: '' as any, returnQualityType: 'A', inWarehouseId: undefined as any,
  returnDate: '', remark: '',
  // 2026-09-29：每行用料带「单价（默认 FIFO，可人工改）+ 是否人工定价」—— 见 refreshDefaultPrice 注释
  items: [] as Array<{ materialId: any, quantity: any, unitPrice: any, priceManual?: boolean }>
})
/** 回仓仓库（我方自有成品仓） */
const fetchFinishedWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })
/** 「实际用料」候选：后端按**来源单的 BOM 快照**解析（池空 ⇒ 允许只登记返回、不填用料，不卡流程） */
const candidates = ref<any[]>([])
async function loadCandidates() {
  candidates.value = []
  try {
    candidates.value = (await request.get<any, any>(`/outsource/order-delivery/${route.params.id}/return-back-material-candidates`)) || []
  } catch { candidates.value = [] }
}
/** 选料后默认数量 = 单套用量 × 本次返回数量（可改；数量仍可超 BOM） */
function onPickMaterial(it: any) {
  const c = candidates.value.find((x: any) => String(x.materialId) === String(it.materialId))
  if (!c) return
  const n = Math.round(Number(returnForm.quantity) || 0)
  it.quantity = Math.max(1, Math.round(Number(c.perSetQuantity || 0) * (n > 0 ? n : 1)))
  it.priceManual = false          // 换料 ⇒ 单价回到默认（重新带 FIFO）
  refreshDefaultPrice(it)
}

/**
 * ==================== 用料单价（2026-09-29 用户口径） ====================
 * 「登记返回的时候，可以填写具体价格，默认 FIFO 可修改」：单价默认由后端按**登记时点**的默认价带出
 * （FIFO 四级链：物料移动加权成本 → 交期 FIFO → 物料主数据参考价 → 0），用户可人工改写（`priceManual` 随单提交，留痕）。
 * ⚠️ 人工定价只影响**对加工厂的赔料应收**；修好入库成品的**成本结转仍按审核时点的 FIFO**（后端保证）。
 */
async function refreshDefaultPrice(it: any) {
  if (it.priceManual) return                                   // 人工改过 ⇒ 不再被默认值覆盖
  if (!it.materialId || !(Number(it.quantity) > 0)) { it.unitPrice = undefined; return }
  try {
    const r = await request.get<any, any>(`/outsource/order-delivery/${route.params.id}/return-back-material-price`,
      { params: { materialId: it.materialId, quantity: Number(it.quantity) } })
    it.unitPrice = Number(r?.unitPrice ?? 0)
  } catch { /* 取默认价失败不挡登记：留空由后端按登记时点兜底快照 */ }
}
/** 用量变化：未人工定价 ⇒ 重取默认价（FIFO 按量取批次，量变价可能变） */
function onQtyChange(it: any) {
  it.quantity = Math.max(0, Math.round(Number(it.quantity) || 0))
  refreshDefaultPrice(it)
}
/** 人工改单价 ⇒ 标记（后续不再被默认价覆盖） */
function onPriceInput(it: any) { it.priceManual = true }
/** 行料款 = 单价 × 用量（= 该行的赔料应收） */
function lineAmount(it: any) { return (Number(it.unitPrice) || 0) * (Number(it.quantity) || 0) }
/** 赔料应收合计（默认 FIFO，可人工定价） */
const returnTotal = computed(() => returnForm.items.reduce((s: number, it: any) => s + lineAmount(it), 0))

function isMaterialPicked(mid: any, cur: any) {
  return returnForm.items.some((it: any) => it !== cur && String(it.materialId) === String(mid))
}
function addItem() { returnForm.items.push({ materialId: undefined, quantity: undefined, unitPrice: undefined, priceManual: false }) }
function removeItem(i: number) { returnForm.items.splice(i, 1) }

function openReturn() {
  Object.assign(returnForm, {
    quantity: unreturnedQty.value,                       // 默认=未返回量（可改；后端按单防超返）
    returnQualityType: detail.value.qualityType || 'A',  // 默认沿用退货规格
    inWarehouseId: undefined, returnDate: '', remark: '', items: []
  })
  returnDlg.visible = true
  loadCandidates()
}

async function submitReturn() {
  const qty = Math.round(Number(returnForm.quantity) || 0)
  if (!(qty > 0)) { ElMessage.warning('请输入返回数量'); return }
  if (qty > unreturnedQty.value) { ElMessage.warning(`返回数量不能超过未返回量（${unreturnedQty.value}）`); return }
  if (!returnForm.inWarehouseId) { ElMessage.warning('请选择回仓仓库'); return }
  const items = returnForm.items
    .map((it: any) => ({
      materialId: it.materialId, quantity: Math.round(Number(it.quantity) || 0),
      // 2026-09-29（用户口径）：单价随单提交（人工定价必带价；默认价也一起提交，后端按登记时点快照）
      unitPrice: (it.unitPrice === '' || it.unitPrice == null) ? undefined : Number(it.unitPrice),
      priceManual: !!it.priceManual
    }))
    .filter((it: any) => it.materialId && it.quantity > 0)
  // 池非空时必须至少一行有效用料；池空（来源单没绑 BOM 快照/该产品无 BOM）允许只登记返回（料款应收 0）
  if (!items.length && candidates.value.length > 0) { ElMessage.warning('请至少填写一行有效用料（物料+数量）'); return }
  if (items.some((it: any) => it.priceManual && it.unitPrice == null)) { ElMessage.warning('人工定价的用料行必须填写单价'); return }
  if (items.some((it: any) => it.unitPrice != null && it.unitPrice < 0)) { ElMessage.warning('用料单价不能为负数'); return }
  returnDlg.saving = true
  try {
    await request.post(`/outsource/order-delivery/${route.params.id}/return-back`, {
      factoryId: detail.value.factoryId, productId: detail.value.productMasterId,
      quantity: qty, defectQualityType: detail.value.qualityType,
      returnQualityType: returnForm.returnQualityType, inWarehouseId: returnForm.inWarehouseId,
      returnDate: returnForm.returnDate || undefined, remark: returnForm.remark, items
    })
    ElMessage.success('返回草稿已保存，请在下方「返回记录」里审核（审核后才落账）')
    returnDlg.visible = false
    await Promise.all([load(), loadReturns()])
  } catch (e: any) { ElMessage.error(e?.message || '登记返回失败') } finally { returnDlg.saving = false }
}

/**
 * ==================== 返回记录的 审核 / 反审核 / 删除（2026-09-28 用户口径） ====================
 * 登记只建**草稿**（不动库存/账务）；审核才落账（核销在厂成品 + 成品回仓 + 按实际用料扣料 + 赔料应收 + 成本结转）；
 * 反审核对称逆回并**留痕**（记录回草稿）；删除只对草稿开放。
 */
async function auditReturn(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认审核返回「${row.code}」吗？审核后才会落账：核销在厂成品（加工厂委外仓）、修好成品回我方仓、按实际用料从委外仓扣料，`
      + `并按登记时的用料单价（默认 FIFO、可人工定价）生成对加工厂的赔料应收（成品成本仍按 FIFO 结转）。`,
      '确认审核', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/order-delivery/return-back/${row.id}/audit`)
    ElMessage.success('已审核')
    invalidate('outsourceReturnOrder')
    await Promise.all([load(), loadReturns()])
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function unAuditReturn(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认反审核返回「${row.code}」吗？将对称逆回：恢复在厂成品、扣回已回仓成品、回补实际用料、冲销赔料应收并反结转成本；记录回到草稿（留痕可查）。`,
      '确认反审核', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/order-delivery/return-back/${row.id}/un-audit`)
    ElMessage.success('已反审核')
    invalidate('outsourceReturnOrder')
    await Promise.all([load(), loadReturns()])
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/** 删除返回**草稿**（仅草稿；已审核的必须先「反审核」） */
async function revokeReturn(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除返回草稿「${row.code}」吗？该草稿尚未落账（未动库存/账务），删除后不可恢复。`,
      '删除返回草稿', { type: 'warning' })
  } catch { return }
  try {
    await request.delete(`/outsource/order-delivery/return-back/${row.id}`)
    ElMessage.success('已删除草稿')
    await Promise.all([load(), loadReturns()])
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

/**
 * ==================== 审核 / 反审核（2026-09-28 用户口径） ====================
 * 「加工退货详情」本页只读，但审核与反审核要能在这里直接办（原先只能绕到成品收货详细页的「收货记录」表点审核）。
 * 用**通用端点**（与收货记录列表、加工退货台账同一对）：
 * `PUT /outsource/order-delivery/{id}/audit` · `/un-audit` —— 服务层按 isReverse 分派落账，页面不需要任何新端点。
 * 提示语按**有单/无单**分两支（落账口径不同：有单还料 + 回退加工单已收 + 冲应付；无单只扣成品 + 转入厂委外仓）。
 */
async function handleAudit() {
  const tip = detail.value.orderId
    ? '确认审核该加工退货？审核后成品出库、按 BOM 还料，并把退回数量从该加工单的已收数量中回退、同时冲减应付。'
    : '确认审核该加工退货？审核后成品出库，并以「成品（加工退货）」形态转入该加工厂委外仓（不还料、不冲应付）。'
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/outsource/order-delivery/${route.params.id}/audit`)
    ElMessage.success('已审核')
    invalidate('outsourceReturnOrder')
    await load()
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

/**
 * 反审核（**不可逆**：恢复成品库存 / 扣回已还的料 / 冲回应付，单据回草稿）。
 * <p>先挡「已登记返回」：工厂把货送回后在厂成品已被核销，逆回会造成账实错位 ⇒ 要求先逐条撤销返回。
 * 这道闸后端也有（同批补的服务层校验），前端先拦只是为了给出可操作提示、不让人去撞"库存不足"。</p>
 */
async function handleUnAudit() {
  if (auditedReturns.value.length > 0) {
    ElMessage.warning(`该记录已有 ${auditedReturns.value.length} 条「已审核」的加工返回，请先在下方逐条反审核后再反审核本退货单`)
    return
  }
  const tip = detail.value.orderId
    ? '确认反审核？将恢复成品库存、扣回已还的料并冲销应付，单据回到草稿。'
    : '确认反审核？将把在厂成品（加工退货）回退并恢复成品库存，单据回到草稿。'
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/outsource/order-delivery/${route.params.id}/un-audit`)
    ElMessage.success('已反审核')
    invalidate('outsourceReturnOrder')
    await Promise.all([load(), loadReturns()])
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

onMounted(async () => { await load(); await loadReturns() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原「返回」按钮已删）。
       本页动作（2026-09-27 / 2026-09-28 用户口径）：审核 · 反审核 ·（无单新口径）登记返回 -->
  <PageShell :title="pageTitleText" :loading="loading" back-fallback="/outsource/return-order">
    <template #actions>
      <!-- 审核 / 反审核（2026-09-28 用户口径「加工退货详情需要有审核和反审核功能」）：
           与收货记录列表/加工退货台账同一对通用端点；条件与它们一致（草稿可审核、已审核可反审核） -->
      <el-button v-perm="'outsource:order-delivery'" v-if="detail.status===DocStatus.DRAFT" type="success" @click="handleAudit">审核</el-button>
      <el-button v-perm="'outsource:order-delivery'" v-if="detail.status===DocStatus.AUDITED" type="warning" @click="handleUnAudit">反审核</el-button>
      <!-- 2026-09-27 用户口径：工厂修好送回时**就在本页登记返回**（不再去「加工返回单」叶子开单） -->
      <el-button v-if="canReturn" type="primary" @click="openReturn">登记返回</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <!-- 卡片标题与页头/页签/浏览器标题**逐字同名**（2026-09-28 家规：四处同源，守卫按等值断言）；
               单据号在下方描述列表「退货单号」里可见，不再拼进标题，避免四处不一致 -->
          <span style="font-weight:600">{{ pageTitleText }}</span>
        </div>
      </template>

      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code || ('加工退货#' + (detail.id ?? '-')) }}</el-descriptions-item>
        <el-descriptions-item label="记录ID">{{ detail.id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ detail.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="加工厂">{{ detail.factoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联加工单">
          <el-button v-if="detail.orderCode && detail.orderId" type="primary" link @click="goOrder()">{{ detail.orderCode }}</el-button>
          <span v-else-if="detail.orderCode">{{ detail.orderCode }}</span>
          <span v-else style="color:var(--app-text-placeholder)">未关联（无单退回）</span>
        </el-descriptions-item>
        <el-descriptions-item label="产品">{{ (detail.productName || '-') + (detail.sku ? '（' + detail.sku + '）' : '') }}</el-descriptions-item>
        <el-descriptions-item label="退货规格">{{ specText(detail.qualityType) }}</el-descriptions-item>
        <el-descriptions-item label="退货数量">
          <span style="color:var(--app-color-danger);font-weight:500">{{ Math.abs(Number(detail.quantity || 0)) }}</span>
        </el-descriptions-item>
        <!-- 返回进度（2026-09-27）：口径同台账「退货/已返回」列 —— 工厂售后才有（有单红冲不进在厂，没有返回一说） -->
        <el-descriptions-item v-if="isNoOrderNew && detail.settled" label="返回进度">
          <span :style="{ color: unreturnedQty > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontWeight: 500 }">
            {{ sentQty }} / {{ returnedQty }}
          </span>
          <span style="margin-left:6px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
            {{ unreturnedQty > 0 ? ('未返回 ' + unreturnedQty) : '已全部返回' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="扣减仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <!-- 2026-09-27（用户口径）：工厂售后建单时解析/选定的 BOM 快照 —— 工厂修好送回时
             「加工返回单」按它限定可选的「实际用料」（无单红冲本身仍不拆料还料，见 P1-1） -->
        <el-descriptions-item label="BOM 快照">
          <span v-if="detail.bomSnapshotId">v{{ detail.bomVersion ?? '?' }}</span>
          <span v-else style="color:var(--app-text-placeholder)">未绑定（返回单里将没有可选的用料）</span>
        </el-descriptions-item>
        <el-descriptions-item label="建单时间">{{ detail.createTime ? String(detail.createTime).replace('T', ' ').slice(0, 19) : '-' }}</el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">落账明细</span></template>
      <!-- 2026-09-27 用户口径（详情页文案按真实流水分支）：工厂售后（GTW-）与有单红冲（GTH-）落账口径不同，
           原先一律写「按 BOM 还料 + 冲减应付」⇒ 工厂售后会显示根本不存在的动作（用户问到的困惑点）。
           判定见 isNoOrderNew（只看流水，兼容存量的「独立 DEFECT 单」）。 -->
      <el-alert v-if="detail.id && !detail.settled" type="info" :closable="false" show-icon
        :title="isNoOrderNew
          ? '尚未落账（草稿 / 已反审核）：审核后才会扣减成品，并把成品以「成品（加工退货）」形态转入加工厂委外仓（本口径不还料、不冲应付）。'
          : '尚未落账（草稿 / 已反审核）：审核后才会扣减成品、把 BOM 料还回工厂委外仓并冲减应付。'" />
      <template v-else-if="detail.settled">
        <p style="margin:0 0 8px;line-height:1.6;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
          ① 成品：已从「{{ detail.warehouseName || '-' }}」扣减
          <b style="color:var(--app-color-danger)">{{ Math.abs(Number(detail.quantity || 0)) }}</b> 件（{{ specText(detail.qualityType) }}）；
          <template v-if="!isNoOrderNew">
            ② 还料：按 BOM 还回工厂委外仓的物料如下<template v-if="detail.orderCode">，并回退该加工单的已收数量</template>。
          </template>
          <template v-else>
            ② 还料：<b>本单不还料</b>（工厂售后不拆 BOM —— 拆料与退货时点无关，BOM 改过即拆错）。
            退回成品以「成品（加工退货）」形态挂在下方委外仓；工厂修好送回时在<b>本页「登记返回」</b>按
            <b>实际用料</b>扣料，并生成对加工厂的赔料应收（<b>用料单价默认按 FIFO 带出，登记时可人工修改</b>）。
          </template>
        </p>
        <el-table v-if="(detail.materials || []).length" :data="detail.materials" border stripe size="small">
          <el-table-column prop="materialName" label="还回物料" min-width="130" show-overflow-tooltip />
          <el-table-column label="品质" width="70" align="center">
            <template #default="{ row }">{{ row.qualityType === 'DEFECT' ? '不良' : '良品' }}</template>
          </el-table-column>
          <el-table-column label="数量" width="80" align="right"><template #default="{ row }">{{ row.quantity }}</template></el-table-column>
          <el-table-column prop="warehouseName" label="还入的委外仓" min-width="120" show-overflow-tooltip />
        </el-table>
        <p v-if="!isNoOrderNew && !(detail.materials || []).length" style="margin:6px 0 0;color:var(--app-text-placeholder);font-size:var(--app-font-xs)">
          无还料记录（包工包料产品 / 该产品无 BOM 快照 ⇒ 只扣成品、不还料）
        </p>
        <!-- 工厂售后独有：成品落在哪个委外仓 + 什么形态（数据来自 OUTSOURCE_DEFECT_IN 流水） -->
        <el-table v-if="detail.outsourceIn" :data="[detail.outsourceIn]" border stripe size="small" style="margin-top:8px">
          <el-table-column prop="warehouseName" label="转入的委外仓" min-width="150" show-overflow-tooltip />
          <el-table-column label="形态" width="150" align="center">
            <template #default="{ row }"><el-tag type="danger" size="small">{{ StockFormLabel[row.stockForm] || row.stockForm || '-' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="数量" width="90" align="right">
            <template #default="{ row }">{{ Number(row.quantity || 0) > 0 ? '+' : '' }}{{ row.quantity }}</template>
          </el-table-column>
        </el-table>
        <p v-else-if="isNoOrderNew" style="margin:6px 0 0;color:var(--app-text-placeholder);font-size:var(--app-font-xs)">
          未查到委外仓入库流水（异常：工厂售后审核后应有 PRODUCT_DEFECT 转移腿，请核对库存流水）
        </p>
        <p style="margin:12px 0 0;line-height:1.6;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
          <template v-if="!isNoOrderNew">
            ③ 应付冲减：
            <b :style="{ color: Number(detail.payableAmount) < 0 ? 'var(--app-color-success)' : 'var(--app-text-regular)' }">
              {{ Number(detail.payableAmount || 0).toFixed(2) }}
            </b>
            <span v-if="detail.payableStatus">（{{ detail.payableStatus === 'UNSETTLED' ? '未付款' : detail.payableStatus === 'SETTLED' ? '已付款' : detail.payableStatus }}）</span>
            <span style="color:var(--app-text-placeholder)"> —— 负数表示冲减已生成的加工应付。</span>
          </template>
          <template v-else>
            ③ 应付：<b>本次退货不产生应付</b>（无单口径不动应付）—— 料的账在本页「登记返回」时按实际用料结转。
          </template>
        </p>
      </template>
    </el-card>

    <!-- ============ 返回记录（2026-09-27 用户口径：加工返回不再单独开单 —— 就在本页登记/撤销） ============
         与「客户售后」详情页的「维修返回」记录同范式：登记即生效 + 逐条撤销（表内撤销走对称逆回后删除）。 -->
    <el-card v-if="isNoOrderNew && detail.settled" shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">返回记录</span>
          <span style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">
            工厂修好送回时点右上角「登记返回」→ 存为<b>草稿</b>（不动库存/账务）→ 在本表点<b>审核</b>才落账
            （核销在厂 + 成品回仓 + 按实际用料扣料 + 赔料应收）；草稿可删除、已审核可反审核。
          </span>
        </div>
      </template>
      <el-table :data="returns" border stripe size="small" v-loading="returnsLoading">
        <!-- 2026-09-28（用户口径「登记返回需要审核和反审核」）：状态并进「返回单号」的第二行（tag），
             不新开列以守住本页表格宽度预算；动作按状态渲染：草稿 → 审核 / 删除；已审核 → 反审核 -->
        <el-table-column label="返回单号" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.code }}</div>
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small" style="margin-top:2px">
              {{ DocStatusLabel[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="返回数量" width="90" align="right">
          <template #default="{ row }">{{ row.quantity }}</template>
        </el-table-column>
        <el-table-column label="回仓品质" width="80" align="center">
          <template #default="{ row }">{{ specText(row.returnQualityType) }}</template>
        </el-table-column>
        <el-table-column prop="inWarehouseName" label="回仓仓库" min-width="130" show-overflow-tooltip />
        <el-table-column label="实际用料料款" width="100" align="right">
          <template #default="{ row }">
            <span :title="'赔料应收 = Σ(用料单价 × 用量)；单价默认按 FIFO 带出、可人工填写（草稿未落账为 0）'">{{ Number(row.materialAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="returnDate" label="返回日期" width="100" />
        <el-table-column prop="createByName" label="登记人" width="90" />
        <el-table-column label="审核人" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status===DocStatus.DRAFT" type="success" link @click="auditReturn(row)">审核</el-button>
            <el-button v-if="row.status===DocStatus.AUDITED" type="warning" link @click="unAuditReturn(row)">反审核</el-button>
            <el-button v-if="row.status===DocStatus.DRAFT" type="danger" link @click="revokeReturn(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span style="color:var(--app-text-placeholder)">尚未登记返回（工厂把货送回时点右上角「登记返回」）</span>
        </template>
      </el-table>
    </el-card>

    <!-- 登记返回弹窗：加工厂/产品/在厂规格由本记录带入（不可改，后端还会按来源单复核同厂同产品同规格） -->
    <el-dialog v-model="returnDlg.visible" title="登记返回（先存草稿，审核后落账）" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            本页保存为<b>草稿</b>（不动库存与账务）；在下方「返回记录」里点<b>审核</b>才落账：
            <b>核销在厂成品</b>（加工厂委外仓的「成品（加工退货）」）→ <b>修好成品回我方仓</b> →
            按<b>实际用料</b>从委外仓扣料 → 料款生成对加工厂的<b>赔料应收</b>
            （用料单价默认按 <b>FIFO</b> 带出、可人工修改，见下方用料明细）。草稿可删除、已审核可反审核。
          </span>
        </template>
      </el-alert>
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="来源退货单">{{ detail.code || ('加工退货#' + (detail.id ?? '-')) }}</el-descriptions-item>
        <el-descriptions-item label="加工厂">{{ detail.factoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ detail.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="在厂规格 / 未返回量">{{ specText(detail.qualityType) }} / {{ unreturnedQty }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="returnForm" label-width="120px" size="small">
        <el-form-item required label="返回数量">
          <el-input v-model="returnForm.quantity" type="number" placeholder="整数"
            @change="returnForm.quantity = Math.round(Number(returnForm.quantity) || 0)" />
        </el-form-item>
        <el-form-item required label="回仓仓库">
          <RemoteSelect v-model="returnForm.inWarehouseId" :fetch="fetchFinishedWarehouses"
            :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" placeholder="修好成品回仓仓库" style="width:100%" domain="warehouse" />
        </el-form-item>
        <el-form-item label="回仓品质">
          <el-select v-model="returnForm.returnQualityType" style="width:100%">
            <el-option v-for="o in specOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <!-- 实际用料只能从**本张加工退货单的 BOM 快照**里选（数量仍可超 BOM）；池空 ⇒ 允许只登记返回、不填料 -->
        <el-form-item :required="candidates.length > 0" label="实际用料明细">
          <div style="width:100%">
            <el-alert v-if="candidates.length === 0" type="warning" :closable="false" show-icon style="margin-bottom:8px">
              <template #title>
                <span style="font-size:var(--app-font-xs);line-height:1.5">
                  本退货单未绑定 BOM 快照、或该产品没有可用 BOM ⇒ 没有可选的用料。
                  本次可以<b>只登记返回、不填用料</b>（料款应收按 0）。
                </span>
              </template>
            </el-alert>
            <div v-else style="margin-bottom:8px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
              只能从<b>本加工退货单的 BOM 快照</b>里选；选料后自动带出默认用量（可改），数量可超 BOM。
              单价默认按 <b>FIFO</b> 带出、可<b>人工填写</b>具体价格（人工定价只影响对工厂的赔料应收，
              成品成本仍按 FIFO 结转）。
            </div>
            <div v-for="(it, i) in returnForm.items" :key="i" style="display:flex;gap:8px;margin-bottom:8px;align-items:center">
              <el-select v-model="it.materialId" filterable clearable style="flex:1" placeholder="从 BOM 快照里选物料"
                :disabled="candidates.length === 0" @change="onPickMaterial(it)">
                <el-option v-for="c in candidates" :key="c.materialId" :value="c.materialId"
                  :disabled="isMaterialPicked(c.materialId, it)"
                  :label="(c.materialName || ('#' + c.materialId)) + (c.unit ? ('（' + c.unit + '）') : '') + ' · 单套用量 ' + c.perSetQuantity" />
              </el-select>
              <el-input v-model="it.quantity" type="number" placeholder="用量(可超BOM)" style="width:110px"
                @change="onQtyChange(it)" />
              <!-- 2026-09-29（用户口径「可以填写具体价格，默认 FIFO 可修改」）：单价可改，人工改过后不再被默认值覆盖 -->
              <el-input v-model="it.unitPrice" type="number" placeholder="单价(默认FIFO)" style="width:120px"
                title="默认按 FIFO 带出；可人工填写具体价格（人工定价只影响对工厂的赔料应收，成品成本仍按 FIFO）"
                @input="onPriceInput(it)" />
              <span style="width:86px;text-align:right;font-weight:600" title="行料款 = 单价 × 用量">{{ lineAmount(it).toFixed(2) }}</span>
              <el-button type="danger" link @click="removeItem(i)">删除</el-button>
            </div>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <el-button type="primary" link :icon="'Plus'" :disabled="candidates.length === 0" @click="addItem">添加用料行</el-button>
              <span style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">
                赔料应收合计：<b style="color:var(--app-color-danger)">{{ returnTotal.toFixed(2) }}</b> 元
              </span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="返回日期">
          <el-date-picker v-model="returnForm.returnDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="returnForm.remark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="returnDlg.saving" @click="submitReturn">登记返回</el-button>
      </template>
    </el-dialog>
  </PageShell>
</template>

<style scoped>/* 页头已统一到全局骨架（PageShell）；原 .p 局部样式已删除 */</style>
