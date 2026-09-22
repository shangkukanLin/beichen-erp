<script setup lang="ts">
// 应付详情（2026-09-23 用户要求：原「应付详情」50% 抽屉改为独立页面，与应收/账单详情同范式）
// —— 按 id 回源单条台账；列表行点击 / 行内「详情」按钮都跳到这里。
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SettlementStatus, SettlementStatusLabel, sourceBillTypeLabel } from '@/api/enums'
import { TYPE_MAP, TYPE_TAG } from '@/constants/supplier'
import request from '@/utils/request'

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
function statusLabel(code?: string) { return code ? (SettlementStatusLabel[code] || code) : '' }
/** 主体类型 code -> 中文（供货商/加工厂/辅料商/方案商） */
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }
/** 负数为退货/扣款类冲减项 */
function isDeduction(d: any) { return Number(d?.amount) < 0 }

async function load() {
  loading.value = true
  try {
    const r: any = await request.get('/finance/payable/' + route.params.id)
    detail.value = r || {}
  } catch { detail.value = {} } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="p" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">应付详情 — {{ detail.billNo || '' }}</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单据号">{{ detail.billNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="主体类型">
          <el-tag v-if="detail.supplierType" :type="TYPE_TAG[detail.supplierType] || 'info'" size="small">{{ typeLabel(detail.supplierType) }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="来源类型">{{ sourceBillTypeLabel(detail.sourceBillType) }}</el-descriptions-item>
        <el-descriptions-item label="来源单号">{{ detail.sourceBillNo }}</el-descriptions-item>
        <el-descriptions-item label="到期日">{{ detail.dueDate }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">
          <!-- 负数=退货/扣款冲减项，标红区分于正常货款 -->
          <span :style="{ color: isDeduction(detail) ? 'var(--app-color-danger)' : undefined }">{{ fmt(detail.amount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="已付金额">{{ fmt(detail.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="未付金额"><span style="color:var(--app-color-danger)">{{ fmt(detail.unpaidAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="是否已转应收">{{ detail.transferredToReceivable ? '已转应收' : '—' }}</el-descriptions-item>
        <!-- 制单人（2026-09-23 用户口径）：台账按单自动生成 ⇒ **只显示制单人，无审核流程故不显示审核人** -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>
