<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getInvoicePage, createInvoice, updateInvoice, cancelInvoice, type FinanceInvoice } from '@/api/finance'

// 发票管理（税务口径）：销项=我方开出的发票，进项=我方收到的发票。登记即生效，作废仅标记可重新登记同号。
// 发票类型存枚举 code，显示用 label 映射
const INVOICE_KIND_LABELS: Record<string, string> = { special: '增值税专用发票', normal: '增值税普通发票', e_special: '电子专票', e_normal: '电子普票' }
const INVOICE_KINDS = Object.keys(INVOICE_KIND_LABELS)
const DIRECTION_LABEL: Record<string, string> = { SALE: '销项', PURCHASE: '进项' }
const STATUS_LABEL: Record<string, string> = { REGISTERED: '已登记', CANCELLED: '已作废' }
const STATUS_TAG: Record<string, string> = { REGISTERED: 'success', CANCELLED: 'info' }

const query = reactive({ direction: '', status: '', keyword: '', dateRange: null as [string, string] | null })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceInvoice[]>([])
const dialog = ref(false)
const dialogTitle = ref('登记发票')
const form = reactive<FinanceInvoice>({})
// 输入模式：total=填价税合计（自动拆税），net=填不含税金额（自动算税额与价税合计）
const inputMode = ref<'total' | 'net'>('total')

const emptyForm = (): FinanceInvoice => ({
  id: undefined, invoiceNo: '', direction: 'SALE', invoiceKind: 'special',
  invoiceDate: localDate(), partnerName: '',
  amount: undefined, taxRate: 13, taxAmount: undefined, totalAmount: undefined,
  sourceBillCode: '', remark: '',
})

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.direction) p.direction = query.direction
    if (query.status) p.status = query.status
    if (query.keyword) p.keyword = query.keyword
    if (query.dateRange && query.dateRange[0]) { p.start = query.dateRange[0]; p.end = query.dateRange[1] }
    const res = await getInvoicePage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}

// 金额联动预览（后端同样兜底计算）：total 模式=价税合计×税率/(100+税率)；net 模式反向
const preview = computed(() => {
  const rate = Number(form.taxRate) || 0
  if (inputMode.value === 'total') {
    const t = Number(form.totalAmount) || 0
    const tax = Math.round(t * rate / (100 + rate) * 100) / 100
    return { amount: Math.round((t - tax) * 100) / 100, tax, total: t }
  }
  const a = Number(form.amount) || 0
  const total = Math.round(a * (100 + rate) / 100 * 100) / 100
  return { amount: a, tax: Math.round((total - a) * 100) / 100, total }
})

