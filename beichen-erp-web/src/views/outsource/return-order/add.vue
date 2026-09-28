<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { OUTSOURCE_RETURN_ORDER_DIRTY_KEY, OutsourceChargeType, OutsourceChargeTypeLabel, OutsourceReturnType, OutsourceReturnTypeLabel, ProductQualityType, ProductQualityTypeLabel } from '@/api/enums'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import { applyPageTitle } from '@/utils/pageTitle'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()
/** E4：编辑草稿模式（路由 /outsource/return-order/edit/:id）；0 = 新增 */
const editId = Number(route.params.id) || 0
/**
 * 来源参数（2026-09-17）：从「成品收货」发起退货时带出。
 * <p>?sourceDeliveryId= 按某条收货记录退（带出各规格「已收 − 已退 = 可退」）；
 * ?orderId=&factoryId= 按加工单退（列表行入口，数量留空）。</p>
 */
const prefillDeliveryId = Number(route.query.sourceDeliveryId) || 0
const prefillOrderId = Number(route.query.orderId) || 0
/** ?returnType=REPAIR 直接进入维修退货表单（列表页签入口） */
const prefillReturnType = String(route.query.returnType || '')

const form = reactive({
  // 退货类型（2026-09-17）：DEFECT 加工退货 / REPAIR 维修退货 —— 规则差异见 onTypeChange 与提交校验
  // 2026-09-21（用户口径）：加工退货已统一到「成品收货」办理（有单走该单的加工退货、无单走成品收货列表的
  // 「无单加工退货」）⇒ 本页（独立退货单）**默认维修退货**；显式带 ?returnType=DEFECT 仍按 DEFECT 渲染
  // （存量加工退货草稿编辑时保持原类型，后端会拒绝把它改成维修退货或新建加工退货）。
  returnType: (prefillReturnType === OutsourceReturnType.DEFECT ? OutsourceReturnType.DEFECT : OutsourceReturnType.REPAIR) as string,
  factoryId: undefined as any, warehouseId: undefined as any,
  returnDate: localDate(), remark: '',
  // 工厂收费：**加工厂向我方收取**（我方付加工厂），审核后生成一条正向应付
  chargeFlag: 0, chargeType: '' as string, chargeAmount: 0, chargeReason: ''
})
/** 维修退货：不关联加工单、不还料（无物料明细）、**必须由加工厂收费**，修好后走「维修返回」登记入库 */
const isRepair = computed(() => form.returnType === OutsourceReturnType.REPAIR)
/** 从「成品收货」带来/预填的加工单号（仅展示，说明这张单关联了哪张加工单） */
const linkedOrderCode = ref('')
/**
 * 真正要落库的「关联加工单」：可选可清空（2026-09-17）。
 * <p>选了 → 落 <code>order_id</code>（并按该单那一版 BOM 快照带料）；清空 → 不关联。
 * 来源入口（成品收货 / 收货记录）进入时自动选中。**初值必须是空（undefined）**：
 * 用 0 会被 el-select 当成"已选值"渲染成字面量 0（E2E 实证）。</p>
 */
const linkedOrderId = ref<number | undefined>(undefined)

/**
 * 切换退货类型：清空明细行（物料明细只对加工退货有意义），
 * 维修退货自动带上「返工费」并锁定收费必填；加工退货强制不收费（不良是工厂的问题，加工厂不向我方收费）。
 */
function onTypeChange() {
  rows.value = [createEmptyRow()]
  mergedItems.value = []
  if (isRepair.value) {
    // 维修退货不关联加工单（后端同口径拦截），这里把选择一并清掉
    linkedOrderId.value = undefined
    form.chargeFlag = 1
    form.chargeType = form.chargeType || OutsourceChargeType.REWORK
  } else {
    form.chargeFlag = 0; form.chargeType = ''; form.chargeAmount = 0; form.chargeReason = ''
  }
}
const chargeTypeOptions = computed(() =>
  Object.values(OutsourceChargeType).map((v) => ({ value: v, label: OutsourceChargeTypeLabel[v] || v }))
)
const factoryOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
/** 可关联的加工单（该加工厂下，2026-09-17 需求：可选可清空，清空=不关联） */
const orderOptions = ref<any[]>([])
async function loadOrderOptions(factoryId: any) {
  orderOptions.value = []
  if (!factoryId) return
  try {
    // 期 3（2026-09-19 读隔离）：关联加工单改走本页前缀（原读 /outsource/order/page 需 outsource:order）
    const r: any = await request.get('/outsource/return-order/orders', { params: { factoryId, pageNum: 1, pageSize: 500 } })
    orderOptions.value = r?.records || []
  } catch { orderOptions.value = [] }
}
function orderLabel(o: any) { return o?.code ? (o.code + (o.productNames ? ' · ' + o.productNames : '')) : '' }
/**
 * 选中/清空关联加工单（2026-09-17 用户口径）：
 * **关联了加工单就用那张单的 BOM 快照，且不允许再手改**（下拉同时置灰）；
 * 清空关联（不关联）时保持当前快照，用户可自选。
 */
