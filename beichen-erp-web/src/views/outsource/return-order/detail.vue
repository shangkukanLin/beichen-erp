<script setup lang="ts">
import { ref, reactive, computed, watch, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { localDate } from '@/utils/date'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_RETURN_ORDER_DIRTY_KEY, OutsourceChargeType, OutsourceChargeTypeLabel, OutsourceReturnType, OutsourceReturnTypeLabel, OutsourceReturnTypeTag, ProductQualityType, ProductQualityTypeLabel } from '@/api/enums'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useTabStore } from '@/stores/tabs'
import { applyPageTitle } from '@/utils/pageTitle'
import { invalidate } from '@/utils/dataFreshness'

/**
 * 委外加工退货详情（2026-09-24 用户口径：草稿态就地可编辑，列表不再给「编辑」）
 *
 * ⚠️ 只对**维修退货（REPAIR）**开放就地编辑，原因有二（都不是偷懒）：
 *  ① 列表里唯一带「编辑」的是 REPAIR 页签那张退货单表（DEFECT 页签是台账，本来就没有编辑入口）⇒ 口径刚好对齐；
 *  ② 加工退货（DEFECT）的「退货物料明细」是**按 BOM 快照联动派生**的（产品 × BOM 用量），编辑它必须把
 *     add.vue 里"选产品 → 取快照 → 带料"整套联动搬进详情页，会把同一套规则分叉成两处（这是最容易漂移的地方）
 *     ⇒ DEFECT 保持只读（与改造前完全一致，无行为回退）。
 * 草稿分支的字段、校验、payload 与 add.vue 的 REPAIR 路径**完全一致**：
 *  工厂(RWORK 返工费必填)、送修出库仓(我方成品仓)、送修日期、备注、收费(类型+金额>0+说明)、送修产品(数量/规格)；
 *  payload `{returnType, orderId:null, sourceDeliveryId:null, factoryId, warehouseId, returnDate, remark,
 *  chargeFlag:1, chargeType, chargeAmount, chargeReason, items:[], products[]}`（维修退货不还料 ⇒ items 恒为空）。
 */
const route = useRoute()
const router = useRouter()
const id = route.params.id as string
const detail = ref<any>({})
const loading = ref(false)
const saving = ref(false)
const tabStore = useTabStore()

/** 维修退货：不还料、必须收费；已审核（已送修）后可登记「维修返回」把修好的货入回来 */
const isRepair = computed(() => (showDraftForm.value ? form.returnType : (detail.value.returnType || OutsourceReturnType.DEFECT)) === OutsourceReturnType.REPAIR)
/** 草稿 + 维修退货 才渲染可编辑表单（DEFECT 草稿保持只读，见文件头注释②） */
const showDraftForm = computed(() => detail.value.status === DocStatus.DRAFT && (detail.value.returnType || OutsourceReturnType.DEFECT) === OutsourceReturnType.REPAIR)

// ===== 草稿态可编辑副本（白名单：单号/状态/来源收货记录/制单人 不回传） =====
const form = reactive({
  returnType: OutsourceReturnType.REPAIR as string,
  factoryId: undefined as any,
  warehouseId: undefined as any,
  returnDate: localDate(),
  remark: ''
  // 2026-09-28（用户口径）：本页草稿表单**不再有「工厂收费」** —— 维修费改到「登记维修返回」
  //   按**返回产品行**填（单价 × 数量），登记即按行挂一条对加工厂的应付，撤销该行即冲销该条。
})
/**
 * 页头标题 / 页签名**跟随实际类型**（2026-09-27 用户口径：这类页面此前一律叫"委外加工退货…"，名不符实）：
 * 路由 meta.title 是历史名「委外加工退货详情」，而本页现在主营维修退货。
 * ⚠️ ① 必须放在 `showDraftForm` / `form` **之后**：`watch(source, cb)` 会在 setup 阶段**立刻求值一次**初始值
 *      （不是 immediate 才求值），而 isRepair 依赖这两个常量 ⇒ 放前面会踩"未初始化前访问"（TDZ）把整页打白
 *      （2026-09-27 实测：deep-link 详情页整个应用白屏，控制台 `Unhandled error during execution of setup function`）。
 *   ⚠️ ② 标题**必须等数据回来**才能定（isRepair 依赖 detail.returnType）⇒ 故意**不写 immediate**：
 *      加载中沿用 meta 名（对 DEFECT 单本来就是对的），加载完对 REPAIR 单改成「客户售后详情」。
 */
const pageTitleText = computed(() => isRepair.value ? '客户售后详情' : '委外加工退货详情')
// ⚠️ 2026-09-28：这里**必须 immediate**（原为不 immediate + 依赖"计算值会变"去触发）——
//   路由 meta 已改为「客户售后详情」，而**存量 DEFECT 单**的 pageTitleText 从 setup 到加载完**始终**是
//   「委外加工退货详情」（计算值不变）⇒ 不 immediate 就永远不会把页签从 meta 名改过来，
//   出现"页头=委外加工退货详情、页签=客户售后详情"的三处不一致。immediate 后页签恒由本页决定（与 add.vue 的 onMounted 同步同效）。
watch(pageTitleText, (t) => { tabStore.updateTabTitle(route.path, t); applyPageTitle(t) }, { immediate: true })
/** 送修产品行（可改数量与规格；数量改 0 = 本次不再送修该产品） */
const editableProducts = ref<any[]>([])

