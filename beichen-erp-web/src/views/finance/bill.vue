<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import { getBillPage, getBillItems, generateBill, auditBill, unAuditBill, cancelBill, type FinanceBill, type FinanceBillItem } from '@/api/finance'
import { BillType, BillTypeLabel } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

// 账单状态 code → 中文 label（后端存 DocStatus code，前端展示中文）
const StatusLabel: Record<string, string> = DocStatusLabel
const StatusTag: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'primary'> = DocStatusTag

const query = reactive({ billType: BillType.RECEIVABLE, partnerId: '' as string|number })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceBill[]>([])
const customersOptions = ref<any[]>([])
const suppliersOptions = ref<any[]>([])

const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadCustomersOptions() {
  try { const r: any = await fetchCustomers(''); customersOptions.value = r?.records || [] } catch { customersOptions.value = [] }
}
async function loadSuppliersOptions() {
  try { const r: any = await fetchSuppliers(''); suppliersOptions.value = r?.records || [] } catch { suppliersOptions.value = [] }
}

// 往来单位下拉：按当前单据类型切换查询客户/供应商
const fetchPartner = (kw: string) => (query.billType === BillType.RECEIVABLE ? fetchCustomers(kw) : fetchSuppliers(kw))

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
onMounted(() => { loadCustomersOptions(); loadSuppliersOptions(); loadData() })
onActivated(() => { loadData() })
function query_() { page.pageNum = 1; loadData() }
function reset_() { query.partnerId = ''; page.pageNum = 1; loadData() }
function partnerName(id?: number) {
  if (query.billType === BillType.RECEIVABLE) return customersOptions.value.find(x => x.id === id)?.name || ''
  return suppliersOptions.value.find(x => x.id === id)?.name || ''
}
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

const genForm = reactive({ billType: BillType.RECEIVABLE, partnerId: undefined as number|undefined, partnerName: '', periodStart: '', periodEnd: '' })
const genLoading = ref(false)
const genDialog = ref(false)

function onBillTypeChange() { genForm.partnerId = undefined; genForm.partnerName = '' }
function onPartnerPick(rows: any[]) {
  genForm.partnerName = rows?.[0]?.name || ''
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
async function handleAudit(row: FinanceBill) { try { await auditBill(row.id as number); ElMessage.success('账单已审核'); loadData() } catch {} }
async function handleUnAudit(row: FinanceBill) { try { await unAuditBill(row.id as number); ElMessage.success('账单已反审核'); loadData() } catch {} }
async function handleCancel(row: FinanceBill) { try { await cancelBill(row.id as number); ElMessage.success('账单已作废'); loadData() } catch {} }
</script>
<template>
  <div class="p">
    <el-card shadow="never"><el-form :inline="true" :model="query" class="qf">
      <el-form-item label="类型"><el-select v-model="query.billType" style="width:120px"><el-option :label="BillTypeLabel[BillType.RECEIVABLE]" :value="BillType.RECEIVABLE"/><el-option :label="BillTypeLabel[BillType.PAYABLE]" :value="BillType.PAYABLE"/></el-select></el-form-item>
      <el-form-item label="往来单位"><RemoteSelect v-model="query.partnerId" :fetch="fetchPartner" placeholder="全部" style="width:160px" /></el-form-item>
      <el-form-item><el-button type="primary" @click="query_">查询</el-button><el-button @click="reset_">重置</el-button><el-button type="success" @click="genDialog=true">生成账单</el-button></el-form-item>
    </el-form></el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column type="index" width="55" align="center"/>
        <el-table-column prop="billNo" label="账单号" min-width="140"/>
        <el-table-column label="类型" width="70" align="center"><template #default="{row}"><el-tag :type="row.billType===BillType.RECEIVABLE?undefined:'warning'">{{ BillTypeLabel[row.billType] || row.billType }}</el-tag></template></el-table-column>
        <el-table-column prop="partnerName" label="往来单位" min-width="140"/>
        <el-table-column prop="periodStart" label="账期起" width="120" align="center"/>
        <el-table-column prop="periodEnd" label="账期止" width="120" align="center"/>
        <el-table-column prop="totalAmount" label="总额" width="120" align="right"><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
        <el-table-column prop="paidAmount" label="已收付" width="120" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" label="未收付" width="120" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="StatusTag[row.status] || 'info'" size="small">{{ StatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="200" align="center"><template #default="{row}">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button v-if="row.status===DocStatus.DRAFT" type="success" link @click="handleAudit(row)">审核</el-button>
          <el-button v-if="row.status===DocStatus.AUDITED" type="warning" link @click="handleUnAudit(row)">反审核</el-button>
          <el-button v-if="row.status!==DocStatus.CANCELLED" type="danger" link @click="handleCancel(row)">作废</el-button>
        </template></el-table-column>
      </el-table>
      <div class="pg"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>

    <el-dialog v-model="genDialog" title="生成账单" width="550px">
      <el-form :model="genForm" label-width="90px">
        <el-form-item label="类型"><el-select v-model="genForm.billType" style="width:100%" @change="onBillTypeChange"><el-option :label="BillTypeLabel[BillType.RECEIVABLE]" :value="BillType.RECEIVABLE"/><el-option :label="BillTypeLabel[BillType.PAYABLE]" :value="BillType.PAYABLE"/></el-select></el-form-item>
        <el-form-item label="往来单位"><RemoteSelect v-model="genForm.partnerId" :fetch="fetchPartner" placeholder="请选择" style="width:100%" @pick="onPartnerPick" /></el-form-item>
        <el-form-item label="账期起"><el-date-picker v-model="genForm.periodStart" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item label="账期止"><el-date-picker v-model="genForm.periodEnd" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="genDialog=false">取消</el-button><el-button type="primary" :loading="genLoading" @click="handleGenerate">生成</el-button></template>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="账单详情" size="50%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="账单号">{{ detail.billNo }}</el-descriptions-item>
        <el-descriptions-item label="类型"><el-tag :type="detail.billType===BillType.RECEIVABLE?undefined:'warning'">{{ BillTypeLabel[detail.billType ?? 0] || detail.billType }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ detail.partnerName }}</el-descriptions-item>
        <el-descriptions-item label="账期">{{ detail.periodStart }} ~ {{ detail.periodEnd }}</el-descriptions-item>
        <el-descriptions-item label="总额">{{ fmt(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已收付">{{ fmt(detail.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="未收付"><span style="color:var(--app-color-danger)">{{ fmt(detail.unpaidAmount) }}</span></el-descriptions-item>
      </el-descriptions>
      <el-divider>明细</el-divider>
      <el-table :data="detailItems" border>
        <el-table-column type="index" width="50" align="center"/>
        <el-table-column prop="sourceBillType" label="来源类型" width="90"/>
        <el-table-column prop="sourceBillNo" label="来源单号" min-width="150"/>
        <el-table-column prop="amount" label="金额" width="110" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column prop="paidAmount" label="已收付" width="110" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" label="未收付" width="110" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="120" align="center"/>
      </el-table>
    </el-drawer>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
