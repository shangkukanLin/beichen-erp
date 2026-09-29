<script setup lang="ts">
import { localDate } from '@/utils/date'
defineOptions({ name: 'OutsourceOrderDetail' })

import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { getProjectBom } from '@/api/system'
import { exportContractPdf } from '@/api/contract-template'
// 2026-09-16：收货相关的枚举/状态（DeliveryType、DocStatus 等）随「交货管理」页签移出到
// 独立菜单页「成品收货」（views/outsource/order/delivery.vue），本页不再使用
import { OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag, OrderType, OUTSOURCE_ORDER_DIRTY_KEY } from '@/api/enums'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'

const route = useRoute(); const router = useRouter()
const loading = ref(true); const saving = ref(false)
const activeTab = ref('detail')
const uploadFile = ref<File | null>(null); const attachSaving = ref(false)

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
  p.set('materialName', matName(row.materialId)); p.set('materialTypeId', String(row.materialTypeId ?? ''))
  p.set('unit', row.unit || ''); p.set('quantity', String(s.shortage || 0))
  router.push('/outsource/material-order/add?' + p.toString())
}
function goOutsource(row: any) {
  const s = getStock(row.materialId)
  const ids = (s.supplierIds || '') as string; const firstId = ids.split(',')[0]?.trim()
  const p = new URLSearchParams(); p.set('orderType', OrderType.OUTSOURCE)
  if (firstId) p.set('supplierId', firstId)
  if (s.materialId) p.set('materialId', String(s.materialId))
  p.set('materialName', matName(row.materialId)); p.set('materialTypeId', String(row.materialTypeId ?? ''))
  p.set('unit', row.unit || ''); p.set('quantity', String(s.shortage || 0))
  router.push('/outsource/material-order/add?' + p.toString())
}

const form = reactive({
  id: undefined as any, code: '', status: '',
  factoryId: undefined as any,
  supplyMode: 'OURS',
  planStartDate: '', planEndDate: '',
  actualStartDate: '', actualEndDate: '',
  taxIncluded: 0, taxRate: '', taxAmount: '',
  totalAmount: '', remark: '',
  attachUrl: '', logisticsCompany: '', logisticsNo: '',
  // 制单人 / 审核人（2026-09-23 单据详情口径：详情页显示这两项）
  createByName: '', auditorName: ''
})
// 供料模式：OURS来料加工 / FACTORY包工包料
const SUPPLY_MODE_OPTIONS = [{ label: '来料加工', value: 'OURS' }, { label: '包工包料', value: 'FACTORY' }]
const SUPPLY_TYPE_OPTIONS = [{ label: '我方供', value: 'OURS' }, { label: '工厂包', value: 'FACTORY' }]
// 包工包料默认规则：仅玻璃（按物料类型ID对比）我方供，其余物料默认工厂包
const glassTypeId = computed(() => materialTypes.value.find((t: any) => t.typeName === '玻璃')?.id)
function defaultSupplyType(materialTypeId: any) {
  return glassTypeId.value != null && materialTypeId === glassTypeId.value ? 'OURS' : 'FACTORY'
}
function onSupplyModeChange() {
  // 来料加工：全部我方供；包工包料：玻璃我方供、其余默认工厂包
  products.value.forEach((p: any) => (p.materials || []).forEach((m: any) => {
    m.supplyType = form.supplyMode === 'OURS' ? 'OURS' : defaultSupplyType(m.materialTypeId)
  }))
}

const products = ref<any[]>([])
const factoryOptions = ref<any[]>([])
const projectOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const materialTypes = ref<any[]>([])
const fetchFactories = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw, supplierType: 'factory' } })
const fetchProjects = (kw: string) => request.get('/dev/project/page', { params: { pageSize: 500, name: kw } })
const fetchMaterials = (kw: string) => request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw } })
const fetchMaterialTypes = (kw: string) => request.get('/dev/material-type/enabled', { params: { pageSize: 500, name: kw } })
// 收货/退不良仓库限定为我方（自有）成品仓
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })

// materialTypeId -> 类型名 映射（兜底展示用）
function typeName(id: number | undefined, fallback?: string) {
  if (id != null) { const t = materialTypes.value.find((v: any) => v.id === id); if (t) return t.typeName }
  return fallback || '-'
}
// 数字格式化（保留2位小数，null/undefined 显示 0）
function fmt(v: any) { return v !== undefined && v !== null ? Number(v).toFixed(2) : '0.00' }

