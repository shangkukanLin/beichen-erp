<script setup lang="ts">
// 2026-09-20（F7-159）：显式声明组件名（便于 DevTools 辨认与将来按名 exclude）
defineOptions({ name: 'OutsourceMaterialReturnAdd' })
import { localDate } from '@/utils/date'
import { reactive, ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel, MaterialOrderStatus, MaterialOrderStatusLabel } from '@/api/enums'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import { applyPageTitle } from '@/utils/pageTitle'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { invalidate } from '@/utils/dataFreshness'

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()
/**
 * 来源参数（2026-09-17）：从「物料收货」发起退货时带出。
 * <p>?sourceDeliveryId= 按某张收料单退（带出供应商/源仓/物料与「已收 − 已退 = 可退」）；
 * ?supplierId= 订单维度入口，只预填退回对象。</p>
 */
const prefillDeliveryId = Number(route.query.sourceDeliveryId) || 0
const prefillSupplierId = Number(route.query.supplierId) || 0
/** ?returnType=REPAIR 直接从列表页签的「新增维修退货」进入（2026-09-17 两类型；术语 2026-09-21 统一为"维修退货"） */
const prefillReturnType = String(route.query.returnType || '')
/**
 * 叶子意图（2026-09-28 用户口径「关联退料页面点新增，没有选择关联订单的选项」）：
 * 关联退料与无单退料是**同一类型（REFUND）的两个叶子**、共用本页 ⇒ 由 `?linked=` 声明意图
 * （`WITH_ORDER`=关联退料叶子 / `WITHOUT_ORDER`=无单退料叶子 / 空=维修叶子、物料收货发起、地址栏直达）。
 * <p>用途：①是否渲染「关联物料订单」字段 ②该字段是否必填 ③页签/卡片标题 ④提交前的必填校验。</p>
 */
const prefillLinked = String(route.query.linked || '').toUpperCase()
/** 从「关联退料」叶子进来：**必须**挂关联物料订单（不挂就会落进「无单退料」叶子，与入口不符） */
const fromLinked = computed(() => prefillLinked === 'WITH_ORDER')
/** 从「无单退料」叶子进来：明确不挂订单（字段不渲染，保持原口径） */
const fromUnlinked = computed(() => prefillLinked === 'WITHOUT_ORDER')
/**
 * 编辑草稿（D 档 2026-09-21，与加工退货页对称）：路由 `/outsource/material-return/edit/:id`，0 = 新增。
 * <p>后端 `PUT /api/outsource/material-return/{id}` 早已存在（仅 DRAFT 可编辑，明细整体替换，草稿不动库存/应付），
 * 本页只补前端回填与提交分支。</p>
 */
const editId = Number(route.params.id) || 0
const editing = ref(false)
/** 编辑时保留原「来源收料单」：显式回传（后端 updateById 会忽略 null，但显式传值更稳，不会被误清） */
const editSourceDeliveryId = ref<number | null>(null)

const form = reactive({
  // 退货类型（2026-09-17 两态 → **2026-09-28 三态**，用户口径）：
  // REFUND 退货退款（冲减应付）/ REPAIR 维修返回（不冲应付，修好登记返回入库）
  // ⚠️ ORDER 订单退料不在本字段：它由「关联订单是否未结单」**自动判定并锁定**（见 effectiveType）
  returnType: (prefillReturnType === MaterialReturnType.REPAIR ? MaterialReturnType.REPAIR : MaterialReturnType.REFUND) as string,
  supplierId: undefined as any, fromWarehouseId: undefined as any, returnDate: localDate(), remark: '',
  // 关联物料订单（2026-09-17 维修退货闭环）：可清空；不选=不关联（靠本单「送修/已返回」跟踪）
  materialOrderId: undefined as any
})
/**
 * 「关联物料订单」字段是否渲染（2026-09-29 用户口径「工厂维修**不需要**关联订单」）：
 * <ul>
 *   <li><b>工厂维修（REPAIR）不再渲染</b> —— 送修/返回靠本单「送修/已返回」+ 结案跟踪，不需要挂物料订单；
 *       审核只出源仓（+ 维修费应付），登记维修返回把物料入回来，全程与订单无关；</li>
 *   <li>关联退料叶子（历史，叶子已下线）仍渲染（必选）；</li>
 *   <li>外加"草稿已挂单"的情况（历史关联单 / 物料收货带出来的订单）：编辑时看得见、可改可清
 *       —— 否则会看不到自己在挂哪个订单，一保存还会把它悄悄留着。</li>
 * </ul>
 * ⚠️ 无单退料叶子与物料收货发起的 REFUND 刻意不渲染 —— 它们的口径就是"不挂订单"。
 */
