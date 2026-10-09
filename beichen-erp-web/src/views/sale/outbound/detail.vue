<script setup lang="ts">
import { computed, reactive, ref, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { getQualityTypes, type QualityOption } from '@/api/product'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import {
  getSaleOutbound, getSaleOutboundItems, updateSaleOutbound, auditSaleOutbound, cancelSaleOutbound, unAuditSaleOutbound,
  type SaleOutbound, type SaleOutboundItem,
} from '@/api/sale'

/**
 * 销售出库详情（2026-09-24 用户口径：草稿态在详情页就地改+存，列表不再给「编辑」/「反审核」）
 *
 * 本页由原列表的**只读详情弹窗**升级而来（原「详情」是 dialog ⇒ 撤销/编辑类动作仍挤在列表行内）。
 * 结构对齐其它单据详情：`head` 只读快照 + `form`/`items` 可编辑副本（仅草稿态）；
 * 字段、校验、payload 与列表弹窗**完全一致**：payload = `{ outbound: {...}, items: [...] }`。
 *
 * 一处**有意的收窄**（写清楚以免后人当成 bug）：明细支持改「数量/单价/备注」与删除行，但**不提供新增产品行**
 * —— 新增产品要一整套"选产品 → 带出名称/SKU"的联动（列表弹窗里那套 `loadProducts/onProductChange`），
 * 而给一张已存在的出库单临时加产品属于罕见操作（正常做法是重新建单）；保持一套联动入口可避免规则分叉。
 * （2026-10-09 §7.26：本页明细的对象是**成品 product** —— 原注释写的"物料/规格/单位"是幽灵字段时代的残留。）
 */
const route = useRoute()
const router = useRouter()
const id = () => Number(route.params.id)
const loading = ref(false)
const acting = ref(false)
const saving = ref(false)

const head = ref<SaleOutbound>({})
const items = ref<SaleOutboundItem[]>([])
const isDraft = computed(() => head.value.status === DocStatus.DRAFT)
const isAudited = computed(() => head.value.status === DocStatus.AUDITED)

/** 可编辑副本（白名单：单号/状态/总金额/来源销售单 不回传；金额由后端按明细重算） */
const form = reactive({ customerId: undefined as any, warehouseId: undefined as any, outboundDate: '', remark: '' })

const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })
const customers = ref<any[]>([])
const warehouses = ref<any[]>([])
async function loadDicts() {
  try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] }
  try { const r: any = await fetchWarehouses(''); warehouses.value = r?.records || [] } catch { warehouses.value = [] }
}
const customerName = computed(() => customers.value.find((x) => x.id === head.value.customerId)?.name || '—')
const warehouseName = computed(() => warehouses.value.find((x) => x.id === head.value.warehouseId)?.warehouseName || '—')

const qualityOptions = ref<QualityOption[]>([])
async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }

async function loadData() {
  loading.value = true
  try {
    head.value = (await getSaleOutbound(id())) || {}
    form.customerId = head.value.customerId ?? undefined
    form.warehouseId = head.value.warehouseId ?? undefined
    form.outboundDate = head.value.outboundDate ? String(head.value.outboundDate).slice(0, 10) : ''
    form.remark = head.value.remark || ''
    const res: any = await getSaleOutboundItems(id())
    // 深拷一层：编辑不污染 head（金额为展示用派生值，保存时由后端按 数量×单价 重算）
    items.value = (res || []).map((it: any) => ({ ...it, quantity: Number(it.quantity || 0), unitPrice: Number(it.unitPrice || 0) }))
  } catch { items.value = [] } finally { loading.value = false }
}

function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }
function fmtDate(v?: string) { return v ? String(v).slice(0, 10) : '—' }
function itemAmount(row: SaleOutboundItem) { return ((Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)).toFixed(2) }
function removeItem(index: number) { items.value.splice(index, 1) }

/** 保存（与列表弹窗同一套校验与 payload；后端 update 自带「只有草稿可编辑」守卫） */
async function doSave() {
  if (!form.customerId) { ElMessage.warning('请选择客户'); return }
  if (!form.warehouseId) { ElMessage.warning('请选择出库仓库'); return }
  // 2026-10-09（用户口径：本单是销售单的出库凭证）：草稿保存在后端同样会校验——必须挂已审核销售单、
  // 且明细产品属于该单；这里先给一致的提示，避免"点了保存才报错"。
  if (!head.value.orderId) { ElMessage.warning('本单未关联来源销售单，无法保存（销售出库须由销售单带入）'); return }
  if (items.value.length === 0) { ElMessage.warning('请至少保留一条明细'); return }
  saving.value = true
  try {
    await updateSaleOutbound(id(), {
      outbound: {
        id: id(), orderId: head.value.orderId, customerId: form.customerId, warehouseId: form.warehouseId,
        outboundDate: form.outboundDate, remark: form.remark,
      },
      // 2026-10-09（§7.26 幽灵字段）：明细发**产品**键（productId/orderItemId）—— 原发
      // materialId/materialName/spec/unit 在实体里根本不存在，Jackson 静默丢弃 ⇒ product_id 恒 NULL。
      items: items.value.map((it) => ({
        id: it.id, productId: it.productId, orderItemId: it.orderItemId,
        qualityType: it.qualityType, quantity: Number(it.quantity) || 0, unitPrice: Number(it.unitPrice) || 0, remark: it.remark,
      })),
    } as any)
    ElMessage.success('已保存')
    await loadData()
  } catch (e: any) { ElMessage.error(e?.msg || e?.message || '保存失败') } finally { saving.value = false }
}

