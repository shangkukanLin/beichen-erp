<template>
  <!-- 列表页统一骨架（2026-09-23/24 全站定稿口径）：.page-list > 筛选卡 + 表格卡 -->
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
              <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="移出仓">
            <el-select v-model="query.fromWarehouseId" placeholder="全部" clearable filterable style="width:170px">
              <el-option v-for="w in moveWarehouses" :key="w.id" :label="whLabel(w)" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="移入仓">
            <el-select v-model="query.toWarehouseId" placeholder="全部" clearable filterable style="width:170px">
              <el-option v-for="w in moveWarehouses" :key="w.id" :label="whLabel(w)" :value="w.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1100px > 内容区 956px
           ⇒ 横向滚动 144px。收窄为合计 832px。
           2026-09-26 B5a（用户口径「数据显示完整 + 单号/仓库可点」）：
           ①单号 min120→**146 固定**并做成链接进详情；
           ②移出/移入仓库做成链接进**对应仓库详情**（委外仓/自有物料仓自动分流，见 goWarehouseDetail）；
           ③物料明细改 EntityLinks（物料可点，多值弹层逐项）。
           2026-09-26 B8（白名单收尾）：移出/移入仓库 min112→**189**（委外仓名 = 主体名 +「委外仓库」后缀，
             实测需 189 ⇒ 真实仓名完整显示）；物料明细 min140→120 抵平（多物料汇总本身无上界，仍白名单 + tooltip + 弹层）。
           声明合计 = 146+189+189+120+100+78+132 = **954** ✓ -->
      <el-table v-loading="loading" :data="tableData" border stripe @row-click="handleDetail">
        <el-table-column label="单号" width="146" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="handleDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column label="移出仓库" min-width="189" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.fromWarehouseId" type="primary" link @click.stop="goWarehouseDetail(row.fromWarehouseId)">{{ warehouseName(row.fromWarehouseId) }}</el-button>
            <span v-else>{{ warehouseName(row.fromWarehouseId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="移入仓库" min-width="189" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.toWarehouseId" type="primary" link @click.stop="goWarehouseDetail(row.toWarehouseId)">{{ warehouseName(row.toWarehouseId) }}</el-button>
            <span v-else>{{ warehouseName(row.toWarehouseId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="物料明细" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <EntityLinks :items="row.items" target="material" qty-key="quantity">
              <span>{{ row.itemsSummary || '-' }}</span>
            </EntityLinks>
          </template>
        </el-table-column>
        <el-table-column label="移仓日期" width="100" align="center">
          <template #default="{ row }">{{ $fmtDate(row.moveDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="78" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <!-- 2026-09-24（用户口径）：反审核移入详情页 ⇒ 操作列 174→132（详情/审核/作废 3 个按钮）。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button>
            <el-button v-perm="'stock:material-move'" v-if="row.status === DocStatus.DRAFT" type="success" link @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-perm="'stock:material-move'" v-if="row.status === DocStatus.DRAFT" type="danger" link @click.stop="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="(v:number)=>{pagination.pageSize=v;pagination.pageNum=1;loadData()}" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { WarehouseCategory, WarehouseType, INVENTORY_MATERIAL_MOVE_DIRTY_KEY } from '@/api/enums'
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import EntityLinks from '@/components/EntityLinks.vue'

const router = useRouter()
const route = useRoute()
const query = reactive({ status: '', fromWarehouseId: undefined as number | undefined, toWarehouseId: undefined as number | undefined })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const tableData = ref<any[]>([])

const statusOptions = [
  { label: DocStatusLabel[DocStatus.DRAFT], value: DocStatus.DRAFT },
  { label: DocStatusLabel[DocStatus.AUDITED], value: DocStatus.AUDITED },
  { label: DocStatusLabel[DocStatus.CANCELLED], value: DocStatus.CANCELLED }
]

/**
 * 物料移仓的仓库池（2026-09-24 用户口径）：**我方物料仓（辅料仓）+ 委外仓**。
 * 这正是原「物料收发单-调拨」的仓库范围；物料移仓不带任何类型切换（用户口径：不要调拨），
 * 因此两端都从同一个池里选，任何两端组合都是合法的"搬库"。
 */
const warehouseOptions = ref<any[]>([])
const moveWarehouses = ref<any[]>([])

function isMaterialPool(w: any) {
  return w?.warehouseCategory === WarehouseCategory.OUTSOURCE || w?.warehouseType === WarehouseType.AUXILIARY
}
function whLabel(w: any) {
  const kind = w?.warehouseCategory === WarehouseCategory.OUTSOURCE ? '委外仓' : '我方物料仓'
  return `${w.warehouseName}（${kind}）`
}

/** 名称映射要覆盖任意仓（列表只回 ID），故拉全量后本地映射；下拉只给物料池 */
async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } })
    warehouseOptions.value = r?.records || []
    moveWarehouses.value = warehouseOptions.value.filter(isMaterialPool)
  } catch { warehouseOptions.value = []; moveWarehouses.value = [] }
}

function warehouseName(id?: number) {
  const w = warehouseOptions.value.find((x: any) => x.id === id)
  return w ? w.warehouseName : '-'
}
/**
 * 仓库详情分流（2026-09-26 B5a）：物料移仓的移出/移入仓可能是**委外仓**或**我方物料仓**，
 * 两者详情页不同（与物料退货页 goWarehouseDetail 同口径）。仓库列表本页已全量加载 ⇒ 零额外请求。
 */
function goWarehouseDetail(id?: number) {
  if (id == null) return
  const w = warehouseOptions.value.find((x: any) => x.id === id)
  if (w?.warehouseCategory === WarehouseCategory.OUTSOURCE) router.push(`/outsource/warehouse/detail/${id}`)
  else router.push(`/inventory/warehouse/detail/${id}`)
}
function statusType(s?: string) { return DocStatusTag[s || ''] || '' }

async function loadData() {
  loading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.status) params.status = query.status
    if (query.fromWarehouseId) params.fromWarehouseId = query.fromWarehouseId
    if (query.toWarehouseId) params.toWarehouseId = query.toWarehouseId
    const res = await request.get<any, any>('/inventory/material-move/page', { params })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = []; pagination.total = 0 }
  finally { loading.value = false }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.status = ''; query.fromWarehouseId = undefined; query.toWarehouseId = undefined; pagination.pageNum = 1; loadData() }

