<script setup lang="ts">
// 付款单详情（2026-09-23 用户要求：原「付款单详情」50% 抽屉改为独立页面）
// —— 按 id 回源单头 + 分款明细 + 核销明细；凭证上传能力一并从抽屉迁到这里。
//
// 2026-09-29 用户口径（付款侧与收款侧**对称**）：
//   ① **多账户分款**：新增「付款账户」卡片（逐行 = 账户 + 金额），单头「账户」只显示首行快照；
//   ② **核销开关**：核销明细可为空（开关关闭）⇒ 未核销余额显示出来（审核后落预付台账 ADVANCE）；
//   ③ **草稿可编辑**：草稿态**就地可编辑**（家规：草稿在详情页改+存、列表不给「编辑」）——
//      表单字段/校验/payload 与新增页 `payment-add.vue` 完全一致；已审核/已作废仍为只读。
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocStatus, DocStatusLabel } from '@/api/enums'
import { TYPE_MAP, TYPE_TAG } from '@/constants/supplier'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { updatePayment, getPaymentUnpaidPayables, type FinancePaymentAccount, type FinancePaymentItem } from '@/api/finance'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const attachSaving = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
/** 分款明细（2026-09-29 多账户）：只读态用它渲染「付款账户」卡片 */
const accounts = ref<any[]>([])
/** 账户下拉选项（草稿态可编辑时用） */
const accountOptions = ref<{id:number;accountName:string}[]>([])

const isDraft = computed(() => detail.value?.status === DocStatus.DRAFT)
/** 草稿态可编辑副本（与 payment-add.vue 同构） */
const form = reactive<any>({ supplierId: undefined, paymentDate: '', remark: '', attachUrl: '' })
const editRows = ref<FinancePaymentAccount[]>([{ accountId: undefined, amount: 0, remark: '' }])
const writeOff = ref(false)
const editItems = ref<FinancePaymentItem[]>([])
const unpaid = ref<any[]>([])

/** 付款金额 = 分款合计（与新增页/后端同一口径：主表 amount） */
const editPaidTotal = computed(() => editRows.value.reduce((s, r) => s + Number(r.amount || 0), 0))
/** 核销合计（草稿态；开关关闭恒为 0） */
const editSettledTotal = computed(() => (writeOff.value ? editItems.value.reduce((s, r) => s + Number(r.thisAmount || 0), 0) : 0))
/** 只读态的未核销差额 = 付款总额 − Σ核销额（> 0 ⇒ 审核时已落预付台账） */
const readUnsettled = computed(() => {
  const settled = items.value.reduce((s, r) => s + Number(r.thisAmount || 0), 0)
  return Math.max(0, Number(detail.value?.amount || 0) - settled)
})
/** 多账户提示：>1 时单头「账户」只是首行快照 */
const accountCount = computed(() => accounts.value.length)

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | undefined {
  if (s === DocStatus.DRAFT) return 'warning'
  if (s === DocStatus.AUDITED) return 'success'
  if (s === DocStatus.CANCELLED) return 'info'
  return undefined
}
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }
function openAttach(url: string) { window.open(url + '?inline=true') }
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })

async function loadAccountOptions() {
  try { accountOptions.value = await request.get<any, any>('/finance/account/list') || [] } catch { accountOptions.value = [] }
}
/** 草稿态：按当前供应商拉可核销的未结清应付（走付款页自身前缀，避免跨模块权限被拦） */
async function loadUnpaid() {
  const sid = form.supplierId
  if (sid == null) { unpaid.value = []; return }
  try { unpaid.value = await getPaymentUnpaidPayables(Number(sid)) || [] } catch { unpaid.value = [] }
}

