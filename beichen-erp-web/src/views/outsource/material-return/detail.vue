<script setup lang="ts">
import { ref, reactive, computed, watch, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageShell from '@/components/PageShell.vue'
import { useTabStore } from '@/stores/tabs'
import { applyPageTitle } from '@/utils/pageTitle'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { localDate } from '@/utils/date'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel, MaterialReturnTypeTag, MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag } from '@/api/enums'
import { invalidate } from '@/utils/dataFreshness'

/**
 * 委外物料退货详情（2026-09-24 用户口径：草稿态就地可编辑，列表不再给「编辑」）
 *
 * 结构对齐其它单据详情：`head` = 只读快照，`form`/`items` = 可编辑副本（仅草稿态）。
 * 草稿分支的字段、校验、payload 与 add.vue **完全一致**（类型 / 供应商 / 出库源仓 / 日期 / 备注 / 关联物料订单 + 明细数量·单价）。
 * ⚠️ 「关联物料订单」只对**本单已挂订单**的历史单渲染（2026-09-29 用户口径「工厂维修不需要关联订单」）。
 *
 *
 * ⚠️ 关键差异（比 add.vue 更保守，故意的）：add.vue 的编辑态是"按**源仓当前库存**重建明细行再回填"，
 * 它的注释已明确警告「源仓当前库存里已没有该物料的（例如被别的单占掉）也要带上，否则一保存就会把这行**静默删掉**」。
 * 本页**不重建明细**：直接拿本单自己的 items 改数量/单价 ⇒ 天然不会丢行；只有用户自己把某行数量改成 0 才会不再退回，
 * 且这种情况会**先弹确认**（杜绝静默删除）。
 */
const route = useRoute()
const router = useRouter()
const id = route.params.id as string
const detail = ref<any>({})
const loading = ref(false)
const saving = ref(false)
const tabStore = useTabStore()

/** 维修返回（2026-09-17 立，术语 2026-09-28 统一）：审核=送修出库（不冲应付）；供应商修好后「登记维修返回」把物料入回来 */
const isRepair = computed(() => (isDraft.value ? form.returnType : (detail.value.returnType || MaterialReturnType.REFUND)) === MaterialReturnType.REPAIR)
/** 订单退料（2026-09-28 新增类型）：只扣源仓 + 扣该订单出货/收料数，不动账务、不跟踪返回、无结案 */
const isOrderReturn = computed(() => (isDraft.value ? form.returnType : (detail.value.returnType || MaterialReturnType.REFUND)) === MaterialReturnType.ORDER)
const isDraft = computed(() => detail.value.status === DocStatus.DRAFT)

// ===== 草稿态可编辑副本（白名单：单号/状态/来源收料单/制单人 不回传） =====
const form = reactive({
  returnType: MaterialReturnType.REFUND as string,
  supplierId: undefined as any,
  fromWarehouseId: undefined as any,
  returnDate: localDate(),
  remark: '',
  /** 关联物料订单（仅历史关联单会有值；2026-09-29 起工厂维修不挂订单） */
  materialOrderId: undefined as any
})
/**
 * 页头标题 / 页签名 / 浏览器标题 / 卡片标题**四处同源**，跟随**类型**。
 * <p>沿革：2026-09-27 立（跟类型）→ 2026-09-28 按"关联/无单"细分 4 种 → **2026-09-29 用户口径
 * （目录改「物料售后」、叶子改 工厂维修/退货退款、单据名一起改）**：工厂维修=**工厂维修详情** /
 * 退货退款=**退货退款详情**；历史「订单退料」单仍显示 **订单退料详情**（该类型不再新建）。</p>
 * <p>⚠️ ① 必须放在 `isDraft` / `form` / `detail` **之后**：`watch(source, cb)` 在 setup 阶段会**立刻求值一次**
 * 初始值，而 isRepair 依赖这些常量 ⇒ 放前面会踩 TDZ 把整页打白（2026-09-27 实测，与加工侧同一坑）。</p>
 */
const pageTitleText = computed(() => {
  if (isOrderReturn.value) return '订单退料详情'
  if (isRepair.value) return '工厂维修详情'
  return '退货退款详情'
})
/**
 * 返回落点（2026-09-29 叶子=类型）：**回这张单类型所在的叶子** ——
 * 工厂维修 ⇒ `/outsource/material-return/repair`；退货退款 ⇒ `/outsource/material-return/unlinked`。
 */
const backFallback = computed(() => (isRepair.value
  ? '/outsource/material-return/repair' : '/outsource/material-return/unlinked'))
