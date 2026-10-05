<script setup lang="ts">
// 供应商应付详情 / 工作台（2026-09-29 用户口径：**汇总搬到「应付管理」**）
//   —— 本页由 `/finance/payment/supplier/:id` **搬到** `/finance/payable/supplier/:id`（应付前缀），
//      数据接口同步镜像到应付前缀（`/finance/payable/{supplier-summary,page,payments}`）⇒
//      **只被授予 `finance:payable` 的用户**点「应付管理 → 按供应商汇总 → 详情」也能正常打开；
//      旧地址保留为重定向（见 `router/index.ts`，回归脚本/书签不失效）。
//   内容 = 汇总卡（应付总额/已付/未付/逾期金额）+ 应付明细 + 付款记录。
//   「新增付款」**按权限显示**（2026-09-29 用户口径⑤）：只有拿到 `finance:payment` 的用户才看得见
//   （v-perm 与后端前缀权限同码），点击进统一的新增付款独立页（`?supplierId=` 预填）。
import { ref } from 'vue'
import { useDomainRefresh } from '@/utils/dataFreshness'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { DocStatusLabel, DocStatusTag } from '@/api/common'
import { SettlementStatus, SettlementStatusLabel, sourceBillTypeLabel, SourceBillDetailRoute } from '@/api/enums'

const route = useRoute(); const router = useRouter()
const supplierId = Number(route.params.id)
const loading = ref(false)
/** 汇总行（含 supplierName —— 刻意不再单独读 `/supplier/{id}`：那需要供应商模块权限，
 *  只被授予 finance:payable 的用户会 403；供应商名从汇总行带过来即可） */
const summary = ref<any>({})
const payables = ref<any[]>([])
const payments = ref<any[]>([])

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
/** 「账户」列文案（2026-09-29 多账户付款）：单账户 = 账户名；多账户 =「N 个账户」（明细见付款单详情页） */
function accountText(row: any) {
  const n = Number(row?.accountCount || 0)
  if (n > 1) return `${n} 个账户`
  return row?.accountName || '—'
}

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
  // 属豁免前缀），不直读加工单页的 /outsource/order/page（需 outsource:order ⇒ 只有财务权限的用户会 403）。
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
    // 全部走**本页前缀**（应付）⇒ 只要求 finance:payable。
    // 口径与应付管理页/付款管理页共用同一实现（PayableQuery / FinancePaymentService.page）。
    const [sumList, pList, payList] = await Promise.all([
      request.get<any, any>('/finance/payable/supplier-summary'),
      request.get<any, any>('/finance/payable/page', { params: { supplierId, pageSize: 200 } }),
      request.get<any, any>('/finance/payable/payments', { params: { supplierId, pageSize: 100 } })
    ])
    summary.value = (sumList || []).find((x: any) => x.supplierId === supplierId) || {}
    payables.value = pList?.records || []
    payments.value = payList?.records || []
  } catch { summary.value = {}; payables.value = []; payments.value = [] }
  // 2026-09-29 审核批 B · F7-221：原先只有 try/finally 没有 catch ⇒ `Promise.all` 任一端点失败会成为
  // **未处理的 Promise rejection**（消息仍由拦截器弹出，但页面无兜底、状态半新半旧）。补显式兜底。
  finally { loading.value = false }
}

/** 新增付款（按权限显示）：先在列表拦住"没有未结清应付"，避免进去才发现没法核销 */
async function openAddPayment() {
  let list: any[] = []
  try { list = await request.get<any, any>('/finance/payable/unpaid', { params: { supplierId } }) || [] } catch { list = [] }
  if (list.length === 0) { ElMessage.info('该供应商没有未结清应付'); return }
  router.push({ path: '/finance/payment/add', query: { supplierId } })
}

function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { if (s === SettlementStatus.UNSETTLED) return 'danger'; if (s === SettlementStatus.PARTIAL) return 'warning'; if (s === SettlementStatus.SETTLED) return 'success'; if (s === SettlementStatus.CANCELLED) return 'info'; return 'info' }
function pStType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { return DocStatusTag[s || ''] || undefined }
function openAttach(url: string) { window.open(url + '?inline=true') }
function goSettlement() { router.push(`/finance/supplier-settlement/${supplierId}`) }
/** 返回应付管理（本页从「按供应商汇总」进来，回列表而不是回付款管理） */
function goBack() { router.push('/finance/payable') }

useDomainRefresh('payable', () => loadAll())
</script>

<template>
  <div class="p" v-loading="loading">
    <div class="page-header">
      <div style="display:flex;align-items:center;gap:12px">
        <span style="font-weight:600;font-size:var(--app-font-lg)">{{ summary.supplierName || ('供应商 #' + supplierId) }}</span>
        <el-tag v-if="summary.supplierType" size="small" type="info">{{ summary.supplierType }}</el-tag>
      </div>
      <div>
        <!-- 2026-09-29 用户口径⑤：付款动作归「付款管理」，故本页只在持有 finance:payment 时显示该入口 -->
        <el-button v-perm="'finance:payment'" type="primary" :icon="'Plus'" @click="openAddPayment">新增付款</el-button>
        <el-button type="danger" plain @click="goSettlement">清算</el-button>
        <el-button @click="goBack">返回</el-button>
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
      <el-empty v-if="payables.length===0" description="暂无应付数据" :image-size="60" />
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">付款记录</span></template>
      <el-table :data="payments" border stripe>
        <el-table-column prop="code" label="单号" width="150" />
        <!-- 2026-09-29 多账户付款：单账户 = 账户名；多账户 =「N 个账户」（明细在付款单详情页） -->
        <el-table-column label="账户" width="120"><template #default="{row}">{{ accountText(row) }}</template></el-table-column>
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
