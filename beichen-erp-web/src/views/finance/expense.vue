<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getExpensePage, createExpense, auditExpense, cancelExpense, getAccountPage, type FinanceExpense, type FinanceAccount } from '@/api/finance'
import { DocStatusLabel, DocStatusTag } from '@/api/common'
import { ExpenseTypeLabel as EXPENSE_TYPE_LABELS } from '@/api/enums'

// 费用管理：审核后扣减资金账户并生成「费用支出」流水；反审核冲回（模式与收款单一致）
const router = useRouter()
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
/** 详情（2026-09-24 新增）：草稿态在详情页就地改+存，撤销类的反审核也在那里 */
function goDetail(row: FinanceExpense) { router.push(`/finance/expense/detail/${row.id}`) }
/* 2026-09-24（用户口径）：列表弹窗只保留「新增」；草稿编辑已收进详情页 ⇒ handleEdit / updateExpense 分支一并删除
   （updateExpense 现仅在详情页使用）。 */
async function save() {
  if (!form.expenseType) { ElMessage.warning('请选择费用类型'); return }
  if (!form.amount || form.amount <= 0) { ElMessage.warning('费用金额必须大于 0'); return }
  if (!form.accountId) { ElMessage.warning('请选择支出账户'); return }
  try { await createExpense(form); ElMessage.success('已新增'); dialog.value = false; loadData() } catch {}
}
async function audit(row: any) {
  try { await ElMessageBox.confirm(`确认审核费用单 ${row.expenseNo}？审核后将从「${row.accountName}」扣款 ${row.amount} 元`, '审核确认', { type: 'warning' }) } catch { return }
  try { await auditExpense(row.id); ElMessage.success('已审核'); loadData() } catch {}
}
/* 2026-09-24（用户口径）：反审核已移入详情页 —— 它会生成「费用冲正」流水把资金冲回账户（撤销类操作，
   风险高、原因只在单据上下文里说得清），列表只保留高频的审核。 */
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
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1010px > 内容区 956px
           ⇒ 横向滚动 54px。收窄为合计 876px（支出账户/备注保持 min-width，宽屏自动吃余量）。 -->
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column prop="expenseNo" label="费用单号" width="120" show-overflow-tooltip/>
        <el-table-column prop="expenseType" label="费用类型" width="96"><template #default="{row}"><el-tag size="small">{{ EXPENSE_TYPE_LABELS[row.expenseType] || row.expenseType }}</el-tag></template></el-table-column>
        <el-table-column prop="amount" label="金额" width="100" align="right" show-overflow-tooltip><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.amount) }}</span></template></el-table-column>
        <el-table-column label="费用日期" width="100"><template #default="{row}">{{ fmtDate(row.expenseDate) }}</template></el-table-column>
        <el-table-column prop="accountName" label="支出账户" min-width="110" show-overflow-tooltip/>
        <el-table-column label="状态" width="76" align="center"><template #default="{row}"><el-tag :type="DocStatusTag[row.status]" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip/>
        <!-- 2026-09-24（用户口径）：编辑与反审核都收进详情页（详情草稿态可就地改+存）⇒ 操作列 174→132。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link @click="goDetail(row)">详情</el-button>
            <el-button v-if="row.status==='DRAFT'" type="success" link @click="audit(row)">审核</el-button>
            <el-button v-if="row.status==='DRAFT'" type="danger" link @click="cancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>
    <el-dialog v-model="dialog" :title="dialogTitle" width="var(--app-dialog-sm)" :close-on-click-modal="false">
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
