<script setup lang="ts">
import { computed, reactive, ref, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOverdraftNeedConfirm, confirmOverdraft } from '@/utils/overdraftConfirm'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { localDate } from '@/utils/date'
import PageShell from '@/components/PageShell.vue'
import {
  getExpense, updateExpense, auditExpense, unAuditExpense, cancelExpense,
  getAccountPage, type FinanceExpense, type FinanceAccount,
} from '@/api/finance'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { ExpenseTypeLabel as EXPENSE_TYPE_LABELS } from '@/api/enums'

/**
 * 费用管理详情（2026-09-24 用户口径：草稿态在详情页就地改+存，列表不再给「编辑」/「反审核」）
 *
 * 补这个详情页的原因（此前"单据但无详情页"的三个缺口之一）：
 *  · 列表原来靠**弹窗**新增/编辑，草稿的单据级操作（改/审核/反审核/作废）全挤在列表行内 ⇒ 撤销类操作（反审核）
 *    会生成「费用冲正」流水把钱冲回账户，风险高且原因（哪张单、哪个账户、多少钱）只在单据上下文里说得清；
 *  · 结构对齐其它单据详情：`head` 只读快照 + `form` 可编辑副本（仅草稿态）；字段/校验/payload 与列表弹窗一致。
 */
const route = useRoute()
const router = useRouter()
const id = () => Number(route.params.id)
const loading = ref(false)
const acting = ref(false)
const saving = ref(false)

const head = ref<FinanceExpense>({})
const isDraft = computed(() => head.value.status === DocStatus.DRAFT)
const isAudited = computed(() => head.value.status === DocStatus.AUDITED)
/**
 * F7-235（2026-09-29 审核批 C）：费用动作权限与后端**同源两码任一**
 * （`ApiPermGuard.rule("/api/finance/expense", "finance:expense", "finance:cashflow")`）。
 */
const userStore = useUserStore()
const canExpense = computed(() => userStore.hasPerm('finance:expense') || userStore.hasPerm('finance:cashflow'))

/** 可编辑副本（白名单：单号/状态/账户名回显/制单人 不回传；金额与账户由后端复核） */
const form = reactive({ expenseType: 'OFFICE', amount: undefined as number | undefined, expenseDate: localDate(), accountId: undefined as any, remark: '', taxIncluded: 0 as number, taxRate: 0 as number })

// 2026-10-09（V7 含税）：与研发支出登记/采购单同口径 —— 关闭 = 未含税（默认）；打开时税率默认 13%；
// **金额本身不变**（含税时金额即含税总额，扣款就是这个数），税额只做拆分展示。
function onTaxSwitch(v: any) {
  form.taxIncluded = v ? 1 : 0
  form.taxRate = v ? (form.taxRate || 13) : 0
}
const taxAmount = computed(() => {
  if (form.taxIncluded !== 1) return 0
  const a = Number(form.amount || 0); const r = Number(form.taxRate || 0)
  if (!(a > 0) || !(r > 0)) return 0
  return Math.round((a * r) / (100 + r) * 100) / 100
})
const netAmount = computed(() => Math.max(0, Number(form.amount || 0) - taxAmount.value))
/** 只读态直接展示库里存的税额（含税时才显示；未含税视为 0） */
const headTax = computed(() => (head.value.taxIncluded === 1 ? Number(head.value.taxAmount || 0) : 0))

const accounts = ref<FinanceAccount[]>([])
async function loadAccounts() {
  try { const r: any = await getAccountPage({ pageSize: 200 }); accounts.value = (r?.records || []).filter((a: any) => a.status === 1) }
  catch { accounts.value = [] }
}

async function loadData() {
  loading.value = true
  try {
    head.value = (await getExpense(id())) || {}
    form.expenseType = head.value.expenseType || 'OFFICE'
    form.amount = head.value.amount
    form.expenseDate = head.value.expenseDate ? String(head.value.expenseDate).slice(0, 10) : localDate()
    form.accountId = head.value.accountId ?? undefined
    form.remark = head.value.remark || ''
    // 2026-10-09（V7）：含税口径按库里现状回填（未含税 ⇒ 开关关闭、税率 0）
    form.taxIncluded = head.value.taxIncluded === 1 ? 1 : 0
    form.taxRate = Number(head.value.taxRate || 0)
  } catch { head.value = {} }
  // F7-237（2026-09-29 审核批 C）：原先**只有 finally 没有 catch** ⇒ 详情加载失败会成为未处理的
  // Promise rejection，页面状态半新半旧（消息由拦截器弹出，但页面无兜底）。
  finally { loading.value = false }
}

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtDate(v?: string) { return v ? String(v).slice(0, 10) : '' }

