<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { exportMaterialOrderPdf } from '@/api/contract-template'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(true)
const order = reactive({ id: 0, code: '', status: '', orderType: '采购', supplierId: undefined as any, supplierName: '', deliveryDate: '', finishTime: '', remark: '', attachUrl: '' })
const items = ref<any[]>([])
const activeTab = ref('detail')
const saving = ref(false)
const supplierOptions = ref<any[]>([])

async function loadOptions() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { pageSize: 500 } }); supplierOptions.value = r?.records || [] } catch { }
  // 确保当前供应商在选项中（即使不是对应类型）
  if (order.supplierId && !supplierOptions.value.some((s: any) => s.id === order.supplierId)) {
    try { const s = await request.get<any, any>(`/supplier/${order.supplierId}`); if (s) supplierOptions.value.unshift(s) } catch {}
  }
}

const recVisible = ref(false); const recSaving = ref(false)
const recWarehouseId = ref<number>()
const recItems = ref<any[]>([])
const warehouseOptions = ref<any[]>([])

const defectVisible = ref(false); const defectSaving = ref(false)
const defectItems = ref<any[]>([])
const defectHandleType = ref('维修返还')
const defectWarehouseId = ref<number>()
const defectWarehouseOptions = ref<any[]>([])

async function loadDefectWarehouses() {
  try {
    // 查询该物料订单发料到了哪些委外仓库
    const r = await request.get<any,any>(`/outsource/material-order/${id}/defect-warehouses`)
    defectWarehouseOptions.value = r || []
  } catch { defectWarehouseOptions.value = [] }
}
function onDefectWhChange(whId: number) {
  defectWarehouseId.value = whId
  // 刷新物料的可退库存
  for (const it of defectItems.value) {
    it.warehouseStock = undefined
    it.stockLoading = true
  }
  if (!whId) return
  loadDefectStock(whId)
}
async function loadDefectStock(whId: number) {
  try {
    const r = await request.get<any,any>('/outsource/stock/by-warehouse/' + whId)
    const stockMap: Record<number, number> = {}
    if (Array.isArray(r)) for (const s of r) stockMap[s.materialId] = s.quantity || 0
    for (const it of defectItems.value) {
      it.warehouseStock = stockMap[it.materialId] ?? 0
      it.stockLoading = false
    }
  } catch {
    for (const it of defectItems.value) it.stockLoading = false
  }
}

// 交货记录
const deliveries = ref<any[]>([])
const totalQuantity = computed(() => items.value.reduce((s: number, it: any) => s + (it.orderQuantity || 0), 0))
const deliveredQuantity = computed(() => items.value.reduce((s: number, it: any) => s + (it.receivedQuantity || 0) - (it.defectReturnedQty || 0), 0))
const deliveryProgress = computed(() => totalQuantity.value ? Math.min(100, Math.round(deliveredQuantity.value / totalQuantity.value * 100)) : 0)

