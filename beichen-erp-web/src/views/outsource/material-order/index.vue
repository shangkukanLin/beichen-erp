<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag, OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY } from '@/api/enums'

const router = useRouter()
// F7-136（2026-09-20）：原函数名为 typeName 却 `String(id ?? '')` ⇒ 物料 tooltip 显示的是**类型数字 ID**。
// 后端 `MaterialOrderServiceImpl.buildItemMaps` 已随 items 返回 `materialTypeName`（:740），此处直接取用。
function typeName(it: any): string { return it?.materialTypeName || '-' }
const query = reactive({ code: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const loading = ref(false)
const activeTab = ref('进行中')

// 状态 Tab 定义 - 进行中：待审核 + 收货中
const STATUS_TABS = [
  { key: '进行中', label: '进行中', type: 'warning', statuses: [MaterialOrderStatus.PENDING, MaterialOrderStatus.RECEIVING] },
  { key: '已完成', label: '已完成', type: 'success', statuses: [MaterialOrderStatus.FINISHED] },
  { key: '已作废', label: '已作废', type: 'danger', statuses: [MaterialOrderStatus.CANCELLED] }
]
const tabPanes = computed(() => STATUS_TABS.map(t => ({ tab: t.key, name: t.key })))

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) p.code = query.code
    const tab = STATUS_TABS.find(t => t.key === activeTab.value)
    if (tab && tab.statuses.length === 1) {
      p.status = tab.statuses[0]
    } else if (tab && tab.statuses.length > 1) {
      p.statuses = tab.statuses.join(',')
    }
    const r = await request.get<any, any>('/outsource/material-order/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { loading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; loadData() }
function onTabChange() { pagination.pageNum = 1; loadData() }

async function handleConfirm(row: any) {
  try { await ElMessageBox.confirm('审核后将进入收货中状态', '审核订单', { type: 'warning' }); await request.put(`/outsource/material-order/${row.id}/audit`); ElMessage.success('已审核'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleUnAudit(row: any) {
  try { await ElMessageBox.confirm('确认反审核？将回到待审核状态', '反审核', { type: 'warning' }); await request.put(`/outsource/material-order/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确定作废该订单？', '作废订单', { type: 'warning' }); await request.put(`/outsource/material-order/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
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
onMounted(() => { loadData() })
onActivated(() => {
  // 详情/新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(OUTSOURCE_MATERIAL_ORDER_DIRTY_KEY)
    loadData()
  }
})

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="订单号" clearable @keyup.enter="handleQuery" /></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="router.push('/outsource/material-order/add')">新增</el-button>
      </div>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="status-tabs" @tab-change="onTabChange">
      <el-tab-pane v-for="t in STATUS_TABS" :key="t.key" :name="t.key">
        <template #label>
          <el-badge :value="t.key === activeTab ? pagination.total : 0" :hidden="t.key !== activeTab" :max="9999" type="primary">
            <span :class="['tab-label', `tab-${t.type}`]">{{ t.label }}</span>
          </el-badge>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：10 列、原列宽合计 1172px > 内容区 956px
           ⇒ 横向滚动 216px。收窄为合计 944px；单据号/供应商/物料名称保持 min-width（宽屏自动吃余量）。
           操作列按钮原有的内联 padding:0 4px 已删除 —— 全局样式已把表格内 link 按钮左右内边距清零，
           留着等于每个按钮多占 8px（本列 4 个按钮 = 32px）。 -->
      <el-table :data="tableData" border stripe v-loading="loading" row-key="id" @row-click="(row: any) => router.push(`/outsource/material-order/detail/${row.id}`)">
        <el-table-column prop="code" label="订单号" width="100" show-overflow-tooltip />
        <el-table-column label="供应商" min-width="104" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName }}</el-button></template>
        </el-table-column>
        <el-table-column label="下单日期" width="88" align="center"><template #default="{row}">{{ $fmtDate(row.createTime) || '-' }}</template></el-table-column>
        <el-table-column label="物料名称" min-width="94" show-overflow-tooltip>
          <template #default="{row}">
            <el-tooltip placement="top" :show-after="300" raw-content>
              <template #content>
                <div v-for="(it,i) in (row.items||[])" :key="i" style="line-height:1.6">{{ typeName(it) }} {{ it.materialName }} ×{{ it.orderQuantity }}{{it.unit}}（已收{{it.receivedQuantity||0}}）</div>
              </template>
              <span>{{ (row.items || []).map((it: any) => it.materialName).filter(Boolean).join('、') || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="下单总数" width="72" align="center">
          <template #default="{row}">{{ (row.items || []).reduce((s: number, it: any) => s + (it.orderQuantity || 0), 0) }}</template>
        </el-table-column>
        <el-table-column label="已收" width="56" align="center">
          <template #default="{row}"><span :style="{color: (row.items || []).reduce((s: number, it: any) => s + (it.receivedQuantity || 0), 0)>0?'var(--app-color-success)':''}">{{ (row.items || []).reduce((s: number, it: any) => s + (it.receivedQuantity || 0), 0) }}</span></template>
        </el-table-column>
        <el-table-column label="最近收货" width="76" align="center" show-overflow-tooltip><template #default="{row}">{{ $fmtDate(row.lastDeliveryTime) || '-' }}</template></el-table-column>
        <el-table-column label="交期" width="80" align="center"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column label="状态" width="68" align="center"><template #default="{row}"><el-tag :type="MaterialOrderStatusTag[row.status]||'info'" size="small">{{ MaterialOrderStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <!-- 2026-09-24（用户口径）：反审核移入详情页 ⇒ 操作列 206→200。 -->
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link size="small" @click.stop="router.push(`/outsource/material-order/detail/${row.id}`)">详情</el-button>
            <el-button type="success" link size="small" @click.stop="handleDownloadContract(row)">下载合同</el-button>
            <el-button v-if="row.status===MaterialOrderStatus.PENDING" type="success" link size="small" @click.stop="handleConfirm(row)">审核</el-button>
            <el-button v-if="row.status!==MaterialOrderStatus.FINISHED && row.status!==MaterialOrderStatus.CANCELLED" type="danger" link size="small" @click.stop="handleCancel(row)">作废</el-button>
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
.mo-page { display:flex; flex-direction:column; gap:12px; }
/* 卡片内边距已统一到全局（styles/page.css 的 .table-card .el-card__body） */
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
.status-tabs { background: #fff; padding: 0 16px; border-radius: 4px; }
.status-tabs :deep(.el-tabs__header) { margin: 0; }
.tab-label { font-weight: 500; }
.tab-label.tab-info { color: var(--app-text-secondary); }
.tab-label.tab-warning { color: var(--app-color-warning); }
.tab-label.tab-success { color: var(--app-color-success); }
.tab-label.tab-danger { color: var(--app-color-danger); }
</style>