/** 同步顶部页签 + 浏览器标签页标题（页头/卡片由模板绑定同一个 computed） */
function syncTitle() {
  tabStore.updateTabTitle(route.path, pageTitleText.value)
  applyPageTitle(pageTitleText.value)
}
watch(pageTitleText, syncTitle)

/**
 * 退回对象 / 维修供应商 实时查库（Odoo 风格）。
 * <p>2026-09-21（用户口径）：**物料退货只允许退给辅料商 + 供应商，不能退给供货商** ⇒
 * `excludeSupplierType: 'product'`（供货商=成品商 product；其余 辅料商/方案商/加工厂 都放行）。
 */
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { pageSize: 500, name: kw, excludeSupplierType: 'product' } })
/** 该物料商的物料订单（生产中/已结单）：生产中的单审核会扣减收料数，已结单的靠本单跟踪 */
const fetchMaterialOrders = (kw: string) => request.get('/outsource/material-return/material-orders', {
  params: {
    pageSize: 500, code: kw || undefined,
    supplierId: form.supplierId || undefined,
    statuses: `${MaterialOrderStatus.RECEIVING},${MaterialOrderStatus.FINISHED}`
  }
})
/** 状态可能缺失（preset 回填项只带 id/code）→ 缺状态时只显示单号，避免出现"（undefined）" */
function materialOrderLabel(o: any) {
  const st = o && o.status ? (MaterialOrderStatusLabel[o.status] || o.status) : ''
  return st ? `${o.code}（${st}）` : `${o.code}`
}

// ===== 维修返回（对齐加工退货的维修返回实现）=====
const warehouseOptions = ref<any[]>([])
const repairVisible = ref(false)
const repairSaving = ref(false)
const repairWarehouseId = ref<number>()
const repairDate = ref(localDate())
const repairRows = ref<any[]>([])
// 2026-09-25 物料形态化：实际用料（子物料补料）多行——供应商维修主物料时实际耗用的子物料，
// 登记时从该供应商委外仓按 FIFO 扣账（允许扣负），成本结转到回仓主物料；无赔料应收。
// 2026-09-27（用户口径）：**可选范围**收口到"送修物料自己的子物料"（outsource_material_component）：
// 候选由后端 /repair-material-candidates 一次给出（含来源物料 + 用量），提交时后端用**同一份逻辑**再校验；
// **数量仍可超**（原"可超 BOM"只针对数量）。
const repairMaterials = ref<Array<{ materialId: any, quantity: any }>>([])
const repairCandidates = ref<any[]>([])
/** 按**来源物料**分组 */
const repairCandidateGroups = computed(() => {
  const map = new Map<string, any>()
  for (const c of repairCandidates.value) {
    const label = `子物料·${c.fromMaterialName || ('#' + c.fromMaterialId)}`
    if (!map.has(label)) map.set(label, { label, items: [] })
    map.get(label).items.push(c)
  }
  return [...map.values()]
})
async function loadRepairCandidates() {
  try {
    repairCandidates.value = (await request.get<any, any>(`/outsource/material-return/${id}/repair-material-candidates`)) || []
  } catch (e: any) { repairCandidates.value = []; console.warn('加载实际用料候选失败', e?.message || e) }
}
/** 选料后默认数量 = 子物料用量 × 该来源物料的本次返回数量（可改；不设上限） */
function onPickRepairMaterial(m: any) {
  const c = repairCandidates.value.find((x: any) => String(x.materialId) === String(m.materialId))
  if (!c) return
  const row = repairRows.value.find((r: any) => String(r.materialId) === String(c.fromMaterialId))
  const n = row && Number(row.quantity) > 0 ? Number(row.quantity) : 1
  m.quantity = Math.max(1, Math.round(Number(c.quantity || 0) * n))
}
/** 同一物料只允许一行 */
function isRepairMaterialPicked(mid: any, cur: any) {
  return repairMaterials.value.some((m: any) => m !== cur && String(m.materialId) === String(mid))
}
function addRepairMaterial() { repairMaterials.value.push({ materialId: undefined, quantity: undefined }) }
function removeRepairMaterial(i: number) { repairMaterials.value.splice(i, 1) }

async function loadWarehouseOptions() {
  // F7-129（2026-09-20）：加载失败不再静默 —— 留痕，避免"空下拉"被误认为"没有数据"
  try { const r = await request.get<any, any>('/outsource/material-return/warehouse-options'); warehouseOptions.value = r || [] } catch (e: any) { console.warn('加载仓库选项失败', e?.message || e) }
}

