<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { getPaymentPage, getPaymentItems, auditPayment, cancelPayment, type FinancePayment, type FinancePaymentItem } from '@/api/finance'

const router = useRouter()
const activeTab = ref('supplier')

// ========== Tab1 供应商汇总 ==========
const summaryLoading = ref(false)
const summaryData = ref<any[]>([])

async function loadSummary() {
  summaryLoading.value = true
  try {
    const r = await request.get<any, any>('/finance/payable/supplier-summary')
    summaryData.value = r || []
  } catch { summaryData.value = [] } finally { summaryLoading.value = false }
}
function goSupplierDetail(row: any) { router.push(`/finance/payment/supplier/${row.supplierId}`) }

// ========== Tab2 付款记录 ==========
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
  try { const r = await request.get<any, any>('/supplier/page',{params:{pageSize:200}}); suppliers.value = r?.records || [] } catch {}
  try { const r = await request.get<any, any>('/finance/account/list'); accounts.value = r || [] } catch {}
}
function query_() { page.pageNum = 1; loadData() }
function reset_() { query.supplierId = ''; query.status = ''; page.pageNum = 1; loadData() }
function sName(id?: number) { return suppliers.value.find(x => x.id === id)?.name || '' }
function aName(id?: number) { return accounts.value.find(x => x.id === id)?.accountName || '' }
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string) { if (s === '草稿') return 'info'; if (s === '已审核') return 'success'; if (s === '已作废') return 'danger'; return '' }

async function handleAudit(row: FinancePayment) {
  try { await ElMessageBox.confirm(`确认审核付款单「${row.code}」？将核销应付并扣减账户余额`, '提示', { type: 'warning' })
    await auditPayment(row.id as number); ElMessage.success('审核成功，已核销应付并更新资金'); loadData(); loadSummary() } catch {}
}
async function handleCancel(row: FinancePayment) {
  try { await ElMessageBox.confirm(`确认作废付款单「${row.code}」？`, '提示', { type: 'warning' })
    await cancelPayment(row.id as number); ElMessage.success('已作废'); loadData() } catch {}
}

// ========== 付款单详情 + 凭证 ==========
const detailVisible = ref(false)
const detail = ref<FinancePayment>({})
const detailItems = ref<FinancePaymentItem[]>([])
const attachSaving = ref(false)

async function handleDetail(row: FinancePayment) {
  detail.value = { ...row }
  try { detailItems.value = await getPaymentItems(row.id as number) || [] } catch {}
  detailVisible.value = true
}
async function handleUploadAttach(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  attachSaving.value = true
  try {
    const fd = new FormData(); fd.append('file', file)
    const url = await request.post<any, string>('/dev/file/upload', fd)
    await request.put(`/finance/payment/${detail.value.id}/attach`, { attachUrl: url })
    detail.value.attachUrl = url as unknown as string
    ElMessage.success('凭证已上传'); loadData()
  } catch (e: any) { ElMessage.error(e?.message || '上传失败') } finally { attachSaving.value = false }
}
function openAttach(url: string) { window.open(url + '?inline=true') }

onMounted(() => { loadOpts(); loadSummary(); loadData() })
</script>