const showOrderPicker = computed(() => fromLinked.value || form.materialOrderId != null)
/**
 * **类型提示**：与后端 `MaterialReturnType.checkOrderStatus` + `assertNotOverReturnable` 口径逐字对齐。
 * <p>2026-09-29：订单退料不再新建（只在打开历史单时提示）；挂了"未结单"订单被明确拦下并引导去「物料收退」。
 * 退货退款 = 扣源仓 + 生成对供应商的应收；工厂维修 = 扣源仓送修 → 回厂登记 → 全返回可结案。
 * 挂订单时审核都要过「不超可退」这道闸。</p>
 */
const typeHint = computed(() => {
  if (isOrderReturn.value) {
    return '这是历史「订单退料」单（2026-09-29 起不再新建）：审核时扣源仓库存，并扣减该订单的出货/收料数量'
      + '（永久扣减，反审核才加回）；不动账务、不跟踪返回。'
  }
  if (form.materialOrderId != null && orderStatus.value === MaterialOrderStatus.RECEIVING) {
    return '该物料订单「未结单」—— 2026-09-29 起「订单退料」不再新建 ⇒ 不能在这里退料。'
      + '请到「物料收退」里该单的收货详细页点「物料退货」（按新口径：冲减该单已收数量 + 冲减应付）。'
  }
  if (form.materialOrderId != null) {
    return '该订单「已结单」⇒ 可走「退货退款」（物料回源仓 + 生成对供应商的应收）或「工厂维修」（送修 → 回厂登记 → 可结案；填了维修费则生成应付）。'
      + '审核按该订单的「可退」校验本单数量 —— 可退 = 该物料『已收 − 已退不良 − 送修中 − 订单退料 − 已退货退款』，超出会被拒。'
  }
  return ''
})
/**
 * 卡片标题 = 页面名（2026-09-28 用户口径「按你的推荐」）：**卡片标题与页头完全相同**，与 `detail.vue` 同范式。
 * <p>原先另有一套「xx信息」体例（关联退料信息 / 无单退料信息 / **维修退货信息**），第三支还缺「物料」二字，
 * 与页头「新增物料维修退货」及叶子名「物料维修退货」都对不齐 ⇒ 收敛掉，模板直接用 `pageTitle`。</p>
 */
/**
 * **页面名**（2026-09-28 用户口径「最上面标题和页面里的标题也要改」）：页头 / 顶部页签 / 浏览器标签页
 * **三处同源** —— 与 `material-return/detail.vue` 同一范式（那条"三处一致"家规由
 * `tools/regression/verify-detail-render.ps1` 守着）。⚠️ 页头原先吃路由 `meta.title`
 * （两种类型共用时的历史名「新增委外物料退货」）⇒ 只有页签/浏览器标题跟了类型，页头没跟（用户实测报回）。
 * <p>口径：REPAIR=物料维修退货｜REFUND+关联叶子=关联退料｜REFUND+无单叶子=无单退料｜
 * 其余（收货发起/地址栏直达）=委外物料退货（历史兜底名）。</p>
 */
const pageTitle = computed(() => (editId ? '编辑' : '新增') + (isOrderReturn.value ? '订单退料'
  : isRepair.value ? '工厂维修' : '退货退款'))
/**
 * 返回 / 保存后的落点（2026-09-29 叶子=类型）：**必须回到"这张单会在哪个叶子出现"** ——
 * 工厂维修（REPAIR）⇒ `/outsource/material-return/repair`；退货退款 ⇒ `/outsource/material-return/unlinked`。
 * <p>判据用 `form.returnType`（不是 `isRepair`）：本常量在 setup 早期即被 PageShell 取用，
 * 用后声明的 computed 会踩 TDZ（与 detail.vue 那个坑同源，见其 pageTitleText 注释）。</p>
 */
