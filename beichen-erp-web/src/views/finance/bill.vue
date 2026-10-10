<script setup lang="ts">
import { reactive, ref, computed, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBillPage, generateBill, auditBill, unAuditBill, cancelBill, type FinanceBill } from '@/api/finance'
import { invalidate, useDomainRefresh } from '@/utils/dataFreshness'
// F7-246④（2026-09-29 批 D）：移除未使用的 sourceBillTypeLabel（死导入；列表无「来源类型」列）
import { BillType, BillTypeLabel, FINANCE_BILL_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

// 账单状态 code → 中文 label（后端存 DocStatus code，前端展示中文）
const StatusLabel: Record<string, string> = DocStatusLabel
const StatusTag: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'primary'> = DocStatusTag

const router = useRouter()
// 2026-09-29 用户口径：类型**默认「全部」**（空串 = 不传 billType ⇒ 应收+应付混排）。
// ⚠️ 显式 `as string`：若写成 `BillType.RECEIVABLE`（as const 字面量）会被 TS 推断成字面量类型，
// 之后与 PAYABLE 比较会被判"类型无交集"（TS2367）；这里给宽类型，也便于"重置"回写空串。
const query = reactive({ billType: '' as string, partnerId: '' as string|number })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceBill[]>([])
const customersOptions = ref<any[]>([])
const suppliersOptions = ref<any[]>([])

const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadCustomersOptions() {
  try { const r: any = await fetchCustomers(''); customersOptions.value = r?.records || [] } catch { customersOptions.value = [] }
}
async function loadSuppliersOptions() {
  try { const r: any = await fetchSuppliers(''); suppliersOptions.value = r?.records || [] } catch { suppliersOptions.value = [] }
}

/**
 * **列表筛选**的往来单位下拉：按列表类型切换查客户/供应商。
 * ⚠️「全部」类型下本下拉在模板里**禁用**：客户与供应商是两张表、**主键空间独立**，而后端只按
 * `partner_id` 过滤 ⇒ 允许混筛就会串号（客户 5 号与供应商 5 号都会被算进去，返回的不是你要的那一家）。
 * 想按往来单位筛选，请先选具体类型。
 */
const fetchPartner = (kw: string) => (query.billType === BillType.RECEIVABLE ? fetchCustomers(kw) : fetchSuppliers(kw))
/** 切类型：切到「全部」时清空往来单位（免得带着一个只对某类型有意义的 partnerId 去混查） */
function onQueryTypeChange() {
  if (isAllTypes.value) query.partnerId = ''
}

/**
 * 「已结算 / 未结算」两列的列名（2026-09-29 用户口径：「已收付/未收付」这种**合写词不合理** ——
 * "已收就是已收、已付就是已付"）。账单要么应收要么应付，而本页类型是**必选**（默认应收、无"全部"）
 * ⇒ 列名直接跟着选中的类型走：**应付=已付/未付，应收=已收/未收**。
 * 与仓库既有措辞一致（应付侧 payable.vue 用「已付/未付」、应收侧 receivable.vue 用「已收/未收」）。
 */
// ⚠️ 必须 String(...) 包一层：query.billType 的初值是 as const 的 BillType.RECEIVABLE ⇒ TS 把它推断成
// **字面量类型 "RECEIVABLE"**，直接与 PAYABLE 比较会被判为"两个类型没有交集"（TS2367）。
const isPayable = computed(() => String(query.billType) === BillType.PAYABLE)
/** 类型筛选没选具体类型 = 全部（2026-09-29 用户口径：类型下拉加「全部」选项） */
const isAllTypes = computed(() => !query.billType)
/**
 * 两列金额的列名：应收=已收/未收、应付=已付/未付（用户口径：不要「已收付/未收付」这种合写词）；
 * **全部** = 两种单据混排，任何单向词都会误导对方 ⇒ 用台账状态枚举的中性词「已结算/未结算」
 * （与 `enums.ts` 的 `SettlementStatusLabel` 同一用词，不另造词）。
 */
const paidLabel = computed(() => (isAllTypes.value ? '已结算' : (isPayable.value ? '已付' : '已收')))
const unpaidLabel = computed(() => (isAllTypes.value ? '未结算' : (isPayable.value ? '未付' : '未收')))

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.billType) p.billType = query.billType
    if (query.partnerId) p.partnerId = query.partnerId
    const res = await getBillPage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