/** 保存（与列表弹窗 save() 同一套校验与 payload；后端 update 自带「只有草稿可编辑」守卫） */
async function doSave() {
  if (!form.expenseType) { ElMessage.warning('请选择费用类型'); return }
  if (!form.amount || form.amount <= 0) { ElMessage.warning('费用金额必须大于 0'); return }
  // F7-232 配套：报损损失（LOSS）= 非资金费用 ⇒ 不要求账户，提交前清空（后端同口径会拒绝带账户的 LOSS）
  if (form.expenseType === 'LOSS') form.accountId = undefined as any
  if (form.expenseType !== 'LOSS' && !form.accountId) { ElMessage.warning('请选择支出账户'); return }
  saving.value = true
  try {
    await updateExpense({
      id: id(), expenseType: form.expenseType, amount: form.amount,
      expenseDate: form.expenseDate, accountId: form.accountId, remark: form.remark,
      // 2026-10-09（V7 含税）：开关与税率随草稿一起保存；税额由后端重算（金额不变）
      taxIncluded: form.taxIncluded, taxRate: form.taxRate,
    } as FinanceExpense)
    ElMessage.success('已保存')
    await loadData()
  } catch (e: any) { ElMessage.error(e?.msg || e?.message || '保存失败') } finally { saving.value = false }
}

async function doAudit(allowOverdraft = false) {
  try {
    await ElMessageBox.confirm(`确认审核费用单 ${head.value.expenseNo}？审核后将从「${head.value.accountName}」扣款 ${fmt(head.value.amount)} 元`, '审核确认', { type: 'warning' })
  } catch { return }
  acting.value = true
  // F7-237：接口调用补 catch（原先仅 finally ⇒ 失败无局部兜底、产生未处理 rejection）
  try { await auditExpense(id(), allowOverdraft); ElMessage.success('已审核'); await loadData() }
  catch (e: any) {
    // 2026-10-09：余额不足 ⇒ 业务码 409（**钱没动**）⇒ 弹确认框；用户确认后带标志重发（账户可扣成负数，流水留痕）
    if (isOverdraftNeedConfirm(e) && await confirmOverdraft(e.msg)) { acting.value = false; return doAudit(true) }
    // 其它失败：提示由拦截器统一给出
  }
  finally { acting.value = false }
}

async function doUnAudit() {
  try { await ElMessageBox.confirm('反审核将生成「费用冲正」流水把钱冲回账户，确认继续？', '反审核确认', { type: 'warning' }) } catch { return }
  acting.value = true
  try { await unAuditExpense(id()); ElMessage.success('已反审核'); await loadData() }
  catch { /* 提示由拦截器统一给出 */ }
  finally { acting.value = false }
}

async function doCancel() {
  try { await ElMessageBox.confirm('确认作废该费用单？', '作废确认', { type: 'warning' }) } catch { return }
  acting.value = true
  try {
    await cancelExpense(id())
    ElMessage.success('已作废')
    router.push('/finance/expense')
  } catch { /* 提示由拦截器统一给出 */ }
  finally { acting.value = false }
}