function onLinkedOrderChange() {
  rows.value.forEach((row: any, i: number) => {
    if (!row.productName) return
    const hit = findSnapshotOfOrder(row, linkedOrderId.value)
    if (hit && row.selectedSnapshot?.snapshotId !== hit.snapshotId) {
      row.selectedSnapshot = hit
      onSnapshotChange(i)
    }
  })
}
const productList = ref<any[]>([]) // 该工厂所有产品汇总
const rows = ref<any[]>([createEmptyRow()])
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / rows 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, rows: rows.value }))
const mergedItems = ref<any[]>([])
const materialTypes = ref<any[]>([])
const loading = ref(false)
/** 保存中（与 loading 分开：loading 用于"带出/加载数据"） */
const submitting = ref(false)

// materialTypeId -> 类型名 映射供展示
function typeName(id: number | undefined) { if (id == null) return '-'; const t = materialTypes.value.find((v: any) => v.id === id); return t ? t.typeName : (id as any) }

function createEmptyRow() {
  // qualityType：退回成品规格（审核按该规格从成品仓扣减，避免不同等级间账实错位）
  // 维修退货默认「不良品」：售后品先经退货整理分成不良，再推给工厂维修
  const qualityType = isRepair.value ? ProductQualityType.DEFECT : ProductQualityType.A
  return {
    // BOM 来源 = **BOM 快照**（2026-09-17 用户口径：来源应是快照而不是加工单）
    productName: undefined as any, productMasterId: undefined as any,
    selectedSnapshot: null as any, snapshots: [] as any[],
    qualityType: qualityType as string, returnQuantity: undefined as any, materials: [] as any[],
    stock: undefined as number | undefined,
    // 该仓该产品「按规格」的库存（2026-09-17：选产品后一次取回，用于默认落在有库存的规格 + 0库存置灰）
    stockByQuality: {} as Record<string, number>, stockLoaded: false,
    // 来源记录带出的规格：不自动改判（收货记录是什么规格就退什么规格），手工选产品时才自动挑有库存的
    qualityLocked: false
  }
}

/**
 * BOM 快照下拉文案：**只显示 v几**（用户要求不带加工单号）。
 * <p>同版本可能有多份快照（下单时调整过损耗/供料方 → 调整；历史订单迁移 → 历史），
 * 此时才追加短标记与序号加以区分。</p>
 */
function snapshotMark(s: any) {
  if (!s) return ''
  if (s.kind === 'ORDER') return '（调整）'
  if (s.kind === 'MIGRATED') return '（历史）'
  return ''
}
function snapshotBase(s: any) {
  return (s?.bomVersion != null ? ('v' + s.bomVersion) : '无版本') + snapshotMark(s)
}
function snapshotLabel(s: any, row?: any) {
  if (!s) return ''
  const list = ((row?.snapshots || []) as any[])
  // 该产品只有一份快照 → 就显示 v几（用户要求：带 v几就行，不带加工单号）
  if (list.length <= 1) return (s.bomVersion != null ? ('v' + s.bomVersion) : '无版本')
  const text = snapshotBase(s)
  const same = list.filter((x: any) => snapshotBase(x) === text)
  if (same.length <= 1) return text
  // 同版本同来源标记仍有重复（明细不同）→ 补序号
  return text + ' #' + (same.findIndex((x: any) => x.snapshotId === s.snapshotId) + 1)
}

/** 某加工单在这个产品上所用的 BOM 快照（关联了加工单就必须用它，2026-09-17 用户要求不允许手改） */
function findSnapshotOfOrder(row: any, orderId?: number) {
  if (!orderId) return null
  return ((row.snapshots || []) as any[]).find((s: any) => (s.orders || []).some((o: any) => o.orderId === orderId)) || null
}

/** 关联了加工单时，只有该单做过的产品可选（否则取不到"该单那份快照"，与"不允许手改快照"冲突） */
function inLinkedOrder(p: any) {
  if (!linkedOrderId.value) return true
  return ((p?.snapshots || []) as any[]).some((s: any) => (s.orders || []).some((o: any) => o.orderId === linkedOrderId.value))
}

