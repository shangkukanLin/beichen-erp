<script setup lang="ts">
/**
 * 成品收货 — 收货详细（委外加工 → 成品收货 → 点单号进入）
 * <p>2026-09-16：原「加工订单详情 → 交货管理」页签整块迁出至此（含新增/编辑/删除/审核/反审核收货 + 加工退货）。
 * 列表页带 ?add=1 进入时自动打开「新增收货」弹窗（一步收货）。</p>
 * <p>2026-09-21（用户口径）：加工单详情页上那个「成品收货」跳转按钮**已移除** ——
 * 收货统一从「委外加工 → 成品收货」菜单进（列表行内「收货」直达、行内「详情」看记录）。</p>
 */
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag, DeliveryType, DeliveryTypeLabel } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

defineOptions({ name: 'OutsourceOrderDeliveryDetail' })

const route = useRoute(); const router = useRouter()
const orderId = Number(route.params.id)
const loading = ref(true)
const order = reactive({ code: '', status: '', factoryName: '', planEndDate: '' })
const deliveries = ref<any[]>([])
const summary = ref<any>({})
const products = ref<any[]>([])
const warehouseOptions = ref<any[]>([])

/** 只有生产中的加工单可录入收货/加工退货（与后端校验一致） */
const canDeliver = computed(() => order.status === OutsourceOrderStatus.PRODUCING)

// ===== 收货弹窗 =====
const dialogVisible = ref(false); const isEdit = ref(false); const editId = ref<number>()
const saving = ref(false); const uploadFile = ref<File | null>(null)
const warehouseId = ref<number>()
const form = reactive({
  productId: undefined as any, quantity: '' as any,
  aQty: 0 as number, bQty: 0 as number, cQty: 0 as number, defectQty: 0 as number,
  deliveryDate: localDate(), trackingNo: '', remark: '', attachUrl: ''
})
/** 四等级自动合计为总数量 */
const totalGrade = computed(() => (Number(form.aQty) || 0) + (Number(form.bQty) || 0) + (Number(form.cQty) || 0) + (Number(form.defectQty) || 0))
const progress = computed(() => {
  const total = Number(summary.value.totalQuantity || 0)
  const delivered = Number(summary.value.deliveredQuantity || 0)
  return total === 0 ? 0 : Math.min(100, Math.round(delivered / total * 100))
})

// 收货仓库限定为我方（自有）成品仓
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })
async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })
    warehouseOptions.value = r?.records || []
  } catch (e: any) { console.warn('加载仓库失败', e?.message || e) }
}

/** 收货记录表格行样式：加工退货行高亮 */
function deliveryRowClass({ row }: { row: any }) {
  return row.deliveryType === DeliveryType.DEFECT_RETURN ? 'defect-row' : ''
}
function openAttach(url: string) { window.open(url + '?inline=true') }

// ===== 收货记录「详情」抽屉（2026-09-21 用户建议：列表只做扫读，明细放到详情里看） =====
/**
 * 列表从此瘦身为 7 列（收货日期/产品名称/类型/等级分布/数量/状态/操作），
 * 「收货仓库 / 物流单号 / 备注 / 附件」全部移入本抽屉；并顺带补上原先**任何界面都看不到**的字段：
 * SKU、加工退货规格、记录ID、创建时间。
 */
const detailVisible = ref(false)
const detailRow = ref<any>(null)
function openDetail(row: any) { detailRow.value = row; detailVisible.value = true }

/**
 * 该记录对应的加工单产品行。**列表与详情共用同一套匹配口径**：
 * 优先按产品主数据ID匹配（加工单整单编辑会重建产品明细行、行ID会变，收货记录仍指向原产品），
 * 匹配不到再退回按产品行ID匹配。
 */
