<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { exportMaterialOrderPdf } from '@/api/contract-template'
// 2026-09-16：收料/退不良相关枚举与状态（DeliveryType、DefectHandleType、QualityType、DocStatus 等）
// 随「交货管理」页签移出到独立菜单页「物料收货」（views/outsource/material-order/delivery.vue），本页不再使用
import { MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag, OrderType, OrderTypeLabel, OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY } from '@/api/enums'
import RemoteSelect from '@/components/RemoteSelect.vue'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(true)
const order = reactive({ id: 0, code: '', status: '', orderType: OrderType.PURCHASE as string, supplierId: undefined as any, supplierName: '', deliveryDate: '', finishTime: '', remark: '', attachUrl: '', targetWarehouseId: undefined as any })
const items = ref<any[]>([])
const activeTab = ref('detail')
const saving = ref(false)
const materialTypes = ref<any[]>([])

// Odoo 风格：下拉框实时查库
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
const fetchMaterialTypes = (kw: string) => request.get('/dev/material-type/enabled', { params: { kw } })
const fetchMaterialsByType = (kw: string, row: any) => request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw, materialTypeId: row.materialTypeId || undefined } })

// 待审核状态行内编辑物料明细
const allMaterials = ref<any[]>([])
function addItem() { items.value.push({ materialTypeId: undefined, materialId: undefined, materialName: '', unit: '', orderQuantity: 1, unitPrice: undefined, remark: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onMatChange(v: any, row: any) {
  if (!v) { row.materialName = ''; row.unit = ''; return }
  const m = allMaterials.value.find((x: any) => x.id === v)
  if (m) { row.materialName = m.materialName; row.unit = m.unit }
}

// materialTypeId -> 类型名 映射（兜底展示用）
function typeName(bid: number | undefined, fallback?: string) {
  if (bid != null) { const t = materialTypes.value.find((v: any) => v.id === bid); if (t) return t.typeName }
  return fallback || '-'
}
async function loadMaterialTypes() {
  // F7-129（2026-09-20）：加载失败不再静默 —— 留痕，避免"空下拉"被误认为"没有数据"
  try { const r = await request.get<any, any>('/dev/material-type/enabled'); materialTypes.value = r || [] } catch (e: any) { console.warn('加载物料类型失败', e?.message || e) }
}

async function loadOptions() {
  loadMaterialTypes()
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); allMaterials.value = r?.records || [] } catch { allMaterials.value = [] }
}

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
    ElMessage.success('合同文件已保存'); uploadFile.value = null; await loadAll(); markOrderDirty()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) } finally { attachSaving.value = false }
}
async function handleDeleteAttach() {
  try {
    await ElMessageBox.confirm('确定删除附件吗？', '删除附件', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await request.delete(`/outsource/material-order/${id}/attach`); ElMessage.success('附件已删除'); await loadAll(); markOrderDirty()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function loadAll() {
  loading.value = true
  try {
    // 收货记录（收料/退不良）已移到独立菜单页「物料收货」加载，本页不再拉 /deliveries
    const o = await request.get<any, any>(`/outsource/material-order/${id}`)
    if (o) {
      Object.assign(order, { id: o.id, code: o.code, status: o.status, orderType: o.orderType || OrderType.PURCHASE, supplierId: o.supplierId, supplierName: o.supplierName, deliveryDate: o.deliveryDate || '', finishTime: o.finishTime || '', remark: o.remark || '', attachUrl: o.attachUrl || '' })
    }
    items.value = o?.items || []
    loadOptions()
  } finally { loading.value = false }
}

function markOrderDirty() { sessionStorage.setItem(OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY, '1') }

// 子物料缺料「去采购」：带物料/数量/供应商预填跳新增物料订单（本页 Tab1 子物料清单用）
function goPurchaseComponent(comp: any, parentItem: any) {
  const ids = (comp.supplierIds || '') as string; const firstId = ids.split(',')[0]?.trim()
  const p = new URLSearchParams(); if (firstId) p.set('supplierId', firstId)
  if (comp.childMaterialId) p.set('materialId', String(comp.childMaterialId))
  p.set('materialName', comp.childMaterialName || ''); p.set('materialTypeId', String(comp.childMaterialTypeId ?? ''))
  p.set('unit', comp.childUnit || ''); p.set('quantity', String(comp.shortage || 0))
  router.push('/outsource/material-order/add?' + p.toString())
}

async function handleSave() {
  saving.value = true
  try {
    await request.put(`/outsource/material-order/${id}`, { orderType: order.orderType, supplierId: order.supplierId, targetWarehouseId: order.targetWarehouseId, deliveryDate: order.deliveryDate, remark: order.remark, items: items.value })
    ElMessage.success('保存成功')
    await loadAll(); markOrderDirty()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function handleConfirm() {
  try { await ElMessageBox.confirm('审核后进入收货中', '审核', { type: 'warning' }); await request.put(`/outsource/material-order/${id}/audit`); ElMessage.success('已审核'); loadAll(); markOrderDirty() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleUnAudit() {
  try { await ElMessageBox.confirm('确认反审核？将回到待审核状态', '反审核', { type: 'warning' }); await request.put(`/outsource/material-order/${id}/un-audit`); ElMessage.success('已反审核'); loadAll(); markOrderDirty() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleFinish() {
  try { await ElMessageBox.confirm('结单后订单将标记为已完成，不可再修改。', '结单', { type: 'warning' }); await request.put(`/outsource/material-order/${id}/finish`); ElMessage.success('已结单'); loadAll(); markOrderDirty() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleCancel() {
  try { await ElMessageBox.confirm('确定作废？', '作废', { type: 'warning' }); await request.put(`/outsource/material-order/${id}/cancel`); ElMessage.success('已作废'); loadAll(); markOrderDirty() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

function exportPdf() {
  const url = exportMaterialOrderPdf(id)
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
    const link = document.createElement('a'); link.href = URL.createObjectURL(blob)
    link.download = `物料采购合同-${order.code}.docx`; link.click(); URL.revokeObjectURL(link.href)
    ElMessage.success('合同已下载')
  }).catch(() => { ElMessage.error('导出失败') })
}

// 物料类型字典只需加载一次
onMounted(() => { loadMaterialTypes() })
/**
 * 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发，
 * 只靠 onMounted 会停留在上次缓存的状态（如在列表改单后再进详情看到的还是旧数据）。
 * loadAll 内部会自行调用 loadOptions 补齐物料字典，故此处无需额外 await loadOptions。
 */
onActivated(() => { loadAll() })
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-tabs v-model="activeTab" style="margin-bottom:12px">
      <el-tab-pane label="订单详情" name="detail" />
      <!-- 「交货管理」页签已于 2026-09-16 移出为独立菜单页「物料收货」（下方按钮跳转） -->
    </el-tabs>

    <!-- Tab 1: 订单详情 -->
    <template v-if="activeTab === 'detail'">
      <el-card shadow="never" style="margin-bottom:12px">
        <template #header><span style="font-weight:600">基础信息</span></template>
        <el-form :model="order" label-width="90px" size="small">
          <el-row :gutter="12">
            <el-col :span="8"><el-form-item label="订单号"><el-input :model-value="order.code" readonly class="readonly-input" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="订单类型"><el-input :model-value="OrderTypeLabel[order.orderType] || order.orderType" readonly class="readonly-input" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="状态"><el-tag :type="MaterialOrderStatusTag[order.status]||'info'" size="small">{{ MaterialOrderStatusLabel[order.status] || order.status }}</el-tag></el-form-item></el-col>
            <el-col :span="8"><el-form-item :label="order.orderType===OrderType.OUTSOURCE?'加工厂':'供应商'">
              <RemoteSelect v-model="order.supplierId" :fetch="fetchSuppliers" style="width:100%" :disabled="order.status!==MaterialOrderStatus.PENDING" placeholder="选择供应商" />
            </el-form-item></el-col>
            <el-col :span="8"><el-form-item label="交期"><el-input v-model="order.deliveryDate" type="date" :disabled="order.status!==MaterialOrderStatus.PENDING" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="订单完成时间"><el-input :model-value="$fmtDate(order.finishTime) || '-'" readonly class="readonly-input" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="备注"><el-input v-model="order.remark" type="textarea" :rows="2" :disabled="order.status!==MaterialOrderStatus.PENDING" /></el-form-item></el-col>
          </el-row>
          <div style="display:flex;gap:8px;margin-top:12px">
            <!-- F7-127（2026-09-20）：与后端白名单一致 —— 非待审核不可保存（原仅禁 CANCELLED） -->
            <el-button type="primary" size="small" :loading="saving" @click="handleSave" :disabled="order.status!==MaterialOrderStatus.PENDING">保存</el-button>
            <el-button v-if="order.status===MaterialOrderStatus.PENDING" type="success" size="small" @click="handleConfirm">审核</el-button>
            <el-button v-if="order.status===MaterialOrderStatus.RECEIVING" type="warning" size="small" @click="handleUnAudit">反审核</el-button>
            <el-button v-if="order.status===MaterialOrderStatus.RECEIVING" type="warning" size="small" @click="handleFinish">结单</el-button>
            <!-- 收料/退不良 2026-09-16 移出为独立菜单页「物料收货」，此处只留跳转入口 -->
            <el-button type="warning" size="small" @click="router.push(`/outsource/material-order/delivery/${id}`)">物料收货</el-button>
            <el-button v-if="order.status!==MaterialOrderStatus.FINISHED && order.status!==MaterialOrderStatus.CANCELLED" type="danger" size="small" @click="handleCancel">作废</el-button>
          </div>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">物料明细</span><el-button v-if="order.status===MaterialOrderStatus.PENDING" type="primary" size="small" @click="addItem">+ 添加物料</el-button></div></template>
        <el-table :data="items" border size="small" row-key="id" default-expand-all>
          <el-table-column type="expand" v-if="items.some((it: any) => it.components && it.components.length > 0)">
            <template #default="{row}">
              <div v-if="row.components && row.components.length > 0" style="margin:4px 20px">
                <div style="font-size:var(--app-font-xs);color:var(--app-text-regular);margin-bottom:4px;font-weight:500">子物料清单（每套用量 × 下单数 = 需求总数）</div>
                <el-table :data="row.components" border size="small">
                  <el-table-column prop="childMaterialName" label="子物料" min-width="120" />
                  <el-table-column prop="childUnit" label="单位" width="55" />
                  <el-table-column label="需求" width="80"><template #default="{row:r}">{{ r.demandQuantity || 0 }}</template></el-table-column>
                  <el-table-column label="已出货消耗" width="85"><template #default="{row:r}"><span :style="{color: r.deliveredQuantity>0?'var(--app-color-primary)':''}">{{ r.deliveredQuantity || 0 }}</span></template></el-table-column>
                  <el-table-column label="剩余需求" width="80"><template #default="{row:r}">{{ Math.max(0, Number(r.demandQuantity||0) - Number(r.deliveredQuantity||0)) }}</template></el-table-column>
                  <el-table-column label="库存" width="70"><template #default="{row:r}"><span :style="{color: Number(r.stockQuantity||0) < Number(r.demandQuantity||0) ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ r.stockQuantity || 0 }}</span></template></el-table-column>
                  <el-table-column label="可能在途" width="80"><template #default="{row:r}"><span :style="{color: Number(r.inTransit||0) > 0 ? 'var(--app-color-primary)' : ''}">{{ r.inTransit || 0 }}</span></template></el-table-column>
                  <el-table-column label="缺料" width="70"><template #default="{row:r}"><span :style="{color: Number(r.shortage||0) > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ r.shortage || 0 }}</span></template></el-table-column>
                  <el-table-column label="损耗率(%)" width="80"><template #default="{row:r}">{{ r.lossRate || 0 }}</template></el-table-column>
                  <el-table-column label="操作" width="80" align="center"><template #default="{row:r}"><el-button v-if="Number(r.shortage||0) > 0" type="warning" link size="small" @click="goPurchaseComponent(r, row)">去采购</el-button></template></el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="130"><template #default="{row}">
            <RemoteSelect v-if="order.status===MaterialOrderStatus.PENDING" v-model="row.materialTypeId" :fetch="fetchMaterialTypes" label-key="typeName" size="small" clearable style="width:100%" @change="() => { row.materialId = undefined; row.materialName = ''; row.unit = '' }" />
            <span v-else>{{ typeName(row.materialTypeId) }}</span>
          </template></el-table-column>
          <el-table-column label="物料名称" min-width="150"><template #default="{row}">
            <RemoteSelect v-if="order.status===MaterialOrderStatus.PENDING" v-model="row.materialId" :fetch="(kw:string)=>fetchMaterialsByType(kw,row)" label-key="materialName" size="small" filterable clearable disable-cache style="width:100%" :preset="row.materialId?{id:row.materialId,materialName:row.materialName}:null" @change="(v:any)=>onMatChange(v,row)" />
            <span v-else>{{ row.materialName }}</span>
          </template></el-table-column>
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column label="下单数" width="110"><template #default="{row}">
            <el-input-number v-if="order.status===MaterialOrderStatus.PENDING" v-model="row.orderQuantity" :min="0" :precision="0" :step="1" size="small" style="width:100%" />
            <span v-else>{{ row.orderQuantity }}</span>
          </template></el-table-column>
          <el-table-column label="已出货" width="90"><template #default="{row}"><span :style="{color:row.receivedQuantity>0?'var(--app-color-success)':''}">{{ row.receivedQuantity || 0 }}</span></template></el-table-column>
          <el-table-column label="已退(不良)" width="90"><template #default="{row}"><span :style="{color:row.defectReturnedQty>0?'var(--app-color-danger)':''}">{{ row.defectReturnedQty || 0 }}</span></template></el-table-column>
          <!-- 送修中（2026-09-17）：维修退货已送修未返回的数量，已从「已出货」中扣出（修好返回后自动加回） -->
          <el-table-column label="送修中" width="80"><template #default="{row}"><span :style="{color:Number(row.repairReturnedQty)>0?'var(--app-color-warning)':''}">{{ row.repairReturnedQty || 0 }}</span></template></el-table-column>
          <el-table-column label="单价" width="110"><template #default="{row}">
            <el-input-number v-if="order.status===MaterialOrderStatus.PENDING" v-model="row.unitPrice" :min="0" :precision="2" size="small" style="width:100%" />
            <span v-else>{{ row.unitPrice }}</span>
          </template></el-table-column>
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column v-if="order.status===MaterialOrderStatus.PENDING" label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button></template></el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" style="margin-top:12px">
        <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">合同文件</span><el-button type="warning" size="small" @click="exportPdf">导出合同模板</el-button></div></template>
        <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'var(--app-color-success)':'var(--app-border-color)', background: uploadFile?'#f0f9eb':'#fafafa' }">
          <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:var(--app-color-success);font-weight:600">{{ uploadFile.name }}</span><el-button type="primary" size="small" :loading="attachSaving" @click.stop="handleSaveAttach">保存</el-button><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
          <template v-else-if="order.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:var(--app-color-primary)">已有附件</span><el-button type="primary" size="small" @click.stop="openAttach(order.attachUrl)">查看</el-button><el-button type="success" size="small"><a :href="order.attachUrl" download style="color:inherit;text-decoration:none">下载</a></el-button><el-button type="danger" size="small" @click.stop="handleDeleteAttach">删除</el-button><span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">可拖拽新文件替换</span></div></template>
          <template v-else><p style="color:var(--app-text-secondary);margin:0">拖拽文件到此处，或点击选择</p></template>
          <input v-if="!order.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
        </div>
      </el-card>
    </template>

      </div>
</template>

<style scoped>
.detail-page { padding:16px; }
.page-header { display:flex; align-items:center; gap:12px; margin-bottom:12px; }

.drop-zone { position:relative; border:2px dashed var(--app-border-color); border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:var(--app-color-primary); background:#ecf5ff }
:deep(.readonly-input .el-input__inner) { background-color: var(--app-bg-hover); color: var(--app-text-secondary); cursor: default; }
</style>
