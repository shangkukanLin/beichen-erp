<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const route = useRoute(); const router = useRouter()
const supplierId = Number(route.params.id)
const loading = ref(false)
const data = ref<any>({})
const returnVisible = ref(false)
const returnWarehouseId = ref<number>()

async function loadAll() {
  loading.value = true
  try { data.value = await request.get<any, any>(`/supplier-settlement/${supplierId}`) || {} }
  catch { data.value = {} } finally { loading.value = false }
}

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

// ========== 一键退料 ==========
const invWarehouses = ref<any[]>([])
const returnSaving = ref(false)
async function openReturn() {
  returnWarehouseId.value = undefined
  try { const r = await request.get<any, any>('/inventory/warehouse/page', { params: { pageSize: 200 } }); invWarehouses.value = r?.records || [] } catch { invWarehouses.value = [] }
  returnVisible.value = true
}
async function handleReturn() {
  if (!returnWarehouseId.value) { ElMessage.warning('请选择退回目标仓'); return }
  returnSaving.value = true
  try {
    await request.post(`/supplier-settlement/${supplierId}/return-materials`, { toWarehouseId: returnWarehouseId.value })
    ElMessage.success('退料完成，已全部退回我方仓')
    returnVisible.value = false; loadAll()
  } catch (e: any) { ElMessage.error(e?.message || '退料失败') } finally { returnSaving.value = false }
}

// ========== 清算完成 ==========
const finishing = ref(false)
async function handleFinish() {
  try {
    await ElMessageBox.confirm(
      `确认与「${data.value.supplier?.name}」结束合作？清算后供应商将停用，历史数据保留。`,
      '确认清算', { type: 'warning', confirmButtonText: '确认清算并停用', cancelButtonText: '再想想' })
  } catch { return }
  finishing.value = true
  try {
    await request.post(`/supplier-settlement/${supplierId}/finish`)
    ElMessage.success('清算完成，供应商已停用')
    router.push('/finance/payment')
  } catch (e: any) { ElMessage.error(e?.message || '清算失败') } finally { finishing.value = false }
}

const unpaidOk = ref(false)
const ordersOk = ref(false)
const stocksOk = ref(false)
function refreshChecks() {
  unpaidOk.value = Number(data.value.unpaidTotal || 0) === 0
  ordersOk.value = (data.value.activeOrders || []).length === 0 && (data.value.activeMaterialOrders || []).length === 0
  stocksOk.value = (data.value.stocks || []).length === 0
}

onMounted(async () => { await loadAll(); refreshChecks() })
</script>

