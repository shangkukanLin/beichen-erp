<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { SettleTypeLabel, SettleTypeTag } from '@/api/enums'
import RemoteSelect from '@/components/RemoteSelect.vue'
import {
  getSaleOrderPage, auditSaleOrder, cancelSaleOrder, unAuditSaleOrder, SALE_ORDER_DIRTY_KEY,
  type SaleOrder
} from '@/api/sale'
import { useDomainRefresh } from '@/utils/dataFreshness'

const router = useRouter()

const query = reactive({ code: '', customerId: '' as string | number, status: '' as string })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<SaleOrder[]>([])

const statusOptions = [
  { label: DocStatusLabel[DocStatus.DRAFT], value: DocStatus.DRAFT },
  { label: DocStatusLabel[DocStatus.AUDITED], value: DocStatus.AUDITED },
  { label: DocStatusLabel[DocStatus.CANCELLED], value: DocStatus.CANCELLED }
]

// Odoo 风格：下拉框展开/搜索时实时查库（不预缓存全量）
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })

// 列表显示用的本地轻量列表（组件内维护，不再依赖全局 optionsStore）
const customers = ref<any[]>([])
const warehouses = ref<any[]>([])

async function loadCustomers() { try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] } }
async function loadWarehouses() {
  try {
    const r: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseType: 'FINISHED' } })
    warehouses.value = r?.records || []
  } catch { warehouses.value = [] }
}

async function loadData() {
  tableLoading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.customerId !== '' && query.customerId !== null) params.customerId = query.customerId
    if (query.status) params.status = query.status
    const res = await getSaleOrderPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = []; pagination.total = 0 } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.customerId = ''; query.status = ''; pagination.pageNum = 1; loadData() }

