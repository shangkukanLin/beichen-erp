<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { OutsourceOrderStatus, OutsourceOrderStatusLabel, DeliveryType, DeliveryTypeLabel, OUTSOURCE_DELIVERY_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const activeTab = ref('')
const query = reactive({ code: '', factoryId: undefined as any })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const tableLoading = ref(false)

// Odoo 风格：加工厂筛选实时查库
const fetchFactories = (kw: string) => request.get('/supplier/page', { params: { supplierType: 'factory', pageSize: 500, name: kw } })
// 仓库列表（用于点击仓库跳转详情）
const warehouseOptions = ref<any[]>([])
async function loadWarehouseOptions() {
  try { const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500 } }); warehouseOptions.value = r?.records || [] } catch { warehouseOptions.value = [] }
}

function goWhDetail(warehouseId: number) {
  const w = warehouseOptions.value.find((x:any)=>x.id===warehouseId)
  // F7-136（2026-09-20）：原为**静默 return**（仓库不在已加载列表——如 pageSize 截断——时点击毫无反应）。
  // 改为显式提示；跳转判定保持"委外仓 → /outsource、自有仓 → /inventory"（与 material-warehouse.vue 已统一）。
  if (!w) { ElMessage.info('未找到该仓库信息，请刷新后重试'); return }
  if (w.factoryId != null) router.push(`/outsource/warehouse/detail/${warehouseId}`)
  else router.push(`/inventory/warehouse/detail/${warehouseId}`)
}

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (activeTab.value) p.deliveryType = activeTab.value
    if (query.code) p.code = query.code
    if (query.factoryId) p.factoryId = query.factoryId
    const r = await request.get<any, any>('/outsource/delivery/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { tableLoading.value = false }
}
function onTabChange() { pagination.pageNum = 1; loadData() }
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.factoryId = undefined; loadData() }

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确定作废该收发单吗？作废后库存将自动恢复。', '提示', { type: 'warning' }); await request.put(`/outsource/delivery/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleAudit(row: any) {
  try { await ElMessageBox.confirm('确定审核该收发单吗？审核后将扣减/增加库存并生成流水。', '审核', { type: 'warning' }); await request.put(`/outsource/delivery/${row.id}/audit`); ElMessage.success('已审核'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleUnaudit(row: any) {
  try { await ElMessageBox.confirm('确定反审核该收发单吗？反审核后将回滚库存与流水，回到草稿。', '反审核', { type: 'warning' }); await request.put(`/outsource/delivery/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

onActivated(() => {
  // 详情页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(OUTSOURCE_DELIVERY_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(OUTSOURCE_DELIVERY_DIRTY_KEY)
    loadData()
  }
})
onMounted(() => { loadWarehouseOptions(); loadData() })

</script>

<template>
  <div class="delivery-page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="收发单号" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="加工厂"><RemoteSelect v-model="query.factoryId" :fetch="fetchFactories" placeholder="全部" clearable style="width:180px" /></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="router.push('/outsource/delivery/add')">新增</el-button>
      </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 2026-09-16 流程重构：手工单据只有 发料/调拨；收料/退不良为物料订单自动生成、退料已下线（页签保留以便查历史） -->
        <el-tab-pane label="全部" name="" /><el-tab-pane :label="DeliveryTypeLabel[DeliveryType.DELIVERY]" :name="DeliveryType.DELIVERY" /><el-tab-pane :label="DeliveryTypeLabel[DeliveryType.TRANSFER]" :name="DeliveryType.TRANSFER" /><el-tab-pane :label="`${DeliveryTypeLabel[DeliveryType.RECEIVE]}（自动）`" :name="DeliveryType.RECEIVE" /><el-tab-pane :label="`${DeliveryTypeLabel[DeliveryType.DEFECT_RETURN]}（自动）`" :name="DeliveryType.DEFECT_RETURN" /><el-tab-pane :label="`${DeliveryTypeLabel[DeliveryType.RETURN]}（已下线）`" :name="DeliveryType.RETURN" />
      </el-tabs>

      <el-table :data="tableData" border stripe v-loading="tableLoading" style="width:100%" @row-click="(row: any) => router.push(`/outsource/delivery/detail/${row.id}`)">
        <el-table-column label="日期" width="100"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column prop="code" label="单号" width="170" />
        <el-table-column label="发出仓库/供应商" width="170" show-overflow-tooltip>
          <template #default="{row}"><span v-if="row.supplierDirect"><el-link type="primary" underline="never" @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{row.supplierName||'供应商直发'}}</el-link></span><el-button v-else type="primary" link @click.stop="goWhDetail(row.fromWarehouseId)">{{row.fromWarehouseName||'-'}}</el-button></template>
        </el-table-column>
        <el-table-column label="目标仓库" width="160" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="goWhDetail(row.toWarehouseId)">{{row.toWarehouseName||'-'}}</el-button></template>
        </el-table-column>
        <el-table-column label="物料" min-width="180" show-overflow-tooltip>
          <template #default="{row}"><span v-if="row.itemSummary">{{row.itemSummary}}</span><span v-else style="color:var(--app-text-placeholder)">{{row.itemCount||0}}项</span></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90"><template #default="{row}">
          <el-tag :type="DocStatusTag[row.status] || 'info'">
            {{ DocStatusLabel[row.status] || row.status }}
          </el-tag>
        </template></el-table-column>
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link @click.stop="router.push(`/outsource/delivery/detail/${row.id}`)">详情</el-button>
            <el-button type="success" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleAudit(row)">审核</el-button>
            <el-button type="warning" link v-if="row.status===DocStatus.AUDITED" @click.stop="handleUnaudit(row)">反审核</el-button>
            <el-button type="danger" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>
  </div>
</template>

<style scoped>
.delivery-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
