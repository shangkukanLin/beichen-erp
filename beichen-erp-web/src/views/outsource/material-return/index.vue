<script setup lang="ts">
/**
 * 委外物料退货（页面 = 物料退货；两个页签 = 物料退货 / 维修退货）
 *
 * <p>2026-09-21（用户口径「加工退货页面和物料退货的 UI 需要优化和统一，按 A+B+C+D 做」）：
 * 本页与「加工退货」页（`outsource/return-order`）**对齐成同一套列表页家规** ——
 * ①**一页一张卡片**：页签 → 筛选行 → 表格 → 分页（原先「筛选卡片 + 表格卡片」两张卡片）；
 * ②**筛选行统一**：单号 / 供应商 / 状态 /（维修页签）返回进度 + [查询][重置]，**新增按钮靠右且随页签切换**
 *   （原先两个新增按钮常驻，不随页签）；
 * ③**列宽瘦身、消除横向滚动**（原先两页签都横向滚动：实测 1055px / 1265px ＞ 内容区 948px）；
 * ④**术语统一**：页签①「退货退款」→「**物料退货**」（与菜单/页面同名，与加工侧「加工退货」同模式）、
 *   页签②「维修返还」→「**维修退货**」（与加工侧一致）；列「金额」→「**退货金额**」；
 *   列「退货/送修内容」「送修/已返回」「状态」与加工侧同名；
 * ⑤**返回进度不再单列**（原先「送修/已返回」+「返回进度」两列）：合并且与加工侧一致 ——
 *   「送修/已返回」显示 `sent / returned`（橙=还有未返回、绿=已全部返回，悬停给未返回数），
 *   **已结案直接显示在「状态」列**（加工侧同款做法）；
 * ⑥**动作集与顺序统一**（与加工侧一致）：详情 → 编辑 → 审核 → 反审核 → 作废 → 结案 → 撤销结案；
 *   「编辑」为 D 档新增（复用新增页 `/outsource/material-return/edit/:id`，后端 `PUT /{id}` 早已支持，仅允许草稿）。
 *
 * <p>📏 列宽预算（家规：合计 ≤ 948，纵向滚动条出现时内容区从 963 缩到约 948，故留余量）：
 * 2026-09-25（用户口径「数据显示完整 + 单号/仓库可点」，实测见 tools/regression/scan-col-truncation.ps1）；
 * 2026-09-27 拆叶子后三套列（详见模板里的逐列合计）：关联退料 922 / 无单退料 944 / 维修退货 919 ✓
 * （公共列同宽：单号 158 / 对方 134 / 金额 90 / 日期 96 / 状态 74-122 / 操作 132；
 *   「出库源仓」只在**无单退料**叶子，「关联物料订单」只在**关联退料**叶子，维修叶子两者都不列）。</p>
 *
 * <p>📌 详情入口规则（与加工侧同一条家规）：**有独立详情页的单据 → 行点击 / 「详情」跳详情页**；
 * 只有「收货台账」那种没有独立页的记录才用抽屉。</p>
 */
import { computed, reactive, ref, watch, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel, MaterialReturnTypeTag } from '@/api/enums'
import EntityLinks from '@/components/EntityLinks.vue'

defineOptions({ name: 'OutsourceMaterialReturn' })

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ code: '', supplierId: undefined as any })

/**
 * 三级菜单叶子（2026-09-27 拆叶子 → **2026-09-28 收敛为 2 个叶子**，用户口径「物料维修退料这个不需要了」）：
 * **本组件被两个叶子共用**（按路由路径判定叶子，与加工侧同范式）：
 *  REFUND   关联退料   /outsource/material-return          挂了物料订单的退料（单号 MRH-）
 *  UNLINKED 无单退料   /outsource/material-return/unlinked 没挂物料订单的退料（单号 MRW-）
 * ⚠️ 两个叶子靠 `linked` 参数区分（WITH_ORDER / WITHOUT_ORDER，口径与加工侧 return-defect 的 linked 完全一致
 *    ⇒ 后端一个条件即可）。
 *
 * <p>🔖 **每个叶子内都可能有三种类型**（`returnType`，用户口径 2026-09-28，见 `MaterialReturnType`）：
 * `ORDER` 订单退料（仅关联未结单订单，扣源仓 + 扣订单出货/收料数）/
 * `REFUND` 退货退款 / `REPAIR` 维修返回（送修 → 登记返回 → 可结案）。
 * ⇒ 列表用「类型」列区分、动作按**行类型**显示（结案只对维修返回），查询**不按类型过滤**（同一叶子里三类型混排）。</p>
 *
 * <p>📑 页签口径（两叶子统一）：**有效单据｜已返回完｜已作废** ——
 * 「有效单据」= 草稿 ∪ 已审核未返回完（后端 `progress=OPEN`；订单退料/退货退款天然落这里，它们没有"返回"概念）；
 * 「已返回完」= 已审核且送修全部送回（`progress=RETURNED`，含已结案）；
 * 「已作废」= 草稿被作废（`statuses=CANCELLED`）。三者互补且互斥，无遗漏。</p>
 */
