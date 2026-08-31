<script setup lang="ts">
import { ref, onMounted, onActivated, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocStatus, DocStatusLabel, DocStatusTag,
  ExchangeChargeTypeLabel, SALE_EXCHANGE_DIRTY_KEY } from '@/api/enums'
import { getSaleExchangePage, auditSaleExchange, unAuditSaleExchange, deleteSaleExchange } from '@/api/sale'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const query = reactive({ kw: '', status: '' })

async function loadData() {
  loading.value = true
  try {
    const res: any = await getSaleExchangePage({ ...query, pageNum: pagination.pageNum, pageSize: pagination.pageSize })
    list.value = res.records || []
    pagination.total = res.total || 0
  } finally { loading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.kw = ''; query.status = ''; handleQuery() }
function handleSizeChange(v: number) { pagination.pageSize = v; pagination.pageNum = 1; loadData() }
function handleCurrentChange(v: number) { pagination.pageNum = v; loadData() }

onMounted(async () => {
  loadData()
  // 兼容旧链接：从销售单详情「换货」带 ?saleOrderId= 跳转 → 转发到独立新增页
  const soId = route.query.saleOrderId
  if (soId !== undefined && soId !== '') {
    router.replace(`/sale/exchange/add?saleOrderId=${soId}`)
  }
})
onActivated(() => {
  if (sessionStorage.getItem(SALE_EXCHANGE_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(SALE_EXCHANGE_DIRTY_KEY)
    loadData()
  }
})

// ============ 跳转 ============
function goAdd() { router.push('/sale/exchange/add') }
function goEdit(row: any) { router.push(`/sale/exchange/add?id=${row.id}`) }
function goDetail(row: any) { router.push(`/sale/exchange/detail/${row.id}`) }

// ============ 审核/反审核/删除 ============
async function handleAudit(row: any) {
  const charged = Number(row.chargeFlag) === 1 && Number(row.chargeAmount) > 0
  const chargeTip = charged
    ? `\n并生成一条向客户收取的费用应收 ${Number(row.chargeAmount).toFixed(2)} 元（台账单号 ${row.code}-FEE）。`
    : ''
  await ElMessageBox.confirm(`确认审核「${row.code}」？审核后退回货品入售后仓(待分类)，换出货品从成品仓扣减。${chargeTip}`, '审核确认', { type: 'warning' })
  await auditSaleExchange(row.id)
  ElMessage.success('已审核'); sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1'); loadData()
}
async function handleUnAudit(row: any) {
  await ElMessageBox.confirm(`确认反审核「${row.code}」？将回滚退回与换出的库存。`, '提示', { type: 'warning' })
  await unAuditSaleExchange(row.id)
  ElMessage.success('已反审核'); sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1'); loadData()
}
async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确认删除换货单「${row.code}」？`, '提示', { type: 'warning' })
  await deleteSaleExchange(row.id)
  ElMessage.success('已删除'); sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1'); loadData()
}
</script>

<template>
  <div>
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="单号">
          <el-input v-model="query.kw" placeholder="换货单号/销售单号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option label="草稿" :value="DocStatus.DRAFT" />
            <el-option label="已审核" :value="DocStatus.AUDITED" />
            <el-option label="已作废" :value="DocStatus.CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="goAdd">新增换货单</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe size="small">
        <el-table-column prop="code" label="换货单号" width="160" />
        <el-table-column prop="saleOrderCode" label="来源销售单" width="150" />
        <el-table-column prop="warehouseInName" label="换入仓(售后)" min-width="140" />
        <el-table-column prop="warehouseOutName" label="换出仓(成品)" min-width="140" />
        <el-table-column prop="exchangeDate" label="换货日期" width="110" />
        <el-table-column label="货值" width="110" align="right">
          <template #default="{ row }">{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="收费" width="140" align="center">
          <template #default="{ row }">
            <span v-if="Number(row.chargeFlag) === 1 && Number(row.chargeAmount) > 0">
              <el-tag type="warning" size="small">收费 {{ Number(row.chargeAmount).toFixed(2) }}</el-tag>
              <span style="margin-left:4px;color:#909399">{{ ExchangeChargeTypeLabel[row.chargeType] || '' }}</span>
            </span>
            <el-tag v-else type="info" size="small">不收费</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small">
              {{ DocStatusLabel[row.status] ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row)">详情</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="primary" link @click="goEdit(row)">编辑</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.AUDITED" type="warning" link @click="handleUnAudit(row)">反审核</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top:12px;justify-content:flex-end" layout="total, sizes, prev, pager, next"
        :total="pagination.total" v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        @size-change="handleSizeChange" @current-change="handleCurrentChange" />
    </el-card>
  </div>
</template>
