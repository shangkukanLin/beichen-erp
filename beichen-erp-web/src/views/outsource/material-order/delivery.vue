<script setup lang="ts">
/**
 * 物料收退 — 收退详细（委外加工 → 物料收退 → 点单号进入；原名「物料收货」，2026-09-29 用户口径改名）
 * <p>2026-09-16：原「物料订单详情 → 交货管理」页签整块迁出至此（收货记录 RECEIVE + 退不良 DEFECT_RETURN、
 * 记录审核/反审核），详情页只保留一个跳转按钮。列表页带 ?add=1 进入时自动打开收货弹窗。</p>
 * <p>2026-09-29（用户口径「物料收退详情的收货记录列表需要有详细，参考加工收退的收货记录详情来做」）：
 * 收货记录行内新增 <b>「详细」</b> ⇒ 只读独立页 `/outsource/material-order/delivery/record/:id`
 * （与加工侧 `/outsource/order/delivery/record/:id` 同构：记录头 + 收货明细，含 登记时间/制单人/审核人）；
 * 同行的 <b>「单号」链接也改指该页</b>（原先跳老的「物料收发单详情」`/outsource/delivery/detail/:id`
 * —— 那是物料收发单时代的页面，库存流水那边**仍在用**，本页不再用）。</p>
 * <p><b>2026-09-29（用户口径「物料收退详情里也要有物料退货和结单」）—— 工具栏动作：</b></p>
 * <ul>
 *   <li><b>物料退货</b>：把该订单**已收**的物料退回物料商（RECEIVE_RETURN 草稿，审核后 ① 扣退货仓库存
 *       ② 冲减该订单已收数量 ③ 冲减应付）—— 落 {@code POST /outsource/material-order/{id}/return}，
 *       按**订单明细**预填（默认范围 = 该单已收的量）。**本页退货的唯一入口**（见下方最终口径）；
 *       入口**只按订单状态给**（生产中/已结单），不按"有没有可退量"隐藏（2026-09-29 用户口径「没必要隐藏」）。</li>
 *   <li><b>结单 / 反结单</b>：结单 = 物料订单状态 → 已结单（后端 {@code PUT /{id}/finish}，纯状态变更、
 *       **不要求收满、无账务副作用**，未收满时二次确认）；反结单 = 回到生产中/待审核
 *       （{@code PUT /{id}/reopen}）。这两个入口原先在「物料订单详情」与收货列表行内，
 *       2026-09-29 按用户口径**统一收到本页**。</li>
 * </ul>
 * <ul>
 *   <li>原工具栏「退不良」+「退货」两个入口（都往**同一个退货仓**扣库存，被用户判为功能重复）**合并**为
 *       收货记录行内的 <b>「新增退货」</b>：落一张 {@code RECEIVE_RETURN} 草稿，审核后 ① 扣退货仓库存
 *       ② <b>冲减该订单已收数量</b>（{@code received_quantity}）③ 冲减应付；反审核原路回滚。</li>
 *   <li><b>2026-09-29 最终口径（用户：「去掉行内新增退货」）</b>：行内「新增退货」**已去掉** ⇒ 退货统一走
 *       工具栏「物料退货」（同一弹窗/同一端点，按订单明细发起，语义 = 退该单已收的货）。行内入口原先那个
 *       「来源收货单」只是**前端显示**（后端 {@code returnMaterial} 只吃 {@code warehouseId + items}，
 *       **不落来源单**），除了"可退上限收紧到该条记录"没有别的价值 ⇒ 不保留。</li>
 *   <li>收货记录行操作列 = <b>审核 ｜ 反审核</b>：原先「反审核」只在其它页面开放，现按口径挂到收货记录
 *       自己身上；行内那个跳「物料退货单」的「退货」按钮**已去掉**。</li>
 *   <li><b>取消自动审核</b>：新增收货 / 物料退货都只落草稿，必须人工点「审核」才动库存、应付与订单数量
 *       （此前建单后前端自动调审核，界面上看不到「审核」按钮）。</li>
 * </ul>
 * <p>枚举 label（`RECEIVE`=收料、`DEFECT_RETURN`=退不良、`RECEIVE_RETURN`=退货）被**库存流水等页面共用**。</p>
 */
import { reactive, ref, computed, onActivated } from 'vue'
import PageShell from '@/components/PageShell.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag, DeliveryType, DeliveryTypeLabel, DefectHandleTypeLabel, OrderType, OrderTypeLabel, QualityType, QualityTypeLabel, WarehouseCategory, WarehouseType, OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { invalidate } from '@/utils/dataFreshness'

