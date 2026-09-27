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
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel } from '@/api/enums'
import EntityLinks from '@/components/EntityLinks.vue'

defineOptions({ name: 'OutsourceMaterialReturn' })

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ code: '', supplierId: undefined as any })

/**
 * 三级菜单叶子（2026-09-27 用户口径）：原「物料退货」一个页面 2 页签 → 拆成 **3 个**菜单叶子，
 * **本组件被三个叶子共用**（按路由路径判定叶子，与加工侧同范式）：
 *  REFUND   关联退料     /outsource/material-return          页签：有效单据 | 已作废单据（单号 MRH-，由物料收货页发起）
 *  UNLINKED 无单退料     /outsource/material-return/unlinked 页签：有效单据 | 已作废单据（单号 MRW-，手工发起）
 *  REPAIR   物料维修退货 /outsource/material-return/repair   页签：待返回 | 已返回完 | 已作废
 * ⚠️ REFUND / UNLINKED 是**同一类型（returnType=REFUND）**的两个叶子，靠 `linked` 参数区分
 *    （WITH_ORDER / WITHOUT_ORDER，口径与加工侧 return-defect 的 linked 完全一致 ⇒ 后端一个条件即可）。
 */
type Leaf = 'REFUND' | 'UNLINKED' | 'REPAIR'
const leaf = computed<Leaf>(() => {
  const p = route.path.replace(/\/$/, '')
  if (p.endsWith('/repair')) return 'REPAIR'
  if (p.endsWith('/unlinked')) return 'UNLINKED'
  return 'REFUND'
})
/** 类型（后端 returnType）：REFUND=退料（关联/无单两个叶子共用）/ REPAIR=维修退货（送修→返回→结案） */
const activeType = computed(() => (leaf.value === 'REPAIR' ? MaterialReturnType.REPAIR : MaterialReturnType.REFUND))
/** 关联物料订单筛选：关联叶子=WITH_ORDER / 无单叶子=WITHOUT_ORDER / 维修叶子不筛（可关联也可不关联） */
const linkedFilter = computed(() => (leaf.value === 'REPAIR' ? undefined : (leaf.value === 'UNLINKED' ? 'WITHOUT_ORDER' : 'WITH_ORDER')))

type TabKey = 'ACTIVE' | 'CANCELLED' | 'PENDING' | 'DONE'
const TABS: Record<Leaf, Array<{ key: TabKey; label: string }>> = {
  REFUND: [{ key: 'ACTIVE', label: '有效单据' }, { key: 'CANCELLED', label: '已作废单据' }],
  // 无单退料与关联退料同构（都是 REFUND），页签一致
  UNLINKED: [{ key: 'ACTIVE', label: '有效单据' }, { key: 'CANCELLED', label: '已作废单据' }],
  // 「待返回」含草稿（未审核的送修单不能在任何页签里消失）；「已返回完」= 已审核且全部送回
  REPAIR: [{ key: 'PENDING', label: '待返回' }, { key: 'DONE', label: '已返回完' }, { key: 'CANCELLED', label: '已作废' }]
}
const tabs = computed(() => TABS[leaf.value])
const activeTab = ref<TabKey>('ACTIVE')
/** 页签角标：各页签条数（pageSize=1 取 total，零后端改动） */
const tabCounts = reactive<Record<string, number>>({})
function countOf(key: TabKey) { return tabCounts[leaf.value + ':' + key] }
/** 列表查询参数：叶子决定 returnType，页签决定 status / progress */
function listParams(tab: TabKey, pageNum: number, pageSize: number) {
  const p: any = {
    pageNum, pageSize, returnType: leaf.value === 'REPAIR' ? MaterialReturnType.REPAIR : MaterialReturnType.REFUND,
    // 2026-09-27：关联/无单退料两个叶子靠 linked 区分（后端 material_order_id 空/非空）
    linked: linkedFilter.value,
    code: query.code || undefined, supplierId: query.supplierId || undefined
  }
  if (tab === 'CANCELLED') p.statuses = DocStatus.CANCELLED
  else if (tab === 'ACTIVE') p.statuses = [DocStatus.DRAFT, DocStatus.AUDITED].join(',')
  else if (tab === 'PENDING') p.progress = 'OPEN'
  else if (tab === 'DONE') p.progress = 'RETURNED'
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

const isRepairTab = () => leaf.value === 'REPAIR'
/** 关联退料叶子才显示「关联物料订单」列（无单叶子该列恒空 ⇒ 不占宽；镜像加工侧「关联加工单」） */
const isLinkedTab = () => leaf.value === 'REFUND'

/**
 * 出库源仓可点（2026-09-25 用户口径「仓库之类可以点进详情」）：源仓可能是**委外仓**（物料在工厂处）
 * 或**自有物料仓**，两者详情页不同 ⇒ 与「物料其他出入库」页同款分流（有 factoryId = 委外仓）。
 * 挂载时拉一次仓库列表建 id→factoryId 映射（1 次请求），点击零等待；与 other-io/index.vue:55-60 同一范式。
 */
const warehouses = ref<any[]>([])
async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500 } })
    warehouses.value = r?.records || []
  } catch { warehouses.value = [] }
}
function goWarehouseDetail(id: any) {
  if (id == null) return
  const wh = warehouses.value.find((w: any) => w.id === id)
  if (wh?.factoryId != null) router.push(`/outsource/warehouse/detail/${id}`)
  else router.push(`/inventory/warehouse/detail/${id}`)
}

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

