<template>
  <div class="app-container">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="退单号">
          <el-input v-model="query.code" placeholder="请输入单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="客户">
          <RemoteSelect v-model="query.customerId" :fetch="fetchCustomers" placeholder="请选择客户" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <!-- 状态值为字符串编码（DRAFT/AUDITED/CANCELLED），不能再 Number() 转换（会变成 NaN 导致筛选失效） -->
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option v-for="(label, val) in statusOptions" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="Search" @click="load(1)">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="primary" :icon="Plus" @click="goAdd">新增销售退单</el-button>
        </div>
      </div>
      <el-table :data="list" v-loading="loading" border stripe @row-click="goDetail">
        <el-table-column prop="returnDate" label="退货日期" width="110" />
        <el-table-column prop="code" label="退单号" width="140" />
        <el-table-column label="客户" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.customerId" type="primary" link @click.stop="goCustomer(row.customerId)">{{ row.customerName || '—' }}</el-button>
            <span v-else>{{ row.customerName || '—' }}</span>
          </template>
        </el-table-column>
        <!-- 概况为弹性列：超长自动省略并 tooltip，保证整表尽量不出现横向滚动 -->
        <el-table-column label="退货概况" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemsSummary }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="收费" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.chargeFlag) === 1 && Number(row.chargeAmount) > 0" type="warning" size="small"
              :title="ExchangeChargeTypeLabel[row.chargeType] || ''">
              收费 {{ formatMoney(row.chargeAmount) }}
            </el-tag>
            <span v-else style="color:#c0c4cc">不收费</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row)">详情</el-button>
            <el-button v-if="row.status === SaleReturnStatus.DRAFT" link type="success" @click.stop="doAudit(row)">审核</el-button>
            <el-button v-if="row.status === SaleReturnStatus.AUDITED" link type="warning" @click.stop="doUnAudit(row)">反审核</el-button>
            <!-- 销售退单不提供删除：单据需留痕，只能作废（后端 delete 语义也等同作废） -->
            <el-button v-if="row.status === SaleReturnStatus.DRAFT" link type="danger" @click.stop="doCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        @current-change="(p: number) => load(p)"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onActivated, reactive, ref } from 'vue'
import { SALE_RETURN_DIRTY_KEY, ExchangeChargeTypeLabel } from '@/api/enums'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import {
  getSaleReturnPage,
  auditSaleReturn,
  unAuditSaleReturn,
  cancelSaleReturn,
  SaleReturnStatus,
  SaleReturnStatusLabel,
} from '@/api/sale'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
// Odoo 风格：下拉框展开/搜索时实时查库（不预缓存全量）
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
// 列表/查询显示用的本地轻量列表（组件内维护，不再依赖全局 optionsStore）
const customers = ref<{ id: number; name: string }[]>([])
async function loadCustomers() { try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] } }
const statusOptions = SaleReturnStatusLabel

const query = reactive({
  code: '',
  customerId: undefined as number | undefined,
  status: '' as string,
  pageNum: 1,
  pageSize: 10,
})

function statusLabel(s: any) {
  return SaleReturnStatusLabel[String(s)] ?? '未知'
}
/** 状态为字符串编码（DRAFT/AUDITED/CANCELLED），与后端 status 字段(varchar)一致 */
function statusTagType(s: any) {
  if (String(s) === SaleReturnStatus.AUDITED) return 'success'
  if (String(s) === SaleReturnStatus.CANCELLED) return 'info'
  return 'warning'
}
function formatMoney(v: any) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load(p?: number) {
  if (p) query.pageNum = p
  loading.value = true
  try {
    const res = await getSaleReturnPage({
      code: query.code || undefined,
      customerId: query.customerId,
      status: query.status || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.code = ''
  query.customerId = undefined
  query.status = ''
  load(1)
}

function goAdd() {
  router.push('/sale/return/add')
}
/** 草稿态点进去直接编辑（列表不再单独放「编辑」按钮）；其余状态进只读详情 */
function goDetail(row: any) {
  if (row.status === SaleReturnStatus.DRAFT) router.push(`/sale/return/add?id=${row.id}`)
  else router.push(`/sale/return/detail/${row.id}`)
}
function goCustomer(id?: number) {
  if (id) router.push(`/inventory/customer/detail/${id}`)
}

function doAudit(row: any) {
  ElMessageBox.confirm(`确认审核销售退单 ${row.code}？审核后客户退回的待分类品将入库售后仓增加库存。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await auditSaleReturn(row.id)
      ElMessage.success('审核成功')
      load()
    })
    .catch(() => {})
}

function doUnAudit(row: any) {
  ElMessageBox.confirm(`确认反审核销售退单 ${row.code}？将扣减已入库的待分类品库存。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await unAuditSaleReturn(row.id)
      ElMessage.success('反审核成功')
      load()
    })
    .catch(() => {})
}

function doCancel(row: any) {
  ElMessageBox.confirm(`确认作废销售退单 ${row.code}？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await cancelSaleReturn(row.id)
      ElMessage.success('作废成功')
      load()
    })
    .catch(() => {})
}


onActivated(() => {
  // 详情/新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(SALE_RETURN_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(SALE_RETURN_DIRTY_KEY)
    load()
  }
})
onMounted(() => {
  loadCustomers()
  load()
})

</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