// 2026-09-28（用户口径）：原「收费类型」下拉（chargeTypeOptions）随整单收费字段一起下线 ——
//   维修费改到「登记维修返回」按产品行填，见 repairRows[].repairUnitPrice / repairFeeTotal
const qualityOptions = computed(() => [ProductQualityType.A, ProductQualityType.B, ProductQualityType.C, ProductQualityType.DEFECT]
  .map(v => ({ value: v, label: ProductQualityTypeLabel[v] || v })))

// ===== 纯 Odoo 方案：本地轻量列表 + RemoteSelect 实时查库 =====
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { supplierType: 'factory', name: kw, pageSize: 500 } })
// 成品出库仓只取我方成品仓：自有仓库(INVENTORY) + 类型=成品仓，排除委外仓/不良仓/售后仓等
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED', pageSize: 500 } })

async function loadData() {
  loading.value = true
  try { detail.value = (await request.get<any, any>(`/outsource/return-order/${id}`)) || {} } finally { loading.value = false }
  if (showDraftForm.value) resetForm()
}

/** 草稿（维修退货）：用本单自己的产品行填充可编辑副本 —— **不重建行**，避免丢行 */
function resetForm() {
  const d = detail.value
  form.returnType = d.returnType || OutsourceReturnType.REPAIR
  form.factoryId = d.factoryId ?? undefined
  form.warehouseId = d.warehouseId ?? undefined
  form.returnDate = d.returnDate ? String(d.returnDate).slice(0, 10) : localDate()
  form.remark = d.remark || ''
  // 2026-09-28：收费字段不再进表单（维修费在登记返回时按产品行收）
  editableProducts.value = (d.products || []).map((p: any) => ({
    ...p,
    returnQuantity: Number(p.quantity || 0),
    qualityType: p.qualityType || ProductQualityType.DEFECT
  }))
}