/** 退回成品规格可选项（加工退货只允许 A/B/C/不良，不含待整理）；维修退货只在「不良品」里选（送修件本身不良） */
function qualityOptionsFor(_row?: any) {
  const list = isRepair.value
    ? [ProductQualityType.DEFECT]
    : [ProductQualityType.A, ProductQualityType.B, ProductQualityType.C, ProductQualityType.DEFECT]
  return list.map(v => ({ value: v, label: ProductQualityTypeLabel[v] || v }))
}

/** 某规格在该仓的库存；未加载完返回 null（前端不置灰、不标注） */
function qtyOf(row: any, quality: string): number | null {
  if (!row.stockLoaded || !row.stockByQuality) return null
  return Number(row.stockByQuality[quality] || 0)
}

function usedProducts(idx: number) {
  return rows.value.filter((_, i) => i !== idx).map(r => r.productName).filter(Boolean) as string[]
}

// ===== 纯 Odoo 方案：本地轻量列表 + RemoteSelect 实时查库 =====
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { supplierType: 'factory', name: kw, pageSize: 500 } })
// 成品出库仓只取我方成品仓：自有仓库(INVENTORY) + 类型=成品仓，排除委外仓/不良仓/售后仓等
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED', pageSize: 500 } })

async function loadFactories() {
  const [sf, wf]: any[] = await Promise.all([fetchSuppliers(''), fetchWarehouses('')])
  factoryOptions.value = sf?.records || []
  warehouseOptions.value = wf?.records || []
}

async function onFactoryChange(v: any) {
  rows.value = [createEmptyRow()]
  mergedItems.value = []
  productList.value = []
  linkedOrderId.value = undefined
  await loadOrderOptions(v)
  if (!v) return
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/return-order/order-products', { params: { factoryId: v } })
    productList.value = r || []
  } finally { loading.value = false }
}

function onProductChange(idx: number) {
  const row = rows.value[idx]
  row.selectedSnapshot = null
  row.snapshots = []
  row.productMasterId = undefined
  row.materials = []
  row.stock = undefined
  row.stockByQuality = {}
  row.stockLoaded = false
  row.qualityLocked = false
  if (!row.productName) { refreshMerged(); return }
  const p = productList.value.find((p: any) => p.productName === row.productName)
  if (p) {
    row.snapshots = p.snapshots || []
    row.productMasterId = p.productMasterId
    // 关联了加工单 → 用该单那份快照（不允许改）；没关联 → 默认最新一份，用户可自选
    row.selectedSnapshot = findSnapshotOfOrder(row, linkedOrderId.value) || row.snapshots[0] || null
    if (row.selectedSnapshot) {
      loadBom(idx)
      loadStockAll(idx)
    }
  }
}

/** 按所选 **BOM 快照**取物料明细（单套用量口径，2026-09-17 起按快照ID取） */
async function loadBom(idx: number) {
  const row = rows.value[idx]
  if (!row.selectedSnapshot?.snapshotId) { row.materials = []; refreshMerged(); return }
  try {
    row.materials = await request.get<any, any>('/outsource/return-order/bom-snapshot', {
      params: { snapshotId: row.selectedSnapshot.snapshotId }
    }) || []
  } catch { row.materials = [] }
  refreshMerged()
}

/**
 * 加载该产品在所选成品仓的库存（**按规格**一次取回，2026-09-17 需求）。
 * <p>用途：① 默认把「退回规格」落在仓库**有库存**的规格上；② 规格下拉显示各规格库存、0 库存置灰不可选；
 * ③ 当前规格库存供数量校验/超额提示。未选仓库或产品时置空，展示为「—」。</p>
 */
async function loadStockAll(idx: number) {
  const row = rows.value[idx]
  row.stock = undefined
  row.stockByQuality = {}
  row.stockLoaded = false
  // 库存按「产品主数据ID」查询（快照与订单数量无关，产品维度取产品本身的主数据ID）
  const productId = row.productMasterId
  if (!form.warehouseId || !productId) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.warehouseId, productId, stockType: 'PRODUCT', pageSize: 500 }
    })
    const map: Record<string, number> = {}
    for (const rec of (r?.records || [])) {
      const q = rec.qualityType || ProductQualityType.A
      map[q] = (map[q] || 0) + (Number(rec.quantity) || 0)
    }
    row.stockByQuality = map
    row.stockLoaded = true
    // 默认规格：仅"手工选产品"时自动挑有库存的规格（来源收货记录带出的规格不动，避免把 A 规退成 B 规）
    if (!row.qualityLocked && Number(row.returnQuantity) <= 0) {
      const priority = isRepair.value
        ? [ProductQualityType.DEFECT]
        : [ProductQualityType.A, ProductQualityType.B, ProductQualityType.C, ProductQualityType.DEFECT]
      const hit = priority.find(q => Number(map[q]) > 0)
      if (hit) row.qualityType = hit
    }
    row.stock = Number(map[row.qualityType] || 0)
  } catch { row.stock = undefined }
}

