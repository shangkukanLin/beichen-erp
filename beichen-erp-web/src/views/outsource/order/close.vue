<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DeliveryType, CloseReportStatus, CloseReportStatusLabel } from '@/api/enums'
import RemoteSelect from '@/components/RemoteSelect.vue'

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
const bomTypes = ref<any[]>([])
const returnWarehouseId = ref<number | null>(null)
// 退回仓库选择：纯 Odoo 方案，RemoteSelect 实时查库
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { warehouseName: kw, pageSize: 500 } })

// bomTypeId -> 类型名 映射（兜底展示用）
function typeName(id: number | undefined, fallback?: string) {
  if (id != null) { const t = bomTypes.value.find((v: any) => v.id === id); if (t) return t.typeName }
  return fallback || '-'
}
async function loadBomTypes() {
  try { const r = await request.get<any, any>('/dev/bom-type/enabled'); bomTypes.value = r || [] } catch {}
}

/** 自动计算 */
function recalc(row: any) {
  const shipped = Number(row.shippedQuantity) || 0
  const good = Number(row.goodReturnQty) || 0
  const defect = Number(row.defectReturnQty) || 0
  const targetYield = Number(row.targetYieldRate) || 0
  const factoryRetain = Number(row.factoryRetainQty) || 0
  const missing = Number(row.missingQty) || 0
  // 用料总数 = 出货消耗 + 良品退料 + 不良退料 + 留存工厂 + 缺失（缺失为手动填写）
  row.usedTotalQuantity = shipped + good + defect + factoryRetain + missing
  // 生产良率% = 出货消耗 / (用料总数 - 工厂留存 - 良品退料) × 100
  const denom = row.usedTotalQuantity - factoryRetain - good
  if (denom > 0) {
    row.actualYieldRate = +(shipped / denom * 100).toFixed(2)
  } else {
    row.actualYieldRate = 0
  }
  // 良率超损% = 加工良率 - 生产良率
  row.yieldLoss = +(targetYield - row.actualYieldRate).toFixed(2)
  // 超损数量 = (出货消耗 + 不良退料 + 缺失) × (良率超损%/100)（最小0）
  row.excessLossQty = Math.max(0, +((shipped + defect + missing) * (row.yieldLoss / 100)).toFixed(2))
  // 最大超损 = (用料总数 - 良品退料 - 工厂留存) × (1 - 加工良率/100)（最小0）
  row.maxExcessLossQty = Math.max(0, +((row.usedTotalQuantity - good - factoryRetain) * (1 - targetYield / 100)).toFixed(2))
  // 超损总价 = 超损数量 × 物料单价
  row.excessLossAmount = +(row.excessLossQty * (row.unitPrice || 0)).toFixed(2)
}

function onGoodChange(row: any) { recalc(row) }
function onDefectChange(row: any) {
  row.defectReturnQty = Math.max(0, Number(row.defectReturnQty) || 0)
  recalc(row)
}
function onRetainChange(row: any) { recalc(row) }

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
  if (!returnWarehouseId.value) {
    ElMessage.warning('请选择退回仓库')
    return
  }
  try {
    await ElMessageBox.confirm('确认结单？结单后将自动生成退料单，加工单状态变为"已完成"。', '确认结单', { type: 'warning' })
  } catch { return }
  try {
    // 先保存最终数据
    await request.put(`/outsource/order/${orderId}/close-report`, { items: items.value, remark: remark.value })
    await request.post(`/outsource/order/${orderId}/close-report/confirm`, { returnWarehouseId: returnWarehouseId.value })
    ElMessage.success('结单完成')
    loadReport()
  } catch (e: any) {
    ElMessage.error('结单失败: ' + (e?.message || '未知错误'))
  }
}

async function handleReopen() {
  try {
    await ElMessageBox.confirm('确认反结单？将逆向退料、缺失、超损应付，加工单状态回到"生产中"。', '反结单', { type: 'warning' })
  } catch { return }
  try {
    await request.post(`/outsource/order/${orderId}/close-report/reopen`)
    ElMessage.success('反结单完成')
    loadReport()
  } catch (e: any) {
    ElMessage.error('反结单失败: ' + (e?.message || '未知错误'))
  }
}

function fmt(v: any) { return v !== undefined && v !== null ? Number(v).toFixed(2) : '0.00' }

onMounted(() => { loadBomTypes(); loadReport() })
</script>

