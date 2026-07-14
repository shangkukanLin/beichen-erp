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
const recItems = ref<any[]>([])
const factoryOptions = ref<any[]>([])

async function loadFactories() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } }); factoryOptions.value = r?.records || [] } catch { }
}

const defectVisible = ref(false); const defectSaving = ref(false)
const defectItems = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>(`/outsource/material-order/${id}`)
    order.value = r || {}
    items.value = r?.items || []
  } finally { loading.value = false }
}

function openReceive() {
  recFactoryId.value = undefined
  recItems.value = items.value.map((it: any) => ({
    itemId: it.id, materialName: it.materialName, orderQuantity: it.orderQuantity,
    receivedQuantity: it.receivedQuantity, quantity: undefined as any
  }))
  recVisible.value = true; loadFactories()
}
async function handleReceive() {
  if (!recFactoryId.value) { ElMessage.warning('请选择收货加工厂'); return }
  const data = recItems.value.filter(r => r.quantity && Number(r.quantity) > 0)
  if (data.length === 0) { ElMessage.warning('请输入收货数量'); return }
  recSaving.value = true
  try { await request.post(`/outsource/material-order/${id}/receive`, { factoryId: recFactoryId.value, items: data }); ElMessage.success('收货完成'); recVisible.value = false; loadData() }
  catch (e: any) { ElMessage.error(e?.message || '收货失败') } finally { recSaving.value = false }
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
        <el-button type="warning" @click="exportPdf">导出合同</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-table :data="items" border size="small">
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

    <!-- 收货弹窗 -->
    <el-dialog v-model="recVisible" title="收货" width="550px" :close-on-click-modal="false">
      <div style="margin-bottom:8px"><span style="font-weight:500;font-size:13px">收货加工厂：</span><el-select v-model="recFactoryId" filterable size="small" style="width:200px" placeholder="选择加工厂"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></div>
      <el-table :data="recItems" border size="small">
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
</style>