const backFallback = computed(() => (form.returnType === MaterialReturnType.REPAIR
  ? '/outsource/material-return/repair' : '/outsource/material-return/unlinked'))
const warehouseOptions = ref<any[]>([])
const stockList = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)

/**
 * 退回对象 / 维修供应商 实时查库（Odoo 风格）。
 * <p>2026-09-21（用户口径）：**物料退货只允许退给辅料商 + 供应商，不能退给供货商** ⇒
 * `excludeSupplierType: 'product'`（供货商=成品商 product；其余 辅料商/方案商/加工厂 都放行）。
 * 后端 `create` / `update` 有同一口径的兜底校验 ✓。</p>
 */
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { pageSize: 500, name: kw, excludeSupplierType: 'product' } })

// ===== 关联物料订单（维修退货闭环）=====
/** 选中的物料订单行（含状态，用于提示"扣减订单收料 / 靠本单跟踪"两种收尾方式） */
const pickedOrder = ref<any>(null)
/** 该物料商的物料订单（生产中/已结单）：生产中的单审核会扣减收料数，已结单的靠本单跟踪 */
// 期 3（2026-09-19 读隔离）：改走本页前缀（原读 /outsource/material-order/page 需 outsource:material-order）
const fetchMaterialOrders = (kw: string) => request.get('/outsource/material-return/material-orders', {
  params: {
    pageSize: 500, code: kw || undefined,
    supplierId: form.supplierId || undefined,
    statuses: `${MaterialOrderStatus.RECEIVING},${MaterialOrderStatus.FINISHED}`
  }
})
// 状态可能缺失（preset 回填项只带 id/code）→ 缺状态时只显示单号，避免出现"（undefined）"
function materialOrderLabel(o: any) {
  const st = o && o.status ? (MaterialOrderStatusLabel[o.status] || o.status) : ''
  return st ? `${o.code}（${st}）` : `${o.code}`
}
const orderStatus = computed(() => pickedOrder.value?.status || '')
// 注（2026-09-28）：原「收尾方式提示」（orderHint，仅维修退货）已被 typeHint 取代 ——
// 三态下"扣不扣订单收料数"由**类型**决定（订单退料扣、其余不扣），不再是维修退货的订单状态分支。
/**
 * **本单最终类型**（2026-09-29 用户口径「订单退料以后不再新建」）：就是 `form.returnType`
 * （**退货退款 / 工厂维修**两态）。
 * <p>⚠️ 原「关联订单未结单 ⇒ 自动锁定订单退料」的 `forcedOrderReturn` **已删除** —— 未结单的订单
 * 不允许在本模块退料：提交被拦（见 `handleSubmit`），提示引导去「物料收退」的收货详细页点「物料退货」
 * （按新口径：冲减该单已收数量 + 冲减应付）。历史 ORDER 单仍可打开/编辑（`form.returnType` 从单据
 * 回填成 ORDER，`isOrderReturn` 照旧生效，动作与提示语都按它走）。</p>
 */
const effectiveType = computed(() => form.returnType)
/** 历史「订单退料」单：只扣源仓 + 扣订单出货数，不动账务、不跟踪返回 */
const isOrderReturn = computed(() => effectiveType.value === MaterialReturnType.ORDER)
/** 工厂维修：不冲减应付；审核后在详情页登记「维修返回」把物料入回来（全返回后可结案） */
const isRepair = computed(() => effectiveType.value === MaterialReturnType.REPAIR)
/** 「退货类型」下拉绑定（2026-09-29 只剩两态：退货退款 / 工厂维修；历史 ORDER 单回填后不再可改） */
const typeSelect = computed({ get: () => form.returnType, set: (v: string) => { form.returnType = v } })
/** 预填期间不因 supplierId 变化清空已带出的关联订单 */
let prefilling = false
watch(() => form.supplierId, (nv, ov) => {
  if (prefilling || nv === ov) return
  if (form.materialOrderId || pickedOrder.value) { form.materialOrderId = undefined; pickedOrder.value = null }
})

