<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useDomainRefresh } from '@/utils/dataFreshness'
import { isOverdraftNeedConfirm, confirmOverdraft } from '@/utils/overdraftConfirm'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { getPaymentPage, auditPayment, cancelPayment, unAuditPayment, type FinancePayment } from '@/api/finance'
import { TYPE_MAP, TYPE_TAG, TYPE_OPTIONS } from '@/constants/supplier'
import RemoteSelect from '@/components/RemoteSelect.vue'

/**
 * 付款管理（2026-09-29 用户口径「付款管理只显示**付款记录**就行，然后可以**新增付款记录**；
 * 汇总要显示在**应付管理**和**应收管理**里面」）：
 * <ul>
 *   <li>原「供应商汇总」页签**整体搬到「应付管理」**（视图页签「按供应商汇总 / 应付台账」，
 *       详情进 <code>/finance/payable/supplier/:id</code>）—— 汇总属"看账"，归应付管理；
 *       本页是"付款作业台"，只做付款单与新增；</li>
 *   <li>新增「新增付款」按钮 → 独立页 <code>/finance/payment/add</code>（供应商在页内选，
 *       不再必须先进供应商工作台）；</li>
 *   <li>列表「账户」列支持多账户（2026-09-29 付款侧与收款侧对称：一单可多账户分款）。</li>
 * </ul>
 */
const router = useRouter()

// ========== 付款记录 ==========
const query = reactive({ supplierId: '' as string|number, supplierType: '', status: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinancePayment[]>([])
const suppliersOptions = ref<any[]>([])
const accounts = ref<{id:number;accountName:string}[]>([])

const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadSuppliersOptions() {
  try { const r: any = await fetchSuppliers(''); suppliersOptions.value = r?.records || [] } catch { suppliersOptions.value = [] }
}

const statusOpts = [{l:DocStatusLabel[DocStatus.DRAFT],v:DocStatus.DRAFT},{l:DocStatusLabel[DocStatus.AUDITED],v:DocStatus.AUDITED},{l:DocStatusLabel[DocStatus.CANCELLED],v:DocStatus.CANCELLED}]

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.supplierId) p.supplierId = query.supplierId
    if (query.supplierType) p.supplierType = query.supplierType
    if (query.status) p.status = query.status
    const res = await getPaymentPage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
async function loadAccounts() {
  try { const r = await request.get<any, any>('/finance/account/list'); accounts.value = r || [] } catch {}
}
function query_() { page.pageNum = 1; loadData() }
function reset_() { query.supplierId = ''; query.supplierType = ''; query.status = ''; page.pageNum = 1; loadData() }
function sName(id?: number) { return suppliersOptions.value.find(x => x.id === id)?.name || '' }
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }
function aName(id?: number) { return accounts.value.find(x => x.id === id)?.accountName || '' }
/**
 * 「账户」列文案（2026-09-29 多账户付款）：
 * <ul>
 *   <li>单账户 ⇒ 账户名（历史单与老 payload 都是这种）；</li>
 *   <li>多账户 ⇒ <b>「N 个账户」</b>。⚠️ 本列 104px 装不下「CASH-01 等 N 个」，
 *       全貌在**付款单详情页「付款账户（分款明细）」卡片**里逐行看（点整行即达）。</li>
 * </ul>
 */
function accountText(row: any) {
  const n = Number(row?.accountCount || 0)
  if (n > 1) return `${n} 个账户`
  return row?.accountName || aName(row?.accountId) || '—'
}
/** 2026-09-29：新增付款改独立页（供应商在页内选，不再必须先进供应商工作台） */
function handleAdd() { router.push('/finance/payment/add') }
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { return DocStatusTag[s || ''] || undefined }

/**
 * 行内危险动作（2026-09-29 审核批 B · F7-221，镜像批 A F7-209）：**确认框与接口调用分开 try** ——
 * 原先两者在同一 try/catch 里，用户点「取消」也走 catch，与"接口失败"混同。现在照 payable-transfer/*
 * 与 receipt.vue 的口径写：取消即 `return`，接口失败静默（提示由 request 拦截器统一弹出）。
 */
async function handleAudit(row: FinancePayment, allowOverdraft = false) {
  try { await ElMessageBox.confirm(`确认审核付款单「${row.code}」？将核销应付（如有核销明细）、按各账户扣减余额并写资金流水；未核销差额作为预付挂账`, '提示', { type: 'warning' }) } catch { return }
  try { await auditPayment(row.id as number, allowOverdraft); ElMessage.success('已审核：已核销应付、按账户写入资金流水'); loadData() }
  catch (e: any) {
    // 2026-10-09（用户口径「扣款时余额不足 ⇒ 给用户提示，用户确认后可通过」）：后端**在动账之前**返回业务码 409
    // （钱没动、单据仍是草稿）⇒ 弹确认框（文案用后端原文，带当前余额 / 本次付款 / 付款后余额）；
    // 用户点「确认继续」后带 allowOverdraft 重发**同一个请求** ⇒ 此时才真的扣款（可把账户扣成负数，流水备注留痕）。
    if (isOverdraftNeedConfirm(e) && await confirmOverdraft(e.msg)) return handleAudit(row, true)
    // 其它失败：提示由拦截器统一给出
  }
}
async function handleCancel(row: FinancePayment) {
  try { await ElMessageBox.confirm(`确认作废付款单「${row.code}」？`, '提示', { type: 'warning' }) } catch { return }
  try { await cancelPayment(row.id as number); ElMessage.success('已作废'); loadData() }
  catch { /* 提示由拦截器统一给出 */ }
}
async function handleUnAudit(row: FinancePayment) {
  try { await ElMessageBox.confirm(`确认反审核付款单「${row.code}」？将冲销核销与账户余额`, '提示', { type: 'warning' }) } catch { return }
  try { await unAuditPayment(row.id as number); ElMessage.success('已反审核'); loadData() }
  catch { /* 提示由拦截器统一给出 */ }
}