/**
 * 数据变动后刷新：置脏标志并重新拉取。
 * 脏标志供从其它页面切回本页时按需刷新——只在有变动时才刷，避免每次切换菜单都重新请求。
 */
function afterChange() {
  invalidate('bill')
  loadData()
}
// 2026-10-05 F7-287 修复：原先 `onMounted` 与 `useDomainRefresh` **并存**（且前者还多加载两个下拉选项）
//   ⇒ 首次进入把 loadData() 跑了两遍。useDomainRefresh 内部已在 onMounted 调一次 loader，
//   故把原来的三项加载**整体并入** loader、删掉独立的 onMounted（行为不变，只少一次重复请求）。
useDomainRefresh('bill', () => {
    loadCustomersOptions(); loadSuppliersOptions(); loadData()
}, FINANCE_BILL_DIRTY_KEY)

function query_() { page.pageNum = 1; loadData() }
/**
 * 重置 = 回到**默认查询**：类型回「全部」（空串）、清往来单位。
 * 2026-09-29：原先只清往来单位（因为当时类型是必选、没有"默认值"概念）；现在类型有默认「全部」，
 * 不一起回位就会出现"重置后类型还停在应付"的不一致。
 */
function reset_() { query.billType = ''; query.partnerId = ''; page.pageNum = 1; loadData() }
function partnerName(id?: number) {
  // 无具体类型（全部）时两张表都找一遍，避免返回空名
  const inCustomers = customersOptions.value.find(x => x.id === id)?.name || ''
  const inSuppliers = suppliersOptions.value.find(x => x.id === id)?.name || ''
  if (query.billType === BillType.RECEIVABLE) return inCustomers
  if (query.billType === BillType.PAYABLE) return inSuppliers
  return inCustomers || inSuppliers
}
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

const genForm = reactive({ billType: BillType.RECEIVABLE, partnerId: undefined as number|undefined, partnerName: '', periodStart: '', periodEnd: '' })
const genLoading = ref(false)
const genDialog = ref(false)

function onBillTypeChange() { genForm.partnerId = undefined; genForm.partnerName = '' }
/**
 * **生成弹框**的往来单位下拉：必须跟**弹框自己的类型**（`genForm.billType`）。
 *
 * <p>2026-09-29 修（既有 bug）：原先列表与弹框共用同一个 `fetchPartner`，而它读的是**列表筛选**的
 * `query.billType` ⇒ 两边类型不一致时（例：列表在看应收、弹框选应付）弹框会列出**错误的主数据**
 * （要选供应商却列了客户）⇒ 生成出来的账单会挂错往来单位。同一元凶：一个 fetch 被两处共用却只读一份状态。</p>
 */
const fetchGenPartner = (kw: string) => (genForm.billType === BillType.RECEIVABLE ? fetchCustomers(kw) : fetchSuppliers(kw))
function onPartnerPick(rows: any[]) {
  genForm.partnerName = rows?.[0]?.name || ''
}
async function handleGenerate() {
  if (!genForm.partnerId) { ElMessage.warning('请选择往来单位'); return }
  if (!genForm.periodStart || !genForm.periodEnd) { ElMessage.warning('请选择账期'); return }
  genLoading.value = true
  try {
    const res = await generateBill(genForm)
    // F7-246②（2026-09-29 批 D）：原实现裸解引用 `res.billNo`（200 空体会 NPE）⇒ 改可选链；失败也不再静默
    ElMessage.success(`账单「${res?.billNo || ''}」生成成功，共${fmt(res?.totalAmount)}元`)
    genDialog.value = false
    // 把列表筛选对齐到刚生成的账单并回到第一页：
    // 否则账单类型/往来单位与当前筛选不符、或正停在其他页时，新账单会被过滤掉看不见
    query.billType = genForm.billType
    query.partnerId = genForm.partnerId ?? ''
    page.pageNum = 1
    afterChange()
  } catch { /* F7-246②：失败提示由 request 拦截器统一给出（原先 catch{} 连"没反应"都看不出） */ }
  finally { genLoading.value = false }
}