async function loadOptions() {
  // F7-129（2026-09-20）：加载失败不再静默 —— 留痕，避免"空下拉"被误认为"没有数据"
  try { const r = await request.get<any, any>('/outsource/material-return/warehouse-options'); warehouseOptions.value = r || [] } catch (e: any) { console.warn('加载仓库选项失败', e?.message || e) }
}

async function onWarehouseChange() {
  stockList.value = []
  if (!form.fromWarehouseId) return
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/material-return/material-stock', { params: { warehouseId: form.fromWarehouseId } })
    stockList.value = (r || []).map((m: any) => ({ ...m, returnQuantity: undefined as any, unitPrice: undefined as any }))
  } catch (e: any) { ElMessage.error(e?.message || '加载库存失败') } finally { loading.value = false }
}

/**
 * 从「物料收货」带来源参数进入时预填（2026-09-17）：
 * 退回对象（物料商）/出库源仓/该收料单的物料与「已收 − 已退 = 可退」数量（不超过源仓当前良品库存）。
 */
async function loadFromQuery() {
  if (prefillSupplierId) form.supplierId = prefillSupplierId
  if (!prefillDeliveryId) return
  loading.value = true
  // 预填期间关闭"换供应商清空关联订单"的联动，避免刚带出的订单被清掉
  prefilling = true
  try {
    const d: any = await request.get('/outsource/material-return/return-prefill', { params: { deliveryId: prefillDeliveryId } })
    if (d.supplierId) form.supplierId = d.supplierId
    if (d.fromWarehouseId) form.fromWarehouseId = d.fromWarehouseId
    // 关联物料订单：按收料单的来源订单自动带出（2026-09-17）
    if (d.materialOrderId) {
      form.materialOrderId = d.materialOrderId
      pickedOrder.value = { id: d.materialOrderId, code: d.materialOrderCode, status: d.materialOrderStatus }
    }
    await onWarehouseChange() // 载入该源仓的可退物料（良品库存）
    const lines: any[] = (d.lines || []).filter((l: any) => Number(l.returnableQty) > 0)
    if (lines.length === 0) { ElMessage.warning('该收料单已无可退数量（可能已全部退货）'); return }
    const qtyMap: Record<string, number> = {}
    for (const l of lines) qtyMap[String(l.materialId)] = Number(l.returnableQty)
    // 只保留该收料单涉及的物料；默认数量取「可退」与源仓良品库存的较小值
    const kept = stockList.value.filter((m: any) => qtyMap[String(m.materialId)] != null)
    stockList.value = kept.map((m: any) => ({ ...m, returnQuantity: Math.min(qtyMap[String(m.materialId)], Number(m.quantity) || 0) }))
    if (kept.length === 0) ElMessage.warning('该收料单的物料在当前源仓已无良品库存，无法按单退货')
    else ElMessage.success(`已按收料单 ${d.sourceCode || ''} 带出 ${kept.length} 行退货明细，请核对数量`)
  } catch (e: any) {
    ElMessage.error('带出退货明细失败：' + (e?.message || '未知错误'))
  } finally { loading.value = false; prefilling = false }
}