<template>
  <div class="p" v-loading="loading">
    <div class="page-header">
      <span class="page-title">清算看板 - {{ data.supplier?.name || '' }}</span>
      <el-tag v-if="data.canSettle" type="success">满足清算条件</el-tag>
      <el-tag v-else type="warning">存在待处理事项</el-tag>
    </div>

    <!-- ① 财务结算 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span style="font-weight:600">① 财务结算</span>
          <span>未付合计：<b style="color:#f56c6c">¥ {{ fmt(data.unpaidTotal) }}</b>
            <el-button v-if="Number(data.unpaidTotal)>0" type="primary" size="small" style="margin-left:12px" @click="router.push(`/finance/payment/supplier/${supplierId}`)">去付款</el-button>
          </span>
        </div>
      </template>
      <el-table :data="data.payables || []" border stripe size="small" max-height="260">
        <el-table-column prop="billNo" label="应付单号" width="150" />
        <el-table-column prop="sourceBillType" label="来源" width="110" />
        <el-table-column prop="sourceBillNo" label="来源单号" width="150" show-overflow-tooltip />
        <el-table-column label="金额" width="100" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="未付" width="100" align="right"><template #default="{row}"><span style="color:#f56c6c">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="到期日" width="100" align="center"><template #default="{row}">{{ $fmtDate(row.dueDate) }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center" />
      </el-table>
      <el-empty v-if="(data.payables||[]).length===0" description="应付已结清" :image-size="50" />
    </el-card>

    <!-- ② 未完成订单 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">② 未完成订单（需完成或取消）</span></template>
      <template v-if="(data.activeOrders||[]).length > 0">
        <div class="section-title">委外加工单</div>
        <el-table :data="data.activeOrders" border stripe size="small">
          <el-table-column prop="code" label="单号" width="170" />
          <el-table-column prop="status" label="状态" width="90" align="center" />
          <el-table-column label="计划完成" width="110" align="center"><template #default="{row}">{{ $fmtDate(row.planEndDate) }}</template></el-table-column>
          <el-table-column label="总金额" width="110" align="right"><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
          <el-table-column label="操作" width="90" align="center"><template #default="{row}"><el-button type="primary" link size="small" @click="router.push(`/outsource/order/detail/${row.id}`)">去处理</el-button></template></el-table-column>
        </el-table>
      </template>
      <template v-if="(data.activeMaterialOrders||[]).length > 0">
        <div class="section-title" style="margin-top:12px">物料订单</div>
        <el-table :data="data.activeMaterialOrders" border stripe size="small">
          <el-table-column prop="code" label="单号" width="170" />
          <el-table-column label="类型" width="80" align="center"><template #default="{row}">{{ row.orderType || '采购' }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="90" align="center" />
          <el-table-column label="交期" width="110" align="center"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
          <el-table-column label="操作" width="90" align="center"><template #default="{row}"><el-button type="primary" link size="small" @click="router.push(`/outsource/material-order/detail/${row.id}`)">去处理</el-button></template></el-table-column>
        </el-table>
      </template>
      <el-empty v-if="(data.activeOrders||[]).length===0 && (data.activeMaterialOrders||[]).length===0" description="无未完成订单" :image-size="50" />
    </el-card>

    <!-- ③ 委外仓物料 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span style="font-weight:600">③ 委外仓物料（我方还在他厂里的料）</span>
          <el-button v-if="(data.stocks||[]).length>0" type="warning" size="small" @click="openReturn">一键退料</el-button>
        </div>
      </template>
      <el-table :data="data.stocks || []" border stripe size="small" max-height="260">
        <el-table-column prop="warehouseName" label="委外仓" width="150" />
        <el-table-column prop="materialType" label="类型" width="90" />
        <el-table-column prop="materialName" label="物料" min-width="140" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="qualityType" label="质量" width="80" align="center" />
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
      </el-table>
      <el-empty v-if="(data.stocks||[]).length===0" description="委外仓无物料" :image-size="50" />
    </el-card>

    <!-- ④ 清算校验 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">④ 清算校验</span></template>
      <div class="check-list">
        <div class="check-item"><el-tag :type="Number(data.unpaidTotal)===0?'success':'danger'" size="small">{{ Number(data.unpaidTotal)===0 ? '✓' : '✗' }}</el-tag> 应付已结清（未付 ¥{{ fmt(data.unpaidTotal) }}）</div>
        <div class="check-item"><el-tag :type="(data.activeOrders||[]).length===0 && (data.activeMaterialOrders||[]).length===0 ?'success':'danger'" size="small">{{ (data.activeOrders||[]).length===0 && (data.activeMaterialOrders||[]).length===0 ? '✓' : '✗' }}</el-tag> 无进行中订单（加工单 {{ (data.activeOrders||[]).length }}，物料单 {{ (data.activeMaterialOrders||[]).length }}）</div>
        <div class="check-item"><el-tag :type="(data.stocks||[]).length===0?'success':'danger'" size="small">{{ (data.stocks||[]).length===0 ? '✓' : '✗' }}</el-tag> 委外仓物料已清零（{{ (data.stocks||[]).length }} 项）</div>
      </div>
      <div style="margin-top:16px;text-align:center">
        <el-button type="danger" size="large" :disabled="!data.canSettle" :loading="finishing" @click="handleFinish">确认清算并停用供应商</el-button>
        <div v-if="!data.canSettle" style="color:#909399;font-size:12px;margin-top:8px">请先处理以上待办事项，全部满足后才能清算</div>
      </div>
    </el-card>

    <!-- 一键退料弹窗 -->
    <el-dialog v-model="returnVisible" title="一键退料" width="480px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" style="margin-bottom:12px">
        将该供应商所有委外仓的物料（{{ (data.stocks||[]).length }} 项）全部退回我方仓库，并生成退料单。
      </el-alert>
      <el-form label-width="90px">
        <el-form-item label="退回目标仓" required>
          <el-select v-model="returnWarehouseId" filterable placeholder="选择我方仓库" style="width:100%">
            <el-option v-for="w in invWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="returnVisible=false">取消</el-button><el-button type="warning" :loading="returnSaving" @click="handleReturn">确认退料</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.p{display:flex;flex-direction:column;gap:12px}
.page-header{display:flex;align-items:center;gap:12px;padding-bottom:4px}
.page-title{font-size:18px;font-weight:600}
.card-header{display:flex;align-items:center;justify-content:space-between}
.section-title{font-weight:600;font-size:13px;color:#606266;margin-bottom:6px}
.check-list{display:flex;flex-direction:column;gap:10px;padding:4px 8px}
.check-item{display:flex;align-items:center;gap:8px;font-size:14px}
</style>