/** 详情改独立页（2026-09-23 全站口径）：点整行 / 行内「详情」都跳付款单详情页（草稿可在详情页就地编辑） */
function handleDetail(row: FinancePayment) { if (row?.id != null) router.push(`/finance/payment/detail/${row.id}`) }
function openAttach(url: string) { window.open(url + '?inline=true') }

useDomainRefresh('payment', () => { loadSuppliersOptions(); loadAccounts(); loadData() })

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
        <div class="query-bar">
        <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="供应商"><RemoteSelect v-model="query.supplierId" add-route="/supplier/manage/add" :fetch="fetchSuppliers" placeholder="全部" style="width:160px" domain="supplier" /></el-form-item>
        <el-form-item label="主体类型">
          <!-- 2026-09-20（F7-165）：改用 @/constants/supplier 的 TYPE_OPTIONS（4 个主体类型集中维护，
               含 方案商/加工厂/供货商/辅料商），不再在本页硬编码 label + code -->
          <el-select v-model="query.supplierType" placeholder="全部" clearable style="width:120px">
            <el-option v-for="o in TYPE_OPTIONS" :key="o.name" :label="o.label" :value="o.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:120px"><el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v"/></el-select></el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="query_">查询</el-button>
          <el-button :icon="'Refresh'" @click="reset_">重置</el-button>
          <!-- 2026-09-29 审核批 B · F7-220：入口按权限显示（后端前缀 /api/finance/payment ⇒ finance:payment） -->
          <el-button type="success" :icon="'Plus'" v-perm="'finance:payment'" @click="handleAdd">新增付款</el-button>
        </div>
        </div>
      </el-card>
      <el-card shadow="never" class="table-card">
        <!-- 2026-09-26 B6（顺带修既有缺陷）：本页签**原列宽合计 1110px > 内容区 956px ⇒ 横向滚动 154px**
             （首屏默认在「供应商汇总」页签，此表未激活 ⇒ 历次横向滚动扫描都没扫到它）。
             重排为合计 954px：单号 min150→146 + 可点进付款单详情（补 tooltip）、供应商 min140→124 + 可点、
             主体类型 100→72、账户 min120→104（补 tooltip）、日期 110→100、金额 120→96、凭证 70→64、
             状态 90→72（tag 补 size="small"）、操作 210→176（详情/审核/反审核/作废 4 按钮家规档）。 -->
        <el-table v-loading="loading" :data="data" border stripe @row-click="handleDetail">
          <el-table-column label="单号" min-width="146" show-overflow-tooltip>
            <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">{{ row.code }}</el-button></template>
          </el-table-column>
          <el-table-column label="供应商" min-width="124" show-overflow-tooltip>
            <template #default="{row}">
              <el-button v-if="row.supplierId" type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName || sName(row.supplierId) || '—' }}</el-button>
              <span v-else>{{ row.supplierName || sName(row.supplierId) || '—' }}</span>
            </template>
          </el-table-column>
          <!-- 2026-09-29：本页去掉「供应商汇总」页签后，本表成为**首屏表格** ⇒ 列截断扫描器终于扫到它，
               并查出既有缺陷：列名「主体类型」4 字表头实测需 56px，本列 72px - 内边距只剩 55px ⇒ 差 1px 被裁。
               按家规改短名「类型」（与应付台账页 B10 同一处理；页签/详情页仍有全称，信息不丢）。 -->
          <el-table-column label="类型" width="72" align="center">
            <template #default="{row}">
              <el-tag v-if="row.supplierType" :type="TYPE_TAG[row.supplierType] || 'info'" size="small">{{ typeLabel(row.supplierType) }}</el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <!-- 2026-09-29 多账户付款：单账户 = 账户名；多账户 =「N 个账户」（本列 104px 装不下「首行 等 N 个」，
             全貌在付款单详情页「付款账户（分款明细）」卡片）—— 判据见 script 里的 accountText -->
        <el-table-column label="账户" min-width="104" show-overflow-tooltip><template #default="{row}">{{ accountText(row) }}</template></el-table-column>
          <el-table-column prop="paymentDate" label="日期" width="100" align="center"/>
          <el-table-column prop="amount" label="金额" width="96" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column label="凭证" width="64" align="center"><template #default="{row}"><el-link v-if="row.attachUrl" type="primary" @click.stop="openAttach(row.attachUrl)">查看</el-link><span v-else style="color:#c0c4cc">—</span></template></el-table-column>
          <el-table-column label="状态" width="72" align="center"><template #default="{row}"><el-tag :type="stType(row.status)" size="small">{{DocStatusLabel[row.status]||row.status}}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="176" align="center" fixed="right">
            <!-- 2026-09-29 审核批 B · F7-220：三个危险动作按权限显示（与后端前缀守卫同码，避免"点必失败"的入口） -->
            <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button><el-button v-if="row.status===DocStatus.DRAFT" type="success" link v-perm="'finance:payment'" @click.stop="handleAudit(row)">审核</el-button><el-button v-if="row.status===DocStatus.AUDITED" type="warning" link v-perm="'finance:payment'" @click.stop="handleUnAudit(row)">反审核</el-button><el-button v-if="row.status===DocStatus.DRAFT" type="danger" link v-perm="'finance:payment'" @click.stop="handleCancel(row)">作废</el-button></template>
          </el-table-column>
        </el-table>
        <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
      </el-card>

  </div>
</template>

<style scoped>
/* 根容器已统一到全局（styles/page.css 的 .page-list） */
.qf{display:flex;flex-wrap:wrap}
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
.upload-btn{display:inline-block}
</style>
