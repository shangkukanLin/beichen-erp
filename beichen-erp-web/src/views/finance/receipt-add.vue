<script setup lang="ts">
// 新增收款（2026-09-23 用户要求：原 850px 弹框改为独立页面）
// —— 主体类型（客户收款 / 供应商收款，如应付转应收）决定往来单位与可核销应收的口径。
//
// 2026-09-29 用户口径（两件）：
//   ① **收款账户可添加**：一个单可拆到多个账户收款（A 账户 50 + B 账户 100）⇒「收款账户」由单选改为**表格**，
//      「收款金额」= 各账户金额合计（只读展示）；后端按分款明细**逐条**写资金流水（各账户余额各自累计）。
//   ② **核销项做成开关**：`el-switch`「本次核销」，**默认关**（= 只记收款不核销）。关掉时核销明细为空，
//      收款全额作为**预收/预付台账**（ADVANCE，我方欠客户）挂账；打开才选应收单据核销，
//      且核销合计 ≤ 收款金额（差额同样落预收台账）。
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { createReceipt, getReceiptUnpaidReceivables, getReceiptPartySummary, type FinanceReceipt, type FinanceReceiptAccount, type FinanceReceiptItem, type FinanceReceivable, type PartyDebtSummary } from '@/api/finance'
import { SubjectType } from '@/api/enums'

const router = useRouter()
const accounts = ref<{id:number;accountName:string}[]>([])

const form = reactive<FinanceReceipt>({ customerId: undefined, supplierId: undefined, subjectType: SubjectType.CUSTOMER as string, receiptDate: '', remark: '' })
/** 收款账户分款明细（2026-09-29 口径①）：一行 = 一个账户本次收到的钱；初始给一行空行，用户直接填 */
const rows = ref<FinanceReceiptAccount[]>([{ accountId: undefined, amount: 0, remark: '' }])
/** 核销开关（2026-09-29 口径②：**默认关** = 只记收款不核销） */
const writeOff = ref(false)
const items = ref<FinanceReceiptItem[]>([])
const unpaid = ref<FinanceReceivable[]>([])
const saving = ref(false)

/** 收款金额 = 分款合计（**唯一来源**：主表 amount 由后端同样按分款合计落库） */
const receivedTotal = computed(() => rows.value.reduce((s, r) => s + Number(r.amount || 0), 0))
/** 核销合计（开关关闭时恒为 0 —— 关掉开关即"不核销"） */
const settledTotal = computed(() => (writeOff.value ? items.value.reduce((s, r) => s + Number(r.thisAmount || 0), 0) : 0))
/** 未核销余额 = 收款合计 − 核销合计（> 0 时审核后会生成预收/预付台账） */
const unsettled = computed(() => Math.max(0, receivedTotal.value - settledTotal.value))
/** 实际参与分款的账户行数（只统计填了账户或金额的行） */
const usedAccountCount = computed(() => rows.value.filter(r => r.accountId != null || Number(r.amount || 0) > 0).length)
function fmt(v?: number) { return Number(v || 0).toFixed(2) }

const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadAccounts() {
  try { accounts.value = await request.get<any, any>('/finance/account/list') || [] } catch { accounts.value = [] }
}
function accountName(id?: number) { return accounts.value.find(x => x.id === id)?.accountName || '' }
/** 切换主体类型：清空往来单位与已选核销明细（收款账户与金额保留 —— 与主体无关） */
function onSubjectTypeChange() {
  form.customerId = undefined
  form.supplierId = undefined
  items.value = []
  unpaid.value = []
  summary.value = null
}
/** 按主体类型拉取可核销的未结清应收（走收款页自身前缀，避免跨模块权限被拦） */
async function loadUnpaid() {
  try {
    unpaid.value = form.subjectType === SubjectType.SUPPLIER
      ? (await getReceiptUnpaidReceivables(undefined, form.supplierId as number)) || []
      : (await getReceiptUnpaidReceivables(form.customerId as number)) || []
  } catch { unpaid.value = [] }
}
/**
 * 主体欠款汇总（2026-09-29 用户口径）：**到期欠款 + 总欠款**。
 * <p>口径与后端 {@code ReceivableQuery.partySummary} 一致（= 应付侧 supplierSummary 的镜像）：
 * 未结清且未收&gt;0（排除预收台账 ADVANCE）；到期 = `due_date < 今天`（当天不算）；无到期日不计入到期。</p>
 */