async function handleAudit(row: SaleOrder) {
  try {
    await ElMessageBox.confirm(`确认审核销售单「${row.code}」？审核后将直接出库并生成应收。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditSaleOrder(row.id as number); ElMessage.success('已审核'); loadData()
  } catch { }
}
async function handleCancel(row: SaleOrder) {
  try {
    await ElMessageBox.confirm(`确认作废销售单「${row.code}」？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelSaleOrder(row.id as number); ElMessage.success('已作废'); loadData()
  } catch { }
}
async function handleUnAudit(row: SaleOrder) {
  try {
    await ElMessageBox.confirm(`确认反审核销售单「${row.code}」？将冲回出库与应收。`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await unAuditSaleOrder(row.id as number); ElMessage.success('已反审核'); loadData()
  } catch { }
}

function handleSizeChange(val: number) { pagination.pageSize = val; pagination.pageNum = 1; loadData() }
function handleCurrentChange(val: number) { pagination.pageNum = val; loadData() }
function statusType(s?: string) { return DocStatusTag[s || ''] || '' }
function customerName(id?: number) { const c = customers.value.find(x => x.id === id); return c ? c.name : '' }
function warehouseName(id?: number) { const w = warehouses.value.find(x => x.id === id); return w ? w.warehouseName : '' }
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

function goDetail(row: SaleOrder) { router.push('/inventory/sale/detail/' + row.id) }
function goCustomer(id?: number) { if (id) router.push(`/inventory/customer/detail/${id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
/** 新增走独立页面 /inventory/sale/add。
 *  2026-09-24（用户口径）：列表不再提供「编辑」——草稿态直接在详情页改+存（详情页已支持），
 *  避免同一动作两套入口，故 goEdit 已移除。 */
function goAdd() { router.push('/inventory/sale/add') }
/**
 * 售后退货 / 换货**快捷入口**（2026-09-21 用户口径：销售单列表的操作列要有退货、换货快捷键）。
 * <p>与销售单详情页的「退货 / 换货」完全一致：带 saleOrderId 跳转让新增页自动预填来源销售单并带入
 * （可退/可换）明细，省去再选一次客户与销售单。</p>
 * <p>⚠️ 两点必须保持：①只有**已审核**销售单能发起（未审核还没出库，没有可退的量 —— 后端 saleOrders
 * 也只返回已审核单据）②按钮必须 .stop —— 本列表有 @row-click=goDetail，不阻止冒泡会被行点击抢去详情页
 * （同类问题在采购换货列表踩过，修法是给操作列按钮补 .stop）。</p>
 */
// 2026-10-02（用户口径「统一跳转参数名」）：来源单参数统一为 **fromOrder**（与进货侧一致）；
// 两个新增页同时兼容旧链接的 saleOrderId。
function goReturn(row: SaleOrder) { router.push(`/sale/return/add?fromOrder=${row.id}`) }
function goExchange(row: SaleOrder) { router.push(`/sale/exchange/add?fromOrder=${row.id}`) }

onMounted(() => { loadCustomers(); loadWarehouses(); loadData() })
// 数据变动（新增/编辑页、详情页编辑/审核/反审核/作废）后返回列表时按需刷新，保留查询条件与分页现场
useDomainRefresh('saleOrder', () => {
    loadData()
}, SALE_ORDER_DIRTY_KEY)
</script>

<template>
  <div class="page-list">
    <!-- 列表页统一骨架（2026-09-23 第二轮）：筛选卡(.query-card) + 表格卡(.table-card)，
         筛选行沿用 index.css 的全局约定，页面不再自写样式 -->
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="单号">
            <el-input v-model="query.code" placeholder="请输入单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="客户">
            <RemoteSelect v-model="query.customerId" add-route="/inventory/customer/add" :fetch="fetchCustomers" placeholder="请选择" clearable style="width:160px" domain="customer" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="请选择" clearable style="width:120px">
              <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="goAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1156px > 内容区 956px
           ⇒ 横向滚动 200px。收窄为合计 890px（单号/客户/仓库保持 min-width，宽屏自动吃余量）。 -->
      <el-table v-loading="tableLoading" :data="tableData" border stripe @row-click="goDetail">
        <el-table-column prop="orderDate" label="订单日期" width="100" align="center" />
        <!-- 2026-09-26 B4：单号 min120→**148 固定**并做成链接进详情（后端行自带 id） -->
        <el-table-column label="单号" width="148" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="客户" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.customerId" type="primary" link @click.stop="goCustomer(row.customerId)">{{ customerName(row.customerId) }}</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="出库仓库" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.warehouseId" type="primary" link @click.stop="goWarehouse(row.warehouseId)">{{ warehouseName(row.warehouseId) }}</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="总金额" width="108" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <!-- 结算方式（2026-09-18 按单记）：现金 = **立刻到账**（审核销售单时系统自动收款/核销） -->
        <el-table-column label="结算方式" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="SettleTypeTag[String(row.settleType || 'CREDIT')] || 'info'" size="small">
              {{ SettleTypeLabel[String(row.settleType || 'CREDIT')] || '账期' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="78" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ DocStatusLabel[String(row.status)] || row.status }}</el-tag></template>
        </el-table-column>
        <!-- 2026-09-24（用户口径）：反审核移入详情页（撤销已生效单据 + 冲回应收/回退余额，风险高、
             失败原因只在详情看得见）；列表保留高频的审核。⇒ 操作列 190→168。 -->
        <!-- 2026-09-24（用户口径）：列表不再给「编辑」——草稿态直接在详情页改+存（详情页已支持），
             避免同一动作两套入口。⇒ 操作列 176→132（最多 3 个按钮：详情/退货/换货 或 详情/审核/作废）。 -->
        <el-table-column label="操作" width="132" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">详情</el-button>
            <!-- 售后快捷入口（2026-09-21 用户口径）：仅已审核单据可发起，带 saleOrderId 跳转 ⇒ 新增页自动预填来源销售单与明细 -->
            <el-button v-if="row.status === DocStatus.AUDITED" type="warning" link @click.stop="goReturn(row)">退货</el-button>
            <el-button v-if="row.status === DocStatus.AUDITED" type="warning" link @click.stop="goExchange(row)">换货</el-button>
            <!-- F3-3 按钮级权限（方案 A）：动作码跟随页面自动下发 -->
            <el-button v-if="row.status === DocStatus.DRAFT" v-perm="'sale:order:audit'" type="success" link @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" v-perm="'sale:order:cancel'" type="danger" link @click.stop="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
/* 列表页样式已统一到全局（styles/page.css 的 .page-list/.table-card/.pagination
   + index.css 的 .query-card/.query-bar/.query-form/.toolbar），本页不再自写 */
</style>
