<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" class="query-form">
        <el-form-item label="单号">
          <el-input v-model="query.code" placeholder="退货单号" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="供货商">
          <RemoteSelect v-model="query.supplierId" :fetch="fetchSuppliers" placeholder="请选择" clearable style="width:180px" domain="supplier" />
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
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" border stripe v-loading="loading" row-key="id" @row-click="handleDetail">
        <el-table-column prop="returnDate" label="退货日期" width="100" align="center" />
        <!-- 2026-09-25 B3：退货单号 → 详情；退货明细 → 产品可点（单值直链 / 多值弹层） -->
        <el-table-column label="退货单号" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleDetail(row)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="退货仓库" min-width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleWarehouseClick(row.warehouseId)">{{ warehouseName(row.warehouseId) }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="退货明细" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <EntityLinks :items="row.items" target="product" qty-key="quantity">
              <span>{{ row.itemsSummary || '-' }}</span>
            </EntityLinks>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="退货总金额" width="100" align="right">
          <template #default="{ row }">{{ row.totalAmount ? Number(row.totalAmount).toFixed(2) : '0.00' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <!-- 2026-09-24（用户口径）：反审核与编辑都收进详情页（详情草稿态本就可就改明细并保存）⇒ 操作列 200→132。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button>
            <!-- F3-3 按钮级权限（方案 A）：动作码跟随页面自动下发 -->
            <el-button v-if="row.status === ReturnStatus.DRAFT" v-perm="'purchase:return:audit'" type="success" link @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === ReturnStatus.DRAFT" v-perm="'purchase:return:cancel'" type="danger" link @click.stop="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { PURCHASE_RETURN_DIRTY_KEY, WarehouseCategory, WarehouseType } from '@/api/enums'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPurchaseReturnPage,
  auditPurchaseReturn, cancelPurchaseReturn, unAuditPurchaseReturn,
  ReturnStatus, ReturnStatusLabel,
  type PurchaseReturn
} from '@/api/purchase'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import EntityLinks from '@/components/EntityLinks.vue'
import { useDomainRefresh } from '@/utils/dataFreshness'

const router = useRouter()
const list = ref<PurchaseReturn[]>([])
const loading = ref(false)
const query = reactive({ code: '', supplierId: '' as string | number, status: '' as string | number })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

const supplierOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw, supplierType: 'product' } })
// 2026-09-20（F7-149）：采购退货出库仓统一为**自有成品仓**（原先不过滤 ⇒ 可选出委外仓/辅料仓）
const fetchWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WarehouseType.FINISHED } })
const loadSupplierOptions = async () => { try { const r: any = await fetchSuppliers(''); supplierOptions.value = r?.records || [] } catch { supplierOptions.value = [] } }
const loadWarehouseOptions = async () => { try { const r: any = await fetchWarehouses(''); warehouseOptions.value = r?.records || [] } catch { warehouseOptions.value = [] } }

const statusOptions = [
  { label: ReturnStatusLabel[ReturnStatus.DRAFT], value: ReturnStatus.DRAFT },
  { label: ReturnStatusLabel[ReturnStatus.AUDITED], value: ReturnStatus.AUDITED },
  { label: ReturnStatusLabel[ReturnStatus.CANCELLED], value: ReturnStatus.CANCELLED },
]
function statusLabel(s?: number) { return s != null ? (ReturnStatusLabel[s] || '') : '' }
function statusType(s?: any): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined {
  if (s === ReturnStatus.DRAFT) return 'info'
  if (s === ReturnStatus.AUDITED) return 'success'
  if (s === ReturnStatus.CANCELLED) return 'danger'
  return undefined
}
function warehouseName(id?: number) {
  const w = warehouseOptions.value.find((x: any) => x.id === id)
  return w ? w.warehouseName : ''
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) params.code = query.code
    if (query.supplierId) params.supplierId = query.supplierId
    if (query.status !== '' && query.status != null) params.status = query.status
    const res = await getPurchaseReturnPage(params)
    list.value = res.records
    pagination.total = res.total
  } finally { loading.value = false }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.supplierId = ''; query.status = ''; handleQuery() }
function handleAdd() { router.push('/inventory/purchase-return/add') }
/* 2026-09-24（用户口径）：handleEdit 已移除 —— 草稿态编辑统一在详情页内联完成（本页详情本就可改明细并保存），
   列表不再提供编辑入口；新增仍走 /inventory/purchase-return/add。 */
function handleWarehouseClick(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }

async function handleDetail(row: PurchaseReturn) {
  router.push(`/inventory/purchase-return/detail/${row.id}`)
}

async function handleAudit(row: PurchaseReturn) {
  try {
    await ElMessageBox.confirm(`确认审核退货单「${row.code}」？审核后出库减库存并冲减应付账款。`, '确认审核', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditPurchaseReturn(row.id as number)
    ElMessage.success('已审核')
    loadData()
  } catch { /* */ }
}
async function handleUnAudit(row: PurchaseReturn) {
  try {
    await ElMessageBox.confirm(`确认反审核退货单「${row.code}」？反审核后恢复库存、清除应付台账，回到草稿状态。`, '确认反审核', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await unAuditPurchaseReturn(row.id as number)
    ElMessage.success('已反审核')
    loadData()
  } catch { /* */ }
}
async function handleCancel(row: PurchaseReturn) {
  try {
    await ElMessageBox.confirm(`确认作废退货单「${row.code}」？`, '确认作废', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelPurchaseReturn(row.id as number)
    ElMessage.success('已作废')
    loadData()
  } catch { /* */ }
}

// 新增/编辑页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
useDomainRefresh('purchaseReturn', () => {
    loadData()
}, PURCHASE_RETURN_DIRTY_KEY)
onMounted(() => {
  loadSupplierOptions()
  loadWarehouseOptions()
  loadData()
})

</script>

<style scoped>
/* 根容器已统一到全局（styles/page.css 的 .page-list） */
.query-form { align-items: center; }
/* .query-actions 已删除：模板中无人引用（按钮组用的是全局 .toolbar） */
</style>
