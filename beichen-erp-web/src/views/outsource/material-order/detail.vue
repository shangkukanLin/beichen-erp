<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { exportMaterialOrderPdf } from '@/api/contract-template'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(true)
const order = ref<any>({})
const items = ref<any[]>([])

const recVisible = ref(false); const recSaving = ref(false)
const recFactoryId = ref<number>()
const recWarehouseId = ref<number>()
const recItems = ref<any[]>([])
const factoryOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])

async function loadFactories() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } }); factoryOptions.value = r?.records || [] } catch { }
}

const defectVisible = ref(false); const defectSaving = ref(false)
const defectItems = ref<any[]>([])

// 附件上传
const uploadFile = ref<File | null>(null)
const attachSaving = ref(false)
function openAttach(url: string) { window.open(url + '?inline=true') }
function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }
async function handleSaveAttach() {
  if (!uploadFile.value) return
  attachSaving.value = true
  try {
    const fd = new FormData(); fd.append('file', uploadFile.value)
    const res = await request.post<any, string>('/dev/file/upload', fd)
    await request.put(`/outsource/material-order/${id}`, { ...order.value, attachUrl: res as unknown as string, items: items.value })
    ElMessage.success('合同文件已保存')
    uploadFile.value = null
    await loadData()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) } finally { attachSaving.value = false }
}
async function handleDeleteAttach() {
  try {
    await ElMessageBox.confirm('确定删除附件吗？', '删除附件', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await request.delete(`/outsource/material-order/${id}/attach`)
    ElMessage.success('附件已删除')
    await loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>(`/outsource/material-order/${id}`)
    order.value = r || {}
    items.value = r?.items || []
  } finally { loading.value = false }
}

async function openReceive() {
  recWarehouseId.value = undefined
  recItems.value = items.value.map((it: any) => ({
    itemId: it.id, materialName: it.materialName, orderQuantity: it.orderQuantity,
    receivedQuantity: it.receivedQuantity, quantity: undefined as any,
    components: (it.components || []).map((c: any) => ({
      childMaterialName: c.childMaterialName,
      childUnit: c.childUnit,
      stockQuantity: c.stockQuantity || 0,
      quantity: c.quantity || 1,
    }))
  }))
  // 加载所有仓库
  try {
    const r = await request.get<any, any>('/outsource/warehouse/page', { params: { pageSize: 500 } })
    warehouseOptions.value = (r?.records || [])
  } catch { warehouseOptions.value = [] }
  recVisible.value = true
}
async function handleReceive() {
  if (!recWarehouseId.value) { ElMessage.warning('请选择收货仓库'); return }
  const data = recItems.value.filter(r => r.quantity && Number(r.quantity) > 0)
  if (data.length === 0) { ElMessage.warning('请输入收货数量'); return }
  recSaving.value = true
  try { await request.post(`/outsource/material-order/${id}/receive`, { warehouseId: recWarehouseId.value, items: data }); ElMessage.success('收货完成'); recVisible.value = false; loadData() }
  catch (e: any) { ElMessage.error(e?.message || '收货失败') } finally { recSaving.value = false }
}

function goPurchaseComponent(comp: any, parentItem: any) {
  const ids = (comp.supplierIds || '') as string
  const firstId = ids.split(',')[0]?.trim()
  const p = new URLSearchParams()
  if (firstId) p.set('supplierId', firstId)
  if (comp.childMaterialId) p.set('materialId', String(comp.childMaterialId))
  p.set('materialName', comp.childMaterialName || '')
  p.set('materialType', comp.childMaterialType || '')
  p.set('unit', comp.childUnit || '')
  p.set('quantity', String(comp.shortage || 0))
  router.push('/outsource/material-order/add?' + p.toString())
}

function openDefectReturn() {
  recFactoryId.value = undefined
  defectItems.value = items.value.filter((it: any) => it.receivedQuantity > 0).map((it: any) => ({
    itemId: it.id, materialName: it.materialName, available: (it.receivedQuantity || 0) - (it.defectReturnedQty || 0), quantity: undefined as any
  }))
  defectVisible.value = true; loadFactories()
}
async function handleDefectReturn() {
  if (!recFactoryId.value) { ElMessage.warning('请选择退料加工厂'); return }
  const data = defectItems.value.filter(r => r.quantity && Number(r.quantity) > 0)
  if (data.length === 0) { ElMessage.warning('请输入退料数量'); return }
  defectSaving.value = true
  try { await request.post(`/outsource/material-order/${id}/return-defect`, { factoryId: recFactoryId.value, items: data }); ElMessage.success('退不良完成'); defectVisible.value = false; loadData() }
  catch (e: any) { ElMessage.error(e?.message || '退料失败') } finally { defectSaving.value = false }
}

async function handleConfirm() {
  try { await ElMessageBox.confirm('确认后进入收货中', '确认', { type: 'warning' }); await request.put(`/outsource/material-order/${id}/confirm`); ElMessage.success('已确认'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleCancel() {
  try { await ElMessageBox.confirm('确定取消？', '取消', { type: 'warning' }); await request.put(`/outsource/material-order/${id}/cancel`); ElMessage.success('已取消'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

function exportPdf() {
  const url = exportMaterialOrderPdf(id)
  request.get(url, { responseType: 'blob' }).then((res: any) => {
    const blob = new Blob([res], { type: 'application/pdf' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `物料采购合同-${order.value.code}.pdf`
    link.click()
    URL.revokeObjectURL(link.href)
    ElMessage.success('PDF合同已下载')
  }).catch(() => { ElMessage.error('导出失败') })
}

onMounted(loadData)
onActivated(loadData)
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <div class="page-header"><el-button @click="router.push('/outsource/material-order')">← 返回</el-button><span class="page-title">订单详情 - {{ order.code }}</span><el-tag :type="order.status==='待确认'?'info':order.status==='收货中'?'warning':order.status==='已完成'?'success':'danger'" size="small">{{ order.status }}</el-tag></div>

    <el-card shadow="never" style="margin-bottom:12px">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="订单号">{{ order.code }}</el-descriptions-item>
        <el-descriptions-item label="供应商" :span="2">{{ order.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="交期">{{ order.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单完成时间" :span="2">{{ order.finishTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ order.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:12px;display:flex;gap:8px">
        <el-button v-if="order.status==='待确认'" type="primary" @click="router.push(`/outsource/material-order/add/${id}`)">编辑</el-button>
        <el-button v-if="order.status==='待确认'" type="success" @click="handleConfirm">确认</el-button>
        <el-button v-if="order.status==='收货中' || order.status==='已确认'" type="primary" @click="openReceive">收货</el-button>
        <el-button v-if="order.status==='收货中' || order.status==='已确认'" type="warning" @click="openDefectReturn">退不良</el-button>
        <el-button v-if="order.status!=='已完成' && order.status!=='已取消'" type="danger" @click="handleCancel">取消</el-button>
      
      </div>
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
                <el-table-column label="库存" width="75">
                  <template #default="{row:r}">
                    <span :style="{color: Number(r.stockQuantity||0) < Number(r.demandQuantity||0) ? '#f56c6c' : '#67c23a'}">{{ r.stockQuantity || 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="缺料" width="75">
                  <template #default="{row:r}">
                    <span :style="{color: Number(r.shortage||0) > 0 ? '#f56c6c' : '#67c23a'}">{{ r.shortage || 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="已发料" width="75">
                  <template #default="{row:r}"><span :style="{color: r.deliveredQuantity>0?'#67c23a':''}">{{ r.deliveredQuantity || 0 }}</span></template>
                </el-table-column>
                <el-table-column label="损耗率(%)" width="80"><template #default="{row:r}">{{ r.lossRate || 0 }}</template></el-table-column>
                <el-table-column label="操作" width="80" align="center">
                  <template #default="{row:r}">
                    <el-button v-if="Number(r.shortage||0) > 0" type="warning" link size="small" @click="goPurchaseComponent(r, row)">去采购</el-button>
                  </template>
                </el-table-column>
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

    <!-- 合同文件 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">合同文件</span>
          <el-button type="warning" size="small" @click="exportPdf">导出合同模板</el-button>
        </div>
      </template>
      <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
        <template v-if="uploadFile">
          <div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap">
            <span style="color:#67c23a;font-weight:600">{{ uploadFile.name }}</span>
            <el-button type="primary" size="small" :loading="attachSaving" @click.stop="handleSaveAttach">保存</el-button>
            <el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button>
          </div>
        </template>
        <template v-else-if="order.attachUrl">
          <div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap">
            <span style="color:#409eff">已有附件</span>
            <el-button type="primary" size="small" @click.stop="openAttach(order.attachUrl)">查看</el-button>
            <el-button type="success" size="small"><a :href="order.attachUrl" download style="color:inherit;text-decoration:none">下载</a></el-button>
            <el-button type="danger" size="small" @click.stop="handleDeleteAttach">删除</el-button>
            <span style="color:#909399;font-size:12px">可拖拽新文件替换</span>
          </div>
        </template>
        <template v-else><p style="color:#909399;margin:0">拖拽文件到此处，或点击选择</p></template>
        <input v-if="!order.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
    </el-card>

    <!-- 收货弹窗 -->
    <el-dialog v-model="recVisible" title="收货" width="700px" :close-on-click-modal="false">
      <div style="margin-bottom:8px;display:flex;align-items:center;gap:16px">
        <span style="font-size:13px;color:#606266">加工厂：<b>{{ order.supplierName || '-' }}</b></span>
        <span style="font-size:13px">收货仓库：</span>
        <el-select v-model="recWarehouseId" filterable size="small" style="width:180px" placeholder="选择仓库">
          <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName || w.name || '仓库'+w.id" :value="w.id" />
        </el-select>
      </div>
      <el-table :data="recItems" border size="small" row-key="itemId">
        <el-table-column type="expand" v-if="recItems.some((it: any) => it.components && it.components.length > 0)">
          <template #default="{row}">
            <div v-if="row.components && row.components.length > 0" style="margin:4px 20px">
              <div style="font-size:12px;color:#f56c6c;margin-bottom:4px">收货将扣减以下子物料库存：</div>
              <el-table :data="row.components" border size="small">
                <el-table-column prop="childMaterialName" label="子物料" min-width="100" />
                <el-table-column prop="childUnit" label="单位" width="50" />
                <el-table-column label="每套用量" width="80"><template #default="{row:r}">{{ r.quantity }}</template></el-table-column>
                <el-table-column label="本次需求" width="90"><template #default="{row:r}">{{ Number(r.quantity||1) * Number(row.quantity||0) }}</template></el-table-column>
                <el-table-column label="工厂库存" width="85">
                  <template #default="{row:r}">
                    <span :style="{color: Number(r.stockQuantity||0) < Number(r.quantity||1)*Number(row.quantity||0) ? '#f56c6c' : '#67c23a'}">{{ r.stockQuantity }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="receivedQuantity" label="已收" width="70" />
        <el-table-column label="本次收货" width="140"><template #default="{row}"><el-input v-model="row.quantity" size="small" type="number" placeholder="收货数量" /></template></el-table-column>
        <el-table-column prop="orderQuantity" label="下单数" width="80" />
      </el-table>
      <template #footer><el-button @click="recVisible=false">取消</el-button><el-button type="primary" :loading="recSaving" @click="handleReceive">确认收货</el-button></template>
    </el-dialog>

    <!-- 退不良弹窗 -->
    <el-dialog v-model="defectVisible" title="退不良品" width="550px" :close-on-click-modal="false">
      <div style="margin-bottom:8px"><span style="font-weight:500;font-size:13px">退料加工厂：</span><el-select v-model="recFactoryId" filterable size="small" style="width:200px" placeholder="选择加工厂"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></div>
      <el-table :data="defectItems" border size="small">
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="available" label="可退" width="70" />
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
</style>