<template>
  <div class="p">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="供应商汇总" name="supplier" />
      <el-tab-pane label="付款记录" name="records" />
    </el-tabs>

    <!-- Tab1 供应商汇总 -->
    <el-card v-if="activeTab==='supplier'" shadow="never">
      <el-table v-loading="summaryLoading" :data="summaryData" border stripe>
        <el-table-column type="index" width="55" align="center"/>
        <el-table-column prop="supplierName" label="供应商" min-width="180" />
        <el-table-column label="应付总额" width="130" align="right"><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
        <el-table-column label="已付" width="130" align="right"><template #default="{row}"><span style="color:#67c23a">{{ fmt(row.paidAmount) }}</span></template></el-table-column>
        <el-table-column label="未付" width="130" align="right"><template #default="{row}"><span style="color:#e6a23c;font-weight:600">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="逾期金额" width="130" align="right"><template #default="{row}"><span :style="{color: Number(row.overdueAmount)>0?'#f56c6c':'#909399', fontWeight: Number(row.overdueAmount)>0?600:400}">{{ fmt(row.overdueAmount) }}</span></template></el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{row}"><el-button type="primary" link @click="goSupplierDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!summaryLoading && summaryData.length===0" description="暂无应付数据" />
    </el-card>

    <!-- Tab2 付款记录 -->
    <template v-if="activeTab==='records'">
      <el-card shadow="never"><el-form :inline="true" :model="query" class="qf">
        <el-form-item label="供应商"><el-select v-model="query.supplierId" placeholder="全部" clearable filterable style="width:160px"><el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:120px"><el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v"/></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="query_">查询</el-button><el-button @click="reset_">重置</el-button></el-form-item>
      </el-form></el-card>
      <el-card shadow="never">
        <el-table v-loading="loading" :data="data" border stripe>
          <el-table-column type="index" width="55" align="center"/>
          <el-table-column prop="code" label="单号" min-width="150"/>
          <el-table-column label="供应商" min-width="140"><template #default="{row}">{{ sName(row.supplierId) }}</template></el-table-column>
          <el-table-column label="账户" min-width="120"><template #default="{row}">{{ aName(row.accountId) }}</template></el-table-column>
          <el-table-column prop="paymentDate" label="日期" width="110" align="center"/>
          <el-table-column prop="amount" label="金额" width="120" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column label="凭证" width="70" align="center"><template #default="{row}"><el-link v-if="row.attachUrl" type="primary" @click="openAttach(row.attachUrl)">查看</el-link><span v-else style="color:#c0c4cc">—</span></template></el-table-column>
          <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="stType(row.status)">{{row.status}}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="140" align="center" fixed="right">
            <template #default="{row}"><el-button type="primary" link @click="handleDetail(row)">详情</el-button><el-button v-if="row.status==='草稿'" type="success" link @click="handleAudit(row)">审核</el-button><el-button v-if="row.status==='草稿'" type="danger" link @click="handleCancel(row)">作废</el-button></template>
          </el-table-column>
        </el-table>
        <div class="pg"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
      </el-card>
    </template>

    <!-- 付款单详情 -->
    <el-drawer v-model="detailVisible" title="付款单详情" size="50%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ sName(detail.supplierId) }}</el-descriptions-item>
        <el-descriptions-item label="账户">{{ aName(detail.accountId) }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.paymentDate }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmt(detail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="付款凭证" :span="2">
          <div style="display:flex;align-items:center;gap:12px">
            <template v-if="detail.attachUrl">
              <el-image :src="detail.attachUrl" :preview-src-list="[detail.attachUrl]" fit="contain" style="width:80px;height:80px;border:1px solid #dcdfe6;border-radius:4px" preview-teleported />
              <el-link type="primary" @click="openAttach(detail.attachUrl)">查看原图</el-link>
            </template>
            <span v-else style="color:#909399">未上传</span>
            <label class="upload-btn">
              <input type="file" accept="image/*" style="display:none" @change="handleUploadAttach" />
              <el-button size="small" :loading="attachSaving" @click="($event.currentTarget as HTMLElement).parentElement?.querySelector('input')?.click()">{{ detail.attachUrl ? '更换凭证' : '上传凭证' }}</el-button>
            </label>
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <el-divider>核销明细</el-divider>
      <el-table :data="detailItems" border><el-table-column type="index" width="50" align="center"/><el-table-column prop="payableBillNo" label="应付单据" min-width="150"/><el-table-column prop="thisAmount" label="核销金额" width="130" align="right"><template #default="{row}">{{ fmt(row.thisAmount) }}</template></el-table-column></el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.p{display:flex;flex-direction:column;gap:12px}
.qf{display:flex;flex-wrap:wrap}
.pg{margin-top:16px;display:flex;justify-content:flex-end}
.upload-btn{display:inline-block}
</style>
