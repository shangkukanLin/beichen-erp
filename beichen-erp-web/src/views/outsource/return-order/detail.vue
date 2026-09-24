<script setup lang="ts">
import { ref, reactive, computed, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { localDate } from '@/utils/date'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_RETURN_ORDER_DIRTY_KEY, OutsourceChargeType, OutsourceChargeTypeLabel, OutsourceReturnType, OutsourceReturnTypeLabel, OutsourceReturnTypeTag, ProductQualityType, ProductQualityTypeLabel } from '@/api/enums'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'

/**
 * 委外加工退货详情（2026-09-24 用户口径：草稿态就地可编辑，列表不再给「编辑」）
 *
 * ⚠️ 只对**维修退货（REPAIR）**开放就地编辑，原因有二（都不是偷懒）：
 *  ① 列表里唯一带「编辑」的是 REPAIR 页签那张退货单表（DEFECT 页签是台账，本来就没有编辑入口）⇒ 口径刚好对齐；
 *  ② 加工退货（DEFECT）的「退货物料明细」是**按 BOM 快照联动派生**的（产品 × BOM 用量），编辑它必须把
 *     add.vue 里"选产品 → 取快照 → 带料"整套联动搬进详情页，会把同一套规则分叉成两处（这是最容易漂移的地方）
 *     ⇒ DEFECT 保持只读（与改造前完全一致，无行为回退）。
 * 草稿分支的字段、校验、payload 与 add.vue 的 REPAIR 路径**完全一致**：
 *  工厂(RWORK 返工费必填)、送修出库仓(我方成品仓)、送修日期、备注、收费(类型+金额>0+说明)、送修产品(数量/规格)；
 *  payload `{returnType, orderId:null, sourceDeliveryId:null, factoryId, warehouseId, returnDate, remark,
 *  chargeFlag:1, chargeType, chargeAmount, chargeReason, items:[], products[]}`（维修退货不还料 ⇒ items 恒为空）。
 */
const route = useRoute()
const router = useRouter()
const id = route.params.id as string
const detail = ref<any>({})
const loading = ref(false)
const saving = ref(false)

/** 维修退货：不还料、必须收费；已审核（已送修）后可登记「维修返回」把修好的货入回来 */
const isRepair = computed(() => (showDraftForm.value ? form.returnType : (detail.value.returnType || OutsourceReturnType.DEFECT)) === OutsourceReturnType.REPAIR)
/** 草稿 + 维修退货 才渲染可编辑表单（DEFECT 草稿保持只读，见文件头注释②） */
const showDraftForm = computed(() => detail.value.status === DocStatus.DRAFT && (detail.value.returnType || OutsourceReturnType.DEFECT) === OutsourceReturnType.REPAIR)

// ===== 草稿态可编辑副本（白名单：单号/状态/来源收货记录/制单人 不回传） =====
const form = reactive({
  returnType: OutsourceReturnType.REPAIR as string,
  factoryId: undefined as any,
  warehouseId: undefined as any,
  returnDate: localDate(),
  remark: '',
  // 工厂收费：**加工厂向我方收取**（我方付加工厂），维修退货必填（默认返工费）
  chargeType: OutsourceChargeType.REWORK as string,
  chargeAmount: 0 as number,
  chargeReason: ''
})
/** 送修产品行（可改数量与规格；数量改 0 = 本次不再送修该产品） */
const editableProducts = ref<any[]>([])

const chargeTypeOptions = computed(() =>
  Object.values(OutsourceChargeType).map((v) => ({ value: v, label: OutsourceChargeTypeLabel[v] || v }))
)
const qualityOptions = computed(() => [ProductQualityType.A, ProductQualityType.B, ProductQualityType.C, ProductQualityType.DEFECT]
  .map(v => ({ value: v, label: ProductQualityTypeLabel[v] || v })))

