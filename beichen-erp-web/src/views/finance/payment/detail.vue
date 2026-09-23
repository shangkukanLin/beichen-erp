<script setup lang="ts">
// 付款单详情（2026-09-23 用户要求：原「付款单详情」50% 抽屉改为独立页面）
// —— 按 id 回源单头 + 核销明细；凭证上传能力一并从抽屉迁到这里（否则该功能会随抽屉消失）。
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DocStatus, DocStatusLabel } from '@/api/enums'
import { TYPE_MAP, TYPE_TAG } from '@/constants/supplier'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const attachSaving = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | undefined {
  if (s === DocStatus.DRAFT) return 'warning'
  if (s === DocStatus.AUDITED) return 'success'
  if (s === DocStatus.CANCELLED) return 'info'
  return undefined
}
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }
function openAttach(url: string) { window.open(url + '?inline=true') }

async function load() {
  const id = route.params.id
  loading.value = true
  try {
    const [h, its] = await Promise.all([
      request.get<any, any>('/finance/payment/' + id),
      request.get<any, any>('/finance/payment/' + id + '/items').catch(() => [])
    ])
    detail.value = h || {}
    items.value = Array.isArray(its) ? its : ((its as any)?.records || [])
  } catch { detail.value = {}; items.value = [] } finally { loading.value = false }
}
async function handleUploadAttach(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  attachSaving.value = true
  try {
    const fd = new FormData(); fd.append('file', file)
    const url = await request.post<any, string>('/dev/file/upload', fd)
    await request.put(`/finance/payment/${detail.value.id}/attach`, { attachUrl: url })
    detail.value.attachUrl = url as unknown as string
    ElMessage.success('凭证已上传')
  } catch (e: any) { ElMessage.error(e?.message || '上传失败') } finally { attachSaving.value = false }
}
onMounted(load)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原「返回」按钮已删）；上传凭证按钮留在卡内 -->
  <PageShell :loading="loading" back-fallback="/finance/payment">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">付款单详情 — {{ detail.code || '' }}</span>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ DocStatusLabel[detail.status ?? 0] || detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="主体类型">
          <el-tag v-if="detail.supplierType" :type="TYPE_TAG[detail.supplierType] || 'info'" size="small">{{ typeLabel(detail.supplierType) }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="账户">{{ detail.accountName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.paymentDate }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmt(detail.amount) }}</el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="付款凭证" :span="2">
          <div style="display:flex;align-items:center;gap:12px">
            <template v-if="detail.attachUrl">
              <el-image :src="detail.attachUrl" :preview-src-list="[detail.attachUrl]" fit="contain" style="width:80px;height:80px;border:1px solid #dcdfe6;border-radius:4px" preview-teleported />
              <el-link type="primary" @click="openAttach(detail.attachUrl)">查看原图</el-link>
            </template>
            <span v-else style="color:var(--app-text-secondary)">未上传</span>
            <label class="upload-btn">
              <input type="file" accept="image/*" style="display:none" @change="handleUploadAttach" />
              <el-button size="small" :loading="attachSaving">{{ detail.attachUrl ? '重新上传' : '上传凭证' }}</el-button>
            </label>
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">核销明细</span></template>
      <el-table :data="items" border>
        <el-table-column prop="payableBillNo" label="应付单据" min-width="150"/>
        <el-table-column prop="thisAmount" label="核销金额" width="130" align="right"><template #default="{row}">{{ fmt(row.thisAmount) }}</template></el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>

<style scoped>/* 根容器已统一到全局骨架（PageShell）；原 .p 局部样式已删除（.upload-btn 仍在用，保留） */.upload-btn{display:inline-flex}</style>
