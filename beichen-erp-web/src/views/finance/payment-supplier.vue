<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { getPaymentPage, createPayment, getPaymentUnpaidPayables, getPaymentPayableSummary, getPaymentPayables, type FinancePaymentItem } from '@/api/finance'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { SettlementStatus, SettlementStatusLabel, sourceBillTypeLabel, SourceBillDetailRoute } from '@/api/enums'

const route = useRoute(); const router = useRouter()
const supplierId = Number(route.params.id)
const loading = ref(false)
const supplier = ref<any>({})
const summary = ref<any>({})
const payables = ref<any[]>([])
const payments = ref<any[]>([])
const accounts = ref<any[]>([])

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

// 结算状态 code -> 中文
const STATUS_LABEL: Record<string, string> = SettlementStatusLabel
function statusLabel(code?: string) { return code ? (STATUS_LABEL[code] || code) : '' }

// 来源单据类型 -> 详情路由前缀（用于点击来源单号跳转，公共映射见 @/api/enums）
async function goSourceDetail(row: any) {
  const base = SourceBillDetailRoute[row.sourceBillType]
  if (!base || row.sourceBillNo == null) return
  let targetId = row.sourceId
  // 委外加工收货/超损的 sourceId 是收货记录/结单报表ID，需按单号反查加工单ID。
  // 期 2（2026-09-19 读隔离）：改用通用单号解析器 /common/resolve-code（跨模块单号跳转的标准做法，
  // 属豁免前缀），不再直读加工单页的 /outsource/order/page（需 outsource:order ⇒ 只有 finance:payment 的用户会 403）。
  if (row.sourceBillType === 'OUTSOURCE_DELIVERY' || row.sourceBillType === 'OUTSOURCE_EXCESS_LOSS') {
    const r: any = await request.get('/common/resolve-code', { params: { code: row.sourceBillNo } })
    targetId = r?.type === 'order' ? r.id : undefined
  }
  if (targetId == null) return
  router.push(`${base}/${targetId}`)
}

async function loadAll() {
  loading.value = true
  try {
    // 期 2（2026-09-19 读隔离）：应付汇总/明细改走付款页自身前缀（原读 /finance/payable/* 需 finance:payable）
    const [sup, sumList, pList, payList, accList] = await Promise.all([
      request.get<any, any>(`/supplier/${supplierId}`),
      getPaymentPayableSummary(),
      getPaymentPayables({ supplierId, pageSize: 200 }),
      getPaymentPage({ supplierId, pageSize: 100 }),
      request.get<any, any>('/finance/account/list')
    ])
    supplier.value = sup || {}
    summary.value = (sumList || []).find((x: any) => x.supplierId === supplierId) || {}
    payables.value = pList?.records || []
    payments.value = payList?.records || []
    accounts.value = accList || []
  } finally { loading.value = false }
}

// ========== 新增付款 ==========
const dVisible = ref(false)
const dLoading = ref(false)
const dForm = reactive({ accountId: undefined as any, paymentDate: localDate(), remark: '', attachUrl: '' })
const dItems = ref<FinancePaymentItem[]>([])
const unpaid = ref<any[]>([])
const uploadFile = ref<File | null>(null)