/** 打开「登记维修返回」：按送修**物料**生成行，数量默认 = 送修 − 已返回（物料库存只有良品一档） */
async function openRepairReturn() {
  // 2026-09-28（草稿口径）：只有**已审核**的返回计入"已返回"（草稿未落账、不占额度 ⇒ 仍可继续登记）
  const returned: Record<string, number> = {}
  for (const r of (detail.value.repairReturns || []) as any[]) {
    if (r.status !== DocStatus.AUDITED) continue
    const k = String(r.materialId)
    returned[k] = (returned[k] || 0) + (Number(r.quantity) || 0)
  }
  const sent: Record<string, any> = {}
  for (const it of (detail.value.items || []) as any[]) {
    const k = String(it.materialId)
    if (!sent[k]) sent[k] = { materialId: it.materialId, materialName: it.materialName, unit: it.unit, sentQty: 0, returnedQty: 0, quantity: undefined }
    sent[k].sentQty += Number(it.quantity) || 0
  }
  repairRows.value = Object.entries(sent).map(([k, v]: any) => {
    const done = returned[k] || 0
    return { ...v, returnedQty: done, quantity: Math.max(0, v.sentQty - done) }
  }).filter((r: any) => r.sentQty - r.returnedQty > 0)
  if (repairRows.value.length === 0) { ElMessage.warning('该单已全部返回，无需再登记'); return }
  // 默认入库仓 = 该单出库源仓，可改（物料可能在委外仓或自有物料仓，故不限仓型）
  repairWarehouseId.value = detail.value.fromWarehouseId || undefined
  repairDate.value = localDate()
  await loadRepairCandidates()
  // 候选为空（送修物料没维护子物料）⇒ 不预置用料行，允许"只登记物料返回"
  repairMaterials.value = repairCandidates.value.length > 0 ? [{ materialId: undefined, quantity: undefined }] : []
  repairVisible.value = true
}

async function submitRepairReturn() {
  if (!repairWarehouseId.value) { ElMessage.warning('请选择返回入库仓'); return }
  const items = repairRows.value.filter((r: any) => Number(r.quantity) > 0)
    .map((r: any) => ({ materialId: r.materialId, unit: r.unit, quantity: Number(r.quantity) }))
  if (items.length === 0) { ElMessage.warning('请填写维修返回数量'); return }
  const materials = repairMaterials.value
    .map((m: any) => ({ materialId: m.materialId, quantity: Math.round(Number(m.quantity) || 0) }))
    .filter((m: any) => m.materialId && m.quantity > 0)
  repairSaving.value = true
  try {
    await request.post(`/outsource/material-return/${id}/repair-return`, { warehouseId: repairWarehouseId.value, repairDate: repairDate.value, items, materials })
    ElMessage.success('维修返回草稿已保存，请在下方「维修返回记录」里审核（审核后才入库）')
    repairVisible.value = false
    await loadData()
    invalidate('outsourceMaterialReturn')
  } catch (e: any) { ElMessage.error(e?.message || '登记失败') } finally { repairSaving.value = false }
}

/**
 * ==================== 维修返回记录的 审核 / 反审核 / 删除（2026-09-28 用户口径） ====================
 * 「加工和物料的登记返回都需要审核和反审核」：登记只建**草稿**（不动库存/账务），
 * 审核才落账（物料入库 + 核销在厂 + 订单收料数回补 + 实际用料/成本结转）；反审核对称逆回并**留痕**（回草稿）；
 * 删除只对草稿开放。与「加工退货详情页」的返回记录同一口径。
 */
