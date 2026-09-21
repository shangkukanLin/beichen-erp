<script setup lang="ts">
/**
 * 成品收货（委外加工 → 成品收货）
 * <p>只看**正在加工**（PRODUCING）的加工单 —— 与后端「只有生产中的加工单可录入收货」口径一致。
 * 行内「收货」直接进入该单收货详细页并自动弹出新增收货弹窗；「收货详细」只看记录。</p>
 * <p>2026-09-21（用户口径）：本页**只做收货**，退回（红冲收货）不再出现在本页 ——
 * 有加工单的退回到该单收货详细页用「加工退货」，无单的退回到「加工退货」菜单页的「加工退货」页签
 * 用「新增无单加工退货」；两者最终都汇总到那张台账里（用「关联加工单」列区分）。
 * （原先挂在本页下方的「无单加工退货」区块已按该口径迁走。）</p>
 */
import { reactive, ref, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { OutsourceOrderStatusLabel, OutsourceOrderStatusTag } from '@/api/enums'

defineOptions({ name: 'OutsourceOrderDelivery' })

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const query = reactive({ code: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/order-delivery/order-page', {
      params: { page: pagination.pageNum, size: pagination.pageSize, code: query.code || undefined }
    })
    tableData.value = r?.records || []
    pagination.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载待收货订单失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; handleQuery() }

/** 收货进度（口径与后端 summary 一致：只算已审核收货） */
function progressOf(row: any) {
  const total = Number(row.totalQuantity || 0)
  if (!total) return 0
  return Math.min(100, Math.round(Number(row.deliveredQuantity || 0) / total * 100))
}
/**
 * 进入收货详细页：带 add=1 时自动打开新增收货弹窗，一步完成收货。
 * 追加时间戳是为了让每次点击都是「新的 fullPath」——layout 的 keep-alive 以 fullPath 为 key，
 * 否则复用缓存实例会导致弹窗不再自动弹出。
 */
function goDelivery(row: any) { router.push(`/outsource/order/delivery/${row.id}?add=1&_t=${Date.now()}`) }
function goDetail(row: any) { router.push(`/outsource/order/delivery/${row.id}`) }

onActivated(() => { loadData() })
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">成品收货（正在加工的加工单）</span></div></template>
      <el-form :inline="true" :model="query" style="margin-bottom:12px">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="加工单号" clearable style="width:200px" @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <!-- 表格/按钮/标签一律不设 size="small"：与「加工订单」等列表页保持同一字号（2026-09-16）。
           列宽合计 ≈942px（**留 20px 余量**）＜ 内容区，保证「一行显示完、不横向滑动」。
           注意：内容区宽度不是固定的 —— 本页行数多、页面变高时会出现**纵向滚动条**，
           内容区会再少约 15px（实测 963 → 948），所以不能贴着 963 排满。
           2026-09-17：①按用户要求「下单/已收/剩余」+40（114→154）、「收货进度」+20（80→100），
           由弹性列「产品」min-width 等量减回（100→77）；②新增行内「退货」按钮（良品退回加工厂 →
           加工退货单），操作列 84→138，由「加工单号 −12、下单/已收/剩余 −10、收货进度 −8、
           最近收货 −8、计划完成 −8、状态 −8」抵平。
           2026-09-21（用户口径「成品收货页只留加工退货、退回走红冲收货」）：**移除**行内「退货」按钮 ⇒
           操作列 124→84；腾出的 40px **自动归弹性列「产品」**（无需手工抵平，合计仍 ≤ 容器 ⇒ 依旧不横向滑动）。 -->
      <el-table :data="tableData" border stripe v-loading="loading" style="width:100%" @row-click="goDetail">
        <el-table-column label="加工单号" width="140" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column prop="factoryName" label="加工厂" min-width="90" show-overflow-tooltip />
        <el-table-column prop="productNames" label="产品" min-width="70" show-overflow-tooltip />
        <el-table-column label="下单/已收/剩余" width="144" align="center">
          <template #default="{ row }">
            <span>{{ row.totalQuantity }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span style="color:var(--app-color-success);font-weight:500">{{ row.deliveredQuantity }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span :style="{ color: Number(row.remainingQuantity) <= 0 ? 'var(--app-color-success)' : 'var(--app-color-warning)', fontWeight: 500 }">{{ row.remainingQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货进度" width="92">
          <template #default="{ row }"><el-progress :percentage="progressOf(row)" :stroke-width="10" :color="progressOf(row) >= 100 ? 'var(--app-color-success)' : 'var(--app-color-primary)'" /></template>
        </el-table-column>
        <el-table-column label="最近收货" width="98">
          <template #default="{ row }">{{ $fmtDate(row.latestDeliveryDate) }}</template>
        </el-table-column>
        <el-table-column label="计划完成" width="98">
          <template #default="{ row }">{{ $fmtDate(row.planEndDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="82" align="center">
          <template #default="{ row }"><el-tag :type="OutsourceOrderStatusTag[row.status] || 'info'" size="small">{{ OutsourceOrderStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="84" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDelivery(row)">收货</el-button>
            <!-- 2026-09-21（用户口径）：退回进详情页做「加工退货」红冲收货，原「退货」按钮已移除 -->
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" />
      </div>
    </el-card>

  </div>
</template>
