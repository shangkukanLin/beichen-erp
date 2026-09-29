<script setup lang="ts">
// 收款单详情（2026-09-23 用户要求：原「收款单详情」50% 抽屉改为独立页面）
// —— 按 id 回源单头 + 分款明细 + 核销明细；列表行点击 / 行内「详情」按钮都跳到这里。
//
// 2026-09-29 用户口径：
//   ① **多账户分款**：新增「收款账户」卡片（逐行 = 账户 + 金额），单头「账户」只显示首行快照；
//   ② **核销开关**：核销明细可为空（开关关闭）⇒ 未核销余额显示出来（审核后落预收/预付台账 ADVANCE）；
//   ③ **草稿可编辑**：草稿态**就地可编辑**（家规：草稿在详情页改+存、列表不给「编辑」）——
//      表单字段/校验/payload 与新增页 `receipt-add.vue` 完全一致；已审核/已作废仍为只读。
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocStatus, DocStatusLabel, SubjectType, SubjectTypeLabel } from '@/api/enums'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { updateReceipt, getReceiptUnpaidReceivables, getReceiptPartySummary, type FinanceReceiptAccount, type FinanceReceiptItem, type FinanceReceivable, type PartyDebtSummary } from '@/api/finance'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
/** 分款明细（2026-09-29 多账户）：只读态用它渲染「收款账户」卡片 */
const accounts = ref<any[]>([])
/** 账户下拉选项（草稿态可编辑时用） */
const accountOptions = ref<{id:number;accountName:string}[]>([])

const isDraft = computed(() => detail.value?.status === DocStatus.DRAFT)
/** 草稿态可编辑副本（与 receipt-add.vue 同构） */
const form = reactive<any>({ subjectType: SubjectType.CUSTOMER, customerId: undefined, supplierId: undefined, receiptDate: '', remark: '' })
const editRows = ref<FinanceReceiptAccount[]>([])
const editItems = ref<FinanceReceiptItem[]>([])
/** 核销开关（2026-09-29 口径②）：编辑态初值 = 本单已核销明细是否非空 */
const writeOff = ref(false)
const unpaid = ref<FinanceReceivable[]>([])

