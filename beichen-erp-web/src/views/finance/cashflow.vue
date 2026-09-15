<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { getCashflowPage, getAccountPage, type FinanceCashflow, type FinanceAccount } from '@/api/finance'

// 资金流水（资金账户已拆分为独立子菜单 /finance/account，账户下拉仅用于筛选）
const fquery = reactive({ accountId: undefined as number|undefined, flowType: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceCashflow[]>([])
// 流水类型：后端存 code（英文），前端展示 label（中文）
const flowTypes = [
  { label: '收款', value: 'RECEIPT' },
  { label: '付款', value: 'PAYMENT' },
  { label: '费用支出', value: 'EXPENSE' },
  { label: '期初', value: 'OPENING' },
  { label: '收款冲正', value: 'RECEIPT_REVERSE' },
  { label: '付款冲正', value: 'PAYMENT_REVERSE' },
  { label: '费用冲正', value: 'EXPENSE_REVERSE' },
]
const flowTypeLabelMap: Record<string, string> = {
  RECEIPT: '收款', PAYMENT: '付款', OPENING: '期初',
  RECEIPT_REVERSE: '收款冲正', PAYMENT_REVERSE: '付款冲正',
  EXPENSE: '费用支出', EXPENSE_REVERSE: '费用冲正',
}
function flowTypeLabel(code?: string) { return code ? (flowTypeLabelMap[code] ?? code) : '' }

async function loadFlow() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (fquery.accountId) p.accountId = fquery.accountId
    if (fquery.flowType) p.flowType = fquery.flowType
    const res = await getCashflowPage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
function fq_() { page.pageNum = 1; loadFlow() }
function fr_() { fquery.accountId = undefined; fquery.flowType = ''; page.pageNum = 1; loadFlow() }
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

// 账户下拉（仅筛选用）
const accounts = ref<FinanceAccount[]>([])
async function loadAccounts() { try { const r = await getAccountPage({pageSize:200}); accounts.value = r?.records || [] } catch {} }

onMounted(() => { loadFlow(); loadAccounts() })

</script>
<template>
  <div class="p">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="fquery" class="qf">
        <el-form-item label="账户"><el-select v-model="fquery.accountId" placeholder="全部" clearable style="width:150px"><el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id ?? ''"/></el-select></el-form-item>
        <el-form-item label="类型"><el-select v-model="fquery.flowType" placeholder="全部" clearable style="width:130px"><el-option v-for="t in flowTypes" :key="t.value" :label="t.label" :value="t.value"/></el-select></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="fq_">查询</el-button>
        <el-button :icon="'Refresh'" @click="fr_">重置</el-button>
      </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column prop="flowNo" label="流水号" width="150"/>
        <el-table-column label="时间" width="170"><template #default="{row}">{{ $fmtDate(row.createTime) }}</template></el-table-column>
        <el-table-column prop="accountName" label="账户" min-width="120"/>
        <el-table-column label="类型" width="100" align="center"><template #default="{row}"><el-tag :type="row.flowType==='RECEIPT'||row.flowType==='OPENING'?'success':'danger'">{{flowTypeLabel(row.flowType)}}</el-tag></template></el-table-column>
        <el-table-column label="收入" width="120" align="right"><template #default="{row}"><span style="color:var(--app-color-success)">{{fmt(row.income)}}</span></template></el-table-column>
        <el-table-column label="支出" width="120" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{fmt(row.expense)}}</span></template></el-table-column>
        <el-table-column prop="balance" label="余额" width="130" align="right"><template #default="{row}">{{fmt(row.balance)}}</template></el-table-column>
        <el-table-column prop="relatedBillNo" label="关联单据" min-width="150"/>
      </el-table>
      <div class="pg"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadFlow" @current-change="loadFlow"/></div>
    </el-card>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