async function auditRepairReturn(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认审核该条维修返回（${row.materialName || ''} × ${row.quantity}）吗？审核后才会落账：物料入「${row.warehouseName || '入库仓'}」、核销供应商在厂、回补订单收料数，并按实际用料扣子物料与结转成本。`,
      '确认审核', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/material-return/repair-return/${row.id}/audit`)
    ElMessage.success('已审核')
    await loadData()
    invalidate('outsourceMaterialReturn')
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function unAuditRepairReturn(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认反审核该条维修返回（${row.materialName || ''} × ${row.quantity}）吗？将对称逆回：扣回已入库物料、恢复在厂、回补实际用料并反结转成本、订单收料数退回；记录回到草稿（留痕可查）。`,
      '确认反审核', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/material-return/repair-return/${row.id}/un-audit`)
    ElMessage.success('已反审核')
    await loadData()
    invalidate('outsourceMaterialReturn')
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/** 删除维修返回**草稿**（草稿未落账 ⇒ 直接删；已审核的必须先「反审核」） */
async function cancelRepairReturn(row: any) {
  try { await ElMessageBox.confirm(`确认删除该条维修返回草稿（${row.materialName || ''} × ${row.quantity}）？该草稿尚未落账（未动库存/账务），删除后不可恢复。`, '删除维修返回草稿', { type: 'warning' }) } catch { return }
  try {
    await request.delete(`/outsource/material-return/repair-return/${row.id}`)
    ElMessage.success('已删除草稿')
    await loadData()
    invalidate('outsourceMaterialReturn')
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

async function loadData() {
  loading.value = true
  try { detail.value = (await request.get<any, any>(`/outsource/material-return/${id}`)) || {} } finally { loading.value = false }
  if (isDraft.value) resetForm()
  // 每次数据到位都显式同步标题：无单退料这类"加载前后同名"的场景 watch 不会触发，
  // 只靠 watch 会让页签/浏览器标题停在路由 meta.title（用户实测报回的不一致之一）
  syncTitle()
}

/** 草稿：用本单自己的明细填充可编辑副本（**不重建**，见文件头注释） */
function resetForm() {
  const d = detail.value
  form.returnType = d.returnType || MaterialReturnType.REFUND
  form.supplierId = d.supplierId ?? undefined
  form.fromWarehouseId = d.fromWarehouseId ?? undefined
  form.returnDate = d.returnDate ? String(d.returnDate).slice(0, 10) : localDate()
  form.remark = d.remark || ''
  form.materialOrderId = d.materialOrderId ?? undefined
  editableItems.value = (d.items || []).map((it: any) => ({
    ...it,
    returnQuantity: Number(it.quantity || 0),
    // 后端 unitPrice 为空 = 由后端按 FIFO 自动计价（add.vue 也保留空串），不要强行补 0
    unitPrice: it.unitPrice === null || it.unitPrice === undefined ? '' : it.unitPrice
  }))
}
const editableItems = ref<any[]>([])

function formatMoney(v: any) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
/** 行金额（参考值）：单价留空 = 由后端 FIFO 计价，此处按 0 显示为「自动」 */
function lineAmount(row: any) {
  const up = row.unitPrice
  if (up === '' || up === null || up === undefined) return null
  return (Number(row.returnQuantity) || 0) * (Number(up) || 0)
}

/** 保存（与 add.vue 同一套校验与 payload；后端 PUT 自带「只有草稿可编辑」守卫） */
async function doSave() {
  if (!form.supplierId) { ElMessage.warning(isRepair.value ? '请选择维修供应商' : '请选择退回对象（物料商）'); return }
  if (!form.fromWarehouseId) { ElMessage.warning('请选择出库源仓'); return }
  const keep = editableItems.value.filter((m: any) => Number(m.returnQuantity) > 0)
  if (keep.length === 0) { ElMessage.warning(isRepair.value ? '请输入送修数量' : (isOrderReturn.value ? '请输入退料数量' : '请输入退货数量')); return }
  const dropped = editableItems.value.length - keep.length
  if (dropped > 0) {
    try {
      await ElMessageBox.confirm(`有 ${dropped} 行数量为 0，保存后这些物料将不再${isRepair.value ? '送修' : '退回'}，确认保存？`, '提示', { type: 'warning' })
    } catch { return }
  }
  saving.value = true
  try {
    await request.put(`/outsource/material-return/${id}`, {
      supplierId: form.supplierId, fromWarehouseId: form.fromWarehouseId,
      returnDate: form.returnDate, remark: form.remark,
      // 类型（2026-09-28 三态）：ORDER 订单退料 / REFUND 退货退款 / REPAIR 维修返回
      returnType: form.returnType,
      items: keep.map((m: any) => ({
        materialId: m.materialId, materialTypeId: m.materialTypeId, unit: m.unit,
        quantity: Number(m.returnQuantity), unitPrice: m.unitPrice || '', remark: m.remark || ''
      })),
      // 来源收料单：原值保留（编辑不改变来源，用于按记录算可退数量并追溯）
      sourceDeliveryId: detail.value.sourceDeliveryId ?? null,
      // 关联物料订单（2026-09-17 维修闭环；2026-09-28 起订单退料/退货退款也要保留）：
      // **不再按 isRepair 强制 null** —— 否则订单退料/关联退料的草稿一保存就把订单丢成无单。
      materialOrderId: form.materialOrderId || null
    })
    ElMessage.success('已保存')
    invalidate('outsourceMaterialReturn')
    await loadData()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function handleAudit() {
  const tip = isOrderReturn.value
    ? ('确认审核该订单退料单？审核后物料出源仓，并扣减关联订单的出货/收料数量（永久扣减，反审核才加回）'
       + (detail.value.materialOrderCode ? `：${detail.value.materialOrderCode}` : ''))
    : (isRepair.value
      ? '确认审核该维修返回单？审核后物料出源仓送供应商维修；「填了维修费」则按明细金额生成对供应商的应付。修好回厂时「登记维修返回」'
      : '确认审核该退货退款单？审核后物料出源仓，并生成「对供应商的应收」（供应商把货款退来后走收款核销）')
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/audit`); ElMessage.success('已审核'); loadData(); invalidate('outsourceMaterialReturn') } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit() {
  const tip = isOrderReturn.value
    ? '确认反审核？将物料回源仓，并把关联订单的出货/收料数量加回'
    : (isRepair.value
      ? '确认反审核？将送修物料回源仓（若有维修返回记录需先撤销；已生成的维修费应付会一并冲回）'
      : '确认反审核？将物料回源仓并冲回对供应商的应收（已有收款需先退款）')
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/un-audit`); ElMessage.success('已反审核'); loadData(); invalidate('outsourceMaterialReturn') } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/** 结案（**仅维修返回**）：未返回=0 后收尾；订单退料/退货退款没有"返回"概念 ⇒ 不出现该动作 */
async function handleClose() {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/撤销维修返回，也不能反审核（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/close`); ElMessage.success('已结案'); loadData(); invalidate('outsourceMaterialReturn') } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen() {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/re-open`); ElMessage.success('已撤销结案'); loadData(); invalidate('outsourceMaterialReturn') } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/cancel`); ElMessage.success('已作废'); loadData(); invalidate('outsourceMaterialReturn') } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

// 业务数据放在 onActivated 加载：layout 用 keep-alive 缓存页面，再次进入详情页会复用组件、
// onMounted 不再触发，只靠 onMounted 会停留在上次缓存的状态
onActivated(() => { loadData(); loadWarehouseOptions() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题 → 右端操作
       （2026-09-29 标题按类型：工厂维修=工厂维修详情 / 退货退款=退货退款详情 / 历史订单退料=订单退料详情；
        返回落点回该类型所在的叶子） -->
  <PageShell :loading="loading" :title="pageTitleText" :back-fallback="backFallback">
    <template #actions>
      <!-- 草稿：保存(主) + 审核 + 作废（2026-09-24 用户口径：草稿态就地编辑，不再跳独立编辑页） -->
      <el-button type="primary" v-if="isDraft" :loading="saving" @click="doSave">保存</el-button>
      <el-button v-perm="'outsource:material-return'" type="success" v-if="detail.status===DocStatus.DRAFT" @click="handleAudit">审核</el-button>
      <el-button v-perm="'outsource:material-return'" type="warning" v-if="detail.status===DocStatus.AUDITED && detail.closedFlag!==1" @click="handleUnAudit">反审核</el-button>
      <el-button v-perm="'outsource:material-return'" type="danger" v-if="detail.status===DocStatus.DRAFT" @click="handleCancel">作废</el-button>
      <!-- 结案 / 撤销结案（仅维修返回，2026-09-17）：未返回=0 才可结案 -->
      <el-button v-perm="'outsource:material-return'" type="success" v-if="isRepair && detail.status===DocStatus.AUDITED && detail.closedFlag!==1 && Number(detail.unreturnedQty)===0" @click="handleClose">结案</el-button>
      <el-button v-perm="'outsource:material-return'" type="warning" v-if="isRepair && detail.closedFlag===1" @click="handleReOpen">撤销结案</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <span style="font-weight:600">{{ pageTitleText }}</span>
      </template>

      <!-- ============ 草稿：可编辑（字段/校验/payload 与 add.vue 一致） ============
           ⚠️ label-width 用 **lg 档**（2026-09-27）：本表单默认字号，「关联物料订单」6 字 = 84px + 冒号 > 90px
           必然换行 ⇒ 104px 一行显示（与加工退货详情同口径）。 -->
      <el-form v-if="isDraft" :model="form" label-width="var(--app-label-width-lg)">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="退货单号">{{ detail.code }}</el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态"><el-tag :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag></el-form-item></el-col>
          <!-- 来源收料单：从「物料收货」按记录发起退货时才有（2026-09-17） -->
          <el-col :span="8"><el-form-item label="来源收料单">{{ detail.sourceDeliveryCode || (detail.sourceDeliveryId ? ('#' + detail.sourceDeliveryId) : '-') }}</el-form-item></el-col>
          <el-col :span="8">
            <el-form-item required label="退货类型">
              <!-- 三态（2026-09-28）：订单退料 / 退货退款 / 维修返回 —— 订单退料需关联未结单订单
                   （选错会在审核时被后端 `checkOrderStatus` 拦下并提示改类型） -->
              <el-select v-model="form.returnType" style="width:100%">
                <el-option :label="MaterialReturnTypeLabel[MaterialReturnType.ORDER]" :value="MaterialReturnType.ORDER" />
                <el-option :label="MaterialReturnTypeLabel[MaterialReturnType.REFUND]" :value="MaterialReturnType.REFUND" />
                <el-option :label="MaterialReturnTypeLabel[MaterialReturnType.REPAIR]" :value="MaterialReturnType.REPAIR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required :label="isRepair ? '维修供应商' : '退回对象'">
              <RemoteSelect v-model="form.supplierId" add-route="/supplier/manage/add" :fetch="fetchSuppliers" label-key="name"
                placeholder="实时查库（只允许辅料商 / 供应商）" style="width:100%" domain="supplier" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="出库源仓">
              <el-select v-model="form.fromWarehouseId" filterable clearable style="width:100%" placeholder="物料出库的来源仓">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="isRepair ? '送修日期' : '退货日期'">
              <el-input v-model="form.returnDate" type="date" />
            </el-form-item>
          </el-col>
          <!-- 关联物料订单（维修返回闭环 2026-09-17；2026-09-28 起历史订单退料/关联退料也要看得见、留得住；
               2026-09-29 用户口径「工厂维修不需要关联订单」⇒ **工厂维修的草稿不再渲染本字段**）：
               判据与 add.vue 的 showOrderPicker 一致 —— 只有**本单已挂订单**（历史关联单）才渲染。
               ⚠️ 工厂维修 / 无单退料的草稿都不渲染（它们的口径就是不挂订单）。 -->
          <el-col :span="8" v-if="form.materialOrderId != null">
            <el-form-item label="关联物料订单">
              <RemoteSelect v-model="form.materialOrderId" :fetch="fetchMaterialOrders" :label-key="materialOrderLabel"
                :disabled="!form.supplierId" placeholder="可不选（不关联则靠本单跟踪）" style="width:100%" domain="material" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <!-- ============ 已审核 / 已作废：只读（原口径原样保留） ============ -->
      <el-descriptions v-else :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code }}</el-descriptions-item>
        <!-- 两类型（2026-09-17）：退货退款 = 冲减应付；维修返回 = 送修，修好登记维修返回入库 -->
        <el-descriptions-item label="类型">
          <el-tag :type="MaterialReturnTypeTag[detail.returnType] || 'info'" size="small">{{ MaterialReturnTypeLabel[detail.returnType] || detail.returnType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="isRepair ? '维修供应商' : '退回对象'">{{ detail.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出库源仓">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <!-- 来源收料单：从「物料收货」按记录发起退货时才有（2026-09-17） -->
        <el-descriptions-item label="来源收料单">{{ detail.sourceDeliveryCode || (detail.sourceDeliveryId ? ('#' + detail.sourceDeliveryId) : '-') }}</el-descriptions-item>
        <!-- 关联物料订单（2026-09-17 维修返回闭环；2026-09-28 起订单退料/关联退料也在此显示；
             2026-09-29 用户口径「工厂维修不需要关联订单」⇒ 只有**本单真的挂了订单**才显示）：
             订单退料 = 已永久扣减该单出货/收料数（反审核才加回）；历史维修返回 = 已扣减（修好自动回补）；
             未挂订单 = 未扣减，靠本单跟踪。 -->
        <el-descriptions-item v-if="detail.materialOrderId != null" label="关联物料订单">
          <template v-if="detail.materialOrderId">
            <el-button type="primary" link @click="router.push(`/outsource/material-order/detail/${detail.materialOrderId}`)">{{ detail.materialOrderCode || ('#' + detail.materialOrderId) }}</el-button>
            <el-tag :type="MaterialOrderStatusTag[detail.materialOrderStatus] || 'info'" size="small" style="margin-left:6px">{{ MaterialOrderStatusLabel[detail.materialOrderStatus] || '-' }}</el-tag>
            <span v-if="detail.deductedFlag===1" style="margin-left:6px;color:var(--app-color-success);font-size:var(--app-font-xs)">
              {{ isOrderReturn ? '已扣减该单出货/收料数（订单退料，反审核才加回）' : '已扣减该单收料数（修好返回自动回补）' }}
            </span>
            <span v-else style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">未扣减该单收料数</span>
          </template>
          <span v-else style="color:var(--app-text-placeholder)">未关联（返回情况靠本单跟踪）</span>
        </el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ $fmtDate(detail.returnDate) }}</el-descriptions-item>
        <!-- 送修 / 已返回 / 未返回（仅维修返回）：场景②③"是否有返回来"的跟踪口径 -->
        <el-descriptions-item v-if="isRepair" label="送修 / 已返回">
          {{ Number(detail.sentQty || 0) }} /
          <span style="color:var(--app-color-success);font-weight:600">{{ Number(detail.repairReturnedQty || 0) }}</span>
          <span v-if="Number(detail.unreturnedQty) > 0" style="margin-left:8px;color:var(--app-color-warning)">未返回 {{ detail.unreturnedQty }}</span>
          <span v-else style="margin-left:8px;color:var(--app-color-success)">已全部返回</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item v-if="isRepair && detail.closedFlag===1" label="结案">
          已结案<el-tag type="success" size="small" style="margin-left:6px">{{ detail.closedBy || '' }}</el-tag>
          <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ $fmtDate(detail.closedTime) }}</span>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <span style="font-weight:600">{{ isRepair ? '送修物料明细' : '退货物料明细' }}</span>
        <span v-if="isDraft" style="font-weight:normal;color:#909399;margin-left:8px">
          数量/单价可直接改（单价留空 = 由后端按 FIFO 自动计价）；把某行改成 0 表示本次不再{{ isRepair ? '送修' : '退回' }}该物料
        </span>
      </template>

      <!-- 草稿：可编辑（物料集合固定 —— 不重建明细，避免 add.vue 注释里"静默删行"的坑） -->
      <el-table v-if="isDraft" :data="editableItems" border size="small">
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="materialTypeName" label="物料类型" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column :label="isRepair ? '送修数量' : '退货数量'" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.returnQuantity" size="small" :min="0" :controls="false" :precision="0" :step="1" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="单价（留空自动FIFO）" width="150">
          <template #default="{ row }"><el-input v-model="row.unitPrice" size="small" type="number" placeholder="自动" /></template>
        </el-table-column>
        <el-table-column label="金额（参考）" width="110" align="right">
          <template #default="{ row }">
            <span v-if="lineAmount(row) === null" style="color:#c0c4cc">自动</span>
            <span v-else>{{ formatMoney(lineAmount(row)) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="detail.items || []" border size="small">
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="materialTypeName" label="物料类型" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" :label="isRepair ? '送修数量' : '数量'" width="100" align="right" />
        <!-- P3（2026-09-28）：维修返回的单价/金额就是**维修费**（审核后按它生成对供应商的应付） -->
        <el-table-column prop="unitPrice" :label="isRepair ? '维修费单价' : '单价'" width="100" align="right" />
        <el-table-column prop="amount" :label="isRepair ? '维修费' : '金额'" width="110" align="right" />
      </el-table>
    </el-card>

    <!-- 维修返回记录（仅维修返回单，2026-09-17）：登记即入库，可逐行撤销 -->
    <el-card shadow="never" style="margin-top:12px" v-if="isRepair">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px">
          <span style="font-weight:600">维修返回记录
            <span v-if="detail.closedFlag===1" style="margin-left:6px;font-size:var(--app-font-xs);color:var(--app-color-success)">（已结案）</span>
          </span>
          <span style="flex:1;text-align:right;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
            供应商修好送回时点「登记维修返回」→ 存为<b>草稿</b>（不动库存/账务）→ 在本表点<b>审核</b>才落账
            （物料入库 + 核销在厂 + 回补订单收料数 + 实际用料/成本）；草稿可删除、已审核可反审核。
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
        <el-table-column label="入库仓库" width="130"><template #default="{row}">{{ row.warehouseName || '-' }}</template></el-table-column>
        <el-table-column label="物料名称" min-width="150"><template #default="{row}">{{ row.materialName || ('#' + row.materialId) }}</template></el-table-column>
        <el-table-column label="返回数量" width="100" align="right"><template #default="{row}"><span style="color:var(--app-color-success);font-weight:500">{{ row.quantity }}</span></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="90" show-overflow-tooltip />
        <!-- 2026-09-25 物料形态化：实际用料（子物料补料）汇总，明细金额 = FIFO 快照合计；
             2026-09-28：草稿未落账/旧行无用料 ⇒ 显示 —（金额在审核时按 FIFO 回填） -->
        <el-table-column label="实际用料" min-width="120" show-overflow-tooltip>
          <template #default="{row}">
            <span v-if="row.materialSummary">{{ row.materialSummary }}<span style="margin-left:6px;color:var(--app-text-secondary)">{{ Number(row.materialAmount || 0).toFixed(2) }}</span></span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="审核人" width="80" show-overflow-tooltip><template #default="{row}">{{ row.auditorName || '-' }}</template></el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{row}">
            <!-- 2026-09-28：已结案时**隐藏**行内动作（与全页"结案后不再受理返回动作"同口径；后端也一律拦截）。
                 刻意用 v-if 而非 disabled：全站结案/作废类动作都是"消失"，且守卫按"按钮是否存在"判定。 -->
            <el-button v-perm="'outsource:material-return'" v-if="row.status===DocStatus.DRAFT && detail.closedFlag!==1" type="success" link size="small" @click="auditRepairReturn(row)">审核</el-button>
            <el-button v-perm="'outsource:material-return'" v-if="row.status===DocStatus.AUDITED && detail.closedFlag!==1" type="warning" link size="small" @click="unAuditRepairReturn(row)">反审核</el-button>
            <el-button v-if="row.status===DocStatus.DRAFT && detail.closedFlag!==1" type="danger" link size="small" @click="cancelRepairReturn(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!(detail.repairReturns || []).length" style="color:var(--app-text-secondary);font-size:var(--app-font-xs);padding:8px 0">
        {{ detail.status!==DocStatus.AUDITED ? '审核（送修）后即可登记维修返回。' : (detail.closedFlag===1 ? '已结案（无维修返回记录）。' : '尚未登记维修返回（供应商修好送回后登记，登记先存草稿、审核后才入库）。') }}
      </div>
      <div v-else-if="Number(detail.unreturnedQty) > 0" style="color:var(--app-color-warning);font-size:var(--app-font-xs);padding:8px 0">
        还有 {{ detail.unreturnedQty }} 件未返回（供应商尚未修好送回）；全部返回后可结案。
      </div>
    </el-card>

    <!-- 登记维修返回弹窗（2026-09-28 用户口径：只存草稿，审核才落账 —— 与「加工退货详情页」的返回登记同口径） -->
    <el-dialog v-model="repairVisible" title="登记维修返回（先存草稿，审核后落账）" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            本页保存为<b>草稿</b>（不动库存与账务）；在下方「维修返回记录」里点<b>审核</b>才落账：
            物料入「入库仓库」→ 核销供应商在厂物料 → 回补关联订单收料数 → 按<b>实际用料</b>从委外仓扣子物料并结转成本。
            草稿可删除、已审核可反审核。
          </span>
        </template>
      </el-alert>
      <el-form label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item required label="入库仓库">
              <el-select v-model="repairWarehouseId" filterable clearable style="width:100%" placeholder="默认该单出库源仓，可改">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="返回日期"><el-input v-model="repairDate" type="date" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <el-table :data="repairRows" border size="small">
        <el-table-column label="物料名称" min-width="150"><template #default="{row}">{{ row.materialName }}</template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="{row}">{{ row.unit || '-' }}</template></el-table-column>
        <el-table-column label="送修 / 已返回" width="130" align="center"><template #default="{row}">{{ row.sentQty }} / {{ row.returnedQty }}</template></el-table-column>
        <el-table-column label="本次返回" width="140">
          <template #default="{row}">
            <el-input-number v-model="row.quantity" size="small" :min="0" :max="row.sentQty - row.returnedQty" :controls="false" :precision="0" :step="1" style="width:100%" />
          </template>
        </el-table-column>
      </el-table>
      <!-- 2026-09-25 物料形态化：实际用料（子物料补料）——供应商维修实际耗用，登记时从其委外仓按 FIFO 扣账，
           成本结转到回仓主物料；补料到仓用「物料发料单」（我方物料仓 → 供应商委外仓），无赔料应收。
           2026-09-27（用户口径）：**只能从送修物料的子物料里选**（数量仍可超）。 -->
      <div style="margin-top:12px;font-weight:600;margin-bottom:6px">实际用料（子物料）
        <span style="font-weight:400;font-size:var(--app-font-xs);color:var(--app-text-secondary)">（只能从送修物料的 <b>子物料</b> 里选；数量可超；从供应商委外仓扣账，无赔料应收。补料到仓请先开「物料发料单」）</span>
      </div>
      <el-alert v-if="repairCandidates.length === 0" type="warning" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            本单送修物料还没有维护子物料 ⇒ 没有可选的补料。本次可以<b>只登记物料返回、不填用料</b>；
            若确有补料，请先到「<b>物料信息管理 → 子物料</b>」维护该物料的子物料。
          </span>
        </template>
      </el-alert>
      <div v-for="(m, i) in repairMaterials" :key="i" style="display:flex;gap:8px;margin-bottom:8px">
        <el-select v-model="m.materialId" filterable clearable style="flex:1" placeholder="从送修物料的子物料里选"
          :disabled="repairCandidates.length === 0" @change="onPickRepairMaterial(m)">
          <el-option-group v-for="g in repairCandidateGroups" :key="g.label" :label="g.label">
            <el-option v-for="c in g.items" :key="c.materialId" :value="c.materialId"
              :disabled="isRepairMaterialPicked(c.materialId, m)"
              :label="(c.materialName || ('#' + c.materialId)) + (c.unit ? ('（' + c.unit + '）') : '') + ' · 用量 ' + c.quantity" />
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