async function handleSubmit() {
  if (!form.supplierId) { ElMessage.warning(isRepair.value ? '请选择维修供应商' : '请选择退回对象（物料商）'); return }
  // 关联退料叶子新增 ⇒ 必须挂订单，否则会落进「无单退料」叶子，与用户点进来的入口不符
  if (fromLinked.value && form.materialOrderId == null) {
    ElMessage.warning('请选择关联物料订单（如确实不挂订单，请从「无单退料」叶子新增）'); return
  }
  // 2026-09-29（用户口径「订单退料以后不再新建」）：挂了"未结单"订单不允许在本模块退料 ——
  //   老口径会自动把它锁成「订单退料」，现改为**拦下并引导**：去「物料收退」该单的收货详细页点「物料退货」
  //   （按新口径冲减该单已收数量 + 冲减应付）。历史 ORDER 单的编辑不受影响（effectiveType=ORDER）。
  //   ⚠️ 本拦截**放在明细校验之前**：这是一条"选错了入口"的硬拦，不该等用户填完数量才报。
  if (form.materialOrderId != null && orderStatus.value === MaterialOrderStatus.RECEIVING
      && effectiveType.value !== MaterialReturnType.ORDER) {
    ElMessage.error('该物料订单还没结单：「订单退料」已不再新建 —— 请到「物料收退」的收货详细页点「物料退货」')
    return
  }
  if (!form.fromWarehouseId) { ElMessage.warning('请选择出库源仓'); return }
  const items = stockList.value
    .filter((m: any) => Number(m.returnQuantity) > 0)
    .map((m: any) => ({
      materialId: m.materialId, materialTypeId: m.materialTypeId, unit: m.unit,
      quantity: Number(m.returnQuantity), unitPrice: m.unitPrice || '', remark: ''
    }))
  if (items.length === 0) { ElMessage.warning(isRepair.value ? '请输入送修数量' : (isOrderReturn.value ? '请输入退料数量' : '请输入退货数量')); return }
  submitting.value = true
  try {
    const payload = {
      supplierId: form.supplierId, fromWarehouseId: form.fromWarehouseId,
      returnDate: form.returnDate, remark: form.remark,
      // 类型（2026-09-29 只剩两态）：REFUND 退货退款 / REPAIR 工厂维修；
      // 历史「订单退料」单编辑时回填为 ORDER（不再新建，见 effectiveType 注释）
      returnType: effectiveType.value, items,
      // 来源收料单（从「物料收货」发起时落库，用于按记录算可退数量并追溯；编辑时保留原值）
      sourceDeliveryId: editId ? editSourceDeliveryId.value : (prefillDeliveryId || null),
      // 关联物料订单（2026-09-17 维修退货闭环；2026-09-28 起「关联退料」也用）：
      // 未选/清空 = 不关联；**不再按 isRepair 强制 null** —— 否则关联退料叶子里选好的订单会被静默丢掉、
      // 单据落成无单（MRW-）。未挂单时仍传 null，物料收货发起的场景由后端按收料单来源订单兜底派生（行为不变）。
      materialOrderId: form.materialOrderId || null
    }
    // D 档（2026-09-21）：编辑草稿走 PUT（后端仅允许 DRAFT 编辑，明细整体替换；草稿不动库存/应付）
    if (editId) {
      await request.put(`/outsource/material-return/${editId}`, payload)
      ElMessage.success('退货单已更新')
    } else {
      await request.post('/outsource/material-return', payload)
      ElMessage.success('退货单草稿已保存，请在列表中审核生效')
    }
    invalidate('outsourceMaterialReturn')
    // 提交成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本次录入的页签并回列表
    markClean()
    tabStore.closeTabAndBack(window.location.hash.replace('#', ''))
    router.replace(backFallback.value)
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitting.value = false }
}

/**
 * 编辑草稿（D 档 2026-09-21）：回填表头 + 按明细回填数量/单价（明细整体替换交由后端）。
 * <p>⚠️ 源仓当前库存里已没有该物料的（例如被别的单占掉）也要带上，否则一保存就会把这行**静默删掉**。</p>
 */
