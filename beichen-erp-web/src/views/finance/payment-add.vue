<script setup lang="ts">
// 新增付款（2026-09-29 用户口径：「付款管理只显示付款记录就行，然后可以**新增付款记录**」）
// —— 由「付款管理」页头「新增付款」进入（供应商**在页内选**，不再必须先进某供应商的应付工作台）；
//    兼容 `?supplierId=` 预填：供应商工作台的「新增付款」仍可直链过来（选中即带出未结清应付）。
//
// 与收款侧 receipt-add.vue **逐条对称**（用户在 2026-09-29 明确要求付款侧一并做）：
//   ① **付款账户可添加**：一个单可拆到多个账户付款（A 账户 50 + B 账户 100）⇒「付款账户」是**表格**，
//      「付款金额」= 各账户金额合计（只读展示）；后端按分款明细**逐条**写资金流水、**逐账户校验余额**。
//   ② **核销项做成开关**：`el-switch`「本次核销」，**默认关**（= 只记付款不核销）。关掉时核销明细为空，
//      付款全额作为**预付台账**（ADVANCE，负数应付：我方多付）挂账；打开才选应付单据核销，
//      且核销合计 ≤ 付款金额（差额同样落预付台账）。
import { localDate } from '@/utils/date'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { createPayment, getPaymentUnpaidPayables, getPaymentPartySummary, type FinancePaymentAccount, type FinancePaymentItem, type PayableDebtSummary } from '@/api/finance'

const route = useRoute()
const router = useRouter()

const accounts = ref<{id:number;accountName:string}[]>([])
const form = reactive({ supplierId: undefined as any, paymentDate: localDate(), remark: '', attachUrl: '' })
/** 付款账户分款明细（2026-09-29 口径①）：一行 = 一个账户本次付出的钱；初始给一行空行，用户直接填 */
const rows = ref<FinancePaymentAccount[]>([{ accountId: undefined, amount: 0, remark: '' }])
/** 核销开关（2026-09-29 口径②：**默认关** = 只记付款不核销） */
const writeOff = ref(false)
const items = ref<FinancePaymentItem[]>([])
const unpaid = ref<any[]>([])
const saving = ref(false)
const uploadFile = ref<File | null>(null)
const summary = ref<PayableDebtSummary | null>(null)

/** 付款金额 = 分款合计（**唯一来源**：主表 amount 由后端同样按分款合计落库） */
const paidTotal = computed(() => rows.value.reduce((s, r) => s + Number(r.amount || 0), 0))
/** 核销合计（开关关闭时恒为 0 —— 关掉开关即"不核销"） */
const settledTotal = computed(() => (writeOff.value ? items.value.reduce((s, r) => s + Number(r.thisAmount || 0), 0) : 0))
/** 未核销余额 = 付款合计 − 核销合计（> 0 时审核后会生成预付台账） */
const unsettled = computed(() => Math.max(0, paidTotal.value - settledTotal.value))
/** 实际参与分款的账户行数（只统计填了账户或金额的行） */
const usedAccountCount = computed(() => rows.value.filter(r => r.accountId != null || Number(r.amount || 0) > 0).length)
function fmt(v?: number) { return Number(v || 0).toFixed(2) }

const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadAccounts() {
  try { accounts.value = await request.get<any, any>('/finance/account/list') || [] } catch { accounts.value = [] }
}
/** 按供应商拉取可核销的未结清应付（走付款页自身前缀，避免跨模块权限被拦） */
async function loadUnpaid() {
  if (form.supplierId == null) { unpaid.value = []; return }
  try { unpaid.value = await getPaymentUnpaidPayables(Number(form.supplierId)) || [] } catch { unpaid.value = [] }
}
/**
 * 欠款汇总（2026-09-29）：**到期欠款 + 总欠款**。
 * <p>口径与后端 {@code PayableQuery.partySummary} 一致（同应付汇总唯一口径）：未结清且未付&gt;0
 * （排除预付台账 ADVANCE）；到期 = `due_date < 今天`（当天不算）；无到期日不计入到期。</p>
 */
