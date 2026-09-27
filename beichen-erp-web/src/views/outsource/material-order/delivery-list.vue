<script setup lang="ts">
/**
 * 物料收货（委外加工 → 物料收货）
 *
 * <p>2026-09-27（用户口径「物料收货应该有 收货中｜已结单 两个页签」）：本页由"只列收货中"改为**两个页签** ——</p>
 * <ul>
 *   <li><b>收货中</b>（默认）= RECEIVING：与后端「只有收货中的订单可收货」口径一致。行内「收货」「退货」；</li>
 *   <li><b>已结单</b> = FINISHED：行内「收货详细」「反结单」（+「退货」，后端对已结单仍**允许**退不良/物料退货）。
 *       收货按钮刻意不放 —— 后端明确拒绝（"订单已结单，不可再收货"）；</li>
 * </ul>
 * <p><b>反结单（2026-09-27 用户口径「E 也要做」）</b>：物料订单原先结单即**终态**，结错了只能新建单。
 * 现走后端 `PUT /outsource/material-order/{id}/reopen`：曾被审核或已收过货 ⇒ 回「收货中」（可继续收货）；
 * 两者皆无（待审核直接结单的 API 路径）⇒ 回「待审核」。纯状态回退、**清空结单时间与结单人**，
 * **无账务副作用**（结单本身不动库存/应付）。</p>
 *
 * <p>2026-09-21（用户口径「操作文案从收料/退料改成收货/退货」）：行内按钮「收料」→「**收货**」。</p>
 */
import { reactive, ref, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag } from '@/api/enums'
import EntityLinks from '@/components/EntityLinks.vue'

defineOptions({ name: 'OutsourceMaterialOrderDelivery' })

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const query = reactive({ code: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

/** 页签：收货中（默认，原口径）｜已结单 */
type TabKey = 'RECEIVING' | 'FINISHED'
const TABS: { key: TabKey; label: string }[] = [
  { key: 'RECEIVING', label: '收货中' },
  { key: 'FINISHED', label: '已结单' }
]
const activeTab = ref<TabKey>('RECEIVING')
const isClosed = () => activeTab.value === 'FINISHED'
/** 页签角标：各页签条数（pageSize=1 只取 total；不带单号筛选，表达"一共有多少单"） */
const tabCounts = reactive<Record<string, number>>({})

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize, status: activeTab.value }
    if (query.code) p.code = query.code
    const r = await request.get<any, any>('/outsource/material-order/page', { params: p })
    tableData.value = r?.records || []
    pagination.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载待收货订单失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}
async function loadCounts() {
  for (const t of TABS) {
    try {
      const r = await request.get<any, any>('/outsource/material-order/page', {
        params: { pageNum: 1, pageSize: 1, status: t.key }
      })
      tabCounts[t.key] = Number(r?.total || 0)
    } catch { /* 角标失败不影响列表 */ }
  }
}
/** 切页签：重置分页 + 重查（两个页签共用同一接口，只换 status） */
function handleTabChange() { pagination.pageNum = 1; loadData() }
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; handleQuery() }
/** 反结单（仅已结单页签可见）：回到收货中/待审核，可继续收货 */
async function handleReopen(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认反结单？订单 ${row.code} 将回到「收货中」（可继续收货；若该单从未审核过则回「待审核」），结单时间与结单人清空。`,
      '反结单', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/material-order/${row.id}/reopen`)
    ElMessage.success('已反结单')
    loadData(); loadCounts()
  } catch (e: any) {
    ElMessage.error('反结单失败：' + (e?.msg || e?.message || '未知错误'))
  }
}

const itemsOf = (row: any) => (row?.items || []) as any[]
const totalOf = (row: any) => itemsOf(row).reduce((s: number, it: any) => s + (Number(it.orderQuantity) || 0), 0)
const receivedOf = (row: any) => itemsOf(row).reduce((s: number, it: any) => s + (Number(it.receivedQuantity) || 0) - (Number(it.defectReturnedQty) || 0), 0)
const namesOf = (row: any) => itemsOf(row).map((it: any) => it.materialName).filter(Boolean).join(' / ') || '-'
function progressOf(row: any) {
  const total = totalOf(row)
  return total === 0 ? 0 : Math.min(100, Math.round(receivedOf(row) / total * 100))
}
/**
 * 进入收货详细页：带 add=1 时自动打开收货弹窗，一步完成收货。
 * 追加时间戳是为了让每次点击都是「新的 fullPath」——layout 的 keep-alive 以 fullPath 为 key，
 * 否则复用缓存实例会导致弹窗不再自动弹出。
 */
