<script setup lang="ts">
defineOptions({ name: 'OutsourceOrderDetail' })

import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { getProjectBom } from '@/api/system'
import { exportContractPdf } from '@/api/contract-template'
import { OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag, DeliveryType, DeliveryTypeLabel } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

const route = useRoute(); const router = useRouter()
const loading = ref(true); const saving = ref(false)
const activeTab = ref('detail')
const uploadFile = ref<File | null>(null)

// BOM物料库存缺料
const materialStockMap = ref<Record<string, any>>({})

async function loadMaterialStock() {
  if (!form.id) return
  try {
    const r = await request.get<any, any>(`/outsource/order/${form.id}/material-stock`)
    if (r?.materials) {
      const map: Record<string, any> = {}
      for (const m of r.materials) { if (m.materialId != null) map[m.materialId] = m }
      materialStockMap.value = map
    }
  } catch { materialStockMap.value = {} }
}
function getStock(materialId: number | string) {
  const s = materialId != null ? materialStockMap.value[materialId] : undefined
  return s || { stockQuantity: 0, shortage: 0 }
}
// 交货记录表格行样式：退不良行高亮
function deliveryRowClass({ row }: { row: any }) {
  return row.deliveryType === DeliveryType.DEFECT_RETURN ? 'defect-row' : ''
}
// 按物料ID关联反查物料名称（实体已不冗余存name，统一用ID查）
function matName(id: number | string) {
  if (id == null) return ''
  const o = materialOptions.value.find((v:any) => v.id === id)
  return o?.materialName || ''
}
function goPurchase(row: any) {
  const s = getStock(row.materialId)
  const ids = (s.supplierIds || '') as string; const firstId = ids.split(',')[0]?.trim()
  const p = new URLSearchParams(); if (firstId) p.set('supplierId', firstId)
  if (s.materialId) p.set('materialId', String(s.materialId))
  p.set('materialName', matName(row.materialId)); p.set('bomTypeId', String(row.bomTypeId ?? ''))
  p.set('unit', row.unit || ''); p.set('quantity', String(s.shortage || 0))
  router.push('/outsource/material-order/add?' + p.toString())
}
function goOutsource(row: any) {
  const s = getStock(row.materialId)
  const ids = (s.supplierIds || '') as string; const firstId = ids.split(',')[0]?.trim()
  const p = new URLSearchParams(); p.set('orderType', '委外')
  if (firstId) p.set('supplierId', firstId)
  if (s.materialId) p.set('materialId', String(s.materialId))
  p.set('materialName', matName(row.materialId)); p.set('bomTypeId', String(row.bomTypeId ?? ''))
  p.set('unit', row.unit || ''); p.set('quantity', String(s.shortage || 0))
  router.push('/outsource/material-order/add?' + p.toString())
}

const form = reactive({
  id: undefined as any, code: '', status: '',
  factoryId: undefined as any,
  planStartDate: '', planEndDate: '',
  actualStartDate: '', actualEndDate: '',
  taxIncluded: 0, taxRate: '',
  totalAmount: '', remark: '',
  attachUrl: '', logisticsCompany: '', logisticsNo: ''
})

const products = ref<any[]>([])
const factoryOptions = ref<any[]>([])
const projectOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const bomTypes = ref<any[]>([])
const fetchFactories = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw, supplierType: 'factory' } })
const fetchProjects = (kw: string) => request.get('/dev/project/page', { params: { pageSize: 500, name: kw } })
const fetchMaterials = (kw: string) => request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw } })
const fetchBomTypes = (kw: string) => request.get('/dev/bom-type/enabled', { params: { pageSize: 500, name: kw } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })

// bomTypeId -> 类型名 映射（兜底展示用）
function typeName(id: number | undefined, fallback?: string) {
  if (id != null) { const t = bomTypes.value.find((v: any) => v.id === id); if (t) return t.typeName }
  return fallback || '-'
}
// 数字格式化（保留2位小数，null/undefined 显示 0）
function fmt(v: any) { return v !== undefined && v !== null ? Number(v).toFixed(2) : '0.00' }
// 交货数据
const deliveries = ref<any[]>([])

async function loadOptions() {
  const [f, p, m, b]: any[] = await Promise.all([fetchFactories(''), fetchProjects(''), fetchMaterials(''), fetchBomTypes('')])
  factoryOptions.value = f?.records || []
  projectOptions.value = p?.records || []
  materialOptions.value = m?.records || []
  bomTypes.value = Array.isArray(b) ? b : (b?.records || [])
}

async function loadData() {
  loading.value = true
  try {
    const d = await request.get<any,any>(`/outsource/order/${route.params.id}`)
    if (d) {
      Object.assign(form, {
        id: d.id, code: d.code, status: d.status, factoryId: d.factoryId,
        planStartDate: d.planStartDate || '', planEndDate: d.planEndDate || '',
        actualStartDate: d.actualStartDate || '', actualEndDate: d.actualEndDate || '',
        taxIncluded: d.taxIncluded || 0, taxRate: d.taxRate || '',
        totalAmount: d.totalAmount || '', remark: d.remark || '',
        attachUrl: d.attachUrl || '', logisticsCompany: d.logisticsCompany || '', logisticsNo: d.logisticsNo || ''
      })
    }
    const ps = await request.get<any,any>(`/outsource/order/${route.params.id}/products`)
    products.value = (ps || []).map((p:any) => ({
      ...p, _key: p.id || Date.now() + Math.random(),
      materials: (p.materials || []).map((m:any) => ({ ...m }))
    }))
    if (form.factoryId && !factoryOptions.value.some((f:any)=>f.id===form.factoryId)) {
      try { const sup = await request.get<any,any>(`/supplier/${form.factoryId}`); if (sup) factoryOptions.value.push({id:sup.id,name:sup.name}) } catch (e: any) { console.warn('加载工厂信息失败', e?.message || e) }
    }
    await loadMaterialStock()
    await loadDeliveryData()
    if (form.status === OutsourceOrderStatus.FINISHED || form.status === OutsourceOrderStatus.CANCELLED) loadCloseReport()
  } finally { loading.value = false }
}