function orderProductOf(row: any) {
  return products.value.find((p: any) => row.productMasterId && p.productId === row.productMasterId)
    || products.value.find((p: any) => p.id === row.productId)
}
/** 产品名称（列表与详情共用） */
function productNameOf(row: any) { return orderProductOf(row)?.productName || '-' }
/** SKU（列表不显示，详情里给；加工单产品行未回填时显示 -） */
function skuOf(row: any) { return orderProductOf(row)?.sku || '-' }
/** 收货仓库名（列表不显示，详情里给） */
function warehouseNameOf(row: any) {
  if (!row.warehouseId) return '-'
  return warehouseOptions.value.find((w: any) => w.id === row.warehouseId)?.warehouseName || row.warehouseId
}
/** 加工退货规格文案：A/B/C → A规/B规/C规；DEFECT → 不良（普通收货为空） */
function qualityTextOf(row: any) {
  const q = row?.qualityType
  if (!q) return '-'
  return q === 'DEFECT' ? '不良' : q + '规'
}
/**
 * 类型文案（列表与详情共用）：空=普通收货，DELIVERY=收货，DEFECT_RETURN=**加工退货**。
 * <p>2026-09-21（用户口径「文案改成加工退货」）：这个名字**全链一个词** —— 本页按钮/弹窗、
 * 「加工退货」页的**同名页签**（`OutsourceReturnTypeLabel`）、后端提示语与备注快照都是「加工退货」
 * （沿革：退不良 → 加工退货 → 不良退货 → **定稿「加工退货」**）。实现不变 —— 仍是往收货记录里
 * 写一条**负数**的 `DEFECT_RETURN` 红冲行。</p>
 * <p>故这里**不走共享的 `DeliveryTypeLabel`**：物料收货页那边仍叫「退不良」（共用枚举，改共享文案会串词），
 * 两处文案各自独立、互不影响；枚举值 / 列名 / 后端标识一律不动。</p>
 */
function typeTextOf(row: any) {
  if (!row?.deliveryType) return '普通收货'
  if (row.deliveryType === DeliveryType.DELIVERY) return '收货'
  if (row.deliveryType === DeliveryType.DEFECT_RETURN) return '加工退货'
  return DeliveryTypeLabel[row.deliveryType] || row.deliveryType
}

async function loadData() {
  loading.value = true
  try {
    const [o, dList, dSummary, prods] = await Promise.all([
      request.get<any, any>(`/outsource/order/${orderId}`),
      request.get<any, any>(`/outsource/order-delivery/list/${orderId}`),
      request.get<any, any>(`/outsource/order-delivery/summary/${orderId}`),
      request.get<any, any>(`/outsource/order/${orderId}/products`)
    ])
    Object.assign(order, { code: o?.code || '', status: o?.status || '', planEndDate: o?.planEndDate || '', factoryName: '' })
    if (o?.factoryId) {
      try { const s = await request.get<any, any>(`/supplier/${o.factoryId}`); order.factoryName = s?.name || '' }
      catch (e: any) { console.warn('加载加工厂失败', e?.message || e) }
    }
    deliveries.value = dList || []
    summary.value = dSummary || {}
    products.value = prods || []
    if (warehouseOptions.value.length === 0) await loadWarehouses()
  } catch (e: any) {
    ElMessage.error('加载收货数据失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}

function openAdd() {
  isEdit.value = false; editId.value = undefined; warehouseId.value = undefined; uploadFile.value = null
  Object.assign(form, { productId: undefined, quantity: '', aQty: 0, bQty: 0, cQty: 0, defectQty: 0, deliveryDate: localDate(), trackingNo: '', remark: '', attachUrl: '' })
  dialogVisible.value = true
}
function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id; warehouseId.value = row.warehouseId || undefined; uploadFile.value = null
  Object.assign(form, {
    productId: row.productId, quantity: row.quantity,
    aQty: Number(row.aQty) || 0, bQty: Number(row.bQty) || 0, cQty: Number(row.cQty) || 0, defectQty: Number(row.defectQty) || 0,
    deliveryDate: row.deliveryDate, trackingNo: row.trackingNo || '', remark: row.remark || '', attachUrl: row.attachUrl || ''
  })
  dialogVisible.value = true
}
function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const f = e.dataTransfer?.files?.[0]; if (f) uploadFile.value = f }
function handleFileSelect(e: Event) { const f = (e.target as HTMLInputElement).files?.[0]; if (f) uploadFile.value = f }
function handleRemoveFile() { uploadFile.value = null }

async function handleSubmit(forceDelivery = false) {
  if (!form.productId) { ElMessage.warning('请选择产品名称'); return }
  if (totalGrade.value <= 0) { ElMessage.warning('请至少填写一个等级的数量'); return }
  if (!warehouseId.value) { ElMessage.warning('请选择收货仓库'); return }
  saving.value = true
  try {
    if (uploadFile.value) {
      const fd = new FormData(); fd.append('file', uploadFile.value)
      const res = await request.post<any, string>('/dev/file/upload', fd)
      form.attachUrl = res as unknown as string
    }
    const aQty = Number(form.aQty) || 0, bQty = Number(form.bQty) || 0
    const cQty = Number(form.cQty) || 0, defectQty = Number(form.defectQty) || 0
    const body = {
      productId: form.productId, quantity: totalGrade.value, aQty, bQty, cQty, defectQty,
      deliveryDate: form.deliveryDate, trackingNo: form.trackingNo, remark: form.remark,
      attachUrl: form.attachUrl, orderId, warehouseId: warehouseId.value || null
    }
    const params = forceDelivery ? { params: { forceDelivery: true } } : {}
    let res: any
    if (isEdit.value && editId.value) {
      res = await request.put(`/outsource/order-delivery/${editId.value}`, body, params)
    } else {
      res = await request.post('/outsource/order-delivery', body, params)
    }
    // 缺料：确认后强制出库（物料库存将变负）
    if (res && res.canProceed !== true) {
      saving.value = false
      const shortages = (res.shortages || []) as any[]
      let html = '<div style="margin-bottom:8px">以下物料库存不足，是否确认强制出库？</div>'
      html += '<table style="width:100%;border-collapse:collapse;font-size:var(--app-font-base)">'
      html += '<tr style="background:var(--app-bg-hover)"><th style="padding:6px;border:1px solid var(--app-border-light);text-align:left">物料名称</th><th style="padding:6px;border:1px solid var(--app-border-light)">需要</th><th style="padding:6px;border:1px solid var(--app-border-light)">库存</th><th style="padding:6px;border:1px solid var(--app-border-light)">缺口</th></tr>'
      for (const s of shortages) {
        html += `<tr><td style="padding:6px;border:1px solid var(--app-border-light)">${s.materialName || ''}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-warning)">${s.needed || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-danger)">${s.stock || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-danger);font-weight:600">${s.gap || 0}</td></tr>`
      }
      html += '</table>'
      html += '<div style="margin-top:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">确认后物料库存将变为负数</div>'
      try {
        await ElMessageBox.confirm(html, '缺料提示', { confirmButtonText: '确认强制出库', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true })
      } catch { return }
      return handleSubmit(true)
    }
    ElMessage.success(isEdit.value ? '收货记录已更新' : '收货记录已保存')
    dialogVisible.value = false
    await loadData()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) }
  } finally { saving.value = false }
}

