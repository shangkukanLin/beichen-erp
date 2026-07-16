<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { listCustomers, type Customer } from '@/api/customer'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'

const router = useRouter()
import { getReceiptPage, getReceiptItems, createReceipt, auditReceipt, cancelReceipt, getUnpaidReceivables, type FinanceReceipt, type FinanceReceiptItem } from '@/api/finance'

const query = reactive({ customerId: '' as string|number, status: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceReceipt[]>([])
const customers = ref<Customer[]>([])
const accounts = ref<{id:number;accountName:string}[]>([])

const statusOpts = [{l:'草稿',v:'草稿'},{l:'已审核',v:'已审核'},{l:'已作废',v:'已作废'}]

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.customerId) p.customerId = query.customerId
    if (query.status) p.status = query.status
    const res = await getReceiptPage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
async function loadOpts() {
  try { customers.value = await listCustomers() || [] } catch {}
  try { const r = await request.get('/finance/account/list'); accounts.value = r || [] } catch {}
}
onMounted(() => { loadOpts(); loadData() })
function query_() { page.pageNum = 1; loadData() }
function reset_() { query.customerId = ''; query.status = ''; page.pageNum = 1; loadData() }
function cName(id?: number) { return customers.value.find(x => x.id === id)?.name || '' }
function aName(id?: number) { return accounts.value.find(x => x.id === id)?.accountName || '' }
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string) { if (s === '草稿') return 'info'; if (s === '已审核') return 'success'; if (s === '已作废') return 'danger'; return '' }

const dVisible = ref(false)
const dTitle = ref('新增收款单')
const dForm = reactive<FinanceReceipt>({ customerId: undefined, accountId: undefined, receiptDate: '', remark: '' })
const dItems = ref<FinanceReceiptItem[]>([])
const unpaid = ref<{id:number;billNo:string;unpaidAmount:number;sourceBillType:string;dueDate:string}[]>([])
const dLoading = ref(false)