// 附件上传
const uploadFile = ref<File | null>(null); const attachSaving = ref(false)
function openAttach(url: string) { window.open(url + '?inline=true') }
function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }
async function handleSaveAttach() {
  if (!uploadFile.value) return; attachSaving.value = true
  try {
    const fd = new FormData(); fd.append('file', uploadFile.value)
    const res = await request.post<any, string>('/dev/file/upload', fd)
    await request.put(`/outsource/material-order/${id}`, { orderType: order.orderType, supplierId: order.supplierId, targetWarehouseId: order.targetWarehouseId, deliveryDate: order.deliveryDate, remark: order.remark, attachUrl: res as unknown as string, items: items.value })
    ElMessage.success('合同文件已保存'); uploadFile.value = null; await loadAll()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) } finally { attachSaving.value = false }
}
async function handleDeleteAttach() {
  try {
    await ElMessageBox.confirm('确定删除附件吗？', '删除附件', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await request.delete(`/outsource/material-order/${id}/attach`); ElMessage.success('附件已删除'); await loadAll()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function loadAll() {
  loading.value = true
  try {
    const [o, dList] = await Promise.all([
      request.get<any, any>(`/outsource/material-order/${id}`),
      request.get<any, any>(`/outsource/material-order/${id}/deliveries`)
    ])
    if (o) {
      Object.assign(order, { id: o.id, code: o.code, status: o.status, orderType: o.orderType || '采购', supplierId: o.supplierId, supplierName: o.supplierName, deliveryDate: o.deliveryDate || '', finishTime: o.finishTime || '', remark: o.remark || '', attachUrl: o.attachUrl || '' })
    }
    items.value = o?.items || []
    deliveries.value = dList || []
    loadOptions()
  } finally { loading.value = false }
}

async function openReceive() {
  recWarehouseId.value = undefined
  recItems.value = items.value.map((it: any) => ({
    itemId: it.id, materialName: it.materialName, orderQuantity: it.orderQuantity,
    receivedQuantity: it.receivedQuantity, quantity: undefined as any,
    components: (it.components || []).map((c: any) => ({ childMaterialName: c.childMaterialName, childUnit: c.childUnit, stockQuantity: c.stockQuantity || 0, quantity: c.quantity || 1 }))
  }))
  try { const r = await request.get<any, any>('/outsource/warehouse/page', { params: { pageSize: 500 } }); warehouseOptions.value = (r?.records || []) } catch { warehouseOptions.value = [] }
  recVisible.value = true
}
async function handleReceive(force?: boolean) {
  if (!recWarehouseId.value) { ElMessage.warning('请选择收货仓库'); return }
  const data = recItems.value.filter((r: any) => r.quantity && Number(r.quantity) > 0)
  if (data.length === 0) { ElMessage.warning('请输入交货数量'); return }
  recSaving.value = true
  try {
    const res = await request.post<any, any>(`/outsource/material-order/${id}/receive`, { warehouseId: recWarehouseId.value, items: data, force: force || false })
    if (res?._shortage) {
      const shortages = (res.shortages || []) as any[]
      let html = '<div style="margin-bottom:8px">以下子物料库存不足，是否确认缺料收货？</div>'
      html += '<table style="width:100%;border-collapse:collapse;font-size:13px">'
      html += '<tr style="background:#f5f7fa"><th style="padding:6px;border:1px solid #ebeef5;text-align:left">物料名称</th><th style="padding:6px;border:1px solid #ebeef5">需要</th><th style="padding:6px;border:1px solid #ebeef5">库存</th><th style="padding:6px;border:1px solid #ebeef5">缺口</th></tr>'
      for (const s of shortages) {
        html += `<tr><td style="padding:6px;border:1px solid #ebeef5">${s.materialName||''}</td>`
        html += `<td style="padding:6px;border:1px solid #ebeef5;text-align:center;color:#e6a23c">${s.demand||0}</td>`
        html += `<td style="padding:6px;border:1px solid #ebeef5;text-align:center;color:#f56c6c">${s.stock||0}</td>`
        html += `<td style="padding:6px;border:1px solid #ebeef5;text-align:center;color:#f56c6c;font-weight:600">${s.shortage||0}</td></tr>`
      }
      html += '</table>'
      html += '<div style="margin-top:8px;color:#909399;font-size:12px">确认后子物料库存将变为负数</div>'
      recSaving.value = false
      try {
        await ElMessageBox.confirm(html, '缺料提示', {
          confirmButtonText: '确认缺料收货',
          cancelButtonText: '取消',
          type: 'warning',
          dangerouslyUseHTMLString: true
        })
      } catch { return }
      handleReceive(true)
      return
    }
    ElMessage.success(force ? '缺料交货完成（子物料库存已为负数）' : '交货完成')
    recVisible.value = false; loadAll()
  }
  catch (e: any) { ElMessage.error(e?.message || '交货失败') } finally { recSaving.value = false }
}

function goPurchaseComponent(comp: any, parentItem: any) {
  const ids = (comp.supplierIds || '') as string; const firstId = ids.split(',')[0]?.trim()
  const p = new URLSearchParams(); if (firstId) p.set('supplierId', firstId)
  if (comp.childMaterialId) p.set('materialId', String(comp.childMaterialId))
  p.set('materialName', comp.childMaterialName || ''); p.set('materialType', comp.childMaterialType || '')
  p.set('unit', comp.childUnit || ''); p.set('quantity', String(comp.shortage || 0))
  router.push('/outsource/material-order/add?' + p.toString())
}

function openDefectReturn() {
  defectHandleType.value = '维修返还'
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
  if (defectHandleType.value !== '折现退款' && !defectWarehouseId.value) { ElMessage.warning('请选择退料仓库'); return }
  defectSaving.value = true
  try {
    await request.post(`/outsource/material-order/${id}/return-defect`, { handleType: defectHandleType.value, warehouseId: defectWarehouseId.value, items: data })
    ElMessage.success('退不良完成'); defectVisible.value = false; loadAll()
  }
  catch (e: any) { ElMessage.error(e?.message || '退料失败') } finally { defectSaving.value = false }
}

async function handleSave() {
  saving.value = true
  try {
    await request.put(`/outsource/material-order/${id}`, { orderType: order.orderType, supplierId: order.supplierId, targetWarehouseId: order.targetWarehouseId, deliveryDate: order.deliveryDate, remark: order.remark, items: items.value })
    ElMessage.success('保存成功')
    await loadAll()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function handleConfirm() {
  try { await ElMessageBox.confirm('确认后进入收货中', '确认', { type: 'warning' }); await request.put(`/outsource/material-order/${id}/confirm`); ElMessage.success('已确认'); loadAll() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleFinish() {
  try { await ElMessageBox.confirm('结单后订单将标记为已完成，不可再修改。', '结单', { type: 'warning' }); await request.put(`/outsource/material-order/${id}/finish`); ElMessage.success('已结单'); loadAll() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleCancel() {
  try { await ElMessageBox.confirm('确定取消？', '取消', { type: 'warning' }); await request.put(`/outsource/material-order/${id}/cancel`); ElMessage.success('已取消'); loadAll() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

function exportPdf() {
  const url = exportMaterialOrderPdf(id)
  request.get(url, { responseType: 'blob' }).then((res: any) => {
    const blob = new Blob([res], { type: 'application/pdf' })
    const link = document.createElement('a'); link.href = URL.createObjectURL(blob)
    link.download = `物料采购合同-${order.code}.pdf`; link.click(); URL.revokeObjectURL(link.href)
    ElMessage.success('PDF合同已下载')
  }).catch(() => { ElMessage.error('导出失败') })
}

onMounted(async () => { await loadOptions(); loadAll() })
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <div class="page-header">
      <span class="page-title">订单详情 - {{ order.code }}</span>
      <el-tag :type="order.status==='待确认'?'info':order.status==='收货中'?'warning':order.status==='已完成'?'success':'danger'" size="small">{{ order.status }}</el-tag>
    </div>

    <el-tabs v-model="activeTab" style="margin-bottom:12px">
      <el-tab-pane label="订单详情" name="detail" />
      <el-tab-pane label="交货管理" name="delivery" />
    </el-tabs>

    <!-- Tab 1: 订单详情 -->
    <template v-if="activeTab === 'detail'">
      <el-card shadow="never" style="margin-bottom:12px">
        <template #header><span style="font-weight:600">基础信息</span></template>
        <el-form :model="order" label-width="90px" size="small">
          <el-row :gutter="12">
            <el-col :span="8"><el-form-item label="订单号"><el-input :model-value="order.code" readonly class="readonly-input" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="订单类型"><el-input :model-value="order.orderType" readonly class="readonly-input" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item :label="order.orderType==='委外'?'加工厂':'供应商'">
              <el-select v-model="order.supplierId" filterable style="width:100%" :disabled="order.status!=='待确认'" @focus="loadOptions">
                <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item></el-col>
            <el-col :span="8"><el-form-item label="交期"><el-input v-model="order.deliveryDate" type="date" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="订单完成时间"><el-input :model-value="$fmtDate(order.finishTime) || '-'" readonly class="readonly-input" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="备注"><el-input v-model="order.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
          <div style="display:flex;gap:8px;margin-top:12px">
            <el-button v-if="order.status==='待确认'" type="primary" size="small" @click="router.push(`/outsource/material-order/add/${id}`)">编辑物料</el-button>
            <el-button type="primary" size="small" :loading="saving" @click="handleSave" :disabled="order.status==='已取消'">保存</el-button>
            <el-button v-if="order.status==='待确认'" type="success" size="small" @click="handleConfirm">确认</el-button>
            <el-button v-if="order.status==='收货中' || order.status==='已确认'" type="warning" size="small" @click="handleFinish">结单</el-button>
            <el-button v-if="order.status==='收货中' || order.status==='已确认'" type="warning" size="small" @click="openDefectReturn">退不良</el-button>
            <el-button v-if="order.status!=='已完成' && order.status!=='已取消'" type="danger" size="small" @click="handleCancel">取消</el-button>
          </div>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header><span style="font-weight:600">物料明细</span></template>
        <el-table :data="items" border size="small" row-key="id" default-expand-all>
          <el-table-column type="expand" v-if="items.some((it: any) => it.components && it.components.length > 0)">
            <template #default="{row}">
              <div v-if="row.components && row.components.length > 0" style="margin:4px 20px">
                <div style="font-size:12px;color:#606266;margin-bottom:4px;font-weight:500">子物料清单（每套用量 × 下单数 = 需求总数）</div>
                <el-table :data="row.components" border size="small">
                  <el-table-column prop="childMaterialName" label="子物料" min-width="120" />
                  <el-table-column prop="childUnit" label="单位" width="55" />
                  <el-table-column label="需求" width="80"><template #default="{row:r}">{{ r.demandQuantity || 0 }}</template></el-table-column>
                  <el-table-column label="库存" width="75"><template #default="{row:r}"><span :style="{color: Number(r.stockQuantity||0) < Number(r.demandQuantity||0) ? '#f56c6c' : '#67c23a'}">{{ r.stockQuantity || 0 }}</span></template></el-table-column>
                  <el-table-column label="缺料" width="75"><template #default="{row:r}"><span :style="{color: Number(r.shortage||0) > 0 ? '#f56c6c' : '#67c23a'}">{{ r.shortage || 0 }}</span></template></el-table-column>
                  <el-table-column label="已发料" width="75"><template #default="{row:r}"><span :style="{color: r.deliveredQuantity>0?'#67c23a':''}">{{ r.deliveredQuantity || 0 }}</span></template></el-table-column>
                  <el-table-column label="损耗率(%)" width="80"><template #default="{row:r}">{{ r.lossRate || 0 }}</template></el-table-column>
                  <el-table-column label="操作" width="80" align="center"><template #default="{row:r}"><el-button v-if="Number(r.shortage||0) > 0" type="warning" link size="small" @click="goPurchaseComponent(r, row)">去采购</el-button></template></el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="materialType" label="类型" width="80" />
          <el-table-column prop="materialName" label="物料名称" min-width="130" />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column prop="orderQuantity" label="下单数" width="90" />
          <el-table-column label="已收(良)" width="90"><template #default="{row}"><span :style="{color:row.receivedQuantity>0?'#67c23a':''}">{{ row.receivedQuantity || 0 }}</span></template></el-table-column>
          <el-table-column label="已退(不良)" width="90"><template #default="{row}"><span :style="{color:row.defectReturnedQty>0?'#f56c6c':''}">{{ row.defectReturnedQty || 0 }}</span></template></el-table-column>
          <el-table-column prop="unitPrice" label="单价" width="80" />
          <el-table-column prop="amount" label="金额" width="100" />
        </el-table>
      </el-card>

      <el-card shadow="never" style="margin-top:12px">
        <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">合同文件</span><el-button type="warning" size="small" @click="exportPdf">导出合同模板</el-button></div></template>
        <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
          <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">{{ uploadFile.name }}</span><el-button type="primary" size="small" :loading="attachSaving" @click.stop="handleSaveAttach">保存</el-button><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
          <template v-else-if="order.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:#409eff">已有附件</span><el-button type="primary" size="small" @click.stop="openAttach(order.attachUrl)">查看</el-button><el-button type="success" size="small"><a :href="order.attachUrl" download style="color:inherit;text-decoration:none">下载</a></el-button><el-button type="danger" size="small" @click.stop="handleDeleteAttach">删除</el-button><span style="color:#909399;font-size:12px">可拖拽新文件替换</span></div></template>
          <template v-else><p style="color:#909399;margin:0">拖拽文件到此处，或点击选择</p></template>
          <input v-if="!order.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
        </div>
      </el-card>
    </template>

    <!-- Tab 2: 交货管理 -->
    <template v-if="activeTab === 'delivery'">
      <el-row :gutter="12" style="margin-bottom:12px">
        <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">订单总量</p><p style="font-size:20px;font-weight:600;margin:4px 0">{{ totalQuantity }}</p></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">已交数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:#67c23a">{{ deliveredQuantity }}</p></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">剩余数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:#e6a23c">{{ totalQuantity - deliveredQuantity }}</p></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">交货进度</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:#409eff">{{ deliveryProgress }}%</p></el-card></el-col>
      </el-row>
      <el-card shadow="never" style="margin-bottom:12px">
        <el-progress :percentage="deliveryProgress" :stroke-width="16" :text-inside="true" :color="deliveredQuantity>=totalQuantity?'#67c23a':'#409eff'" />
      </el-card>
      <el-card shadow="never">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <span style="font-weight:600">交货记录</span>
          <el-button v-if="order.status==='收货中' || order.status==='已确认'" type="primary" size="small" @click="openReceive">新增交货</el-button>
        </div>
        <el-table :data="deliveries" border stripe size="small">
          <el-table-column type="expand">
            <template #default="{row}">
              <el-table :data="row.items || []" border size="small" style="margin:4px 20px">
                <el-table-column prop="materialName" label="物料" min-width="120" />
                <el-table-column prop="unit" label="单位" width="60" />
                <el-table-column prop="quantity" label="数量" width="90" />
                <el-table-column prop="qualityType" label="品质" width="70"><template #default="{row:r}"><el-tag :type="r.qualityType==='良品'?'success':'danger'" size="small">{{ r.qualityType }}</el-tag></template></el-table-column>
                <el-table-column prop="handleType" label="处理方式" width="100" />
              </el-table>
            </template>
          </el-table-column>
          <el-table-column prop="code" label="单号" width="150" />
          <el-table-column prop="deliveryType" label="类型" width="70"><template #default="{row}"><el-tag :type="row.deliveryType==='收料'?'success':'warning'" size="small">{{ row.deliveryType }}</el-tag></template></el-table-column>
          <el-table-column label="日期" width="110"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
          <el-table-column label="型号" min-width="140" show-overflow-tooltip><template #default="{row}">{{ (row.items||[]).map((i:any)=>i.materialName).join(' / ') }}</template></el-table-column>
          <el-table-column label="数量" width="80" align="right"><template #default="{row}">{{ (row.items||[]).reduce((s:number,i:any)=>s+(i.quantity||0),0) }}</template></el-table-column>
          <el-table-column prop="warehouseName" label="仓库" width="120" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        </el-table>
      </el-card>
    </template>

    <!-- 收货弹窗 -->
    <el-dialog v-model="recVisible" title="新增交货" width="700px" :close-on-click-modal="false">
      <div style="margin-bottom:8px;display:flex;align-items:center;gap:16px">
        <span style="font-size:13px;color:#606266">供应商：<b>{{ order.supplierName || '-' }}</b></span>
        <span style="font-size:13px">收货仓库：</span>
        <el-select v-model="recWarehouseId" filterable size="small" style="width:180px" placeholder="选择仓库"><el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName || w.name || '仓库'+w.id" :value="w.id" /></el-select>
      </div>
      <el-table :data="recItems" border size="small" row-key="itemId">
        <el-table-column type="expand" v-if="recItems.some((it: any) => it.components && it.components.length > 0)">
          <template #default="{row}">
            <div v-if="row.components && row.components.length > 0" style="margin:4px 20px">
              <div style="font-size:12px;color:#f56c6c;margin-bottom:4px">交货将扣减以下子物料库存：</div>
              <el-table :data="row.components" border size="small">
                <el-table-column prop="childMaterialName" label="子物料" min-width="100" />
                <el-table-column prop="childUnit" label="单位" width="50" />
                <el-table-column label="本次需求" width="90"><template #default="{row:r}">{{ Number(r.quantity||1) * Number(row.quantity||0) }}</template></el-table-column>
                <el-table-column label="库存" width="85"><template #default="{row:r}"><span :style="{color: Number(r.stockQuantity||0) < Number(r.quantity||1)*Number(row.quantity||0) ? '#f56c6c' : '#67c23a'}">{{ r.stockQuantity }}</span></template></el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column label="已收" width="70" align="right"><template #default="{row}">{{ (row.receivedQuantity || 0) - (row.defectReturnedQty || 0) }}</template></el-table-column>
        <el-table-column label="本次交货" width="140"><template #default="{row}"><el-input v-model="row.quantity" size="small" type="number" placeholder="数量" /></template></el-table-column>
        <el-table-column prop="orderQuantity" label="下单数" width="80" />
      </el-table>
      <template #footer><el-button @click="recVisible=false">取消</el-button><el-button type="primary" :loading="recSaving" @click="handleReceive">确认交货</el-button></template>
    </el-dialog>

    <!-- 退不良弹窗 -->
    <el-dialog v-model="defectVisible" title="退不良品" width="650px" :close-on-click-modal="false">
      <div style="margin-bottom:8px;display:flex;align-items:center;gap:12px;flex-wrap:wrap">
        <span style="font-size:13px;color:#606266">供应商：<b>{{ order.supplierName || '-' }}</b></span>
        <span style="font-size:13px">处理方式：</span>
        <el-radio-group v-model="defectHandleType" size="small" @change="defectWarehouseId=undefined"><el-radio label="维修返还" /><el-radio label="折现退款" /></el-radio-group>
      </div>
      <div v-if="defectHandleType!=='折现退款'" style="margin-bottom:8px"><el-select v-model="defectWarehouseId" filterable style="width:100%" placeholder="选择退料仓库" @change="onDefectWhChange"><el-option v-for="w in defectWarehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></div>
      <div v-if="defectHandleType==='折现退款'" style="margin-bottom:8px;padding:6px 10px;background:#fdf6ec;border-left:3px solid #e6a23c;font-size:12px;color:#e6a23c">折现退款仅作记录，不会扣减库存。款项由财务后续处理。</div>
      <el-table :data="defectItems" border size="small">
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="available" label="可退" width="70" />
        <el-table-column label="仓库库存" width="90" align="right"><template #default="{row}"><span v-if="row.stockLoading">加载中...</span><span v-else-if="row.warehouseStock===undefined" style="color:#c0c4cc">—</span><span v-else :style="{color:row.warehouseStock<row.quantity?'#f56c6c':'#67c23a'}">{{ row.warehouseStock }}</span></template></el-table-column>
        <el-table-column label="退料数量" width="140"><template #default="{row}"><el-input v-model="row.quantity" size="small" type="number" placeholder="数量" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="defectVisible=false">取消</el-button><el-button type="warning" :loading="defectSaving" @click="handleDefectReturn">确认退料</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-page { padding:16px; }
.page-header { display:flex; align-items:center; gap:12px; margin-bottom:12px; }
.page-title { font-size:18px; font-weight:600; }
.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:#409eff; background:#ecf5ff }
:deep(.readonly-input .el-input__inner) { background-color: #f5f7fa; color: #909399; cursor: default; }
</style>