/** 切换退回规格：库存取该规格的值（下拉已对 0 库存置灰，这里兜底重算） */
function onQualityChange(idx: number) {
  const row = rows.value[idx]
  row.stock = row.stockLoaded ? Number(row.stockByQuality[row.qualityType] || 0) : undefined
}

/** 退回数量超过库存 */
function overStock(row: any) {
  return row.stock != null && Number(row.returnQuantity) > 0 && Number(row.returnQuantity) > Number(row.stock)
}

function onSnapshotChange(idx: number) {
  loadBom(idx)
  loadStockAll(idx)
}

/** 切换成品出库仓：所有已选产品的库存都要按新仓库重新查（手工行的默认规格也会重挑有库存的） */
function onWarehouseChange() {
  rows.value.forEach((_, i) => loadStockAll(i))
}

function onQtyChange() { refreshMerged() }

function addRow() { rows.value.push(createEmptyRow()) }
function removeRow(idx: number) {
  if (rows.value.length <= 1) { rows.value[0] = createEmptyRow(); refreshMerged(); return }
  rows.value.splice(idx, 1)
  refreshMerged()
}

function refreshMerged() {
  const map: Record<string, any> = {}
  for (const row of rows.value) {
    const qty = Number(row.returnQuantity) || 0
    if (qty <= 0 || !row.materials) continue
    for (const m of row.materials) {
      const key = m.outsourceMaterialId || m.materialName || ''
      if (!key) continue
      if (!map[key]) {
        map[key] = { materialId: m.outsourceMaterialId, materialTypeId: m.materialTypeId, materialName: m.materialName, unit: m.unit, quantity: 0, perSetQuantity: m.perSetQuantity }
      }
      map[key].quantity += qty * (Number(m.perSetQuantity) || 0)
    }
  }
  mergedItems.value = Object.values(map).filter((m: any) => m.quantity > 0)
}

/**
 * 从「成品收货」带来源参数进入时预填（2026-09-17）：
 * 工厂/成品出库仓/产品与 BOM 版本来源(该加工单)/退回规格/退回数量（默认 = 已收 − 已退，可改）。
 */
async function loadFromQuery() {
  // 维修退货：不关联加工单/收货记录，没有来源可带（货从库存选），保持空表单
  if (isRepair.value) { onTypeChange(); return }
  if (!prefillDeliveryId && !prefillOrderId) return
  loading.value = true
  try {
    const d: any = await request.get('/outsource/return-order/return-prefill', {
      params: prefillDeliveryId ? { deliveryId: prefillDeliveryId } : { orderId: prefillOrderId }
    })
    // 注意顺序：先落表头工厂（否则提交时校验「请选择加工厂」直接返回），再载入该厂产品/BOM 版本。
    // ⚠️ onFactoryChange 会清空「关联加工单」，所以来源带入的加工单必须在它之后再赋值（2026-09-17）
    if (d.factoryId) form.factoryId = d.factoryId
    await onFactoryChange(d.factoryId)
    linkedOrderCode.value = d.orderCode || ''
    linkedOrderId.value = Number(d.orderId) || undefined
    if (d.warehouseId) form.warehouseId = d.warehouseId
    const lines: any[] = (d.lines || []).filter((l: any) => l.returnableQty == null || Number(l.returnableQty) > 0)
    if (lines.length === 0) { ElMessage.warning('该收货记录已无可退数量（可能已全部退货）'); return }
    rows.value = lines.map((l: any) => {
      const row: any = createEmptyRow()
      row.productName = l.productName
      const info = productList.value.find((x: any) => x.productName === l.productName)
      row.snapshots = info?.snapshots || []
      row.productMasterId = info?.productMasterId || l.productMasterId
      // 自动选中「这张加工单/这条收货记录」所用的 BOM 快照（后端带出的 snapshotId 优先）
      const prefSnapId = l.snapshotId || d.snapshotId
      row.selectedSnapshot = (prefSnapId ? row.snapshots.find((s: any) => s.snapshotId === prefSnapId) : null)
        || findSnapshotOfOrder(row, d.orderId) || row.snapshots[0] || null
      // 规格：按"收货记录"进入时锁定该记录的规格（不把 A 规自动改成 B 规）；
      // 按"加工单"进入（列表行入口，lines 只给产品不给规格）时允许按仓库库存自动挑规格
      row.qualityType = l.qualityType || ProductQualityType.A
      row.qualityLocked = !!prefillDeliveryId
      row.returnQuantity = l.returnableQty == null ? undefined : Number(l.returnableQty)
      return row
    })
    for (let i = 0; i < rows.value.length; i++) await loadBom(i)
    for (let i = 0; i < rows.value.length; i++) await loadStockAll(i)
    ElMessage.success(`已按来源带出 ${rows.value.length} 行退货明细，请核对数量后保存`)
  } catch (e: any) {
    ElMessage.error('带出退货明细失败：' + (e?.message || '未知错误'))
  } finally { loading.value = false }
}

