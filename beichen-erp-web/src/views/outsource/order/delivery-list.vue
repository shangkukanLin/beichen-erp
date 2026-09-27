<script setup lang="ts">
/**
 * 成品收货（委外加工 → 成品收货）
 *
 * <p>2026-09-27（用户口径「成品收货应该有 收货中｜已结单 两个页签」）：本页由"只列生产中"改为**两个页签** ——</p>
 * <ul>
 *   <li><b>收货中</b>（默认）= PRODUCING：与后端「只有生产中的加工单可录入收货」口径一致。行内「收货」「退货」；</li>
 *   <li><b>已结单</b> = FINISHED：**只读** —— 行内「收货详细」「结单报表」（反结单就在结单报表页里）。
 *       已结单的加工单后端**禁止收货**（"只有生产中的加工单可录入收货"）、**禁止有单加工退货**
 *       （P3-1：账务已清算，如需退货走「无单退货」）⇒ 这两个按钮**刻意不放**，否则点了必被拒。</li>
 * </ul>
 * <p>页签数量角标：用 `pageSize=1` 的轻量请求取 total（沿用加工退货页的既有做法，零后端改动）。
 * 结单日期取加工单 `actual_end_date`（结单时写入），与物料侧 `finish_time` 同口径。</p>
 *
 * <p>2026-09-21（用户口径）：本页**只做收货**，退回（红冲收货）不再出现在本页 ——
 * 有加工单的退回到该单收货详细页用「加工退货」，无单的退回到「加工退货」菜单页的「加工退货」页签
 * 用「新增无单加工退货」；两者最终都汇总到那张台账里（用「关联加工单」列区分）。
 * （原先挂在本页下方的「无单加工退货」区块已按该口径迁走。）</p>
 */
import { reactive, ref, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag } from '@/api/enums'
import EntityLinks from '@/components/EntityLinks.vue'

defineOptions({ name: 'OutsourceOrderDelivery' })

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const query = reactive({ code: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

/** 页签：收货中（默认，原口径）｜已结单 */
type TabKey = 'PRODUCING' | 'FINISHED'
const TABS: { key: TabKey; label: string }[] = [
  { key: 'PRODUCING', label: '收货中' },
  { key: 'FINISHED', label: '已结单' }
]
const activeTab = ref<TabKey>('PRODUCING')
const isClosed = () => activeTab.value === 'FINISHED'
/** 页签角标：各页签条数（pageSize=1 只取 total；不带单号筛选，表达"一共有多少单"） */
const tabCounts = reactive<Record<string, number>>({})

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/order-delivery/order-page', {
      params: { page: pagination.pageNum, size: pagination.pageSize, code: query.code || undefined, status: activeTab.value }
    })
    tableData.value = r?.records || []
    pagination.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载待收货订单失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}
async function loadCounts() {
  for (const t of TABS) {
    try {
      const r = await request.get<any, any>('/outsource/order-delivery/order-page', {
        params: { page: 1, size: 1, status: t.key }
      })
      tabCounts[t.key] = Number(r?.total || 0)
    } catch { /* 角标失败不影响列表 */ }
  }
}
/** 切页签：重置分页 + 重查（两个页签共用同一接口，只换 status） */
function handleTabChange() { pagination.pageNum = 1; loadData() }
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; handleQuery() }

/** 收货进度（口径与后端 summary 一致：只算已审核收货） */
function progressOf(row: any) {
  const total = Number(row.totalQuantity || 0)
  if (!total) return 0
  return Math.min(100, Math.round(Number(row.deliveredQuantity || 0) / total * 100))
}
/**
 * 进入收货详细页：带 add=1 时自动打开新增收货弹窗，一步完成收货。
 * 追加时间戳是为了让每次点击都是「新的 fullPath」——layout 的 keep-alive 以 fullPath 为 key，
 * 否则复用缓存实例会导致弹窗不再自动弹出。
 */
function goDelivery(row: any) { router.push(`/outsource/order/delivery/${row.id}?add=1&_t=${Date.now()}`) }
function goDetail(row: any) { router.push(`/outsource/order/delivery/${row.id}`) }