// ===== 纯 Odoo 方案：本地轻量列表 + RemoteSelect 实时查库 =====
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { supplierType: 'factory', name: kw, pageSize: 500 } })
// 成品出库仓只取我方成品仓：自有仓库(INVENTORY) + 类型=成品仓，排除委外仓/不良仓/售后仓等
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED', pageSize: 500 } })

async function loadData() {
  loading.value = true
  try { detail.value = (await request.get<any, any>(`/outsource/return-order/${id}`)) || {} } finally { loading.value = false }
  if (showDraftForm.value) resetForm()
}

/** 草稿（维修退货）：用本单自己的产品行填充可编辑副本 —— **不重建行**，避免丢行 */
function resetForm() {
  const d = detail.value
  form.returnType = d.returnType || OutsourceReturnType.REPAIR
  form.factoryId = d.factoryId ?? undefined
  form.warehouseId = d.warehouseId ?? undefined
  form.returnDate = d.returnDate ? String(d.returnDate).slice(0, 10) : localDate()
  form.remark = d.remark || ''
  form.chargeType = d.chargeType || OutsourceChargeType.REWORK
  form.chargeAmount = Number(d.chargeAmount || 0)
  form.chargeReason = d.chargeReason || ''
  editableProducts.value = (d.products || []).map((p: any) => ({
    ...p,
    returnQuantity: Number(p.quantity || 0),
    qualityType: p.qualityType || ProductQualityType.DEFECT
  }))
}

