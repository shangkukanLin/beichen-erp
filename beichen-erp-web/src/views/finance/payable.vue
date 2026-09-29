<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { SettlementStatus, SettlementStatusLabel, sourceBillTypeShortLabel, SourceBillTypeLabel, codeLabelOptions } from '@/api/enums'
import { getPayablePage, type FinancePayable } from '@/api/finance'
import { TYPE_MAP, TYPE_TABS, TYPE_TAG } from '@/constants/supplier'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
/** 来源单据类型下拉（2026-09-20 F7-166：由前端枚举映射生成，不请求后端；后端接口只回 code） */
const sourceBillTypeOptions = ref<{ code: string; label: string }[]>([])
const query = reactive({ supplierId: '' as string|number, sourceBillType: '', status: '', billNo: '' })
/** 页签：按主体类型查看应付（与供应商管理页一致），all=全部 */
const activeType = ref('all')
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinancePayable[]>([])
const suppliersOptions = ref<any[]>([])

const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadSuppliersOptions() {
  try { const r: any = await fetchSuppliers(''); suppliersOptions.value = r?.records || [] } catch { suppliersOptions.value = [] }
}

async function load() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.supplierId) p.supplierId = query.supplierId
    if (activeType.value !== 'all') p.supplierType = activeType.value
    if (query.sourceBillType) p.sourceBillType = query.sourceBillType
    if (query.status) p.status = query.status
    if (query.billNo) p.billNo = query.billNo
    const res = await getPayablePage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
// 切页签：回到第一页重新查询
watch(activeType, () => { page.pageNum = 1; load() })

/** 来源单据类型选项（2026-09-14：改由前端枚举映射生成；后端接口已只回 code） */
function loadSourceBillTypes() { sourceBillTypeOptions.value = codeLabelOptions(SourceBillTypeLabel) }

// ========== 视图：按供应商汇总 / 应付台账（2026-09-29 用户口径「汇总要显示在**应付管理**里面」） ==========
/**
 * 视图切换：① <b>按供应商汇总</b> = 原「付款管理 → 供应商汇总」页签**整体搬来**（列与口径不变）；
 * ② <b>应付台账</b> = 原页面（主体类型页签 + 查询 + 台账表）。
 * <p>默认停在「按供应商汇总」—— 用户口径就是"汇总要显示在应付管理里"，进来先看到管理视角。</p>
 */
const view = ref<'summary' | 'ledger'>('summary')
const summaryLoading = ref(false)
const summaryData = ref<any[]>([])
async function loadSummary() {
  summaryLoading.value = true
  try {
    // 走应付页自身前缀（只要求 finance:payable）；与付款页 /finance/payment/payable-summary 同一实现（PayableQuery）
    const r = await request.get<any, any>('/finance/payable/supplier-summary')
    summaryData.value = r || []
  } catch { summaryData.value = [] } finally { summaryLoading.value = false }
}
/**
 * 汇总行「详情」→ 供应商应付工作台。
 * <p>2026-09-29 用户口径：工作台随汇总一起从 <code>/finance/payment/supplier/:id</code> 搬到
 * <code>/finance/payable/supplier/:id</code>（应付前缀 ⇒ 只被授予 finance:payable 的用户不会 403）。</p>
 */
function goSupplierDetail(row: any) { if (row?.supplierId != null) router.push(`/finance/payable/supplier/${row.supplierId}`) }

onMounted(() => { loadSuppliersOptions(); loadSourceBillTypes(); load(); loadSummary() })

function query_() { page.pageNum = 1; load() }
function reset_() {
  query.supplierId = ''; query.sourceBillType = ''
  query.status = ''; query.billNo = ''; page.pageNum = 1; load()
}
function sName(id?: number) { return suppliersOptions.value.find(x => x.id === id)?.name || '' }
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

