<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="退货单号">
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
          <el-button type="success" :icon="Plus" @click="goAdd">新增</el-button>
        </div>
      </div>
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1060px > 内容区 948px
           ⇒ 横向滚动 112px。收窄为合计 882px（客户/退货概况保持 min-width，宽屏自动吃余量）。 -->
      <el-table :data="list" v-loading="loading" border stripe @row-click="goDetail">
        <el-table-column prop="returnDate" label="退货日期" width="100" />
        <el-table-column prop="code" label="退货单号" width="120" />
        <el-table-column label="客户" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.customerId" type="primary" link @click.stop="goCustomer(row.customerId)">{{ row.customerName || '—' }}</el-button>
            <span v-else>{{ row.customerName || '—' }}</span>
          </template>
        </el-table-column>
        <!-- 概况为弹性列：超长自动省略并 tooltip，保证整表尽量不出现横向滚动 -->
        <el-table-column label="退货概况" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemsSummary }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="108" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="收费" width="96" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.chargeFlag) === 1 && Number(row.chargeAmount) > 0" type="warning" size="small"
              :title="ExchangeChargeTypeLabel[row.chargeType] || ''">
              收费 {{ formatMoney(row.chargeAmount) }}
            </el-tag>
            <span v-else style="color:#c0c4cc">不收费</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="78" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row)">详情</el-button>
            <!-- F3-3 按钮级权限（方案 A）：动作码跟随页面自动下发 -->
            <el-button v-if="row.status === SaleReturnStatus.DRAFT" v-perm="'sale:return:audit'" link type="success" @click.stop="doAudit(row)">审核</el-button>
            <el-button v-if="row.status === SaleReturnStatus.AUDITED" v-perm="'sale:return:unaudit'" link type="warning" @click.stop="doUnAudit(row)">反审核</el-button>
            <!-- 销售退货单不提供删除：单据需留痕，只能作废（后端 delete 语义也等同作废） -->
            <el-button v-if="row.status === SaleReturnStatus.DRAFT" v-perm="'sale:return:cancel'" link type="danger" @click.stop="doCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="() => load(1)" @current-change="(p: number) => load(p)" />
      </div>
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
// 2026-09-20（F7-146①）：删除 customers / loadCustomers —— 拉回后**模板从未使用**
// （列表直接用后端返回的 row.customerName；查询下拉走 RemoteSelect 的 fetchCustomers 实时查库）。
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
  ElMessageBox.confirm(`确认审核销售退货单 ${row.code}？审核后客户退回的待整理品将入库成品仓增加库存。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await auditSaleReturn(row.id)
      ElMessage.success('已审核')
      load()
    })
    .catch(() => {})
}

function doUnAudit(row: any) {
  ElMessageBox.confirm(`确认反审核销售退货单 ${row.code}？将扣减已入库的待整理品库存。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await unAuditSaleReturn(row.id)
      ElMessage.success('已反审核')
      load()
    })
    .catch(() => {})
}

function doCancel(row: any) {
  ElMessageBox.confirm(`确认作废销售退货单 ${row.code}？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await cancelSaleReturn(row.id)
      ElMessage.success('已作废')
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
  load()
})

</script>

<style scoped>
/* .toolbar / .pager 已删除：按钮组用全局 .toolbar，分页统一为全局 .pagination */

</style>
