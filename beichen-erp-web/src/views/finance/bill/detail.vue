<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBill, getBillItems, auditBill, unAuditBill, cancelBill, type FinanceBill, type FinanceBillItem } from '@/api/finance'
import { BillType, BillTypeLabel, sourceBillTypeLabel, FINANCE_BILL_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'

const route = useRoute(); const router = useRouter()
// 用 computed 取路由参数：keep-alive 会复用组件，从账单 A 跳到 B 时 route.params.id 会变
const id = computed(() => Number(route.params.id) || 0)
const loading = ref(false)
const detail = ref<FinanceBill>({})
const items = ref<FinanceBillItem[]>([])

const StatusLabel: Record<string, string> = DocStatusLabel
const StatusTag: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'primary'> = DocStatusTag

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function statusLabel(s?: string) { return StatusLabel[s || ''] || s || '-' }
function statusTag(s?: string) { return StatusTag[s || ''] || 'info' }

async function loadDetail() {
  loading.value = true
  try {
    const bill = await getBill(id.value)
    detail.value = bill || {}
    const its = await getBillItems(id.value)
    items.value = Array.isArray(its) ? its : []
  } finally { loading.value = false }
}

/**
 * 审核 / 反审核 / 作废：操作后刷新本页并置脏标志，
 * 返回列表时列表会重新拉取，不会出现「详情已审核、列表还显示草稿」。
 */
async function afterStatusChange() {
  sessionStorage.setItem(FINANCE_BILL_DIRTY_KEY, '1')
  await loadDetail()
}
async function handleAudit() {
  try { await ElMessageBox.confirm('确认审核该账单？', '审核确认', { type: 'warning' }) } catch { return }
  try { await auditBill(id.value); ElMessage.success('账单已审核'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function handleUnAudit() {
  try { await ElMessageBox.confirm('确认反审核该账单？', '反审核确认', { type: 'warning' }) } catch { return }
  try { await unAuditBill(id.value); ElMessage.success('账单已反审核'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废该账单？', '作废确认', { type: 'warning' }) } catch { return }
  try { await cancelBill(id.value); ElMessage.success('账单已作废'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(() => { loadDetail() })
// keep-alive 缓存下再次进入会复用组件，onMounted 不再触发
onActivated(() => { loadDetail() })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">账单信息</span>
          <div style="display:flex;align-items:center;gap:8px">
            <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
            <el-button type="success" size="small" v-if="detail.status===DocStatus.DRAFT" @click="handleAudit">审核</el-button>
            <el-button type="warning" size="small" v-if="detail.status===DocStatus.AUDITED" @click="handleUnAudit">反审核</el-button>
            <el-button type="danger" size="small" v-if="detail.status!==DocStatus.CANCELLED" @click="handleCancel">作废</el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="账单号">{{ detail.billNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="detail.billType===BillType.PAYABLE?'warning':undefined" size="small">
            {{ BillTypeLabel[String(detail.billType)] || detail.billType || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ detail.partnerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账期">
          {{ detail.periodStart || '-' }} ~ {{ detail.periodEnd || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="总额">{{ fmt(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已收付">{{ fmt(detail.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="未收付">
          <span style="color:var(--app-color-danger)">{{ fmt(detail.unpaidAmount) }}</span>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；账单无审核流程 ⇒ 审核人通常为 —） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">账单明细</span></template>
      <el-table :data="items" border stripe>
        <el-table-column label="来源类型" width="120">
          <template #default="{ row }">{{ sourceBillTypeLabel(row.sourceBillType) }}</template>
        </el-table-column>
        <el-table-column prop="sourceBillNo" label="来源单号" min-width="160" />
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="paidAmount" label="已收付" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column prop="unpaidAmount" label="未收付" width="120" align="right">
          <template #default="{ row }"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="130" align="center" />
      </el-table>
    </el-card>

    <div style="display:flex;gap:12px;justify-content:center">
      <el-button @click="router.push('/finance/bill')">返回列表</el-button>
    </div>
  </div>
</template>
