<script setup lang="ts">
defineOptions({ name: 'OutsourceOrderDetail' })

import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { exportContractPdf } from '@/api/contract-template'

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
      for (const m of r.materials) { map[m.materialName] = m }
      materialStockMap.value = map
    }
  } catch { materialStockMap.value = {} }
}
function getStock(materialName: string) {
  const s = materialStockMap.value[materialName]
  return s || { stockQuantity: 0, shortage: 0 }
}
function goPurchase(row: any) {
  const s = getStock(row.materialName)
  const ids = (s.supplierIds || '') as string; const firstId = ids.split(',')[0]?.trim()
  const p = new URLSearchParams(); if (firstId) p.set('supplierId', firstId)
  if (s.materialId) p.set('materialId', String(s.materialId))
  p.set('materialName', row.materialName || ''); p.set('materialType', row.materialType || '')
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

// 交货数据
const deliveries = ref<any[]>([])

async function loadOptions() {
  try { const r = await request.get<any,any>('/supplier/page',{params:{supplierType:'factory',pageSize:200}}); factoryOptions.value=r?.records||[] } catch (e: any) { console.warn('加载工厂选项失败', e?.message || e) }
  try { const r = await request.get<any,any>('/dev/project/page',{params:{pageSize:200}}); projectOptions.value=r?.records||[] } catch (e: any) { console.warn('加载项目选项失败', e?.message || e) }
  try { const r = await request.get<any,any>('/outsource/material/page',{params:{pageSize:500}}); materialOptions.value=r?.records||[] } catch (e: any) { console.warn('加载物料选项失败', e?.message || e) }
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
  } finally { loading.value = false }
}