async function loadOptions() {
  const [f, p, m, b]: any[] = await Promise.all([fetchFactories(''), fetchProjects(''), fetchMaterials(''), fetchMaterialTypes('')])
  factoryOptions.value = f?.records || []
  projectOptions.value = p?.records || []
  materialOptions.value = m?.records || []
  materialTypes.value = Array.isArray(b) ? b : (b?.records || [])
}

async function loadData() {
  loading.value = true
  try {
    const d = await request.get<any,any>(`/outsource/order/${route.params.id}`)
    if (d) {
      Object.assign(form, {
        id: d.id, code: d.code, status: d.status, factoryId: d.factoryId,
        supplyMode: d.supplyMode || 'OURS',
        planStartDate: d.planStartDate || '', planEndDate: d.planEndDate || '',
        actualStartDate: d.actualStartDate || '', actualEndDate: d.actualEndDate || '',
        taxIncluded: d.taxIncluded || 0, taxRate: d.taxRate || '', taxAmount: d.taxAmount || '',
        totalAmount: d.totalAmount || '', remark: d.remark || '',
        attachUrl: d.attachUrl || '', logisticsCompany: d.logisticsCompany || '', logisticsNo: d.logisticsNo || ''
      })
    }
    const ps = await request.get<any,any>(`/outsource/order/${route.params.id}/products`)
    products.value = (ps || []).map((p:any) => ({
      ...p, _key: p.id || Date.now() + Math.random(),
      materials: (p.materials || []).map((m:any) => ({ ...m, price: materialOptions.value.find((o:any) => o.id === m.materialId)?.price ?? null }))
    }))
    if (form.factoryId && !factoryOptions.value.some((f:any)=>f.id===form.factoryId)) {
      try { const sup = await request.get<any,any>(`/supplier/${form.factoryId}`); if (sup) factoryOptions.value.push({id:sup.id,name:sup.name}) } catch (e: any) { console.warn('加载工厂信息失败', e?.message || e) }
    }
    await loadMaterialStock()
    if (form.status === OutsourceOrderStatus.FINISHED || form.status === OutsourceOrderStatus.CANCELLED) loadCloseReport()
  } finally { loading.value = false }
  // 数据加载完成 ⇒ 重建"未保存"基线（保存/审核后都会重跑本函数 ⇒ 自动重置，不误报）
  takeBaseline()
}

/**
 * 未保存拦截（2026-09-23 统一模板）：本页待审核态可直接编辑并保存 ⇒ 属"能改数据"，接守卫。
 * ⚠️ 必须写在 form / products 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline } = useUnsavedGuard(() => ({ form, products: products.value }))

function addProduct() { products.value.push({ _key: Date.now(), projectId: undefined, productName: '', quantity: 1, unitPrice: 0, amount: 0, remark: '', materials: [] }) }
function removeProduct(idx: number) { products.value.splice(idx, 1) }
function onProjectSelect(idx: number, pid: number) {
  const proj = projectOptions.value.find((v:any) => v.id === pid)
  if (proj) {
    products.value[idx].projectId = pid
    // 产品名称取项目的「产品名称」(productName，2026-09-21 由「总成名称」更名)，与产品主数据一致；为空时回退项目名
    products.value[idx].productName = proj.productName || proj.name || ''
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
        return { materialId: m.outsourceMaterialId || null, materialName: opt?.materialName || '', price: opt?.price ?? null, materialTypeId: m.materialTypeId || null,  unit: m.unit || '', demandQuantity: Math.round(qty * Number(m.quantity || 0)), lossRate: m.lossRate || 0, supplyType: form.supplyMode === 'FACTORY' ? defaultSupplyType(m.materialTypeId) : 'OURS', remark: '' }
      })
    }
  } catch { products.value[idx].materials = [] }
}
function calcAmount(idx: number) { const p = products.value[idx]; p.amount = (Number(p.quantity) || 0) * (Number(p.unitPrice) || 0) }
function onMatSelect(idx: number, mi: number, mat: any) { const m = materialOptions.value.find((v:any) => v.id === mi); if (m) { mat.materialName = m.materialName; mat.materialTypeId = m.materialTypeId; mat.unit = m.unit } }
function addMaterial(idx: number) { products.value[idx].materials.push({ materialId: undefined, materialName: '', materialTypeId: undefined, unit: '', demandQuantity: 1, lossRate: 0, supplyType: form.supplyMode === 'FACTORY' ? defaultSupplyType(undefined) : 'OURS', remark: '' }) }
function removeMaterial(pi: number, mi: number) { products.value[pi].materials.splice(mi, 1) }

function openAttach(url:string) { window.open(url + '?inline=true') }
function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }

// 合同文件单独保存：只提交 attachUrl（走 /{id}/attach），不触发整单更新，避免误清产品明细
async function handleSaveAttach() {
  if (!uploadFile.value) return
  attachSaving.value = true
  try {
    const fd = new FormData(); fd.append('file', uploadFile.value)
    const res = await request.post<any, string>('/dev/file/upload', fd)
    await request.put(`/outsource/order/${form.id}/attach`, { attachUrl: res as unknown as string })
    ElMessage.success('合同文件已保存')
    uploadFile.value = null
    await loadData()
    sessionStorage.setItem(OUTSOURCE_ORDER_DIRTY_KEY, '1')
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) } finally { attachSaving.value = false }
}

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
    await request.put(`/outsource/order/${form.id}`, { ...form, products: products.value }); ElMessage.success('已保存'); await loadData(); sessionStorage.setItem(OUTSOURCE_ORDER_DIRTY_KEY, '1')
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) } finally { saving.value = false }
}
async function handleAudit() {
  try { await ElMessageBox.confirm('审核后加工单将进入生产状态。', '审核加工单', { type:'warning' }); await request.put(`/outsource/order/${form.id}/audit`); ElMessage.success('审核通过，进入生产中'); await loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleUnaudit() {
  try { await ElMessageBox.confirm('反审核将回滚所有收货记录和库存变动，确认继续？', '反审核加工单', { type:'warning' }); await request.put(`/outsource/order/${form.id}/un-audit`); ElMessage.success('已反审核，回到待审核状态'); await loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
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

/**
 * 每次进入详情页都重新拉取字典与单据数据：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发。
 * 只写在 onActivated —— 首次挂载时 onActivated 同样会触发，若 onMounted 也写一份会导致首次重复请求两次。
 * 注意 loadOptions 必须先于 loadData：明细单价依赖物料字典（materialOptions），
 * 两者并发会因字典未就绪导致 price 为空，故这里串行 await。
 */