type Leaf = 'REFUND' | 'UNLINKED'
const leaf = computed<Leaf>(() => {
  const p = route.path.replace(/\/$/, '')
  if (p.endsWith('/unlinked')) return 'UNLINKED'
  return 'REFUND'
})
/** 关联物料订单筛选：关联叶子=WITH_ORDER / 无单叶子=WITHOUT_ORDER（**不筛类型**：叶子内三类型混排） */
const linkedFilter = computed(() => (leaf.value === 'UNLINKED' ? 'WITHOUT_ORDER' : 'WITH_ORDER'))

type TabKey = 'ACTIVE' | 'RETURNED' | 'CANCELLED'
const TABS: Record<Leaf, Array<{ key: TabKey; label: string }>> = {
  REFUND: [{ key: 'ACTIVE', label: '有效单据' }, { key: 'RETURNED', label: '已返回完' }, { key: 'CANCELLED', label: '已作废' }],
  // 无单退料与关联退料同构（叶子内都是三类型混排），页签一致
  UNLINKED: [{ key: 'ACTIVE', label: '有效单据' }, { key: 'RETURNED', label: '已返回完' }, { key: 'CANCELLED', label: '已作废' }]
}
const tabs = computed(() => TABS[leaf.value])
const activeTab = ref<TabKey>('ACTIVE')
/** 页签角标：各页签条数（pageSize=1 取 total，零后端改动） */
const tabCounts = reactive<Record<string, number>>({})
function countOf(key: TabKey) { return tabCounts[leaf.value + ':' + key] }
/** 列表查询参数：叶子决定 linked（类型混排不筛），页签决定 progress / statuses */
function listParams(tab: TabKey, pageNum: number, pageSize: number) {
  const p: any = {
    pageNum, pageSize,
    // 2026-09-27：关联/无单两个叶子靠 linked 区分（后端 material_order_id 空/非空）
    linked: linkedFilter.value,
    code: query.code || undefined, supplierId: query.supplierId || undefined
  }
  if (tab === 'CANCELLED') p.statuses = DocStatus.CANCELLED
  else if (tab === 'ACTIVE') p.progress = 'OPEN'          // 草稿 ∪ 已审核未返回完
  else if (tab === 'RETURNED') p.progress = 'RETURNED'    // 已审核且送修全部送回（含已结案）
  return p
}

/** 类型文案（2026-09-21 术语统一）：页签文案改由叶子 TABS 定义（2026-09-27 三级菜单） */

/**
 * 退货对象（辅料商/供应商）实时查库。
 * <p>2026-09-21（用户口径）：**物料退货的对方只可能是辅料商或供应商，不会是供货商** ⇒
 * 筛选与表单下拉一律 `excludeSupplierType: 'product'`（与新增页、与后端兜底校验同一口径 ✓）。</p>
 */
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { pageSize: 500, name: kw, excludeSupplierType: 'product' } })

/** 关联退料叶子才显示「关联物料订单」列（无单叶子该列恒空 ⇒ 不占宽；镜像加工侧「关联加工单」） */
const isLinkedTab = () => leaf.value === 'REFUND'
/**
 * 行类型判定（2026-09-28 三态）：叶子不再等于类型，**动作与列都按行类型**决定 ——
 * 结案/撤销结案只对维修返回；「已返回」小字只对维修返回；审核提示语按三类型分别写。
 */
const isRepairRow = (row: any) => row?.returnType === MaterialReturnType.REPAIR
const isOrderReturnRow = (row: any) => row?.returnType === MaterialReturnType.ORDER

// 注（2026-09-28）：原「出库源仓」列与它的仓库详情跳转（goWarehouseDetail + 挂载时拉仓库列表）
// 已随两叶子列重排移除 —— 类型列（含维修返回的"已返回数量"）优先级更高，源仓在**详情页**可见。
// 若后续要恢复该列，见详情页「出库源仓」字段（同一 id→factoryId 分流逻辑在该页仍在用）。

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/material-return/page',
      { params: listParams(activeTab.value, pagination.pageNum, pagination.pageSize) })
    list.value = r?.records || []; pagination.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载物料退货失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}