function addProduct() { products.value.push({ _key: Date.now(), projectId: undefined, productName: '', productSpec: '', quantity: 1, unitPrice: 0, amount: 0, remark: '', materials: [] }) }
function removeProduct(idx: number) { products.value.splice(idx, 1) }
function onProjectSelect(idx: number, pid: number) {
  const proj = projectOptions.value.find((v:any) => v.id === pid)
  if (proj) {
    products.value[idx].projectId = pid
    products.value[idx].productName = proj.productName || proj.name || ''
    products.value[idx].productSpec = proj.productSpec || ''
    loadBomMaterials(idx, pid)
  }
}
async function loadBomMaterials(idx: number, pid: number) {
  try {
    const mats = await request.get<any,any>(`/dev/bom/project/${pid}`)
    if (mats && Array.isArray(mats)) {
      products.value[idx].materials = mats.map((m:any) => ({ materialId: undefined, materialName: m.materialName || '', materialType: m.materialType || '', unit: m.unit || '', demandQuantity: m.quantityPerSet || 1, lossRate: m.lossRate || 0, remark: '' }))
    }
  } catch { products.value[idx].materials = [] }
}
function calcAmount(idx: number) { const p = products.value[idx]; p.amount = (Number(p.quantity) || 0) * (Number(p.unitPrice) || 0) }
function onMatSelect(idx: number, mi: number, mat: any) { const m = materialOptions.value.find((v:any) => v.id === mi); if (m) { mat.materialName = m.materialName; mat.materialType = m.materialType; mat.unit = m.unit } }
function addMaterial(idx: number) { products.value[idx].materials.push({ materialId: undefined, materialName: '', materialType: '', unit: '', demandQuantity: 1, lossRate: 0, remark: '' }) }
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
async function handleConfirm() {
  try { await ElMessageBox.confirm('确认后加工单将进入生产状态。', '确认加工单', { type:'warning' }); await request.put(`/outsource/order/${form.id}/confirm`); ElMessage.success('已确认，进入生产中'); await loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}



const defectVisible = ref(false); const defectSaving = ref(false)
const defectItems = ref<any[]>([]); const defectWarehouseId = ref<number>()
const defectWarehouseInfo = ref<any>(null)
function openDefectReturn() { defectItems.value = (products.value || []).map((p: any) => ({ productId: p.id, productName: p.productName, quantity: undefined as any })); defectWarehouseId.value = undefined; defectWarehouseInfo.value = null; defectVisible.value = true; loadDelWarehouses() }
function onDefectWhChange(whId: number) {
  defectWarehouseId.value = whId
  if (!whId) { defectWarehouseInfo.value = null; return }
  // 查询该仓库该产品的库存
  request.get<any,any>('/inventory/stock/page', { params: { pageSize: 500 } }).then((r: any) => {
    const stocks = r?.records || []
    const s = stocks.find((s:any) => s.warehouseId === whId && s.productName === defectItems.value[0]?.productName)
    defectWarehouseInfo.value = s || { quantity: 0 }
  }).catch(() => { defectWarehouseInfo.value = { quantity: 0 } })
}
// ===== 交货管理（Tab 2）=====
const delSummary = ref<any>({})
const delProducts = ref<any[]>([])
const delDialogVisible = ref(false); const delIsEdit = ref(false); const delEditId = ref<number>()
const delSaving = ref(false); const delUploadFile = ref<File | null>(null)
const delWarehouseId = ref<number>(); const delWarehouseOptions = ref<any[]>([])
const delForm = reactive({ productName: '', quantity: '', deliveryDate: new Date().toISOString().split('T')[0], trackingNo: '', remark: '', attachUrl: '' })

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
  try { const r = await request.get<any,any>('/inventory/warehouse/page', { params: { pageSize: 200 } }); delWarehouseOptions.value = r?.records || [] } catch {}
}
function delOpenAdd() {
  delIsEdit.value = false; delEditId.value = undefined; delWarehouseId.value = undefined; delUploadFile.value = null
  Object.assign(delForm, { productName: '', quantity: '', deliveryDate: new Date().toISOString().split('T')[0], trackingNo: '', remark: '', attachUrl: '' })
  delDialogVisible.value = true; loadDelWarehouses()
}
function delOpenEdit(row: any) {
  delIsEdit.value = true; delEditId.value = row.id; delWarehouseId.value = row.warehouseId || undefined; delUploadFile.value = null
  Object.assign(delForm, { productName: row.productName, quantity: row.quantity, deliveryDate: row.deliveryDate, trackingNo: row.trackingNo || '', remark: row.remark || '', attachUrl: row.attachUrl || '' })
  delDialogVisible.value = true; loadDelWarehouses()
}
function delHandleDragOver(e: DragEvent) { e.preventDefault() }
function delHandleDrop(e: DragEvent) { e.preventDefault(); const f = e.dataTransfer?.files?.[0]; if (f) delUploadFile.value = f }
function delHandleFileSelect(e: Event) { const f = (e.target as HTMLInputElement).files?.[0]; if (f) delUploadFile.value = f }
function delHandleRemoveFile() { delUploadFile.value = null }
async function delHandleSubmit(forceDelivery = false) {
  if (!delForm.productName) { ElMessage.warning('请选择产品名称'); return }
  if (!delForm.quantity) { ElMessage.warning('请输入数量'); return }
  if (!delWarehouseId.value) { ElMessage.warning('请选择收货仓库'); return }
  delSaving.value = true
  try {
    if (delUploadFile.value) { const fd = new FormData(); fd.append('file', delUploadFile.value); const res = await request.post<any,string>('/dev/file/upload', fd); delForm.attachUrl = res as unknown as string }
    const body = { ...delForm, orderId: form.id, warehouseId: delWarehouseId.value || null }
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
      html += '<tr style="background:#f5f7fa"><th style="padding:6px;border:1px solid #ebeef5;text-align:left">物料名称</th><th style="padding:6px;border:1px solid #ebeef5">需要</th><th style="padding:6px;border:1px solid #ebeef5">库存</th><th style="padding:6px;border:1px solid #ebeef5">缺口</th></tr>'
      for (const s of shortages) {
        html += `<tr><td style="padding:6px;border:1px solid #ebeef5">${s.materialName || ''}</td>`
        html += `<td style="padding:6px;border:1px solid #ebeef5;text-align:center;color:#e6a23c">${s.needed || 0}</td>`
        html += `<td style="padding:6px;border:1px solid #ebeef5;text-align:center;color:#f56c6c">${s.stock || 0}</td>`
        html += `<td style="padding:6px;border:1px solid #ebeef5;text-align:center;color:#f56c6c;font-weight:600">${s.gap || 0}</td></tr>`
      }
      html += '</table>'
      html += '<div style="margin-top:8px;color:#909399;font-size:12px">确认后物料库存将变为负数</div>'
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

async function handleDefectReturn() {
  const data = defectItems.value.filter((r: any) => r.quantity && Number(r.quantity) > 0)
  if (data.length === 0) { ElMessage.warning('请输入退不良数量'); return }
  defectSaving.value = true
  try {
    for (const item of data) {
      await request.post(`/outsource/order-delivery/return-defect/${form.id}`, { productName: item.productName, quantity: item.quantity, warehouseId: defectWarehouseId.value })
    }
    ElMessage.success('退不良完成，物料已还回工厂委外仓库'); defectVisible.value = false; loadData()
  } catch (e: any) { ElMessage.error(e?.message || '退不良失败') } finally { defectSaving.value = false }
}

function exportPdf() {
  const url = exportContractPdf(form.id as number)
  request.get(url, { responseType: 'blob' }).then((res: any) => {
    const blob = new Blob([res], { type: 'application/pdf' }); const link = document.createElement('a')
    link.href = URL.createObjectURL(blob); link.download = `委外加工合同-${form.code}.pdf`; link.click(); URL.revokeObjectURL(link.href)
    ElMessage.success('PDF合同已下载')
  }).catch(() => { ElMessage.error('导出失败') })
}

onMounted(() => { loadOptions(); loadData() })
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <div class="page-header">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
        <span class="page-title">{{ form.code || '委外加工单详情' }}</span>
        <el-tag :type="form.status==='待确认'?'info':form.status==='生产中'?'primary':form.status==='已完成'?'success':'danger'" size="small">{{ form.status }}</el-tag>
      </div>
    </div>

    <el-tabs v-model="activeTab" style="margin-bottom:12px">
      <el-tab-pane label="加工详情" name="detail" />
      <el-tab-pane label="交货管理" name="delivery" />
    </el-tabs>

    <!-- Tab 1: 加工详情 -->
    <template v-if="activeTab === 'detail'">
      <div style="display:flex;gap:8px;margin-bottom:12px">
        <el-button v-if="form.status==='待确认'" type="success" @click="handleConfirm">确认（进入生产）</el-button>
      </div>

      <el-card shadow="never">
        <template #header><span style="font-weight:600">基础信息</span></template>
        <el-form :model="form" label-width="90px" size="small">
          <el-row :gutter="12">
            <el-col :span="8"><el-form-item label="加工厂"><el-select v-model="form.factoryId" filterable style="width:100%" :disabled="form.status!=='待确认'"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item></el-col>
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
            <el-button v-if="form.status==='生产中'" type="warning" size="small" @click="router.push(`/outsource/order/close/${form.id}`)">结单</el-button>
            <el-button v-if="form.status==='生产中' || form.status==='已完成'" type="warning" size="small" @click="openDefectReturn">退不良</el-button>
            <el-button type="primary" size="small" :loading="saving" @click="handleSave" :disabled="form.status==='已取消'">保存</el-button>
          </div>
        </el-form>
      </el-card>

      <el-card v-for="(p, pi) in products" :key="p._key" shadow="never" style="margin-top:12px">
        <template #header><div style="display:flex;align-items:center;justify-content:space-between"><span style="font-weight:600">加工产品 #{{ pi + 1 }}</span><el-button type="danger" size="small" text @click="removeProduct(pi)" v-if="products.length>1 && form.status==='待确认'">删除产品</el-button></div></template>
        <el-form :model="p" label-width="90px" size="small">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="关联项目"><el-select v-model="p.projectId" filterable clearable style="width:100%" :disabled="form.status!=='待确认'" @change="(v:any)=>onProjectSelect(pi,v)"><el-option v-for="pr in projectOptions" :key="pr.id" :label="pr.name" :value="pr.id" /></el-select></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="数量"><el-input v-model="p.quantity" type="number" :disabled="form.status!=='待确认'" @change="calcAmount(pi)" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="单价"><el-input v-model="p.unitPrice" type="number" :disabled="form.status!=='待确认'" @change="calcAmount(pi)" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="小计"><el-input :model-value="p.amount" readonly /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="备注"><el-input v-model="p.remark" :disabled="form.status!=='待确认'" /></el-form-item></el-col>
          </el-row>
        </el-form>
        <div style="margin-top:8px">
          <div style="margin-bottom:6px"><span style="font-weight:500;font-size:13px">BOM物料清单</span></div>
          <el-table v-if="p.materials && p.materials.length" :data="p.materials" border size="small">
            <el-table-column prop="materialType" label="类型" width="70" />
            <el-table-column prop="materialName" label="物料名称" min-width="120" />
            <el-table-column prop="unit" label="单位" width="55" />
            <el-table-column label="需求" width="75"><template #default="{row}">{{ row.demandQuantity }}</template></el-table-column>
            <el-table-column label="库存" width="75"><template #default="{row}"><span :style="{color: Number(getStock(row.materialName).stockQuantity||0) < Number(row.demandQuantity||0) ? '#f56c6c' : '#67c23a'}">{{ getStock(row.materialName).stockQuantity || 0 }}</span></template></el-table-column>
            <el-table-column label="缺料" width="75"><template #default="{row}"><span :style="{color: Number(getStock(row.materialName).shortage||0) > 0 ? '#f56c6c' : '#67c23a'}">{{ getStock(row.materialName).shortage || 0 }}</span></template></el-table-column>
            <el-table-column label="损耗率(%)" width="85"><template #default="{row}"><el-input v-model="row.lossRate" size="small" :disabled="form.status!=='待确认'" /></template></el-table-column>
            <el-table-column label="已发料" width="80"><template #default="{row}"><span :style="{color: Number(row.deliveredQuantity||0)>0?'#67c23a':''}">{{ row.deliveredQuantity || 0 }}</span></template></el-table-column>
            <el-table-column label="备注" min-width="80"><template #default="{row}"><el-input v-model="row.remark" size="small" :disabled="form.status!=='待确认'" /></template></el-table-column>
            <el-table-column label="操作" width="80" align="center" v-if="form.status==='生产中'"><template #default="{row}"><el-button v-if="Number(getStock(row.materialName).shortage||0) > 0" type="warning" link size="small" @click="goPurchase(row)">去采购</el-button></template></el-table-column>
          </el-table>
          <div v-else style="color:#909399;font-size:13px;margin-top:8px">暂无 BOM 物料</div>
        </div>
      </el-card>

      <el-card shadow="never" style="margin-top:12px">
        <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">合同文件</span><el-button type="warning" size="small" @click="exportPdf">导出合同模板</el-button></div></template>
        <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
          <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">{{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
          <template v-else-if="form.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:#409eff">已有附件</span><el-button type="primary" size="small" @click.stop="openAttach(form.attachUrl)">查看</el-button><el-button type="success" size="small"><a :href="form.attachUrl" download style="color:inherit;text-decoration:none">下载</a></el-button><el-button type="danger" size="small" @click.stop="handleDeleteAttach">删除</el-button><span style="color:#909399;font-size:12px">可拖拽新文件替换</span></div></template>
          <template v-else><p style="color:#909399;margin:0">拖拽文件到此处，或点击选择</p></template>
          <input v-if="!form.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
        </div>
      </el-card>
    </template>

    <!-- Tab 2: 交货管理 -->
    <template v-if="activeTab === 'delivery'">
      <el-row :gutter="12" style="margin-bottom:12px">
        <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">订单总量</p><p style="font-size:20px;font-weight:600;margin:4px 0">{{ delSummary.totalQuantity || 0 }}</p></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">已交数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:#67c23a">{{ delSummary.deliveredQuantity || 0 }}</p></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">剩余数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:#e6a23c">{{ delSummary.remainingQuantity || 0 }}</p></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">交货进度</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:#409eff">{{ delProgress }}%</p></el-card></el-col>
      </el-row>
      <el-card shadow="never" style="margin-bottom:12px">
        <el-progress :percentage="delProgress" :stroke-width="16" :text-inside="true" :color="delProgress>=100?'#67c23a':'#409eff'" />
      </el-card>
      <el-card shadow="never" style="margin-bottom:12px" v-if="delSummary.productStats && delSummary.productStats.length > 1">
        <template #header><span style="font-weight:600">按产品分类统计</span></template>
        <el-table :data="delSummary.productStats" border size="small">
          <el-table-column prop="productName" label="产品名称" min-width="150" />
          <el-table-column prop="totalQuantity" label="订单数量" width="100" />
          <el-table-column label="已交数量" width="100"><template #default="{row}"><span style="color:#67c23a;font-weight:500">{{ row.deliveredQuantity }}</span></template></el-table-column>
          <el-table-column label="剩余数量" width="100"><template #default="{row}"><span :style="{color: Number(row.remainingQuantity)<=0?'#67c23a':'#e6a23c',fontWeight:'500'}">{{ row.remainingQuantity }}</span></template></el-table-column>
          <el-table-column label="进度" width="180"><template #default="{row}"><el-progress :percentage="Number(row.totalQuantity)===0?0:Math.min(100,Math.round(Number(row.deliveredQuantity)/Number(row.totalQuantity)*100))" :stroke-width="12" :color="Number(row.remainingQuantity)<=0?'#67c23a':'#409eff'" /></template></el-table-column>
        </el-table>
      </el-card>
      <el-card shadow="never">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <span style="font-weight:600">交货记录</span>
          <el-button v-if="form.status==='生产中'" type="primary" size="small" @click="delOpenAdd">新增交货</el-button>
        </div>
        <el-table :data="deliveries" border stripe size="small" :row-class-name="({row}:{row:any})=>row.deliveryType==='退不良'?'defect-row':''">
          <el-table-column label="交货日期" width="110"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
          <el-table-column prop="productName" label="产品名称" min-width="120" />
          <el-table-column label="类型" width="80" align="center"><template #default="{row}"><el-tag v-if="row.deliveryType==='退不良'" type="warning" size="small">退不良</el-tag><span v-else style="color:#909399">—</span></template></el-table-column>
          <el-table-column label="收货仓库" width="120">
            <template #default="{row}"><span v-if="row.warehouseId">{{ delWarehouseOptions.find((w:any)=>w.id===row.warehouseId)?.warehouseName || row.warehouseId }}</span><span v-else style="color:#c0c4cc">—</span></template>
          </el-table-column>
          <el-table-column label="数量" width="90" align="right"><template #default="{row}"><span :style="{color:Number(row.quantity)<0?'#f56c6c':''}">{{ row.quantity }}</span></template></el-table-column>
          <el-table-column prop="trackingNo" label="物流单号" width="140" />
          <el-table-column label="附件" width="80" align="center"><template #default="{row}"><el-button v-if="row.attachUrl" type="primary" link size="small" @click="openAttach(row.attachUrl)">查看</el-button><span v-else style="color:#c0c4cc">—</span></template></el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150" />
          <el-table-column label="操作" width="120" align="center">
            <template #default="{row}"><el-button type="primary" link size="small" @click="delOpenEdit(row)">编辑</el-button><el-button type="danger" link size="small" @click="delHandleDelete(row)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 新增/编辑交货弹窗 -->
      <el-dialog v-model="delDialogVisible" :title="delIsEdit?'编辑交货记录':'新增交货记录'" width="520px" :close-on-click-modal="false">
        <el-form :model="delForm" label-width="85px" size="small">
          <el-form-item label="产品名称"><el-select v-model="delForm.productName" filterable style="width:100%" placeholder="选择订单产品"><el-option v-for="p in delProducts" :key="p.id" :label="p.productName" :value="p.productName" /></el-select></el-form-item>
          <el-form-item label="数量"><el-input v-model="delForm.quantity" placeholder="交货数量" /></el-form-item>
          <el-form-item label="收货仓库" required><el-select v-model="delWarehouseId" filterable style="width:100%" placeholder="选择入库仓库"><el-option v-for="w in delWarehouseOptions" :key="w.id" :label="`${w.warehouseName} (${w.code})`" :value="w.id" /></el-select></el-form-item>
          <el-form-item label="交货日期"><el-input v-model="delForm.deliveryDate" type="date" /></el-form-item>
          <el-form-item label="物流单号"><el-input v-model="delForm.trackingNo" placeholder="选填" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="delForm.remark" placeholder="选填" /></el-form-item>
          <el-form-item label="交货图片">
            <div class="drop-zone" @dragover="delHandleDragOver" @drop="delHandleDrop" :style="{ borderColor: delUploadFile?'#67c23a':'#dcdfe6', background: delUploadFile?'#f0f9eb':'#fafafa' }">
              <template v-if="delUploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">📎 {{ delUploadFile.name }}</span><el-button type="danger" size="small" @click.stop="delHandleRemoveFile">移除</el-button></div></template>
              <template v-else-if="delForm.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:#409eff">📎 已有图片</span><el-button type="primary" size="small" @click.stop="openAttach(delForm.attachUrl)">查看</el-button><span style="color:#909399;font-size:12px">可拖拽新文件替换</span></div></template>
              <template v-else><p style="color:#909399;margin:0">拖拽图片到此处，或点击选择</p></template>
              <input v-if="!delForm.attachUrl && !delUploadFile" type="file" @change="delHandleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
            </div>
          </el-form-item>
        </el-form>
        <template #footer><el-button @click="delDialogVisible = false">取消</el-button><el-button type="primary" :loading="delSaving" @click="delHandleSubmit()">保存</el-button></template>
      </el-dialog>
    </template>

    <!-- 退不良弹窗 -->
    <el-dialog v-model="defectVisible" title="退不良（拆分还料）" width="550px" :close-on-click-modal="false">
      <el-form-item label="退不良仓库" style="margin-bottom:12px"><el-select v-model="defectWarehouseId" filterable style="width:100%" placeholder="选择扣减的成品仓库" @change="onDefectWhChange"><el-option v-for="w in delWarehouseOptions" :key="w.id" :label="`${w.warehouseName} (${w.code})`" :value="w.id" /></el-select></el-form-item>
      <div v-if="defectWarehouseInfo" style="margin-bottom:8px;font-size:13px;color:#606266">当前库存：<b :style="{color:defectWarehouseInfo.quantity>0?'#67c23a':'#f56c6c'}">{{ defectWarehouseInfo.quantity || 0 }}</b></div>
      <el-table :data="defectItems" border size="small">
        <el-table-column prop="productName" label="产品" min-width="200" />
        <el-table-column label="退不良数量" width="160"><template #default="{row}"><el-input v-model="row.quantity" size="small" type="number" placeholder="数量" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="defectVisible=false">取消</el-button><el-button type="warning" :loading="defectSaving" @click="handleDefectReturn">确认退不良</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:0; }
.page-header { display:flex; align-items:center; gap:12px; padding-bottom:8px; flex-wrap:wrap; }
.page-title { font-size:18px; font-weight:600; }
.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:#409eff; background:#ecf5ff }
:deep(.defect-row) { background:#fdf6ec !important }
</style>