onActivated(async () => { await loadOptions(); await loadData() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作（审核/反审核/保存） -->
  <PageShell :loading="loading" back-fallback="/outsource/order">
    <template #actions>
      <el-button v-perm="'outsource:order'" v-if="form.status===OutsourceOrderStatus.PENDING" type="success" size="small" @click="handleAudit">审核</el-button>
      <el-button v-perm="'outsource:order'" v-if="form.status===OutsourceOrderStatus.PRODUCING" type="danger" size="small" @click="handleUnaudit">反审核</el-button>
      <el-button type="primary" size="small" :loading="saving" @click="handleSave" :disabled="form.status===OutsourceOrderStatus.CANCELLED">保存</el-button>
    </template>

    <el-tabs v-model="activeTab" style="margin-bottom:12px" @tab-change="(t:any)=>{if(t==='close')loadCloseReport()}">
      <el-tab-pane label="加工详情" name="detail" />
      <!-- 「交货管理」页签已于 2026-09-16 移出为独立菜单页「成品收货」；页签移出时留下的「跳转按钮」
           又于 2026-09-21 按用户口径移除（收货统一从「成品收货」菜单进） -->
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
            <el-col :span="8"><el-form-item label="供料模式"><el-select v-model="form.supplyMode" style="width:100%" :disabled="form.status!==OutsourceOrderStatus.PENDING" @change="onSupplyModeChange"><el-option v-for="m in SUPPLY_MODE_OPTIONS" :key="m.value" :label="m.label" :value="m.value" /></el-select></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="计划开始"><el-input v-model="form.planStartDate" type="date" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="计划完成"><el-input v-model="form.planEndDate" type="date" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="实际开始"><el-input :model-value="form.actualStartDate" readonly /></el-form-item></el-col>
            <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
            <el-col :span="8"><el-form-item label="制单人"><el-input :model-value="form.createByName || '—'" readonly /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="审核人"><el-input :model-value="form.auditorName || '—'" readonly /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="实际完成"><el-input :model-value="form.actualEndDate" readonly /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="总金额"><el-input :model-value="Number(form.totalAmount||0).toFixed(2)" readonly /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="含税"><el-switch v-model="form.taxIncluded" :active-value="1" :inactive-value="0" disabled /></el-form-item></el-col>
            <el-col :span="8" v-if="form.taxIncluded"><el-form-item label="税率(%)"><el-input :model-value="form.taxRate" disabled /></el-form-item></el-col>
            <el-col :span="8" v-if="form.taxIncluded"><el-form-item label="税额"><el-input :model-value="form.taxAmount" disabled /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
          <!-- 审核 / 反审核 / 保存 已统一上移到页头右侧操作区（PageShell #actions）；以下为用户口径的历史说明： -->
            <!-- 2026-09-21（用户口径：「委外加工单详情页面里面的成品收货按钮不要了。在成品收货里面收货就行」）：
                 收货统一从左侧「委外加工 → 成品收货」菜单进（列表行内「收货」直达一步收货、行内「详情」看记录），
                 本页不再保留跳转按钮（原先那个是 2026-09-16「交货管理」页签移出时留下的入口）。
                 2026-09-21（用户口径「**结单按钮放到成品收货里**」）：本页的「结单」按钮**已撤掉** ⇒
                 到「成品收货」点「结单」（列表行内 或 该单收货详细页），跳的还是同一个结单报表页；
                 结单后本页的「结单详情」页签仍可回看报表（含"查看完整结单报表"，反结单也在那里）。 -->
            <!-- 原「结单」按钮（跳 /outsource/order/close/:id）已按上述口径移除 -->
        </el-form>
      </el-card>

      <el-card v-for="(p, pi) in products" :key="p._key" shadow="never" style="margin-top:12px">
        <template #header><div style="display:flex;align-items:center;justify-content:space-between"><span style="font-weight:600">加工产品 #{{ pi + 1 }}</span><el-button type="danger" size="small" text @click="removeProduct(pi)" v-if="products.length>1 && form.status===OutsourceOrderStatus.PENDING">删除产品</el-button></div></template>
        <el-form :model="p" label-width="90px" size="small">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="加工产品"><RemoteSelect v-model="p.projectId" :fetch="fetchProjects" :label-key="(row:any)=>row.productName || row.name" filterable clearable style="width:100%" :disabled="form.status!==OutsourceOrderStatus.PENDING" @change="(v:any)=>onProjectSelect(pi,v)" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="数量"><el-input-number v-model="p.quantity" :controls="false" :precision="0" :step="1" style="width:100%" :disabled="form.status!==OutsourceOrderStatus.PENDING" @change="calcAmount(pi)" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item :label="form.supplyMode==='FACTORY' ? '包工包料单价' : '单价'"><el-input v-model="p.unitPrice" type="number" :disabled="form.status!==OutsourceOrderStatus.PENDING" @change="calcAmount(pi)" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="小计"><el-input :model-value="p.amount" readonly /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="备注"><el-input v-model="p.remark" :disabled="form.status!==OutsourceOrderStatus.PENDING" /></el-form-item></el-col>
          </el-row>
        </el-form>
        <div style="margin-top:8px">
          <div style="margin-bottom:6px;display:flex;align-items:center;gap:8px">
            <span style="font-weight:500;font-size:var(--app-font-base)">BOM清单</span>
            <!-- BOM 快照版本（2026-09-17）：同 BOM 版本同内容的多张加工单共享一份快照，明细不再随单重复生成 -->
            <el-tag v-if="p.bomVersion != null" type="info" size="small">BOM版本 v{{ p.bomVersion }}</el-tag>
            <span v-if="p.bomSnapshotKind" style="color:var(--app-text-placeholder);font-size:var(--app-font-xs)">
              {{ p.bomSnapshotKind === 'BOM' ? '与研发BOM一致' : (p.bomSnapshotKind === 'MIGRATED' ? '历史订单迁移快照' : '本单明细有调整') }}
            </span>
          </div>
          <el-table v-if="p.materials && p.materials.length" :data="p.materials" border size="small" class="bom-table">
            <el-table-column label="类型" width="70"><template #default="{row}">{{ typeName(row.materialTypeId) }}</template></el-table-column>
            <el-table-column label="物料名称" min-width="120"><template #default="{row}">{{ matName(row.materialId) }}</template></el-table-column>
            <el-table-column prop="unit" label="单位" width="55" />
            <el-table-column label="单价" width="90" align="right">
              <template #default="{row}">{{ row.price != null ? Number(row.price).toFixed(2) : '-' }}</template>
            </el-table-column>
            <el-table-column label="需求" width="70"><template #default="{row}">{{ row.demandQuantity }}</template></el-table-column>
            <el-table-column label="已出货消耗" width="80"><template #default="{row}"><span :style="{color: Number(getStock(row.materialId).shippedConsumed||0)>0?'var(--app-color-primary)':''}">{{ getStock(row.materialId).shippedConsumed || 0 }}</span></template></el-table-column>
            <el-table-column label="剩余需求" width="75"><template #default="{row}">{{ getStock(row.materialId).remainingDemand || row.demandQuantity }}</template></el-table-column>
            <el-table-column label="库存" width="70"><template #default="{row}"><span :style="{color: Number(getStock(row.materialId).stockQuantity||0) < Number(getStock(row.materialId).remainingDemand||row.demandQuantity) ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ getStock(row.materialId).stockQuantity || 0 }}</span></template></el-table-column>
            <el-table-column label="可能在途" width="80"><template #default="{row}"><span :style="{color: Number(getStock(row.materialId).inTransit||0) > 0 ? 'var(--app-color-primary)' : ''}">{{ getStock(row.materialId).inTransit || 0 }}</span></template></el-table-column>
            <el-table-column label="缺料" width="70"><template #default="{row}"><span :style="{color: Number(getStock(row.materialId).shortage||0) > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ getStock(row.materialId).shortage || 0 }}</span></template></el-table-column>
            <el-table-column label="损耗率(%)" width="85"><template #default="{row}"><el-input v-model="row.lossRate" size="small" :disabled="form.status!==OutsourceOrderStatus.PENDING" /></template></el-table-column>
            <el-table-column label="供料方" width="90" align="center">
              <template #default="{row}">
                <el-select v-model="row.supplyType" size="small" style="width:100%" :disabled="form.status!==OutsourceOrderStatus.PENDING || form.supplyMode==='OURS'">
                  <el-option v-for="t in SUPPLY_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="80"><template #default="{row}"><el-input v-model="row.remark" size="small" :disabled="form.status!==OutsourceOrderStatus.PENDING" /></template></el-table-column>
            <el-table-column label="操作" width="130" align="center" class-name="action-col" v-if="form.status===OutsourceOrderStatus.PRODUCING"><template #default="{row}"><div v-if="Number(getStock(row.materialId).shortage||0) > 0" style="display:flex;align-items:center;justify-content:center;gap:4px"><el-button type="warning" link size="small" @click="goPurchase(row)">去采购</el-button><el-button v-if="getStock(row.materialId).hasComponents" type="primary" link size="small" @click="goOutsource(row)">去委外</el-button></div></template></el-table-column>
          </el-table>
          <div v-else style="color:var(--app-text-secondary);font-size:var(--app-font-base);margin-top:8px">暂无 BOM 物料</div>
        </div>
      </el-card>

      <el-card shadow="never" style="margin-top:12px">
        <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">合同文件</span><el-button type="warning" size="small" @click="exportPdf">导出合同模板</el-button></div></template>
        <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
          <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">{{ uploadFile.name }}</span><el-button type="primary" size="small" :loading="attachSaving" @click.stop="handleSaveAttach">保存</el-button><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
          <template v-else-if="form.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:var(--app-color-primary)">已有附件</span><el-button type="primary" size="small" @click.stop="openAttach(form.attachUrl)">查看</el-button><el-button type="success" size="small"><a :href="form.attachUrl" download style="color:inherit;text-decoration:none">下载</a></el-button><el-button type="danger" size="small" @click.stop="handleDeleteAttach">删除</el-button><span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">可拖拽新文件替换</span></div></template>
          <template v-else><p style="color:#909399;margin:0">拖拽文件到此处，或点击选择</p></template>
          <input v-if="!form.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
        </div>
      </el-card>
    </template>

    <!-- Tab 3: 结单详情 -->
    <template v-if="activeTab === 'close'">
      <el-card shadow="never" v-loading="closeLoading">
        <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">结单详情</span><el-button size="small" @click="router.push(`/outsource/order/close/${form.id}`)">查看完整结单报表</el-button></div></template>
        <el-table :data="closeItems" border size="small" stripe v-if="closeItems.length">
          <el-table-column label="类目" width="70"><template #default="{row}">{{ typeName(row.materialTypeId) }}</template></el-table-column>
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
  </PageShell>
</template>

<style scoped>
/* 页头已统一到全局骨架（PageShell + styles/page.css）；
   原 .detail-page / .page-header 局部样式已删除 —— .page-header 与全局类同名，留着会双重生效 */

.drop-zone { position:relative; border:2px dashed var(--app-border-color); border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:var(--app-color-primary); background:#ecf5ff }
/* BOM清单操作列：确保按钮组在单元格内垂直居中 */
:deep(.bom-table .action-col .cell) { display:flex !important; align-items:center !important; justify-content:center !important; height:100% !important; }
</style>
