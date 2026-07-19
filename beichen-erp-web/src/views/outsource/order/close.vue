<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

defineOptions({ name: 'OrderClose' })
const route = useRoute(); const router = useRouter()
const orderId = Number(route.params.id)
const loading = ref(true)

const report = reactive({
  orderId: 0, orderCode: '', factoryName: '', products: [] as any[],
  deliveries: [] as any[],
  reportId: 0, reportStatus: '', reportRemark: '', closeDate: ''
})
const items = ref<any[]>([])
const remark = ref('')

/** 自动计算 */
function recalc(row: any) {
  const delivered = Number(row.deliveredQuantity) || 0
  const shipped = Number(row.shippedQuantity) || 0
  const good = Number(row.goodReturnQty) || 0
  const defect = Number(row.defectReturnQty) || 0
  const targetYield = Number(row.targetYieldRate) || 0
  // 缺失 = 发料 - 退料总计 - 出货消耗
  row.missingQty = delivered - (good + defect) - shipped
  // 最大超损 = (发料 - 良品退料) × (1 - 加工良率/100)
  row.maxExcessLossQty = Math.max(0, +( (delivered - good) * (1 - targetYield / 100) ).toFixed(2))
  // 超损总价 = 超损数量 × 物料单价
  row.excessLossAmount = +(row.excessLossQty * (row.unitPrice || 0)).toFixed(2)
  if (delivered > 0) {
    row.actualYieldRate = +((shipped + good) / delivered * 100).toFixed(2)
    row.yieldLoss = +(targetYield - row.actualYieldRate).toFixed(2)
    const lossPct = (100 - targetYield) / 100
    let excess = delivered * (1 - lossPct) - shipped - good
    row.excessLossQty = Math.max(0, +excess.toFixed(2))
  } else {
    row.actualYieldRate = 0; row.yieldLoss = 0; row.excessLossQty = 0
  }
}

function onGoodChange(row: any) { recalc(row) }
function onDefectChange(row: any) {
  row.defectReturnQty = Math.max(0, Number(row.defectReturnQty) || 0)
  recalc(row)
}

async function loadReport() {
  loading.value = true
  try {
    const r = await request.get<any, any>(`/outsource/order/${orderId}/close-report`)
    Object.assign(report, r)
    items.value = r.items || []
    remark.value = r.reportRemark || ''
  } catch (e: any) {
    ElMessage.error('加载失败: ' + (e?.message || '未知错误'))
  } finally { loading.value = false }
}

const canConfirm = computed(() => report.reportStatus !== '已结单' && report.reportStatus !== '未生成')

async function handleSave() {
  try {
    await request.put(`/outsource/order/${orderId}/close-report`, { items: items.value, remark: remark.value })
    ElMessage.success('已保存草稿')
    loadReport()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e?.message || '未知错误'))
  }
}

function handleExport() {
  window.open(`/api/outsource/order/${orderId}/close-report/export`)
}

async function handleConfirm() {
  try {
    await ElMessageBox.confirm('确认结单？结单后将自动生成退料单，加工单状态变为"已完成"。', '确认结单', { type: 'warning' })
  } catch { return }
  try {
    // 先保存最终数据
    await request.put(`/outsource/order/${orderId}/close-report`, { items: items.value, remark: remark.value })
    await request.post(`/outsource/order/${orderId}/close-report/confirm`)
    ElMessage.success('结单完成')
    loadReport()
  } catch (e: any) {
    ElMessage.error('结单失败: ' + (e?.message || '未知错误'))
  }
}

function fmt(v: any) { return v !== undefined && v !== null ? Number(v).toFixed(2) : '0.00' }

onMounted(loadReport)
</script>