const receivedTotal = computed(() => editRows.value.reduce((s, r) => s + Number(r.amount || 0), 0))
const settledTotal = computed(() => (writeOff.value ? editItems.value.reduce((s, r) => s + Number(r.thisAmount || 0), 0) : 0))
const unsettled = computed(() => Math.max(0, receivedTotal.value - settledTotal.value))
/** 只读态的未核销余额 = 收款金额 − 已核销合计 */
const readUnsettled = computed(() => {
  const settled = (items.value || []).reduce((s: number, r: any) => s + Number(r.thisAmount || 0), 0)
  return Math.max(0, Number(detail.value?.amount || 0) - settled)
})

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | undefined {
  if (s === DocStatus.DRAFT) return 'warning'
  if (s === DocStatus.AUDITED) return 'success'
  if (s === DocStatus.CANCELLED) return 'info'
  return undefined
}
/** 往来单位：客户收款看客户名，供应商收款（如应付转应收）看供应商名 */
function subjectLabel(d: any) {
  return d?.subjectType === SubjectType.SUPPLIER ? (d?.supplierName || '—') : (d?.customerName || '—')
}
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadAccountOptions() {
  try { accountOptions.value = await request.get<any, any>('/finance/account/list') || [] } catch { accountOptions.value = [] }
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
 * 主体欠款汇总（2026-09-29 用户口径「新增页 + 详情页草稿态」都显示）：**总欠款 + 到期欠款**。
 * <p>口径与新增页/后端 {@code ReceivableQuery.partySummary} 完全一致（到期 = `due_date < 今天`，当天不算；
 * 无到期日不计入到期）。</p>
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
function addRow() { editRows.value.push({ accountId: undefined, amount: 0, remark: '' }) }
function removeRow(i: number) { editRows.value.splice(i, 1) }
function addItem() { editItems.value.push({ receivableId: undefined, receivableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { editItems.value.splice(i, 1) }
function onReceivableChange(val: number, row: FinanceReceiptItem) {
  const r = unpaid.value.find(x => x.id === val)
  if (r) { row.receivableId = r.id; row.receivableBillNo = r.billNo; row.thisAmount = r.unpaidAmount }
}

async function load() {
  const id = route.params.id
  loading.value = true
  try {
    const [h, its, accs] = await Promise.all([
      request.get<any, any>('/finance/receipt/' + id),
      request.get<any, any>('/finance/receipt/' + id + '/items').catch(() => []),
      request.get<any, any>('/finance/receipt/' + id + '/accounts').catch(() => [])
    ])
    detail.value = h || {}
    items.value = Array.isArray(its) ? its : ((its as any)?.records || [])
    accounts.value = Array.isArray(accs) ? accs : ((accs as any)?.records || [])
    // 草稿态：把单据灌进可编辑副本（字段/校验/payload 与新增页一致）
    if (isDraft.value) {
      Object.assign(form, {
        subjectType: detail.value.subjectType || SubjectType.CUSTOMER,
        customerId: detail.value.customerId, supplierId: detail.value.supplierId,
        receiptDate: detail.value.receiptDate || '', remark: detail.value.remark || ''
      })
      editRows.value = accounts.value.length
        ? accounts.value.map((a: any) => ({ accountId: a.accountId, amount: Number(a.amount || 0), remark: a.remark || '' }))
        : [{ accountId: detail.value.accountId, amount: Number(detail.value.amount || 0), remark: '' }]
      editItems.value = items.value.map((i: any) => ({ receivableId: i.receivableId, receivableBillNo: i.receivableBillNo, thisAmount: Number(i.thisAmount || 0), remark: i.remark || '' }))
      writeOff.value = editItems.value.length > 0
      await loadAccountOptions()
      if (form.customerId || form.supplierId) await onPartnerChange()
    }
  } catch { detail.value = {}; items.value = []; accounts.value = [] } finally { loading.value = false }
}
onMounted(load)

/** 草稿保存（家规：草稿在详情页改+存）：校验与新增页逐条一致，分款/核销明细整体替换 */
async function save() {
  if (form.subjectType === SubjectType.SUPPLIER && !form.supplierId) { ElMessage.warning('请选择供应商'); return }
  if (form.subjectType !== SubjectType.SUPPLIER && !form.customerId) { ElMessage.warning('请选择客户'); return }
  const accRows = editRows.value.filter(r => r.accountId != null || Number(r.amount || 0) > 0)
  if (accRows.length === 0) { ElMessage.warning('请至少添加一个收款账户'); return }
  for (let i = 0; i < accRows.length; i++) {
    if (accRows[i].accountId == null) { ElMessage.warning(`收款账户第 ${i + 1} 行未选择账户`); return }
    if (!(Number(accRows[i].amount) > 0)) { ElMessage.warning(`收款账户第 ${i + 1} 行金额必须大于 0`); return }
  }
  const ids = accRows.map(r => Number(r.accountId))
  if (new Set(ids).size !== ids.length) { ElMessage.warning('同一账户请合并为一行（账户不允许重复）'); return }
  const useItems = writeOff.value ? editItems.value : []
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
    await updateReceipt(Number(route.params.id), {
      receipt: { ...form },
      accounts: accRows.map(r => ({ accountId: Number(r.accountId), amount: Number(r.amount), remark: r.remark })),
      items: useItems.map(it => ({ receivableId: it.receivableId, receivableBillNo: it.receivableBillNo, thisAmount: Number(it.thisAmount || 0), remark: it.remark }))
    })
    ElMessage.success('已保存')
    await load()
  } catch { /* 提示由拦截器统一给出 */ } finally { saving.value = false }
}

/** 删掉最后一行分款/核销后不留空表：与新增页同款按钮语义 */
function back() { router.push('/finance/receipt') }
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原「返回」按钮已删） -->
  <PageShell :loading="loading" back-fallback="/finance/receipt">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">收款单详情 — {{ detail.code || '' }}
            <el-tag v-if="isDraft" type="warning" size="small" style="margin-left:8px">草稿态可直接修改</el-tag>
          </span>
          <div style="display:flex;gap:8px">
            <el-button v-if="isDraft" type="primary" :loading="saving" @click="save">保存</el-button>
            <el-button @click="back">返回列表</el-button>
          </div>
        </div>
      </template>

      <!-- ============ 草稿态：就地可编辑（字段/校验/payload 与 receipt-add.vue 完全一致） ============ -->
      <el-form v-if="isDraft" :model="form" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="主体类型" required>
            <el-radio-group v-model="form.subjectType" disabled>
              <el-radio-button :value="SubjectType.CUSTOMER">客户收款</el-radio-button>
              <el-radio-button :value="SubjectType.SUPPLIER">供应商收款</el-radio-button>
            </el-radio-group>
            <span style="margin-left:8px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">主体类型不可改（换主体请作废重开）</span>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="form.subjectType === SubjectType.SUPPLIER ? '供应商' : '客户'" required>
            <RemoteSelect v-if="form.subjectType === SubjectType.SUPPLIER" v-model="form.supplierId" :fetch="fetchSuppliers" placeholder="请选择" style="width:100%" @change="() => onPartnerChange()" />
            <RemoteSelect v-else v-model="form.customerId" :fetch="fetchCustomers" placeholder="请选择" style="width:100%" @change="() => onPartnerChange()" />
          </el-form-item></el-col>
          <!-- 欠款汇总（2026-09-29 用户口径「新增页 + 详情页草稿态」）：总欠款 + 到期欠款，口径同新增页 -->
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

        <el-divider>收款账户（可分多账户收款，如 A 账户 50 + B 账户 100）</el-divider>
        <div style="margin-bottom:8px;display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap">
          <el-button type="primary" @click="addRow">添加账户</el-button>
          <span style="font-size:var(--app-font-base)">收款金额：<b style="font-size:var(--app-font-lg);color:var(--app-color-primary)">¥{{ fmt(receivedTotal) }}</b></span>
        </div>
        <el-table :data="editRows" border>
          <el-table-column label="收款账户" min-width="200">
            <template #default="{row}">
              <el-select v-model="row.accountId" placeholder="选择收款账户" filterable clearable style="width:100%">
                <el-option v-for="a in accountOptions" :key="a.id" :label="a.accountName" :value="a.id"/>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="收款金额" width="160">
            <template #default="{row}"><el-input-number v-model="row.amount" :min="0" :precision="2" controls-position="right" style="width:100%"/></template>
          </el-table-column>
          <el-table-column label="备注" min-width="160"><template #default="{row}"><el-input v-model="row.remark"/></template></el-table-column>
          <el-table-column label="操作" width="70" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeRow($index)">删除</el-button></template></el-table-column>
        </el-table>

        <el-divider>
          <span style="display:inline-flex;align-items:center;gap:8px">
            核销明细
            <el-switch v-model="writeOff" active-text="本次核销" inline-prompt style="--el-switch-on-color:var(--app-color-primary)" />
            <span style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">
              {{ writeOff ? '（选应收单据核销，核销合计 ≤ 收款金额）' : '（关闭＝只记收款不核销，全额作为预收/预付挂账）' }}
            </span>
          </span>
        </el-divider>
        <template v-if="writeOff">
          <div style="margin-bottom:8px"><el-button type="primary" @click="addItem">添加核销项</el-button></div>
          <el-table :data="editItems" border>
            <el-table-column label="应收单据" min-width="260">
              <template #default="{row}">
                <el-select v-model="row.receivableId" placeholder="选择应收单据" filterable style="width:100%" @change="(v:number)=>onReceivableChange(v,row)">
                  <el-option v-for="u in unpaid" :key="u.id" :label="`${u.billNo} (未收:${u.unpaidAmount},到期:${u.dueDate || '-'})`" :value="u.id ?? ''"/>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="核销金额" width="160"><template #default="{row}"><el-input-number v-model="row.thisAmount" :min="0" :precision="2" controls-position="right" style="width:100%"/></template></el-table-column>
            <el-table-column label="操作" width="70" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
          </el-table>
          <div style="margin-top:8px;font-size:var(--app-font-base)">
            核销合计：<b>¥{{ fmt(settledTotal) }}</b>
            <span v-if="unsettled > 0" style="margin-left:12px;color:var(--app-color-warning)">未核销差额 <b>¥{{ fmt(unsettled) }}</b> 审核后作为预收挂账</span>
          </div>
        </template>
      </el-form>

      <!-- ============ 已审核 / 已作废：只读 ============ -->
      <el-descriptions v-else :column="3" border>
        <el-descriptions-item label="单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ DocStatusLabel[String(detail.status)] || detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="主体类型">{{ SubjectTypeLabel[detail.subjectType || ''] || '客户' }}</el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ subjectLabel(detail) }}</el-descriptions-item>
        <el-descriptions-item label="账户">{{ detail.accountName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.receiptDate }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmt(detail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="未核销余额">{{ fmt(readUnsettled) }}</el-descriptions-item>
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 收款账户（分款明细）：单头「账户」只显示首行快照，这里是全貌 -->
    <el-card v-if="!isDraft" shadow="never">
      <template #header><span style="font-weight:600">收款账户（分款明细）</span></template>
      <el-table :data="accounts" border>
        <el-table-column prop="accountName" label="收款账户" min-width="180"/>
        <el-table-column label="收款金额" width="150" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160"/>
      </el-table>
    </el-card>

    <el-card v-if="!isDraft" shadow="never">
      <template #header><span style="font-weight:600">核销明细</span></template>
      <el-table :data="items" border>
        <el-table-column prop="receivableBillNo" label="应收单据" min-width="150"/>
        <el-table-column prop="thisAmount" label="核销金额" width="130" align="right"><template #default="{row}">{{ fmt(row.thisAmount) }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160"/>
      </el-table>
      <div v-if="readUnsettled > 0" style="margin-top:8px;font-size:var(--app-font-base);color:var(--app-color-warning)">
        未核销差额 <b>¥{{ fmt(readUnsettled) }}</b>（审核时已作为预收/未核销余额挂账：应收台账里的 ADVANCE 行）
      </div>
    </el-card>
  </PageShell>
</template>

<style scoped>/* 根容器已统一到全局骨架（PageShell）；原 .p 局部样式已删除 */</style>