// 详情已独立成页，列表不再用抽屉展示
function handleDetail(row: FinanceBill) { router.push(`/finance/bill/detail/${row.id}`) }
/**
 * 往来单位 → 客户/供应商详情（2026-09-26 B6）：应收账单的往来单位是客户，应付账单是供应商
 * （`partnerId` 即对应主数据 id，后端已随账单行回传）。
 */
function goPartner(row: FinanceBill) {
  if (!row?.partnerId) return
  if (row.billType === BillType.RECEIVABLE) router.push(`/inventory/customer/detail/${row.partnerId}`)
  else router.push(`/supplier/detail/${row.partnerId}`)
}
// 2026-09-20（F7-160）：审核 / 反审核 / 作废 补二次确认 —— 与同单据的 bill/detail.vue 口径一致。
// 列表里三个危险动作原先「点一下即执行」（审核/反审核会核销、冲销台账），误点代价高；
// 写法照搬 bill/detail.vue：confirm 与请求各自 try/catch（用户点「取消」不算失败），提示语带单号便于多行操作时辨认。
async function handleAudit(row: FinanceBill) {
  try { await ElMessageBox.confirm(`确认审核账单「${row.billNo}」？`, '审核确认', { type: 'warning' }) } catch { return }
  try { await auditBill(row.id as number); ElMessage.success('账单已审核'); afterChange() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function handleUnAudit(row: FinanceBill) {
  try { await ElMessageBox.confirm(`确认反审核账单「${row.billNo}」？`, '反审核确认', { type: 'warning' }) } catch { return }
  try { await unAuditBill(row.id as number); ElMessage.success('账单已反审核'); afterChange() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function handleCancel(row: FinanceBill) {
  try { await ElMessageBox.confirm(`确认作废账单「${row.billNo}」？`, '作废确认', { type: 'warning' }) } catch { return }
  try { await cancelBill(row.id as number); ElMessage.success('账单已作废'); afterChange() }
  catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}
</script>
<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
      <!-- 2026-09-29 用户口径：类型下拉加「全部」（空串 ⇒ 不传 billType ⇒ 后端返回应收+应付混排） -->
      <el-form-item label="类型"><el-select v-model="query.billType" style="width:120px" @change="onQueryTypeChange"><el-option label="全部" value="" /><el-option :label="BillTypeLabel[BillType.RECEIVABLE]" :value="BillType.RECEIVABLE"/><el-option :label="BillTypeLabel[BillType.PAYABLE]" :value="BillType.PAYABLE"/></el-select></el-form-item>
      <!-- 「全部」下禁用往来单位：客户/供应商 id 空间独立，混筛会串号（见 fetchPartner 注释） -->
      <el-form-item label="往来单位"><RemoteSelect v-model="query.partnerId" :fetch="fetchPartner" :domain="query.billType === BillType.RECEIVABLE ? 'customer' : 'supplier'" :add-route="query.billType === BillType.RECEIVABLE ? '/inventory/customer/add' : '/supplier/manage/add'" :disabled="isAllTypes" :placeholder="isAllTypes ? '先选类型' : '全部'" style="width:160px" /></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="query_">查询</el-button>
        <el-button :icon="'Refresh'" @click="reset_">重置</el-button>
        <!-- F7-246①：按 finance:bill 显示（后端 /api/finance/bill 前缀守卫同码） -->
        <el-button type="success" :icon="'Plus'" v-perm="'finance:bill'" @click="genDialog=true">生成账单</el-button>
      </div>
      </div>
    </el-card>
    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1240px > 内容区 971px
           ⇒ 横向滚动 269px。收窄为合计 962px。
           2026-09-26 B6（用户口径「数据显示完整 + 账单号/往来单位可点」，实测驱动）：
           ①账单号 min100→**124** 并做成链接进账单详情（原先只能点整行/操作列）；
           ②类型 60→**64**：tag **补 size="small"**（原默认尺寸 tag 实测需 88px，「应收/应付」2 字也被截断）；
           ③往来单位做成链接：应收账单→客户详情、应付账单→供应商详情（按 billType 分流）；
           合计 = 124+64+100+90+90+92+92+92+76+132 = **952** ✓ -->
      <el-table v-loading="loading" :data="data" border stripe @row-click="handleDetail">
        <el-table-column label="账单号" min-width="124" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">{{ row.billNo }}</el-button></template>
        </el-table-column>
        <el-table-column label="类型" width="64" align="center"><template #default="{row}"><el-tag :type="row.billType===BillType.RECEIVABLE?undefined:'warning'" size="small">{{ BillTypeLabel[row.billType] || row.billType }}</el-tag></template></el-table-column>
        <el-table-column label="往来单位" min-width="100" show-overflow-tooltip>
          <template #default="{row}">
            <el-button v-if="row.partnerId" type="primary" link @click.stop="goPartner(row)">{{ row.partnerName }}</el-button>
            <span v-else>{{ row.partnerName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="periodStart" label="账期起" width="90" align="center"/>
        <el-table-column prop="periodEnd" label="账期止" width="90" align="center"/>
        <el-table-column prop="totalAmount" label="总额" width="92" align="right" show-overflow-tooltip><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
        <!-- 2026-09-29 用户口径：列名跟账单类型走（应付=已付/未付，应收=已收/未收），不用合写词 -->
        <el-table-column prop="paidAmount" :label="paidLabel" width="92" align="right" show-overflow-tooltip><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" :label="unpaidLabel" width="92" align="right" show-overflow-tooltip><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="状态" width="76" align="center"><template #default="{row}"><el-tag :type="StatusTag[row.status] || 'info'" size="small">{{ StatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <!-- 2026-09-24（用户口径）：反审核移入详情页 ⇒ 操作列 170→132（详情/审核/作废 3 个按钮）。 -->
      <el-table-column label="操作" width="132" align="center"><template #default="{row}">
          <el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button>
          <!-- F7-246①：审核/作废按 finance:bill 显示（与后端前缀守卫同码，避免"点必失败"入口） -->
          <el-button v-if="row.status===DocStatus.DRAFT" type="success" link v-perm="'finance:bill'" @click.stop="handleAudit(row)">审核</el-button>
          <el-button v-if="row.status!==DocStatus.CANCELLED" type="danger" link v-perm="'finance:bill'" @click.stop="handleCancel(row)">作废</el-button>
        </template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>

    <el-dialog v-model="genDialog" title="生成账单" width="var(--app-dialog-sm)">
      <el-form :model="genForm" label-width="90px">
        <el-form-item label="类型"><el-select v-model="genForm.billType" style="width:100%" @change="onBillTypeChange"><el-option :label="BillTypeLabel[BillType.RECEIVABLE]" :value="BillType.RECEIVABLE"/><el-option :label="BillTypeLabel[BillType.PAYABLE]" :value="BillType.PAYABLE"/></el-select></el-form-item>
        <el-form-item label="往来单位"><RemoteSelect v-model="genForm.partnerId" :fetch="fetchGenPartner" :domain="genForm.billType === BillType.RECEIVABLE ? 'customer' : 'supplier'" :add-route="genForm.billType === BillType.RECEIVABLE ? '/inventory/customer/add' : '/supplier/manage/add'" placeholder="请选择" style="width:100%" @pick="onPartnerPick" /></el-form-item>
        <el-form-item label="账期起"><el-date-picker v-model="genForm.periodStart" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item label="账期止"><el-date-picker v-model="genForm.periodEnd" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="genDialog=false">取消</el-button><el-button type="primary" :loading="genLoading" @click="handleGenerate">生成</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