<template>
  <div class="close-page" v-loading="loading">
    <div class="page-header">
      <span class="page-title">结单报表</span>
      <el-tag v-if="report.reportStatus === '已结单'" type="success">已结单</el-tag>
      <el-tag v-else-if="report.reportStatus === '草稿'" type="warning">草稿</el-tag>
      <el-tag v-else type="info">未生成</el-tag>
    </div>

    <!-- 表头 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="加工单号">{{ report.orderCode }}</el-descriptions-item>
        <el-descriptions-item label="代工厂">{{ report.factoryName }}</el-descriptions-item>
        <el-descriptions-item label="产品">
          {{ report.products?.map((p:any) => `${p.productName}×${p.quantity}`).join(' / ') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="结单日期">{{ report.closeDate || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:8px">
        <span style="font-weight:500;font-size:13px">备注：</span>
        <el-input v-model="remark" placeholder="结单备注" size="small" style="width:400px;margin-left:4px" />
      </div>
    </el-card>

    <!-- 物料明细 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-table :data="items" border size="small" stripe show-summary :summary-method="() => []">
        <el-table-column prop="materialType" label="类目" width="70" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" />


        <el-table-column label="发料数量" width="90"><template #default="{row}">{{ fmt(row.deliveredQuantity) }}</template></el-table-column>
        <el-table-column label="退料总计" width="90" align="right"><template #default="{row}">{{ fmt((+row.goodReturnQty||0) + (+row.defectReturnQty||0)) }}</template></el-table-column>
        <el-table-column label="出货消耗" width="90"><template #default="{row}">{{ fmt(row.shippedQuantity) }}</template></el-table-column>
        <el-table-column label="良品退料" width="100">
          <template #default="{row}"><el-input v-model="row.goodReturnQty" size="small" type="number" @change="onGoodChange(row)" /></template>
        </el-table-column>
        <el-table-column label="不良退料" width="100">
          <template #default="{row}"><el-input v-model="row.defectReturnQty" size="small" type="number" @change="onDefectChange(row)" /></template>
        </el-table-column>
        <el-table-column label="缺失" width="80" align="right">
          <template #default="{row}"><span :style="{color: row.missingQty !== 0 ? '#f56c6c' : '#67c23a'}">{{ fmt(row.missingQty) }}</span></template>
        </el-table-column>
        <el-table-column label="加工良率%" width="90">
          <template #default="{row}"><span style="color:#409eff">{{ fmt(row.targetYieldRate) }}</span></template>
        </el-table-column>
        <el-table-column label="生产良率%" width="90">
          <template #default="{row}"><span :style="{color: row.yieldLoss > 0 ? '#e6a23c' : '#67c23a'}">{{ fmt(row.actualYieldRate) }}</span></template>
        </el-table-column>
        <el-table-column label="良率超损%" width="90">
          <template #default="{row}"><span :style="{color: row.yieldLoss > 0 ? '#f56c6c' : '#67c23a'}">{{ fmt(row.yieldLoss) }}</span></template>
        </el-table-column>
        <el-table-column label="超损数量" width="90">
          <template #default="{row}"><span :style="{color: row.excessLossQty > 0 ? '#f56c6c' : '#67c23a'}">{{ fmt(row.excessLossQty) }}</span></template>
        </el-table-column>
        <el-table-column label="最大超损" width="90">
          <template #default="{row}">{{ fmt(row.maxExcessLossQty) }}</template>
        </el-table-column>
        <el-table-column label="物料单价" width="100">
          <template #default="{row}"><el-input v-model="row.unitPrice" size="small" @change="recalc(row)" /></template>
        </el-table-column>
        <el-table-column label="超损总价" width="100">
          <template #default="{row}"><span :style="{color: row.excessLossAmount > 0 ? '#f56c6c' : '#67c23a'}">{{ fmt(row.excessLossAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="备注" min-width="100"><template #default="{row}"><el-input v-model="row.remark" size="small" placeholder="备注" /></template></el-table-column>
      </el-table>
    </el-card>

    <!-- 交货记录 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <template #header><span style="font-weight:600">交货记录</span></template>
      <div style="display:flex;gap:20px;margin-bottom:12px;font-size:13px;color:#606266">
        <span>正常交货：<b style="color:#67c23a">{{ (report.deliveries || []).filter((d:any)=>d.deliveryType!=='退不良').reduce((s:number,d:any)=>s+(d.quantity||0),0) }}</b></span>
        <span>退不良：<b style="color:#e6a23c">{{ Math.abs((report.deliveries || []).filter((d:any)=>d.deliveryType==='退不良').reduce((s:number,d:any)=>s+(d.quantity||0),0)) }}</b></span>
        <span>实际已交：<b style="color:#409eff">{{ (report.deliveries || []).reduce((s:number,d:any)=>s+(d.quantity||0),0) }}</b></span>
      </div>
      <el-table :data="report.deliveries" border size="small">
        <el-table-column label="日期" width="110"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column prop="productName" label="产品" min-width="130" />
        <el-table-column label="数量" width="100" align="right"><template #default="{row}"><span :style="{color:Number(row.quantity)<0?'#f56c6c':''}">{{ row.quantity }}</span></template></el-table-column>
        <el-table-column prop="trackingNo" label="物流单号" width="150" />
        <el-table-column prop="remark" label="备注" min-width="150" />
      </el-table>
    </el-card>

    <!-- 操作 -->
    <div style="display:flex;gap:12px">
      <el-button type="primary" :disabled="report.reportStatus==='已结单'" @click="handleSave">保存草稿</el-button>
      <el-button type="success" :disabled="!canConfirm" @click="handleConfirm">确认结单</el-button>
      <el-button type="info" @click="handleExport">导出Excel</el-button>
    </div>
  </div>
</template>

<style scoped>
.close-page { padding: 16px; }
.page-header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.page-title { font-size: 18px; font-weight: 600; }
</style>