async function loadForEdit(id: number) {
  loading.value = true
  prefilling = true // 回填期间压住"换供应商清空关联订单"的联动
  try {
    const d: any = await request.get(`/outsource/material-return/${id}`)
    editing.value = true
    editSourceDeliveryId.value = d.sourceDeliveryId ?? null
    Object.assign(form, {
      returnType: d.returnType || MaterialReturnType.REFUND,
      supplierId: d.supplierId, fromWarehouseId: d.fromWarehouseId,
      returnDate: d.returnDate ? String(d.returnDate).slice(0, 10) : localDate(),
      remark: d.remark || '',
      materialOrderId: d.materialOrderId ?? undefined
    })
    if (d.materialOrderId) pickedOrder.value = { id: d.materialOrderId, code: d.materialOrderCode, status: d.materialOrderStatus }
    // 载入该源仓当前可退物料，再按本单明细回填
    await onWarehouseChange()
    const byMaterial: Record<string, any[]> = {}
    for (const it of ((d.items || []) as any[])) {
      const k = String(it.materialId)
      byMaterial[k] = byMaterial[k] || []
      byMaterial[k].push(it)
    }
    const used = new Set<string>()
    stockList.value = stockList.value.map((m: any) => {
      const list = byMaterial[String(m.materialId)]
      if (!list) return m
      used.add(String(m.materialId))
      return {
        ...m,
        returnQuantity: list.reduce((s: number, x: any) => s + Number(x.quantity || 0), 0),
        unitPrice: list[0]?.unitPrice ?? undefined
      }
    })
    // 源仓库存里已经没有、但本单明细里有的物料：补到列表尾部，避免保存时被静默删除
    for (const [mid, list] of Object.entries(byMaterial)) {
      if (used.has(mid)) continue
      stockList.value.push({
        materialId: Number(mid), materialName: list[0]?.materialName || ('物料#' + mid),
        materialTypeId: list[0]?.materialTypeId, materialTypeName: list[0]?.materialTypeName, unit: list[0]?.unit || '',
        quantity: 0, returnQuantity: list.reduce((s: number, x: any) => s + Number(x.quantity || 0), 0),
        unitPrice: list[0]?.unitPrice ?? undefined
      })
    }
  } catch (e: any) {
    ElMessage.error('加载退货单失败：' + (e?.message || '未知错误'))
  } finally { loading.value = false; prefilling = false }
}

// 顶栏"刷新数据"：重新加载出库源仓下拉
async function handleRefreshData() { await loadOptions() }
/**
 * 未保存拦截（2026-09-23 统一模板）：本页明细由来源单带入、只读 ⇒ 脏状态就是 form 本身。
 * ⚠️ 必须写在 form / editing 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form }))

/**
 * 页签标题**跟随实际类型**（2026-09-27 用户实测，加工侧同口径修）：
 * 路由 meta.title 是「新增/编辑委外物料退货」（两种类型共用一个入口时的旧名），从「物料维修退货」叶子
 * 点「新增」时页签就名不符实 ⇒ 按 isRepair 改对页签名（只影响顶部页签显示，落库类型不变）。
 */
function syncTabTitle() {
  // 2026-09-28（用户实测）：页面名必须与**类型 + 入口叶子**一致 —— 关联退料/无单退料共用本页，
  //   原先页头/页签一律叫「新增委外物料退货」；现在统一取 pageTitle（页头由模板 :title 绑定同一个值）。
  const t = pageTitle.value
  tabStore.updateTabTitle(route.path, t)
  applyPageTitle(t)   // 浏览器标签页标题同口径（后缀统一在 @/utils/pageTitle）
}
// 页内切换「退货类型」时：页头（computed 自动跟）+ 页签 + 浏览器标题一起跟变
watch(pageTitle, syncTabTitle)

onMounted(async () => {
  syncTabTitle()           // 先摆正页签名，再拉数据
  await loadOptions()      // 先备好仓库下拉，再按来源预填源仓（否则下拉只显示 ID）
  if (editId) await loadForEdit(editId)
  else await loadFromQuery()
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
  // 初始化完成（含编辑回填 / 来源预填）⇒ 建立"未保存"基线
  takeBaseline()
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))