const summary = ref<PartyDebtSummary | null>(null)
async function loadSummary() {
  const id = form.subjectType === SubjectType.SUPPLIER ? form.supplierId : form.customerId
  if (id == null) { summary.value = null; return }
  try {
    summary.value = await getReceiptPartySummary(form.subjectType === SubjectType.SUPPLIER
      ? { subjectType: SubjectType.SUPPLIER, supplierId: Number(id) }
      : { subjectType: SubjectType.CUSTOMER, customerId: Number(id) }) || null
  } catch { summary.value = null }
}
/** 选完往来单位：可核销应收 + 欠款汇总一起刷新（同源同口径） */
async function onPartnerChange() { await loadUnpaid(); loadSummary() }
/** 分款明细：增/删行 */
function addRow() { rows.value.push({ accountId: undefined, amount: 0, remark: '' }) }
function removeRow(i: number) { rows.value.splice(i, 1) }
/** 核销明细：增/删行 + 选中应收后默认带出"未收额" */
function addItem() { items.value.push({ receivableId: undefined, receivableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onReceivableChange(val: number, row: FinanceReceiptItem) {
  const r = unpaid.value.find(x => x.id === val)
  if (r) { row.receivableId = r.id; row.receivableBillNo = r.billNo; row.thisAmount = r.unpaidAmount }
}

async function submit() {
  if (form.subjectType === SubjectType.SUPPLIER && !form.supplierId) { ElMessage.warning('请选择供应商'); return }
  if (form.subjectType !== SubjectType.SUPPLIER && !form.customerId) { ElMessage.warning('请选择客户'); return }
  // 分款校验（与后端 saveAccounts 逐条对齐：账户必选、金额 > 0、同账户不许重复行）
  const accRows = rows.value.filter(r => r.accountId != null || Number(r.amount || 0) > 0)
  if (accRows.length === 0) { ElMessage.warning('请至少添加一个收款账户'); return }
  for (let i = 0; i < accRows.length; i++) {
    if (accRows[i].accountId == null) { ElMessage.warning(`收款账户第 ${i + 1} 行未选择账户`); return }
    if (!(Number(accRows[i].amount) > 0)) { ElMessage.warning(`收款账户第 ${i + 1} 行金额必须大于 0`); return }
  }
  const ids = accRows.map(r => Number(r.accountId))
  if (new Set(ids).size !== ids.length) { ElMessage.warning('同一账户请合并为一行（账户不允许重复）'); return }
  // 核销校验：开关关闭 ⇒ 一律不核销；打开 ⇒ 每行必须选应收，且核销合计 ≤ 收款合计
  const useItems = writeOff.value ? items.value : []
  if (writeOff.value) {
    if (useItems.length === 0) { ElMessage.warning('已打开「本次核销」：请添加核销明细（或关闭开关只记收款）'); return }
    for (let i = 0; i < useItems.length; i++) {
      if (!useItems[i].receivableId) { ElMessage.warning(`核销明细第 ${i + 1} 行未选择应收单据`); return }
    }
    if (settledTotal.value > receivedTotal.value) {
      ElMessage.warning(`核销合计 ${fmt(settledTotal.value)} 不能超过收款合计 ${fmt(receivedTotal.value)}`); return
    }
  }
  saving.value = true
  try {
    await createReceipt({
      receipt: { ...form },
      accounts: accRows.map(r => ({ accountId: Number(r.accountId), amount: Number(r.amount), remark: r.remark })),
      items: useItems.map(it => ({ receivableId: it.receivableId, receivableBillNo: it.receivableBillNo, thisAmount: Number(it.thisAmount || 0), remark: it.remark }))
    })
    ElMessage.success('已保存')
    router.push('/finance/receipt')
  } catch { /* 提示由拦截器统一给出 */ } finally { saving.value = false }
}
onMounted(loadAccounts)
</script>

<template>
  <div class="p">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">新增收款单</span>
          <el-button @click="router.push('/finance/receipt')">返回</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="90px" style="max-width:1000px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="主体类型" required>
            <el-radio-group v-model="form.subjectType" @change="onSubjectTypeChange">
              <el-radio-button :value="SubjectType.CUSTOMER">客户收款</el-radio-button>
              <el-radio-button :value="SubjectType.SUPPLIER">供应商收款</el-radio-button>
            </el-radio-group>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="form.subjectType === SubjectType.SUPPLIER ? '供应商' : '客户'" required>
            <RemoteSelect v-if="form.subjectType === SubjectType.SUPPLIER" v-model="form.supplierId" :fetch="fetchSuppliers" placeholder="请选择" style="width:100%" @change="() => onPartnerChange()" />
            <RemoteSelect v-else v-model="form.customerId" :fetch="fetchCustomers" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.customerId = undefined; router.push('/inventory/customer'); return } onPartnerChange() }"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
          </el-form-item></el-col>
          <!-- 欠款汇总（2026-09-29 用户口径）：选完客户/供应商后显示 **总欠款 + 到期欠款**；
               口径 = 后端 ReceivableQuery.partySummary（严格镜像应付侧：到期 = due_date < 今天，当天不算、
               无到期日不计入 ⇒ 故有"其中 N 张未约定到期日"这句解释，避免被当成算错） -->
          <el-col :span="24" v-if="summary">
            <el-form-item label="欠款情况">
              <div class="party-summary" style="display:flex;gap:18px;align-items:center;flex-wrap:wrap;font-size:var(--app-font-base)">
                <span v-if="Number(summary.billCount || 0) === 0" style="color:var(--app-text-secondary)">
                  该{{ form.subjectType === SubjectType.SUPPLIER ? '供应商' : '客户' }}当前无欠款
                </span>
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
          <el-col :span="12"><el-form-item label="收款日期"><el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2"/></el-form-item></el-col>
        </el-row>

        <!-- ===== 收款账户（2026-09-29 口径①：可多账户分款）===== -->
        <el-divider>
          收款账户（可分多账户收款，如 A 账户 50 + B 账户 100）
        </el-divider>
        <div style="margin-bottom:8px;display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap">
          <el-button type="primary" @click="addRow">添加账户</el-button>
          <!-- 收款金额 = 分款合计（只读，后端同样按分款合计落库 amount） -->
          <span style="font-size:var(--app-font-base)">
            收款金额：<b style="font-size:var(--app-font-lg);color:var(--app-color-primary)">¥{{ fmt(receivedTotal) }}</b>
            <span style="color:var(--app-text-secondary)">（{{ usedAccountCount }} 个账户）</span>
          </span>
        </div>
        <el-table :data="rows" border>
          <el-table-column label="收款账户" min-width="200">
            <template #default="{row}">
              <el-select v-model="row.accountId" placeholder="选择收款账户" filterable clearable style="width:100%">
                <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id"/>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="收款金额" width="160">
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
              {{ writeOff ? '（选应收单据核销，核销合计 ≤ 收款金额）' : '（关闭＝只记收款不核销，全额作为预收/预付挂账）' }}
            </span>
          </span>
        </el-divider>
        <template v-if="writeOff">
          <div style="margin-bottom:8px"><el-button type="primary" @click="addItem">添加核销项</el-button></div>
          <el-table :data="items" border>
            <el-table-column :label="form.subjectType === SubjectType.SUPPLIER ? '应收单据（供应商应收，如应付转应收）' : '应收单据（客户未结清应收）'" min-width="260">
              <template #default="{row}">
                <el-select v-model="row.receivableId" placeholder="选择应收单据" filterable style="width:100%" @change="(v:number)=>onReceivableChange(v,row)">
                  <el-option v-for="u in unpaid" :key="u.id" :label="`${u.billNo} (未收:${u.unpaidAmount},到期:${u.dueDate || '-'})`" :value="u.id ?? ''"/>
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
              未核销差额 <b>¥{{ fmt(unsettled) }}</b> 将作为预收/未核销余额挂账（审核后生成预收台账）
            </span>
          </div>
        </template>
        <el-alert v-else type="info" :closable="false" show-icon style="margin-top:4px"
          :title="`本单不核销应收：收款金额 ¥${fmt(receivedTotal)} 将全额作为预收（我方欠${form.subjectType === SubjectType.SUPPLIER ? '对方' : '客户'}）挂账，审核后可在应收台账里抵扣或退款`" />
      </el-form>

      <div style="margin-top:12px;display:flex;gap:8px;justify-content:flex-end;max-width:1000px">
        <el-button @click="router.push('/finance/receipt')">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>.p{display:flex;flex-direction:column;gap:12px}</style>
