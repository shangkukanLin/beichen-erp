<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell back-fallback="/outsource/stock-loss">
    <template #actions>
      <el-button v-if="info.status === DocStatus.DRAFT" type="primary" @click="goEdit">编辑</el-button>
      <el-button v-if="info.status === DocStatus.DRAFT" type="success" @click="onAudit">审核</el-button>
      <el-button v-if="info.status === DocStatus.AUDITED" type="warning" @click="onUnAudit">反审核</el-button>
      <el-button v-if="info.status === DocStatus.DRAFT" type="danger" @click="onCancel">作废</el-button>
    </template>
    <el-card shadow="never">
      <!-- 卡片页头（标题 + 旧「返回」按钮）已删除：标题交骨架、返回交骨架 -->

      <el-descriptions v-loading="loading" :column="3" border>
        <el-descriptions-item label="报损单号">{{ info?.code || '—' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ info?.warehouseName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="报损日期">{{ info?.lossDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="报损原因">
          {{ LossReasonLabel[info?.lossReason] || info?.lossReason || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="DocStatusTag[info?.status] || 'info'" size="small">
            {{ DocStatusLabel[info?.status] || info?.status || '—' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="报损金额">
          <strong>{{ money(info?.totalAmount) }}</strong>
        </el-descriptions-item>
        <!-- 制单人（2026-09-23 用户口径：单据详情显示「制单人 + 审核人」） -->
        <el-descriptions-item label="制单人">{{ info?.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ info?.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ info?.auditTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="4">{{ info?.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">报损明细</span>
        </div>
      </template>
      <el-table v-loading="tableLoading" :data="items" border stripe show-summary :summary-method="summaries">
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="materialTypeName" label="物料类型" width="120">
          <template #default="{ row }">{{ row.materialTypeName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="spec" label="规格" width="130" />
        <el-table-column label="单位" width="70" align="center">
          <template #default="{ row }">{{ row.unit || '—' }}</template>
        </el-table-column>
        <el-table-column label="报损数量" width="110" align="right">
          <template #default="{ row }">{{ fmtQty(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">{{ money(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 操作按钮（编辑/审核/反审核/作废）已统一上移到页头右侧（PageShell #actions） -->
  </PageShell>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag, LossReasonLabel } from '@/api/enums'
import PageShell from '@/components/PageShell.vue'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const loading = ref(false)
const tableLoading = ref(false)
const info = ref<any>(null)
const items = ref<any[]>([])

// 数量一律整数（2026-09-16）
function fmtQty(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }
function money(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

/** 合计行（列序固定：0物料 1物料类型 2规格 3单位 4数量 5单价 6金额 7备注） */
function summaries({ columns }: any) {
  const sumQty = items.value.reduce((s, r) => s + (Number(r.quantity) || 0), 0)
  const sumAmt = items.value.reduce((s, r) => s + (Number(r.amount) || 0), 0)
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 4) return fmtQty(sumQty)
    if (i === 6) return money(sumAmt)
    return ''
  })
}

async function load() {
  loading.value = true
  tableLoading.value = true
  try {
    info.value = await request.get<any, any>(`/outsource/stock-loss/${id}`)
    items.value = await request.get<any, any>(`/outsource/stock-loss/${id}/items`) || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
    tableLoading.value = false
  }
}

function goEdit() { router.push(`/outsource/stock-loss/edit/${id}`) }

async function onAudit() {
  try {
    await ElMessageBox.confirm('确认审核？审核后将扣减对应仓库的物料库存。', '审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/stock-loss/${id}/audit`)
    ElMessage.success('审核成功，库存已扣减')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function onUnAudit() {
  try {
    await ElMessageBox.confirm('确认反审核？反审核后库存将加回。', '反审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/stock-loss/${id}/un-audit`)
    ElMessage.success('已反审核，库存已加回')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确认作废该报损单？', '作废确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/outsource/stock-loss/${id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(load)
</script>

<style scoped>
/* 页头/根容器/操作条已统一到全局骨架（PageShell + styles/page.css）；
   原 .page / .card-header / .title / .actions 局部样式已删除 */
:deep(.el-card__body) { padding: 16px; }
</style>
