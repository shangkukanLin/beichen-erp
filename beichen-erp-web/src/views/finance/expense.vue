<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getExpensePage, createExpense, updateExpense, auditExpense, unAuditExpense, cancelExpense, getAccountPage, type FinanceExpense, type FinanceAccount } from '@/api/finance'
import { DocStatusLabel, DocStatusTag } from '@/api/common'
import { ExpenseTypeLabel as EXPENSE_TYPE_LABELS } from '@/api/enums'

// 费用管理：审核后扣减资金账户并生成「费用支出」流水；反审核冲回（模式与收款单一致）
const query = reactive({ expenseType: '', status: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceExpense[]>([])
const accounts = ref<FinanceAccount[]>([])
const dialog = ref(false)
const dialogTitle = ref('新增费用')
const form = reactive<FinanceExpense>({ id: undefined, expenseType: 'OFFICE', amount: undefined, expenseDate: localDate(), accountId: undefined, remark: '' })

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.expenseType) p.expenseType = query.expenseType
    if (query.status) p.status = query.status
    const res = await getExpensePage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
async function loadAccounts() { try { const r = await getAccountPage({pageSize:200}); accounts.value = (r?.records || []).filter((a:any)=>a.status===1) } catch { accounts.value = [] } }
function handleAdd() { Object.assign(form, { id: undefined, expenseType: 'OFFICE', amount: undefined, expenseDate: localDate(), accountId: undefined, remark: '' }); dialogTitle.value = '新增费用'; dialog.value = true }
function handleEdit(row: FinanceExpense) { Object.assign(form, { id: row.id, expenseType: row.expenseType, amount: row.amount, expenseDate: row.expenseDate, accountId: row.accountId, remark: row.remark }); dialogTitle.value = '编辑费用'; dialog.value = true }
async function save() {
  if (!form.expenseType) { ElMessage.warning('请选择费用类型'); return }
  if (!form.amount || form.amount <= 0) { ElMessage.warning('费用金额必须大于 0'); return }
  if (!form.accountId) { ElMessage.warning('请选择支出账户'); return }
  try { if (form.id) { await updateExpense(form); ElMessage.success('修改成功') } else { await createExpense(form); ElMessage.success('新增成功') }; dialog.value = false; loadData() } catch {}
}
async function audit(row: any) {
  try { await ElMessageBox.confirm(`确认审核费用单 ${row.expenseNo}？审核后将从「${row.accountName}」扣款 ${row.amount} 元`, '审核确认', { type: 'warning' }) } catch { return }
  try { await auditExpense(row.id); ElMessage.success('审核成功'); loadData() } catch {}
}
async function unAudit(row: any) {
  try { await ElMessageBox.confirm('反审核将生成「费用冲正」流水把钱冲回账户，确认继续？', '反审核确认', { type: 'warning' }) } catch { return }
  try { await unAuditExpense(row.id); ElMessage.success('反审核成功'); loadData() } catch {}
}
async function cancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该费用单？', '作废确认', { type: 'warning' }) } catch { return }
  try { await cancelExpense(row.id); ElMessage.success('已作废'); loadData() } catch {}
}
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtDate(v?: string) { return v ? String(v).slice(0, 10) : '' }
onMounted(() => { loadData(); loadAccounts() })
</script>
<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="qf">
          <el-form-item label="费用类型">
            <el-select v-model="query.expenseType" placeholder="全部" clearable style="width:130px">
              <el-option v-for="(lb, code) in EXPENSE_TYPE_LABELS" :key="code" :label="lb" :value="code"/>
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
              <el-option v-for="(label, code) in DocStatusLabel" :key="code" :label="label" :value="code"/>
            </el-select>
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="page.pageNum=1;loadData()">查询</el-button>
          <el-button :icon="'Refresh'" @click="query.expenseType='';query.status='';page.pageNum=1;loadData()">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column prop="expenseNo" label="费用单号" width="150"/>
        <el-table-column prop="expenseType" label="费用类型" width="110"><template #default="{row}"><el-tag size="small">{{ EXPENSE_TYPE_LABELS[row.expenseType] || row.expenseType }}</el-tag></template></el-table-column>
        <el-table-column prop="amount" label="金额" width="130" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.amount) }}</span></template></el-table-column>
        <el-table-column label="费用日期" width="110"><template #default="{row}">{{ fmtDate(row.expenseDate) }}</template></el-table-column>
        <el-table-column prop="accountName" label="支出账户" min-width="120"/>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="DocStatusTag[row.status]" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip/>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{row}">
            <el-button v-if="row.status==='DRAFT'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status==='DRAFT'" type="success" link @click="audit(row)">审核</el-button>
            <el-button v-if="row.status==='AUDITED'" type="warning" link @click="unAudit(row)">反审核</el-button>
            <el-button v-if="row.status==='DRAFT'" type="danger" link @click="cancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>
    <el-dialog v-model="dialog" :title="dialogTitle" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-width="80px">
        <el-form-item required label="费用类型">
          <el-select v-model="form.expenseType" style="width:100%"><el-option v-for="(lb, code) in EXPENSE_TYPE_LABELS" :key="code" :label="lb" :value="code"/></el-select>
        </el-form-item>
        <el-form-item required label="金额"><el-input-number v-model="form.amount" :min="0.01" :precision="2" controls-position="right" style="width:100%"/></el-form-item>
        <el-form-item label="费用日期"><el-date-picker v-model="form.expenseDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item required label="支出账户">
          <el-select v-model="form.accountId" placeholder="请选择" style="width:100%">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName}（余额 ${fmt(a.balance)}）`" :value="a.id ?? ''"/>
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
