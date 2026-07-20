<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { getPaymentPage, createPayment, getUnpaidPayables, type FinancePaymentItem } from '@/api/finance'

const route = useRoute(); const router = useRouter()
const supplierId = Number(route.params.id)
const loading = ref(false)
const supplier = ref<any>({})
const summary = ref<any>({})
const payables = ref<any[]>([])
const payments = ref<any[]>([])
const accounts = ref<any[]>([])

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

async function loadAll() {
  loading.value = true
  try {
    const [sup, sumList, pList, payList, accList] = await Promise.all([
      request.get<any, any>(`/supplier/${supplierId}`),
      request.get<any, any>('/finance/payable/supplier-summary'),
      request.get<any, any>('/finance/payable/page', { params: { supplierId, pageSize: 200 } }),
      getPaymentPage({ supplierId, pageSize: 100 }),
      request.get<any, any>('/finance/account/list')
    ])
    supplier.value = sup || {}
    summary.value = (sumList || []).find((x: any) => x.supplierId === supplierId) || {}
    payables.value = pList?.records || []
    payments.value = payList?.records || []
    accounts.value = accList || []
  } finally { loading.value = false }
}

// ========== 新增付款 ==========
const dVisible = ref(false)
const dLoading = ref(false)
const dForm = reactive({ accountId: undefined as any, paymentDate: new Date().toISOString().slice(0,10), remark: '', attachUrl: '' })
const dItems = ref<FinancePaymentItem[]>([])
const unpaid = ref<any[]>([])
const uploadFile = ref<File | null>(null)