async function handleSubmit() {
  if (!form.factoryId) { ElMessage.warning('请选择加工厂'); return }
  // 出库仓必选：库存按「仓库+产品」校验，不先选仓就没有比对基准
  if (!form.warehouseId) { ElMessage.warning(isRepair.value ? '请选择送修出库仓' : '请选择成品出库仓'); return }
  // 收费方向：**加工厂向我方收取**（我方付加工厂）。
  // 维修退货必须收费（工厂收我方维修费）；加工退货禁止收费（不良是工厂的问题，工厂不向我方收费）
  const charged = Number(form.chargeFlag) === 1
  if (isRepair.value) {
    if (!charged) { ElMessage.warning('维修退货必须填写「加工厂向我方收取」的维修费，请打开「工厂收费」'); return }
    if (!form.chargeType) { ElMessage.warning('请选择收费类型（如返工费）'); return }
    if (!(Number(form.chargeAmount) > 0)) { ElMessage.warning('收费金额必须大于 0'); return }
  } else if (charged) {
    ElMessage.warning('加工退货不产生工厂收费（不良是工厂的问题，加工厂不向我方收费）')
    return
  }
  // 库存校验：退回/送修数量不能超出所选仓**该规格**的库存（未选仓库时查不到库存，不拦截）
  for (const r of rows.value as any[]) {
    if (!r.productName || !(Number(r.returnQuantity) > 0)) continue
    if (r.stock == null) continue
    if (Number(r.returnQuantity) > Number(r.stock)) {
      ElMessage.warning(Number(r.stock) === 0
        ? `产品「${r.productName}」在所选仓库的该规格库存为 0，请更换仓库/规格，或先入库后再退`
        : `产品「${r.productName}」${isRepair.value ? '送修' : '退回'}数量 ${r.returnQuantity} 超过库存 ${r.stock}，无法保存`)
      return
    }
  }
  // 维修退货不还料：物料明细强制为空（改料账与维修无关）
  const items = isRepair.value ? [] : mergedItems.value.map(m => ({
    materialId: m.materialId, materialTypeId: m.materialTypeId, unit: m.unit,
    quantity: m.quantity, unitPrice: '', remark: ''
  }))
  const products = rows.value.filter((r: any) => r.productName && Number(r.returnQuantity) > 0)
    .map((r: any) => ({
      productName: r.productName,
      productMasterId: r.productMasterId || null,
      // 所用 BOM 快照（2026-09-17）：关联加工单时后端以该订单快照为准（不允许手改）
      bomSnapshotId: r.selectedSnapshot?.snapshotId || null,
      qualityType: r.qualityType || ProductQualityType.A,
      quantity: Number(r.returnQuantity)
    }))
  if (products.length === 0) { ElMessage.warning(isRepair.value ? '请选择产品并填写送修数量' : '请选择产品并填写退回数量'); return }
  if (!isRepair.value) {
    // 加工退货：退货物料按 BOM 快照自动带出（可不带：包工包料产品 / 该产品无 BOM 快照 → 只退成品）
    if (items.some((m: any) => !m.materialId)) { ElMessage.warning('存在未关联委外物料的明细，无法保存，请检查BOM物料是否已登记'); return }
    if (items.length === 0) ElMessage.warning('该产品没有 BOM 快照（无退货物料），将只做成品出库')
  }
  const payload = {
    returnType: form.returnType,
    // 关联加工单 + 来源收货记录：维修退货**不关联**加工单（后端也会拦截）；
    // 加工退货：只落"来源入口带来的加工单"，手工新建一律不关联（BOM 快照只用于带出退货物料）
    orderId: isRepair.value ? null : (linkedOrderId.value || prefillOrderId || null),
    sourceDeliveryId: isRepair.value ? null : (prefillDeliveryId || null),
    factoryId: form.factoryId, warehouseId: form.warehouseId,
    returnDate: form.returnDate, remark: form.remark,
    chargeFlag: charged ? 1 : 0,
    chargeType: charged ? form.chargeType : '',
    chargeAmount: charged ? Number(form.chargeAmount) : 0,
    chargeReason: charged ? (form.chargeReason || '') : '',
    items, products
  }
  try {
    submitting.value = true
    // E4：编辑草稿走 PUT（后端仅允许 DRAFT 编辑，明细整体替换；草稿不动库存/应付）
    if (editId) { await request.put(`/outsource/return-order/${editId}`, payload); ElMessage.success('退货单已更新') }
    else { await request.post('/outsource/return-order', payload); ElMessage.success('退货单草稿已保存，请在列表中审核生效') }
    sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1')
    resetForm()
    // 提交成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本次录入的页签并回列表
    markClean()
    tabStore.closeTabAndBack(window.location.hash.replace('#', ''))
    router.replace('/outsource/return-order')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitting.value = false }
}

