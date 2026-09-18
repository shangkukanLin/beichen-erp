<script setup lang="ts">
/**
 * 物料收货（委外加工 → 物料收货）
 * <p>只看**收货中**（RECEIVING）的物料订单 —— 与后端「只有收货中的订单可收料/退不良」口径一致。
 * 行内「收料」直接进入该单收货详细页并自动弹出收货弹窗；「收货详细」只看记录。</p>
 */
import { reactive, ref, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag } from '@/api/enums'

defineOptions({ name: 'OutsourceMaterialOrderDelivery' })

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const query = reactive({ code: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize, status: MaterialOrderStatus.RECEIVING }
    if (query.code) p.code = query.code
    const r = await request.get<any, any>('/outsource/material-order/page', { params: p })
    tableData.value = r?.records || []
    pagination.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载待收货订单失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; handleQuery() }

const itemsOf = (row: any) => (row?.items || []) as any[]
const totalOf = (row: any) => itemsOf(row).reduce((s: number, it: any) => s + (Number(it.orderQuantity) || 0), 0)
const receivedOf = (row: any) => itemsOf(row).reduce((s: number, it: any) => s + (Number(it.receivedQuantity) || 0) - (Number(it.defectReturnedQty) || 0), 0)
const namesOf = (row: any) => itemsOf(row).map((it: any) => it.materialName).filter(Boolean).join(' / ') || '-'
function progressOf(row: any) {
  const total = totalOf(row)
  return total === 0 ? 0 : Math.min(100, Math.round(receivedOf(row) / total * 100))
}
/**
 * 进入收货详细页：带 add=1 时自动打开收货弹窗，一步完成收料。
 * 追加时间戳是为了让每次点击都是「新的 fullPath」——layout 的 keep-alive 以 fullPath 为 key，
 * 否则复用缓存实例会导致弹窗不再自动弹出。
 */
function goReceive(row: any) { router.push(`/outsource/material-order/delivery/${row.id}?add=1&_t=${Date.now()}`) }
function goDetail(row: any) { router.push(`/outsource/material-order/delivery/${row.id}`) }
/**
 * 退货（2026-09-17）：把已收的物料退回物料商 —— 走**委外物料退货单**（独立单据：源仓扣减 + 冲减应付），
 * 与「退不良」（不良品维修返还/折现退款，写在收货记录里并影响净已收）是两件事。
 * 列表行是订单维度（不知具体收料单），故只带供应商预填；按收料记录退货请进详情页。
 */
function goReturn(row: any) {
  router.push(`/outsource/material-return/add?supplierId=${row.supplierId || ''}`)
}

onActivated(loadData)
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">物料收货（收货中的物料订单）</span></div></template>
      <el-form :inline="true" :model="query" style="margin-bottom:12px">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="物料订单号" clearable style="width:200px" @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <!-- 表格/按钮/标签一律不设 size="small"：与「物料订单」等列表页保持同一字号（2026-09-16 统一）。
           列宽合计 ≈938px（**留余量**）＜ 内容区，保证「一行显示完、不横向滑动」。
           注意内容区宽度会变：行数多出现**纵向滚动条**时内容区少约 15px（963 → 948），
           所以列宽合计按 948 兜底，不能贴着 963 排满。
           为此：①「订单类型」并入订单号单元格第二行小字；②「下单总数/已收/剩余」合并为一列；
           ③订单号做成链接（点它进收货详细）。
           2026-09-17：按用户要求新增行内「退货」按钮（物料退回物料商 → 委外物料退货单）：
           操作列 84→134，由「订单号 −6、最近交货 −4、交期 −4」抵平。 -->
      <el-table :data="tableData" border stripe v-loading="loading" style="width:100%" @row-click="goDetail">
        <el-table-column label="订单号" width="144" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商/加工厂" min-width="90" show-overflow-tooltip />
        <el-table-column label="物料" min-width="100" show-overflow-tooltip><template #default="{ row }">{{ namesOf(row) }}</template></el-table-column>
        <el-table-column label="下单/已收/剩余" width="114" align="center">
          <template #default="{ row }">
            <span>{{ totalOf(row) }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span style="color:var(--app-color-success);font-weight:500">{{ receivedOf(row) }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span :style="{ color: (totalOf(row) - receivedOf(row)) <= 0 ? 'var(--app-color-success)' : 'var(--app-color-warning)', fontWeight: 500 }">{{ totalOf(row) - receivedOf(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货进度" width="80"><template #default="{ row }"><el-progress :percentage="progressOf(row)" :stroke-width="10" :color="progressOf(row) >= 100 ? 'var(--app-color-success)' : 'var(--app-color-primary)'" /></template></el-table-column>
        <el-table-column label="最近交货" width="96"><template #default="{ row }">{{ $fmtDate(row.lastDeliveryTime) }}</template></el-table-column>
        <el-table-column label="交期" width="96"><template #default="{ row }">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column label="状态" width="84" align="center"><template #default="{ row }"><el-tag :type="MaterialOrderStatusTag[row.status] || 'info'" size="small">{{ MaterialOrderStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="134" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goReceive(row)">收料</el-button>
            <!-- 退货：把已收物料退回物料商（委外物料退货单），与「退不良」区分 -->
            <el-button type="warning" link @click.stop="goReturn(row)">退货</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" />
      </div>
    </el-card>
  </div>
</template>
