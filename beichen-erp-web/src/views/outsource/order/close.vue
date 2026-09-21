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
const materialTypes = ref<any[]>([])
const returnWarehouseId = ref<number | null>(null)
// 强制退料（2026-09-16 问题①）：委外收货领料走"允许负"口径 → 工厂仓账面常为负/无库存行；
// 此时严格口径会直接拦住结单，需显式勾选"强制退料"（按强制出库口径扣减，允许负数，流水留痕）
const forceReturn = ref(false)
const showForceDialog = ref(false)
/** 退料数量是否超过工厂委外仓账面库存（后端返回 factoryStockQty） */
function rowShortage(row: any) {
  const ret = (Number(row.goodReturnQty) || 0) + (Number(row.defectReturnQty) || 0)
  const stock = Number(row.factoryStockQty) || 0
  return ret > stock + 0.000005
}
const shortageRows = computed(() => (items.value || []).filter((r: any) => rowShortage(r)))
// 退回仓库选择：纯 Odoo 方案，RemoteSelect 实时查库
// F7-81-③（2026-09-20）：**限定为我方物料仓（INVENTORY + AUXILIARY）** —— 结单退料是一张"调拨单"
// （工厂委外仓 → 退回仓），后端已按调拨规则校验；下拉原实现不带任何过滤，会把成品仓也列出来供选，
// 选中即被后端拒绝（或（修复前）把物料记进成品仓）。现与后端口径对齐。
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { warehouseName: kw, pageSize: 500, warehouseCategory: 'INVENTORY', warehouseType: 'AUXILIARY' } })

// materialTypeId -> 类型名 映射（兜底展示用）
function typeName(id: number | undefined, fallback?: string) {
  if (id != null) { const t = materialTypes.value.find((v: any) => v.id === id); if (t) return t.typeName }
  return fallback || '-'
}
async function loadMaterialTypes() {
  // F7-129（2026-09-20）：加载失败不再静默 —— 留痕，避免"空下拉"被误认为"没有数据"
  try { const r = await request.get<any, any>('/dev/material-type/enabled'); materialTypes.value = r || [] } catch (e: any) { console.warn('加载物料类型失败', e?.message || e) }
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
  // 良率超损% = 加工良率 - 生产良率（最小0，不允许负值）
  row.yieldLoss = Math.max(0, +(targetYield - row.actualYieldRate).toFixed(2))
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

// 只有草稿可确认结单（未生成/已结单都不可）
const canConfirm = computed(() => report.reportStatus === CloseReportStatus.DRAFT)

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
  // 2026-09-16 问题①：退料超过工厂仓账面库存时，先弹窗列明细 + 勾选强制退料，
  // 而不是让用户直接吃后端"库存不足，无法出库"的报错
  if (shortageRows.value.length > 0) {
    forceReturn.value = false
    showForceDialog.value = true
    return
  }
  try {
    await ElMessageBox.confirm('确认结单？结单后将自动生成退料单，加工单状态变为"已完成"。', '确认结单', { type: 'warning' })
  } catch { return }
  await doConfirm(false)
}

