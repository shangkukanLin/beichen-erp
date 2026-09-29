<script setup lang="ts">
// 客户应收详情 / 工作台（2026-09-29 用户口径：**汇总搬到「应收管理」**，与供应商侧对称）
//   —— 应收管理页「按客户汇总」的「详情」进本页（`/finance/receivable/customer/:id`）。
//   数据全部走**应收前缀**（`/finance/receivable/{customer-summary,page,receipts}`）⇒ 只要求 finance:receivable，
//   只被授予应收权限的用户也不会 403（与供应商应付工作台同款处理；「收款记录」是后端镜像的只读视图）。
//   内容 = 汇总卡（应收总额/已收/未收/逾期金额）+ 应收明细 + 收款记录。
//   「新增收款」**按权限显示**：只有拿到 `finance:receipt` 的用户才看得见（v-perm 与后端前缀权限同码）。
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { DocStatusLabel, DocStatusTag } from '@/api/common'
import { SettlementStatus, SettlementStatusLabel, sourceBillTypeLabel, SubjectType } from '@/api/enums'

const route = useRoute(); const router = useRouter()
const customerId = Number(route.params.id)
const loading = ref(false)
/** 汇总行（含 customerName —— 刻意不读 `/inventory/customer/{id}`：那需要库存/客户模块权限） */
const summary = ref<any>({})
const receivables = ref<any[]>([])
const receipts = ref<any[]>([])

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
/** 「账户」列文案（2026-09-29 多账户收款）：单账户 = 账户名；多账户 =「N 个账户」（明细见收款单详情页） */
function accountText(row: any) {
  const n = Number(row?.accountCount || 0)
  if (n > 1) return `${n} 个账户`
  return row?.accountName || '—'
}
const STATUS_LABEL: Record<string, string> = SettlementStatusLabel
function statusLabel(code?: string) { return code ? (STATUS_LABEL[code] || code) : '' }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { if (s === SettlementStatus.UNSETTLED) return 'danger'; if (s === SettlementStatus.PARTIAL) return 'warning'; if (s === SettlementStatus.SETTLED) return 'success'; if (s === SettlementStatus.CANCELLED) return 'info'; return 'info' }
function pStType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { return DocStatusTag[s || ''] || undefined }
function goReceivableDetail(row: any) { if (row?.id != null) router.push(`/finance/receivable/detail/${row.id}`) }
function goReceiptDetail(row: any) { if (row?.id != null) router.push(`/finance/receipt/detail/${row.id}`) }

async function loadAll() {
  loading.value = true
  try {
    const [sumList, rList, recList] = await Promise.all([
      request.get<any, any>('/finance/receivable/customer-summary'),
      request.get<any, any>('/finance/receivable/page', { params: { customerId, subjectType: SubjectType.CUSTOMER, pageSize: 200 } }),
      request.get<any, any>('/finance/receivable/receipts', { params: { customerId, pageSize: 100 } })
    ])
    summary.value = (sumList || []).find((x: any) => x.customerId === customerId) || {}
    receivables.value = rList?.records || []
    receipts.value = recList?.records || []
  } finally { loading.value = false }
}

/** 新增收款（按权限显示）：先去收款页确认没有可收/预收限制？—— 收款支持"不核销只挂预收"，故不拦，直接进 */
function openAddReceipt() { router.push({ path: '/finance/receipt/add', query: { customerId } }) }
/** 返回应收管理（本页从「按客户汇总」进来） */
function goBack() { router.push('/finance/receivable') }

onMounted(() => loadAll())
</script>

<template>
  <div class="p" v-loading="loading">
    <div class="page-header">
      <span style="font-weight:600;font-size:var(--app-font-lg)">{{ summary.customerName || ('客户 #' + customerId) }}</span>
      <div>
        <!-- 2026-09-29 用户口径⑤（对称）：收款动作归「收款管理」，本页只在持有 finance:receipt 时显示入口 -->
        <el-button v-perm="'finance:receipt'" type="primary" :icon="'Plus'" @click="openAddReceipt">新增收款</el-button>
        <el-button @click="goBack">返回</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="stat-row">
        <div class="stat-item"><div class="stat-label">应收总额</div><div class="stat-value">{{ fmt(summary.totalAmount) }}</div></div>
        <div class="stat-item"><div class="stat-label">已收</div><div class="stat-value" style="color:var(--app-color-success)">{{ fmt(summary.paidAmount) }}</div></div>
        <div class="stat-item"><div class="stat-label">未收</div><div class="stat-value" style="color:var(--app-color-warning)">{{ fmt(summary.unpaidAmount) }}</div></div>
        <div class="stat-item">
          <div class="stat-label">逾期金额</div>
          <div class="stat-value" style="color:var(--app-color-danger)">{{ fmt(summary.overdueAmount) }}</div>
          <!-- 实测客户侧台账到期日普遍为空 ⇒ 解释"为什么逾期是 0"，否则像算错 -->
          <div v-if="Number(summary.noDueCount) > 0" style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">
            （{{ summary.noDueCount }} 张未约定到期日，未计入）
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">应收明细</span></template>
      <el-table :data="receivables" border stripe>
        <el-table-column label="单据号" width="150" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click="goReceivableDetail(row)">{{ row.billNo }}</el-button></template>
        </el-table-column>
        <el-table-column label="来源" width="130" show-overflow-tooltip><template #default="{row}">{{ sourceBillTypeLabel(row.sourceBillType) }}</template></el-table-column>
        <el-table-column label="来源单号" width="160" show-overflow-tooltip><template #default="{row}">{{ row.sourceBillNo || '—' }}</template></el-table-column>
        <el-table-column label="应收金额" width="110" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="已收" width="110" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column label="未收" width="110" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="到期日" width="100" align="center"><template #default="{row}">{{ $fmtDate(row.dueDate) }}</template></el-table-column>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="stType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
      </el-table>
      <el-empty v-if="receivables.length===0" description="暂无应收数据" :image-size="60" />
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">收款记录</span></template>
      <el-table :data="receipts" border stripe>
        <el-table-column label="单号" width="150">
          <template #default="{row}"><el-button type="primary" link @click="goReceiptDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <!-- 2026-09-29 多账户收款：单账户 = 账户名；多账户 =「N 个账户」（明细在收款单详情页） -->
        <el-table-column label="账户" width="120"><template #default="{row}">{{ accountText(row) }}</template></el-table-column>
        <el-table-column prop="receiptDate" label="日期" width="100" align="center" />
        <el-table-column label="金额" width="110" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="pStType(row.status)" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="receipts.length===0" description="暂无收款记录" :image-size="60" />
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