/** 主体类型 code -> 中文（供货商/加工厂/辅料商/方案商） */
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }
/** 负数为退货/扣款类冲减项 */
function isDeduction(row: any) { return Number(row.amount) < 0 }
/** 只有未结清的冲减项才可以转应收向对方收款 */
function canTransfer(row: any) {
  return isDeduction(row) && row.status === SettlementStatus.UNSETTLED && !row.transferredToReceivable
}
function goTransfer(row: any) {
  router.push(`/finance/payable-transfer/add?payableId=${row.id}`)
}
/** 详情改独立页（2026-09-23）：点整行 / 行内「详情」都跳详情页，不再开抽屉 */
function goDetail(row: any) { if (row?.id != null) router.push(`/finance/payable/detail/${row.id}`) }
// 结算状态 code -> 中文
const STATUS_LABEL: Record<string, string> = SettlementStatusLabel
function statusLabel(code?: string) { return code ? (STATUS_LABEL[code] || code) : '' }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { if (s === SettlementStatus.UNSETTLED) return 'danger'; if (s === SettlementStatus.PARTIAL) return 'warning'; if (s === SettlementStatus.SETTLED) return 'success'; if (s === SettlementStatus.CANCELLED) return 'info'; return undefined }
</script>
<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <!-- 视图切换（2026-09-29 用户口径「汇总要显示在**应付管理**里」）：
           ①按供应商汇总 = 原「付款管理 → 供应商汇总」页签整体搬来（列/口径不变，「详情」进供应商应付工作台）
           ②应付台账 = 原页面（主体类型页签 + 查询 + 台账表），一行未改
           用 el-radio-group 做第一层切换，避免与台账内部的「主体类型」el-tabs 视觉混淆。 -->
      <el-radio-group v-model="view" style="margin-bottom:12px">
        <el-radio-button value="summary">按供应商汇总</el-radio-button>
        <el-radio-button value="ledger">应付台账</el-radio-button>
      </el-radio-group>
      <template v-if="view === 'ledger'">
      <!-- 页签：按主体类型查看应付（全部/供货商/加工厂/辅料商/方案商），与供应商管理页交互一致 -->
      <el-tabs v-model="activeType">
        <el-tab-pane v-for="t in TYPE_TABS" :key="t.name" :label="t.label" :name="t.name" />
      </el-tabs>
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
      <el-form-item label="供应商"><RemoteSelect v-model="query.supplierId" :fetch="fetchSuppliers" placeholder="全部" style="width:160px" /></el-form-item>
      <el-form-item label="业务场景">
        <el-select v-model="query.sourceBillType" placeholder="全部" clearable style="width:150px">
          <el-option v-for="o in sourceBillTypeOptions" :key="o.code" :label="o.label" :value="o.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:120px"><el-option v-for="s in [{l:SettlementStatusLabel[SettlementStatus.UNSETTLED],v:SettlementStatus.UNSETTLED},{l:SettlementStatusLabel[SettlementStatus.PARTIAL],v:SettlementStatus.PARTIAL},{l:SettlementStatusLabel[SettlementStatus.SETTLED],v:SettlementStatus.SETTLED}]" :key="s.v" :label="s.l" :value="s.v"/></el-select></el-form-item>
      <el-form-item label="单号"><el-input v-model="query.billNo" placeholder="单据号" clearable @keyup.enter="query_"/></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="query_">查询</el-button>
        <el-button :icon="'Refresh'" @click="reset_">重置</el-button>
      </div>
      </div>
      </template>
      </el-card>

      <!-- 按供应商汇总（2026-09-29 用户口径：原「付款管理 → 供应商汇总」页签**整体搬来**，
       列与口径未改：供应商/主体类型/应付总额/已付/未付/逾期金额 + 「详情」进供应商应付工作台。
       本表合计 868px ≤ 948px ✓ 一行显示完；供应商是**合作方列**（家规：不得省略号）。
       口径见 PayableQuery.supplierSummary（未结清 + 排除已转应收；逾期 = due_date < 今天） -->
      <el-card v-if="view === 'summary'" shadow="never" class="table-card">
      <el-table v-loading="summaryLoading" :data="summaryData" border stripe @row-click="goSupplierDetail">
      <el-table-column label="供应商" min-width="180" show-overflow-tooltip>
        <template #default="{row}"><el-button type="primary" link @click.stop="goSupplierDetail(row)">{{ row.supplierName || sName(row.supplierId) || '—' }}</el-button></template>
      </el-table-column>
      <el-table-column label="类型" width="96" align="center">
        <template #default="{row}">
          <el-tag v-if="row.supplierType" :type="TYPE_TAG[row.supplierType] || 'info'" size="small">{{ typeLabel(row.supplierType) }}</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="应付总额" width="126" align="right"><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
      <el-table-column label="已付" width="126" align="right"><template #default="{row}"><span style="color:var(--app-color-success)">{{ fmt(row.paidAmount) }}</span></template></el-table-column>
      <el-table-column label="未付" width="126" align="right"><template #default="{row}"><span style="color:var(--app-color-warning);font-weight:600">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
      <el-table-column label="逾期金额" width="130" align="right"><template #default="{row}"><span :style="{color: Number(row.overdueAmount)>0?'var(--app-color-danger)':'var(--app-text-secondary)', fontWeight: Number(row.overdueAmount)>0?600:400}">{{ fmt(row.overdueAmount) }}</span></template></el-table-column>
      <el-table-column label="操作" width="84" align="center" fixed="right">
        <template #default="{row}"><el-button type="primary" link @click.stop="goSupplierDetail(row)">详情</el-button></template>
      </el-table-column>
      </el-table>
      <el-empty v-if="!summaryLoading && summaryData.length === 0" description="暂无应付数据" />
      </el-card>

      <el-card v-else shadow="never" class="table-card">
      <!-- 2026-09-23：详情改独立页 ⇒ 点整行 / 行内「详情」都跳转（与应收/账单页现状一致） -->
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1240px > 内容区 956px
           ⇒ 横向滚动 284px。收窄为合计 940px。
           2026-09-26 B6（用户口径「数据显示完整 + 供应商/单据号可点」，实测驱动）：
           ①供应商 min100→**128**（实测需 128，原 1/10 行被截断）并做成链接进供应商详情；
           ②业务场景 88→**104**：内容是「来源单据类型」中文标签（实测需 118，最长「委外加工收货」6 字）
              + 条件 tag「已转应收」⇒ 登记白名单 + tooltip；
           ③单据号做成链接进应付详情（原先只能点整行/操作列）；
           ④状态 tag 补 size="small"（原默认尺寸 tag 需 96px）⇒ 76→72；
           ⑤为抵平：主体类型 76→72、应付金额 100→88、已付 92→84、未付 92→84、到期日 96→92。
           合计 = 120+128+72+104+88+84+84+92+72+108 = **952** ✓
           2026-09-29（用户口径「顺手修既有列截断」，实测驱动，`scan-col-truncation -Only /finance/payable`）：
           ①**业务场景 84→110**：实测需 **104**（样本「物料维修费」——B9 立「列表短名 ≤4 字」时后加的
             来源类型 `OUTSOURCE_MATERIAL_REPAIR_FEE` 短名是 5 字，5 字 tag/标签需 104 > 84 被截断）；
           ②腾挪：单据号 min154→**140**（14 字码 `YF-20260929032` 实测自然宽 ~131）、
             供应商 min128→**116**（**白名单列**：`col_allow_truncate`，链接 + tooltip，允许省略号）；
           ③合计不变 952（弹性列在宽屏自动变宽）。
           ⚠️ 已知未修（需口径决定，属**数据条件性**截断）：本列在
             `row.transferredToReceivable` 为真时会**再加一个「已转应收」tag** ⇒ 该格需 ~128px；
             实测库里有 **5 行**是这种（685 行里），本页扫描默认页签的前 10 行恰好没有 ⇒ 守卫看不见。
             两种修法：把 tag 文案压成「已转」（2 字 ⇒ 该格 ~110，正好装下）／或把该 tag 从列表移到详情；
             本批未动（改文案/去信息属产品口径，等用户定）。 -->
      <el-table v-loading="loading" :data="data" border stripe @row-click="(row: any) => goDetail(row)">
        <el-table-column label="单据号" min-width="140" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.billNo }}</el-button></template>
        </el-table-column>
        <el-table-column label="供应商" min-width="116" show-overflow-tooltip>
          <template #default="{row}">
            <el-button v-if="row.supplierId" type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName || sName(row.supplierId) || '—' }}</el-button>
            <span v-else>{{ row.supplierName || sName(row.supplierId) || '—' }}</span>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B10：列名「主体类型」4 字实测需 90px（本列 70）⇒ 按家规改短名「类型」
             （页签已按主体类型筛选、详情页也有全称，信息不丢）。 -->
        <el-table-column label="类型" width="70" align="center">
          <template #default="{row}">
            <el-tag v-if="row.supplierType" :type="TYPE_TAG[row.supplierType] || 'info'" size="small">{{ typeLabel(row.supplierType) }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <!-- 2026-09-29：84→**110**（实测需 104：「物料维修费」5 字短名；B9 的「≤4 字」口径漏了这个后加的类型） -->
        <el-table-column label="业务场景" width="110" show-overflow-tooltip>
          <template #default="{row}">
            <!-- 2026-09-26 B9：改用**列表短名**（≤4 字，如「委外收货/换货入库」）⇒ 8 字全称「委外加工退货收费」
                 实测需 128px 装不下，短名 4 字只需 72px ⇒ **本列不再被省略号截断**；
                 筛选下拉/导出/详情页仍用全称（SourceBillTypeLabel）。 -->
            {{ sourceBillTypeShortLabel(row.sourceBillType) }}
            <el-tag v-if="row.transferredToReceivable" type="warning" size="small" style="margin-left:4px">已转应收</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应付金额" width="86" align="right">
          <!-- 负数=退货/扣款冲减项，标红区分于正常货款 -->
          <template #default="{row}"><span :style="{ color: isDeduction(row) ? 'var(--app-color-danger)' : undefined }">{{ fmt(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column prop="paidAmount" label="已付" width="82" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" label="未付" width="82" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="92" align="center"/>
        <el-table-column label="状态" width="70" align="center"><template #default="{row}"><el-tag :type="stType(row.status)" size="small">{{statusLabel(row.status)}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="104" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link @click.stop="goDetail(row)">详情</el-button>
            <!-- 无货款可抵时，把这笔扣款/退货转为向对方收款。
                 2026-09-29 审核批 B · F7-220：按权限显示 —— 转应收的动作与跳转目标都属"应付转应收"模块
                 （后端前缀 /api/finance/payable-transfer ⇒ finance:payable-transfer），本页自身只要求 finance:payable。 -->
            <el-button v-if="canTransfer(row)" type="warning" link v-perm="'finance:payable-transfer'" @click.stop="goTransfer(row)">转应收</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="load" @current-change="load"/></div>
    </el-card>

  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
