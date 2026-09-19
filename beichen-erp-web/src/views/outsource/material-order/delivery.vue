<script setup lang="ts">
/**
 * 物料收货 — 收货详细（委外加工 → 物料收货 → 点单号进入）
 * <p>2026-09-16：原「物料订单详情 → 交货管理」页签整块迁出至此（收料 RECEIVE + 退不良 DEFECT_RETURN、
 * 记录审核/反审核），详情页只保留一个跳转按钮。列表页带 ?add=1 / ?defect=1 进入时自动打开对应弹窗。</p>
 */
import { reactive, ref, computed, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag, DeliveryType, DeliveryTypeLabel, DefectHandleType, DefectHandleTypeLabel, OrderType, OrderTypeLabel, QualityType, QualityTypeLabel, OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

defineOptions({ name: 'OutsourceMaterialOrderDeliveryDetail' })

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(true)
const order = reactive({ code: '', status: '', orderType: OrderType.PURCHASE as string, supplierId: undefined as any, supplierName: '', deliveryDate: '', finishTime: '' })
const items = ref<any[]>([])
const deliveries = ref<any[]>([])

const totalQuantity = computed(() => items.value.reduce((s: number, it: any) => s + (Number(it.orderQuantity) || 0), 0))
const deliveredQuantity = computed(() => items.value.reduce((s: number, it: any) => s + (Number(it.receivedQuantity) || 0) - (Number(it.defectReturnedQty) || 0), 0))
const remainingQuantity = computed(() => totalQuantity.value - deliveredQuantity.value)
const deliveryProgress = computed(() => totalQuantity.value ? Math.min(100, Math.round(deliveredQuantity.value / totalQuantity.value * 100)) : 0)

/** 只有收货中的订单可新增交货（退不良在收货中/已完成都允许，与后端一致） */
const canReceive = computed(() => order.status === MaterialOrderStatus.RECEIVING)
const canDefectReturn = computed(() => order.status === MaterialOrderStatus.RECEIVING || order.status === MaterialOrderStatus.FINISHED)

const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })

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

function markOrderDirty() { sessionStorage.setItem(OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY, '1') }

// ===== 收料弹窗 =====
const recVisible = ref(false); const recSaving = ref(false)
const recWarehouseId = ref<number>()
const recItems = ref<any[]>([])

function openReceive() {
  recWarehouseId.value = undefined
  recItems.value = items.value.map((it: any) => ({
    itemId: it.id, materialName: it.materialName, orderQuantity: it.orderQuantity,
    receivedQuantity: it.receivedQuantity, defectReturnedQty: it.defectReturnedQty, quantity: undefined as any,
    // F7-66（2026-09-19）：收货数量上限 = 下单数 − 已收数（服务端还会再扣掉"在途草稿"，此处仅作 UI 提示；
    // 精确拦截以服务端为准）。原先输入框无上限 ⇒ 正常操作即可超收。
    maxReceive: Math.max(0, Number(it.orderQuantity || 0) - Number(it.receivedQuantity || 0)),
    components: (it.components || []).map((c: any) => ({ childMaterialName: c.childMaterialName, childUnit: c.childUnit, stockQuantity: c.stockQuantity || 0, quantity: c.quantity || 1 }))
  }))
  recVisible.value = true
}
async function handleReceive(force?: boolean) {
  if (!recWarehouseId.value) { ElMessage.warning('请选择收货仓库'); return }
  const data = recItems.value.filter((r: any) => r.quantity && Number(r.quantity) > 0)
  if (data.length === 0) { ElMessage.warning('请输入交货数量'); return }
  recSaving.value = true
  try {
    const res = await request.post<any, any>(`/outsource/material-order/${id}/receive`, { warehouseId: recWarehouseId.value, items: data, force: force || false })
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
      handleReceive(true)
      return
    }
    // 收货草稿创建成功后自动审核（审核才扣库存/生成应付），保持一步到位体验。
    // 注意：本接口**成功时 data 直接是新建收货单ID（数字）**，缺料时才是 {_shortage,shortages} 对象 ——
    // 故不能写 res?.id（对数字取属性恒 undefined，会静默跳过审核；2026-09-16 实测发现并修正）
    const deliveryId = res && typeof res === 'object' ? (res.id ?? (res as any).data) : res
    if (deliveryId) {
      try { await request.put(`/outsource/material-order/delivery/${deliveryId}/audit`) }
      catch (err: any) { ElMessage.warning('草稿已保存但审核失败：' + (err?.message || '')) }
    }
    ElMessage.success(force ? '缺料交货完成（子物料库存已为负数）' : '交货完成')
    recVisible.value = false; await loadAll(); markOrderDirty()
  } catch (e: any) { ElMessage.error(e?.message || '交货失败') } finally { recSaving.value = false }
}

