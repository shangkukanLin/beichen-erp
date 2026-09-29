<script setup lang="ts">
defineOptions({ name: 'OutsourceOrderIndex' })

import { reactive, ref, onMounted, onActivated, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag, OUTSOURCE_ORDER_DIRTY_KEY } from '@/api/enums'
import RemoteSelect from '@/components/RemoteSelect.vue'
import EntityLinks from '@/components/EntityLinks.vue'

const router = useRouter()
const activeTab = ref('PENDING_PRODUCING')
const query = reactive({ code: '', factoryId: undefined as any })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const tableLoading = ref(false)

const fetchFactories = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw, supplierType: 'factory' } })

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (activeTab.value === 'PENDING_PRODUCING') { p.status = [OutsourceOrderStatus.PENDING, OutsourceOrderStatus.PRODUCING].join(',') }
    else if (activeTab.value === OutsourceOrderStatus.FINISHED) { p.status = OutsourceOrderStatus.FINISHED }
    else if (activeTab.value === OutsourceOrderStatus.CANCELLED) { p.status = OutsourceOrderStatus.CANCELLED }
    if (query.code) p.code = query.code
    if (query.factoryId) p.factoryId = query.factoryId
    const r = await request.get<any, any>('/outsource/order/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { tableLoading.value = false }
}
function onTabChange() { pagination.pageNum = 1; loadData() }
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.factoryId = undefined; loadData() }

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确定作废该加工单吗？', '提示', { type: 'warning' }); await request.put(`/outsource/order/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

// 下载合同：取该订单已上传的合同文件（attachUrl，非合同模板），未上传则提示
function handleDownloadContract(row: any) {
  if (!row.attachUrl) { ElMessage.warning('该订单未上传合同，请先进入订单详情上传合同文件'); return }
  const a = document.createElement('a')
  a.href = row.attachUrl
  a.download = ''
  a.target = '_blank'
  document.body.appendChild(a); a.click(); document.body.removeChild(a)
}