<template>
  <div class="close-page" v-loading="loading">
    <div class="page-header">
      <el-tag v-if="report.reportStatus === CloseReportStatus.FINISHED" type="success">{{ CloseReportStatusLabel[CloseReportStatus.FINISHED] }}</el-tag>
      <el-tag v-else-if="report.reportStatus === CloseReportStatus.DRAFT" type="warning">{{ CloseReportStatusLabel[CloseReportStatus.DRAFT] }}</el-tag>
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
        <span style="font-weight:500;font-size:var(--app-font-sm)">备注：</span>
        <el-input v-model="remark" placeholder="结单备注" size="small" style="width:400px;margin-left:4px" />
      </div>
    </el-card>

    <!-- 物料明细 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span style="font-weight:600">物料明细</span>
          <div style="display:flex;align-items:center;gap:8px">
            <span style="font-size:var(--app-font-sm);color:var(--app-text-regular)">退回仓库：</span>
            <RemoteSelect v-model="returnWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="请选择退回仓库" size="small" style="width:200px" :disabled="report.reportStatus==='已结单'" />
          </div>
        </div>
      </template>
      <el-table :data="items" border size="small" stripe show-summary :summary-method="() => []">
        <el-table-column label="类目" width="70"><template #default="{row}">{{ typeName(row.bomTypeId) }}</template></el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="120" />


        <el-table-column label="用料总数" width="90"><template #default="{row}">{{ fmt(row.usedTotalQuantity) }}</template></el-table-column>
        <el-table-column label="退料总计" width="90" align="right"><template #default="{row}">{{ fmt((+row.goodReturnQty||0) + (+row.defectReturnQty||0)) }}</template></el-table-column>
        <el-table-column label="出货消耗" width="90"><template #default="{row}">{{ fmt(row.shippedQuantity) }}</template></el-table-column>
        <el-table-column label="良品退料" width="100">
          <template #default="{row}"><el-input v-model="row.goodReturnQty" size="small" type="number" @change="onGoodChange(row)" /></template>
        </el-table-column>
        <el-table-column label="不良退料" width="100">
          <template #default="{row}"><el-input v-model="row.defectReturnQty" size="small" type="number" @change="onDefectChange(row)" /></template>
        </el-table-column>
        <el-table-column label="留存工厂" width="90">
          <template #default="{row}"><el-input v-model="row.factoryRetainQty" size="small" type="number" @change="onRetainChange(row)" /></template>
        </el-table-column>
        <el-table-column label="缺失" width="90">
          <template #default="{row}"><el-input v-model="row.missingQty" size="small" type="number" @change="recalc(row)" /></template>
        </el-table-column>
        <el-table-column label="加工良率%" width="90">
          <template #default="{row}"><span style="color:var(--app-color-primary)">{{ fmt(row.targetYieldRate) }}</span></template>
        </el-table-column>
        <el-table-column label="生产良率%" width="90">
          <template #default="{row}"><span :style="{color: row.yieldLoss > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)'}">{{ fmt(row.actualYieldRate) }}</span></template>
        </el-table-column>
        <el-table-column label="良率超损%" width="90">
          <template #default="{row}"><span :style="{color: row.yieldLoss > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ fmt(row.yieldLoss) }}</span></template>
        </el-table-column>
        <el-table-column label="超损数量" width="90">
          <template #default="{row}"><span :style="{color: row.excessLossQty > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ fmt(row.excessLossQty) }}</span></template>
        </el-table-column>
        <el-table-column label="最大超损" width="90">
          <template #default="{row}">{{ fmt(row.maxExcessLossQty) }}</template>
        </el-table-column>
        <el-table-column label="物料单价" width="100">
          <template #default="{row}"><el-input v-model="row.unitPrice" size="small" type="number" @change="recalc(row)" /></template>
        </el-table-column>
        <el-table-column label="超损总价" width="100">
          <template #default="{row}"><span :style="{color: row.excessLossAmount > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ fmt(row.excessLossAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="备注" min-width="100"><template #default="{row}"><el-input v-model="row.remark" size="small" placeholder="备注" /></template></el-table-column>
      </el-table>
    </el-card>

    <!-- 交货记录 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <template #header><span style="font-weight:600">交货记录</span></template>
      <div style="display:flex;gap:20px;margin-bottom:12px;font-size:var(--app-font-sm);color:var(--app-text-regular)">
        <span>正常交货：<b style="color:var(--app-color-success)">{{ (report.deliveries || []).filter((d:any)=>d.deliveryType!==DeliveryType.DEFECT_RETURN).reduce((s:number,d:any)=>s+(d.quantity||0),0) }}</b></span>
        <span>退不良：<b style="color:var(--app-color-warning)">{{ Math.abs((report.deliveries || []).filter((d:any)=>d.deliveryType===DeliveryType.DEFECT_RETURN).reduce((s:number,d:any)=>s+(d.quantity||0),0)) }}</b></span>
        <span>实际已交：<b style="color:var(--app-color-primary)">{{ (report.deliveries || []).reduce((s:number,d:any)=>s+(d.quantity||0),0) }}</b></span>
      </div>
      <el-table :data="report.deliveries" border size="small">
        <el-table-column label="日期" width="110"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column prop="productName" label="产品" min-width="130" />
        <el-table-column label="数量" width="100" align="right"><template #default="{row}"><span :style="{color:Number(row.quantity)<0?'var(--app-color-danger)':''}">{{ row.quantity }}</span></template></el-table-column>
        <el-table-column prop="trackingNo" label="物流单号" width="150" />
        <el-table-column prop="remark" label="备注" min-width="150" />
      </el-table>
    </el-card>

    <!-- 操作 -->
    <div style="display:flex;gap:12px;align-items:center">
      <el-button type="primary" :disabled="report.reportStatus==='已结单'" @click="handleSave">保存草稿</el-button>
      <el-button type="success" :disabled="!canConfirm" @click="handleConfirm">确认结单</el-button>
      <el-button v-if="report.reportStatus==='已结单'" type="warning" @click="handleReopen">反结单</el-button>
      <el-button type="info" @click="handleExport">导出Excel</el-button>
    </div>
  </div>
</template>

<style scoped>
.close-page { padding: 16px; }
.page-header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }

</style>