function resetForm() {
  Object.assign(form, {
    factoryId: undefined, warehouseId: undefined,
    returnDate: localDate(), remark: '',
    chargeFlag: 0, chargeType: '', chargeAmount: 0, chargeReason: ''
  })
  rows.value = [createEmptyRow()]
  mergedItems.value = []
  productList.value = []
}

/** E4：编辑草稿——回填表头 + 按 BOM 版本重建产品行（明细整体替换交由后端） */
async function loadForEdit(id: number) {
  loading.value = true
  try {
    const d: any = await request.get(`/outsource/return-order/${id}`)
    Object.assign(form, {
      returnType: d.returnType || OutsourceReturnType.DEFECT,
      factoryId: d.factoryId, warehouseId: d.warehouseId,
      returnDate: d.returnDate || localDate(), remark: d.remark || '',
      chargeFlag: Number(d.chargeFlag) === 1 ? 1 : 0, chargeType: d.chargeType || '',
      chargeAmount: Number(d.chargeAmount) || 0, chargeReason: d.chargeReason || ''
    })
    linkedOrderCode.value = d.orderCode || ''
    linkedOrderId.value = Number(d.orderId) || undefined
    const r: any = await request.get('/outsource/return-order/order-products', { params: { factoryId: d.factoryId } })
    productList.value = r || []
    await loadOrderOptions(d.factoryId)
    const products: any[] = d.products || []
    rows.value = products.length
      ? products.map((p: any) => {
        const row: any = createEmptyRow()
        row.productName = p.productName
        const info = productList.value.find((x: any) => x.productName === p.productName)
        row.snapshots = info?.snapshots || []
        row.productMasterId = info?.productMasterId || p.productId
        // 编辑草稿：优先还原当时存的快照（bom_snapshot_id），退化到"该订单快照 / 最新一份"
        row.selectedSnapshot = row.snapshots.find((s: any) => s.snapshotId === p.bomSnapshotId)
          || findSnapshotOfOrder(row, linkedOrderId.value) || row.snapshots[0] || null
        row.qualityType = p.qualityType || ProductQualityType.A
        // 草稿里已定的规格保持不变（不因仓库库存自动改判）
        row.qualityLocked = true
        row.returnQuantity = Number(p.quantity) || undefined
        return row
      })
      : [createEmptyRow()]
    // 逐行拉 BOM 物料（mergedItems 与提交都依赖它）与库存
    for (let i = 0; i < rows.value.length; i++) await loadBom(i)
    for (let i = 0; i < rows.value.length; i++) await loadStockAll(i)
  } catch (e: any) { ElMessage.error(e?.message || '加载退货单失败') } finally { loading.value = false }
}

/**
 * 页签标题**跟随实际类型**（2026-09-27 用户实测）：
 * 本页路由 `meta.title` 是「新增/编辑委外加工退货」—— 那是本页**早先主营加工退货**时定的名；
 * 现在本页默认就是维修退货（见 form.returnType 注释：加工退货已统一到「成品收货」办理），
 * 于是从「成品维修退货」叶子点「新增」，页签却写着"新增委外加工退货"（名不符实）。
 * ⇒ 这里按 isRepair 把页签名改对；落库类型不变，只影响顶部页签显示；页内切类型时同步。
 *    页签由 layout 在路由变化时按 meta.title 打开，本函数在其后（onMounted）覆盖。
 */