defineOptions({ name: 'OutsourceMaterialOrderDeliveryDetail' })

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(true)
const order = reactive({ code: '', status: '', orderType: OrderType.PURCHASE as string, supplierId: undefined as any, supplierName: '', deliveryDate: '', finishTime: '' })
const items = ref<any[]>([])
const deliveries = ref<any[]>([])

const totalQuantity = computed(() => items.value.reduce((s: number, it: any) => s + (Number(it.orderQuantity) || 0), 0))
const deliveredQuantity = computed(() => items.value.reduce((s: number, it: any) => s + (Number(it.receivedQuantity) || 0) - (Number(it.defectReturnedQty) || 0), 0))
/** 剩余数量（2026-09-29 用户口径「可以超量收货」+ 剩余封顶）：超收时按 0 显示，不给负数 */
const remainingQuantity = computed(() => Math.max(0, totalQuantity.value - deliveredQuantity.value))
const deliveryProgress = computed(() => totalQuantity.value ? Math.min(100, Math.round(deliveredQuantity.value / totalQuantity.value * 100)) : 0)

/** 只有生产中（RECEIVING）的订单可新增收货（退货在生产中/已结单都允许，与后端一致） */
const canReceive = computed(() => order.status === MaterialOrderStatus.RECEIVING)
/**
 * 物料退货（2026-09-29 用户口径「物料收退详情里也要有物料退货」→ 同日追加「**没必要隐藏**」）：
 * 只按**订单状态**给入口 —— 生产中 / 已结单都可退货（与后端 {@code returnMaterial} 的第一道校验同口径）。
 * <p><b>不再</b>要求"某明细已收 > 0"：那条闸门会让**刚建、还没收过货**的单（实测：MWO-20260929002
 * 生产中、已收 0）整页看不到入口。有没有可退的量交给**弹窗**（逐行显示 可退 = 已收量，可为 0）
 * 与**后端**（可退 ≤ 已收量）判定，不由入口替用户决定。</p>
 */
const canReturn = computed(() => order.status === MaterialOrderStatus.RECEIVING || order.status === MaterialOrderStatus.FINISHED)
/**
 * 结单 / 反结单（2026-09-29 用户口径「统一收到收退详情」）：
 * 与后端状态机一致 —— 待审核/生产中可结单（后端 {@code finish} **不校验收满**），已结单可反结单；
 * 两者都是纯状态变更（不动库存/应付），结单后本页不可再收货（canReceive 只在生产中为真）。
 */
const canFinish = computed(() => order.status === MaterialOrderStatus.PENDING || order.status === MaterialOrderStatus.RECEIVING)
const canReopen = computed(() => order.status === MaterialOrderStatus.FINISHED)

/**
 * 收货仓库可选范围（2026-10-08 用户口径「选择仓库应该只有加工厂和我们自有物料仓才对」）：
 *   ① 加工厂委外仓：warehouse_category = OUTSOURCE
 *   ② 我们自有物料仓：warehouse_category = INVENTORY 且 仓型 = AUXILIARY
 *      （WarehouseType.AUXILIARY 的注释原文即「辅料仓（自有物料仓：物料仓库 → 自有物料仓）」）
 * 并只取**启用**仓（status = 1）。
 *
 * 原实现是 `/warehouse/page{warehouseName}` —— **没有任何范围过滤** ⇒ 把全部 11 个仓库都列出来
 * （其中 7 个是已停用的外厂委外仓），既冗长又容易选错仓。
 * 本口径与「物料库存盘点」(StockTakePanel.vue) 的**物料侧完全同源**，差异只是这里额外支持关键字搜索。
 */
const fetchWarehouses = async (kw: string) => {
  const [os, aux] = await Promise.all([
    request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.OUTSOURCE } }).catch(() => ({})),
    request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WarehouseType.AUXILIARY } }).catch(() => ({}))
  ])
  return [
    ...((os?.records || []).filter((w: any) => w.status === 1)),
    ...((aux?.records || []).filter((w: any) => w.status === 1))
  ]
}

