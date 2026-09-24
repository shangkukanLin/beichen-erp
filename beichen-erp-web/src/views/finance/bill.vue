<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBillPage, generateBill, auditBill, unAuditBill, cancelBill, type FinanceBill } from '@/api/finance'
import { BillType, BillTypeLabel, sourceBillTypeLabel, FINANCE_BILL_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

// 账单状态 code → 中文 label（后端存 DocStatus code，前端展示中文）
const StatusLabel: Record<string, string> = DocStatusLabel
const StatusTag: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'primary'> = DocStatusTag

const router = useRouter()
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
/**
 * 数据变动后刷新：置脏标志并重新拉取。
 * 脏标志供从其它页面切回本页时按需刷新——只在有变动时才刷，避免每次切换菜单都重新请求。
 */
function afterChange() {
  sessionStorage.setItem(FINANCE_BILL_DIRTY_KEY, '1')
  loadData()
}
onMounted(() => { loadCustomersOptions(); loadSuppliersOptions(); loadData() })
onActivated(() => {
  if (sessionStorage.getItem(FINANCE_BILL_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(FINANCE_BILL_DIRTY_KEY)
    loadData()
  }
})

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
    genDialog.value = false
    // 把列表筛选对齐到刚生成的账单并回到第一页：
    // 否则账单类型/往来单位与当前筛选不符、或正停在其他页时，新账单会被过滤掉看不见
    query.billType = genForm.billType
    query.partnerId = genForm.partnerId ?? ''
    page.pageNum = 1
    afterChange()
  } catch {} finally { genLoading.value = false }
}

// 详情已独立成页，列表不再用抽屉展示
function handleDetail(row: FinanceBill) { router.push(`/finance/bill/detail/${row.id}`) }
// 2026-09-20（F7-160）：审核 / 反审核 / 作废 补二次确认 —— 与同单据的 bill/detail.vue 口径一致。
// 列表里三个危险动作原先「点一下即执行」（审核/反审核会核销、冲销台账），误点代价高；
// 写法照搬 bill/detail.vue：confirm 与请求各自 try/catch（用户点「取消」不算失败），提示语带单号便于多行操作时辨认。
async function handleAudit(row: FinanceBill) {
  try { await ElMessageBox.confirm(`确认审核账单「${row.billNo}」？`, '审核确认', { type: 'warning' }) } catch { return }
  try { await auditBill(row.id as number); ElMessage.success('账单已审核'); afterChange() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function handleUnAudit(row: FinanceBill) {
  try { await ElMessageBox.confirm(`确认反审核账单「${row.billNo}」？`, '反审核确认', { type: 'warning' }) } catch { return }
  try { await unAuditBill(row.id as number); ElMessage.success('账单已反审核'); afterChange() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function handleCancel(row: FinanceBill) {
  try { await ElMessageBox.confirm(`确认作废账单「${row.billNo}」？`, '作废确认', { type: 'warning' }) } catch { return }
  try { await cancelBill(row.id as number); ElMessage.success('账单已作废'); afterChange() }
  catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}
</script>
<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
      <el-form-item label="类型"><el-select v-model="query.billType" style="width:120px"><el-option :label="BillTypeLabel[BillType.RECEIVABLE]" :value="BillType.RECEIVABLE"/><el-option :label="BillTypeLabel[BillType.PAYABLE]" :value="BillType.PAYABLE"/></el-select></el-form-item>
      <el-form-item label="往来单位"><RemoteSelect v-model="query.partnerId" :fetch="fetchPartner" placeholder="全部" style="width:160px" /></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="query_">查询</el-button>
        <el-button :icon="'Refresh'" @click="reset_">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="genDialog=true">生成账单</el-button>
      </div>
      </div>
    </el-card>
    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1240px > 内容区 971px
           ⇒ 横向滚动 269px。收窄为合计 962px（账单号/往来单位保持 min-width，宽屏自动吃余量）。 -->
      <el-table v-loading="loading" :data="data" border stripe @row-click="handleDetail">
        <el-table-column prop="billNo" label="账单号" min-width="100" show-overflow-tooltip/>
        <el-table-column label="类型" width="60" align="center"><template #default="{row}"><el-tag :type="row.billType===BillType.RECEIVABLE?undefined:'warning'">{{ BillTypeLabel[row.billType] || row.billType }}</el-tag></template></el-table-column>
        <el-table-column prop="partnerName" label="往来单位" min-width="100" show-overflow-tooltip/>
        <el-table-column prop="periodStart" label="账期起" width="90" align="center"/>
        <el-table-column prop="periodEnd" label="账期止" width="90" align="center"/>
        <el-table-column prop="totalAmount" label="总额" width="92" align="right" show-overflow-tooltip><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
        <el-table-column prop="paidAmount" label="已收付" width="92" align="right" show-overflow-tooltip><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" label="未收付" width="92" align="right" show-overflow-tooltip><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="状态" width="76" align="center"><template #default="{row}"><el-tag :type="StatusTag[row.status] || 'info'" size="small">{{ StatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right"><template #default="{row}">
          <el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button>
          <el-button v-if="row.status===DocStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
          <el-button v-if="row.status===DocStatus.AUDITED" type="warning" link @click.stop="handleUnAudit(row)">反审核</el-button>
          <el-button v-if="row.status!==DocStatus.CANCELLED" type="danger" link @click.stop="handleCancel(row)">作废</el-button>
        </template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>

    <el-dialog v-model="genDialog" title="生成账单" width="var(--app-dialog-sm)">
      <el-form :model="genForm" label-width="90px">
        <el-form-item label="类型"><el-select v-model="genForm.billType" style="width:100%" @change="onBillTypeChange"><el-option :label="BillTypeLabel[BillType.RECEIVABLE]" :value="BillType.RECEIVABLE"/><el-option :label="BillTypeLabel[BillType.PAYABLE]" :value="BillType.PAYABLE"/></el-select></el-form-item>
        <el-form-item label="往来单位"><RemoteSelect v-model="genForm.partnerId" :fetch="fetchPartner" placeholder="请选择" style="width:100%" @pick="onPartnerPick" /></el-form-item>
        <el-form-item label="账期起"><el-date-picker v-model="genForm.periodStart" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item label="账期止"><el-date-picker v-model="genForm.periodEnd" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="genDialog=false">取消</el-button><el-button type="primary" :loading="genLoading" @click="handleGenerate">生成</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