/** 切页签：重置到第 1 页再查（筛选条件已由 叶子+页签 表达） */
function handleTabChange() { pagination.pageNum = 1; loadData() }
function handleSearch() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.supplierId = undefined; handleSearch() }
/** 页签角标（2026-09-27）：各页签条数 —— 每页取 1 条只读 total */
async function loadCounts() {
  for (const t of tabs.value) {
    try {
      const r = await request.get<any, any>('/outsource/material-return/page', { params: listParams(t.key, 1, 1) })
      tabCounts[leaf.value + ':' + t.key] = Number(r?.total || 0)
    } catch { tabCounts[leaf.value + ':' + t.key] = 0 }
  }
}

/** 结案（仅维修退货）：全部送修数量已返回（未返回=0）后确认收尾 */
async function handleClose(row: any) {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/撤销维修返回，也不能反审核（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/close`); ElMessage.success('已结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen(row: any) {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/re-open`); ElMessage.success('已撤销结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

/**
 * 审核提示按**行类型**区分（2026-09-28 三态）：
 * 订单退料 = 出源仓 + 扣关联订单的出货/收料数；退货退款 = 出源仓 + 冲减应付（P2 起改为"对供应商的应收"）；
 * 维修返回 = 只出库送修（不冲应付），修好回厂时在详情页登记维修返回。
 */
async function handleAudit(row: any) {
  const tip = isOrderReturnRow(row)
    ? ('确认审核该订单退料单？审核后物料出源仓，并扣减关联订单的出货/收料数量（永久扣减，反审核才加回）'
       + (row.materialOrderCode ? `：${row.materialOrderCode}` : ''))
    : (isRepairRow(row)
      ? '确认审核该维修返回单？审核后物料出源仓送供应商维修；「填了维修费」则按明细金额生成对供应商的应付。修好回厂时在详情页「登记维修返回」'
      : '确认审核该退货退款单？审核后物料出源仓，并生成「对供应商的应收」（供应商把货款退来后走收款核销）')
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/audit`); ElMessage.success('已审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit(row: any) {
  const tip = isOrderReturnRow(row)
    ? '确认反审核？将物料回源仓，并把关联订单的出货/收料数量加回'
    : (isRepairRow(row)
      ? '确认反审核？将送修物料回源仓（若有维修返回记录需先撤销；已生成的维修费应付会一并冲回）'
      : '确认反审核？将物料回源仓并冲回对供应商的应收（已有收款需先退款）')
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

/**
 * 新增时带上**类型 + 叶子意图**（2026-09-28）。
 * <p>`linked` 复用列表接口的既有词汇：`WITH_ORDER`=关联退料叶子 / `WITHOUT_ORDER`=无单退料叶子 / 空=维修叶子。
 * 必须带的原因：关联退料与无单退料是**同一类型（REFUND）的两个叶子**、共用同一个新增页 ⇒
 * 新增页只能靠 `linked` 判断"这一单要不要挂关联物料订单"（字段开关 + 必填 + 页签标题）。</p>
 */
function handleAdd(type?: string, linked?: string) {
  const url = `/outsource/material-return/add?returnType=${type || MaterialReturnType.REFUND}`
  router.push(linked ? `${url}&linked=${linked}` : url)
}
/** 编辑草稿（D 档 2026-09-21）：复用新增页（后端 PUT /{id} 仅允许草稿） */
/* 2026-09-24（用户口径）：列表不再提供「编辑」 —— 草稿态统一在详情页内联改+存（handleEdit 已移除）；
   新增仍走 /outsource/material-return/add（可带 fromDelivery 等预填）。 */
function goDetail(row: any) { router.push(`/outsource/material-return/detail/${row.id}`) }

// 叶子切换（点左侧菜单 / 直达 URL）：页签回到该叶子的第一个并加载
watch(leaf, (lv) => {
  activeTab.value = TABS[lv][0].key
  pagination.pageNum = 1
  loadData(); loadCounts()
})

onActivated(() => {
  // 详情/新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY)
    loadData(); loadCounts()
  }
})
onMounted(() => {
  activeTab.value = TABS[leaf.value][0].key
  loadData(); loadCounts()
})

</script>

<template>
  <!-- 一页一张卡片（家规）：页签 → 筛选行 → 表格 → 分页 -->
  <div class="page-list">
    <el-card shadow="never">
      <!-- 页签按叶子生成（2026-09-28 两叶子统一）：**有效单据 | 已返回完 | 已作废** ——
           「有效单据」= 草稿 ∪ 已审核未返回完（订单退料/退货退款天然落这里）；「已返回完」= 维修返回全部送回；
           标签后带**数量角标**（页签条数）。 -->
      <el-tabs v-model="activeTab" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane v-for="t in tabs" :key="t.key" :name="t.key">
          <template #label>
            <span>{{ t.label }}<span v-if="countOf(t.key)" style="margin-left:4px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ countOf(t.key) }}</span></span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 筛选行（叶子化后简化）：关联/状态/进度已由**叶子 + 页签**表达 ⇒ 只留单号 + 供应商
           （**不按类型筛**：叶子内三种类型混排，靠「类型」列区分） -->
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <span v-if="leaf === 'REFUND'" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">挂了物料订单的退料单（MRH-，本页可新增，也可由该订单的收货页发起）</span>
        <span v-else style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">没挂物料订单的退料单（MRW-，手工发起）</span>
        <el-input v-model="query.code" placeholder="退货单号" clearable style="width:180px" @keyup.enter="handleSearch" />
        <RemoteSelect v-model="query.supplierId" :fetch="fetchSuppliers" placeholder="供应商" style="width:170px" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <!-- 新增入口随叶子切换（2026-09-28）：靠 `linked` 告诉新增页要不要挂「关联物料订单」（否则关联叶子必然录成"无单"）；
             **类型**在新增页按"入口 + 订单状态"自动判定（未结单⇒订单退料；已结单⇒退货退款/维修返回） -->
        <div style="margin-left:auto;display:flex;gap:8px">
          <el-button v-if="leaf === 'REFUND'" type="success" :icon="'Plus'" @click="handleAdd(MaterialReturnType.REFUND, 'WITH_ORDER')">新增</el-button>
          <el-button v-else type="success" :icon="'Plus'" @click="handleAdd(MaterialReturnType.REFUND, 'WITHOUT_ORDER')">新增</el-button>
        </div>
      </div>

      <!-- 业务提示（按叶子）：说清这一页在干什么、三种类型怎么分、后续在哪办 -->
      <el-alert v-if="leaf === 'REFUND'" type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            挂了物料订单的退料（单号 MRH-）：<b>本页「新增」</b>（选关联物料订单）或在该物料订单的收货页发起。
            按订单状态自动定类型 —— <b>订单未结单 ⇒ 订单退料</b>（扣源仓 + 扣该订单出货/收料数量，不动账务）；
            <b>订单已结单 ⇒ 退货退款</b>（物料回源仓 + <b>生成对供应商的应收</b>，供应商退款后走收款核销）
            <b>或维修返回</b>（送修 → 回厂登记 → 可结案；填了维修费则按明细生成对供应商的应付）。
            没挂订单的退料在「无单退料」叶子。
          </span>
        </template>
      </el-alert>
      <el-alert v-else type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            没挂物料订单的退料（单号 MRW-）：<b>不挂订单</b>，可选<b>退货退款</b>（物料回源仓 + <b>生成对供应商的应收</b>）
            或<b>维修返回</b>（送修 → 回厂登记 → 可结案；填了维修费则按明细生成对供应商的应付）。
            确需挂订单请到<b>「关联退料」</b>叶子新增。
          </span>
        </template>
      </el-alert>
      <!-- 列宽合计（家规：≤948 —— 纵向滚动条出现时内容区从 963 缩到约 948）：
           2026-09-28 两叶子统一三类型后重排：**加「类型」列（118，兼显维修返回的"已返回/送修"小字）、
           去掉「退货日期」（96，详情页可查）**，全叶子状态列统一 122（要放下「已结案」标签）：
           关联退料 = 158+120+132(关联物料订单)+118+min71+90+122+132 = 943 ✓
           无单退料 = 158+120+118+min150+90+122+132 = 890 ✓（无「关联物料订单」列 ⇒ 明细列放宽到 min150）
           （公共列：单号 158 / 供应商 120 / 类型 118 / 金额 90 / 状态 122 / 操作 132。） -->
      <el-table :data="list" border stripe v-loading="loading" @row-click="goDetail">
        <!-- 2026-09-25（用户口径「数据显示完整 + 单号/仓库可点」）：退货单号 132→158（MRW-+11 位，实测需 157）
             并做成链接进详情。 -->
        <el-table-column label="退货单号" width="158" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column label="供应商" width="120" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName }}</el-button></template>
        </el-table-column>
        <!-- 2026-09-27（物料侧拆叶子）：**关联退料**叶子增列「关联物料订单」（点进该物料订单详情）——
             它是本叶子的定义属性（镜像加工侧关联叶子的「关联加工单」）。 -->
        <el-table-column v-if="isLinkedTab()" label="关联物料订单" width="132" show-overflow-tooltip>
          <template #default="{row}">
            <el-button v-if="row.materialOrderId" type="primary" link @click.stop="router.push(`/outsource/material-order/detail/${row.materialOrderId}`)">{{ row.materialOrderCode }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- 类型（2026-09-28 三态）：叶子不再等于类型 ⇒ 必须用本列区分「订单退料 / 退货退款 / 维修返回」。
             维修返回在**同一格**里补一行小字（用户口径「列表要显示已返回数量」）：
             未返回完 = 橙色「返 3/5」，已全部返回 = 绿色「已返完」，已结案 = 绿 tag「已结案」（结案即全返回，二者互斥）。 -->
        <el-table-column label="类型" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag :type="MaterialReturnTypeTag[row.returnType] || 'info'" size="small">{{ MaterialReturnTypeLabel[row.returnType] || row.returnType }}</el-tag>
            <span v-if="isRepairRow(row) && row.closedFlag === 1" style="margin-left:4px;color:var(--app-color-success);font-size:var(--app-font-xs);font-weight:500">已结案</span>
            <span v-else-if="isRepairRow(row)"
              :style="{ marginLeft: '4px', color: Number(row.unreturnedQty) > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontSize: 'var(--app-font-xs)', fontWeight: 500 }"
              :title="'送修 ' + (row.sentQty ?? 0) + ' / 已返回 ' + (row.returnedQty ?? 0) + (Number(row.unreturnedQty) > 0 ? ('（还有 ' + row.unreturnedQty + ' 件未返回）') : '（已全部返回）')">
              返 {{ row.returnedQty ?? 0 }}/{{ row.sentQty ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B10：原列名「退货/送修内容」7 字实测需 124px（本页给不出）⇒ 按家规改为短列名「明细」
             （4 字以下才放得下；列内仍是可点的物料明细 + tooltip，信息不丢）。
             2026-09-25：物料可点进「物料库存分布详情」（后端 items[]）。 -->
        <el-table-column label="明细" :min-width="isLinkedTab() ? 71 : 240" show-overflow-tooltip>
          <template #default="{ row }">
            <EntityLinks :items="row.items" target="material" name-key="materialName" qty-key="quantity">
              <span>{{ row.itemSummary || '-' }}</span>
            </EntityLinks>
          </template>
        </el-table-column>
        <el-table-column label="退货金额" width="90" align="right">
          <template #default="{ row }">
            <!-- 订单退料不产生金额（不退款/不收费）⇒ 显示 —；退货退款=退款金额；维修返回=维修费（P3 落地） -->
            <span v-if="isOrderReturnRow(row)">-</span>
            <span v-else>{{ row.totalAmount != null ? Number(row.totalAmount).toFixed(2) : '-' }}</span>
          </template>
        </el-table-column>
        <!-- 状态：恒显示单据状态（「已结案」已并入「类型」列，故本列全叶子同宽 74） -->
        <el-table-column label="状态" width="74" align="center">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <!-- 动作集与顺序统一（与加工侧一致）：详情 → 审核 → 反审核 → 作废 → 结案 → 撤销结案 -->
        <!-- 2026-09-24（用户口径）：反审核与编辑都收进详情页（详情草稿态可就地改+存）⇒ 操作列 176→132。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">详情</el-button>
            <el-button type="success" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleAudit(row)">审核</el-button>
            <el-button type="danger" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleCancel(row)">作废</el-button>
            <!-- 结案（**仅维修返回**，2026-09-28 起按行类型判定而非按叶子）：未返回=0 时才出现，代表跟踪终点 -->
            <el-button type="success" link v-if="isRepairRow(row) && row.status===DocStatus.AUDITED && row.closedFlag!==1 && Number(row.unreturnedQty)===0" @click.stop="handleClose(row)">结案</el-button>
            <el-button type="warning" link v-if="isRepairRow(row) && row.closedFlag===1" @click.stop="handleReOpen(row)">撤销结案</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSearch" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>
