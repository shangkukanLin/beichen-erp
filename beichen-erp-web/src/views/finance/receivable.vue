<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useDomainRefresh } from '@/utils/dataFreshness'
import { useRouter } from 'vue-router'
import { getReceivablePage, type FinanceReceivable, type PageResult } from '@/api/finance'
import { SettlementStatus, SettlementStatusLabel, sourceBillTypeLabel, SubjectType, SubjectTypeLabel, SubjectTypeTag } from '@/api/enums'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
/** 页签：客户应收（销售业务）/ 供应商应收（应付转应收，向对方收款） */
const activeSubject = ref<'CUSTOMER' | 'SUPPLIER'>('CUSTOMER')
const query = reactive({ customerId: '' as string|number, supplierId: '' as string|number, status: '', billNo: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceReceivable[]>([])
const customersOptions = ref<any[]>([])

const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadCustomersOptions() {
  try { const r: any = await fetchCustomers(''); customersOptions.value = r?.records || [] } catch { customersOptions.value = [] }
}
const suppliersOptions = ref<any[]>([])
async function loadSuppliersOptions() {
  try { const r: any = await fetchSuppliers(''); suppliersOptions.value = r?.records || [] } catch { suppliersOptions.value = [] }
}

async function load() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize, subjectType: activeSubject.value }
    if (query.customerId) p.customerId = query.customerId
    if (query.supplierId) p.supplierId = query.supplierId
    if (query.status) p.status = query.status
    if (query.billNo) p.billNo = query.billNo
    const res = await getReceivablePage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
// ========== 视图：按客户汇总 / 应收台账（2026-09-29 用户口径「汇总要显示在**应收管理**里面」） ==========
/**
 * 视图切换：① <b>按客户汇总</b>（客户/应收总额/已收/未收/逾期金额/单据数 + 详情进客户应收工作台）；
 * ② <b>应收台账</b> = 原页面（客户应收/供应商应收页签 + 查询 + 台账表），一行未改。
 * <p>⚠️ 汇总**只含客户应收**（{@code subject_type=CUSTOMER}）：本视图就叫「按客户汇总」；
 * 「供应商应收」（应付转应收产生的）在台账的第二个页签里看。</p>
 * <p>逾期口径 = {@code due_date < 今天}（当天不算）、无到期日不计入 —— 实测客户侧到期日普遍为空，
 * 故汇总表另给「未约定到期日张数」提示，避免"逾期恒 0"被当成算错。</p>
 */
const view = ref<'summary' | 'ledger'>('summary')
const summaryLoading = ref(false)
const summaryData = ref<any[]>([])
async function loadSummary() {
  summaryLoading.value = true
  try {
    // 走应收页自身前缀（只要求 finance:receivable）；口径见 ReceivableQuery.customerSummary
    const r = await request.get<any, any>('/finance/receivable/customer-summary')
    summaryData.value = r || []
  } catch { summaryData.value = [] } finally { summaryLoading.value = false }
}
/** 汇总行「详情」→ 客户应收工作台（2026-09-29 新增，与「供应商应付工作台」对称） */
function goCustomerDetail(row: any) { if (row?.customerId != null) router.push(`/finance/receivable/customer/${row.customerId}`) }

useDomainRefresh('receivable', () => { loadCustomersOptions(); loadSuppliersOptions(); load(); loadSummary() })

// 切页签：清空往来单位筛选回到第一页
watch(activeSubject, () => {
  query.customerId = ''; query.supplierId = ''
  page.pageNum = 1; load()
})

function query_() { page.pageNum = 1; load() }
function reset_() { query.customerId = ''; query.supplierId = ''; query.status = ''; query.billNo = ''; page.pageNum = 1; load() }
function cName(id?: number) { return customersOptions.value.find(x => x.id === id)?.name || '' }
/** 往来单位：客户应收看客户名，供应商应收（应付转应收产生）看供应商名 */
function subjectName(row: any) {
  return row.subjectType === SubjectType.SUPPLIER
    ? (row.supplierName || '—')
    : (row.customerName || cName(row.customerId) || '—')
}
function goSubject(row: any) {
  if (row.subjectType === SubjectType.SUPPLIER && row.supplierId) router.push(`/supplier/detail/${row.supplierId}`)
}
/** 详情改独立页（2026-09-23）：点整行 / 行内「详情」都跳详情页，不再开抽屉 */
function goDetail(row: any) { if (row?.id != null) router.push(`/finance/receivable/detail/${row.id}`) }
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { if (s === SettlementStatus.UNSETTLED) return 'danger'; if (s === SettlementStatus.PARTIAL) return 'warning'; if (s === SettlementStatus.SETTLED) return 'success'; if (s === SettlementStatus.CANCELLED) return 'info'; return undefined }
</script>
<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <!-- 视图切换（2026-09-29 用户口径「汇总要显示在**应收管理**里」）：
           ①按客户汇总（新）②应收台账 = 原页面（页签 + 查询 + 台账表），一行未改。
           用 el-radio-group 做第一层切换，避免与台账内部的「客户/供应商应收」el-tabs 视觉混淆。 -->
      <el-radio-group v-model="view" style="margin-bottom:12px">
        <el-radio-button value="summary">按客户汇总</el-radio-button>
        <el-radio-button value="ledger">应收台账</el-radio-button>
      </el-radio-group>
      <template v-if="view === 'ledger'">
      <!-- 页签：客户应收 / 供应商应收，切换即切换主体类型（与供应商/供货商管理页交互一致） -->
      <el-tabs v-model="activeSubject">
        <el-tab-pane label="客户应收" name="CUSTOMER" />
        <el-tab-pane label="供应商应收" name="SUPPLIER" />
      </el-tabs>
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
      <el-form-item v-if="activeSubject === 'CUSTOMER'" label="客户"><RemoteSelect v-model="query.customerId" add-route="/inventory/customer/add" :fetch="fetchCustomers" placeholder="全部" style="width:160px" domain="customer" /></el-form-item>
      <el-form-item v-else label="供应商"><RemoteSelect v-model="query.supplierId" add-route="/supplier/manage/add" :fetch="fetchSuppliers" placeholder="全部" style="width:160px" domain="supplier" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:120px"><el-option v-for="s in [{l:SettlementStatusLabel[SettlementStatus.UNSETTLED],v:SettlementStatus.UNSETTLED},{l:SettlementStatusLabel[SettlementStatus.PARTIAL],v:SettlementStatus.PARTIAL},{l:SettlementStatusLabel[SettlementStatus.SETTLED],v:SettlementStatus.SETTLED}]" :key="s.v" :label="s.l" :value="s.v"/></el-select></el-form-item>
      <el-form-item label="单号"><el-input v-model="query.billNo" placeholder="单据号" clearable @keyup.enter="query_"/></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="query_">查询</el-button>
        <el-button :icon="'Refresh'" @click="reset_">重置</el-button>
      </div>
      </div>
      </template>
    </el-card>

    <!-- 按客户汇总（2026-09-29 用户口径「汇总要显示在应收管理里」）：
         客户/应收总额/已收/未收/逾期金额/单据数 + 「详情」进客户应收工作台。
         本表合计 870px ≤ 948px ✓ 一行显示完；客户是**合作方列**（家规：不得省略号）。
         口径见 ReceivableQuery.customerSummary（未结清 + 排除预收台账 ADVANCE；逾期 = due_date < 今天）。 -->
    <el-card v-if="view === 'summary'" shadow="never" class="table-card">
      <el-table v-loading="summaryLoading" :data="summaryData" border stripe @row-click="goCustomerDetail">
        <el-table-column label="客户" min-width="180" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="goCustomerDetail(row)">{{ row.customerName || cName(row.customerId) || '—' }}</el-button></template>
        </el-table-column>
        <el-table-column label="应收总额" width="120" align="right"><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
        <el-table-column label="已收" width="120" align="right"><template #default="{row}"><span style="color:var(--app-color-success)">{{ fmt(row.paidAmount) }}</span></template></el-table-column>
        <el-table-column label="未收" width="120" align="right"><template #default="{row}"><span style="color:var(--app-color-warning);font-weight:600">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="逾期金额" width="130" align="right"><template #default="{row}"><span :style="{color: Number(row.overdueAmount)>0?'var(--app-color-danger)':'var(--app-text-secondary)', fontWeight: Number(row.overdueAmount)>0?600:400}">{{ fmt(row.overdueAmount) }}</span></template></el-table-column>
        <!-- 单据数 + 无到期日张数：实测客户侧台账到期日普遍为空 ⇒ 必须解释"为什么逾期是 0"（否则像算错） -->
        <!-- 2026-09-29：90→**116**（实测「1」+「(1 无到期日)」需 111，90 会被省略号截断）；
             腾挪来源：应收总额/已收/未收 各 126→120（金额正文最长 ~57px，120 内完整）。
             合计 = 180+120+120+120+130+116+84 = 870 ≤ 948 ✓ -->
        <el-table-column label="未结清单据" width="116" align="center">
          <template #default="{row}">
            <span>{{ row.billCount }}</span>
            <el-tooltip v-if="Number(row.noDueCount) > 0" :content="`其中 ${row.noDueCount} 张未约定到期日，未计入逾期金额`" placement="top">
              <span style="margin-left:4px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">({{ row.noDueCount }} 无到期日)</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="84" align="center" fixed="right">
          <template #default="{row}"><el-button type="primary" link @click.stop="goCustomerDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!summaryLoading && summaryData.length === 0" description="暂无客户应收数据" />
    </el-card>

    <el-card v-else shadow="never" class="table-card">
      <!-- 2026-09-23：详情改独立页 ⇒ 点整行 / 行内「详情」都跳转（与账单页现状一致） -->
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1050px > 内容区 956px
           ⇒ 横向滚动 94px。收窄为合计 858px。
           2026-09-26 B6（用户口径「数据显示完整 + 单据号/往来单位可点」）：
           ①单据号 min112→**140**（AR- 单号 14 位以上，原被截断）并做成链接进应收详情；
           ②往来单位：原**只有供应商分支可点**（goSubject），补上客户分支 ⇒ 客户/供应商都能点进各自详情；
           合计 = 140+110+96+100+96+96+96+76+76 = **886** ✓ -->
      <el-table v-loading="loading" :data="data" border stripe @row-click="(row: any) => goDetail(row)">
        <el-table-column label="单据号" min-width="140" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.billNo }}</el-button></template>
        </el-table-column>
        <el-table-column label="往来单位" min-width="110" show-overflow-tooltip>
          <template #default="{row}">
            <el-link v-if="row.subjectType === SubjectType.SUPPLIER && row.supplierId" type="primary" underline="never" @click.stop="goSubject(row)">{{ subjectName(row) }}</el-link>
            <el-link v-else-if="row.customerId" type="primary" underline="never" @click.stop="router.push(`/inventory/customer/detail/${row.customerId}`)">{{ subjectName(row) }}</el-link>
            <span v-else>{{ subjectName(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="96" show-overflow-tooltip><template #default="{row}">{{ sourceBillTypeLabel(row.sourceBillType) }}</template></el-table-column>
        <el-table-column prop="amount" label="应收金额" width="100" align="right" show-overflow-tooltip><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column prop="paidAmount" label="已收" width="96" align="right" show-overflow-tooltip><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" label="未收" width="96" align="right" show-overflow-tooltip><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="96" align="center"/>
        <el-table-column label="状态" width="76" align="center"><template #default="{row}"><el-tag :type="stType(row.status)">{{ SettlementStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="76" align="center" fixed="right"><template #default="{row}"><el-button type="primary" link @click.stop="goDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="load" @current-change="load"/></div>
    </el-card>

  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
