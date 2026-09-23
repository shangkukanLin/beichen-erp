<script setup lang="ts">
// 应收详情（2026-09-23 用户要求：原「应收详情」抽屉改为独立页面，与账单详情页一致）
// —— 按 id 回源单条台账；列表行点击 / 行内「详情」按钮都跳到这里。
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SettlementStatus, SettlementStatusLabel, sourceBillTypeLabel, SubjectType } from '@/api/enums'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<any>({})

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | undefined {
  if (s === SettlementStatus.UNSETTLED) return 'danger'
  if (s === SettlementStatus.PARTIAL) return 'warning'
  if (s === SettlementStatus.SETTLED) return 'success'
  if (s === SettlementStatus.CANCELLED) return 'info'
  return undefined
}
/** 往来单位：客户应收看客户名，供应商应收（应付转应收产生）看供应商名 */
function subjectName(d: any) {
  return d?.subjectType === SubjectType.SUPPLIER ? (d?.supplierName || '—') : (d?.customerName || '—')
}

async function load() {
  loading.value = true
  try {
    const r: any = await request.get('/finance/receivable/' + route.params.id)
    detail.value = r || {}
  } catch { detail.value = {} } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原「返回」按钮已删）；本页只读无操作 -->
  <PageShell :loading="loading" back-fallback="/finance/receivable">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">应收详情 — {{ detail.billNo || '' }}</span>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单据号">{{ detail.billNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ SettlementStatusLabel[String(detail.status)] || detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item :label="detail.subjectType === SubjectType.SUPPLIER ? '供应商' : '客户'">{{ subjectName(detail) }}</el-descriptions-item>
        <el-descriptions-item label="来源类型">{{ sourceBillTypeLabel(detail.sourceBillType) }}</el-descriptions-item>
        <el-descriptions-item label="来源单号">{{ detail.sourceBillNo }}</el-descriptions-item>
        <el-descriptions-item label="到期日">{{ detail.dueDate }}</el-descriptions-item>
        <el-descriptions-item label="应收金额">{{ fmt(detail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="已收金额">{{ fmt(detail.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="未收金额"><span style="color:var(--app-color-danger)">{{ fmt(detail.unpaidAmount) }}</span></el-descriptions-item>
        <!-- 制单人（2026-09-23 用户口径）：台账按单自动生成 ⇒ **只显示制单人，无审核流程故不显示审核人** -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </PageShell>
</template>