function handleAdd() { Object.assign(form, emptyForm()); inputMode.value = 'total'; dialogTitle.value = '登记发票'; dialog.value = true }
function handleEdit(row: FinanceInvoice) {
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  inputMode.value = 'total'
  dialogTitle.value = '编辑发票'; dialog.value = true
}
async function save() {
  if (!form.invoiceNo) { ElMessage.warning('请填写发票号码'); return }
  if (inputMode.value === 'total' && (!form.totalAmount || form.totalAmount <= 0)) { ElMessage.warning('请填写价税合计'); return }
  if (inputMode.value === 'net' && (!form.amount || form.amount <= 0)) { ElMessage.warning('请填写不含税金额'); return }
  // 按输入模式清掉另一侧金额，后端统一联动计算
  const payload: any = { ...form }
  if (inputMode.value === 'total') payload.amount = undefined
  else payload.totalAmount = undefined
  try {
    if (form.id) { await updateInvoice(payload); ElMessage.success('修改成功') }
    else { await createInvoice(payload); ElMessage.success('登记成功') }
    dialog.value = false; loadData()
  } catch {}
}
async function cancel(row: FinanceInvoice) {
  try { await ElMessageBox.confirm(`确认作废发票 ${row.invoiceNo}？作废后该号码可重新登记。`, '作废确认', { type: 'warning' }) } catch { return }
  try { await cancelInvoice(row.id!); ElMessage.success('已作废'); loadData() } catch {}
}
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtDate(v?: string) { return v ? String(v).slice(0, 10) : '' }
onMounted(() => { loadData() })
</script>
<template>
  <div class="p">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="qf">
          <el-form-item label="方向">
            <el-select v-model="query.direction" placeholder="全部" clearable style="width:100px">
              <el-option label="销项" value="SALE"/><el-option label="进项" value="PURCHASE"/>
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width:110px">
              <el-option v-for="(label, code) in STATUS_LABEL" :key="code" :label="label" :value="code"/>
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input v-model="query.keyword" placeholder="发票号/对方单位/关联单号" clearable style="width:200px" @keyup.enter="page.pageNum=1;loadData()"/>
          </el-form-item>
          <el-form-item label="开票日期">
            <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD"
              start-placeholder="开始" end-placeholder="结束" style="width:240px" :clearable="true"/>
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="page.pageNum=1;loadData()">查询</el-button>
          <el-button :icon="'Refresh'" @click="query.direction='';query.status='';query.keyword='';query.dateRange=null;page.pageNum=1;loadData()">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">登记发票</el-button>
        </div>
      </div>
    </el-card>
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column prop="invoiceNo" label="发票号码" width="160"/>
        <el-table-column label="方向" width="80" align="center">
          <template #default="{row}"><el-tag :type="row.direction==='SALE'?'success':'warning'" size="small">{{ DIRECTION_LABEL[row.direction] || row.direction }}</el-tag></template>
        </el-table-column>
        <el-table-column label="发票类型" width="130"><template #default="{row}">{{ INVOICE_KIND_LABELS[row.invoiceKind] || row.invoiceKind }}</template></el-table-column>
        <el-table-column label="开票日期" width="110"><template #default="{row}">{{ fmtDate(row.invoiceDate) }}</template></el-table-column>
        <el-table-column prop="partnerName" label="对方单位" min-width="140" show-overflow-tooltip/>
        <el-table-column prop="amount" label="不含税金额" width="120" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="税率" width="75" align="right"><template #default="{row}">{{ Number(row.taxRate)||0 }}%</template></el-table-column>
        <el-table-column label="税额" width="110" align="right"><template #default="{row}"><span style="color:var(--app-color-primary)">{{ fmt(row.taxAmount) }}</span></template></el-table-column>
        <el-table-column prop="totalAmount" label="价税合计" width="120" align="right"><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
        <el-table-column prop="sourceBillCode" label="关联单号" width="140" show-overflow-tooltip/>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="(STATUS_TAG[row.status]||'info') as any" size="small">{{ STATUS_LABEL[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{row}">
            <template v-if="row.status==='REGISTERED'">
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button type="danger" link @click="cancel(row)">作废</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pg"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>
    <el-dialog v-model="dialog" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="方向">
          <el-radio-group v-model="form.direction" :disabled="!!form.id">
            <el-radio value="SALE">销项（我方开出）</el-radio>
            <el-radio value="PURCHASE">进项（我方收到）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发票号码" required><el-input v-model="form.invoiceNo" placeholder="请输入发票号码"/></el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="form.invoiceKind" style="width:100%"><el-option v-for="k in INVOICE_KINDS" :key="k" :label="INVOICE_KIND_LABELS[k]" :value="k"/></el-select>
        </el-form-item>
        <el-form-item label="开票日期"><el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item :label="form.direction==='SALE' ? '购买方' : '销售方'"><el-input v-model="form.partnerName" placeholder="对方单位名称"/></el-form-item>
        <el-form-item label="金额模式">
          <el-radio-group v-model="inputMode">
            <el-radio value="total">填价税合计（自动拆税）</el-radio>
            <el-radio value="net">填不含税金额</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="税率(%)"><el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%"/></el-form-item>
        <el-form-item v-if="inputMode==='total'" label="价税合计" required>
          <el-input-number v-model="form.totalAmount" :min="0.01" :precision="2" controls-position="right" style="width:100%"/>
        </el-form-item>
        <el-form-item v-else label="不含税金额" required>
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" controls-position="right" style="width:100%"/>
        </el-form-item>
        <el-form-item label="联动预览">
          <span style="font-size:var(--app-font-base)">不含税 <b>{{ fmt(preview.amount) }}</b> ＋ 税额 <b style="color:var(--app-color-primary)">{{ fmt(preview.tax) }}</b> ＝ 价税合计 <b>{{ fmt(preview.total) }}</b></span>
        </el-form-item>
        <el-form-item label="关联单号"><el-input v-model="form.sourceBillCode" placeholder="可选：关联销售单/采购单号"/></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