/** 审核提示按类型区分：物料退货冲减应付；维修退货只出库送修（不冲应付） */
async function handleAudit(row: any) {
  const repair = row.returnType === MaterialReturnType.REPAIR
  // 维修退货：关联订单未完成时审核会同时扣减该订单收料数（修好返回自动回补，2026-09-17）
  const tip = repair
    ? ('确认审核该维修退货单？审核后物料出源仓送供应商维修（不冲减应付）' + (row.materialOrderCode ? `；关联订单 ${row.materialOrderCode} 若未完成，将同时扣减其收料数` : ''))
    : '确认审核该退货单？审核后物料出源仓并冲减应付'
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/audit`); ElMessage.success('已审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit(row: any) {
  const repair = row.returnType === MaterialReturnType.REPAIR
  const tip = repair
    ? '确认反审核？将送修物料回源仓（若有维修返回记录需先撤销；关联订单已扣减的收料数会一并回滚）'
    : '确认反审核？将物料回源仓并冲销应付'
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

/** 新增时带上类型（物料退货 / 维修退货），进新增页后表单按类型切换 */
function handleAdd(type?: string) { router.push(`/outsource/material-return/add?returnType=${type || MaterialReturnType.REFUND}`) }
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
  loadData(); loadCounts(); loadWarehouses()
})

</script>

<template>
  <!-- 一页一张卡片（家规）：页签 → 筛选行 → 表格 → 分页 -->
  <div class="page-list">
    <el-card shadow="never">
      <!-- 页签按叶子生成（2026-09-27 三级菜单）：退料 = 有效单据/已作废单据；维修退货 = 待返回/已返回完/已作废；
           标签后带**数量角标**（页签条数）。 -->
      <el-tabs v-model="activeTab" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane v-for="t in tabs" :key="t.key" :name="t.key">
          <template #label>
            <span>{{ t.label }}<span v-if="countOf(t.key)" style="margin-left:4px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ countOf(t.key) }}</span></span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 筛选行（叶子化后简化）：类型/关联/状态/进度已由**叶子 + 页签**表达 ⇒ 只留单号 + 供应商 -->
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <span v-if="leaf === 'REFUND'" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">挂了物料订单的退料单（MRH-，由该订单的收货页发起）</span>
        <span v-else-if="leaf === 'UNLINKED'" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">没挂物料订单的退料单（MRW-，手工发起）</span>
        <span v-else style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">送供应商维修的料（与加工侧的「成品维修退货」同口径，两处各自成页）</span>
        <el-input v-model="query.code" placeholder="退货单号" clearable style="width:180px" @keyup.enter="handleSearch" />
        <RemoteSelect v-model="query.supplierId" :fetch="fetchSuppliers" placeholder="供应商" style="width:170px" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div style="margin-left:auto;display:flex;gap:8px">
          <el-button v-if="!isRepairTab()" type="success" :icon="'Plus'" @click="handleAdd(MaterialReturnType.REFUND)">新增</el-button>
          <el-button v-else type="success" :icon="'Plus'" @click="handleAdd(MaterialReturnType.REPAIR)">新增</el-button>
        </div>
      </div>

      <!-- 业务提示（按叶子）：说清这一页在干什么、单从哪来、后续在哪办 -->
      <el-alert v-if="leaf === 'REFUND'" type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            关联物料订单的退料（单号 MRH-）：在<b>该物料订单的收货页</b>按收货记录发起，审核后物料回源仓并冲减应付；
            订单还没完成时会同时扣减它的收料数。没挂订单的退料在「无单退料」叶子。
          </span>
        </template>
      </el-alert>
      <el-alert v-else-if="leaf === 'UNLINKED'" type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            没挂物料订单的退料（单号 MRW-）：直接选供应商 + 源仓 + 退什么料，审核后物料回源仓并冲减应付。
            这里新增的<b>默认不关联物料订单</b>；若在新增页手工选了订单，单据会出现在「关联退料」叶子。
          </span>
        </template>
      </el-alert>
      <el-alert v-else type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            送供应商维修的物料：送修出库 → 供应商送回时在详情页「登记维修返回」→ 全部送回后可<b>结案</b>。
            「待返回」含草稿；「已作废」= 草稿被作废的单。
          </span>
        </template>
      </el-alert>

      <!-- 列宽合计（家规：≤948 —— 纵向滚动条出现时内容区从 963 缩到约 948）：
           关联退料 = 158+134+158+min80+90+96+74+132 = 922 ✓（多一列「关联物料订单」⇒ 不单列「出库源仓」，与加工侧关联叶子同做法）
           无单退料 = 158+134+189+min71+90+96+74+132 = 944 ✓（与拆分前完全一致，未动）
           维修退货 = 158+134+min71+90+96+116+122+132 = 919 ✓（无「出库源仓」，多「送修/已返回」列）。 -->
      <el-table :data="list" border stripe v-loading="loading" @row-click="goDetail">
        <!-- 2026-09-25（用户口径「数据显示完整 + 单号/仓库可点」）：退货单号 132→158（MRW-+11 位，实测需 157）
             并做成链接进详情；出库源仓 96→140 并做成链接进**对应仓库详情**（委外仓/自有仓自动分流）。 -->
        <el-table-column label="退货单号" width="158" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column :label="isRepairTab() ? '维修供应商' : '供应商'" width="134" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName }}</el-button></template>
        </el-table-column>
        <!-- 2026-09-27（物料侧拆叶子）：**关联退料**叶子增列「关联物料订单」（点进该物料订单详情）——
             它是本叶子的定义属性（镜像加工侧关联叶子的「关联加工单」）；为守住 948 宽度，本叶子不单列
             「出库源仓」（详情页可查，与加工侧关联叶子同做法）。 -->
        <el-table-column v-if="isLinkedTab()" label="关联物料订单" width="158" show-overflow-tooltip>
          <template #default="{row}">
            <el-button v-if="row.materialOrderId" type="primary" link @click.stop="router.push(`/outsource/material-order/detail/${row.materialOrderId}`)">{{ row.materialOrderCode }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="leaf === 'UNLINKED'" label="出库源仓" width="189" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="goWarehouseDetail(row.fromWarehouseId)">{{ row.warehouseName }}</el-button></template>
        </el-table-column>
        <!-- 2026-09-26 B10：原列名「退货/送修内容」7 字实测需 124px（本页给不出）⇒ 按家规改为短列名「明细」
             （4 字以下才放得下；列内仍是可点的物料明细 + tooltip，信息不丢）。 -->
        <el-table-column label="明细" min-width="71" show-overflow-tooltip>
          <!-- 物料退货显示"退货物料"、维修退货显示"送修物料"（同一列，明细在详情页）。
               2026-09-25：物料可点进「物料库存分布详情」（两个页签同源，后端新增 items[]） -->
          <template #default="{ row }">
            <EntityLinks :items="row.items" target="material" name-key="materialName" qty-key="quantity">
              <span>{{ row.itemSummary || '-' }}</span>
            </EntityLinks>
          </template>
        </el-table-column>
        <el-table-column label="退货金额" width="90" align="right">
          <template #default="{ row }">{{ row.totalAmount != null ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column label="退货日期" width="96" align="center">
          <template #default="{ row }">{{ $fmtDate(row.returnDate) }}</template>
        </el-table-column>
        <!-- 送修 / 已返回（仅维修退货，与加工侧同名同口径）：橙=供应商还没送完、绿=已全部送回；悬停给未返回数 -->
        <el-table-column v-if="isRepairTab()" label="送修/已返回" width="116" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ color: Number(row.unreturnedQty) > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontWeight: 500 }"
              :title="Number(row.unreturnedQty) > 0 ? ('还有 ' + row.unreturnedQty + ' 件未返回') : '已全部返回'">
              {{ row.sentQty ?? '-' }} / {{ row.returnedQty ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <!-- 2026-09-27：状态与进度**分开** —— 状态列恒显示单据状态；「已结案」作为附加标签并列。
             宽度按叶子给：维修退货要放下两个 tag（122），退料叶子没有结案概念（74，保持原宽不破坏预算）。 -->
        <el-table-column label="状态" :width="isRepairTab() ? 122 : 74" align="center">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
            <el-tag v-if="row.returnType === MaterialReturnType.REPAIR && row.closedFlag === 1" type="success" size="small" style="margin-left:4px">已结案</el-tag>
          </template>
        </el-table-column>
        <!-- 动作集与顺序统一（与加工侧一致）：详情 → 审核 → 反审核 → 作废 → 结案 → 撤销结案 -->
        <!-- 2026-09-24（用户口径）：反审核与编辑都收进详情页（详情草稿态可就地改+存）⇒ 操作列 176→132。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">详情</el-button>
            <el-button type="success" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleAudit(row)">审核</el-button>
            <el-button type="danger" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleCancel(row)">作废</el-button>
            <!-- 结案（仅维修退货）：未返回=0 时才出现，代表跟踪终点 -->
            <el-button type="success" link v-if="isRepairTab() && row.status===DocStatus.AUDITED && row.closedFlag!==1 && Number(row.unreturnedQty)===0" @click.stop="handleClose(row)">结案</el-button>
            <el-button type="warning" link v-if="row.closedFlag===1" @click.stop="handleReOpen(row)">撤销结案</el-button>
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