async function loadSummary() {
  if (form.supplierId == null) { summary.value = null; return }
  try { summary.value = await getPaymentPartySummary({ supplierId: Number(form.supplierId) }) || null } catch { summary.value = null }
}
/** 选完供应商：可核销应付 + 欠款汇总一起刷新（同源同口径） */
async function onSupplierChange() { await loadUnpaid(); loadSummary() }
/** 分款明细：增/删行 */
function addRow() { rows.value.push({ accountId: undefined, amount: 0, remark: '' }) }
function removeRow(i: number) { rows.value.splice(i, 1) }
/** 核销明细：增/删行 + 选中应付后默认带出"未付额" */
function addItem() { items.value.push({ payableId: undefined, payableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onPayableChange(val: number, row: FinancePaymentItem) {
  const r = unpaid.value.find(x => x.id === val)
  if (r) { row.payableId = r.id; row.payableBillNo = r.billNo; row.thisAmount = r.unpaidAmount }
}
function handleFileSelect(e: Event) { const f = (e.target as HTMLInputElement).files?.[0]; if (f) uploadFile.value = f }
/** 从按钮向上找最近的 form-item 容器再取隐藏的 file input（不依赖固定 DOM 层级；与供应商付款页同款） */
function pickUploadFile(e: Event) {
  const btn = e.currentTarget as HTMLElement | null
  const box = btn?.closest('.el-form-item') as HTMLElement | null
  box?.querySelector<HTMLInputElement>('input[type="file"]')?.click()
}

async function submit() {
  if (form.supplierId == null) { ElMessage.warning('请选择供应商'); return }
  // 分款校验（与后端 saveAccounts 逐条对齐：账户必选、金额 > 0、同账户不许重复行）
  const accRows = rows.value.filter(r => r.accountId != null || Number(r.amount || 0) > 0)
  if (accRows.length === 0) { ElMessage.warning('请至少添加一个付款账户'); return }
  for (let i = 0; i < accRows.length; i++) {
    if (accRows[i].accountId == null) { ElMessage.warning(`付款账户第 ${i + 1} 行未选择账户`); return }
    if (!(Number(accRows[i].amount) > 0)) { ElMessage.warning(`付款账户第 ${i + 1} 行金额必须大于 0`); return }
  }
  const ids = accRows.map(r => Number(r.accountId))
  if (new Set(ids).size !== ids.length) { ElMessage.warning('同一账户请合并为一行（账户不允许重复）'); return }
  // 核销校验：开关关闭 ⇒ 一律不核销；打开 ⇒ 每行必须选应付，且核销合计 ≤ 付款合计
  const useItems = writeOff.value ? items.value : []
  if (writeOff.value) {
    if (useItems.length === 0) { ElMessage.warning('已打开「本次核销」：请添加核销明细（或关闭开关只记付款）'); return }
    for (let i = 0; i < useItems.length; i++) {
      if (!useItems[i].payableId) { ElMessage.warning(`核销明细第 ${i + 1} 行未选择应付单据`); return }
      // 2026-09-29 审核批 B · F7-214：核销金额必须大于 0（后端 saveItems 同护栏；前端先拦，避免整单提交后才报错）
      if (!(Number(useItems[i].thisAmount) > 0)) { ElMessage.warning(`核销明细第 ${i + 1} 行核销金额必须大于 0`); return }
    }
    if (settledTotal.value > paidTotal.value) {
      ElMessage.warning(`核销合计 ${fmt(settledTotal.value)} 不能超过付款合计 ${fmt(paidTotal.value)}`); return
    }
  }
  saving.value = true
  try {
    if (uploadFile.value) {
      const fd = new FormData(); fd.append('file', uploadFile.value)
      form.attachUrl = await request.post<any, string>('/dev/file/upload', fd) as unknown as string
    }
    await createPayment({
      payment: { supplierId: Number(form.supplierId), paymentDate: form.paymentDate, remark: form.remark, attachUrl: form.attachUrl },
      accounts: accRows.map(r => ({ accountId: Number(r.accountId), amount: Number(r.amount), remark: r.remark })),
      items: useItems.map(it => ({ payableId: it.payableId, payableBillNo: it.payableBillNo, thisAmount: Number(it.thisAmount || 0), remark: it.remark }))
    })
    ElMessage.success('付款单已创建，请在付款记录中审核')
    router.push('/finance/payment')
  } catch { /* 提示由拦截器统一给出 */ } finally { saving.value = false }
}

onMounted(async () => {
  loadAccounts()
  // 兼容 `?supplierId=` 预填（供应商工作台「新增付款」直链过来）
  const q = Number(route.query.supplierId)
  if (q) { form.supplierId = q; await onSupplierChange() }
})
</script>

<template>
  <div class="p">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">新增付款单</span>
          <el-button @click="router.push('/finance/payment')">返回</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="90px" style="max-width:1000px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="供应商" required>
            <RemoteSelect v-model="form.supplierId" add-route="/supplier/manage/add" :fetch="fetchSuppliers" placeholder="请选择" style="width:100%" @change="() => onSupplierChange()" domain="supplier" />
          </el-form-item></el-col>
          <!-- 欠款汇总（2026-09-29 用户口径）：选完供应商后显示 **总欠款 + 到期欠款**；
               口径 = 后端 PayableQuery.partySummary（到期 = due_date < 今天，当天不算、无到期日不计入
               ⇒ 故有"其中 N 张未约定到期日"这句解释，避免被当成算错） -->
          <el-col :span="12" v-if="summary">
            <el-form-item label="欠款情况">
              <div class="party-summary" style="display:flex;gap:18px;align-items:center;flex-wrap:wrap;font-size:var(--app-font-base)">
                <span v-if="Number(summary.billCount || 0) === 0" style="color:var(--app-text-secondary)">该供应商当前无欠款</span>
                <template v-else>
                  <span>总欠款：<b style="color:var(--app-color-danger);font-size:var(--app-font-lg)">¥{{ fmt(summary.unpaidAmount) }}</b></span>
                  <span>到期欠款：<b :style="Number(summary.overdueAmount || 0) > 0 ? 'color:var(--app-color-danger);font-weight:600' : 'color:var(--app-text-secondary)'">¥{{ fmt(summary.overdueAmount) }}</b></span>
                  <span v-if="Number(summary.noDueAmount || 0) > 0" style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">
                    （{{ summary.noDueCount }} 张单未约定到期日，未计入到期欠款）
                  </span>
                </template>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="付款日期"><el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款凭证">
            <div style="display:flex;align-items:center;gap:8px">
              <el-button size="small" @click="pickUploadFile($event)">选择图片</el-button>
              <span style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">{{ uploadFile?.name || '未选择' }}</span>
              <input type="file" accept="image/*" style="display:none" @change="handleFileSelect" />
            </div>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2"/></el-form-item></el-col>
        </el-row>

        <!-- ===== 付款账户（2026-09-29 口径①：可多账户分款）===== -->
        <el-divider>
          付款账户（可分多账户付款，如 A 账户 50 + B 账户 100）
        </el-divider>
        <div style="margin-bottom:8px;display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap">
          <el-button type="primary" @click="addRow">添加账户</el-button>
          <!-- 付款金额 = 分款合计（只读，后端同样按分款合计落库 amount） -->
          <span style="font-size:var(--app-font-base)">
            付款金额：<b style="font-size:var(--app-font-lg);color:var(--app-color-primary)">¥{{ fmt(paidTotal) }}</b>
            <span style="color:var(--app-text-secondary)">（{{ usedAccountCount }} 个账户）</span>
          </span>
        </div>
        <el-table :data="rows" border>
          <el-table-column label="付款账户" min-width="200">
            <template #default="{row}">
              <el-select v-model="row.accountId" placeholder="选择付款账户" filterable clearable style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { $router.push('/finance/account') } }">
                <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id"/>
              
                <el-option label="+ 鏂板" :value="ADD_MARKER" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="付款金额" width="160">
            <template #default="{row}"><el-input-number v-model="row.amount" :min="0" :precision="2" controls-position="right" style="width:100%"/></template>
          </el-table-column>
          <el-table-column label="备注" min-width="160">
            <template #default="{row}"><el-input v-model="row.remark" placeholder="可不填"/></template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{$index}"><el-button type="danger" link @click="removeRow($index)">删除</el-button></template>
          </el-table-column>
        </el-table>

        <!-- ===== 核销明细（2026-09-29 口径②：开关项，默认关）===== -->
        <el-divider>
          <span style="display:inline-flex;align-items:center;gap:8px">
            核销明细
            <el-switch v-model="writeOff" active-text="本次核销" inline-prompt
              style="--el-switch-on-color:var(--app-color-primary)" />
            <span style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">
              {{ writeOff ? '（选应付单据核销，核销合计 ≤ 付款金额）' : '（关闭＝只记付款不核销，全额作为预付挂账）' }}
            </span>
          </span>
        </el-divider>
        <template v-if="writeOff">
          <div style="margin-bottom:8px"><el-button type="primary" @click="addItem">添加核销项</el-button></div>
          <el-table :data="items" border>
            <el-table-column label="应付单据（该供应商未结清应付）" min-width="260">
              <template #default="{row}">
                <el-select v-model="row.payableId" placeholder="选择应付单据" filterable style="width:100%" @change="(v:number)=>onPayableChange(v,row)">
                  <el-option v-for="u in unpaid" :key="u.id" :label="`${u.billNo} (未付:${u.unpaidAmount}, 到期:${u.dueDate || '-'})`" :value="u.id ?? ''"/>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="核销金额" width="160">
              <template #default="{row}"><el-input-number v-model="row.thisAmount" :min="0" :precision="2" controls-position="right" style="width:100%"/></template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <div style="margin-top:8px;font-size:var(--app-font-base)">
            核销合计：<b>¥{{ fmt(settledTotal) }}</b>
            <span v-if="unsettled > 0" style="margin-left:12px;color:var(--app-color-warning)">
              未核销差额 <b>¥{{ fmt(unsettled) }}</b> 将作为预付/未核销余额挂账（审核后生成预付台账）
            </span>
          </div>
        </template>
        <el-alert v-else type="info" :closable="false" show-icon style="margin-top:4px"
          :title="`本单不核销应付：付款金额 ¥${fmt(paidTotal)} 将全额作为预付（我方多付，供应商欠我方）挂账，审核后可在应付台账里抵扣或退款`" />
      </el-form>

      <div style="margin-top:12px;display:flex;gap:8px;justify-content:flex-end;max-width:1000px">
        <el-button @click="router.push('/finance/payment')">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>.p{display:flex;flex-direction:column;gap:12px}</style>
