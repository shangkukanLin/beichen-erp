<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listCustomers, type Customer } from '@/api/customer'
import request from '@/utils/request'
import { getBillPage, getBillItems, generateBill, type FinanceBill, type FinanceBillItem } from '@/api/finance'

const query = reactive({ billType: '应收', partnerId: '' as string|number })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceBill[]>([])
const customers = ref<Customer[]>([])
const suppliers = ref<{id:number;name:string}[]>([])

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.billType) p.billType = query.billType
    if (query.partnerId) p.partnerId = query.partnerId
    const res = await getBillPage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
async function loadOpts() {
  try { customers.value = await listCustomers() || [] } catch {}
  try { const r = await request.get('/supplier/page',{params:{pageSize:200}}); suppliers.value = r?.records || [] } catch {}
}
onMounted(() => { loadOpts(); loadData() })
function query_() { page.pageNum = 1; loadData() }
function reset_() { query.partnerId = ''; page.pageNum = 1; loadData() }
function partnerName(id?: number) {
  if (query.billType === '应收') return customers.value.find(x => x.id === id)?.name || ''
  return suppliers.value.find(x => x.id === id)?.name || ''
}
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

const genForm = reactive({ billType: '应收', partnerId: undefined as number|undefined, partnerName: '', periodStart: '', periodEnd: '' })
const genLoading = ref(false)
const genDialog = ref(false)

function onBillTypeChange() { genForm.partnerId = undefined; genForm.partnerName = '' }
async function onPartnerSelect(id: number) {
  if (genForm.billType === '应收') { const c = customers.value.find(x => x.id === id); genForm.partnerName = c?.name || '' }
  else { const s = suppliers.value.find(x => x.id === id); genForm.partnerName = s?.name || '' }
}
async function handleGenerate() {
  if (!genForm.partnerId) { ElMessage.warning('请选择往来单位'); return }
  if (!genForm.periodStart || !genForm.periodEnd) { ElMessage.warning('请选择账期'); return }
  genLoading.value = true
  try {
    const res = await generateBill(genForm)
    ElMessage.success(`账单「${res.billNo}」生成成功，共${fmt(res.totalAmount)}元`)
    genDialog.value = false; loadData()
  } catch {} finally { genLoading.value = false }
}
const detailVisible = ref(false)
const detail = ref<FinanceBill>({})
const detailItems = ref<FinanceBillItem[]>([])
async function handleDetail(row: FinanceBill) { detail.value = { ...row }; try { detailItems.value = await getBillItems(row.id as number) || [] } catch {}; detailVisible.value = true }
</script>
<template>
  <div class="p">
    <el-card shadow="never"><el-form :inline="true" :model="query" class="qf">
      <el-form-item label="类型"><el-select v-model="query.billType" style="width:120px"><el-option label="应收" value="应收"/><el-option label="应付" value="应付"/></el-select></el-form-item>
      <el-form-item label="往来单位"><el-select v-model="query.partnerId" placeholder="全部" clearable filterable style="width:160px"><el-option v-for="c in (query.billType==='应收'?customers:suppliers)" :key="c.id" :label="(c as any).name||(c as any).companyName" :value="c.id"/></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="query_">查询</el-button><el-button @click="reset_">重置</el-button><el-button type="success" @click="genDialog=true">生成账单</el-button></el-form-item>
    </el-form></el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column type="index" width="55" align="center"/>
        <el-table-column prop="billNo" label="账单号" min-width="140"/>
        <el-table-column label="类型" width="70" align="center"><template #default="{row}"><el-tag :type="row.billType==='应收'?'':'warning'">{{ row.billType }}</el-tag></template></el-table-column>
        <el-table-column prop="partnerName" label="往来单位" min-width="140"/>
        <el-table-column prop="periodStart" label="账期起" width="120" align="center"/>
        <el-table-column prop="periodEnd" label="账期止" width="120" align="center"/>
        <el-table-column prop="totalAmount" label="总额" width="120" align="right"><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
        <el-table-column prop="paidAmount" label="已收付" width="120" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" label="未收付" width="120" align="right"><template #default="{row}"><span style="color:#f56c6c">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="操作" width="80" align="center"><template #default="{row}"><el-button type="primary" link @click="handleDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <div class="pg"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>

    <el-dialog v-model="genDialog" title="生成账单" width="550px">
      <el-form :model="genForm" label-width="90px">
        <el-form-item label="类型"><el-select v-model="genForm.billType" style="width:100%" @change="onBillTypeChange"><el-option label="应收" value="应收"/><el-option label="应付" value="应付"/></el-select></el-form-item>
        <el-form-item label="往来单位"><el-select v-model="genForm.partnerId" placeholder="请选择" filterable style="width:100%" @change="onPartnerSelect"><el-option v-for="c in (genForm.billType==='应收'?customers:suppliers)" :key="c.id" :label="(c as any).name||(c as any).companyName" :value="c.id"/></el-select></el-form-item>
        <el-form-item label="账期起"><el-date-picker v-model="genForm.periodStart" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item label="账期止"><el-date-picker v-model="genForm.periodEnd" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="genDialog=false">取消</el-button><el-button type="primary" :loading="genLoading" @click="handleGenerate">生成</el-button></template>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="账单详情" size="50%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="账单号">{{ detail.billNo }}</el-descriptions-item>
        <el-descriptions-item label="类型"><el-tag :type="detail.billType==='应收'?'':'warning'">{{ detail.billType }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ detail.partnerName }}</el-descriptions-item>
        <el-descriptions-item label="账期">{{ detail.periodStart }} ~ {{ detail.periodEnd }}</el-descriptions-item>
        <el-descriptions-item label="总额">{{ fmt(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已收付">{{ fmt(detail.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="未收付"><span style="color:#f56c6c">{{ fmt(detail.unpaidAmount) }}</span></el-descriptions-item>
      </el-descriptions>
      <el-divider>明细</el-divider>
      <el-table :data="detailItems" border>
        <el-table-column type="index" width="50" align="center"/>
        <el-table-column prop="sourceBillType" label="来源类型" width="90"/>
        <el-table-column prop="sourceBillNo" label="来源单号" min-width="150"/>
        <el-table-column prop="amount" label="金额" width="110" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column prop="paidAmount" label="已收付" width="110" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" label="未收付" width="110" align="right"><template #default="{row}"><span style="color:#f56c6c">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="120" align="center"/>
      </el-table>
    </el-drawer>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
