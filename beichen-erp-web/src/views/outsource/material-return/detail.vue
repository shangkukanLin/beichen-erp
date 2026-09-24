<script setup lang="ts">
import { ref, computed, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageShell from '@/components/PageShell.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { localDate } from '@/utils/date'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel, MaterialReturnTypeTag, MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag } from '@/api/enums'

const route = useRoute()
const router = useRouter()
const id = route.params.id as string
const detail = ref<any>({})
const loading = ref(false)

/** 维修退货（2026-09-17）：审核=送修出库（不冲应付）；供应商修好后「登记维修返回」把物料入回来 */
const isRepair = computed(() => (detail.value.returnType || MaterialReturnType.REFUND) === MaterialReturnType.REPAIR)

// ===== 维修返回（对齐加工退货的维修返回实现）=====
const warehouseOptions = ref<any[]>([])
const repairVisible = ref(false)
const repairSaving = ref(false)
const repairWarehouseId = ref<number>()
const repairDate = ref(localDate())
const repairRows = ref<any[]>([])

async function loadWarehouseOptions() {
  try { const r = await request.get<any, any>('/outsource/material-return/warehouse-options'); warehouseOptions.value = r || [] } catch { warehouseOptions.value = [] }
}

/** 打开「登记维修返回」：按送修**物料**生成行，数量默认 = 送修 − 已返回（物料库存只有良品一档） */
function openRepairReturn() {
  const returned: Record<string, number> = {}
  for (const r of (detail.value.repairReturns || []) as any[]) {
    const k = String(r.materialId)
    returned[k] = (returned[k] || 0) + (Number(r.quantity) || 0)
  }
  const sent: Record<string, any> = {}
  for (const it of (detail.value.items || []) as any[]) {
    const k = String(it.materialId)
    if (!sent[k]) sent[k] = { materialId: it.materialId, materialName: it.materialName, unit: it.unit, sentQty: 0, returnedQty: 0, quantity: undefined }
    sent[k].sentQty += Number(it.quantity) || 0
  }
  repairRows.value = Object.entries(sent).map(([k, v]: any) => {
    const done = returned[k] || 0
    return { ...v, returnedQty: done, quantity: Math.max(0, v.sentQty - done) }
  }).filter((r: any) => r.sentQty - r.returnedQty > 0)
  if (repairRows.value.length === 0) { ElMessage.warning('该单已全部返回，无需再登记'); return }
  // 默认入库仓 = 该单出库源仓，可改（物料可能在委外仓或自有物料仓，故不限仓型）
  repairWarehouseId.value = detail.value.fromWarehouseId || undefined
  repairDate.value = localDate()
  repairVisible.value = true
}

async function submitRepairReturn() {
  if (!repairWarehouseId.value) { ElMessage.warning('请选择返回入库仓'); return }
  const items = repairRows.value.filter((r: any) => Number(r.quantity) > 0)
    .map((r: any) => ({ materialId: r.materialId, unit: r.unit, quantity: Number(r.quantity) }))
  if (items.length === 0) { ElMessage.warning('请填写维修返回数量'); return }
  repairSaving.value = true
  try {
    await request.post(`/outsource/material-return/${id}/repair-return`, { warehouseId: repairWarehouseId.value, repairDate: repairDate.value, items })
    ElMessage.success('维修返回已登记（物料已入库）')
    repairVisible.value = false
    await loadData()
    sessionStorage.setItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, '1')
  } catch (e: any) { ElMessage.error(e?.message || '登记失败') } finally { repairSaving.value = false }
}

async function cancelRepairReturn(row: any) {
  try { await ElMessageBox.confirm(`确认撤销该条维修返回（${row.materialName || ''} × ${row.quantity}）？撤销后物料库存将扣回。`, '撤销维修返回', { type: 'warning' }) } catch { return }
  try {
    await request.delete(`/outsource/material-return/repair-return/${row.id}`)
    ElMessage.success('已撤销')
    await loadData()
    sessionStorage.setItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, '1')
  } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

async function loadData() {
  loading.value = true
  try { detail.value = (await request.get<any, any>(`/outsource/material-return/${id}`)) || {} } finally { loading.value = false }
}

