<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { getCashflowPage, getAccountPage, createAccount, updateAccount, type FinanceCashflow, type FinanceAccount, type PageResult } from '@/api/finance'

const tab = ref('cashflow')
// cashflow
const fquery = reactive({ accountId: undefined as number|undefined, flowType: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceCashflow[]>([])
const flowTypes = ['收款','付款','其他收入','费用支出'].map(t=>({label:t,value:t}))

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

// accounts
const accounts = ref<FinanceAccount[]>([])
const aDialog = ref(false)
const aForm = reactive<FinanceAccount>({ accountName: '', accountType: '银行', bankName: '', accountNo: '', balance: 0, status: 1 })
const aRef = ref<FormInstance>()
async function loadAccounts() { try { const r = await getAccountPage({pageSize:200}); accounts.value = r?.records || [] } catch {} }
function addAccount() { Object.assign(aForm, { id: undefined, accountName: '', accountType: '银行', bankName: '', accountNo: '', balance: 0, status: 1 }); aDialog.value = true }
function editAccount(row: FinanceAccount) { Object.assign(aForm, row); aDialog.value = true }
async function saveAccount() {
  try { if (aForm.id) { await updateAccount(aForm); ElMessage.success('修改成功') } else { await createAccount(aForm); ElMessage.success('新增成功') }; aDialog.value = false; loadAccounts() } catch {}
}

onMounted(() => { loadFlow(); loadAccounts() })
</script>
<template>
  <div class="p">
    <el-card shadow="never"><el-tabs v-model="tab">
      <el-tab-pane label="资金流水" name="cashflow">
        <el-form :inline="true" :model="fquery" class="qf">
          <el-form-item label="账户"><el-select v-model="fquery.accountId" placeholder="全部" clearable style="width:150px"><el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id"/></el-select></el-form-item>
          <el-form-item label="类型"><el-select v-model="fquery.flowType" placeholder="全部" clearable style="width:130px"><el-option v-for="t in flowTypes" :key="t.value" :label="t.label" :value="t.value"/></el-select></el-form-item>
          <el-form-item><el-button type="primary" @click="fq_">查询</el-button><el-button @click="fr_">重置</el-button></el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="data" border stripe>
          <el-table-column type="index" width="55" align="center"/>
          <el-table-column prop="flowNo" label="流水号" width="150"/>
          <el-table-column label="时间" width="170"><template #default="{row}">{{ $fmtDate(row.createTime) }}</template></el-table-column>
          <el-table-column prop="accountName" label="账户" min-width="120"/>
          <el-table-column label="类型" width="90" align="center"><template #default="{row}"><el-tag :type="row.flowType==='收款'||row.flowType==='其他收入'?'success':'danger'">{{row.flowType}}</el-tag></template></el-table-column>
          <el-table-column label="收入" width="120" align="right"><template #default="{row}"><span style="color:#67C23A">{{fmt(row.income)}}</span></template></el-table-column>
          <el-table-column label="支出" width="120" align="right"><template #default="{row}"><span style="color:#F56C6C">{{fmt(row.expense)}}</span></template></el-table-column>
          <el-table-column prop="balance" label="余额" width="130" align="right"><template #default="{row}">{{fmt(row.balance)}}</template></el-table-column>
          <el-table-column prop="relatedBillNo" label="关联单据" min-width="150"/>
        </el-table>
        <div class="pg"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadFlow" @current-change="loadFlow"/></div>
      </el-tab-pane>
      <el-tab-pane label="资金账户" name="accounts">
        <div style="margin-bottom:12px"><el-button type="success" @click="addAccount">新增账户</el-button></div>
        <el-table :data="accounts" border stripe>
          <el-table-column type="index" width="55" align="center"/>
          <el-table-column prop="accountName" label="账户名称" min-width="140"/>
          <el-table-column label="类型" width="80" align="center"><template #default="{row}"><el-tag>{{row.accountType}}</el-tag></template></el-table-column>
          <el-table-column prop="bankName" label="开户行" min-width="120"/>
          <el-table-column prop="accountNo" label="账号" min-width="150"/>
          <el-table-column prop="balance" label="余额" width="130" align="right"><template #default="{row}">{{fmt(row.balance)}}</template></el-table-column>
          <el-table-column label="操作" width="80" align="center"><template #default="{row}"><el-button type="primary" link @click="editAccount(row)">编辑</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs></el-card>

    <el-dialog v-model="aDialog" title="资金账户" width="500px">
      <el-form ref="aRef" :model="aForm" label-width="80px">
        <el-form-item label="名称"><el-input v-model="aForm.accountName"/></el-form-item>
        <el-form-item label="类型"><el-select v-model="aForm.accountType" style="width:100%"><el-option label="现金" value="现金"/><el-option label="银行" value="银行"/></el-select></el-form-item>
        <el-form-item label="开户行"><el-input v-model="aForm.bankName"/></el-form-item>
        <el-form-item label="账号"><el-input v-model="aForm.accountNo"/></el-form-item>
        <el-form-item label="余额"><el-input-number v-model="aForm.balance" :min="0" :precision="2" controls-position="right" style="width:100%"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="aDialog=false">取消</el-button><el-button type="primary" @click="saveAccount">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