onActivated(() => {
  // 新增/修改页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(OUTSOURCE_ORDER_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(OUTSOURCE_ORDER_DIRTY_KEY)
    loadData()
  }
})
onMounted(() => {
  loadData()
})

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="加工单号" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="加工厂"><RemoteSelect v-model="query.factoryId" :fetch="fetchFactories" placeholder="全部" clearable style="width:180px" /></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="router.push('/outsource/order/add')">新增</el-button>
      </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="进行中" name="PENDING_PRODUCING" />
        <el-tab-pane label="已结单" name="FINISHED" />
        <el-tab-pane label="已作废" name="CANCELLED" />
      </el-tabs>

      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1220px > 内容区 956px
           ⇒ 横向滚动 264px。
           2026-09-25（用户口径「列表数据显示完整 + 单号可点进详情」，实测驱动，见 tools/regression/scan-col-truncation.ps1）：
           ①单号 min104→**146 固定**（WO-+11 位定长，实测需 146）+ 做成链接点进详情（原先只能点整行）；
           ②模式 tag 文案「包工包料/来料加工」→「**包料/来料**」，列宽 80→70（缩短文案后 2 字 tag 只需 ~46）；
           ③最近收货 96→**100**（并改用 $fmtDate 只显示日期 —— 原样输出 datetime 实测需 106 会截断）、
             计划完成 96→84、是否缺料 80→62、状态 78→74；
           ④操作 170→**176**（详情|下载合同|作废 三按钮实测需 176，「下载合同」4 字比家规档 140 宽）；
           ⑤加工厂 min110→112、产品 min130→**104**（弹性列吃余量 + tooltip：多产品汇总长度无上界）。
           合计 = 146+70+112+104+84+100+62+74+176 = **928** ✓（家规上限，弹性列在宽屏自动变宽）。
           2026-09-29（用户口径「顺手修既有列截断」，实测驱动，`scan-col-truncation -Only /outsource/order`）：
           ①**加工厂 min112→146**：它是**合作方列**（家规：不得被省略号截断），实测需 **141**
             （样本「测试加工厂A98」；渲染成链接按钮，见下方 BTNCLIP 提醒）⇒ 加宽 34；
           ②**是否缺料 62 的旧账**：B10 为 4 字表头把它放到 90（本页合计 956 = 内容区上限）⇒ 只能从别处挪；
           ③腾挪（都不动家规禁截断的列）：单号 min146→**140**（14 字定长码实测自然宽 ~137）、
             模式 70→**62**（2 字 tag 实测 ~56）、最近收货 100→**92**（10 字日期实测 ~88，与「到期日」同款）、
             产品 min104→**92**（**白名单列**：多产品汇总无上界，靠 tooltip + 点进详情，允许省略号）；
           ④合计 = 140+62+146+92+84+92+90+74+176 = **956**（= 内容区上限，与改前同，未新增横向滚动）。
           📌 已知未动：**计划完成 84** —— 若以后真有人填计划完成日期，10 字日期需 ~88 会被截断；
             实测库里 `outsource_order.plan_end_date` **28 行全为空**（该列一直显示「-」）⇒ 现在不占宽度，
             等这条业务真用起来再加宽（或与「最近收货」对调）。 -->
      <el-table :data="tableData" border stripe v-loading="tableLoading" style="width:100%" @row-click="(row: any) => router.push(`/outsource/order/detail/${row.id}`)">
        <el-table-column label="单号" min-width="140" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/outsource/order/detail/${row.id}`)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column label="模式" width="62" align="center">
          <template #default="{row}"><el-tag :type="row.supplyMode==='FACTORY' ? 'warning' : 'info'" size="small">{{ row.supplyMode==='FACTORY' ? '包料' : '来料' }}</el-tag></template>
        </el-table-column>
        <!-- 2026-09-29：合作方列（家规：不得截断），实测需 141 ⇒ min112→**146**（被截断的样本「测试加工厂A98」） -->
        <el-table-column label="加工厂" min-width="146" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.factoryId}`)">{{ row.factoryName }}</el-button></template>
        </el-table-column>
        <!-- 产品与 SKU 合并为**一行**（2026-09-16 用户要求：列表内容不要换行；多产品时悬浮看全文） -->
        <!-- 白名单列（`col_allow_truncate`）：多产品汇总长度无上界 ⇒ 允许省略号，靠 tooltip + 点进详情 -->
        <el-table-column label="产品" min-width="92" show-overflow-tooltip>
          <template #default="{row}">
            <!-- 2026-09-25：产品可点进产品详情（单项直链 / 多项 Popover；无 id 时回退原文本） -->
            <EntityLinks :items="row.products" target="product" sub-key="sku">
              <span>{{ row.productNames || (row.productCount || 0) + '项' }}</span>
              <span v-if="row.productSkus" style="color:var(--app-text-secondary)"> · {{ row.productSkus }}</span>
            </EntityLinks>
          </template>
        </el-table-column>
        <el-table-column label="计划完成" width="84">
          <template #default="{row}">
            <span :style="{ color: row.planEndDate && new Date(row.planEndDate) < new Date() && row.status !== OutsourceOrderStatus.FINISHED && row.status !== OutsourceOrderStatus.CANCELLED ? 'red' : '' }">{{ $fmtDate(row.planEndDate) || '-' }}</span>
          </template>
        </el-table-column>
        <!-- 2026-09-29：100→92（10 字日期实测 ~88，与「到期日」同款），腾给「加工厂」 -->
        <el-table-column label="最近收货" width="92" show-overflow-tooltip>
          <template #default="{row}">{{ $fmtDate(row.latestDeliveryDate) || '-' }}</template>
        </el-table-column>
        <!-- 是否缺料（2026-09-17）：口径 = 剩余待收量对应的物料需求 > 该加工厂委外仓库存（与收货时的缺料拦截一致）；
             仅进行中（待审核/生产中）计算，已结单/已作废显示 — -->
        <!-- 2026-09-26 B10：62→**90** —— 4 字表头「是否缺料」实测需 90px，62 会把**列名**省略成「是否…」
             （守卫此前只量 body 单元格、漏了表头，本批已补 HDRCLIP 检查）。本页余量 28px，正好够。 -->
        <el-table-column label="是否缺料" width="90" align="center">
          <template #default="{row}">
            <span v-if="row.materialShortage === null || row.materialShortage === undefined">—</span>
            <el-tag v-else :type="row.materialShortage ? 'danger' : 'success'" size="small">{{ row.materialShortage ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="74" align="center">
          <template #default="{row}">
            <el-tag :type="OutsourceOrderStatusTag[row.status]||'info'">{{ OutsourceOrderStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="176" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link @click.stop="router.push(`/outsource/order/detail/${row.id}`)">详情</el-button>
            <el-button type="success" link @click.stop="handleDownloadContract(row)">下载合同</el-button>
            <el-button type="danger" link v-if="row.status!==OutsourceOrderStatus.CANCELLED" @click.stop="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleQuery" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
/* 根容器/卡片内边距已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body） */
</style>