async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确定删除该收货记录吗？', '删除', { type: 'warning' }) } catch { return }
  try { await request.delete(`/outsource/order-delivery/${row.id}`); ElMessage.success('已删除'); await loadData() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}
async function handleAudit(row: any) {
  try { await ElMessageBox.confirm('确定审核该收货记录吗？审核后将扣减物料、成品入库并生成应付。', '审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/order-delivery/${row.id}/audit`); ElMessage.success('已审核'); await loadData() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function handleUnaudit(row: any) {
  try { await ElMessageBox.confirm('确定反审核该收货记录吗？反审核后将回滚库存与应付，回到草稿。', '反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/order-delivery/${row.id}/un-audit`); ElMessage.success('已反审核'); await loadData() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/**
 * 2026-09-21（用户口径）：本页**只保留「加工退货」**（= 红冲收货；原名「退不良」，2026-09-21 统一命名）。
 * <p>本页每一条收货记录都挂在**一张委外加工单**上，退回本质就是"这张单少收了多少" ——
 * 写一条**负数**的加工退货记录（`deliveryType=DEFECT_RETURN`、`isReverse=1`）红冲掉，
 * 已收数量 / 剩余数量 / 应付就会一并改对，**不需要**再跳独立单据。</p>
 * <p>原有的「退货」入口（良品退回加工厂 → 加工退货单 `outsource_return_order`：成品出库 +
 * BOM 料还回工厂委外仓 + 冲减应付 + 可选收费）已按该口径**整体移除**。
 * 加工退货单本身仍在「委外加工 → 委外加工退货」菜单独立使用（含维修退货 / 工厂收费 /
 * 维修返回 / 结案），与本页无关。</p>
 */

// ===== 加工退货（拆分还料） =====
const defectVisible = ref(false); const defectSaving = ref(false)
const defectItems = ref<any[]>([])
const defectWarehouseId = ref<number>()
const emptyDefectStock = () => ({ a: 0, b: 0, c: 0, defect: 0 })
function openDefectReturn() {
  defectItems.value = (products.value || []).map((p: any) => ({
    productId: p.id, productName: p.productName, masterId: p.productId,
    aQty: undefined as any, bQty: undefined as any, cQty: undefined as any, defectQty: undefined as any,
    stocks: emptyDefectStock()
  }))
  defectWarehouseId.value = undefined
  defectVisible.value = true
}
function onDefectWhChange(whId: number) {
  defectWarehouseId.value = whId
  if (!whId) { defectItems.value = defectItems.value.map((it: any) => ({ ...it, stocks: emptyDefectStock() })); return }
  // 按产品主数据ID查该仓库各规格库存（productName 为快照名，不能用名称匹配）
  request.get<any, any>('/warehouse/stock/page', { params: { pageSize: 500, stockType: 'PRODUCT' } }).then((r: any) => {
    const stocks = r?.records || []
    defectItems.value = defectItems.value.map((it: any) => {
      const stocksOf = emptyDefectStock()
      const qmap: Record<string, 'a' | 'b' | 'c' | 'defect'> = { A: 'a', B: 'b', C: 'c', DEFECT: 'defect' }
      for (const row of stocks.filter((s: any) => s.warehouseId === whId && s.productId === it.masterId)) {
        const key = qmap[row.qualityType]; if (key) stocksOf[key] = Number(row.quantity || 0)
      }
      return { ...it, stocks: stocksOf }
    })
  }).catch(() => { /* 库存拉取失败不影响填写 */ })
}
async function handleDefectReturn() {
  const data: any[] = []
  for (const r of defectItems.value) {
    for (const [qualityType, qtyKey] of [['A', 'aQty'], ['B', 'bQty'], ['C', 'cQty'], ['DEFECT', 'defectQty']] as const) {
      const q = Number(r[qtyKey]); if (q > 0) data.push({ productId: r.productId, qualityType, quantity: q })
    }
  }
  if (data.length === 0) { ElMessage.warning('请输入加工退货数量'); return }
  if (!defectWarehouseId.value) { ElMessage.warning('请选择加工退货仓库'); return }
  defectSaving.value = true
  try {
    // 逐规格存草稿，审核时统一落账
    for (const r of data) {
      await request.post(`/outsource/order-delivery/return-defect/${orderId}`, { productId: r.productId, qualityType: r.qualityType, quantity: r.quantity, warehouseId: defectWarehouseId.value })
    }
    ElMessage.success('加工退货草稿已保存，请在收货记录中审核')
    defectVisible.value = false
    await loadData()
  } catch (e: any) { ElMessage.error(e?.message || '加工退货失败') } finally { defectSaving.value = false }
}

/**
 * 结单（2026-09-21 用户口径「委外加工单详情页面的结单按钮放到成品收货里」）：
 * 本页只是**入口** —— 跳结单报表页（`/outsource/order/close/:id`），结单本身要在那里
 * "保存草稿 → 确认结单"（后端要求先保存报表），本页不做任何落账。
 * <p>⚠️ 剩余未收 > 0 时先二次确认：后端**不拦**"未收满就结单"，而结单后加工单变"已完成"、
 * 本页就不能再收货了（要改回来得先反结单）。</p>
 */
async function goClose() {
  const remaining = Number(summary.value?.remainingQuantity || 0)
  if (remaining > 0) {
    try {
      await ElMessageBox.confirm(
        `该单还有 ${remaining} 件未收；结单后不能再收货（需先反结单）。确定进入结单报表？`, '结单前确认', { type: 'warning' })
    } catch { return }
  }
  router.push(`/outsource/order/close/${orderId}`)
}

/**
 * 从「成品收货」列表带参（?add=1 / ?defect=1）进入时自动打开对应弹窗，一步完成收货。
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
  if (flag === 'add') { if (canDeliver.value) openAdd(); else ElMessage.warning('只有生产中的加工单可录入收货') }
  else { if (canDeliver.value) openDefectReturn(); else ElMessage.warning('只有生产中的加工单可做加工退货') }
}

onActivated(async () => { await loadData(); await maybeAutoOpen() })
</script>

<template>
  <div v-loading="loading">
    <el-card shadow="never" style="margin-bottom:12px">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
        <el-button size="small" @click="router.push('/outsource/order/delivery')">← 返回列表</el-button>
        <span style="font-size:var(--app-font-md)">加工单号：<b>{{ order.code || '-' }}</b></span>
        <span>加工厂：<b>{{ order.factoryName || '-' }}</b></span>
        <span>状态：<el-tag :type="OutsourceOrderStatusTag[order.status] || 'info'" size="small">{{ OutsourceOrderStatusLabel[order.status] || order.status }}</el-tag></span>
        <span>计划完成：{{ $fmtDate(order.planEndDate) }}</span>
        <el-button type="primary" size="small" @click="router.push(`/outsource/order/detail/${orderId}`)">查看加工单详情</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">订单总量</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0">{{ summary.totalQuantity || 0 }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">已收数量</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0;color:var(--app-color-success)">{{ summary.deliveredQuantity || 0 }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">剩余数量</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0;color:var(--app-color-warning)">{{ summary.remainingQuantity || 0 }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">收货进度</p><p style="font-size:var(--app-font-num);font-weight:600;margin:4px 0;color:var(--app-color-primary)">{{ progress }}%</p></el-card></el-col>
    </el-row>

    <el-card shadow="never" style="margin-bottom:12px">
      <el-progress :percentage="progress" :stroke-width="16" :text-inside="true" :color="progress >= 100 ? 'var(--app-color-success)' : 'var(--app-color-primary)'" />
    </el-card>

    <el-card shadow="never" style="margin-bottom:12px" v-if="summary.productStats && summary.productStats.length > 1">
      <template #header><span style="font-weight:600">按产品分类统计</span></template>
      <el-table :data="summary.productStats" border size="small">
        <el-table-column prop="sku" label="SKU" width="130" />
        <el-table-column prop="productName" label="产品名称" min-width="150" />
        <el-table-column prop="totalQuantity" label="订单数量" width="100" />
        <el-table-column label="已收数量" width="100"><template #default="{ row }"><span style="color:var(--app-color-success);font-weight:500">{{ row.deliveredQuantity }}</span></template></el-table-column>
        <el-table-column label="剩余数量" width="100"><template #default="{ row }"><span :style="{ color: Number(row.remainingQuantity) <= 0 ? 'var(--app-color-success)' : 'var(--app-color-warning)', fontWeight: '500' }">{{ row.remainingQuantity }}</span></template></el-table-column>
        <el-table-column label="进度" width="180"><template #default="{ row }"><el-progress :percentage="Number(row.totalQuantity) === 0 ? 0 : Math.min(100, Math.round(Number(row.deliveredQuantity) / Number(row.totalQuantity) * 100))" :stroke-width="12" :color="Number(row.remainingQuantity) <= 0 ? 'var(--app-color-success)' : 'var(--app-color-primary)'" /></template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <span style="font-weight:600">收货记录</span>
        <div style="display:flex;gap:8px">
          <template v-if="canDeliver">
            <el-button type="primary" size="small" @click="openAdd">新增收货</el-button>
            <el-button type="danger" size="small" @click="openDefectReturn">加工退货</el-button>
            <!-- 2026-09-21（用户口径「加工单详情页面的结单按钮放到成品收货里」）：结单入口（跳结单报表页）；
                 未收满时二次确认（后端不拦未收满即结单，而结单后本页就不能再收货） -->
            <el-button type="warning" size="small" @click="goClose">结单</el-button>
          </template>
          <!-- 2026-09-21（用户口径「统一命名」）：本页退回一律走「加工退货」红冲收货（原名「退不良」→「加工退货」→ 定稿「加工退货」，
               与「加工退货」页的「加工退货」页签同一个词）⇒ 原「退货」按钮已移除 -->
        </div>
      </div>
      <!--
        2026-09-21（用户：收货记录「一行就显示完毕、不要左右滑动」⇒ 随后「有一个详细会不会好一点，
        那列表就不用显示这么多信息了」）：采纳"列表只做扫读、明细看详情"的结构。
        列表瘦身为 **7 列**（合计约 686px，容器约 963px）：富余的 ~277px 全部补给两个 min-width 列
        （产品名称/等级分布）⇒ 基本不再出现省略号；
        「收货仓库 / 物流单号 / 备注 / 附件」+ SKU / 加工退货规格 / 记录ID / 创建时间
        **一律移入行内「详情」抽屉**（见下方 el-drawer）。
        ⚠️ 日后加列前先算总宽：容器 ≈ window.innerWidth − 299（1262px 窗口 → 963px），别又撑出横向滚动。
      -->
      <el-table :data="deliveries" border stripe size="small" :row-class-name="deliveryRowClass">
        <el-table-column label="收货日期" width="92"><template #default="{ row }">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column label="产品名称" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ productNameOf(row) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="60" align="center">
          <template #default="{ row }"><el-tag v-if="row.deliveryType" :type="row.deliveryType === DeliveryType.DEFECT_RETURN ? 'warning' : 'info'" size="small">{{ typeTextOf(row) }}</el-tag><span v-else style="color:var(--app-text-secondary)">—</span></template>
        </el-table-column>
        <el-table-column label="等级分布" min-width="130">
          <template #default="{ row }">
            <span v-if="row.aQty || row.bQty || row.cQty || row.defectQty">
              <span style="color:var(--app-color-success)">A{{ row.aQty || 0 }}</span> /
              <span style="color:var(--app-color-primary)">B{{ row.bQty || 0 }}</span> /
              <span style="color:var(--app-color-warning)">C{{ row.cQty || 0 }}</span> /
              <span style="color:var(--app-color-danger)">不良{{ row.defectQty || 0 }}</span>
            </span>
            <span v-else style="color:var(--app-text-secondary)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="64" align="right"><template #default="{ row }"><span :style="{ color: Number(row.quantity) < 0 ? 'var(--app-color-danger)' : '' }">{{ row.quantity }}</span></template></el-table-column>
        <el-table-column label="状态" width="60"><template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <!-- 详情：仓库/物流单号/备注/附件/SKU/等级明细/创建时间等明细字段都在抽屉里看 -->
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
            <el-button type="success" link size="small" v-if="row.status === DocStatus.DRAFT" @click="handleAudit(row)">审核</el-button>
            <el-button type="warning" link size="small" v-if="row.status === DocStatus.AUDITED" @click="handleUnaudit(row)">反审核</el-button>
            <el-button type="primary" link size="small" v-if="row.status === DocStatus.DRAFT" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" v-if="row.status === DocStatus.DRAFT" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑收货弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑收货记录' : '新增收货记录'" width="600px" :close-on-click-modal="false">
      <el-form :model="form" label-width="85px" size="small">
        <el-form-item required label="产品名称"><el-select v-model="form.productId" filterable style="width:100%" placeholder="选择订单产品"><el-option v-for="p in products" :key="p.id" :label="p.productName" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="总数量"><el-input :model-value="totalGrade" readonly placeholder="由等级数量自动合计" /></el-form-item>
        <el-form-item label="等级数量">
          <el-row :gutter="8">
            <!-- 数量一律整数：el-input-number 无 prepend 插槽，故保留 el-input + change 取整（2026-09-16） -->
            <el-col :span="6"><el-input v-model="form.aQty" type="number" @change="form.aQty = Math.round(Number(form.aQty) || 0)"><template #prepend>A规</template></el-input></el-col>
            <el-col :span="6"><el-input v-model="form.bQty" type="number" @change="form.bQty = Math.round(Number(form.bQty) || 0)"><template #prepend>B规</template></el-input></el-col>
            <el-col :span="6"><el-input v-model="form.cQty" type="number" @change="form.cQty = Math.round(Number(form.cQty) || 0)"><template #prepend>C规</template></el-input></el-col>
            <el-col :span="6"><el-input v-model="form.defectQty" type="number" @change="form.defectQty = Math.round(Number(form.defectQty) || 0)"><template #prepend>不良</template></el-input></el-col>
          </el-row>
        </el-form-item>
        <el-form-item required label="收货仓库"><RemoteSelect v-model="warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" style="width:100%" placeholder="选择入库仓库" /></el-form-item>
        <el-form-item label="收货日期"><el-input v-model="form.deliveryDate" type="date" /></el-form-item>
        <el-form-item label="物流单号"><el-input v-model="form.trackingNo" placeholder="选填" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="选填" /></el-form-item>
        <el-form-item label="收货图片">
          <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile ? '#67c23a' : '#dcdfe6', background: uploadFile ? '#f0f9eb' : '#fafafa' }">
            <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">📎 {{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveFile">移除</el-button></div></template>
            <template v-else-if="form.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:var(--app-color-primary)">📎 已有图片</span><el-button type="primary" size="small" @click.stop="openAttach(form.attachUrl)">查看</el-button><span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">可拖拽新文件替换</span></div></template>
            <template v-else><p style="color:#909399;margin:0">拖拽图片到此处，或点击选择</p></template>
            <input v-if="!form.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="handleSubmit()">保存</el-button></template>
    </el-dialog>

    <!-- 加工退货弹窗（原名「退不良（拆分还料）」→「加工退货（拆分还料）」，2026-09-21 统一命名为「加工退货」） -->
    <el-dialog v-model="defectVisible" title="加工退货（拆分还料）" width="780px" :close-on-click-modal="false">
      <el-form-item label="加工退货仓库" style="margin-bottom:12px"><RemoteSelect v-model="defectWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" style="width:100%" placeholder="选择扣减的成品仓库" @change="onDefectWhChange" /></el-form-item>
      <el-table :data="defectItems" border size="small">
        <el-table-column prop="productName" label="产品" min-width="160" />
        <el-table-column label="A规" width="130"><template #default="{ row }"><div style="font-size:var(--app-font-xs);color:var(--app-text-regular)">库存 {{ row.stocks?.a ?? 0 }}</div><el-input-number v-model="row.aQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
        <el-table-column label="B规" width="130"><template #default="{ row }"><div style="font-size:var(--app-font-xs);color:var(--app-text-regular)">库存 {{ row.stocks?.b ?? 0 }}</div><el-input-number v-model="row.bQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
        <el-table-column label="C规" width="130"><template #default="{ row }"><div style="font-size:var(--app-font-xs);color:var(--app-text-regular)">库存 {{ row.stocks?.c ?? 0 }}</div><el-input-number v-model="row.cQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
        <el-table-column label="不良" width="130"><template #default="{ row }"><div style="font-size:var(--app-font-xs);color:var(--app-text-regular)">库存 {{ row.stocks?.defect ?? 0 }}</div><el-input-number v-model="row.defectQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="defectVisible = false">取消</el-button><el-button type="warning" :loading="defectSaving" @click="handleDefectReturn">确认加工退货</el-button></template>
    </el-dialog>

    <!--
      收货记录「详情」抽屉（2026-09-21 用户建议）：列表已瘦身为 7 列，明细字段都在这里看。
      沿用本项目既有抽屉惯例（sale/outbound、finance 系列：el-drawer + el-descriptions :column="2" border）。
      抽屉**只读** —— 审核/退货/编辑/删除等动作仍留在列表的「操作」列，避免两处入口不一致。
    -->
    <el-drawer v-model="detailVisible" title="收货记录详情" size="60%">
      <el-descriptions v-if="detailRow" :column="2" border>
        <el-descriptions-item label="记录ID">{{ detailRow.id }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="DocStatusTag[detailRow.status] || 'info'" size="small">{{ DocStatusLabel[detailRow.status] || detailRow.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="收货日期">{{ $fmtDate(detailRow.deliveryDate) }}</el-descriptions-item>
        <!-- 登记时间：原先任何界面都看不到，详情里补上（后端 create_time） -->
        <el-descriptions-item label="登记时间">{{ detailRow.createTime ? String(detailRow.createTime).replace('T', ' ') : '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ productNameOf(detailRow) }}</el-descriptions-item>
        <!-- SKU：原先任何界面都看不到 -->
        <el-descriptions-item label="SKU">{{ skuOf(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeTextOf(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="加工退货规格">{{ qualityTextOf(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="收货仓库">{{ warehouseNameOf(detailRow) }}</el-descriptions-item>
        <el-descriptions-item label="物流单号">{{ detailRow.trackingNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="A规数量">{{ detailRow.aQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="B规数量">{{ detailRow.bQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="C规数量">{{ detailRow.cQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="不良数量">{{ detailRow.defectQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="总数量"><span :style="{ color: Number(detailRow.quantity) < 0 ? 'var(--app-color-danger)' : '', fontWeight: '600' }">{{ detailRow.quantity }}</span></el-descriptions-item>
        <el-descriptions-item label="收货图片">
          <el-button v-if="detailRow.attachUrl" type="primary" link size="small" @click="openAttach(detailRow.attachUrl)">查看图片</el-button>
          <span v-else style="color:var(--app-text-placeholder)">—</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailRow.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped>
.drop-zone { position:relative; border:2px dashed var(--app-border-color); border-radius:8px; padding:16px; text-align:center; transition:all .3s; cursor:pointer; width:100% }
.drop-zone:hover { border-color:var(--app-color-primary); background:#ecf5ff }
:deep(.defect-row) { background:#fdf6ec !important }
</style>
