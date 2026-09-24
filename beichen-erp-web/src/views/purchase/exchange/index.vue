<script setup lang="ts">
import { ref, onMounted, onActivated, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocStatus, DocStatusLabel, DocStatusTag, PURCHASE_EXCHANGE_DIRTY_KEY } from '@/api/enums'
import {
  getPurchaseExchangePage, auditPurchaseExchange, unAuditPurchaseExchange, cancelPurchaseExchange,
} from '@/api/purchase'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const query = reactive({ kw: '', status: '' })

async function loadData() {
  loading.value = true
  try {
    const res: any = await getPurchaseExchangePage({ ...query, pageNum: pagination.pageNum, pageSize: pagination.pageSize })
    list.value = res.records || []
    pagination.total = res.total || 0
  } catch { list.value = []; pagination.total = 0 } finally { loading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.kw = ''; query.status = ''; handleQuery() }
function handleSizeChange(v: number) { pagination.pageSize = v; pagination.pageNum = 1; loadData() }
function handleCurrentChange(v: number) { pagination.pageNum = v; loadData() }

onMounted(async () => {
  loadData()
  // 兼容从采购单详情「换货」带 ?purchaseOrderId= 跳转 → 转发到独立新增页
  const poId = route.query.purchaseOrderId
  if (poId !== undefined && poId !== '') {
    router.replace(`/inventory/purchase-exchange/add?fromOrder=${poId}`)
  }
})
onActivated(() => {
  if (sessionStorage.getItem(PURCHASE_EXCHANGE_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(PURCHASE_EXCHANGE_DIRTY_KEY)
    loadData()
  }
})

// ============ 跳转 ============
function goAdd() { router.push('/inventory/purchase-exchange/add') }
function goEdit(row: any) { router.push(`/inventory/purchase-exchange/add?id=${row.id}`) }
function goDetail(row: any) { router.push(`/inventory/purchase-exchange/detail/${row.id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }

// ============ 审核/反审核/作废 ============
async function handleAudit(row: any) {
  const ret = Number(row.totalReturnAmount || 0)
  const inn = Number(row.totalInAmount || 0)
  // 是否付费：方向是"我方付给供货商" ⇒ 审核额外生成一条正向应付
  const fee = Number(row.chargeFlag) === 1 ? Number(row.chargeAmount || 0) : 0
  const feeText = fee > 0 ? `；另生成付费应付 ${fee.toFixed(2)}（我方付给供货商）` : ''
  // 2026-09-20（F7-154）：confirm 单独 try/catch —— 点「取消」时 confirm 会 reject，
  // 原先三处都没有 catch ⇒ 每次都产生未处理 rejection（同模块 purchase/order、purchase/return 两页都已防护）。
  try {
    await ElMessageBox.confirm(
      `确认审核「${row.code}」？审核后退回货品从我方仓扣减（退给供货商）、换入良品入库；`
      + `并生成两条应付台账（退回冲减 ${ret.toFixed(2)} / 换入新增 ${inn.toFixed(2)}），净额 ${(inn - ret).toFixed(2)}${feeText}。`,
      '审核确认', { type: 'warning' })
  } catch { return }
  await auditPurchaseExchange(row.id)
  ElMessage.success('已审核'); sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1'); loadData()
}
async function handleUnAudit(row: any) {
  const feeText = Number(row.chargeFlag) === 1 ? '（含付费台账）' : ''
  try {
    await ElMessageBox.confirm(`确认反审核「${row.code}」？将回滚退回与换入的库存，并作废应付台账${feeText}。`, '提示', { type: 'warning' })
  } catch { return }
  await unAuditPurchaseExchange(row.id)
  ElMessage.success('已反审核'); sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1'); loadData()
}
async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认作废换货单「${row.code}」？作废后单据留痕，不可恢复。`, '提示', { type: 'warning' })
  } catch { return }
  await cancelPurchaseExchange(row.id)
  ElMessage.success('已作废'); sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1'); loadData()
}
</script>

<template>
  <div class="page-list">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="单号">
          <el-input v-model="query.kw" placeholder="换货单号/采购单号" clearable @keyup.enter="handleQuery" />
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
          <el-button type="success" :icon="'Plus'" @click="goAdd">新增</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe @row-click="goDetail">
        <!-- 列宽全部用 min-width 以「一屏一行显示」：来源采购单 / 应付净额 只在详情页展示。
             2026-09-21（UI 优化）：原 min-width 合计 915px（1262 窗口下刚好铺满 948、零余量 ⇒ 窗口略窄就横向滚动），
             现收到 **859px**（项目设计线 ≤900），给窗口宽度留出余量；内容不变，「操作」列保持 155 不动。 -->
        <el-table-column prop="exchangeDate" label="换货日期" min-width="88" />
        <el-table-column prop="code" label="换货单号" min-width="134" />
        <!-- 换货概况：退回侧 → 换入侧；「付费」标签（2026-09-21）标出"要向供货商付费"的单据。
             放在 min-width 弹性列内 ⇒ 不新增列、不改变列宽合计（本页面有"一行显示完、不横向滚动"的守卫断言）。 -->
        <el-table-column label="换货概况" min-width="138" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="Number(row.chargeFlag) === 1" type="warning" size="small" style="margin-right:4px"
              :title="'我方付给供货商 ' + Number(row.chargeAmount || 0).toFixed(2)">付费</el-tag>
            {{ row.exchangeSummary || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供货商" min-width="94" show-overflow-tooltip />
        <el-table-column label="退回出库仓" min-width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.warehouseOutId" type="primary" link @click.stop="goWarehouse(row.warehouseOutId)">{{ row.warehouseOutName || '—' }}</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="换入入库仓" min-width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.warehouseInId" type="primary" link @click.stop="goWarehouse(row.warehouseInId)">{{ row.warehouseInName || '—' }}</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small">
              {{ DocStatusLabel[row.status] ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 2026-09-21 修复：表格有 @row-click="goDetail"，操作列按钮原先**没有 .stop** ⇒ 点「编辑」会被行点击
             抢走到详情页（点「审核」也会先跳详情、确认框飘在详情页上）。与项目其它页面（如物料收货列表的
             @click.stop）保持一致，全部加 .stop。 -->
        <el-table-column label="操作" min-width="155" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">详情</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="primary" link @click.stop="goEdit(row)">编辑</el-button>
            <!-- F3-3 按钮级权限（方案 A）：动作码跟随页面自动下发，无码则后端也会 403 -->
            <el-button v-if="row.status === DocStatus.DRAFT" v-perm="'purchase:exchange:audit'" type="success" link @click.stop="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.AUDITED" v-perm="'purchase:exchange:unaudit'" type="warning" link @click.stop="handleUnAudit(row)">反审核</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" v-perm="'purchase:exchange:cancel'" type="danger" link @click.stop="handleCancel(row)">作废</el-button>
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