async function loadAll() {
  loading.value = true
  try {
    const [o, dList] = await Promise.all([
      request.get<any, any>(`/outsource/material-order/${id}`),
      request.get<any, any>(`/outsource/material-order/${id}/deliveries`)
    ])
    if (o) {
      Object.assign(order, {
        code: o.code || '', status: o.status || '', orderType: o.orderType || OrderType.PURCHASE,
        supplierId: o.supplierId, supplierName: o.supplierName || '', deliveryDate: o.deliveryDate || '', finishTime: o.finishTime || ''
      })
    }
    items.value = o?.items || []
    deliveries.value = dList || []
  } catch (e: any) {
    ElMessage.error('加载收货数据失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}

function markOrderDirty() { invalidate('materialOrder') }

// ===== 收货弹窗 =====
const recVisible = ref(false); const recSaving = ref(false)
const recWarehouseId = ref<number>()
const recItems = ref<any[]>([])

function openReceive() {
  recWarehouseId.value = undefined
  recItems.value = items.value.map((it: any) => ({
    itemId: it.id, materialName: it.materialName, orderQuantity: it.orderQuantity,
    receivedQuantity: it.receivedQuantity, defectReturnedQty: it.defectReturnedQty, quantity: undefined as any,
    // 2026-09-29（用户口径「物料订单也可以超量收货」）：此值只作**提示**（不设输入上限）—— 服务端仍按
    // 「下单数 − 已收数 − 在途草稿」判超量，但超量不再硬拒：返回待确认响应，用户二次确认后按实收落库。
    // F7-66（2026-09-19）：剩余可收 = 下单数 − 已收数（服务端还会再扣掉"在途草稿"，此处仅作 UI 提示；
    // 精确拦截以服务端为准）。原先输入框无上限 ⇒ 正常操作即可超收。
    maxReceive: Math.max(0, Number(it.orderQuantity || 0) - Number(it.receivedQuantity || 0)),
    components: (it.components || []).map((c: any) => ({ childMaterialName: c.childMaterialName, childUnit: c.childUnit, stockQuantity: c.stockQuantity || 0, quantity: c.quantity || 1 }))
  }))
  recVisible.value = true
}
async function handleReceive(force?: boolean, overReceipt?: boolean) {
  if (!recWarehouseId.value) { ElMessage.warning('请选择收货仓库'); return }
  const data = recItems.value.filter((r: any) => r.quantity && Number(r.quantity) > 0)
  if (data.length === 0) { ElMessage.warning('请输入收货数量'); return }
  recSaving.value = true
  try {
    // 2026-09-29（用户口径「物料订单也可以超量收货」）：overReceipt=true = 用户已确认超收 ⇒ 后端放行并落标记
    const res = await request.post<any, any>(`/outsource/material-order/${id}/receive`,
      { warehouseId: recWarehouseId.value, items: data, force: force || false, overReceipt: overReceipt || false })
    // 超收提示（2026-09-29）：与缺料提示同一套交互 —— 确认后带 overReceipt=true 重提，后端才建草稿
    if (res && res._over) {
      const overs = (res.overs || []) as any[]
      let html = '<div style="margin-bottom:8px">以下物料本次收货数量将超出「下单数 − 已收」，是否确认超收？</div>'
      html += '<table style="width:100%;border-collapse:collapse;font-size:var(--app-font-base)">'
      html += '<tr style="background:var(--app-bg-hover)"><th style="padding:6px;border:1px solid var(--app-border-light);text-align:left">物料</th><th style="padding:6px;border:1px solid var(--app-border-light)">下单数</th><th style="padding:6px;border:1px solid var(--app-border-light)">已收</th><th style="padding:6px;border:1px solid var(--app-border-light)">剩余可收</th><th style="padding:6px;border:1px solid var(--app-border-light)">本次收货</th><th style="padding:6px;border:1px solid var(--app-border-light)">超出</th></tr>'
      for (const o of overs) {
        html += `<tr><td style="padding:6px;border:1px solid var(--app-border-light)">${o.materialName || ''}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center">${o.ordered || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center">${o.received || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center">${o.remain || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-warning)">${o.request || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-danger);font-weight:600">${o.over || 0}</td></tr>`
      }
      html += '</table>'
      html += '<div style="margin-top:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">确认后按实收数量入库并生成应付（超收部分同样记账）</div>'
      recSaving.value = false
      try {
        await ElMessageBox.confirm(html, '超收确认', { confirmButtonText: '确认超收', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true })
      } catch { return }
      handleReceive(force, true)
      return
    }
    // 缺料提示：确认后重新提交缺料收货
    if (res && res._shortage) {
      const shortages = (res.shortages || []) as any[]
      let html = '<div style="margin-bottom:8px">以下子物料库存不足，是否确认缺料收货？</div>'
      html += '<table style="width:100%;border-collapse:collapse;font-size:var(--app-font-base)">'
      html += '<tr style="background:var(--app-bg-hover)"><th style="padding:6px;border:1px solid var(--app-border-light);text-align:left">物料名称</th><th style="padding:6px;border:1px solid var(--app-border-light)">需要</th><th style="padding:6px;border:1px solid var(--app-border-light)">库存</th><th style="padding:6px;border:1px solid var(--app-border-light)">缺口</th></tr>'
      for (const s of shortages) {
        html += `<tr><td style="padding:6px;border:1px solid var(--app-border-light)">${s.materialName || ''}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-warning)">${s.demand || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-danger)">${s.stock || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-danger);font-weight:600">${s.shortage || 0}</td></tr>`
      }
      html += '</table>'
      html += '<div style="margin-top:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">确认后子物料库存将变为负数</div>'
      recSaving.value = false
      try {
        await ElMessageBox.confirm(html, '缺料提示', { confirmButtonText: '确认缺料收货', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true })
      } catch { return }
      handleReceive(true, overReceipt)
      return
    }
    // 2026-09-29（用户口径「取消自动审核，改为人工审核/反审核」）：收货草稿建好后**不再自动审核** ——
    // 库存/应付/订单已收数量都要在下面「收货记录」里点「审核」才动，故这里只提示去审核。
    ElMessage.success(force
      ? '缺料收货已存为草稿，请在收货记录里点「审核」后生效（审核时子物料库存将变为负数）'
      : '收货已存为草稿，请在收货记录里点「审核」后才会扣库存、生成应付并回写已收数量')
    recVisible.value = false; await loadAll(); markOrderDirty()
  } catch (e: any) { ElMessage.error(e?.message || '收货失败') } finally { recSaving.value = false }
}

/** 收货/退不良草稿单审核 */
async function auditDelivery(row: any) {
  try { await ElMessageBox.confirm('审核后将扣减库存并生成应付，是否继续？', '审核收货单', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-order/delivery/${row.id}/audit`); ElMessage.success('已审核'); await loadAll(); markOrderDirty() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
/** 收货/退不良已审核单反审核（逆向回滚库存与应付） */
/**
 * 打开「收货记录详情」（2026-09-29 用户口径「物料收退详情的收货记录列表需要有详细，参考加工收退的
 * 收货记录详情来做」）—— 只读独立页，与加工侧 `/outsource/order/delivery/record/:id` 同构。
 */
function openRecordDetail(row: any) {
  if (row?.id != null) router.push(`/outsource/material-order/delivery/record/${row.id}`)
}
async function unauditDelivery(row: any) {
  try { await ElMessageBox.confirm('反审核将回滚库存并冲回应付，是否继续？', '反审核收货单', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-order/delivery/${row.id}/un-audit`); ElMessage.success('已反审核'); await loadAll(); markOrderDirty() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
/** 是否为可审核/反审核的物料订单收发明细（收货 / 退不良 / 退货，2026-09-29 加退货） */
function isMaterialDelivery(row: any) {
  return row.deliveryType === DeliveryType.RECEIVE
    || row.deliveryType === DeliveryType.DEFECT_RETURN
    || row.deliveryType === DeliveryType.RECEIVE_RETURN
}

// ===== 物料退货弹窗（2026-09-29 用户口径：取代原工具栏「退不良」+「退货」两个入口；
//       同日最终口径「去掉行内新增退货」⇒ 本弹窗只由工具栏「物料退货」打开，按订单明细发起）=====
// 语义：把该订单**已收**的物料退回物料商 ⇒ 审核后 ① 从退货仓扣库存 ② **冲减该订单已收数量**
// （received_quantity，即"这些货回到未收"）③ 冲减应付（不再欠物料商这批货的钱）。
// 建单只落**草稿**（与收货同口径：库存/数量/账在审核时才动），因此这里**不再自动审核**。
const retVisible = ref(false); const retSaving = ref(false)
const retItems = ref<any[]>([])
/** 退货仓库：默认 = 第一个可退仓（后端缺省也兜底取该物料商第一个委外仓），可改 */
const retWarehouseId = ref<number>()
const retWarehouseOptions = ref<any[]>([])

async function loadReturnWarehouses() {
  try {
    // 复用「退不良」的可退仓接口（= 该订单物料发到过/收过的委外仓）—— 两件事的仓范围完全相同，
    // 故不另开端点（若将来退货仓范围要放开，再拆独立端点）
    const r = await request.get<any, any>(`/outsource/material-order/${id}/defect-warehouses`)
    retWarehouseOptions.value = r || []
    // 默认选第一个（与后端"缺省兜底取该物料商第一个委外仓"同口径），否则会被「请选择退货仓库」卡住
    if (!retWarehouseId.value && retWarehouseOptions.value.length) {
      const firstWhId = Number(retWarehouseOptions.value[0].id)
      retWarehouseId.value = firstWhId
      onRetWhChange(firstWhId)
    }
  } catch { retWarehouseOptions.value = [] }
}
function onRetWhChange(whId: number) {
  retWarehouseId.value = whId
  for (const it of retItems.value) { it.warehouseStock = undefined; it.stockLoading = true }
  if (!whId) return
  loadReturnStock(whId)
}
async function loadReturnStock(whId: number) {
  try {
    const r = await request.get<any, any>('/warehouse/stock/by-warehouse/' + whId)
    const stockMap: Record<number, number> = {}
    if (Array.isArray(r)) for (const s of r) stockMap[s.materialId] = s.quantity || 0
    for (const it of retItems.value) { it.warehouseStock = stockMap[it.materialId] ?? 0; it.stockLoading = false }
  } catch {
    for (const it of retItems.value) it.stockLoading = false
  }
}
/**
 * 打开「物料退货」弹窗（**本页唯一的退货入口**，2026-09-29 用户口径「物料收退详情里也要有物料退货」，
 * 同日又定「去掉行内新增退货」「物料退货没必要隐藏」）：
 * 范围 = **订单明细**（退的就是这张单已收的货），可退上限 = 该明细当前已收量
 * —— 正是后端建单/审核两道校验的口径（`POST /{id}/return` → RECEIVE_RETURN 草稿 → 收货记录里审核）。
 * <p>弹出时**列全部订单明细**（不再过滤掉"已收 = 0"的行）：未收过货的单也能打开弹窗，一眼看到
 * 「可退 = 0」而不是一片空白 —— 与「订单状态给入口、可退量交弹窗/后端判定」同一条口径。</p>
 */
function openReturn() {
  retWarehouseId.value = undefined
  retWarehouseOptions.value = []
  retItems.value = items.value.map((it: any) => ({
    itemId: it.id, materialId: it.materialId, materialName: it.materialName,
    available: Math.max(0, Number(it.receivedQuantity || 0)),
    warehouseStock: undefined, stockLoading: false, quantity: undefined as any
  }))
  retVisible.value = true
  loadReturnWarehouses()
}
async function handleReturn() {
  const data = retItems.value.filter((r: any) => r.quantity && Number(r.quantity) > 0)
  if (data.length === 0) { ElMessage.warning('请输入退货数量'); return }
  if (!retWarehouseId.value) { ElMessage.warning('请选择退货仓库'); return }
  retSaving.value = true
  try {
    await request.post<any, any>(`/outsource/material-order/${id}/return`, {
      warehouseId: retWarehouseId.value,
      items: data.map((r: any) => ({ itemId: r.itemId, quantity: r.quantity }))
    })
    // 与收货同口径：只落草稿 ⇒ 明确告知"还没扣库存/还没冲数量"，要在收货记录里审核
    ElMessage.success('退货已存为草稿，请在收货记录里点「审核」后才会扣库存、冲减已收数量与应付')
    retVisible.value = false; await loadAll(); markOrderDirty()
  } catch (e: any) { ElMessage.error(e?.message || '退货失败') } finally { retSaving.value = false }
}

// ===== 结单 / 反结单（2026-09-29 用户口径：与「物料退货」一起挂到收退详情；原在物料订单详情页与收货列表行内）=====
/**
 * 结单：物料订单 → 已结单（`PUT /{id}/finish`）。
 * <p>后端**不校验收满**（与加工侧"未收满也能结单"同口径），故这里在未收满时做**二次确认**提示；
 * 结单是纯状态变更、无账务副作用，结单后本页不可再收货（`canReceive` 只在生产中为真）。</p>
 */
async function handleFinish() {
  const short = deliveredQuantity.value < totalQuantity.value
  const tip = short
    ? `尚未收满（已收 ${deliveredQuantity.value} / 下单 ${totalQuantity.value}），结单后本页不可再收货 —— 确认结单？`
    : '确认结单？结单后本页不可再收货。'
  try { await ElMessageBox.confirm(tip, '确认结单', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/outsource/material-order/${id}/finish`)
    ElMessage.success('已结单'); markOrderDirty(); await loadAll()
  } catch (e: any) { ElMessage.error(e?.message || '结单失败') }
}
/** 反结单：已结单 → 生产中/待审核（`PUT /{id}/reopen`）；清空结单时间与结单人，纯状态回退。 */
async function handleReopen() {
  try {
    await ElMessageBox.confirm('确认反结单？订单将回到「生产中」（可继续收货；若该单从未审核过则回「待审核」），结单时间与结单人清空。', '反结单', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/material-order/${id}/reopen`)
    ElMessage.success('已反结单'); markOrderDirty(); await loadAll()
  } catch (e: any) { ElMessage.error(e?.message || '反结单失败') }
}

/**
 * 从「物料收货」列表带参 `?add=1` 进入时自动打开收货弹窗，一步录入收货数量。
 * <p>（2026-09-29：原 `?defect=1` 自动开「退不良」弹窗的分支随该入口一并删除 —— 全库已无生产者。）</p>
 * <p>幂等标记按 **route.fullPath** 记录（2026-09-17 修复）：layout 的 keep-alive key 是
 * `fullPath + '-' + tabSeq[path]`，同一 path 会复用实例 —— 若只用一个布尔标记，
 * 第二次带参进入就不会再弹（实测）。列表点击已带 `_t=<时间戳>`，故每次都是新 fullPath。</p>
 */
let lastAutoOpenedPath = ''
function maybeAutoOpen() {
  if (route.query.add !== '1') return
  if (lastAutoOpenedPath === route.fullPath) return
  lastAutoOpenedPath = route.fullPath
  if (canReceive.value) openReceive(); else ElMessage.warning('只有生产中的订单可新增收货')
}

onActivated(async () => { await loadAll(); await maybeAutoOpen() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原「← 返回列表」按钮已删） -->
  <PageShell :loading="loading" back-fallback="/outsource/material-order/delivery">
    <el-card shadow="never" style="margin-bottom:12px">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
        <span style="font-size:var(--app-font-md)">订单号：<b>{{ order.code || '-' }}</b></span>
        <span>类型：{{ OrderTypeLabel[order.orderType] || order.orderType }}</span>
        <span>{{ order.orderType === OrderType.OUTSOURCE ? '加工厂' : '供应商' }}：<b>{{ order.supplierName || '-' }}</b></span>
        <span>状态：<el-tag :type="MaterialOrderStatusTag[order.status] || 'info'" size="small">{{ MaterialOrderStatusLabel[order.status] || order.status }}</el-tag></span>
        <span>交期：{{ $fmtDate(order.deliveryDate) }}</span>
        <el-button type="primary" size="small" @click="router.push(`/outsource/material-order/detail/${id}`)">查看订单详情</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">订单总量</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0">{{ totalQuantity }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">已收数量</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0;color:var(--app-color-success)">{{ deliveredQuantity }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">剩余数量</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0;color:var(--app-color-warning)">{{ remainingQuantity }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">收货进度</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0;color:var(--app-color-primary)">{{ deliveryProgress }}%</p></el-card></el-col>
    </el-row>
    <el-card shadow="never" style="margin-bottom:12px">
      <el-progress :percentage="deliveryProgress" :stroke-width="16" :text-inside="true" :color="deliveredQuantity >= totalQuantity ? 'var(--app-color-success)' : 'var(--app-color-primary)'" />
    </el-card>

    <el-card shadow="never">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <span style="font-weight:600">收货记录</span>
        <div style="display:flex;gap:8px">
          <el-button v-if="canReceive" type="primary" size="small" @click="openReceive">新增收货</el-button>
          <!-- 物料退货（2026-09-29 用户口径「物料收退详情里也要有物料退货」；同日最终口径把行内「新增退货」去掉
               ⇒ **本按钮是全页唯一的退货入口**）：工具栏原「退不良」+「退货」两个入口（同一退货仓、被判功能重复）
               **合并**为此按钮 —— 落 RECEIVE_RETURN 草稿，审核后 ① 扣退货仓库存 ② 冲减该订单已收数量 ③ 冲减应付；
               按**订单明细**发起（可退 = 该明细已收量，与后端两道校验同口径）。 -->
          <el-button v-if="canReturn" type="danger" size="small" @click="openReturn()">物料退货</el-button>
          <!-- 结单 / 反结单（2026-09-29 用户口径「结单也收到收退详情」）：原在「物料订单详情」与收货列表行内，
               现统一收到本页；后端结单不校验收满（未收满时二次确认），纯状态变更、无账务副作用。 -->
          <el-button v-perm="'outsource:material-delivery'" v-if="canFinish" type="warning" size="small" @click="handleFinish">结单</el-button>
          <el-button v-if="canReopen" type="warning" size="small" @click="handleReopen">反结单</el-button>
        </div>
      </div>
      <el-table :data="deliveries" border stripe size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.items || []" border size="small" style="margin:4px 20px">
              <el-table-column label="物料" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ $mLabel(row) }}</template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="60" />
              <el-table-column prop="quantity" label="数量" width="90" />
              <el-table-column prop="qualityType" label="品质" width="70"><template #default="{ row: r }"><el-tag :type="r.qualityType === QualityType.DEFECT ? 'danger' : 'success'" size="small">{{ QualityTypeLabel[r.qualityType] || r.qualityType }}</el-tag></template></el-table-column>
              <el-table-column label="处理方式" width="100"><template #default="{ row: r }">{{ DefectHandleTypeLabel[r.handleType] || r.handleType }}</template></el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <!--
          列宽预算（2026-09-29 实测驱动；家规「列表一行显示完、不左右滑动」）：
          容器实测 948px。原列宽合计 **1048 > 948 ⇒ 本来就横滑 100px**（操作列只有 96 时也已溢 66px，
          属既存债）；本次按实测一并收紧其余列（操作列同一天曾为「反审核 + 新增退货」加宽到 124，
          最终口径去掉行内「新增退货」后**回退到 96**）：
          展开 48（框架固定）· 单号 150→**130**（最长 DEL-20260928008 = 15 字符 ≈ 105+内边距 16+边框 1）
          · 类型 70→**62** · 状态 80→**74**（表头 2 字 = 28+16+1，标签自适应）· 日期 110→**100**（10 字符 ≈ 86）
          · 型号 min140→**120** · 数量 80→**74** · 仓库 120→**100** · 备注 min120→**100** · 操作 **110**
          （2026-09-29：行内加「详细」⇒ 操作列 96→110）
          ⇒ 合计 **918 ≤ 948**（余量 30）。⚠️ 改列宽前请跑 ui-e2e-11（其 S2 会断言本表不横滑且表头不被裁）。
        -->
        <!-- 2026-09-29（用户口径：单号也指到新的收货记录详情）：原先跳老的「物料收发单详情」
             （`/outsource/delivery/detail/:id` —— 那是**物料收发单**时代的页面，库存流水那边仍在用，
             本页不再用）⇒ 本页两处入口（单号 + 行内「详细」）统一到同一个
             `/outsource/material-order/delivery/record/:id`。 -->
        <el-table-column label="单号" width="130"><template #default="{ row }"><a v-if="row.id != null" class="bill-link" @click="openRecordDetail(row)">{{ row.code }}</a><span v-else>{{ row.code }}</span></template></el-table-column>
        <el-table-column prop="deliveryType" label="类型" width="62"><template #default="{ row }"><el-tag :type="row.deliveryType === DeliveryType.RECEIVE ? 'success' : 'warning'" size="small">{{ DeliveryTypeLabel[row.deliveryType] || row.deliveryType }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="74"><template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="日期" width="100"><template #default="{ row }">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column label="型号" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ (row.items || []).map((i:any)=>$mLabel(i)).join(' / ') }}</template></el-table-column>
        <el-table-column label="数量" width="74" align="right"><template #default="{ row }">{{ (row.items || []).reduce((s:number,i:any)=>s+(i.quantity||0),0) }}</template></el-table-column>
        <el-table-column prop="warehouseName" label="仓库" width="100" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
        <!-- 操作列（2026-09-29 用户口径）：「详细 ｜ 审核 ｜ 反审核」——
             ① 行内原「退货」（跳物料退货单）已去掉；② 「反审核」按口径挂到收货记录自己身上（原先只在别的页面）；
             ③ 行内「新增退货」**已去掉** ⇒ 退货统一走工具栏「物料退货」（同一弹窗/同一端点，按订单明细）；
             ④ **新增「详细」**（用户口径「物料收退详情的收货记录列表需要有详细，参考加工收退的收货记录详情来做」）
                —— 与加工侧一致挂在**每一行**（不分状态），跳只读独立页
                `/outsource/material-order/delivery/record/:id`；原先"非物料收退类型"的占位 `-` 随之删除
                （每条记录都看得到详细，占位已无意义）。
             ⚠️ 本表有展开列（类型明细），故**不**像加工侧那样再做 `@row-click` 整行跳转（会与"点开明细"打架）。
             列宽 96→**110**：最长组合 = 「详细 + 反审核」2+3 字 + 间距/内边距 ≈ 100。 -->
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openRecordDetail(row)">详细</el-button>
            <template v-if="isMaterialDelivery(row)">
              <el-button v-perm="'outsource:material-delivery'" v-if="row.status === DocStatus.DRAFT" type="primary" link size="small" @click="auditDelivery(row)">审核</el-button>
              <el-button v-perm="'outsource:material-delivery'" v-else-if="row.status === DocStatus.AUDITED" type="warning" link size="small" @click="unauditDelivery(row)">反审核</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 收货弹窗 -->
    <el-dialog v-model="recVisible" title="新增收货" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <div style="margin-bottom:8px;display:flex;align-items:center;gap:16px">
        <span style="font-size:var(--app-font-base);color:var(--app-text-regular)">供应商：<b>{{ order.supplierName || '-' }}</b></span>
        <span style="font-size:var(--app-font-base)">收货仓库：</span>
        <RemoteSelect v-model="recWarehouseId" add-route="/inventory/warehouse" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName || row.name" size="small" style="width:180px" placeholder="选择仓库" domain="warehouse" />
      </div>
      <el-table :data="recItems" border size="small" row-key="itemId">
        <el-table-column type="expand" v-if="recItems.some((it: any) => it.components && it.components.length > 0)">
          <template #default="{ row }">
            <div v-if="row.components && row.components.length > 0" style="margin:4px 20px">
              <div style="font-size:var(--app-font-xs);color:var(--app-color-danger);margin-bottom:4px">收货将扣减以下子物料库存：</div>
              <el-table :data="row.components" border size="small">
                <el-table-column prop="childMaterialName" label="子物料" min-width="100" />
                <el-table-column prop="childUnit" label="单位" width="50" />
                <el-table-column label="本次需求" width="90"><template #default="{ row: r }">{{ Number(r.quantity || 1) * Number(row.quantity || 0) }}</template></el-table-column>
                <el-table-column label="库存" width="85"><template #default="{ row: r }"><span :style="{ color: Number(r.stockQuantity || 0) < Number(r.quantity || 1) * Number(row.quantity || 0) ? 'var(--app-color-danger)' : 'var(--app-color-success)' }">{{ r.stockQuantity }}</span></template></el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="物料" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ $mLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="已收" width="70" align="right"><template #default="{ row }">{{ (row.receivedQuantity || 0) - (row.defectReturnedQty || 0) }}</template></el-table-column>
        <!-- 2026-09-29（用户口径「物料订单也可以超量收货」）：输入框**不再设上限**（原 :max="row.maxReceive"）；
             超量改由提交后的「超收确认」二次确认把关（见 handleReceive），"剩余可收"仅作提示、超了标红。 -->
        <el-table-column label="本次收货" width="140"><template #default="{ row }"><el-input-number v-model="row.quantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" placeholder="数量" /></template></el-table-column>
        <el-table-column prop="orderQuantity" label="下单数" width="80" />
        <el-table-column label="剩余可收" width="80" align="right"><template #default="{ row }"><span :style="{ color: Number(row.quantity || 0) > Number(row.maxReceive || 0) ? 'var(--app-color-danger)' : '' }">{{ row.maxReceive }}</span></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="recVisible = false">取消</el-button><el-button type="primary" :loading="recSaving" @click="handleReceive()">确认收货</el-button></template>
    </el-dialog>

    <!-- 物料退货弹窗（2026-09-29 用户口径：取代原工具栏「退不良」+「退货」两个入口；
         同日最终口径「去掉行内新增退货」⇒ 只有工具栏这一个入口，按订单明细发起） -->
    <el-dialog v-model="retVisible" title="物料退货" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <div style="margin-bottom:8px;display:flex;align-items:center;gap:12px;flex-wrap:wrap">
        <span style="font-size:var(--app-font-base);color:var(--app-text-regular)">物料商：<b>{{ order.supplierName || '-' }}</b></span>
        <span style="font-size:var(--app-font-base);color:var(--app-text-secondary)">范围：该订单已收的物料（按订单明细退货，可退 ≤ 已收量）</span>
      </div>
      <div style="margin-bottom:8px"><el-select v-model="retWarehouseId" filterable style="width:100%" placeholder="选择退货仓库（默认＝第一个可退仓，可改）" @change="onRetWhChange"><el-option v-for="w in retWarehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></div>
      <div style="margin-bottom:8px;padding:6px 10px;background:#fdf6ec;border-left:3px solid var(--app-color-warning);font-size:var(--app-font-xs);color:var(--app-color-warning)">保存为<b>草稿</b>：需在「收货记录」里点「审核」后才会 ① 从退货仓扣减库存 ② 冲减该订单已收数量 ③ 冲减应付（「反审核」原路回滚）。</div>
      <el-table :data="retItems" border size="small">
        <el-table-column label="物料" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ $mLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="available" label="可退" width="70" />
        <el-table-column label="仓库库存" width="90" align="right"><template #default="{ row }"><span v-if="row.stockLoading">加载中...</span><span v-else-if="row.warehouseStock === undefined" style="color:var(--app-text-placeholder)">—</span><span v-else :style="{ color: row.warehouseStock < row.quantity ? 'var(--app-color-danger)' : 'var(--app-color-success)' }">{{ row.warehouseStock }}</span></template></el-table-column>
        <el-table-column label="退货数量" width="140"><template #default="{ row }"><el-input-number v-model="row.quantity" size="small" :controls="false" :precision="0" :step="1" :max="row.available" style="width:100%" placeholder="数量" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="retVisible = false">取消</el-button><el-button type="primary" :loading="retSaving" @click="handleReturn">确认退货</el-button></template>
    </el-dialog>
  </PageShell>
</template>

<style scoped>
:deep(.bill-link) { color:var(--app-color-primary); cursor:pointer }
</style>