/**
 * 退货（2026-09-24 用户口径）：成品收货**列表**的行内操作是「收货 + 退货」，
 * 与「物料收货」列表（material-order/delivery-list.vue）口径一致。
 *
 * <p>成品侧的退货 = 走「加工退货（拆分还料）」页
 * （`/outsource/order/delivery/return-defect/:orderId`）：按本加工单还料 / 红冲收货，
 * 与订单详情页的「加工退货」按钮是同一条路。</p>
 *
 * <p>⚠️ 「结单」不再出现在列表里 —— 它仍保留在成品收货**详情页**的「结单」按钮上。</p>
 */
function goReturn(row: any) { router.push(`/outsource/order/delivery/return-defect/${row.id}`) }
/** 已结单页签的行内入口（只读）：结单报表页 —— 反结单也在那一页 */
function goCloseReport(row: any) { router.push(`/outsource/order/close/${row.id}`) }

onActivated(() => { loadData(); loadCounts() })
</script>

<template>
  <div class="page-list">
    <el-card shadow="never">
      <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">成品收货</span></div></template>
      <!-- 页签（2026-09-27 用户口径）：收货中（默认）｜已结单；标签后带数量角标。
           注：非活动页签的**列**在 DOM 中不存在（列由 v-if 控制），故"行内按钮"类断言不会被隐藏页签干扰。 -->
      <el-tabs v-model="activeTab" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane v-for="t in TABS" :key="t.key" :name="t.key">
          <template #label>
            <span>{{ t.label }}<span v-if="tabCounts[t.key]" style="margin-left:4px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ tabCounts[t.key] }}</span></span>
          </template>
        </el-tab-pane>
      </el-tabs>
      <el-form :inline="true" :model="query" style="margin-bottom:12px">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="加工单号" clearable style="width:200px" @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <!-- 表格/按钮/标签一律不设 size="small"：与「加工订单」等列表页保持同一字号（2026-09-16）。
           列宽合计 ≈942px（**留 20px 余量**）＜ 内容区，保证「一行显示完、不横向滑动」。
           注意：内容区宽度不是固定的 —— 本页行数多、页面变高时会出现**纵向滚动条**，
           内容区会再少约 15px（实测 963 → 948），所以不能贴着 963 排满。
           2026-09-17：①按用户要求「下单/已收/剩余」+40（114→154）、「收货进度」+20（80→100），
           由弹性列「产品」min-width 等量减回（100→77）；②新增行内「退货」按钮（良品退回加工厂 →
           加工退货单），操作列 84→138，由「加工单号 −12、下单/已收/剩余 −10、收货进度 −8、
           最近收货 −8、计划完成 −8、状态 −8」抵平。
           2026-09-21（用户口径「成品收货页只留加工退货、退回走红冲收货」）：**移除**行内「退货」按钮 ⇒
           操作列 124→84；腾出的 40px **自动归弹性列「产品」**（无需手工抵平，合计仍 ≤ 容器 ⇒ 依旧不横向滑动）。
           2026-09-21（用户口径「结单按钮放到成品收货里」）：行内**新增「结单」** ⇒ 操作列 84→124（收货 + 结单），
           正好用回上一轮腾出的 40px（固定列合计 778 + 两个 min-width 160 = 938 ≤ 948 兜底 ⇒ 仍不横向滑动）。
           2026-09-25（用户口径「列表数据显示完整 + 加工厂可点」）：加工厂 min90→120 固定（实测需 121）
           并做成链接进供应商详情；产品 min70→90（弹性列 + tooltip）；下单/已收/剩余 144→110、收货进度 92→70、
           状态 82→74、最近收货/计划完成 98→**100**（日期实测需 ~100，否则被截断）。
           合计 = 140+120+90+110+70+100+100+74+124 = **928** ✓
           2026-09-27（两个页签）：**收货中**页签 = 上表原样不动；**已结单**页签 = 「计划完成 100 / 状态 74」
           换成「结单日期 100」、操作 124→140（收货详细 + 结单报表两个 4 字按钮，实测需 ~136）
           ⇒ 固定列合计 = 140+120+130+62+100+100+140 = 792，加弹性列「产品」min90 = **882** ≤ 948 ✓
           2026-09-27（补结单人）：结单日期列 100→**110**（第二行放结单人小字，与物料侧同款）⇒ 合计 **892** ≤ 948 ✓ -->
      <el-table :data="tableData" border stripe v-loading="loading" style="width:100%" @row-click="goDetail">
        <el-table-column label="加工单号" width="140" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <!-- 2026-09-25（用户口径「数据显示完整 + 加工厂可点」）：可点进供应商详情（后端 row 已带 factoryId，
             pageOrders 一并返回，无需改接口）。 -->
        <el-table-column label="加工厂" width="120" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.factoryId}`)">{{ row.factoryName }}</el-button></template>
        </el-table-column>
        <el-table-column label="产品" min-width="90" show-overflow-tooltip>
          <!-- 2026-09-25：产品可点进产品详情（单项直链 / 多项 Popover；无 id 时回退文本） -->
          <template #default="{ row }">
            <EntityLinks :items="row.products" target="product" sub-key="sku">
              <span>{{ row.productNames || '-' }}</span>
            </EntityLinks>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B10：表头修复 —— 「下单/已收/剩余」实测需 130px、110 会把列名省略；
             为抵平把「产品」min90→70（多产品汇总，白名单 + tooltip）。 -->
        <el-table-column label="下单/已收/剩余" width="130" align="center">
          <template #default="{ row }">
            <span>{{ row.totalQuantity }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span style="color:var(--app-color-success);font-weight:500">{{ row.deliveredQuantity }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span :style="{ color: Number(row.remainingQuantity) <= 0 ? 'var(--app-color-success)' : 'var(--app-color-warning)', fontWeight: 500 }">{{ row.remainingQuantity }}</span>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B10：列名「收货进度」4 字需 90px ⇒ 改短名「进度」（需 62）——本页 9 列全满，
             省下的 28px 全部还给「产品」（min70→90）恢复其可读性。 -->
        <el-table-column label="进度" width="62">
          <template #default="{ row }"><el-progress :percentage="progressOf(row)" :stroke-width="10" :color="progressOf(row) >= 100 ? 'var(--app-color-success)' : 'var(--app-color-primary)'" /></template>
        </el-table-column>
        <!-- 日期列统一 100：实测 "2026-09-25" 在 96px 以下会被截断（日期类不设 size="small"） -->
        <el-table-column label="最近收货" width="100">
          <template #default="{ row }">{{ $fmtDate(row.latestDeliveryDate) }}</template>
        </el-table-column>
        <!-- 计划完成 / 状态：只在「收货中」显示 —— 已结单页签里状态恒为「已结单」（显示即冗余），
             计划完成也不如"结单日期"有用 ⇒ 让出宽度给 结单日期 + 只读操作列（列宽合计仍需 ≤ 内容区）。 -->
        <el-table-column v-if="!isClosed()" label="计划完成" width="100">
          <template #default="{ row }">{{ $fmtDate(row.planEndDate) }}</template>
        </el-table-column>
        <!-- 结单日期 + 结单人（第二行小字）合并一列 —— 与物料收货列表的「订单类型并入订单号第二行」同款，
             省下的列宽正好让出「结单人」而不破"一行不横滑"预算（列宽台账见上方注释）。
             结单人取结单报表盖章人（close_report.auditor_name）；历史单/未盖章显示为 — -->
        <el-table-column v-if="isClosed()" label="结单日期" width="110">
          <template #default="{ row }">
            <div>{{ $fmtDate(row.actualEndDate) }}</div>
            <div style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ row.closeByName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column v-if="!isClosed()" label="状态" width="74" align="center">
          <template #default="{ row }"><el-tag :type="OutsourceOrderStatusTag[row.status] || 'info'" size="small">{{ OutsourceOrderStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <!-- 操作：收货中 = 收货 + 退货（原口径）；已结单 = **只读**（收货详细 + 结单报表 —— 反结单在报表页）。
             已结单的加工单后端禁止收货、禁止有单加工退货（账务已清算）⇒ 不放对应按钮，避免点了必被拒。 -->
        <el-table-column v-if="!isClosed()" label="操作" width="124" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDelivery(row)">收货</el-button>
            <el-button type="warning" link @click.stop="goReturn(row)">退货</el-button>
          </template>
        </el-table-column>
        <el-table-column v-else label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">收货详细</el-button>
            <el-button type="info" link @click.stop="goCloseReport(row)">结单报表</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <div class="pagination">
          <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="handleQuery" @current-change="loadData" />
        </div>
      </div>
    </el-card>

  </div>
</template>