// 单据数据每次进入都重新拉取（keep-alive 下 onMounted 不会再触发）
onActivated(() => { loadData(); loadAccounts() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/finance/expense">
    <template #actions>
      <!-- 草稿：保存(主) + 审核 + 作废（2026-09-24 用户口径：草稿态就地编辑）
           F7-235：四个动作按 finance:expense|finance:cashflow（后端同源两码任一）显示 -->
      <el-button type="primary" v-if="isDraft && canExpense" :loading="saving" @click="doSave">保存</el-button>
      <!-- 2026-10-09：必须显式调用（不能直接写 `@click="doAudit"` —— 那样 MouseEvent 会落到 allowOverdraft 参数上、
           真值 ⇒ **绕过余额不足确认框直接放行** ✗；vue-tsc 当场报 TS2322 抓到的就是这个） -->
      <el-button type="success" v-if="isDraft && canExpense" :loading="acting" @click="doAudit()">审核</el-button>
      <el-button type="danger" v-if="isDraft && canExpense" :loading="acting" @click="doCancel">作废</el-button>
      <!-- 反审核：生成「费用冲正」流水把钱冲回账户（撤销类操作，2026-09-24 从列表移入详情） -->
      <el-button type="warning" v-if="isAudited && canExpense" :loading="acting" @click="doUnAudit">反审核</el-button>
    </template>

    <el-card shadow="never">
      <!-- ============ 草稿：可编辑（字段/校验/payload 与列表弹窗一致） ============ -->
      <el-form v-if="isDraft" :model="form" label-width="var(--app-label-width)">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="费用单号">{{ head.expenseNo }}</el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态"><el-tag :type="DocStatusTag[head.status || 'DRAFT']" size="small">{{ DocStatusLabel[head.status || 'DRAFT'] || head.status }}</el-tag></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="制单人">{{ head.createByName || '—' }}</el-form-item></el-col>
          <el-col :span="8">
            <el-form-item required label="费用类型">
              <el-select v-model="form.expenseType" style="width:100%">
                <el-option v-for="(lb, code) in EXPENSE_TYPE_LABELS" :key="code" :label="lb" :value="code" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="金额">
              <el-input-number v-model="form.amount" :min="0.01" :precision="2" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <!-- 2026-10-09（V7 含税）：草稿态可就地改含税口径（与研发支出登记/费用新增同款交互）；
               税额由后端重算，**金额与扣款口径不变**。 -->
          <el-col :span="8">
            <el-form-item label="是否含税">
              <el-switch :model-value="form.taxIncluded === 1" @change="onTaxSwitch" />
              <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" :controls="false"
                :disabled="form.taxIncluded !== 1" style="width:92px;margin-left:8px" placeholder="税率%" />
            </el-form-item>
            <div v-if="form.taxIncluded === 1" style="margin:-8px 0 8px 80px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
              税额 {{ taxAmount.toFixed(2) }}，不含税 {{ netAmount.toFixed(2) }}（金额按含税口径填，扣款仍为 {{ Number(form.amount || 0).toFixed(2) }}）
            </div>
          </el-col>
          <el-col :span="8">
            <el-form-item label="费用日期">
              <el-date-picker v-model="form.expenseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="支出账户">
              <el-select v-model="form.accountId" placeholder="请选择" style="width:100%">
                <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName}（余额 ${fmt(a.balance)}）`" :value="a.id ?? ''" />
              
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/finance/account')">+ 新增</div></template>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
        <el-alert type="info" :closable="false" show-icon style="margin-top:4px"
          title="审核后从所选账户扣款并生成一条「费用支出」流水；反审核会生成「费用冲正」把资金冲回账户。" />
      </el-form>

      <!-- ============ 已审核 / 已作废：只读 ============ -->
      <el-descriptions v-else :column="3" border size="small">
        <el-descriptions-item label="费用单号">{{ head.expenseNo }}</el-descriptions-item>
        <el-descriptions-item label="费用类型">
          <el-tag size="small">{{ EXPENSE_TYPE_LABELS[head.expenseType || ''] || head.expenseType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="金额"><span style="color:var(--app-color-danger);font-weight:600">{{ fmt(head.amount) }}</span></el-descriptions-item>
        <!-- 2026-10-09（V7 含税）：只读态只在**含税**时显示这一行（默认未含税 ⇒ 不显示，避免噪声/误导） -->
        <el-descriptions-item v-if="head.taxIncluded === 1" label="含税情况">
          含税 · 税率 {{ Number(head.taxRate || 0) }}% · 税额 {{ headTax.toFixed(2) }} · 不含税 {{ Math.max(0, Number(head.amount || 0) - headTax).toFixed(2) }}
        </el-descriptions-item>
        <el-descriptions-item label="费用日期">{{ fmtDate(head.expenseDate) }}</el-descriptions-item>
        <el-descriptions-item label="支出账户">{{ head.accountName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="DocStatusTag[head.status || '']" size="small">{{ DocStatusLabel[head.status || ''] || head.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="制单人">{{ head.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ head.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ head.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </PageShell>
</template>

<style scoped>
:deep(.el-card__body) { padding: 16px; }
</style>