/** 保存（与 add.vue 的 REPAIR 路径同一套校验与 payload；后端 PUT 自带「只有草稿可编辑」守卫） */
async function doSave() {
  if (!form.factoryId) { ElMessage.warning('请选择加工厂'); return }
  if (!form.warehouseId) { ElMessage.warning('请选择送修出库仓'); return }
  // 维修退货必须收费（不良是工厂的问题之外的返工场景 ⇒ 加工厂向我方收取返工费）
  if (!form.chargeType) { ElMessage.warning('请选择收费类型（如返工费）'); return }
  if (!(Number(form.chargeAmount) > 0)) { ElMessage.warning('收费金额必须大于 0'); return }
  const keep = editableProducts.value.filter((p: any) => Number(p.returnQuantity) > 0)
  if (keep.length === 0) { ElMessage.warning('请选择产品并填写送修数量'); return }
  const dropped = editableProducts.value.length - keep.length
  if (dropped > 0) {
    try {
      await ElMessageBox.confirm(`有 ${dropped} 行送修数量为 0，保存后这些产品将不再送修，确认保存？`, '提示', { type: 'warning' })
    } catch { return }
  }
  saving.value = true
  try {
    await request.put(`/outsource/return-order/${id}`, {
      returnType: form.returnType,
      // 维修退货**不关联**加工单 / 来源收货记录（后端同口径拦截）
      orderId: null,
      sourceDeliveryId: null,
      factoryId: form.factoryId, warehouseId: form.warehouseId,
      returnDate: form.returnDate, remark: form.remark,
      chargeFlag: 1,
      chargeType: form.chargeType,
      chargeAmount: Number(form.chargeAmount),
      chargeReason: form.chargeReason || '',
      // 维修退货不还料：物料明细恒为空
      items: [],
      products: keep.map((p: any) => ({
        productName: p.productName,
        productMasterId: p.productMasterId || null,
        bomSnapshotId: p.bomSnapshotId || null,
        qualityType: p.qualityType || ProductQualityType.DEFECT,
        quantity: Number(p.returnQuantity)
      }))
    })
    ElMessage.success('已保存')
    sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1')
    await loadData()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

// ===== 维修返回（2026-09-17）=====
const fetchWarehousesForRepair = (kw: string) =>
  request.get('/warehouse/page', { params: { warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED', pageSize: 500 } })
const repairVisible = ref(false)
const repairSaving = ref(false)
const repairWarehouseId = ref<number>()
const repairDate = ref(localDate())
const repairRows = ref<any[]>([])

/**
 * 打开「登记维修返回」：按送修**产品**生成行，数量默认 = 送修 − 已返回。
 * <p>数量按产品核销（不按规格）：送修的是不良品，修好回来通常是 A 规等良品，
 * 故默认"返回品质 = A规"，可改（工厂修不好时仍填不良品）。</p>
 */
function openRepairReturn() {
  const returned: Record<string, number> = {}
  for (const r of (detail.value.repairReturns || []) as any[]) {
    const k = String(r.productId)
    returned[k] = (returned[k] || 0) + (Number(r.quantity) || 0)
  }
  const sent: Record<string, any> = {}
  for (const p of (detail.value.products || []) as any[]) {
    const k = String(p.productId)
    if (!sent[k]) sent[k] = { productId: p.productId, productName: p.productName, qualityType: ProductQualityType.A, sentQty: 0, returnedQty: 0, quantity: undefined }
    sent[k].sentQty += Number(p.quantity) || 0
  }
  repairRows.value = Object.entries(sent).map(([k, v]: any) => {
    const done = returned[k] || 0
    return { ...v, returnedQty: done, quantity: Math.max(0, v.sentQty - done) }
  }).filter((r: any) => r.sentQty - r.returnedQty > 0)
  if (repairRows.value.length === 0) { ElMessage.warning('该单已全部返回，无需再登记'); return }
  repairWarehouseId.value = detail.value.warehouseId || undefined
  repairDate.value = localDate()
  repairVisible.value = true
}

async function submitRepairReturn() {
  if (!repairWarehouseId.value) { ElMessage.warning('请选择返回入库仓'); return }
  const items = repairRows.value.filter((r: any) => Number(r.quantity) > 0)
    .map((r: any) => ({ productId: r.productId, productName: r.productName, qualityType: r.qualityType, quantity: Number(r.quantity) }))
  if (items.length === 0) { ElMessage.warning('请填写维修返回数量'); return }
  repairSaving.value = true
  try {
    await request.post(`/outsource/return-order/${id}/repair-return`, { warehouseId: repairWarehouseId.value, repairDate: repairDate.value, items })
    ElMessage.success('维修返回已登记（成品已入库）')
    repairVisible.value = false
    await loadData()
    sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1')
  } catch (e: any) { ElMessage.error(e?.message || '登记失败') } finally { repairSaving.value = false }
}

async function cancelRepairReturn(row: any) {
  try { await ElMessageBox.confirm(`确认撤销该条维修返回（${row.productName || ''} × ${row.quantity}）？撤销后成品库存将扣回。`, '撤销维修返回', { type: 'warning' }) } catch { return }
  try {
    await request.delete(`/outsource/return-order/repair-return/${row.id}`)
    ElMessage.success('已撤销')
    await loadData()
    sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1')
  } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

async function handleAudit() {
  const tip = isRepair.value
    ? '确认审核该维修退货单？审核后成品送修出库（不冲减应付）并生成加工厂向我方收取的维修费应付'
    : '确认审核该退货单？审核后物料入工厂仓、成品出库并冲减应付'
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/audit`); ElMessage.success('已审核'); loadData(); sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit() {
  const tip = isRepair.value
    ? '确认反审核？将送修成品回我方仓并冲销维修费应付（若有维修返回记录需先撤销；已结案需先撤销结案）'
    : '确认反审核？将逆向库存并冲销应付'
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/un-audit`); ElMessage.success('已反审核'); loadData(); sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/** 结案（仅维修退货）：工厂把送修成品全部送回（未返回=0）后收尾 */
async function handleClose() {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/撤销维修返回，也不能反审核（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/close`); ElMessage.success('已结案'); loadData(); sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen() {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/re-open`); ElMessage.success('已撤销结案'); loadData(); sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${id}/cancel`); ElMessage.success('已作废'); loadData(); sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1') } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

// 业务数据放在 onActivated 加载：layout 用 keep-alive 缓存页面，再次进入详情页会复用组件、
// onMounted 不再触发，只靠 onMounted 会停留在上次缓存的状态
onActivated(loadData)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/outsource/return-order">
    <template #actions>
      <!-- 草稿（维修退货）：保存(主) + 审核 + 作废（2026-09-24 用户口径：草稿态就地编辑，不再跳独立编辑页） -->
      <el-button type="primary" v-if="showDraftForm" :loading="saving" @click="doSave">保存</el-button>
      <el-button type="success" v-if="detail.status===DocStatus.DRAFT" @click="handleAudit">审核</el-button>
      <el-button type="warning" v-if="detail.status===DocStatus.AUDITED && detail.closedFlag!==1" @click="handleUnAudit">反审核</el-button>
      <el-button type="danger" v-if="detail.status===DocStatus.DRAFT" @click="handleCancel">作废</el-button>
      <!-- 维修返回：维修退货单审核（已送修）后登记工厂修好送回的成品入库；已结案则关闭入口 -->
      <el-button type="primary" v-if="isRepair && detail.status===DocStatus.AUDITED && detail.closedFlag!==1" @click="openRepairReturn">登记维修返回</el-button>
      <!-- 结案 / 撤销结案（仅维修退货，2026-09-17）：工厂把送修成品全部送回（未返回=0）后收尾 -->
      <el-button type="success" v-if="isRepair && detail.status===DocStatus.AUDITED && detail.closedFlag!==1 && Number(detail.unreturnedQty)===0" @click="handleClose">结案</el-button>
      <el-button type="warning" v-if="isRepair && detail.closedFlag===1" @click="handleReOpen">撤销结案</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <span style="font-weight:600">委外加工退货详情</span>
      </template>

      <!-- ============ 草稿（维修退货）：可编辑（字段/校验/payload 与 add.vue 的 REPAIR 路径一致） ============ -->
      <el-form v-if="showDraftForm" :model="form" label-width="var(--app-label-width)">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="退货单号">{{ detail.code }}</el-form-item></el-col>
          <el-col :span="8"><el-form-item label="退货类型">
            <el-tag :type="OutsourceReturnTypeTag[form.returnType] || 'info'" size="small">{{ OutsourceReturnTypeLabel[form.returnType] || form.returnType }}</el-tag>
            <span style="margin-left:6px;color:#909399;font-size:var(--app-font-xs)">类型不可改（后端同口径拦截）</span>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态"><el-tag :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item required label="加工厂">
              <RemoteSelect v-model="form.factoryId" :fetch="fetchSuppliers" label-key="name" placeholder="实时查库（加工厂）" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="送修出库仓">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="(row:any)=>row.warehouseName" placeholder="我方成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="送修日期"><el-input v-model="form.returnDate" type="date" /></el-form-item>
          </el-col>
          <!-- 工厂收费：**加工厂向我方收取**（我方付加工厂）；维修退货必须收费 -->
          <el-col :span="8">
            <el-form-item required label="收费类型">
              <el-select v-model="form.chargeType" style="width:100%">
                <el-option v-for="o in chargeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="收费金额">
              <el-input-number v-model="form.chargeAmount" :min="0" :precision="2" :controls="false" style="width:100%" placeholder="加工厂向我方收取" />
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="收费说明"><el-input v-model="form.chargeReason" placeholder="可选" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <!-- ============ 已审核 / 已作废 / 加工退货草稿：只读（原口径原样保留） ============ -->
      <el-descriptions v-else :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="退货类型">
          <el-tag :type="OutsourceReturnTypeTag[detail.returnType || 'DEFECT'] || 'info'" size="small">
            {{ OutsourceReturnTypeLabel[detail.returnType || 'DEFECT'] || detail.returnType }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="加工厂">{{ detail.factoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联加工单">
          <span v-if="detail.orderCode">{{ detail.orderCode }}</span>
          <span v-else style="color:var(--app-text-placeholder)">{{ isRepair ? '不关联（维修退货）' : '未关联' }}</span>
        </el-descriptions-item>
        <!-- 来源收货记录：从「成品收货」按记录发起退货时才有（2026-09-17） -->
        <el-descriptions-item label="来源收货记录">{{ detail.sourceDeliveryId ? ('#' + detail.sourceDeliveryId) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ $fmtDate(detail.returnDate) }}</el-descriptions-item>
        <el-descriptions-item label="送修/已返回" v-if="isRepair">
          {{ (detail.products || []).reduce((s: number, p: any) => s + (Number(p.quantity) || 0), 0) }} /
          <span style="color:var(--app-color-success);font-weight:600">{{ Number(detail.repairReturnedQty || 0) }}</span>
          <span v-if="Number(detail.unreturnedQty) > 0" style="margin-left:8px;color:var(--app-color-warning)">未返回 {{ detail.unreturnedQty }}</span>
          <span v-else style="margin-left:8px;color:var(--app-color-success)">已全部返回</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <!-- 已结案直接显示「已结案」（替代"已审核"），2026-09-17 -->
          <el-tag v-if="isRepair && detail.closedFlag===1" type="success" size="small">已结案</el-tag>
          <el-tag v-else :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="结案" v-if="isRepair && detail.closedFlag===1">
          {{ detail.closedBy || '-' }}
          <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ $fmtDate(detail.closedTime) }}</span>
        </el-descriptions-item>
        <!-- 收费方向：**加工厂向我方收取**（我方付加工厂 → 审核生成一条正向应付） -->
        <el-descriptions-item label="工厂收费（我方应付）">
          <template v-if="Number(detail.chargeFlag) === 1 && Number(detail.chargeAmount) > 0">
            <span style="color:#e6a23c;font-weight:600">{{ Number(detail.chargeAmount).toFixed(2) }}</span>
            <span style="margin-left:6px;color:#909399">{{ OutsourceChargeTypeLabel[String(detail.chargeType)] || detail.chargeType || '' }}</span>
          </template>
          <span v-else style="color:#c0c4cc">不收费</span>
        </el-descriptions-item>
        <el-descriptions-item label="收费说明" :span="3">{{ detail.chargeReason || '-' }}</el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 退货成品（送修内容）：维修退货没有物料明细，这一块才是它的主要内容（2026-09-17 补全） -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <span style="font-weight:600">{{ isRepair ? '送修成品明细' : '退货成品明细' }}</span>
        <span v-if="showDraftForm" style="font-weight:normal;color:#909399;margin-left:8px">
          数量/规格可直接改；把某行数量改成 0 表示本次不再送修该产品
        </span>
      </template>

      <!-- 草稿（维修退货）：可编辑（产品行不重建 —— 只改数量/规格，避免丢行） -->
      <el-table v-if="showDraftForm" :data="editableProducts" border size="small">
        <el-table-column label="产品名称" min-width="160"><template #default="{row}">{{ row.productName || ('#' + row.productId) }}</template></el-table-column>
        <el-table-column label="规格" width="110" align="center">
          <template #default="{row}">
            <el-select v-model="row.qualityType" size="small" style="width:100%">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="BOM快照" width="90" align="center">
          <template #default="{row}">
            <span v-if="row.bomSnapshotId">{{ row.bomVersion != null ? ('v' + row.bomVersion) : '已关联' }}</span>
            <span v-else style="color:var(--app-text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="送修数量" width="130">
          <template #default="{row}">
            <el-input-number v-model="row.returnQuantity" size="small" :min="0" :controls="false" :precision="0" :step="1" style="width:100%" />
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="detail.products || []" border size="small">
        <el-table-column label="产品名称" min-width="160"><template #default="{row}">{{ row.productName || ('#' + row.productId) }}</template></el-table-column>
        <el-table-column label="规格" width="90" align="center">
          <template #default="{row}">{{ ProductQualityTypeLabel[row.qualityType || 'A'] || row.qualityType }}</template>
        </el-table-column>
        <!-- 所用 BOM 快照（2026-09-17）：追溯"这单按哪份 BOM 用量退的料" -->
        <el-table-column label="BOM快照" width="90" align="center">
          <template #default="{row}">
            <span v-if="row.bomSnapshotId">{{ row.bomVersion != null ? ('v' + row.bomVersion) : '已关联' }}</span>
            <span v-else style="color:var(--app-text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="isRepair ? '送修数量' : '退回数量'" width="110" align="right"><template #default="{row}">{{ row.quantity }}</template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top:12px" v-if="!isRepair">
      <template #header><span style="font-weight:600">退货物料明细（按 BOM 快照还料）</span></template>
      <el-table :data="detail.items || []" border size="small">
        <el-table-column label="物料名称" min-width="160"><template #default="{row}">{{ row.materialName || row.materialId }}</template></el-table-column>
        <el-table-column label="物料类型" width="100"><template #default="{row}">{{ row.materialTypeName || '-' }}</template></el-table-column>
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right" />
        <el-table-column prop="amount" label="金额" width="110" align="right" />
      </el-table>
      <div v-if="!(detail.items || []).length" style="color:var(--app-text-secondary);font-size:var(--app-font-xs);padding:8px 0">
        该产品未带出退货物料（包工包料或无 BOM 快照），审核时只做成品出库、不冲减应付。
      </div>
    </el-card>

    <!-- 维修返回记录 -->
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
        <el-table-column label="产品名称" min-width="160"><template #default="{row}">{{ row.productName || ('#' + row.productId) }}</template></el-table-column>
        <el-table-column label="品质" width="90" align="center"><template #default="{row}">{{ ProductQualityTypeLabel[row.qualityType || 'A'] || row.qualityType }}</template></el-table-column>
        <el-table-column label="返回数量" width="110" align="right"><template #default="{row}"><span style="color:var(--app-color-success);font-weight:500">{{ row.quantity }}</span></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{row}"><el-button type="danger" link size="small" v-if="detail.closedFlag!==1" @click="cancelRepairReturn(row)">撤销</el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="!(detail.repairReturns || []).length" style="color:var(--app-text-secondary);font-size:var(--app-font-xs);padding:8px 0">
        {{ detail.status!==DocStatus.AUDITED ? '审核（送修）后即可登记维修返回。' : (detail.closedFlag===1 ? '已结案（无维修返回记录）。' : '尚未登记维修返回（工厂修好送回后再登记，登记即入库）。') }}
      </div>
      <div v-else-if="Number(detail.unreturnedQty) > 0" style="color:var(--app-color-warning);font-size:var(--app-font-xs);padding:8px 0">
        还有 {{ detail.unreturnedQty }} 件未返回（工厂尚未修好送回）；全部返回后可结案。
      </div>
    </el-card>

    <!-- 登记维修返回弹窗 -->
    <el-dialog v-model="repairVisible" title="登记维修返回" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <el-form label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item required label="入库仓库"><RemoteSelect v-model="repairWarehouseId" :fetch="fetchWarehousesForRepair" :label-key="(row:any)=>row.warehouseName" placeholder="选择返回入库的我方成品仓" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="返回日期"><el-input v-model="repairDate" type="date" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <el-table :data="repairRows" border size="small">
        <el-table-column label="产品" min-width="150"><template #default="{row}">{{ row.productName }}</template></el-table-column>
        <el-table-column label="规格" width="110">
          <template #default="{row}">
            <el-select v-model="row.qualityType" size="small" style="width:100%">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="送修 / 已返回" width="130" align="center"><template #default="{row}">{{ row.sentQty }} / {{ row.returnedQty }}</template></el-table-column>
        <el-table-column label="本次返回" width="140">
          <template #default="{row}">
            <el-input-number v-model="row.quantity" size="small" :min="0" :max="row.sentQty - row.returnedQty" :controls="false" :precision="0" :step="1" style="width:100%" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="repairVisible = false">取消</el-button>
        <el-button type="primary" :loading="repairSaving" @click="submitRepairReturn">确认登记（成品入库）</el-button>
      </template>
    </el-dialog>
  </PageShell>
</template>