async function load() {
  const id = route.params.id
  loading.value = true
  try {
    const [h, its, accs] = await Promise.all([
      request.get<any, any>('/finance/payment/' + id),
      request.get<any, any>('/finance/payment/' + id + '/items').catch(() => []),
      request.get<any, any>('/finance/payment/' + id + '/accounts').catch(() => [])
    ])
    detail.value = h || {}
    items.value = Array.isArray(its) ? its : ((its as any)?.records || [])
    accounts.value = Array.isArray(accs) ? accs : ((accs as any)?.records || [])
    if (detail.value?.status === DocStatus.DRAFT) {
      form.supplierId = detail.value.supplierId
      form.paymentDate = detail.value.paymentDate
      form.remark = detail.value.remark
      form.attachUrl = detail.value.attachUrl
      // 分款明细：有分款行就用它；老草稿（无分款行）退化成"主表首行 + 主表金额"一行
      editRows.value = accounts.value.length > 0
        ? accounts.value.map((a: any) => ({ accountId: a.accountId, amount: Number(a.amount || 0), remark: a.remark || '' }))
        : [{ accountId: detail.value.accountId, amount: Number(detail.value.amount || 0), remark: '' }]
      editItems.value = items.value.map((i: any) => ({ payableId: i.payableId, payableBillNo: i.payableBillNo, thisAmount: Number(i.thisAmount || 0), remark: i.remark || '' }))
      writeOff.value = editItems.value.length > 0
      await loadAccountOptions()
      if (form.supplierId) await loadUnpaid()
    }
  } catch { detail.value = {}; items.value = []; accounts.value = [] } finally { loading.value = false }
}
onMounted(load)

