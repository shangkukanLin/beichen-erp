<script setup lang="ts">
// 收款单详情（2026-09-23 用户要求：原「收款单详情」50% 抽屉改为独立页面）
// —— 按 id 回源单头 + 核销明细；列表行点击 / 行内「详情」按钮都跳到这里。
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DocStatus, DocStatusLabel, SubjectType, SubjectTypeLabel } from '@/api/enums'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | undefined {
  if (s === DocStatus.DRAFT) return 'warning'
  if (s === DocStatus.AUDITED) return 'success'
  if (s === DocStatus.CANCELLED) return 'info'
  return undefined
}
/** 往来单位：客户收款看客户名，供应商收款（如应付转应收）看供应商名 */
function subjectLabel(d: any) {
  return d?.subjectType === SubjectType.SUPPLIER ? (d?.supplierName || '—') : (d?.customerName || '—')
}

async function load() {
  const id = route.params.id
  loading.value = true
  try {
    const [h, its] = await Promise.all([
      request.get<any, any>('/finance/receipt/' + id),
      request.get<any, any>('/finance/receipt/' + id + '/items').catch(() => [])
    ])
    detail.value = h || {}
    items.value = Array.isArray(its) ? its : ((its as any)?.records || [])
  } catch { detail.value = {}; items.value = [] } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原「返回」按钮已删）；本页只读无操作 -->
  <PageShell :loading="loading" back-fallback="/finance/receipt">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">收款单详情 — {{ detail.code || '' }}</span>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ DocStatusLabel[String(detail.status)] || detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="主体类型">{{ SubjectTypeLabel[detail.subjectType || ''] || '客户' }}</el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ subjectLabel(detail) }}</el-descriptions-item>
        <el-descriptions-item label="账户">{{ detail.accountName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.receiptDate }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmt(detail.amount) }}</el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">核销明细</span></template>
      <el-table :data="items" border>
        <el-table-column prop="receivableBillNo" label="应收单据" min-width="150"/>
        <el-table-column prop="thisAmount" label="核销金额" width="130" align="right"><template #default="{row}">{{ fmt(row.thisAmount) }}</template></el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>

<style scoped>/* 根容器已统一到全局骨架（PageShell）；原 .p 局部样式已删除 */</style>
