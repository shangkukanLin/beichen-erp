<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
import { getReceiptPage, auditReceipt, cancelReceipt, unAuditReceipt, type FinanceReceipt } from '@/api/finance'
import { SubjectType, SubjectTypeLabel, SourceBillDetailRoute } from '@/api/enums'

/**
 * 来源单号可跳转的目标前缀（2026-09-26 B9）：缺类型映射或无 sourceId 时返回空 ⇒ 列内保持纯文本。
 * 现金销售单自动收款（sourceBillType=SALE_ORDER）的 sourceId 即销售单 id ⇒ 可直达销售单详情。
 */
function sourceRoute(row: any): string {
  const base = SourceBillDetailRoute[row?.sourceBillType]
  return base && row?.sourceId != null ? base : ''
}
/** 来源单号 → 源单（有独立详情页直接进详情；无详情页的进列表并带 billId，与「库存流水」同口径） */
function goSource(row: any) {
  const base = sourceRoute(row)
  if (!base) return
  if (base.includes('/detail')) router.push(`${base}/${row.sourceId}`)
  else router.push({ path: base, query: { billId: String(row.sourceId), billType: row.sourceBillType } })
}

const query = reactive({ customerId: '' as string|number, subjectType: '', status: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceReceipt[]>([])
const customersOptions = ref<any[]>([])
const accounts = ref<{id:number;accountName:string}[]>([])
const suppliersOptions = ref<any[]>([])

const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadCustomersOptions() {
  try { const r: any = await fetchCustomers(''); customersOptions.value = r?.records || [] } catch { customersOptions.value = [] }
}
async function loadSuppliersOptions() {
  try { const r: any = await fetchSuppliers(''); suppliersOptions.value = r?.records || [] } catch { suppliersOptions.value = [] }
}

const statusOpts = [{l:DocStatusLabel[DocStatus.DRAFT],v:DocStatus.DRAFT},{l:DocStatusLabel[DocStatus.AUDITED],v:DocStatus.AUDITED},{l:DocStatusLabel[DocStatus.CANCELLED],v:DocStatus.CANCELLED}]

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.customerId) p.customerId = query.customerId
    if (query.subjectType) p.subjectType = query.subjectType
    if (query.status) p.status = query.status
    const res = await getReceiptPage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
async function loadAccounts() {
  // 失败时显式置空（账户列退化为「—」并回落到主表快照 accountName），不静默留旧值
  try { const r = await request.get('/finance/account/list'); accounts.value = r || [] } catch { accounts.value = [] }
}
onMounted(() => { loadCustomersOptions(); loadSuppliersOptions(); loadAccounts(); loadData() })

function query_() { page.pageNum = 1; loadData() }
function reset_() { query.customerId = ''; query.subjectType = ''; query.status = ''; page.pageNum = 1; loadData() }
function cName(id?: number) { return customersOptions.value.find(x => x.id === id)?.name || '' }
function sName(id?: number) { return suppliersOptions.value.find(x => x.id === id)?.name || '' }
/** 往来单位：客户收款显示客户，供应商收款显示供应商 */
function subjectLabel(row: any) {
  return row.subjectType === SubjectType.SUPPLIER
    ? (row.supplierName || sName(row.supplierId) || '—')
    : (row.customerName || cName(row.customerId) || '—')
}
function aName(id?: number) { return accounts.value.find(x => x.id === id)?.accountName || '' }
/**
 * 「账户」列文案（2026-09-29 多账户收款）：
 * <ul>
 *   <li>单账户 ⇒ 账户名（历史 97 单与系统自动单都是这种）；</li>
 *   <li>多账户 ⇒ <b>「N 个账户」</b>。⚠️ 本页 9 列合计已顶满 956px，本列只有 84px —— 实测「CASH-01 等 2 个」
 *       需 ~127px、且没有任何列能挪出 43px（挪就会破"一行显示完 / 不许截断"的家规），
 *       故列表只给「N」，**账户明细在详情页「收款账户（分款明细）」卡片**里逐行看（点整行即达）。</li>
 * </ul>
 */
function accountText(row: any) {
  const n = Number(row?.accountCount || 0)
  if (n > 1) return `${n} 个账户`
  return row?.accountName || aName(row?.accountId) || '—'
}
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { return DocStatusTag[s || ''] || undefined }

/** 2026-09-23 用户要求：新增收款由 850px 弹框改为独立页（弹框内的表单/校验/提交代码已随之删除） */
function handleAdd() { router.push('/finance/receipt/add') }
/**
 * 行内危险动作（2026-09-29 审核批 A · F7-209）：**确认框与接口调用必须分开 try**。
 * 原先把 `ElMessageBox.confirm` 与接口调用写在同一个 try/catch 里 ⇒ 用户点「取消」也走 catch，
 * 与"接口失败"混同（既分不清原因，也没法对失败做特别处理）。现在照 bill.vue 的口径写：
 * 取消即 `return`，接口失败静默（提示由 request 拦截器统一弹出，含 body code=403）。
 */
async function handleAudit(row: FinanceReceipt) {
  try { await ElMessageBox.confirm(`确认审核收款单「${row.code}」？将核销应收（如有核销明细）、按各账户写资金流水；未核销差额作为预收挂账`, '提示', { type: 'warning' }) } catch { return }
  try { await auditReceipt(row.id as number); ElMessage.success('已审核：已核销应收、按账户写入资金流水'); loadData() }
  catch { /* 提示由拦截器统一给出 */ }
}
async function handleCancel(row: FinanceReceipt) {
  try { await ElMessageBox.confirm(`确认作废收款单「${row.code}」？`, '提示', { type: 'warning' }) } catch { return }
  try { await cancelReceipt(row.id as number); ElMessage.success('已作废'); loadData() }
  catch { /* 提示由拦截器统一给出 */ }
}
async function handleUnAudit(row: FinanceReceipt) {
  try { await ElMessageBox.confirm(`确认反审核收款单「${row.code}」？将冲销核销与账户余额`, '提示', { type: 'warning' }) } catch { return }
  try { await unAuditReceipt(row.id as number); ElMessage.success('已反审核'); loadData() }
  catch { /* 提示由拦截器统一给出 */ }
}
/** 详情改独立页（2026-09-23 用户要求：抽屉改独立界面）：点整行 / 行内「详情」都跳详情页 */
function handleDetail(row: FinanceReceipt) { if (row?.id != null) router.push(`/finance/receipt/detail/${row.id}`) }
</script>
<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
      <el-form-item label="客户"><RemoteSelect v-model="query.customerId" :fetch="fetchCustomers" placeholder="全部" style="width:160px" @change="(v: any) => { if (v === ADD_MARKER) { query.customerId = ''; router.push('/inventory/customer'); return } }"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect></el-form-item>
      <el-form-item label="主体类型">
        <el-select v-model="query.subjectType" placeholder="全部" clearable style="width:120px">
          <el-option v-for="(label, code) in SubjectTypeLabel" :key="code" :label="label" :value="code" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:120px"><el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v"/></el-select></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="query_">查询</el-button>
        <el-button :icon="'Refresh'" @click="reset_">重置</el-button>
        <!-- 2026-09-29 审核批 A · F7-210：入口按权限显示（后端前缀 /api/finance/receipt ⇒ finance:receipt） -->
        <el-button type="success" :icon="'Plus'" @click="handleAdd" v-perm="'finance:receipt'">新增</el-button>
      </div>
      </div>
    </el-card>
    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1240px > 内容区 956px
           ⇒ 横向滚动 284px。收窄为合计 944px。
           2026-09-26 B6（用户口径「数据显示完整 + 单号可点」，实测驱动）：
           ①单号 min112→**140**（实测需 138，原 10/10 行全被截断）并做成链接进收款单详情；
           ②来源 104→**134**：来源单据号（XS- 销售单号，实测需 138，仅现金销售自动收款才有值）
              ⇒ 登记白名单 + tooltip；
           ③状态 tag 补 size="small"（与全站一致）⇒ 78→72；
           ④为抵平：主体类型 80→72、往来单位 min104→92、账户 min96→84、金额 100→92。
           合计 = 140+134+72+92+84+100+92+72+170 = **956** ✓（操作列 4 按钮保持 170，不裁切按钮） -->
      <el-table v-loading="loading" :data="data" border stripe @row-click="handleDetail">
        <el-table-column label="单号" min-width="140" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <!-- 来源单据（2026-09-18）：现金销售单由系统自动收款（立刻到账、已审核）时显示销售单号 -->
        <el-table-column label="来源" width="112" show-overflow-tooltip>
          <template #default="{row}">
            <!-- 2026-09-26 B9：来源做成链接进**源单详情**（与库存流水「关联单号」同口径）。
                 本页 9 列全满，该列需 138px 而只有 112px ⇒ 可点后即便省略号也能一键打开原始单据。 -->
            <el-button v-if="row.sourceBillNo && sourceRoute(row)" type="primary" link @click.stop="goSource(row)">{{ row.sourceBillNo }}</el-button>
            <span v-else-if="row.sourceBillNo">{{ row.sourceBillNo }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B6：表头「主体类型」4 字需 56px，列宽 72 时内容框仅 55px ⇒ 差 1px 被省略。
             72→**76**（56 + 内边距 16 + 边框 1 + 3 余量）；为守住本页 956 的容器宽，从「金额」92→88 回收 4px
             （金额正文最长 57px，88 仍宽敞）。 -->
        <el-table-column label="主体类型" width="76" align="center">
          <template #default="{row}">
            <el-tag :type="row.subjectType === SubjectType.SUPPLIER ? 'warning' : 'primary'" size="small">{{ SubjectTypeLabel[row.subjectType] || '客户' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="往来单位" min-width="114" show-overflow-tooltip><template #default="{row}">{{ subjectLabel(row) }}</template></el-table-column>
        <!-- 2026-09-29 多账户收款：单账户 = 账户名；多账户 =「N 个账户」（本列 84px 装不下「首行 等 N 个」，
             全貌在详情页「收款账户（分款明细）」卡片）—— 判据见 script 里的 accountText -->
        <el-table-column label="账户" min-width="84" show-overflow-tooltip><template #default="{row}">{{ accountText(row) }}</template></el-table-column>
        <el-table-column prop="receiptDate" label="日期" width="100" align="center"/>
        <!-- 2026-09-26 B6：92→88（让 4px 给「主体类型」的表头；金额正文最长 57px，88 内仍完整） -->
        <el-table-column prop="amount" label="金额" width="88" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column label="状态" width="72" align="center"><template #default="{row}"><el-tag :type="stType(row.status)" size="small">{{DocStatusLabel[row.status]||row.status}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <!-- 2026-09-29 审核批 A · F7-210：三个危险动作按权限显示（与后端前缀守卫同码，避免"点必失败"的入口） -->
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button><el-button v-if="row.status===DocStatus.DRAFT" type="success" link v-perm="'finance:receipt'" @click.stop="handleAudit(row)">审核</el-button><el-button v-if="row.status===DocStatus.AUDITED" type="warning" link v-perm="'finance:receipt'" @click.stop="handleUnAudit(row)">反审核</el-button><el-button v-if="row.status===DocStatus.DRAFT" type="danger" link v-perm="'finance:receipt'" @click.stop="handleCancel(row)">作废</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>

  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
