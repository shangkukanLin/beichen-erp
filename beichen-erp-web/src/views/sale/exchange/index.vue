<script setup lang="ts">
import { ref, onMounted, onActivated, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocStatus, DocStatusLabel, DocStatusTag,
  ExchangeChargeTypeLabel, SALE_EXCHANGE_DIRTY_KEY } from '@/api/enums'
import { getSaleExchangePage, auditSaleExchange, unAuditSaleExchange, cancelSaleExchange } from '@/api/sale'

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
  } catch { list.value = []; pagination.total = 0 } finally { loading.value = false }
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
/* 2026-09-24（用户口径）：goEdit 已移除 —— 草稿态编辑统一在详情页内联完成，列表不再提供编辑入口；
   新增仍走 /sale/exchange/add（可带 saleOrderId 预填）。 */
function goDetail(row: any) { router.push(`/sale/exchange/detail/${row.id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }

// ============ 审核/反审核/作废 ============
async function handleAudit(row: any) {
  const charged = Number(row.chargeFlag) === 1 && Number(row.chargeAmount) > 0
  const chargeTip = charged
    ? `\n并生成一条向客户收取的费用应收 ${Number(row.chargeAmount).toFixed(2)} 元（台账单号 ${row.code}-FEE）。`
    : ''
  // 2026-09-20（F7-144）：confirm 必须单独 try/catch —— 用户点「取消」时 confirm 会 reject，
  // 原先三个动作都没有 catch ⇒ 每次取消都产生未处理的 Promise rejection（同模块 order/return 两页都已防护）。
  try {
    await ElMessageBox.confirm(`确认审核「${row.code}」？审核后退回货品入成品仓(待整理)，换出货品从成品仓扣减。${chargeTip}`, '审核确认', { type: 'warning' })
  } catch { return }
  await auditSaleExchange(row.id)
  ElMessage.success('已审核'); sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1'); loadData()
}
async function handleUnAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核「${row.code}」？将回滚退回与换出的库存。`, '提示', { type: 'warning' })
  } catch { return }
  await unAuditSaleExchange(row.id)
  ElMessage.success('已反审核'); sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1'); loadData()
}
async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认作废换货单「${row.code}」？作废后单据留痕，不可恢复。`, '提示', { type: 'warning' })
  } catch { return }
  await cancelSaleExchange(row.id)
  ElMessage.success('已作废'); sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1'); loadData()
}
</script>

<template>
  <div class="page-list">
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
          <el-button type="success" :icon="'Plus'" @click="goAdd">新增</el-button>
        </el-form-item>
      </el-form>

      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1220px > 内容区 948px
           ⇒ 横向滚动 272px。收窄为合计 924px（换货概况/换入仓/换出仓保持 min-width，宽屏自动吃余量）。
           2026-09-26 B4（用户口径「显示完整 + 单号可点」）：
             · 换货单号 120→**150 固定**（实测需 143）并做成链接进详情；
             · 换货概况 min130→170（"退1 → 换1"复合文本实测需 196，已登记白名单 + tooltip）；
             · **收费列改单行**：收费类型从并排小字挪进 tag 的 title（与销售退货页同一写法）
               —— 原"tag + 品质差价"并排实测需 159px，改后只需 ~110px；
             · 换货日期 100→92、换入/换出仓 min110→100、状态 80→76 抵平。
           合计 = 92+150+170+100+100+110+76+132 = **930** ✓ -->
      <el-table v-loading="loading" :data="list" border stripe @row-click="goDetail">
        <el-table-column prop="exchangeDate" label="换货日期" width="92" />
        <el-table-column label="换货单号" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <!-- 换货概况：退回侧 → 换出侧（来源销售单在详情中可见） -->
        <el-table-column label="换货概况" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.exchangeSummary || '—' }}</template>
        </el-table-column>
        <el-table-column label="换入仓(售后)" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.warehouseInId" type="primary" link @click.stop="goWarehouse(row.warehouseInId)">{{ row.warehouseInName || '—' }}</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="换出仓(成品)" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.warehouseOutId" type="primary" link @click.stop="goWarehouse(row.warehouseOutId)">{{ row.warehouseOutName || '—' }}</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="收费" width="110" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="Number(row.chargeFlag) === 1 && Number(row.chargeAmount) > 0" type="warning" size="small"
              :title="ExchangeChargeTypeLabel[row.chargeType] || ''">收费 {{ Number(row.chargeAmount).toFixed(2) }}</el-tag>
            <el-tag v-else type="info" size="small">不收费</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="76" align="center">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small">
              {{ DocStatusLabel[row.status] ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 2026-09-24（用户口径）：反审核与编辑都收进详情页（详情草稿态可就地改）⇒ 操作列 174→132。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row)">详情</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="success" link @click="handleAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" type="danger" link @click="handleCancel(row)">作废</el-button>
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