function resetD() { Object.assign(dForm, { id: undefined, customerId: undefined, accountId: undefined, receiptDate: '', remark: '' }); dItems.value = []; unpaid.value = [] }
function handleAdd() { resetD(); dTitle.value = '新增收款单'; dVisible.value = true }
async function handleEdit(row: FinanceReceipt) {
  resetD(); Object.assign(dForm, row); dTitle.value = '编辑收款单'; dVisible.value = true
  try { dItems.value = await getReceiptItems(row.id as number) || [] } catch {}
  if (row.customerId) { try { unpaid.value = await getUnpaidReceivables(row.customerId) || [] } catch {} }
}
async function onCustomerChange(id: number) {
  try { unpaid.value = await getUnpaidReceivables(id) || [] } catch { unpaid.value = [] }
}
function addItem() { dItems.value.push({ receivableId: undefined, receivableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { dItems.value.splice(i, 1) }
function onReceivableChange(val: number, row: FinanceReceiptItem) {
  const r = unpaid.value.find(x => x.id === val)
  if (r) { row.receivableId = r.id; row.receivableBillNo = r.billNo; row.thisAmount = r.unpaidAmount }
}
async function handleSubmit() {
  if (!dForm.customerId) { ElMessage.warning('请选择客户'); return }
  if (!dForm.accountId) { ElMessage.warning('请选择收款账户'); return }
  if (dItems.value.length === 0) { ElMessage.warning('请添加核销明细'); return }
  dLoading.value = true
  try {
    const payload = { receipt: { ...dForm }, items: dItems.value }
    await createReceipt(payload); ElMessage.success('保存成功')
    dVisible.value = false; loadData()
  } catch {} finally { dLoading.value = false }
}
async function handleAudit(row: FinanceReceipt) {
  try { await ElMessageBox.confirm(`确认审核收款单「${row.code}」？将核销应收并更新账户余额`, '提示', { type: 'warning' })
    await auditReceipt(row.id as number); ElMessage.success('审核成功，已核销应收并更新资金'); loadData() } catch {}
}
async function handleCancel(row: FinanceReceipt) {
  try { await ElMessageBox.confirm(`确认作废收款单「${row.code}」？`, '提示', { type: 'warning' })
    await cancelReceipt(row.id as number); ElMessage.success('已作废'); loadData() } catch {}
}
const detailVisible = ref(false)
const detail = ref<FinanceReceipt>({})
const detailItems = ref<FinanceReceiptItem[]>([])
async function handleDetail(row: FinanceReceipt) { detail.value = { ...row }
  try { detailItems.value = await getReceiptItems(row.id as number) || [] } catch { detailItems.value = [] }
  detailVisible.value = true }
</script>
<template>
  <div class="p">
    <el-card shadow="never"><el-form :inline="true" :model="query" class="qf">
      <el-form-item label="客户"><el-select v-model="query.customerId" placeholder="全部" clearable filterable style="width:160px" @change="(v: any) => { if (v === ADD_MARKER) { query.customerId = ''; router.push('/inventory/customer'); return } }"><el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id"/><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:120px"><el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v"/></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="query_">查询</el-button><el-button @click="reset_">重置</el-button><el-button type="success" @click="handleAdd">新增收款</el-button></el-form-item>
    </el-form></el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column type="index" width="55" align="center"/>
        <el-table-column prop="code" label="单号" min-width="150"/>
        <el-table-column label="客户" min-width="140"><template #default="{row}">{{ cName(row.customerId) }}</template></el-table-column>
        <el-table-column label="账户" min-width="130"><template #default="{row}">{{ aName(row.accountId) }}</template></el-table-column>
        <el-table-column prop="receiptDate" label="日期" width="120" align="center"/>
        <el-table-column prop="amount" label="金额" width="120" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="stType(row.status)">{{row.status}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{row}"><el-button type="primary" link @click="handleDetail(row)">详情</el-button><el-button v-if="row.status==='草稿'" type="success" link @click="handleAudit(row)">审核</el-button><el-button v-if="row.status==='草稿'" type="warning" link @click="handleEdit(row)">编辑</el-button><el-button v-if="row.status==='草稿'" type="danger" link @click="handleCancel(row)">作废</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pg"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>
    <el-dialog v-model="dVisible" :title="dTitle" width="850px" :close-on-click-modal="false">
      <el-form :model="dForm" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="客户" required><el-select v-model="dForm.customerId" placeholder="请选择" filterable style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { dForm.customerId = undefined; router.push('/inventory/customer'); return } onCustomerChange() }"><el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id"/><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收款账户" required><el-select v-model="dForm.accountId" placeholder="请选择" filterable style="width:100%"><el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收款日期"><el-date-picker v-model="dForm.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="dForm.remark" type="textarea" :rows="2"/></el-form-item></el-col>
        </el-row>
        <el-divider>核销明细（从客户未结清应收选择）</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" @click="addItem">添加核销项</el-button></div>
        <el-table :data="dItems" border>
          <el-table-column type="index" width="50" align="center"/>
          <el-table-column label="应收单据" min-width="220"><template #default="{row}"><el-select v-model="row.receivableId" placeholder="选择应收单据" filterable style="width:100%" @change="(v:number)=>onReceivableChange(v,row)"><el-option v-for="u in unpaid" :key="u.id" :label="`${u.billNo} (未收:${u.unpaidAmount},到期:${u.dueDate})`" :value="u.id"/></el-select></template></el-table-column>
          <el-table-column label="核销金额" width="140"><template #default="{row}"><el-input-number v-model="row.thisAmount" :min="0" :precision="2" controls-position="right" style="width:100%"/></template></el-table-column>
          <el-table-column label="操作" width="70" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer><el-button @click="dVisible=false">取消</el-button><el-button type="primary" :loading="dLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="收款单详情" size="50%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="客户">{{ cName(detail.customerId) }}</el-descriptions-item>
        <el-descriptions-item label="账户">{{ aName(detail.accountId) }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.receiptDate }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmt(detail.amount) }}</el-descriptions-item>
      </el-descriptions>
      <el-divider>核销明细</el-divider>
      <el-table :data="detailItems" border><el-table-column type="index" width="50" align="center"/><el-table-column prop="receivableBillNo" label="应收单据" min-width="150"/><el-table-column prop="thisAmount" label="核销金额" width="130" align="right"><template #default="{row}">{{ fmt(row.thisAmount) }}</template></el-table-column></el-table>
    </el-drawer>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