function syncTabTitle() {
  const kind = isRepair.value ? '成品维修退货' : '委外加工退货'
  const t = (editId ? '编辑' : '新增') + kind
  tabStore.updateTabTitle(route.path, t)
  applyPageTitle(t)   // 浏览器标签页标题同口径（后缀统一在 @/utils/pageTitle）
}
watch(isRepair, syncTabTitle)

onMounted(async () => {
  syncTabTitle()           // 先摆正页签名，再拉数据
  await loadFactories(); loadMaterialTypes()
  if (editId) await loadForEdit(editId)
  else await loadFromQuery()
  // 初始化完成（含编辑回填 / 来源预填）⇒ 建立"未保存"基线
  takeBaseline()
})
async function loadMaterialTypes() {
  try { const r = await request.get<any, any>('/dev/material-type/enabled'); materialTypes.value = r || [] } catch { materialTypes.value = [] }
}

</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作（保存） -->
  <PageShell back-fallback="/outsource/return-order">
    <template #actions>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">退货信息</span></template>
      <!-- 类型说明单独整行展示（2026-09-17：原先塞在"退货类型"格子里，会把右侧字段挤窄、标签换行） -->
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            {{ isRepair
              ? '维修退货：客户退回的售后品推给工厂维修 —— 不关联加工单、不还料；加工厂向我方收取维修费（必填）；修好后在详情页「登记维修返回」把货入回来。'
              : '加工退货：工厂交货后发现不良退回工厂 —— 可关联加工单（也可不关联）；退货物料按 BOM 快照还回工厂委外仓；加工厂不向我方收费（不良是工厂的问题）。' }}
          </span>
        </template>
      </el-alert>
      <el-form :model="form" label-width="var(--app-label-width-lg)" size="small">
        <el-row :gutter="16">
          <!-- 每行 3 个字段（span=8）：含 5 字标签（送修出库仓/成品出库仓）⇒ 必须用 **lg 档 104px**；
               ⚠️ 2026-09-28 用户实测：原先用默认档 90px，「仓」字被挤到第二行（size="small" 不改 label 字号，仍是 14px） -->
          <el-col :span="8">
            <!-- 2026-09-25（用户口径）：类型**锁定、不可更改**。本页（独立退货单）后端只接受维修退货
                 （OutsourceReturnOrderServiceImpl 会抛「本页只处理维修退货；退回加工厂请在「加工退货」页办理」）
                 ⇒ 旧版让用户能切到「加工退货」是个死路：改完一保存必然报错。加工退货请走「加工退货」页：
                 有单 → 该加工单的收货详细页；无单 → 该页「新增」的录入弹窗（后端走收货模块 returnDefectNoOrder）。 -->
            <el-form-item required label="退货类型">
              <el-tag size="small" style="margin-right:6px">{{ OutsourceReturnTypeLabel[form.returnType] || form.returnType }}</el-tag>
              <span style="color:#909399;font-size:var(--app-font-xs)">类型固定，不可更改</span>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item required label="加工厂"><RemoteSelect v-model="form.factoryId" :fetch="fetchSuppliers" placeholder="请选择加工厂" @update:modelValue="onFactoryChange" /></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item :label="isRepair ? '送修出库仓' : '成品出库仓'" required>
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" :placeholder="isRepair ? '送修品所在仓（我方成品仓）' : '出库仓（我方成品仓）'" @update:modelValue="onWarehouseChange" />
            </el-form-item>
          </el-col>
          <!-- 关联加工单（2026-09-17 需求）：**可选可清空** —— 选了就落 order_id（并按该单那一版 BOM 快照带料），清空=不关联；维修退货固定不关联 -->
          <el-col :span="8" v-if="!isRepair">
            <el-form-item label="关联加工单">
              <el-select v-model="linkedOrderId" filterable clearable style="width:100%"
                placeholder="不关联（按 BOM 快照带料）" @change="onLinkedOrderChange">
                <el-option v-for="o in orderOptions" :key="o.id" :label="orderLabel(o)" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="退货日期"><el-input v-model="form.returnDate" type="date" /></el-form-item></el-col>
          <el-col :span="8">
            <!-- 收费方向：**加工厂向我方收取**（我方付加工厂），审核后生成一条正向应付 -->
            <el-form-item label="工厂收费" :required="isRepair">
              <el-switch v-model="form.chargeFlag" :active-value="1" :inactive-value="0" active-text="收费" inactive-text="不收费" :disabled="!isRepair" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收费类型" :required="form.chargeFlag === 1">
              <el-select v-model="form.chargeType" placeholder="请选择" clearable style="width:100%" :disabled="form.chargeFlag !== 1">
                <el-option v-for="o in chargeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收费金额" :required="form.chargeFlag === 1">
              <el-input-number v-model="form.chargeAmount" :min="0" :precision="2" :step="10" controls-position="right" style="width:100%" :disabled="form.chargeFlag !== 1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收费说明">
              <el-input v-model="form.chargeReason" placeholder="选填，如：返工费/运费" :disabled="form.chargeFlag !== 1" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">{{ isRepair ? '选择送修产品' : '选择产品及BOM版本' }}</span>
          <el-button type="primary" size="small" :disabled="!form.factoryId" @click="addRow">+ 添加产品</el-button>
        </div>
      </template>
      <el-table :data="rows" border size="small">
        <el-table-column label="产品" width="160">
          <template #default="{row,$index}">
            <el-select v-model="row.productName" size="small" filterable clearable style="width:100%"
              :disabled="!form.factoryId" :placeholder="form.factoryId ? '请选择产品' : '请先选择加工厂'"
              @change="onProductChange($index)">
              <el-option v-for="p in productList" :key="p.productName" :label="p.productName" :value="p.productName"
                :disabled="usedProducts($index).includes(p.productName) || !inLinkedOrder(p)" />
            </el-select>
          </template>
        </el-table-column>
        <!-- BOM来源 = BOM 快照（2026-09-17 用户口径：来源是快照不是加工单）。
             **关联了加工单 → 自动用该单的快照并置灰不可改**；未关联 → 自己选（显示 v几）。 -->
        <el-table-column v-if="!isRepair" label="BOM来源（BOM快照）" width="170">
          <template #default="{row,$index}">
            <el-select v-model="row.selectedSnapshot" size="small" style="width:100%" value-key="snapshotId"
              :disabled="!!linkedOrderId && !!findSnapshotOfOrder(row, linkedOrderId)"
              :placeholder="row.snapshots.length ? '请选择BOM快照' : '该产品无BOM快照'"
              @change="onSnapshotChange($index)">
              <el-option v-for="s in row.snapshots" :key="s.snapshotId" :label="snapshotLabel(s, row)" :value="s" />
            </el-select>
          </template>
        </el-table-column>
        <!-- 退回规格：显示各规格在该仓的库存，**0 库存置灰不可选**（2026-09-17 需求：优先选有库存的规格） -->
        <el-table-column :label="isRepair ? '送修规格' : '退回规格'" width="120">
          <template #default="{row,$index}">
            <el-select v-model="row.qualityType" size="small" style="width:100%" @change="onQualityChange($index)">
              <el-option v-for="q in qualityOptionsFor(row)" :key="q.value"
                :label="q.label + (qtyOf(row, q.value) != null ? '（' + qtyOf(row, q.value) + '）' : '')"
                :value="q.value" :disabled="qtyOf(row, q.value) === 0" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="库存（当前规格）" width="110" align="right">
          <template #default="{row}">
            <span v-if="row.stock != null" :style="overStock(row) ? 'color:#f56c6c;font-weight:600' : ''">{{ Number(row.stock) }}</span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="isRepair ? '送修数量' : '退回数量'" width="110">
          <template #default="{row}">
            <el-input-number v-model="row.returnQuantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" @change="onQtyChange()" />
            <div v-if="overStock(row)" style="color:#f56c6c;font-size:var(--app-font-xs);line-height:1.2;margin-top:2px">超出库存</div>
          </template>
        </el-table-column>
        <el-table-column v-if="!isRepair" label="BOM物料（单套）" min-width="180">
          <template #default="{row}">
            <span v-if="row.materials.length" style="font-size:var(--app-font-xs)">
              {{ row.materials.map((m:any) => m.materialName + '×' + (Number(m.perSetQuantity)||0)).join('、') }}
            </span>
            <span v-else style="color:var(--app-text-placeholder);font-size:var(--app-font-xs)">选择产品后自动加载</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }"><el-button type="danger" link size="small" @click="removeRow($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" v-if="!isRepair && mergedItems.length > 0">
      <template #header><span style="font-weight:600">拆解后的退货物料（合并去重）</span></template>
      <el-table :data="mergedItems" border size="small">
        <el-table-column label="类型" width="80"><template #default="{row}">{{ typeName(row.materialTypeId) }}</template></el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="130" />
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="quantity" label="退回数量" width="100" align="right" />
      </el-table>
    </el-card>

    <!-- 保存按钮已统一上移到页头右侧操作区（PageShell #actions）；原「独立成卡」的底部保存条移除 -->
  </PageShell>
</template>