/** 收货/退不良草稿单审核 */
async function auditDelivery(row: any) {
  try { await ElMessageBox.confirm('审核后将扣减库存并生成应付，是否继续？', '审核收货单', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-order/delivery/${row.id}/audit`); ElMessage.success('审核成功'); await loadAll(); markOrderDirty() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
/** 收货/退不良已审核单反审核（逆向回滚库存与应付） */
async function unauditDelivery(row: any) {
  try { await ElMessageBox.confirm('反审核将回滚库存并冲回应付，是否继续？', '反审核收货单', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-order/delivery/${row.id}/un-audit`); ElMessage.success('反审核成功'); await loadAll(); markOrderDirty() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
/** 是否为可审核/反审核的物料订单收发明细（收货/退不良） */
function isMaterialDelivery(row: any) {
  return row.deliveryType === DeliveryType.RECEIVE || row.deliveryType === DeliveryType.DEFECT_RETURN
}

/**
 * 退货（2026-09-17）：把已收的物料退回**物料商** —— 走**委外物料退货单**（独立单据：源仓扣减 + 冲减应付），
 * 与「退不良」（不良品维修返还/折现退款，写在本页交货记录里并影响净已收）是两件事。
 * 传收料记录时后端会按该单「已收 − 已退」算可退数量并预填供应商/源仓/物料。
 */
function goReturn(row?: any) {
  if (row?.id) router.push(`/outsource/material-return/add?sourceDeliveryId=${row.id}`)
  else router.push(`/outsource/material-return/add?supplierId=${order.supplierId || ''}`)
}

// ===== 退不良弹窗 =====
const defectVisible = ref(false); const defectSaving = ref(false)
const defectItems = ref<any[]>([])
const defectHandleType = ref<string>(DefectHandleType.REPAIR_RETURN)
const defectWarehouseId = ref<number>()
const defectWarehouseOptions = ref<any[]>([])

async function loadDefectWarehouses() {
  try {
    // 查询该物料订单发料到了哪些委外仓库
    const r = await request.get<any, any>(`/outsource/material-order/${id}/defect-warehouses`)
    defectWarehouseOptions.value = r || []
  } catch { defectWarehouseOptions.value = [] }
}
function onDefectWhChange(whId: number) {
  defectWarehouseId.value = whId
  for (const it of defectItems.value) { it.warehouseStock = undefined; it.stockLoading = true }
  if (!whId) return
  loadDefectStock(whId)
}
async function loadDefectStock(whId: number) {
  try {
    const r = await request.get<any, any>('/warehouse/stock/by-warehouse/' + whId)
    const stockMap: Record<number, number> = {}
    if (Array.isArray(r)) for (const s of r) stockMap[s.materialId] = s.quantity || 0
    for (const it of defectItems.value) { it.warehouseStock = stockMap[it.materialId] ?? 0; it.stockLoading = false }
  } catch {
    for (const it of defectItems.value) it.stockLoading = false
  }
}
function openDefectReturn() {
  defectHandleType.value = DefectHandleType.REPAIR_RETURN
  defectWarehouseId.value = undefined; defectWarehouseOptions.value = []
  defectItems.value = items.value.filter((it: any) => it.receivedQuantity > 0).map((it: any) => ({
    itemId: it.id, materialId: it.materialId, materialName: it.materialName,
    available: (it.receivedQuantity || 0) - (it.defectReturnedQty || 0),
    warehouseStock: undefined, stockLoading: false, quantity: undefined as any
  }))
  defectVisible.value = true
  loadDefectWarehouses()
}
async function handleDefectReturn() {
  const data = defectItems.value.filter((r: any) => r.quantity && Number(r.quantity) > 0)
  if (data.length === 0) { ElMessage.warning('请输入退料数量'); return }
  if (!defectWarehouseId.value) { ElMessage.warning('请选择退料仓库'); return }
  defectSaving.value = true
  try {
    const res = await request.post<any, any>(`/outsource/material-order/${id}/return-defect`, { handleType: defectHandleType.value, warehouseId: defectWarehouseId.value, items: data })
    // 退不良草稿创建成功后自动审核（同 receive：成功时 data 直接是新建单据ID 数字）
    const deliveryId = res && typeof res === 'object' ? (res.id ?? (res as any).data) : res
    if (deliveryId) {
      try { await request.put(`/outsource/material-order/delivery/${deliveryId}/audit`) }
      catch (err: any) { ElMessage.warning('草稿已保存但审核失败：' + (err?.message || '')) }
    }
    ElMessage.success('退不良完成'); defectVisible.value = false; await loadAll(); markOrderDirty()
  } catch (e: any) { ElMessage.error(e?.message || '退料失败') } finally { defectSaving.value = false }
}

/**
 * 从「物料收货」列表带参（?add=1 / ?defect=1）进入时自动打开对应弹窗，一步完成收料。
 * <p>幂等标记按 **route.fullPath** 记录（2026-09-17 修复）：layout 的 keep-alive key 是
 * `fullPath + '-' + tabSeq[path]`，同一 path 会复用实例 —— 若只用一个布尔标记，
 * 第二次带参进入就不会再弹（实测）。列表点击已带 `_t=<时间戳>`，故每次都是新 fullPath。</p>
 */
let lastAutoOpenedPath = ''
function maybeAutoOpen() {
  const flag = route.query.add === '1' ? 'add' : (route.query.defect === '1' ? 'defect' : '')
  if (!flag) return
  if (lastAutoOpenedPath === route.fullPath) return
  lastAutoOpenedPath = route.fullPath
  if (flag === 'add') { if (canReceive.value) openReceive(); else ElMessage.warning('只有收货中的订单可新增交货') }
  else { if (canDefectReturn.value) openDefectReturn(); else ElMessage.warning('当前状态不可退不良') }
}

onActivated(async () => { await loadAll(); await maybeAutoOpen() })
</script>

<template>
  <div v-loading="loading">
    <el-card shadow="never" style="margin-bottom:12px">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
        <el-button size="small" @click="router.push('/outsource/material-order/delivery')">← 返回列表</el-button>
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
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">已交数量</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0;color:var(--app-color-success)">{{ deliveredQuantity }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">剩余数量</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0;color:var(--app-color-warning)">{{ remainingQuantity }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">交货进度</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0;color:var(--app-color-primary)">{{ deliveryProgress }}%</p></el-card></el-col>
    </el-row>
    <el-card shadow="never" style="margin-bottom:12px">
      <el-progress :percentage="deliveryProgress" :stroke-width="16" :text-inside="true" :color="deliveredQuantity >= totalQuantity ? 'var(--app-color-success)' : 'var(--app-color-primary)'" />
    </el-card>

    <el-card shadow="never">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <span style="font-weight:600">交货记录</span>
        <div style="display:flex;gap:8px">
          <el-button v-if="canReceive" type="primary" size="small" @click="openReceive">新增交货</el-button>
          <el-button v-if="canDefectReturn" type="warning" size="small" @click="openDefectReturn">退不良</el-button>
          <!-- 退货：把已收物料退回物料商（走物料退货单）；不限于收货中，已完成也能退 -->
          <el-button type="warning" plain size="small" @click="goReturn()">退货</el-button>
        </div>
      </div>
      <el-table :data="deliveries" border stripe size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.items || []" border size="small" style="margin:4px 20px">
              <el-table-column prop="materialName" label="物料" min-width="120" />
              <el-table-column prop="unit" label="单位" width="60" />
              <el-table-column prop="quantity" label="数量" width="90" />
              <el-table-column prop="qualityType" label="品质" width="70"><template #default="{ row: r }"><el-tag :type="r.qualityType === QualityType.DEFECT ? 'danger' : 'success'" size="small">{{ QualityTypeLabel[r.qualityType] || r.qualityType }}</el-tag></template></el-table-column>
              <el-table-column label="处理方式" width="100"><template #default="{ row: r }">{{ DefectHandleTypeLabel[r.handleType] || r.handleType }}</template></el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column label="单号" width="150"><template #default="{ row }"><a v-if="row.id != null" class="bill-link" @click="router.push(`/outsource/delivery/detail/${row.id}`)">{{ row.code }}</a><span v-else>{{ row.code }}</span></template></el-table-column>
        <el-table-column prop="deliveryType" label="类型" width="70"><template #default="{ row }"><el-tag :type="row.deliveryType === DeliveryType.RECEIVE ? 'success' : 'warning'" size="small">{{ DeliveryTypeLabel[row.deliveryType] || row.deliveryType }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="日期" width="110"><template #default="{ row }">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column label="型号" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ (row.items || []).map((i:any)=>i.materialName).join(' / ') }}</template></el-table-column>
        <el-table-column label="数量" width="80" align="right"><template #default="{ row }">{{ (row.items || []).reduce((s:number,i:any)=>s+(i.quantity||0),0) }}</template></el-table-column>
        <el-table-column prop="warehouseName" label="仓库" width="120" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="isMaterialDelivery(row)">
              <el-button v-if="row.status === DocStatus.DRAFT" type="primary" link size="small" @click="auditDelivery(row)">审核</el-button>
              <el-button v-if="row.status === DocStatus.AUDITED" type="warning" link size="small" @click="unauditDelivery(row)">反审核</el-button>
              <!-- 退货：仅对已审核的**收料单**开放（退不良记录不再退货） -->
              <el-button v-if="row.status === DocStatus.AUDITED && row.deliveryType === DeliveryType.RECEIVE" type="warning" link size="small" @click="goReturn(row)">退货</el-button>
            </template>
            <span v-else style="color:var(--app-text-placeholder);font-size:var(--app-font-xs)">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 收货弹窗 -->
    <el-dialog v-model="recVisible" title="新增交货" width="700px" :close-on-click-modal="false">
      <div style="margin-bottom:8px;display:flex;align-items:center;gap:16px">
        <span style="font-size:var(--app-font-base);color:var(--app-text-regular)">供应商：<b>{{ order.supplierName || '-' }}</b></span>
        <span style="font-size:var(--app-font-base)">收货仓库：</span>
        <RemoteSelect v-model="recWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName || row.name" size="small" style="width:180px" placeholder="选择仓库" />
      </div>
      <el-table :data="recItems" border size="small" row-key="itemId">
        <el-table-column type="expand" v-if="recItems.some((it: any) => it.components && it.components.length > 0)">
          <template #default="{ row }">
            <div v-if="row.components && row.components.length > 0" style="margin:4px 20px">
              <div style="font-size:var(--app-font-xs);color:var(--app-color-danger);margin-bottom:4px">交货将扣减以下子物料库存：</div>
              <el-table :data="row.components" border size="small">
                <el-table-column prop="childMaterialName" label="子物料" min-width="100" />
                <el-table-column prop="childUnit" label="单位" width="50" />
                <el-table-column label="本次需求" width="90"><template #default="{ row: r }">{{ Number(r.quantity || 1) * Number(row.quantity || 0) }}</template></el-table-column>
                <el-table-column label="库存" width="85"><template #default="{ row: r }"><span :style="{ color: Number(r.stockQuantity || 0) < Number(r.quantity || 1) * Number(row.quantity || 0) ? 'var(--app-color-danger)' : 'var(--app-color-success)' }">{{ r.stockQuantity }}</span></template></el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column label="已收" width="70" align="right"><template #default="{ row }">{{ (row.receivedQuantity || 0) - (row.defectReturnedQty || 0) }}</template></el-table-column>
        <el-table-column label="本次交货" width="140"><template #default="{ row }"><el-input-number v-model="row.quantity" size="small" :controls="false" :precision="0" :step="1" :max="row.maxReceive" style="width:100%" placeholder="数量" /></template></el-table-column>
        <el-table-column prop="orderQuantity" label="下单数" width="80" />
        <el-table-column label="剩余可收" width="80" align="right"><template #default="{ row }">{{ row.maxReceive }}</template></el-table-column>
      </el-table>
      <template #footer><el-button @click="recVisible = false">取消</el-button><el-button type="primary" :loading="recSaving" @click="handleReceive()">确认交货</el-button></template>
    </el-dialog>

    <!-- 退不良弹窗 -->
    <el-dialog v-model="defectVisible" title="退不良品" width="650px" :close-on-click-modal="false">
      <div style="margin-bottom:8px;display:flex;align-items:center;gap:12px;flex-wrap:wrap">
        <span style="font-size:var(--app-font-base);color:var(--app-text-regular)">供应商：<b>{{ order.supplierName || '-' }}</b></span>
        <span style="font-size:var(--app-font-base)">处理方式：</span>
        <el-radio-group v-model="defectHandleType" size="small" @change="defectWarehouseId = undefined"><el-radio :value="DefectHandleType.REPAIR_RETURN">维修返还</el-radio><el-radio :value="DefectHandleType.CASH_REFUND">折现退款</el-radio></el-radio-group>
      </div>
      <div style="margin-bottom:8px"><el-select v-model="defectWarehouseId" filterable style="width:100%" placeholder="选择退料仓库" @change="onDefectWhChange"><el-option v-for="w in defectWarehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></div>
      <div v-if="defectHandleType === DefectHandleType.CASH_REFUND" style="margin-bottom:8px;padding:6px 10px;background:#fdf6ec;border-left:3px solid var(--app-color-warning);font-size:var(--app-font-xs);color:var(--app-color-warning)">折现退款将扣减退料仓库库存，并按退料金额自动冲减供应商应付。</div>
      <el-table :data="defectItems" border size="small">
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="available" label="可退" width="70" />
        <el-table-column label="仓库库存" width="90" align="right"><template #default="{ row }"><span v-if="row.stockLoading">加载中...</span><span v-else-if="row.warehouseStock === undefined" style="color:var(--app-text-placeholder)">—</span><span v-else :style="{ color: row.warehouseStock < row.quantity ? 'var(--app-color-danger)' : 'var(--app-color-success)' }">{{ row.warehouseStock }}</span></template></el-table-column>
        <el-table-column label="退料数量" width="140"><template #default="{ row }"><el-input-number v-model="row.quantity" size="small" :controls="false" :precision="0" :step="1" :max="row.available" style="width:100%" placeholder="数量" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="defectVisible = false">取消</el-button><el-button type="warning" :loading="defectSaving" @click="handleDefectReturn">确认退料</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
:deep(.bill-link) { color:var(--app-color-primary); cursor:pointer }
</style>