function handleAdd() { router.push('/inventory/material-move/add') }
function handleDetail(row: any) { router.push(`/inventory/material-move/detail/${row.id}`) }

// 与成品移仓 F7-29 同口径：把「用户取消」与「请求失败」分开
async function handleAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认审核物料移仓单「${row.code}」？将从移出仓扣减物料库存并增加到移入仓。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/material-move/${row.id}/audit`)
    ElMessage.success('已审核')
    loadData()
  } catch { /* 已由 request 拦截器提示 */ }
}

async function handleUnAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核物料移仓单「${row.code}」？将退回移出仓库存并从移入仓扣回。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/material-move/${row.id}/un-audit`)
    ElMessage.success('已反审核')
    loadData()
  } catch { /* 已由 request 拦截器提示 */ }
}

async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认作废物料移仓单「${row.code}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/inventory/material-move/${row.id}/cancel`)
    ElMessage.success('已作废')
    loadData()
  } catch { /* 已由 request 拦截器提示 */ }
}

onActivated(() => {
  if (sessionStorage.getItem(INVENTORY_MATERIAL_MOVE_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(INVENTORY_MATERIAL_MOVE_DIRTY_KEY)
    loadData()
  }
})
onMounted(async () => {
  await loadWarehouses()
  loadData().then(() => {
    // 库存流水链接跳转：直接进入该单据的详情页
    const billId = route.query.billId
    if (billId) router.push(`/inventory/material-move/detail/${billId}`)
  })
})
</script>

<style scoped>
/* 根容器/卡片内边距/分页样式均已统一到全局（styles/page.css） */
.query-form { align-items: center; }
</style>