/** 保存（与 add.vue 的 REPAIR 路径同一套校验与 payload；后端 PUT 自带「只有草稿可编辑」守卫） */
async function doSave() {
  if (!form.factoryId) { ElMessage.warning('请选择加工厂'); return }
  if (!form.warehouseId) { ElMessage.warning('请选择送修出库仓'); return }
  // 2026-09-28（用户口径）：草稿保存**不再校验整单维修费** —— 费用在「登记维修返回」按产品行填（见下）
  const keep = editableProducts.value.filter((p: any) => Number(p.returnQuantity) > 0)
  if (keep.length === 0) { ElMessage.warning('请选择产品并填写送修数量'); return }
  const dropped = editableProducts.value.length - keep.length
  if (dropped > 0) {
    try {
      await ElMessageBox.confirm(`有 ${dropped} 行送修数量为 0，保存后这些产品将不再送修，确认保存？`, '提示', { type: 'warning' })
    } catch { return }
  }
  saving.value = true
  try {
    await request.put(`/outsource/return-order/${id}`, {
      returnType: form.returnType,
      // 维修退货**不关联**加工单 / 来源收货记录（后端同口径拦截）
      orderId: null,
      sourceDeliveryId: null,
      factoryId: form.factoryId, warehouseId: form.warehouseId,
      returnDate: form.returnDate, remark: form.remark,
      // 2026-09-28（用户口径）：整单收费字段**原值原样回传** —— 本页已无收费输入，但存量老单若仍带
      // charge_amount>0（改造前填的），保存时不能被静默清零（audit 的兼容分支会照旧为其挂整单应付）。
      chargeFlag: Number(detail.value.chargeFlag || 0),
      chargeType: detail.value.chargeType || null,
      chargeAmount: Number(detail.value.chargeAmount || 0),
      chargeReason: detail.value.chargeReason || null,
      // 维修退货不还料：物料明细恒为空
      items: [],
      products: keep.map((p: any) => ({
        productName: p.productName,
        productMasterId: p.productMasterId || null,
        bomSnapshotId: p.bomSnapshotId || null,
        qualityType: p.qualityType || ProductQualityType.DEFECT,
        quantity: Number(p.returnQuantity)
      }))
    })
    ElMessage.success('已保存')
    invalidate('outsourceReturnOrder')
    await loadData()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

// ===== 维修返回（2026-09-17）=====
const fetchWarehousesForRepair = (kw: string) =>
  request.get('/warehouse/page', { params: { warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED', pageSize: 500 } })
const repairVisible = ref(false)
const repairSaving = ref(false)
const repairWarehouseId = ref<number>()
const repairDate = ref(localDate())
const repairRows = ref<any[]>([])
/**
 * 本次登记的**维修费合计**（Σ 单价 × 本次返回数量）—— 2026-09-28 用户口径「费用精确到产品里、在登记返回时填写」。
 * <p>提交后后端**按每行**生成一条对加工厂的应付（撤销该行即冲销该条），故这里就是"本次将挂账多少钱"的预览。</p>
 */
const repairFeeTotal = computed(() =>
  repairRows.value.reduce((s: number, r: any) => s + (Number(r.repairUnitPrice) || 0) * (Number(r.quantity) || 0), 0))
// P2-1（2026-09-25）：实际用料多行 —— 登记时从加工厂委外仓扣减、按 FIFO 结转成本，无赔料应收。
// 2026-09-27（用户口径）：**可选范围**收口到本单产品行的 **BOM 快照**（成品维修 ⇒ 用料 = 该成品的 BOM 组件）：
// 候选由后端 /repair-material-candidates 一次给出（含来源产品 + 单套用量），提交时后端用**同一份逻辑**再校验；
// **数量仍可超 BOM**（原"可超 BOM、不做 BOM 比对"只针对数量）。
const repairMaterials = ref<Array<{ materialId: any, quantity: any }>>([])
const repairCandidates = ref<any[]>([])
/** 按**来源产品**分组（同一物料被多个产品 BOM 引用时按第一个来源展示） */
const repairCandidateGroups = computed(() => {
  const map = new Map<string, any>()
  for (const c of repairCandidates.value) {
    const label = `BOM·${c.fromProductName || ('#' + c.fromProductId)}`
    if (!map.has(label)) map.set(label, { label, items: [] })
    map.get(label).items.push(c)
  }
  return [...map.values()]
})
async function loadRepairCandidates() {
  try {
    repairCandidates.value = (await request.get<any, any>(`/outsource/return-order/${id}/repair-material-candidates`)) || []
  } catch (e: any) { repairCandidates.value = []; console.warn('加载实际用料候选失败', e?.message || e) }
}
/** 选料后默认数量 = 单套用量 × 该来源产品的本次返回数量（可改；不设上限） */
function onPickRepairMaterial(m: any) {
  const c = repairCandidates.value.find((x: any) => String(x.materialId) === String(m.materialId))
  if (!c) return
  const row = repairRows.value.find((r: any) => String(r.productId) === String(c.fromProductId))
  const n = row && Number(row.quantity) > 0 ? Number(row.quantity) : 1
  m.quantity = Math.max(1, Math.round(Number(c.perSetQuantity || 0) * n))
}
/** 同一物料只允许一行（重复选会在提交时被后端拒，前端先拦） */
function isRepairMaterialPicked(mid: any, cur: any) {
  return repairMaterials.value.some((m: any) => m !== cur && String(m.materialId) === String(mid))
}
function addRepairMaterial() { repairMaterials.value.push({ materialId: undefined, quantity: undefined }) }
function removeRepairMaterial(i: number) { repairMaterials.value.splice(i, 1) }

/**
 * 打开「登记维修返回」：按送修**产品**生成行，数量默认 = 送修 − 已返回。
 * <p>数量按产品核销（不按规格）：送修的是不良品，修好回来通常是 A 规等良品，
 * 故默认"返回品质 = A规"，可改（工厂修不好时仍填不良品）。</p>
 */
async function openRepairReturn() {
  // 2026-09-28（草稿口径）：只有**已审核**的返回计入"已返回"（草稿未落账、不占额度 ⇒ 仍可继续登记）
  const returned: Record<string, number> = {}
  for (const r of (detail.value.repairReturns || []) as any[]) {
    if (r.status !== DocStatus.AUDITED) continue
    const k = String(r.productId)
    returned[k] = (returned[k] || 0) + (Number(r.quantity) || 0)
  }
  const sent: Record<string, any> = {}
  for (const p of (detail.value.products || []) as any[]) {
    const k = String(p.productId)
    // 2026-09-28：每行带「维修费单价」（元/件，可留空 = 不收费）；金额 = 单价 × 本次返回数量
    if (!sent[k]) sent[k] = { productId: p.productId, productName: p.productName, qualityType: ProductQualityType.A, sentQty: 0, returnedQty: 0, quantity: undefined, repairUnitPrice: undefined }
    sent[k].sentQty += Number(p.quantity) || 0
  }
  repairRows.value = Object.entries(sent).map(([k, v]: any) => {
    const done = returned[k] || 0
    return { ...v, returnedQty: done, quantity: Math.max(0, v.sentQty - done) }
  }).filter((r: any) => r.sentQty - r.returnedQty > 0)
  if (repairRows.value.length === 0) { ElMessage.warning('该单已全部返回，无需再登记'); return }
  repairWarehouseId.value = detail.value.warehouseId || undefined
  repairDate.value = localDate()
  await loadRepairCandidates()
  // 候选为空（本单产品行没有 BOM 快照）⇒ 不预置用料行，允许"只登记成品返回"
  repairMaterials.value = repairCandidates.value.length > 0 ? [{ materialId: undefined, quantity: undefined }] : []
  repairVisible.value = true
}

async function submitRepairReturn() {
  if (!repairWarehouseId.value) { ElMessage.warning('请选择返回入库仓'); return }
  const items = repairRows.value.filter((r: any) => Number(r.quantity) > 0)
    .map((r: any) => ({
      productId: r.productId, productName: r.productName, qualityType: r.qualityType, quantity: Number(r.quantity),
      // 2026-09-28（用户口径）：维修费随**行**提交（留空/0 = 该产品不收费，不挂应付）
      repairUnitPrice: Number(r.repairUnitPrice) || 0
    }))
  if (items.length === 0) { ElMessage.warning('请填写维修返回数量'); return }
  const materials = repairMaterials.value
    .map((m: any) => ({ materialId: m.materialId, quantity: Math.round(Number(m.quantity) || 0) }))
    .filter((m: any) => m.materialId && m.quantity > 0)
  repairSaving.value = true
  try {
    await request.post(`/outsource/return-order/${id}/repair-return`, { warehouseId: repairWarehouseId.value, repairDate: repairDate.value, items, materials })
    ElMessage.success('维修返回草稿已保存，请在下方「维修返回记录」里审核（审核后才入库并挂维修费）')
    repairVisible.value = false
    await loadData()
    invalidate('outsourceReturnOrder')
  } catch (e: any) { ElMessage.error(e?.message || '登记失败') } finally { repairSaving.value = false }
}

/**
 * ==================== 维修返回记录的 审核 / 反审核 / 删除（2026-09-28 用户口径） ====================
 * 「加工和物料的登记返回都需要审核和反审核」：登记只建**草稿**（不动库存/账务），
 * 审核才落账（成品入库 + 核销在厂 + 扣实际用料/成本 + **按行挂维修费应付**）；反审核对称逆回（先冲应付）并
 * **留痕**（回草稿）；删除只对草稿开放。与「工厂售后详情页」的返回记录、「物料维修返回」同一口径。
 */
async function auditRepairReturn(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认审核该条维修返回（${row.productName || ''} × ${row.quantity}）吗？审核后才会落账：成品入「${row.warehouseName || '我方仓'}」、核销工厂在厂成品、按实际用料扣料并结转成本，并按本行维修费 ${Number(row.repairAmount || 0).toFixed(2)} 生成对加工厂的应付。`,
      '确认审核', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/return-order/repair-return/${row.id}/audit`)
    ElMessage.success('已审核')
    await loadData()
    invalidate('outsourceReturnOrder')
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function unAuditRepairReturn(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认反审核该条维修返回（${row.productName || ''} × ${row.quantity}）吗？将对称逆回：扣回已入库成品、恢复在厂、回补实际用料并反结转成本、冲销本行维修费应付；记录回到草稿（留痕可查）。`,
      '确认反审核', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/return-order/repair-return/${row.id}/un-audit`)
    ElMessage.success('已反审核')
    await loadData()
    invalidate('outsourceReturnOrder')
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/** 删除维修返回**草稿**（草稿未落账 ⇒ 直接删；已审核的必须先「反审核」） */
async function cancelRepairReturn(row: any) {
  try { await ElMessageBox.confirm(`确认删除该条维修返回草稿（${row.productName || ''} × ${row.quantity}）？该草稿尚未落账（未动库存/账务），删除后不可恢复。`, '删除维修返回草稿', { type: 'warning' }) } catch { return }
  try {
    await request.delete(`/outsource/return-order/repair-return/${row.id}`)
    ElMessage.success('已删除草稿')
    await loadData()
    invalidate('outsourceReturnOrder')
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

async function handleAudit() {
  const tip = isRepair.value
    ? '确认审核该维修退货单？审核后成品送修出库（不冲减应付）并生成加工厂向我方收取的维修费应付'
    : '确认审核该退货单？审核后物料入工厂仓、成品出库并冲减应付'
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/audit`); ElMessage.success('已审核'); loadData(); invalidate('outsourceReturnOrder') } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit() {
  const tip = isRepair.value
    ? '确认反审核？将送修成品回我方仓并冲销维修费应付（若有已审核的维修返回记录需先「反审核」；已结案需先撤销结案）'
    : '确认反审核？将逆向库存并冲销应付'
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/un-audit`); ElMessage.success('已反审核'); loadData(); invalidate('outsourceReturnOrder') } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/** 结案（仅维修退货）：工厂把送修成品全部送回（未返回=0）后收尾 */
async function handleClose() {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/审核/反审核维修返回，也不能反审核本单（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/close`); ElMessage.success('已结案'); loadData(); invalidate('outsourceReturnOrder') } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen() {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/re-open`); ElMessage.success('已撤销结案'); loadData(); invalidate('outsourceReturnOrder') } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/cancel`); ElMessage.success('已作废'); loadData(); invalidate('outsourceReturnOrder') } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

// 业务数据放在 onActivated 加载：layout 用 keep-alive 缓存页面，再次进入详情页会复用组件、
// onMounted 不再触发，只靠 onMounted 会停留在上次缓存的状态
onActivated(loadData)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题 → 右端操作
       （标题 2026-09-27 起按单据类型动态取：维修退货=客户售后详情 / 加工退货=委外加工退货详情） -->
  <PageShell :loading="loading" :title="pageTitleText" back-fallback="/outsource/return-order">
    <template #actions>
      <!-- 草稿（维修退货）：保存(主) + 审核 + 作废（2026-09-24 用户口径：草稿态就地编辑，不再跳独立编辑页） -->
      <el-button type="primary" v-if="showDraftForm" :loading="saving" @click="doSave">保存</el-button>
      <el-button v-perm="'outsource:return-order'" type="success" v-if="detail.status===DocStatus.DRAFT" @click="handleAudit">审核</el-button>
      <el-button v-perm="'outsource:return-order'" type="warning" v-if="detail.status===DocStatus.AUDITED && detail.closedFlag!==1" @click="handleUnAudit">反审核</el-button>
      <el-button v-perm="'outsource:return-order'" type="danger" v-if="detail.status===DocStatus.DRAFT" @click="handleCancel">作废</el-button>
      <!-- 维修返回：维修退货单审核（已送修）后登记工厂修好送回的成品入库；已结案则关闭入口 -->
      <el-button type="primary" v-if="isRepair && detail.status===DocStatus.AUDITED && detail.closedFlag!==1" @click="openRepairReturn">登记维修返回</el-button>
      <!-- 结案 / 撤销结案（仅维修退货，2026-09-17）：工厂把送修成品全部送回（未返回=0）后收尾 -->
      <el-button v-perm="'outsource:return-order'" type="success" v-if="isRepair && detail.status===DocStatus.AUDITED && detail.closedFlag!==1 && Number(detail.unreturnedQty)===0" @click="handleClose">结案</el-button>
      <el-button v-perm="'outsource:return-order'" type="warning" v-if="isRepair && detail.closedFlag===1" @click="handleReOpen">撤销结案</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <span style="font-weight:600">{{ pageTitleText }}</span>
      </template>

      <!-- ============ 草稿（维修退货）：可编辑（字段/校验/payload 与 add.vue 的 REPAIR 路径一致） ============
           ⚠️ label-width 用 **lg 档**（2026-09-27 用户实测）：本表单是默认字号，"送修出库仓" 5 字 + 必填星号
           在 90px 下正好卡边缘，字体略宽就被挤成两行（"仓"掉到第二行）⇒ 104px 一行显示。 -->
      <el-form v-if="showDraftForm" :model="form" label-width="var(--app-label-width-lg)">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="退货单号">{{ detail.code }}</el-form-item></el-col>
          <el-col :span="8"><el-form-item label="退货类型">
            <el-tag :type="OutsourceReturnTypeTag[form.returnType] || 'info'" size="small">{{ OutsourceReturnTypeLabel[form.returnType] || form.returnType }}</el-tag>
            <span style="margin-left:6px;color:#909399;font-size:var(--app-font-xs)">类型不可改（后端同口径拦截）</span>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态"><el-tag :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item required label="加工厂">
              <RemoteSelect v-model="form.factoryId" :fetch="fetchSuppliers" label-key="name" placeholder="实时查库（加工厂）" style="width:100%" domain="supplier" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="送修出库仓">
              <!-- 2026-09-27 修复：原先漏了绑定冒号（label-key="(row:any)=>…" 是**静态字符串**），
                   RemoteSelect 的 getLabel 会去取 row["(row:any)=>row.warehouseName"] ⇒ undefined ⇒
                   下拉与回显全部退化成显示 **value（仓库 ID）**。必须写成 `:label-key="函数"`。 -->
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="我方成品仓" style="width:100%" domain="warehouse" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="送修日期"><el-input v-model="form.returnDate" type="date" /></el-form-item>
          </el-col>
          <!-- 2026-09-28（用户口径）：本页不再填「工厂收费」—— 维修费在**登记维修返回**时按返回产品行填
               （单价 × 数量），登记即按行生成对加工厂的应付；撤销该行即冲销该条。 -->
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <!-- ============ 已审核 / 已作废 / 加工退货草稿：只读（原口径原样保留） ============ -->
      <el-descriptions v-else :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="退货类型">
          <el-tag :type="OutsourceReturnTypeTag[detail.returnType || 'DEFECT'] || 'info'" size="small">
            {{ OutsourceReturnTypeLabel[detail.returnType || 'DEFECT'] || detail.returnType }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="加工厂">{{ detail.factoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联加工单">
          <span v-if="detail.orderCode">{{ detail.orderCode }}</span>
          <span v-else style="color:var(--app-text-placeholder)">{{ isRepair ? '不关联（维修退货）' : '未关联' }}</span>
        </el-descriptions-item>
        <!-- 来源收货记录：从「成品收货」按记录发起退货时才有（2026-09-17） -->
        <el-descriptions-item label="来源收货记录">{{ detail.sourceDeliveryId ? ('#' + detail.sourceDeliveryId) : '-' }}</el-descriptions-item>
        <!-- 出库/扣减仓库（2026-09-27 补）：维修退货=送修出库仓；加工退货=扣减成品仓。
             此前只读区**完全没有这一项**（只有草稿编辑态才有下拉），审核后就看不到从哪个仓出库了。 -->
        <el-descriptions-item :label="isRepair ? '送修出库仓' : '扣减成品仓'">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ $fmtDate(detail.returnDate) }}</el-descriptions-item>
        <el-descriptions-item label="送修/已返回" v-if="isRepair">
          {{ (detail.products || []).reduce((s: number, p: any) => s + (Number(p.quantity) || 0), 0) }} /
          <span style="color:var(--app-color-success);font-weight:600">{{ Number(detail.repairReturnedQty || 0) }}</span>
          <span v-if="Number(detail.unreturnedQty) > 0" style="margin-left:8px;color:var(--app-color-warning)">未返回 {{ detail.unreturnedQty }}</span>
          <span v-else style="margin-left:8px;color:var(--app-color-success)">已全部返回</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <!-- 已结案直接显示「已结案」（替代"已审核"），2026-09-17 -->
          <el-tag v-if="isRepair && detail.closedFlag===1" type="success" size="small">已结案</el-tag>
          <el-tag v-else :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="结案" v-if="isRepair && detail.closedFlag===1">
          {{ detail.closedBy || '-' }}
          <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ $fmtDate(detail.closedTime) }}</span>
        </el-descriptions-item>
        <!-- 2026-09-28（用户口径）：维修费改为按**返回产品行**、在「登记维修返回」时收 ⇒ 这里显示已登记合计
             （明细见下方「维修返回记录」表）；存量老单若仍带整单收费（charge_flag=1）照旧并列显示，历史数据不消失。 -->
        <el-descriptions-item label="维修费" v-if="isRepair">
          <span v-if="Number(detail.repairFeeTotal || 0) > 0" style="color:var(--app-color-warning);font-weight:600">{{ Number(detail.repairFeeTotal).toFixed(2) }}</span>
          <span v-else style="color:var(--app-text-placeholder)">-</span>
          <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">按返回产品行登记</span>
        </el-descriptions-item>
        <el-descriptions-item label="工厂收费（历史整单）" v-if="Number(detail.chargeFlag) === 1 && Number(detail.chargeAmount) > 0">
          <span style="color:var(--app-color-warning);font-weight:600">{{ Number(detail.chargeAmount).toFixed(2) }}</span>
          <span style="margin-left:6px;color:var(--app-text-secondary)">{{ OutsourceChargeTypeLabel[String(detail.chargeType)] || detail.chargeType || '' }}{{ detail.chargeReason ? '（' + detail.chargeReason + '）' : '' }}</span>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 退货成品（送修内容）：维修退货没有物料明细，这一块才是它的主要内容（2026-09-17 补全） -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <span style="font-weight:600">{{ isRepair ? '送修成品明细' : '退货成品明细' }}</span>
        <span v-if="showDraftForm" style="font-weight:normal;color:#909399;margin-left:8px">
          数量/规格可直接改；把某行数量改成 0 表示本次不再送修该产品
        </span>
      </template>

      <!-- 草稿（维修退货）：可编辑（产品行不重建 —— 只改数量/规格，避免丢行） -->
      <el-table v-if="showDraftForm" :data="editableProducts" border size="small">
        <el-table-column label="产品名称" min-width="160"><template #default="{row}">{{ row.productName || ('#' + row.productId) }}</template></el-table-column>
        <el-table-column label="规格" width="110" align="center">
          <template #default="{row}">
            <el-select v-model="row.qualityType" size="small" style="width:100%">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="BOM快照" width="90" align="center">
          <template #default="{row}">
            <span v-if="row.bomSnapshotId">{{ row.bomVersion != null ? ('v' + row.bomVersion) : '已关联' }}</span>
            <span v-else style="color:var(--app-text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="送修数量" width="130">
          <template #default="{row}">
            <el-input-number v-model="row.returnQuantity" size="small" :min="0" :controls="false" :precision="0" :step="1" style="width:100%" />
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="detail.products || []" border size="small">
        <el-table-column label="产品名称" min-width="160"><template #default="{row}">{{ row.productName || ('#' + row.productId) }}</template></el-table-column>
        <el-table-column label="规格" width="90" align="center">
          <template #default="{row}">{{ ProductQualityTypeLabel[row.qualityType || 'A'] || row.qualityType }}</template>
        </el-table-column>
        <!-- 所用 BOM 快照（2026-09-17）：追溯"这单按哪份 BOM 用量退的料" -->
        <el-table-column label="BOM快照" width="90" align="center">
          <template #default="{row}">
            <span v-if="row.bomSnapshotId">{{ row.bomVersion != null ? ('v' + row.bomVersion) : '已关联' }}</span>
            <span v-else style="color:var(--app-text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="isRepair ? '送修数量' : '退回数量'" width="110" align="right"><template #default="{row}">{{ row.quantity }}</template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top:12px" v-if="!isRepair">
      <template #header><span style="font-weight:600">退货物料明细（按 BOM 快照还料）</span></template>
      <el-table :data="detail.items || []" border size="small">
        <el-table-column label="物料名称" min-width="160"><template #default="{row}">{{ row.materialName || row.materialId }}</template></el-table-column>
        <el-table-column label="物料类型" width="100"><template #default="{row}">{{ row.materialTypeName || '-' }}</template></el-table-column>
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right" />
        <el-table-column prop="amount" label="金额" width="110" align="right" />
      </el-table>
      <div v-if="!(detail.items || []).length" style="color:var(--app-text-secondary);font-size:var(--app-font-xs);padding:8px 0">
        该产品未带出退货物料（包工包料或无 BOM 快照），审核时只做成品出库、不冲减应付。
      </div>
    </el-card>

    <!-- 维修返回记录 -->
    <el-card shadow="never" style="margin-top:12px" v-if="isRepair">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px">
          <span style="font-weight:600">维修返回记录
            <span v-if="detail.closedFlag===1" style="margin-left:6px;font-size:var(--app-font-xs);color:var(--app-color-success)">（已结案）</span>
          </span>
          <span style="flex:1;text-align:right;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
            工厂修好送回时点「登记维修返回」→ 存为<b>草稿</b>（不动库存/账务）→ 在本表点<b>审核</b>才落账
            （成品入库 + 核销在厂 + 扣实际用料/成本 + 按行挂维修费应付）；草稿可删除、已审核可反审核。
          </span>
          <el-button type="primary" size="small" v-if="detail.status===DocStatus.AUDITED && detail.closedFlag!==1" @click="openRepairReturn">登记维修返回</el-button>
        </div>
      </template>
      <el-table :data="detail.repairReturns || []" border size="small">
        <!-- 2026-09-28（用户口径「登记返回需要审核和反审核」）：状态并入「返回日期」第二行（tag，不新开列以守住表宽），
             动作按状态渲染：草稿 → 审核 / 删除；已审核 → 反审核。 -->
        <el-table-column label="返回日期" width="110">
          <template #default="{row}">
            <div>{{ $fmtDate(row.repairDate) }}</div>
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small" style="margin-top:2px">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="产品名称" min-width="150"><template #default="{row}">{{ row.productName || ('#' + row.productId) }}</template></el-table-column>
        <el-table-column label="品质" width="90" align="center"><template #default="{row}">{{ ProductQualityTypeLabel[row.qualityType || 'A'] || row.qualityType }}</template></el-table-column>
        <el-table-column label="返回数量" width="100" align="right"><template #default="{row}"><span style="color:var(--app-color-success);font-weight:500">{{ row.quantity }}</span></template></el-table-column>
        <!-- P2-1：实际用料汇总（明细金额 = FIFO 快照合计；草稿未落账/旧行无用料显示 —） -->
        <el-table-column label="实际用料" min-width="140" show-overflow-tooltip>
          <template #default="{row}">
            <span v-if="row.materialSummary">{{ row.materialSummary }}<span style="margin-left:6px;color:var(--app-text-secondary)">{{ Number(row.materialAmount || 0).toFixed(2) }}</span></span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <!-- 2026-09-28（用户口径）：本行维修费（**登记返回时按产品行填**；留空=不收费 ⇒ —）；
             2026-09-28 草稿口径：**审核该行时**才按此金额生成一条对加工厂的应付，反审核/删除草稿即冲销该条。 -->
        <el-table-column label="维修费" width="140" align="right">
          <template #default="{row}">
            <span v-if="Number(row.repairAmount) > 0">
              {{ Number(row.repairAmount).toFixed(2) }}
              <span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">（{{ Number(row.repairUnitPrice || 0).toFixed(2) }} × {{ row.quantity }}）</span>
            </span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="90" show-overflow-tooltip />
        <el-table-column label="审核人" width="80" show-overflow-tooltip><template #default="{row}">{{ row.auditorName || '-' }}</template></el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{row}">
            <!-- 2026-09-28：已结案时**隐藏**行内动作（与全页"结案后不再受理返回动作"同口径；后端也一律拦截）。
                 刻意用 v-if 而非 disabled：全站结案/作废类动作都是"消失"，且守卫按"按钮是否存在"判定。 -->
            <el-button v-perm="'outsource:return-order'" v-if="row.status===DocStatus.DRAFT && detail.closedFlag!==1" type="success" link size="small" @click="auditRepairReturn(row)">审核</el-button>
            <el-button v-perm="'outsource:return-order'" v-if="row.status===DocStatus.AUDITED && detail.closedFlag!==1" type="warning" link size="small" @click="unAuditRepairReturn(row)">反审核</el-button>
            <el-button v-if="row.status===DocStatus.DRAFT && detail.closedFlag!==1" type="danger" link size="small" @click="cancelRepairReturn(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="(detail.repairReturns || []).length" style="margin-top:8px;text-align:right;font-size:var(--app-font-xs)">
        维修费合计：<b>{{ Number(detail.repairFeeTotal || 0).toFixed(2) }}</b>
        <span style="color:var(--app-text-secondary);margin-left:6px">（按返回产品行累加，已挂对加工厂的应付）</span>
      </div>
      <div v-if="!(detail.repairReturns || []).length" style="color:var(--app-text-secondary);font-size:var(--app-font-xs);padding:8px 0">
        {{ detail.status!==DocStatus.AUDITED ? '审核（送修）后即可登记维修返回。' : (detail.closedFlag===1 ? '已结案（无维修返回记录）。' : '尚未登记维修返回（工厂修好送回后登记，登记先存草稿、审核后才入库）。') }}
      </div>
      <div v-else-if="Number(detail.unreturnedQty) > 0" style="color:var(--app-color-warning);font-size:var(--app-font-xs);padding:8px 0">
        还有 {{ detail.unreturnedQty }} 件未返回（工厂尚未修好送回）；全部返回后可结案。
      </div>
    </el-card>

    <!-- 登记维修返回弹窗（2026-09-28 用户口径：只存草稿，审核才落账） -->
    <el-dialog v-model="repairVisible" title="登记维修返回（先存草稿，审核后落账）" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            本页保存为<b>草稿</b>（不动库存与账务）；在下方「维修返回记录」里点<b>审核</b>才落账：
            成品入「入库仓库」→ 核销工厂在厂成品 → 按<b>实际用料</b>从委外仓扣料并结转成本 →
            按本行<b>维修费</b>生成对加工厂的应付。草稿可删除、已审核可反审核。
          </span>
        </template>
      </el-alert>
      <el-form label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item required label="入库仓库"><RemoteSelect v-model="repairWarehouseId" :fetch="fetchWarehousesForRepair" :label-key="(row:any)=>row.warehouseName" placeholder="选择返回入库的我方成品仓" style="width:100%" domain="warehouse" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="返回日期"><el-input v-model="repairDate" type="date" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <el-table :data="repairRows" border size="small">
        <el-table-column label="产品" min-width="150"><template #default="{row}">{{ row.productName }}</template></el-table-column>
        <el-table-column label="规格" width="110">
          <template #default="{row}">
            <el-select v-model="row.qualityType" size="small" style="width:100%">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="送修 / 已返回" width="130" align="center"><template #default="{row}">{{ row.sentQty }} / {{ row.returnedQty }}</template></el-table-column>
        <el-table-column label="本次返回" width="120">
          <template #default="{row}">
            <el-input-number v-model="row.quantity" size="small" :min="0" :max="row.sentQty - row.returnedQty" :controls="false" :precision="0" :step="1" style="width:100%" />
          </template>
        </el-table-column>
        <!-- 2026-09-28（用户口径「维修费要精确到产品里，在登记返回时填写」）：**按返回产品行**填单价，
             金额 = 单价 × 本次返回数量；留空 = 该产品不收费（不挂应付）。 -->
        <el-table-column label="维修费单价" width="140">
          <template #default="{row}">
            <el-input-number v-model="row.repairUnitPrice" size="small" :min="0" :precision="2" :controls="false" style="width:100%" placeholder="留空=不收费" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{row}">{{ ((Number(row.repairUnitPrice) || 0) * (Number(row.quantity) || 0)).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
      <div style="margin-top:8px;text-align:right;font-size:var(--app-font-xs)">
        维修费合计：<b>{{ repairFeeTotal.toFixed(2) }}</b>
        <span style="color:var(--app-text-secondary);margin-left:6px">（<b>审核该行时</b>按行生成对加工厂的应付；反审核/删除草稿即冲销该条）</span>
      </div>
      <!-- P2-1：实际用料（**只能从本单产品的 BOM 里选**，数量仍可超；按实际耗用记账，
           **审核时**从加工厂委外仓扣料并按 FIFO 结转成本，无赔料应收） -->
      <div style="margin-top:12px;font-weight:600;margin-bottom:6px">实际用料
        <span style="font-weight:400;font-size:var(--app-font-xs);color:var(--app-text-secondary)">（只能从本单产品的 <b>BOM</b> 里选；数量可超 BOM；<b>审核时</b>从加工厂委外仓扣减，无赔料应收）</span>
      </div>
      <el-alert v-if="repairCandidates.length === 0" type="warning" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            本单产品行没有 BOM 快照 ⇒ 没有可选的用料。本次可以<b>只登记成品返回、不填用料</b>；
            若确有补料，请先确认该产品的 BOM（BOM 决定"能用哪些料"）。
          </span>
        </template>
      </el-alert>
      <div v-for="(m, i) in repairMaterials" :key="i" style="display:flex;gap:8px;margin-bottom:8px">
        <el-select v-model="m.materialId" filterable clearable style="flex:1" placeholder="从本单 BOM 里选物料"
          :disabled="repairCandidates.length === 0" @change="onPickRepairMaterial(m)">
          <el-option-group v-for="g in repairCandidateGroups" :key="g.label" :label="g.label">
            <el-option v-for="c in g.items" :key="c.materialId" :value="c.materialId"
              :disabled="isRepairMaterialPicked(c.materialId, m)"
              :label="(c.materialName || ('#' + c.materialId)) + (c.unit ? ('（' + c.unit + '）') : '') + ' · 单套用量 ' + c.perSetQuantity" />
          </el-option-group>
        </el-select>
        <el-input v-model="m.quantity" type="number" placeholder="用量" style="width:150px" @change="m.quantity = Math.round(Number(m.quantity) || 0)" />
        <el-button type="danger" link @click="removeRepairMaterial(i)">删除</el-button>
      </div>
      <el-button type="primary" link :icon="'Plus'" :disabled="repairCandidates.length === 0" @click="addRepairMaterial">添加用料行</el-button>
      <template #footer>
        <el-button @click="repairVisible = false">取消</el-button>
        <el-button type="primary" :loading="repairSaving" @click="submitRepairReturn">确认登记（存草稿）</el-button>
      </template>
    </el-dialog>
  </PageShell>
</template>