async function openAddPayment() {
  Object.assign(dForm, { accountId: undefined, paymentDate: new Date().toISOString().slice(0,10), remark: '', attachUrl: '' })
  dItems.value = []; uploadFile.value = null
  try { unpaid.value = await getUnpaidPayables(supplierId) || [] } catch { unpaid.value = [] }
  if (unpaid.value.length === 0) { ElMessage.info('该供应商没有未结清应付'); return }
  dVisible.value = true
}
function addItem() { dItems.value.push({ payableId: undefined, payableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { dItems.value.splice(i, 1) }
function onPayableChange(val: number, row: FinancePaymentItem) {
  const r = unpaid.value.find(x => x.id === val)
  if (r) { row.payableId = r.id; row.payableBillNo = r.billNo; row.thisAmount = r.unpaidAmount }
}
function handleFileSelect(e: Event) { const f = (e.target as HTMLInputElement).files?.[0]; if (f) uploadFile.value = f }

async function handleSubmitPayment() {
  if (!dForm.accountId) { ElMessage.warning('请选择付款账户'); return }
  if (dItems.value.length === 0) { ElMessage.warning('请添加核销明细'); return }
  dLoading.value = true
  try {
    if (uploadFile.value) {
      const fd = new FormData(); fd.append('file', uploadFile.value)
      dForm.attachUrl = await request.post<any, string>('/dev/file/upload', fd) as unknown as string
    }
    await createPayment({ payment: { supplierId, ...dForm }, items: dItems.value })
    ElMessage.success('付款单已创建，请在付款记录中审核')
    dVisible.value = false; loadAll()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { dLoading.value = false }
}

const totalThisAmount = computed(() => dItems.value.reduce((s, it) => s + (Number(it.thisAmount) || 0), 0))

function stType(s?: string) { if (s === '未结清') return 'danger'; if (s === '部分结清') return 'warning'; if (s === '已结清') return 'success'; return 'info' }
function pStType(s?: string) { if (s === '草稿') return 'info'; if (s === '已审核') return 'success'; if (s === '已作废') return 'danger'; return '' }
function openAttach(url: string) { window.open(url + '?inline=true') }
function goSettlement() { router.push(`/finance/supplier-settlement/${supplierId}`) }

onMounted(() => loadAll())
</script>

<template>
  <div class="p" v-loading="loading">
    <div class="page-header">
      <span class="page-title">{{ supplier.name || '供应商' }} - 应付详情</span>
      <div>
        <el-button type="primary" @click="openAddPayment">新增付款</el-button>
        <el-button type="danger" plain @click="goSettlement">清算</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="stat-row">
        <div class="stat-item"><div class="stat-label">应付总额</div><div class="stat-value">{{ fmt(summary.totalAmount) }}</div></div>
        <div class="stat-item"><div class="stat-label">已付</div><div class="stat-value" style="color:#67c23a">{{ fmt(summary.paidAmount) }}</div></div>
        <div class="stat-item"><div class="stat-label">未付</div><div class="stat-value" style="color:#e6a23c">{{ fmt(summary.unpaidAmount) }}</div></div>
        <div class="stat-item"><div class="stat-label">逾期金额</div><div class="stat-value" style="color:#f56c6c">{{ fmt(summary.overdueAmount) }}</div></div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">应付明细</span></template>
      <el-table :data="payables" border stripe size="small">
        <el-table-column prop="billNo" label="单据号" width="150" />
        <el-table-column prop="sourceBillType" label="来源" width="110" />
        <el-table-column prop="sourceBillNo" label="来源单号" width="160" show-overflow-tooltip />
        <el-table-column label="应付金额" width="110" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="已付" width="110" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column label="未付" width="110" align="right"><template #default="{row}"><span style="color:#f56c6c">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="到期日" width="100" align="center"><template #default="{row}">{{ $fmtDate(row.dueDate) }}</template></el-table-column>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="stType(row.status)" size="small">{{ row.status }}</el-tag></template></el-table-column>
      </el-table>
      <el-empty v-if="payables.length===0" description="暂无应付" :image-size="60" />
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">付款记录</span></template>
      <el-table :data="payments" border stripe size="small">
        <el-table-column prop="code" label="单号" width="150" />
        <el-table-column prop="accountName" label="账户" width="120" />
        <el-table-column prop="paymentDate" label="日期" width="100" align="center" />
        <el-table-column label="金额" width="110" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="凭证" width="70" align="center"><template #default="{row}"><el-link v-if="row.attachUrl" type="primary" @click="openAttach(row.attachUrl)">查看</el-link><span v-else style="color:#c0c4cc">—</span></template></el-table-column>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="pStType(row.status)" size="small">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="payments.length===0" description="暂无付款记录" :image-size="60" />
    </el-card>

    <!-- 新增付款弹窗 -->
    <el-dialog v-model="dVisible" title="新增付款" width="800px" :close-on-click-modal="false">
      <el-form :model="dForm" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="供应商"><el-input :model-value="supplier.name" readonly /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款账户" required><el-select v-model="dForm.accountId" filterable style="width:100%"><el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款日期"><el-date-picker v-model="dForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款凭证">
            <div style="display:flex;align-items:center;gap:8px">
              <el-button size="small" @click="($event.currentTarget as HTMLElement).parentElement?.parentElement?.querySelector('input')?.click()">选择图片</el-button>
              <span style="font-size:12px;color:#909399">{{ uploadFile?.name || '未选择' }}</span>
              <input type="file" accept="image/*" style="display:none" @change="handleFileSelect" />
            </div>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="dForm.remark" type="textarea" :rows="2"/></el-form-item></el-col>
        </el-row>
        <el-divider>核销明细（未结清应付）</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" size="small" @click="addItem">添加核销项</el-button></div>
        <el-table :data="dItems" border size="small">
          <el-table-column type="index" width="50" align="center"/>
          <el-table-column label="应付单据" min-width="240"><template #default="{row}"><el-select v-model="row.payableId" filterable style="width:100%" @change="(v:number)=>onPayableChange(v,row)"><el-option v-for="u in unpaid" :key="u.id" :label="`${u.billNo} (未付:${u.unpaidAmount}, 到期:${$fmtDate(u.dueDate)})`" :value="u.id"/></el-select></template></el-table-column>
          <el-table-column label="核销金额" width="140"><template #default="{row}"><el-input-number v-model="row.thisAmount" :min="0" :precision="2" controls-position="right" style="width:100%"/></template></el-table-column>
          <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
        </el-table>
        <div style="margin-top:8px;text-align:right;font-weight:600">本次付款合计：¥ {{ fmt(totalThisAmount) }}</div>
      </el-form>
      <template #footer><el-button @click="dVisible=false">取消</el-button><el-button type="primary" :loading="dLoading" @click="handleSubmitPayment">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.p{display:flex;flex-direction:column;gap:12px}
.page-header{display:flex;align-items:center;justify-content:space-between;padding-bottom:4px}
.page-title{font-size:18px;font-weight:600}
.stat-row{display:flex;gap:48px;padding:4px 8px}
.stat-label{font-size:13px;color:#909399;margin-bottom:4px}
.stat-value{font-size:22px;font-weight:600}
</style>