async function handleAudit() {
  const tip = isRepair.value
    ? ('确认审核该维修退货单？审核后物料出源仓送供应商维修（不冲减应付）' + (detail.value.materialOrderCode ? `；关联订单 ${detail.value.materialOrderCode} 若未完成，将同时扣减其收料数` : ''))
    : '确认审核该退货单？审核后物料出源仓并冲减应付'
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/audit`); ElMessage.success('已审核'); loadData(); sessionStorage.setItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit() {
  const tip = isRepair.value
    ? '确认反审核？将送修物料回源仓（若有维修返回记录需先撤销；关联订单已扣减的收料数会一并回滚）'
    : '确认反审核？将物料回源仓并冲销应付'
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/un-audit`); ElMessage.success('已反审核'); loadData(); sessionStorage.setItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/** 结案（仅维修退货）：未返回=0 后收尾；场景②（订单已完成）/③（未关联）的跟踪终点 */
async function handleClose() {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/撤销维修返回，也不能反审核（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/close`); ElMessage.success('已结案'); loadData(); sessionStorage.setItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen() {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/re-open`); ElMessage.success('已撤销结案'); loadData(); sessionStorage.setItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/cancel`); ElMessage.success('已作废'); loadData(); sessionStorage.setItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

// 业务数据放在 onActivated 加载：layout 用 keep-alive 缓存页面，再次进入详情页会复用组件、
// onMounted 不再触发，只靠 onMounted 会停留在上次缓存的状态
onActivated(() => { loadData(); loadWarehouseOptions() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/outsource/material-return">
    <template #actions>
      <el-button type="success" v-if="detail.status===DocStatus.DRAFT" @click="handleAudit">审核</el-button>
      <el-button type="warning" v-if="detail.status===DocStatus.AUDITED && detail.closedFlag!==1" @click="handleUnAudit">反审核</el-button>
      <el-button type="danger" v-if="detail.status===DocStatus.DRAFT" @click="handleCancel">作废</el-button>
      <!-- 结案 / 撤销结案（仅维修退货，2026-09-17）：未返回=0 才可结案 -->
      <el-button type="success" v-if="isRepair && detail.status===DocStatus.AUDITED && detail.closedFlag!==1 && Number(detail.unreturnedQty)===0" @click="handleClose">结案</el-button>
      <el-button type="warning" v-if="isRepair && detail.closedFlag===1" @click="handleReOpen">撤销结案</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <span style="font-weight:600">委外物料退货详情</span>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code }}</el-descriptions-item>
        <!-- 两类型（2026-09-17）：退货退款 = 冲减应付；维修退货 = 送修，修好登记维修返回入库 -->
        <el-descriptions-item label="类型">
          <el-tag :type="MaterialReturnTypeTag[detail.returnType] || 'info'" size="small">{{ MaterialReturnTypeLabel[detail.returnType] || detail.returnType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="isRepair ? '维修供应商' : '退回对象'">{{ detail.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出库源仓">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <!-- 来源收料单：从「物料收货」按记录发起退货时才有（2026-09-17） -->
        <el-descriptions-item label="来源收料单">{{ detail.sourceDeliveryCode || (detail.sourceDeliveryId ? ('#' + detail.sourceDeliveryId) : '-') }}</el-descriptions-item>
        <!-- 关联物料订单（2026-09-17 维修退货闭环）：订单未完成=已扣减其收料数（修好回补）；已完成/未关联=靠本单跟踪 -->
        <el-descriptions-item v-if="isRepair" label="关联物料订单">
          <template v-if="detail.materialOrderId">
            <el-button type="primary" link @click="router.push(`/outsource/material-order/detail/${detail.materialOrderId}`)">{{ detail.materialOrderCode || ('#' + detail.materialOrderId) }}</el-button>
            <el-tag :type="MaterialOrderStatusTag[detail.materialOrderStatus] || 'info'" size="small" style="margin-left:6px">{{ MaterialOrderStatusLabel[detail.materialOrderStatus] || '-' }}</el-tag>
            <span v-if="detail.deductedFlag===1" style="margin-left:6px;color:var(--app-color-success);font-size:var(--app-font-xs)">已扣减该单收料数（修好返回自动回补）</span>
            <span v-else style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">订单已完成，未扣减收料数</span>
          </template>
          <span v-else style="color:var(--app-text-placeholder)">未关联（返回情况靠本单跟踪）</span>
        </el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ $fmtDate(detail.returnDate) }}</el-descriptions-item>
        <!-- 送修 / 已返回 / 未返回（仅维修退货）：场景②③"是否有返回来"的跟踪口径 -->
        <el-descriptions-item v-if="isRepair" label="送修 / 已返回">
          {{ Number(detail.sentQty || 0) }} /
          <span style="color:var(--app-color-success);font-weight:600">{{ Number(detail.repairReturnedQty || 0) }}</span>
          <span v-if="Number(detail.unreturnedQty) > 0" style="margin-left:8px;color:var(--app-color-warning)">未返回 {{ detail.unreturnedQty }}</span>
          <span v-else style="margin-left:8px;color:var(--app-color-success)">已全部返回</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item v-if="isRepair && detail.closedFlag===1" label="结案">
          已结案<el-tag type="success" size="small" style="margin-left:6px">{{ detail.closedBy || '' }}</el-tag>
          <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ $fmtDate(detail.closedTime) }}</span>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">{{ isRepair ? '送修物料明细' : '退货物料明细' }}</span></template>
      <el-table :data="detail.items || []" border size="small">
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="materialTypeName" label="物料类型" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" :label="isRepair ? '送修数量' : '数量'" width="100" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right" />
        <el-table-column prop="amount" label="金额" width="110" align="right" />
      </el-table>
    </el-card>

    <!-- 维修返回记录（仅维修退货单，2026-09-17）：登记即入库，可逐行撤销 -->
    <el-card shadow="never" style="margin-top:12px" v-if="isRepair">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">维修返回记录
            <span v-if="detail.closedFlag===1" style="margin-left:6px;font-size:var(--app-font-xs);color:var(--app-color-success)">（已结案）</span>
          </span>
          <el-button type="primary" size="small" v-if="detail.status===DocStatus.AUDITED && detail.closedFlag!==1" @click="openRepairReturn">登记维修返回</el-button>
        </div>
      </template>
      <el-table :data="detail.repairReturns || []" border size="small">
        <el-table-column label="返回日期" width="110"><template #default="{row}">{{ $fmtDate(row.repairDate) }}</template></el-table-column>
        <el-table-column label="入库仓库" width="150"><template #default="{row}">{{ row.warehouseName || '-' }}</template></el-table-column>
        <el-table-column label="物料名称" min-width="160"><template #default="{row}">{{ row.materialName || ('#' + row.materialId) }}</template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="{row}">{{ row.unit || '-' }}</template></el-table-column>
        <el-table-column label="返回数量" width="110" align="right"><template #default="{row}"><span style="color:var(--app-color-success);font-weight:500">{{ row.quantity }}</span></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{row}"><el-button type="danger" link size="small" v-if="detail.closedFlag!==1" @click="cancelRepairReturn(row)">撤销</el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="!(detail.repairReturns || []).length" style="color:var(--app-text-secondary);font-size:var(--app-font-xs);padding:8px 0">
        {{ detail.status!==DocStatus.AUDITED ? '审核（送修）后即可登记维修返回。' : (detail.closedFlag===1 ? '已结案（无维修返回记录）。' : '尚未登记维修返回（供应商修好送回后再登记，登记即入库）。') }}
      </div>
      <div v-else-if="Number(detail.unreturnedQty) > 0" style="color:var(--app-color-warning);font-size:var(--app-font-xs);padding:8px 0">
        还有 {{ detail.unreturnedQty }} 件未返回（供应商尚未修好送回）；全部返回后可结案。
      </div>
    </el-card>

    <!-- 登记维修返回弹窗 -->
    <el-dialog v-model="repairVisible" title="登记维修返回" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <el-form label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item required label="入库仓库">
              <el-select v-model="repairWarehouseId" filterable clearable style="width:100%" placeholder="默认该单出库源仓，可改">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="返回日期"><el-input v-model="repairDate" type="date" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <el-table :data="repairRows" border size="small">
        <el-table-column label="物料名称" min-width="150"><template #default="{row}">{{ row.materialName }}</template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="{row}">{{ row.unit || '-' }}</template></el-table-column>
        <el-table-column label="送修 / 已返回" width="130" align="center"><template #default="{row}">{{ row.sentQty }} / {{ row.returnedQty }}</template></el-table-column>
        <el-table-column label="本次返回" width="140">
          <template #default="{row}">
            <el-input-number v-model="row.quantity" size="small" :min="0" :max="row.sentQty - row.returnedQty" :controls="false" :precision="0" :step="1" style="width:100%" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="repairVisible = false">取消</el-button>
        <el-button type="primary" :loading="repairSaving" @click="submitRepairReturn">确认登记（物料入库）</el-button>
      </template>
    </el-dialog>
  </PageShell>
</template>