</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作（保存/保存草稿） -->
  <PageShell :title="pageTitle" :back-fallback="backFallback">
    <template #actions>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">{{ pageTitle }}</span></template>
      <!-- 类型说明整行展示（2026-09-17 立；**2026-09-28 改为三态**）：三类型的库存/账务/后续动作不同 -->
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            {{ isOrderReturn
              ? '订单退料：关联订单「未结单」时的退料 —— 审核扣源仓库存，并扣减该订单的出货/收料数量（永久扣减，反审核才加回）；不动账务、不跟踪返回。'
              : (isRepair
                ? '维修返回：把物料送供应商维修 —— 审核只扣源仓；「填了维修费」则按明细金额生成对供应商的应付。供应商修好后在详情页「登记维修返回」把物料入回来（可分批、可撤销），全部返回后可结案。'
                : '退货退款：物料退回供应商 —— 审核扣源仓并生成「对供应商的应收」；供应商把货款退给我们后走收款核销（收款单选该供应商即可核销这笔应收）。') }}
          </span>
        </template>
      </el-alert>
      <el-form :model="form" label-width="var(--app-label-width-lg)" size="small">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item required label="退货类型">
              <!-- 2026-09-29：只剩两态（退货退款 / 工厂维修）—— 「订单退料」不再新建 ⇒ 下拉里不再出现该项
                   （历史订单退料单打开编辑时值为 ORDER，仍会按该值显示/提交） -->
              <el-select v-model="typeSelect" style="width:100%">
                <el-option :label="MaterialReturnTypeLabel[MaterialReturnType.REFUND]" :value="MaterialReturnType.REFUND" />
                <el-option :label="MaterialReturnTypeLabel[MaterialReturnType.REPAIR]" :value="MaterialReturnType.REPAIR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item required :label="isRepair ? '维修供应商' : '退回对象'"><RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" :placeholder="isRepair ? '选择维修供应商' : '选择物料商'" style="width:100%" domain="supplier" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="出库源仓"><el-select v-model="form.fromWarehouseId" filterable clearable style="width:100%" placeholder="选择物料所在仓库" @change="onWarehouseChange"><el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <!-- 关联物料订单（2026-09-17 维修退货闭环；2026-09-28 起「关联退料」叶子也走这里；
               2026-09-29 用户口径「工厂维修不需要关联订单」⇒ **工厂维修不再渲染本字段**，仅剩：
               关联退料（历史叶子）：**必选**（不选会落成无单 MRW-），审核只按订单可退量校验；
               草稿已挂单（历史单）：看得见、可改可清 —— 见 showOrderPicker -->
          <el-col :span="8" v-if="showOrderPicker">
            <el-form-item label="关联物料订单" :required="fromLinked">
              <RemoteSelect v-model="form.materialOrderId" :fetch="fetchMaterialOrders" :label-key="materialOrderLabel" :disabled="!form.supplierId"
                :placeholder="!form.supplierId ? '请先选退回对象' : (fromLinked ? '必选（关联退料）' : '可不选（不关联）')" style="width:100%" disable-cache filterable
                :preset="pickedOrder ? { id: pickedOrder.id, code: pickedOrder.code } : null" @pick="(opts: any[]) => pickedOrder = (opts && opts[0]) || null" domain="material" />
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item :label="isRepair ? '送修日期' : '退货日期'"><el-input v-model="form.returnDate" type="date" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          <!-- 类型提示（三态，2026-09-28）：关联订单状态 ⇒ 类型锁定/可选，以及审核口径（见 typeHint） -->
          <el-col :span="24" v-if="typeHint">
            <el-alert :type="isOrderReturn ? 'success' : 'warning'" :closable="false" show-icon style="margin-bottom:8px">
              <template #title><span style="font-size:var(--app-font-xs);line-height:1.5">{{ typeHint }}</span></template>
            </el-alert>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" v-if="form.fromWarehouseId" v-loading="loading">
      <template #header><span style="font-weight:600">{{ isRepair ? '可送修物料（源仓良品库存）' : '可退物料（源仓良品库存）' }}</span></template>
      <el-table :data="stockList" border size="small">
        <el-table-column prop="materialName" label="物料名称" min-width="140" />
        <el-table-column prop="materialTypeName" label="物料类型" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="可退数量" width="100" align="right">
          <template #default="{row}">{{ Number(row.quantity || 0) }}</template>
        </el-table-column>
        <el-table-column :label="isRepair ? '送修数量' : '退货数量'" width="120">
          <template #default="{row}"><el-input-number v-model="row.returnQuantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" placeholder="数量" /></template>
        </el-table-column>
        <!-- 单价口径按类型（P3 2026-09-28）：退货退款留空=FIFO 自动价；维修返回留空=**不收费 0**（填的是维修费） -->
        <el-table-column :label="isRepair ? '维修费单价（留空=0）' : '单价（留空自动FIFO）'" width="150">
          <template #default="{row}"><el-input v-model="row.unitPrice" size="small" type="number" :placeholder="isRepair ? '不收费' : '自动'" /></template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>