/** force=true：按"强制出库"口径退料（允许把委外仓扣成负数） */
async function doConfirm(force: boolean) {
  try {
    // 先保存最终数据
    await request.put(`/outsource/order/${orderId}/close-report`, { items: items.value, remark: remark.value })
    await request.post(`/outsource/order/${orderId}/close-report/confirm`, { returnWarehouseId: returnWarehouseId.value, force })
    showForceDialog.value = false
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
/** 数量列展示：整数（2026-09-16 数量一律为整数；金额/单价/良率仍用 fmt） */
function fmtQty(v: any) { return v !== undefined && v !== null ? String(Math.round(Number(v))) : '0' }

onMounted(() => { loadMaterialTypes(); loadReport() })
</script>

<template>
  <div class="close-page" v-loading="loading">
    <!-- 表头 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="状态">
          <el-tag v-if="report.reportStatus === CloseReportStatus.FINISHED" type="success">{{ CloseReportStatusLabel[CloseReportStatus.FINISHED] }}</el-tag>
          <el-tag v-else-if="report.reportStatus === CloseReportStatus.DRAFT" type="warning">{{ CloseReportStatusLabel[CloseReportStatus.DRAFT] }}</el-tag>
          <el-tag v-else type="info">未生成</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="加工单号">{{ report.orderCode }}</el-descriptions-item>
        <el-descriptions-item label="代工厂">{{ report.factoryName }}</el-descriptions-item>
        <el-descriptions-item label="产品">
          {{ report.products?.map((p:any) => `${p.productName}×${p.quantity}`).join(' / ') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="结单日期">{{ report.closeDate || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:8px">
        <span style="font-weight:500;font-size:var(--app-font-base)">备注：</span>
        <el-input v-model="remark" placeholder="结单备注" size="small" style="width:400px;margin-left:4px" />
      </div>
    </el-card>

    <!-- 物料明细 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span style="font-weight:600">物料明细</span>
          <div style="display:flex;align-items:center;gap:8px">
            <span style="font-size:var(--app-font-base);color:var(--app-text-regular)">退回仓库：</span>
            <RemoteSelect v-model="returnWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="请选择退回仓库" size="small" style="width:200px" :disabled="report.reportStatus===CloseReportStatus.FINISHED" />
          </div>
        </div>
      </template>
      <el-table :data="items" border size="small" stripe show-summary :summary-method="() => []">
        <el-table-column label="类目" width="70"><template #default="{row}">{{ typeName(row.materialTypeId) }}</template></el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="120">
          <template #default="{row}">
            {{ row.materialName }}
            <el-tooltip v-if="rowShortage(row)" content="退料数量超过工厂委外仓账面库存，需勾选「强制退料」才能结单" placement="top">
              <el-tag type="danger" size="small" effect="plain" style="margin-left:4px">账面不足</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="工厂仓账面" width="110" align="right">
          <template #default="{row}">
            <span :style="{color: Number(row.factoryStockQty) < 0 ? 'var(--app-color-danger)' : 'var(--app-text-regular)'}">{{ fmtQty(row.factoryStockQty) }}</span>
          </template>
        </el-table-column>


        <el-table-column label="用料总数" width="90"><template #default="{row}">{{ fmtQty(row.usedTotalQuantity) }}</template></el-table-column>
        <el-table-column label="退料总计" width="90" align="right"><template #default="{row}">{{ fmtQty((+row.goodReturnQty||0) + (+row.defectReturnQty||0)) }}</template></el-table-column>
        <el-table-column label="出货消耗" width="90"><template #default="{row}">{{ fmtQty(row.shippedQuantity) }}</template></el-table-column>
        <el-table-column label="良品退料" width="100">
          <template #default="{row}"><el-input-number v-model="row.goodReturnQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" @change="onGoodChange(row)" /></template>
        </el-table-column>
        <el-table-column label="不良退料" width="100">
          <template #default="{row}"><el-input-number v-model="row.defectReturnQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" @change="onDefectChange(row)" /></template>
        </el-table-column>
        <el-table-column label="留存工厂" width="90">
          <template #default="{row}"><el-input-number v-model="row.factoryRetainQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" @change="onRetainChange(row)" /></template>
        </el-table-column>
        <el-table-column label="缺失" width="90">
          <template #default="{row}"><el-input-number v-model="row.missingQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" @change="recalc(row)" /></template>
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
          <template #default="{row}"><span :style="{color: row.excessLossQty > 0 ? 'var(--app-color-danger)' : 'var(--app-color-success)'}">{{ fmtQty(row.excessLossQty) }}</span></template>
        </el-table-column>
        <el-table-column label="最大超损" width="90">
          <template #default="{row}">{{ fmtQty(row.maxExcessLossQty) }}</template>
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

    <!-- 收货记录 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <template #header><span style="font-weight:600">收货记录</span></template>
      <div style="display:flex;gap:20px;margin-bottom:12px;font-size:var(--app-font-base);color:var(--app-text-regular)">
        <span>正常收货：<b style="color:var(--app-color-success)">{{ (report.deliveries || []).filter((d:any)=>d.deliveryType!==DeliveryType.DEFECT_RETURN).reduce((s:number,d:any)=>s+(d.quantity||0),0) }}</b></span>
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
      <el-button type="primary" :disabled="report.reportStatus===CloseReportStatus.FINISHED" @click="handleSave">保存草稿</el-button>
      <el-button type="success" :disabled="!canConfirm" @click="handleConfirm">确认结单</el-button>
      <el-button v-if="report.reportStatus===CloseReportStatus.FINISHED" type="warning" @click="handleReopen">反结单</el-button>
      <el-button type="info" @click="handleExport">导出Excel</el-button>
    </div>

    <!-- 强制退料确认（2026-09-16 问题①）：账面库存不足时的显式旁路 -->
    <el-dialog v-model="showForceDialog" title="存在退料超出工厂仓账面库存的物料" width="640px">
      <el-alert type="warning" :closable="false" show-icon
        title="以下物料的退料数量超过工厂委外仓账面库存"
        description="委外收货领料走「允许负」口径，工厂仓账面常为负或没有库存行，此时严格口径会阻止结单。如需按实际情况退料，请勾选下方「强制退料」；否则请先补入库单或下调退料数量。" />
      <el-table :data="shortageRows" border size="small" max-height="240" style="margin-top:12px">
        <el-table-column prop="materialName" label="物料" min-width="130" />
        <el-table-column label="工厂仓账面" width="110" align="right">
          <template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmtQty(row.factoryStockQty) }}</span></template>
        </el-table-column>
        <el-table-column label="拟退料" width="100" align="right">
          <template #default="{row}">{{ fmtQty((+row.goodReturnQty||0) + (+row.defectReturnQty||0)) }}</template>
        </el-table-column>
        <el-table-column label="缺口" width="100" align="right">
          <template #default="{row}">{{ fmtQty(Math.max(0, (+row.goodReturnQty||0) + (+row.defectReturnQty||0) - (+row.factoryStockQty||0))) }}</template>
        </el-table-column>
      </el-table>
      <el-checkbox v-model="forceReturn" style="margin-top:12px">
        <span style="color:var(--app-color-danger)">强制退料</span>（按强制出库口径扣减，允许把工厂委外仓扣成负数，库存流水会留痕）
      </el-checkbox>
      <template #footer>
        <el-button @click="showForceDialog=false">取消</el-button>
        <el-button type="danger" :disabled="!forceReturn" @click="doConfirm(true)">确认结单（强制退料）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.close-page { padding: 16px; }
.page-header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }

</style>