async function doAudit() {
  try {
    await ElMessageBox.confirm(`确认审核销售出库「${head.value.code}」？本单仅为出库凭证：不扣减库存（库存已在销售单审核时扣减）、不生成应收。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  acting.value = true
  try { await auditSaleOutbound(id()); ElMessage.success('已审核（出库凭证，不涉及库存）'); await loadData() } finally { acting.value = false }
}

async function doUnAudit() {
  try { await ElMessageBox.confirm(`确认反审核销售出库「${head.value.code}」？仅回退状态，不涉及库存。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  acting.value = true
  try { await unAuditSaleOutbound(id()); ElMessage.success('已反审核'); await loadData() } finally { acting.value = false }
}

async function doCancel() {
  try { await ElMessageBox.confirm(`确认作废销售出库「${head.value.code}」？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  acting.value = true
  try {
    await cancelSaleOutbound(id())
    ElMessage.success('已作废')
    router.push('/sale/outbound')
  } finally { acting.value = false }
}

// 单据数据每次进入都重新拉取（keep-alive 下 onMounted 不会再触发）；字典只需一次
let dictLoaded = false
onActivated(() => {
  loadData()
  if (!dictLoaded) { dictLoaded = true; loadDicts(); loadQualityTypes() }
})
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/sale/outbound">
    <template #actions>
      <!-- 草稿：保存(主) + 审核 + 作废（2026-09-24 用户口径：草稿态就地编辑） -->
      <el-button type="primary" v-if="isDraft" :loading="saving" @click="doSave">保存</el-button>
      <el-button type="success" v-if="isDraft" :loading="acting" @click="doAudit">审核</el-button>
      <el-button type="danger" v-if="isDraft" :loading="acting" @click="doCancel">作废</el-button>
      <!-- 反审核：仅回退状态、不涉及库存（撤销类操作，2026-09-24 从列表移入详情） -->
      <el-button type="warning" v-if="isAudited" :loading="acting" @click="doUnAudit">反审核</el-button>
    </template>

    <el-card shadow="never">
      <!-- ============ 草稿：可编辑（字段/校验/payload 与列表弹窗一致） ============ -->
      <el-form v-if="isDraft" :model="form" label-width="var(--app-label-width)">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="单号">{{ head.code }}</el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态"><el-tag :type="DocStatusTag[head.status || 'DRAFT']">{{ DocStatusLabel[head.status || 'DRAFT'] || head.status }}</el-tag></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="来源销售单">{{ head.orderId ? ('#' + head.orderId) : '—' }}</el-form-item></el-col>
          <el-col :span="8">
            <el-form-item required label="客户">
              <RemoteSelect v-model="form.customerId" add-route="/inventory/customer/add" :fetch="fetchCustomers" placeholder="请选择" style="width:100%" domain="customer" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="出库仓库">
              <RemoteSelect v-model="form.warehouseId" add-route="/inventory/warehouse" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择" style="width:100%" domain="warehouse" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出库日期">
              <el-date-picker v-model="form.outboundDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
        <el-alert type="info" :closable="false" show-icon
          title="本单仅为出库凭证：审核不扣减库存（库存已在销售单审核时扣减）、不生成应收。" />
      </el-form>

      <!-- ============ 已审核 / 已作废：只读 ============ -->
      <el-descriptions v-else :column="3" border size="small">
        <el-descriptions-item label="单号">{{ head.code }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ customerName }}</el-descriptions-item>
        <el-descriptions-item label="出库仓库">{{ warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="出库日期">{{ fmtDate(head.outboundDate) }}</el-descriptions-item>
        <el-descriptions-item label="总金额"><span style="color:var(--app-color-danger);font-weight:600">{{ fmt(head.totalAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="DocStatusTag[head.status || '']">{{ DocStatusLabel[head.status || ''] || head.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源销售单">{{ head.orderId ? ('#' + head.orderId) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ head.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <span style="font-weight:600">出库明细</span>
        <span v-if="isDraft" style="font-weight:normal;color:#909399;margin-left:8px">
          数量/单价/备注可直接改；本页不支持新增产品行（要加产品请重新建单，避免与列表弹窗的取数联动分叉）
        </span>
      </template>

      <!-- 草稿：可编辑。2026-10-09（§7.26 幽灵字段）：明细对象是**成品** —— 列改 productName/sku。
           原先读 materialName/spec/unit，而实体 SaleOutboundItem 没有这些字段 ⇒ 列恒空（或恒 `#undefined`）。 -->
      <el-table v-if="isDraft" :data="items" border size="small">
        <el-table-column prop="productName" label="产品名称" min-width="150"><template #default="{ row }">{{ row.productName || ('#' + row.productId) }}</template></el-table-column>
        <el-table-column prop="sku" label="SKU" width="130" show-overflow-tooltip />
        <el-table-column label="品质" width="96" align="center">
          <template #default="{ row }">
            <el-select v-model="row.qualityType" size="small" style="width:100%">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="110">
          <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="单价" width="110">
          <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right"><template #default="{ row }">{{ itemAmount(row) }}</template></el-table-column>
        <el-table-column label="备注" min-width="110"><template #default="{ row }"><el-input v-model="row.remark" size="small" /></template></el-table-column>
        <el-table-column label="操作" width="72" align="center">
          <template #default="{ $index }"><el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button></template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="items" border size="small">
        <el-table-column prop="productName" label="产品名称" min-width="150" />
        <el-table-column prop="sku" label="SKU" width="130" show-overflow-tooltip />
        <el-table-column prop="qualityType" label="品质" width="80" align="center" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="90" align="right"><template #default="{ row }">{{ fmt(row.unitPrice) }}</template></el-table-column>
        <el-table-column prop="amount" label="金额" width="100" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip />
      </el-table>
    </el-card>
  </PageShell>
</template>

<style scoped>
:deep(.el-card__body) { padding: 16px; }
</style>