/** 2026-09-23 用户要求：新增付款由 800px 弹框改为独立页（无未结清应付时仍先在列表拦住） */
async function openAddPayment() {
  try { unpaid.value = await getPaymentUnpaidPayables(supplierId) || [] } catch { unpaid.value = [] }
  if (unpaid.value.length === 0) { ElMessage.info('该供应商没有未结清应付'); return }
  router.push({ path: '/finance/payment/supplier/add', query: { supplierId } })
}
function addItem() { dItems.value.push({ payableId: undefined, payableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { dItems.value.splice(i, 1) }
function onPayableChange(val: number, row: FinancePaymentItem) {
  const r = unpaid.value.find(x => x.id === val)
  if (r) { row.payableId = r.id; row.payableBillNo = r.billNo; row.thisAmount = r.unpaidAmount }
}
function handleFileSelect(e: Event) { const f = (e.target as HTMLInputElement).files?.[0]; if (f) uploadFile.value = f }
/** 2026-09-20（F7-169）：从按钮向上找最近的 form-item 容器再取隐藏的 file input，
 *  不依赖固定的 DOM 层级（原写法写死两层 parentElement，结构一调就静默失效）。 */
function pickUploadFile(e: Event) {
  const btn = e.currentTarget as HTMLElement | null
  const box = btn?.closest('.el-form-item') as HTMLElement | null
  box?.querySelector<HTMLInputElement>('input[type="file"]')?.click()
}

async function handleSubmitPayment() {
  if (!dForm.accountId) { ElMessage.warning('请选择付款账户'); return }
  if (dItems.value.length === 0) { ElMessage.warning('请添加核销明细'); return }
  dLoading.value = true
  try {
    if (uploadFile.value) {
      const fd = new FormData(); fd.append('file', uploadFile.value)
      dForm.attachUrl = await request.post<any, string>('/dev/file/upload', fd) as unknown as string
    }
    await createPayment({ payment: { supplierId, ...dForm }, items: dItems.value })
    ElMessage.success('付款单已创建，请在付款记录中审核')
    dVisible.value = false; loadAll()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { dLoading.value = false }
}

const totalThisAmount = computed(() => dItems.value.reduce((s, it) => s + (Number(it.thisAmount) || 0), 0))

function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { if (s === SettlementStatus.UNSETTLED) return 'danger'; if (s === SettlementStatus.PARTIAL) return 'warning'; if (s === SettlementStatus.SETTLED) return 'success'; if (s === SettlementStatus.CANCELLED) return 'info'; return 'info' }
function pStType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { return DocStatusTag[s || ''] || undefined }
function openAttach(url: string) { window.open(url + '?inline=true') }
function goSettlement() { router.push(`/finance/supplier-settlement/${supplierId}`) }

onMounted(() => loadAll())

</script>

<template>
  <div class="p" v-loading="loading">
    <div class="page-header">
      <div>
        <el-button type="primary" :icon="'Plus'" @click="openAddPayment">新增付款</el-button>
        <el-button type="danger" plain @click="goSettlement">清算</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="stat-row">
        <div class="stat-item"><div class="stat-label">应付总额</div><div class="stat-value">{{ fmt(summary.totalAmount) }}</div></div>
        <div class="stat-item"><div class="stat-label">已付</div><div class="stat-value" style="color:var(--app-color-success)">{{ fmt(summary.paidAmount) }}</div></div>
        <div class="stat-item"><div class="stat-label">未付</div><div class="stat-value" style="color:var(--app-color-warning)">{{ fmt(summary.unpaidAmount) }}</div></div>
        <div class="stat-item"><div class="stat-label">逾期金额</div><div class="stat-value" style="color:var(--app-color-danger)">{{ fmt(summary.overdueAmount) }}</div></div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">应付明细</span></template>
      <el-table :data="payables" border stripe>
        <el-table-column prop="billNo" label="单据号" width="150" />
        <el-table-column label="来源" width="130"><template #default="{row}">{{ sourceBillTypeLabel(row.sourceBillType) }}</template></el-table-column>
        <el-table-column label="来源单号" width="160" show-overflow-tooltip><template #default="{row}"><a v-if="row.sourceId != null" class="bill-link" @click="goSourceDetail(row)">{{ row.sourceBillNo }}</a><span v-else>{{ row.sourceBillNo }}</span></template></el-table-column>
        <el-table-column label="应付金额" width="110" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="已付" width="110" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column label="未付" width="110" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="到期日" width="100" align="center"><template #default="{row}">{{ $fmtDate(row.dueDate) }}</template></el-table-column>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="stType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
      </el-table>
      <el-empty v-if="payables.length===0" description="暂无应付" :image-size="60" />
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">付款记录</span></template>
      <el-table :data="payments" border stripe>
        <el-table-column prop="code" label="单号" width="150" />
        <el-table-column prop="accountName" label="账户" width="120" />
        <el-table-column prop="paymentDate" label="日期" width="100" align="center" />
        <el-table-column label="金额" width="110" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="凭证" width="70" align="center"><template #default="{row}"><el-link v-if="row.attachUrl" type="primary" @click="openAttach(row.attachUrl)">查看</el-link><span v-else style="color:#c0c4cc">—</span></template></el-table-column>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="pStType(row.status)" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="payments.length===0" description="暂无付款记录" :image-size="60" />
    </el-card>

  </div>
</template>

<style scoped>
.p{display:flex;flex-direction:column;gap:12px}
.page-header{display:flex;align-items:center;justify-content:space-between;padding-bottom:4px}

.stat-row{display:flex;gap:48px;padding:4px 8px}
.stat-label{font-size:var(--app-font-base);color:var(--app-text-secondary);margin-bottom:4px}
.stat-value{font-size:var(--app-font-num);font-weight:600}
</style>