/** 分款/核销明细：增删行 */
function addRow() { editRows.value.push({ accountId: undefined, amount: 0, remark: '' }) }
function removeRow(i: number) { editRows.value.splice(i, 1) }
function addItem() { editItems.value.push({ payableId: undefined, payableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { editItems.value.splice(i, 1) }
function onPayableChange(val: number, row: FinancePaymentItem) {
  const r = unpaid.value.find(x => x.id === val)
  if (r) { row.payableId = r.id; row.payableBillNo = r.billNo; row.thisAmount = r.unpaidAmount }
}

/** 草稿保存（家规：草稿在详情页改+存）：校验与新增页逐条一致，分款/核销明细整体替换 */
async function save() {
  if (form.supplierId == null) { ElMessage.warning('请选择供应商'); return }
  const accRows = editRows.value.filter(r => r.accountId != null || Number(r.amount || 0) > 0)
  if (accRows.length === 0) { ElMessage.warning('请至少添加一个付款账户'); return }
  for (let i = 0; i < accRows.length; i++) {
    if (accRows[i].accountId == null) { ElMessage.warning(`付款账户第 ${i + 1} 行未选择账户`); return }
    if (!(Number(accRows[i].amount) > 0)) { ElMessage.warning(`付款账户第 ${i + 1} 行金额必须大于 0`); return }
  }
  const ids = accRows.map(r => Number(r.accountId))
  if (new Set(ids).size !== ids.length) { ElMessage.warning('同一账户请合并为一行（账户不允许重复）'); return }
  const useItems = writeOff.value ? editItems.value : []
  if (writeOff.value) {
    if (useItems.length === 0) { ElMessage.warning('已打开「本次核销」：请添加核销明细（或关闭开关只记付款）'); return }
    for (let i = 0; i < useItems.length; i++) {
      if (!useItems[i].payableId) { ElMessage.warning(`核销明细第 ${i + 1} 行未选择应付单据`); return }
    }
    if (editSettledTotal.value > editPaidTotal.value) {
      ElMessage.warning(`核销合计 ${fmt(editSettledTotal.value)} 不能超过付款合计 ${fmt(editPaidTotal.value)}`); return
    }
  }
  saving.value = true
  try {
    await updatePayment(Number(route.params.id), {
      payment: { supplierId: Number(form.supplierId), paymentDate: form.paymentDate, remark: form.remark, attachUrl: form.attachUrl },
      accounts: accRows.map(r => ({ accountId: Number(r.accountId), amount: Number(r.amount), remark: r.remark })),
      items: useItems.map(it => ({ payableId: it.payableId, payableBillNo: it.payableBillNo, thisAmount: Number(it.thisAmount || 0), remark: it.remark }))
    })
    ElMessage.success('已保存')
    await load()
  } catch { /* 提示由拦截器统一给出 */ } finally { saving.value = false }
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
    ElMessage.success('凭证已上传')
  } catch (e: any) { ElMessage.error(e?.message || '上传失败') } finally { attachSaving.value = false }
}
function back() { router.push('/finance/payment') }
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原「返回」按钮已删）；上传凭证按钮留在卡内 -->
  <PageShell :loading="loading" back-fallback="/finance/payment">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">付款单详情 — {{ detail.code || '' }}
            <el-tag v-if="isDraft" type="warning" size="small" style="margin-left:8px">草稿态可直接修改</el-tag>
          </span>
          <div style="display:flex;gap:8px">
            <el-button v-if="isDraft" type="primary" :loading="saving" @click="save">保存</el-button>
            <el-button @click="back">返回列表</el-button>
          </div>
        </div>
      </template>

      <!-- ============ 草稿态：就地可编辑（字段/校验/payload 与 payment-add.vue 完全一致） ============ -->
      <el-form v-if="isDraft" :model="form" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="状态"><el-tag :type="stType(detail.status)">{{ DocStatusLabel[detail.status ?? 0] || detail.status }}</el-tag></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商" required>
            <RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" placeholder="请选择" style="width:100%" @change="() => loadUnpaid()" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="主体类型">
            <el-tag v-if="detail.supplierType" :type="TYPE_TAG[detail.supplierType] || 'info'" size="small">{{ typeLabel(detail.supplierType) }}</el-tag>
            <span v-else style="color:var(--app-text-secondary)">—</span>
            <span style="margin-left:8px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">（主体类型按供应商标签固化，不可改）</span>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款日期"><el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <!-- 制单人 / 审核人（2026-09-23 单据详情口径）：草稿态也要看得见（只读信息，不进 payload） -->
          <el-col :span="12"><el-form-item label="制单人">{{ detail.createByName || '—' }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="审核人">{{ detail.auditorName || '—' }}</el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2"/></el-form-item></el-col>
        </el-row>

        <!-- 付款账户（2026-09-29 口径①：可多账户分款） -->
        <el-divider>付款账户（可分多账户付款，如 A 账户 50 + B 账户 100）</el-divider>
        <div style="margin-bottom:8px;display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap">
          <el-button type="primary" @click="addRow">添加账户</el-button>
          <span style="font-size:var(--app-font-base)">
            付款金额：<b style="font-size:var(--app-font-lg);color:var(--app-color-primary)">¥{{ fmt(editPaidTotal) }}</b>
            <span style="color:var(--app-text-secondary)">（{{ editRows.length }} 行）</span>
          </span>
        </div>
        <el-table :data="editRows" border>
          <el-table-column label="付款账户" min-width="200">
            <template #default="{row}">
              <el-select v-model="row.accountId" placeholder="选择付款账户" filterable clearable style="width:100%">
                <el-option v-for="a in accountOptions" :key="a.id" :label="a.accountName" :value="a.id"/>
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

        <!-- 核销明细（2026-09-29 口径②：开关项） -->
        <el-divider>
          <span style="display:inline-flex;align-items:center;gap:8px">
            核销明细
            <el-switch v-model="writeOff" active-text="本次核销" inline-prompt style="--el-switch-on-color:var(--app-color-primary)" />
            <span style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">
              {{ writeOff ? '（选应付单据核销，核销合计 ≤ 付款金额）' : '（关闭＝只记付款不核销，全额作为预付挂账）' }}
            </span>
          </span>
        </el-divider>
        <template v-if="writeOff">
          <div style="margin-bottom:8px"><el-button type="primary" @click="addItem">添加核销项</el-button></div>
          <el-table :data="editItems" border>
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
            核销合计：<b>¥{{ fmt(editSettledTotal) }}</b>
            <span v-if="editPaidTotal - editSettledTotal > 0" style="margin-left:12px;color:var(--app-color-warning)">
              未核销差额 <b>¥{{ fmt(editPaidTotal - editSettledTotal) }}</b> 将作为预付/未核销余额挂账（审核后生成预付台账）
            </span>
          </div>
        </template>
        <el-alert v-else type="info" :closable="false" show-icon style="margin-top:4px"
          :title="`本单不核销应付：付款金额 ¥${fmt(editPaidTotal)} 将全额作为预付（我方多付，供应商欠我方）挂账，审核后可在应付台账里抵扣或退款`" />

        <!-- 付款凭证：草稿态同样可上传（走 PUT /{id}/attach） -->
        <el-divider>付款凭证</el-divider>
        <div style="display:flex;align-items:center;gap:12px">
          <template v-if="detail.attachUrl">
            <el-image :src="detail.attachUrl" :preview-src-list="[detail.attachUrl]" fit="contain" style="width:80px;height:80px;border:1px solid #dcdfe6;border-radius:4px" preview-teleported />
            <el-link type="primary" @click="openAttach(detail.attachUrl)">查看原图</el-link>
          </template>
          <span v-else style="color:var(--app-text-secondary)">未上传</span>
          <label class="upload-btn">
            <input type="file" accept="image/*" style="display:none" @change="handleUploadAttach" />
            <el-button size="small" :loading="attachSaving">{{ detail.attachUrl ? '重新上传' : '上传凭证' }}</el-button>
          </label>
        </div>
      </el-form>

      <!-- ============ 只读态（已审核 / 已作废）：快照 + 分款明细 + 核销明细 ============ -->
      <el-descriptions v-else :column="3" border>
        <el-descriptions-item label="单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ DocStatusLabel[detail.status ?? 0] || detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="主体类型">
          <el-tag v-if="detail.supplierType" :type="TYPE_TAG[detail.supplierType] || 'info'" size="small">{{ typeLabel(detail.supplierType) }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <!-- 单头「账户」= 分款明细首行快照（多账户时注明看下方分款明细） -->
        <el-descriptions-item label="账户">
          {{ detail.accountName || '—' }}
          <span v-if="accountCount > 1" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">（{{ accountCount }} 个账户，见下方分款明细）</span>
        </el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.paymentDate }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmt(detail.amount) }}</el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="付款凭证" :span="2">
          <div style="display:flex;align-items:center;gap:12px">
            <template v-if="detail.attachUrl">
              <el-image :src="detail.attachUrl" :preview-src-list="[detail.attachUrl]" fit="contain" style="width:80px;height:80px;border:1px solid #dcdfe6;border-radius:4px" preview-teleported />
              <el-link type="primary" @click="openAttach(detail.attachUrl)">查看原图</el-link>
            </template>
            <span v-else style="color:var(--app-text-secondary)">未上传</span>
            <label class="upload-btn">
              <input type="file" accept="image/*" style="display:none" @change="handleUploadAttach" />
              <el-button size="small" :loading="attachSaving">{{ detail.attachUrl ? '重新上传' : '上传凭证' }}</el-button>
            </label>
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 付款账户（分款明细）：单头「账户」只显示首行快照，这里是全貌 -->
    <el-card v-if="!isDraft" shadow="never">
      <template #header><span style="font-weight:600">付款账户（分款明细）</span></template>
      <el-table :data="accounts" border>
        <el-table-column prop="accountName" label="付款账户" min-width="180"/>
        <el-table-column label="付款金额" width="150" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160"/>
      </el-table>
    </el-card>

    <el-card v-if="!isDraft" shadow="never">
      <template #header><span style="font-weight:600">核销明细</span></template>
      <el-table :data="items" border>
        <el-table-column prop="payableBillNo" label="应付单据" min-width="150"/>
        <el-table-column prop="thisAmount" label="核销金额" width="130" align="right"><template #default="{row}">{{ fmt(row.thisAmount) }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160"/>
      </el-table>
      <div v-if="readUnsettled > 0" style="margin-top:8px;font-size:var(--app-font-base);color:var(--app-color-warning)">
        未核销差额 <b>¥{{ fmt(readUnsettled) }}</b>（审核时已作为预付/未核销余额挂账：应付台账里的 ADVANCE 行）
      </div>
    </el-card>
  </PageShell>
</template>

<style scoped>/* 根容器已统一到全局骨架（PageShell）；原 .p 局部样式已删除（.upload-btn 仍在用，保留） */.upload-btn{display:inline-flex}</style>