function addProduct() { products.value.push({ _key: Date.now(), projectId: undefined, productName: '', productSpec: '', quantity: 1, unitPrice: 0, amount: 0, remark: '', materials: [] }) }
function removeProduct(idx: number) { products.value.splice(idx, 1) }
function onProjectSelect(idx: number, pid: number) {
  const proj = projectOptions.value.find((v:any) => v.id === pid)
  if (proj) {
    products.value[idx].projectId = pid
    // 产品名称取项目总成名称(assemblyName)，与产品主数据一致；无总成名称时回退项目名
    products.value[idx].productName = proj.assemblyName || proj.name || ''
    products.value[idx].productSpec = proj.productSpec || ''
    loadBomMaterials(idx, pid)
  }
}
async function loadBomMaterials(idx: number, pid: number) {
  try {
    const mats:any = await getProjectBom(pid)
    if (mats && Array.isArray(mats)) {
      const qty = Number(products.value[idx].quantity) || 1
      products.value[idx].materials = mats.map((m:any) => {
        const opt = materialOptions.value.find((o:any) => o.id === m.outsourceMaterialId)
        return { materialId: m.outsourceMaterialId || null, materialName: opt?.materialName || '', bomTypeId: m.bomTypeId || null, unit: m.unit || '', demandQuantity: +(qty * Number(m.quantity || 0)).toFixed(4), lossRate: m.lossRate || 0, remark: '' }
      })
    }
  } catch { products.value[idx].materials = [] }
}
function calcAmount(idx: number) { const p = products.value[idx]; p.amount = (Number(p.quantity) || 0) * (Number(p.unitPrice) || 0) }
function onMatSelect(idx: number, mi: number, mat: any) { const m = materialOptions.value.find((v:any) => v.id === mi); if (m) { mat.materialName = m.materialName; mat.bomTypeId = m.bomTypeId; mat.unit = m.unit } }
function addMaterial(idx: number) { products.value[idx].materials.push({ materialId: undefined, materialName: '', bomTypeId: undefined, unit: '', demandQuantity: 1, lossRate: 0, remark: '' }) }
function removeMaterial(pi: number, mi: number) { products.value[pi].materials.splice(mi, 1) }

function openAttach(url:string) { window.open(url + '?inline=true') }
function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }

async function handleDeleteAttach() {
  try {
    await ElMessageBox.confirm('确定删除附件吗？', '删除附件', { confirmButtonText:'删除', cancelButtonText:'取消', type:'warning' })
    await request.delete(`/outsource/order/${form.id}/attach`); ElMessage.success('附件已删除'); await loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleSave() {
  saving.value = true
  try {
    if (uploadFile.value) { const fd = new FormData(); fd.append('file', uploadFile.value); const res = await request.post<any,string>('/dev/file/upload', fd); form.attachUrl = res as unknown as string }
    await request.put(`/outsource/order/${form.id}`, { ...form, products: products.value }); ElMessage.success('保存成功'); await loadData()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) } finally { saving.value = false }
}
async function handleAudit() {
  try { await ElMessageBox.confirm('审核后加工单将进入生产状态。', '审核加工单', { type:'warning' }); await request.put(`/outsource/order/${form.id}/audit`); ElMessage.success('审核通过，进入生产中'); await loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleUnaudit() {
  try { await ElMessageBox.confirm('反审核将回滚所有交货记录和库存变动，确认继续？', '反审核加工单', { type:'warning' }); await request.put(`/outsource/order/${form.id}/unaudit`); ElMessage.success('已反审核，回到待审核状态'); await loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}


const defectVisible = ref(false); const defectSaving = ref(false)
const defectItems = ref<any[]>([]); const defectWarehouseId = ref<number>()
const defectWarehouseInfo = ref<any>(null)
// 四规格初始空库存
const emptyDefectStock = () => ({ a: 0, b: 0, c: 0, defect: 0 })
function openDefectReturn() {
  defectItems.value = (products.value || []).map((p: any) => ({ productId: p.id, productName: p.productName, masterId: p.productId, aQty: undefined as any, bQty: undefined as any, cQty: undefined as any, defectQty: undefined as any, stocks: emptyDefectStock() }))
  defectWarehouseId.value = undefined; defectWarehouseInfo.value = null; defectVisible.value = true; loadDelWarehouses()
}
function onDefectWhChange(whId: number) {
  defectWarehouseId.value = whId
  if (!whId) { defectWarehouseInfo.value = null; return }
  // 按产品主数据ID查询该仓库该产品各规格库存（productName 为快照名且实体无此字段，不能用名称匹配）
  request.get<any,any>('/warehouse/stock/page', { params: { pageSize: 500, stockType: 'PRODUCT' } }).then((r: any) => {
    const stocks = r?.records || []
    const masterId = defectItems.value[0]?.masterId
    const s = stocks.find((s:any) => s.warehouseId === whId && s.productId === masterId)
    const qmap: Record<string, 'a' | 'b' | 'c' | 'defect'> = { A: 'a', B: 'b', C: 'c', DEFECT: 'defect' }
    const stocksOf: { a: number; b: number; c: number; defect: number } = emptyDefectStock()
    // 该仓该产品可能有多行(不同规格)，全部取出来按规格填充
    const rows = stocks.filter((s:any) => s.warehouseId === whId && s.productId === masterId)
    for (const row of rows) {
      const key = qmap[row.qualityType]; if (key) stocksOf[key] = Number(row.quantity || 0)
    }
    defectItems.value = defectItems.value.map((it: any) => ({ ...it, stocks: { ...stocksOf } }))
    defectWarehouseInfo.value = s || { quantity: 0 }
  }).catch(() => { defectWarehouseInfo.value = { quantity: 0 } })
}
// ===== 交货管理（Tab 2）=====
const delSummary = ref<any>({})
const delProducts = ref<any[]>([])
const delDialogVisible = ref(false); const delIsEdit = ref(false); const delEditId = ref<number>()
const delSaving = ref(false); const delUploadFile = ref<File | null>(null)
const delWarehouseId = ref<number>(); const delWarehouseOptions = ref<any[]>([])
const delForm = reactive({ productId: undefined as any, quantity: '', aQty: 0 as number, bQty: 0 as number, cQty: 0 as number, defectQty: 0 as number, deliveryDate: new Date().toISOString().split('T')[0], trackingNo: '', remark: '', attachUrl: '' })

// 四等级自动合计
const delGradeSum = computed(() => {
  return (Number(delForm.aQty) || 0) + (Number(delForm.bQty) || 0) + (Number(delForm.cQty) || 0) + (Number(delForm.defectQty) || 0)
})

const delProgress = computed(() => {
  const t = Number(delSummary.value.totalQuantity || 0); const d = Number(delSummary.value.deliveredQuantity || 0)
  return t === 0 ? 0 : Math.min(100, Math.round((d / t) * 100))
})

async function loadDeliveryData() {
  try {
    const [dList, dSummary, prods] = await Promise.all([
      request.get<any,any>(`/outsource/order-delivery/list/${form.id}`),
      request.get<any,any>(`/outsource/order-delivery/summary/${form.id}`),
      request.get<any,any>(`/outsource/order/${form.id}/products`)
    ])
    deliveries.value = dList || []; delSummary.value = dSummary || {}; delProducts.value = prods || []
    if (delWarehouseOptions.value.length === 0) loadDelWarehouses()
  } catch (e: any) { console.warn('加载交货数据失败', e?.message || e) }
}
async function loadDelWarehouses() {
  const r = await fetchWarehouses(''); delWarehouseOptions.value = r?.records || []
}
function delOpenAdd() {
  delIsEdit.value = false; delEditId.value = undefined; delWarehouseId.value = undefined; delUploadFile.value = null
  Object.assign(delForm, { productId: undefined, quantity: '', aQty: 0, bQty: 0, cQty: 0, defectQty: 0, deliveryDate: new Date().toISOString().split('T')[0], trackingNo: '', remark: '', attachUrl: '' })
  delDialogVisible.value = true; loadDelWarehouses()
}
function delOpenEdit(row: any) {
  delIsEdit.value = true; delEditId.value = row.id; delWarehouseId.value = row.warehouseId || undefined; delUploadFile.value = null
  Object.assign(delForm, { productId: row.productId, quantity: row.quantity, aQty: Number(row.aQty) || 0, bQty: Number(row.bQty) || 0, cQty: Number(row.cQty) || 0, defectQty: Number(row.defectQty) || 0, deliveryDate: row.deliveryDate, trackingNo: row.trackingNo || '', remark: row.remark || '', attachUrl: row.attachUrl || '' })
  delDialogVisible.value = true; loadDelWarehouses()
}
function delHandleDragOver(e: DragEvent) { e.preventDefault() }
function delHandleDrop(e: DragEvent) { e.preventDefault(); const f = e.dataTransfer?.files?.[0]; if (f) delUploadFile.value = f }
function delHandleFileSelect(e: Event) { const f = (e.target as HTMLInputElement).files?.[0]; if (f) delUploadFile.value = f }
function delHandleRemoveFile() { delUploadFile.value = null }
async function delHandleSubmit(forceDelivery = false) {
  if (!delForm.productId) { ElMessage.warning('请选择产品名称'); return }
  if (delGradeSum.value <= 0) { ElMessage.warning('请至少填写一个等级的数量'); return }
  if (!delWarehouseId.value) { ElMessage.warning('请选择收货仓库'); return }
  delSaving.value = true
  try {
    if (delUploadFile.value) { const fd = new FormData(); fd.append('file', delUploadFile.value); const res = await request.post<any,string>('/dev/file/upload', fd); delForm.attachUrl = res as unknown as string }
    const aQty = Number(delForm.aQty) || 0
    const bQty = Number(delForm.bQty) || 0
    const cQty = Number(delForm.cQty) || 0
    const defectQty = Number(delForm.defectQty) || 0
    const body = { productId: delForm.productId, quantity: delGradeSum.value, aQty, bQty, cQty, defectQty, deliveryDate: delForm.deliveryDate, trackingNo: delForm.trackingNo, remark: delForm.remark, attachUrl: delForm.attachUrl, orderId: form.id, warehouseId: delWarehouseId.value || null }
    console.log('[交货] 提交body:', JSON.stringify(body), '原始delForm.aQty=', delForm.aQty, '转后aQty=', aQty)
    const params = forceDelivery ? { params: { forceDelivery: true } } : {}
    let res: any
    if (delIsEdit.value && delEditId.value) {
      res = await request.put(`/outsource/order-delivery/${delEditId.value}`, body, params)
    } else {
      res = await request.post('/outsource/order-delivery', body, params)
    }
    console.log('[交货] 后端响应:', JSON.stringify(res))
    // 检查是否需要确认缺料（canProceed 不是 true 时都视为缺料）
    if (res && res.canProceed !== true) {
      delSaving.value = false
      const shortages = (res.shortages || []) as any[]
      let html = '<div style="margin-bottom:8px">以下物料库存不足，是否确认强制出库？</div>'
      html += '<table style="width:100%;border-collapse:collapse;font-size:13px">'
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
        await ElMessageBox.confirm(html, '缺料提示', {
          confirmButtonText: '确认强制出库',
          cancelButtonText: '取消',
          type: 'warning',
          dangerouslyUseHTMLString: true
        })
      } catch { return }
      return delHandleSubmit(true)
    }
    ElMessage.success(delIsEdit.value ? '交货记录已更新' : '交货记录已保存')
    delDialogVisible.value = false; loadDeliveryData(); loadMaterialStock()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) }
  } finally { delSaving.value = false }
}
async function delHandleDelete(row: any) {
  try { await ElMessageBox.confirm('确定删除该交货记录吗？', '删除', { type: 'warning' }); await request.delete(`/outsource/order-delivery/${row.id}`); ElMessage.success('已删除'); loadDeliveryData() }
  catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function delHandleAudit(row: any) {
  try { await ElMessageBox.confirm('确定审核该交货记录吗？审核后将扣减物料、成品入库并生成应付。', '审核', { type: 'warning' }); await request.put(`/outsource/order-delivery/${row.id}/audit`); ElMessage.success('已审核'); loadDeliveryData() }
  catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function delHandleUnaudit(row: any) {
  try { await ElMessageBox.confirm('确定反审核该交货记录吗？反审核后将回滚库存与应付，回到草稿。', '反审核', { type: 'warning' }); await request.put(`/outsource/order-delivery/${row.id}/unaudit`); ElMessage.success('已反审核'); loadDeliveryData() }
  catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleDefectReturn() {
  // 收集所有产品各规格>0的数量
  const data: any[] = []
  for (const r of defectItems.value) {
    for (const [qualityType, qtyKey] of [['A','aQty'],['B','bQty'],['C','cQty'],['DEFECT','defectQty']] as const) {
      const q = Number(r[qtyKey]); if (q > 0) data.push({ productId: r.productId, qualityType, quantity: q })
    }
  }
  if (data.length === 0) { ElMessage.warning('请输入退不良数量'); return }
  if (!defectWarehouseId.value) { ElMessage.warning('请选择退不良仓库'); return }
  defectSaving.value = true
  try {
    // 逐规格调用退不良接口（存草稿），审核时统一落账
    for (const r of data) {
      await request.post(`/outsource/order-delivery/return-defect/${form.id}`, { productId: r.productId, qualityType: r.qualityType, quantity: r.quantity, warehouseId: defectWarehouseId.value })
    }
    ElMessage.success('退不良草稿已保存，请在交货记录中审核'); defectVisible.value = false; loadData(); loadDeliveryData()
  } catch (e: any) { ElMessage.error(e?.message || '退不良失败') } finally { defectSaving.value = false }
}

function exportPdf() {
  const url = exportContractPdf(form.id as number)
  request.get(url, { responseType: 'blob' }).then((res: any) => {
    // 错误响应为 JSON Blob（application/json），成功响应为 DOCX Blob，据此区分
    const blob = res instanceof Blob ? res : new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
    if (blob.type && blob.type.includes('application/json')) {
      blob.text().then((txt: string) => {
        try {
          const err = JSON.parse(txt)
          ElMessage.error(err?.msg || '导出失败')
        } catch { ElMessage.error('导出失败') }
      })
      return
    }
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob); link.download = `委外加工合同-${form.code}.docx`; link.click(); URL.revokeObjectURL(link.href)
    ElMessage.success('合同已下载')
  }).catch(() => { ElMessage.error('导出失败') })
}

const closeReport = ref<any>({})
const closeItems = ref<any[]>([])
const closeLoading = ref(false)

async function loadCloseReport() {
  if (form.status !== OutsourceOrderStatus.FINISHED && form.status !== OutsourceOrderStatus.CANCELLED) return
  closeLoading.value = true
  try {
    const r = await request.get<any, any>(`/outsource/order/${route.params.id}/close-report`)
    Object.assign(closeReport, r || {})
    closeItems.value = r?.items || []
  } catch { closeItems.value = [] }
  finally { closeLoading.value = false }
}

onMounted(() => { loadOptions(); loadData() })
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-tabs v-model="activeTab" style="margin-bottom:12px" @tab-change="(t:any)=>{if(t==='close')loadCloseReport()}">
      <el-tab-pane label="加工详情" name="detail" />
      <el-tab-pane label="交货管理" name="delivery" />
      <el-tab-pane v-if="form.status===OutsourceOrderStatus.FINISHED||form.status===OutsourceOrderStatus.CANCELLED" label="结单详情" name="close" />
    </el-tabs>

    <!-- Tab 1: 加工详情 -->
    <template v-if="activeTab === 'detail'">
      <el-card shadow="never">
        <template #header><span style="font-weight:600">基础信息</span></template>
        <el-form :model="form" label-width="90px" size="small">
          <el-row :gutter="12">
            <el-col :span="8"><el-form-item label="单号"><el-input :model-value="form.code" readonly /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="状态"><el-tag :type="OutsourceOrderStatusTag[form.status]||'info'" size="small">{{ OutsourceOrderStatusLabel[form.status] || form.status }}</el-tag></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="加工厂"><RemoteSelect v-model="form.factoryId" :fetch="fetchFactories" style="width:100%" :disabled="form.status!==OutsourceOrderStatus.PENDING" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="计划开始"><el-input v-model="form.planStartDate" type="date" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="计划完成"><el-input v-model="form.planEndDate" type="date" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="实际开始"><el-input :model-value="form.actualStartDate" readonly /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="实际完成"><el-input :model-value="form.actualEndDate" readonly /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="总金额"><el-input :model-value="Number(form.totalAmount||0).toFixed(2)" readonly /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="含税"><el-switch v-model="form.taxIncluded" :active-value="1" :inactive-value="0" disabled /></el-form-item></el-col>
            <el-col :span="8" v-if="form.taxIncluded"><el-form-item label="税率(%)"><el-input :model-value="form.taxRate" disabled /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
          <div style="display:flex;gap:8px;margin-top:12px">
            <el-button v-if="form.status===OutsourceOrderStatus.PENDING" type="success" size="small" @click="handleAudit">审核</el-button>
            <el-button v-if="form.status===OutsourceOrderStatus.PRODUCING" type="danger" size="small" @click="handleUnaudit">反审核</el-button>
            <el-button v-if="form.status===OutsourceOrderStatus.PRODUCING" type="warning" size="small" @click="router.push(`/outsource/order/close/${form.id}`)">结单</el-button>
            <el-button v-if="form.status===OutsourceOrderStatus.PRODUCING || form.status===OutsourceOrderStatus.FINISHED" type="warning" size="small" @click="openDefectReturn">退不良</el-button>
            <el-button type="primary" size="small" :loading="saving" @click="handleSave" :disabled="form.status===OutsourceOrderStatus.CANCELLED">保存</el-button>
          </div>
        </el-form>
      </el-card>

      <el-card v-for="(p, pi) in products" :key="p._key" shadow="never" style="margin-top:12px">
        <template #header><div style="display:flex;align-items:center;justify-content:space-between"><span style="font-weight:600">加工产品 #{{ pi + 1 }}</span><el-button type="danger" size="small" text @click="removeProduct(pi)" v-if="products.length>1 && form.status===OutsourceOrderStatus.PENDING">删除产品</el-button></div></template>
        <el-form :model="p" label-width="90px" size="small">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="加工产品"><RemoteSelect v-model="p.projectId" :fetch="fetchProjects" :label-key="(row:any)=>row.assemblyName || row.name" filterable clearable style="width:100%" :disabled="form.status!==OutsourceOrderStatus.PENDING" @change="(v:any)=>onProjectSelect(pi,v)" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="数量"><el-input v-model="p.quantity" type="number" :disabled="form.status!==OutsourceOrderStatus.PENDING" @change="calcAmount(pi)" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="单价"><el-input v-model="p.unitPrice" type="number" :disabled="form.status!==OutsourceOrderStatus.PENDING" @change="calcAmount(pi)" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="小计"><el-input :model-value="p.amount" readonly /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="备注"><el-input v-model="p.remark" :disabled="form.status!==OutsourceOrderStatus.PENDING" /></el-form-item></el-col>
          </el-row>
        </el-form>
        <div style="margin-top:8px">
          <div style="margin-bottom:6px"><span style="font-weight:500;font-size:var(--app-font-sm)">BOM物料清单</span></div>
          <el-table v-if="p.materials && p.materials.length" :data="p.materials" border size="small" class="bom-table">
            <el-table-column label="类型" width="70"><template #default="{row}">{{ typeName(row.bomTypeId) }}</template></el-table-column>
            <el-table-column label="物料名称" min-width="120"><template #default="{row}">{{ matName(row.materialId) }}</template></el-table-column>
            <el-table-column prop="unit" label="单位" width="55" />
            <el-table-column label="需求" width="70"><template #default="{row}">{{ row.demandQuantity }}</template></el-table-column>
            <el-table-column label="已出货消耗" width="80"><template #default="{row}"><span :style="{color: Number(getStock(row.materialId).shippedConsumed||0)>0?'var(--app-color-primary)':''}">{{ getStock(row.materialId).shippedConsumed || 0 }}</span></template></el-table-column>
            <el-table-column label="剩余需求" width="75"><template #default="{row}">{{ getStock(row.materialId).remainingDemand || row.demandQuantity }}</template></el-table-column>
            <el-table-column label="库存" width="70"><template #default="{row}"><span :style="{color: Number(getStock(row.materialId).stockQuantity||0) < Number(getStock(row.materialId).remainingDemand||row.demandQuantity) ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ getStock(row.materialId).stockQuantity || 0 }}</span></template></el-table-column>
            <el-table-column label="可能在途" width="80"><template #default="{row}"><span :style="{color: Number(getStock(row.materialId).inTransit||0) > 0 ? 'var(--app-color-primary)' : ''}">{{ getStock(row.materialId).inTransit || 0 }}</span></template></el-table-column>
            <el-table-column label="缺料" width="70"><template #default="{row}"><span :style="{color: Number(getStock(row.materialId).shortage||0) > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ getStock(row.materialId).shortage || 0 }}</span></template></el-table-column>
            <el-table-column label="损耗率(%)" width="85"><template #default="{row}"><el-input v-model="row.lossRate" size="small" :disabled="form.status!==OutsourceOrderStatus.PENDING" /></template></el-table-column>
            <el-table-column label="备注" min-width="80"><template #default="{row}"><el-input v-model="row.remark" size="small" :disabled="form.status!==OutsourceOrderStatus.PENDING" /></template></el-table-column>
            <el-table-column label="操作" width="130" align="center" class-name="action-col" v-if="form.status===OutsourceOrderStatus.PRODUCING"><template #default="{row}"><div v-if="Number(getStock(row.materialId).shortage||0) > 0" style="display:flex;align-items:center;justify-content:center;gap:4px"><el-button type="warning" link size="small" @click="goPurchase(row)">去采购</el-button><el-button v-if="getStock(row.materialId).hasComponents" type="primary" link size="small" @click="goOutsource(row)">去委外</el-button></div></template></el-table-column>
          </el-table>
          <div v-else style="color:var(--app-text-secondary);font-size:var(--app-font-sm);margin-top:8px">暂无 BOM 物料</div>
        </div>
      </el-card>

      <el-card shadow="never" style="margin-top:12px">
        <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">合同文件</span><el-button type="warning" size="small" @click="exportPdf">导出合同模板</el-button></div></template>
        <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
          <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">{{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
          <template v-else-if="form.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:var(--app-color-primary)">已有附件</span><el-button type="primary" size="small" @click.stop="openAttach(form.attachUrl)">查看</el-button><el-button type="success" size="small"><a :href="form.attachUrl" download style="color:inherit;text-decoration:none">下载</a></el-button><el-button type="danger" size="small" @click.stop="handleDeleteAttach">删除</el-button><span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">可拖拽新文件替换</span></div></template>
          <template v-else><p style="color:#909399;margin:0">拖拽文件到此处，或点击选择</p></template>
          <input v-if="!form.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
        </div>
      </el-card>
    </template>

    <!-- Tab 2: 交货管理 -->
    <template v-if="activeTab === 'delivery'">
      <el-row :gutter="12" style="margin-bottom:12px">
        <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">订单总量</p><p style="font-size:20px;font-weight:600;margin:4px 0">{{ delSummary.totalQuantity || 0 }}</p></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">已交数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:var(--app-color-success)">{{ delSummary.deliveredQuantity || 0 }}</p></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">剩余数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:var(--app-color-warning)">{{ delSummary.remainingQuantity || 0 }}</p></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">交货进度</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:var(--app-color-primary)">{{ delProgress }}%</p></el-card></el-col>
      </el-row>
      <el-card shadow="never" style="margin-bottom:12px">
        <el-progress :percentage="delProgress" :stroke-width="16" :text-inside="true" :color="delProgress>=100?'var(--app-color-success)':'var(--app-color-primary)'" />
      </el-card>
      <el-card shadow="never" style="margin-bottom:12px" v-if="delSummary.productStats && delSummary.productStats.length > 1">
        <template #header><span style="font-weight:600">按产品分类统计</span></template>
        <el-table :data="delSummary.productStats" border size="small">
          <el-table-column prop="productName" label="产品名称" min-width="150" />
          <el-table-column prop="totalQuantity" label="订单数量" width="100" />
          <el-table-column label="已交数量" width="100"><template #default="{row}"><span style="color:var(--app-color-success);font-weight:500">{{ row.deliveredQuantity }}</span></template></el-table-column>
          <el-table-column label="剩余数量" width="100"><template #default="{row}"><span :style="{color: Number(row.remainingQuantity)<=0?'var(--app-color-success)':'var(--app-color-warning)',fontWeight:'500'}">{{ row.remainingQuantity }}</span></template></el-table-column>
          <el-table-column label="进度" width="180"><template #default="{row}"><el-progress :percentage="Number(row.totalQuantity)===0?0:Math.min(100,Math.round(Number(row.deliveredQuantity)/Number(row.totalQuantity)*100))" :stroke-width="12" :color="Number(row.remainingQuantity)<=0?'var(--app-color-success)':'var(--app-color-primary)'" /></template></el-table-column>
        </el-table>
      </el-card>
      <el-card shadow="never">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <span style="font-weight:600">交货记录</span>
          <div v-if="form.status===OutsourceOrderStatus.PRODUCING" style="display:flex;gap:8px">
            <el-button type="primary" size="small" @click="delOpenAdd">新增交货</el-button>
            <el-button type="danger" size="small" @click="openDefectReturn">退不良</el-button>
          </div>
        </div>
        <el-table :data="deliveries" border stripe size="small" :row-class-name="deliveryRowClass">
          <el-table-column label="交货日期" width="110"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
          <el-table-column label="产品名称" min-width="120"><template #default="{row}">{{ delProducts.find((p:any)=>p.id===row.productId)?.productName || '-' }}</template></el-table-column>
          <el-table-column label="类型" width="80" align="center"><template #default="{row}"><el-tag v-if="row.deliveryType===DeliveryType.DEFECT_RETURN" type="warning" size="small">{{ DeliveryTypeLabel[DeliveryType.DEFECT_RETURN] }}</el-tag><span v-else style="color:var(--app-text-secondary)">—</span></template></el-table-column>
          <el-table-column label="收货仓库" width="120">
            <template #default="{row}"><span v-if="row.warehouseId">{{ delWarehouseOptions.find((w:any)=>w.id===row.warehouseId)?.warehouseName || row.warehouseId }}</span><span v-else style="color:var(--app-text-placeholder)">—</span></template>
          </el-table-column>
          <el-table-column label="数量" width="90" align="right"><template #default="{row}"><span :style="{color:Number(row.quantity)<0?'var(--app-color-danger)':''}">{{ row.quantity }}</span></template></el-table-column>
          <el-table-column prop="trackingNo" label="物流单号" width="140" />
          <el-table-column label="附件" width="80" align="center"><template #default="{row}"><el-button v-if="row.attachUrl" type="primary" link size="small" @click="openAttach(row.attachUrl)">查看</el-button><span v-else style="color:var(--app-text-placeholder)">—</span></template></el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{row}"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">
              {{ DocStatusLabel[row.status] || row.status }}
            </el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center">
            <template #default="{row}">
              <el-button type="success" link size="small" v-if="row.status===DocStatus.DRAFT" @click="delHandleAudit(row)">审核</el-button>
              <el-button type="warning" link size="small" v-if="row.status===DocStatus.AUDITED" @click="delHandleUnaudit(row)">反审核</el-button>
              <el-button type="primary" link size="small" v-if="row.status===DocStatus.DRAFT" @click="delOpenEdit(row)">编辑</el-button>
              <el-button type="danger" link size="small" v-if="row.status===DocStatus.DRAFT" @click="delHandleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 新增/编辑交货弹窗 -->
      <el-dialog v-model="delDialogVisible" :title="delIsEdit?'编辑交货记录':'新增交货记录'" width="600px" :close-on-click-modal="false">
        <el-form :model="delForm" label-width="85px" size="small">
          <el-form-item label="产品名称"><el-select v-model="delForm.productId" filterable style="width:100%" placeholder="选择订单产品"><el-option v-for="p in delProducts" :key="p.id" :label="p.productName" :value="p.id" /></el-select></el-form-item>
          <el-form-item label="总数量"><el-input :model-value="delGradeSum" readonly placeholder="由等级数量自动合计" /></el-form-item>
          <el-form-item label="等级数量">
            <el-row :gutter="8">
              <el-col :span="6"><el-input v-model="delForm.aQty" type="number"><template #prepend>A规</template></el-input></el-col>
              <el-col :span="6"><el-input v-model="delForm.bQty" type="number"><template #prepend>B规</template></el-input></el-col>
              <el-col :span="6"><el-input v-model="delForm.cQty" type="number"><template #prepend>C规</template></el-input></el-col>
              <el-col :span="6"><el-input v-model="delForm.defectQty" type="number"><template #prepend>不良</template></el-input></el-col>
            </el-row>
          </el-form-item>
          <el-form-item label="收货仓库" required><RemoteSelect v-model="delWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" style="width:100%" placeholder="选择入库仓库" /></el-form-item>
          <el-form-item label="交货日期"><el-input v-model="delForm.deliveryDate" type="date" /></el-form-item>
          <el-form-item label="物流单号"><el-input v-model="delForm.trackingNo" placeholder="选填" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="delForm.remark" placeholder="选填" /></el-form-item>
          <el-form-item label="交货图片">
            <div class="drop-zone" @dragover="delHandleDragOver" @drop="delHandleDrop" :style="{ borderColor: delUploadFile?'#67c23a':'#dcdfe6', background: delUploadFile?'#f0f9eb':'#fafafa' }">
              <template v-if="delUploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">📎 {{ delUploadFile.name }}</span><el-button type="danger" size="small" @click.stop="delHandleRemoveFile">移除</el-button></div></template>
              <template v-else-if="delForm.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:var(--app-color-primary)">📎 已有图片</span><el-button type="primary" size="small" @click.stop="openAttach(delForm.attachUrl)">查看</el-button><span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">可拖拽新文件替换</span></div></template>
              <template v-else><p style="color:#909399;margin:0">拖拽图片到此处，或点击选择</p></template>
              <input v-if="!delForm.attachUrl && !delUploadFile" type="file" @change="delHandleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
            </div>
          </el-form-item>
        </el-form>
        <template #footer><el-button @click="delDialogVisible = false">取消</el-button><el-button type="primary" :loading="delSaving" @click="delHandleSubmit()">保存</el-button></template>
      </el-dialog>
    </template>

    <!-- Tab 3: 结单详情 -->
    <template v-if="activeTab === 'close'">
      <el-card shadow="never" v-loading="closeLoading">
        <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">结单详情</span><el-button size="small" @click="router.push(`/outsource/order/close/${form.id}`)">查看完整结单报表</el-button></div></template>
        <el-table :data="closeItems" border size="small" stripe v-if="closeItems.length">
          <el-table-column label="类目" width="70"><template #default="{row}">{{ typeName(row.bomTypeId) }}</template></el-table-column>
          <el-table-column prop="materialName" label="物料名称" min-width="120" />
          <el-table-column label="用料总数" width="90" align="right"><template #default="{row}">{{ fmt(row.usedTotalQuantity) }}</template></el-table-column>
          <el-table-column label="退料总计" width="90" align="right"><template #default="{row}">{{ fmt((+row.goodReturnQty||0) + (+row.defectReturnQty||0)) }}</template></el-table-column>
          <el-table-column label="出货消耗" width="90" align="right"><template #default="{row}">{{ fmt(row.shippedQuantity) }}</template></el-table-column>
          <el-table-column label="良品退料" width="90" align="right"><template #default="{row}">{{ fmt(row.goodReturnQty) }}</template></el-table-column>
          <el-table-column label="不良退料" width="90" align="right"><template #default="{row}">{{ fmt(row.defectReturnQty) }}</template></el-table-column>
          <el-table-column label="留存工厂" width="90" align="right"><template #default="{row}">{{ fmt(row.factoryRetainQty) }}</template></el-table-column>
          <el-table-column label="缺失" width="90" align="right"><template #default="{row}"><span :style="{color:row.missingQty!=0?'var(--app-color-danger)':''}">{{ fmt(row.missingQty) }}</span></template></el-table-column>
          <el-table-column label="加工良率%" width="90" align="right"><template #default="{row}"><span style="color:var(--app-color-primary)">{{ fmt(row.targetYieldRate) }}</span></template></el-table-column>
          <el-table-column label="生产良率%" width="90" align="right"><template #default="{row}"><span :style="{color: row.yieldLoss > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)'}">{{ fmt(row.actualYieldRate) }}</span></template></el-table-column>
          <el-table-column label="良率超损%" width="90" align="right"><template #default="{row}"><span :style="{color: row.yieldLoss > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ fmt(row.yieldLoss) }}</span></template></el-table-column>
          <el-table-column label="超损数量" width="90" align="right"><template #default="{row}"><span :style="{color: row.excessLossQty > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ fmt(row.excessLossQty) }}</span></template></el-table-column>
          <el-table-column label="最大超损" width="90" align="right"><template #default="{row}">{{ fmt(row.maxExcessLossQty) }}</template></el-table-column>
          <el-table-column label="物料单价" width="90" align="right"><template #default="{row}">{{ fmt(row.unitPrice) }}</template></el-table-column>
          <el-table-column label="超损总价" width="90" align="right"><template #default="{row}"><span :style="{color: row.excessLossAmount > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ fmt(row.excessLossAmount) }}</span></template></el-table-column>
          <el-table-column label="备注" min-width="100"><template #default="{row}">{{ row.remark || '-' }}</template></el-table-column>
        </el-table>
        <div v-else style="color:var(--app-text-secondary);text-align:center;padding:20px">暂无结单数据</div>
      </el-card>
    </template>

    <!-- 退不良弹窗 -->
    <el-dialog v-model="defectVisible" title="退不良（拆分还料）" width="780px" :close-on-click-modal="false">
      <el-form-item label="退不良仓库" style="margin-bottom:12px"><RemoteSelect v-model="defectWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" style="width:100%" placeholder="选择扣减的成品仓库" @change="onDefectWhChange" /></el-form-item>
      <el-table :data="defectItems" border size="small">
        <el-table-column prop="productName" label="产品" min-width="160" />
        <el-table-column label="A规" width="130">
          <template #default="{row}">
            <div style="font-size:12px;color:var(--app-text-regular)">库存 {{ row.stocks?.a ?? 0 }}</div>
            <el-input v-model="row.aQty" size="small" type="number" placeholder="数量" />
          </template>
        </el-table-column>
        <el-table-column label="B规" width="130">
          <template #default="{row}">
            <div style="font-size:12px;color:var(--app-text-regular)">库存 {{ row.stocks?.b ?? 0 }}</div>
            <el-input v-model="row.bQty" size="small" type="number" placeholder="数量" />
          </template>
        </el-table-column>
        <el-table-column label="C规" width="130">
          <template #default="{row}">
            <div style="font-size:12px;color:var(--app-text-regular)">库存 {{ row.stocks?.c ?? 0 }}</div>
            <el-input v-model="row.cQty" size="small" type="number" placeholder="数量" />
          </template>
        </el-table-column>
        <el-table-column label="不良" width="130">
          <template #default="{row}">
            <div style="font-size:12px;color:var(--app-text-regular)">库存 {{ row.stocks?.defect ?? 0 }}</div>
            <el-input v-model="row.defectQty" size="small" type="number" placeholder="数量" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="defectVisible=false">取消</el-button><el-button type="warning" :loading="defectSaving" @click="handleDefectReturn">确认退不良</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:0; }
.page-header { display:flex; align-items:center; gap:12px; padding-bottom:8px; flex-wrap:wrap; }

.drop-zone { position:relative; border:2px dashed var(--app-border-color); border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:var(--app-color-primary); background:#ecf5ff }
:deep(.defect-row) { background:#fdf6ec !important }
/* BOM物料清单操作列：确保按钮组在单元格内垂直居中 */
:deep(.bom-table .action-col .cell) { display:flex !important; align-items:center !important; justify-content:center !important; height:100% !important; }
</style>
