<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import request from '@/utils/request'
import { getPaymentPage, getPaymentItems, createPayment, auditPayment, cancelPayment, getUnpaidPayables, type FinancePayment, type FinancePaymentItem } from '@/api/finance'

const query = reactive({ supplierId: '' as string|number, status: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinancePayment[]>([])
const suppliers = ref<{id:number;name:string}[]>([])
const accounts = ref<{id:number;accountName:string}[]>([])

const statusOpts = [{l:'草稿',v:'草稿'},{l:'已审核',v:'已审核'},{l:'已作废',v:'已作废'}]

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.supplierId) p.supplierId = query.supplierId
    if (query.status) p.status = query.status
    const res = await getPaymentPage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
async function loadOpts() {
  try { const r = await request.get('/supplier/page',{params:{pageSize:200}}); suppliers.value = r?.records || [] } catch {}
  try { const r = await request.get('/finance/account/list'); accounts.value = r || [] } catch {}
}
onMounted(() => { loadOpts(); loadData() })
function query_() { page.pageNum = 1; loadData() }
function reset_() { query.supplierId = ''; query.status = ''; page.pageNum = 1; loadData() }
function sName(id?: number) { return suppliers.value.find(x => x.id === id)?.name || '' }
function aName(id?: number) { return accounts.value.find(x => x.id === id)?.accountName || '' }
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string) { if (s === '草稿') return 'info'; if (s === '已审核') return 'success'; if (s === '已作废') return 'danger'; return '' }

const dVisible = ref(false)
const dTitle = ref('新增付款单')
const dForm = reactive<FinancePayment>({ supplierId: undefined, accountId: undefined, paymentDate: '', remark: '' })
const dItems = ref<FinancePaymentItem[]>([])
const unpaid = ref<{id:number;billNo:string;unpaidAmount:number;sourceBillType:string;dueDate:string}[]>([])
const dLoading = ref(false)

function resetD() { Object.assign(dForm, { id: undefined, supplierId: undefined, accountId: undefined, paymentDate: '', remark: '' }); dItems.value = []; unpaid.value = [] }
function handleAdd() { resetD(); dTitle.value = '新增付款单'; dVisible.value = true }
async function handleEdit(row: FinancePayment) {
  resetD(); Object.assign(dForm, row); dTitle.value = '编辑付款单'; dVisible.value = true
  try { dItems.value = await getPaymentItems(row.id as number) || [] } catch {}
  if (row.supplierId) { try { unpaid.value = await getUnpaidPayables(row.supplierId) || [] } catch {} }
}
async function onSupplierChange(id: number) { try { unpaid.value = await getUnpaidPayables(id) || [] } catch { unpaid.value = [] } }
function addItem() { dItems.value.push({ payableId: undefined, payableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { dItems.value.splice(i, 1) }
function onPayableChange(val: number, row: FinancePaymentItem) { const r = unpaid.value.find(x => x.id === val); if (r) { row.payableId = r.id; row.payableBillNo = r.billNo; row.thisAmount = r.unpaidAmount } }

async function handleSubmit() {
  if (!dForm.supplierId) { ElMessage.warning('请选择供应商'); return }
  if (!dForm.accountId) { ElMessage.warning('请选择付款账户'); return }
  if (dItems.value.length === 0) { ElMessage.warning('请添加核销明细'); return }
  dLoading.value = true
  try { const payload = { payment: { ...dForm }, items: dItems.value }; await createPayment(payload); ElMessage.success('保存成功'); dVisible.value = false; loadData() } catch {} finally { dLoading.value = false }
}
async function handleAudit(row: FinancePayment) {
  try { await ElMessageBox.confirm(`确认审核付款单「${row.code}」？将核销应付并扣减账户余额`, '提示', { type: 'warning' })
    await auditPayment(row.id as number); ElMessage.success('审核成功，已核销应付并更新资金'); loadData() } catch {}
}
async function handleCancel(row: FinancePayment) {
  try { await ElMessageBox.confirm(`确认作废付款单「${row.code}」？`, '提示', { type: 'warning' })
    await cancelPayment(row.id as number); ElMessage.success('已作废'); loadData() } catch {}
}
const detailVisible = ref(false)
const detail = ref<FinancePayment>({})
const detailItems = ref<FinancePaymentItem[]>([])
async function handleDetail(row: FinancePayment) { detail.value = { ...row }; try { detailItems.value = await getPaymentItems(row.id as number) || [] } catch {}; detailVisible.value = true }
</script>
<template>
  <div class="p">
    <el-card shadow="never"><el-form :inline="true" :model="query" class="qf">
      <el-form-item label="供应商"><el-select v-model="query.supplierId" placeholder="全部" clearable filterable style="width:160px"><el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:120px"><el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v"/></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="query_">查询</el-button><el-button @click="reset_">重置</el-button><el-button type="success" @click="handleAdd">新增付款</el-button></el-form-item>
    </el-form></el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column type="index" width="55" align="center"/>
        <el-table-column prop="code" label="单号" min-width="150"/>
        <el-table-column label="供应商" min-width="140"><template #default="{row}">{{ sName(row.supplierId) }}</template></el-table-column>
        <el-table-column label="账户" min-width="130"><template #default="{row}">{{ aName(row.accountId) }}</template></el-table-column>
        <el-table-column prop="paymentDate" label="日期" width="120" align="center"/>
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
          <el-col :span="12"><el-form-item label="供应商" required><el-select v-model="dForm.supplierId" placeholder="请选择" filterable style="width:100%" @change="onSupplierChange"><el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款账户" required><el-select v-model="dForm.accountId" placeholder="请选择" filterable style="width:100%"><el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款日期"><el-date-picker v-model="dForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="dForm.remark" type="textarea" :rows="2"/></el-form-item></el-col>
        </el-row>
        <el-divider>核销明细（从供应商未结清应付选择）</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" @click="addItem">添加核销项</el-button></div>
        <el-table :data="dItems" border>
          <el-table-column type="index" width="50" align="center"/>
          <el-table-column label="应付单据" min-width="220"><template #default="{row}"><el-select v-model="row.payableId" placeholder="选择应付单据" filterable style="width:100%" @change="(v:number)=>onPayableChange(v,row)"><el-option v-for="u in unpaid" :key="u.id" :label="`${u.billNo} (未付:${u.unpaidAmount},到期:${u.dueDate})`" :value="u.id"/></el-select></template></el-table-column>
          <el-table-column label="核销金额" width="140"><template #default="{row}"><el-input-number v-model="row.thisAmount" :min="0" :precision="2" controls-position="right" style="width:100%"/></template></el-table-column>
          <el-table-column label="操作" width="70" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer><el-button @click="dVisible=false">取消</el-button><el-button type="primary" :loading="dLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="付款单详情" size="50%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ sName(detail.supplierId) }}</el-descriptions-item>
        <el-descriptions-item label="账户">{{ aName(detail.accountId) }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.paymentDate }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmt(detail.amount) }}</el-descriptions-item>
      </el-descriptions>
      <el-divider>核销明细</el-divider>
      <el-table :data="detailItems" border><el-table-column type="index" width="50" align="center"/><el-table-column prop="payableBillNo" label="应付单据" min-width="150"/><el-table-column prop="thisAmount" label="核销金额" width="130" align="right"><template #default="{row}">{{ fmt(row.thisAmount) }}</template></el-table-column></el-table>
    </el-drawer>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