function goReceive(row: any) { router.push(`/outsource/material-order/delivery/${row.id}?add=1&_t=${Date.now()}`) }
function goDetail(row: any) { router.push(`/outsource/material-order/delivery/${row.id}`) }
/**
 * 退货（2026-09-17）：把已收的物料退回物料商 —— 走**委外物料退货单**（独立单据：源仓扣减 + 冲减应付），
 * 与「退不良」（不良品维修退货/折现退款，写在收货记录里并影响净已收）是两件事。
 * 列表行是订单维度（不知具体收料单），故只带供应商预填；按收料记录退货请进详情页。
 */
function goReturn(row: any) {
  router.push(`/outsource/material-return/add?supplierId=${row.supplierId || ''}`)
}

onActivated(() => { loadData(); loadCounts() })
</script>

<template>
  <div class="page-list">
    <el-card shadow="never">
      <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">物料收货</span></div></template>
      <!-- 页签（2026-09-27 用户口径）：收货中（默认）｜已结单；标签后带数量角标。
           非活动页签的**列**由 v-if 控制（DOM 中不存在）⇒ 行内按钮类断言不会被隐藏页签干扰。 -->
      <el-tabs v-model="activeTab" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane v-for="t in TABS" :key="t.key" :name="t.key">
          <template #label>
            <span>{{ t.label }}<span v-if="tabCounts[t.key]" style="margin-left:4px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ tabCounts[t.key] }}</span></span>
          </template>
        </el-tab-pane>
      </el-tabs>
      <el-form :inline="true" :model="query" style="margin-bottom:12px">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="物料订单号" clearable style="width:200px" @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <!-- 表格/按钮/标签一律不设 size="small"：与「物料订单」等列表页保持同一字号（2026-09-16 统一）。
           列宽合计 ≈938px（**留余量**）＜ 内容区，保证「一行显示完、不横向滑动」。
           注意内容区宽度会变：行数多出现**纵向滚动条**时内容区少约 15px（963 → 948），
           所以列宽合计按 948 兜底，不能贴着 963 排满。
           为此：①「订单类型」并入订单号单元格第二行小字；②「下单总数/已收/剩余」合并为一列；
           ③订单号做成链接（点它进收货详细）。
           2026-09-17：按用户要求新增行内「退货」按钮（物料退回物料商 → 委外物料退货单）：
           操作列 84→134，由「订单号 −6、最近收货 −4、交期 −4」抵平。
           2026-09-21（用户口径）：行内第一个按钮「收料」→「收货」⇒ 操作列 = 收货 | 退货（与成品侧的收货/退货同词）。
           2026-09-25（用户口径「数据显示完整 + 供应商可点」）：订单号 144→**160**（MWO-+11 位 + 链接按钮，
           实测需 172，取 160 + tooltip）；供应商/加工厂 min90→100 并做成链接进供应商详情；
           物料 min100→80（汇总列：弹性吃余量 + tooltip）；下单/已收/剩余 114→**130**（实测需 130，
           原先 114 被截断）、收货进度 80→64、状态 84→82、操作 134→118（收货|退货 两按钮）。
           合计 = 160+100+80+130+64+96+96+82+118 = **926** ✓
           2026-09-27（两个页签）：**收货中**页签 = 上表原样不动；**已结单**页签 = 「交期 96 / 状态 96」
           换成「结单时间 100」、操作 118→186（收货详细 + 退货 + 反结单，实测约 180）
           ⇒ 固定列合计 = 160+130+62+96+100+186 = 734，加两个弹性列 min134+min56 = **924** ≤ 948 ✓
           2026-09-27（补结单人）：结单时间列 100→**110**（第二行放结单人小字）⇒ 合计 **934** ≤ 948 ✓ -->
      <el-table :data="tableData" border stripe v-loading="loading" style="width:100%" @row-click="goDetail">
        <el-table-column label="订单号" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <!-- 2026-09-25（用户口径「数据显示完整 + 供应商可点」）：可点进供应商详情（行数据来自物料订单分页，
             已带 supplierId，无需改接口） -->
        <!-- 2026-09-26 B10：列名「供应商/加工厂」6 字 + 斜杠实测需 124px（本页给不出）⇒ 改标准短名「往来单位」
             （表头需 90 ✓）；列内容（供应商名 + 链接）实测需 133 ⇒ min100→**134**，由「物料」min76→56 与「状态」82→68 抵平。 -->
        <el-table-column label="往来单位" min-width="134" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName }}</el-button></template>
        </el-table-column>
        <!-- 2026-09-25：物料可点进「物料库存分布详情」（单项直链 / 多项 Popover 逐项可点） -->
        <el-table-column label="物料" min-width="56" show-overflow-tooltip>
          <template #default="{ row }">
            <EntityLinks :items="itemsOf(row)" target="material" name-key="materialName" qty-key="orderQuantity">
              <span>{{ namesOf(row) }}</span>
            </EntityLinks>
          </template>
        </el-table-column>
        <el-table-column label="下单/已收/剩余" width="130" align="center">
          <template #default="{ row }">
            <span>{{ totalOf(row) }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span style="color:var(--app-color-success);font-weight:500">{{ receivedOf(row) }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span :style="{ color: (totalOf(row) - receivedOf(row)) <= 0 ? 'var(--app-color-success)' : 'var(--app-color-warning)', fontWeight: 500 }">{{ totalOf(row) - receivedOf(row) }}</span>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B10：列名「收货进度」4 字需 90px ⇒ 改短名「进度」（需 62）；
             省下的 28px 还给「状态」（68→96，状态 tag 实测需 96）。 -->
        <el-table-column label="进度" width="62"><template #default="{ row }"><el-progress :percentage="progressOf(row)" :stroke-width="10" :color="progressOf(row) >= 100 ? 'var(--app-color-success)' : 'var(--app-color-primary)'" /></template></el-table-column>
        <el-table-column label="最近收货" width="96"><template #default="{ row }">{{ $fmtDate(row.lastDeliveryTime) }}</template></el-table-column>
        <!-- 交期 / 状态：只在「收货中」显示（2026-09-27 页签化）—— 已结单页签里状态恒为「已结单」（冗余），
             交期让位给更有用的「结单时间」，保证列宽合计 ≤ 内容区（本项目"一行不横滑"家规）。 -->
        <el-table-column v-if="!isClosed()" label="交期" width="96"><template #default="{ row }">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <!-- 结单时间 + 结单人（第二行小字）合并一列：本页 11 列已排满，"第二行小字"是本项目既有做法
             （订单类型并入订单号）。结单人由 finish() 盖章、反结单清空；历史单显示 — -->
        <el-table-column v-if="isClosed()" label="结单时间" width="110">
          <template #default="{ row }">
            <div>{{ $fmtDate(row.finishTime) }}</div>
            <div style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ row.finisherName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column v-if="!isClosed()" label="状态" width="96" align="center"><template #default="{ row }"><el-tag :type="MaterialOrderStatusTag[row.status] || 'info'" size="small">{{ MaterialOrderStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <!-- 操作：收货中 = 收货 + 退货（原口径）；已结单 = 收货详细 + 退货 + 反结单
             （已结单**不可收货**（后端明确拒绝）⇒ 不放「收货」；但退不良/物料退货后端仍允许 ⇒ 保留「退货」）。 -->
        <el-table-column v-if="!isClosed()" label="操作" width="118" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goReceive(row)">收货</el-button>
            <!-- 退货：把已收物料退回物料商（委外物料退货单），与「退不良」区分 -->
            <el-button type="warning" link @click.stop="goReturn(row)">退货</el-button>
          </template>
        </el-table-column>
        <el-table-column v-else label="操作" width="186" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">收货详细</el-button>
            <el-button type="warning" link @click.stop="goReturn(row)">退货</el-button>
            <el-button type="danger" link @click.stop="handleReopen(row)">反结单</el-button>
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
