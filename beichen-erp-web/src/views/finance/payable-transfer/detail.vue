<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag, sourceBillTypeLabel } from '@/api/enums'
import { TYPE_MAP, TYPE_TAG } from '@/constants/supplier'
import { PAYABLE_TRANSFER_DIRTY_KEY } from '@/api/enums'
import PageShell from '@/components/PageShell.vue'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const loading = ref(false)
const d = ref<any>({})

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }

async function load() {
  loading.value = true
  try { d.value = await request.get<any, any>(`/finance/payable-transfer/${id}`) || {} }
  catch (e: any) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}

async function onAudit() {
  try { await ElMessageBox.confirm(`确认审核「${d.value.code}」？将生成 ${fmt(d.value.amount)} 元应收。`, '审核确认', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/finance/payable-transfer/${id}/audit`)
    ElMessage.success('审核成功，已生成应收')
    sessionStorage.setItem(PAYABLE_TRANSFER_DIRTY_KEY, '1')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function onUnAudit() {
  try { await ElMessageBox.confirm('确认反审核？将冲销已生成的应收并恢复应付抵扣资格。', '反审核确认', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/finance/payable-transfer/${id}/un-audit`)
    ElMessage.success('已反审核')
    sessionStorage.setItem(PAYABLE_TRANSFER_DIRTY_KEY, '1')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function onCancel() {
  try { await ElMessageBox.confirm(`确认作废「${d.value.code}」？`, '作废确认', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/finance/payable-transfer/${id}/cancel`)
    ElMessage.success('已作废')
    sessionStorage.setItem(PAYABLE_TRANSFER_DIRTY_KEY, '1')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(load)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/finance/payable-transfer">
    <template #actions>
      <el-button v-if="d.status === DocStatus.DRAFT" type="primary" @click="router.push(`/finance/payable-transfer/edit/${id}`)">编辑</el-button>
      <el-button v-if="d.status === DocStatus.DRAFT" type="success" @click="onAudit">审核</el-button>
      <el-button v-if="d.status === DocStatus.AUDITED" type="warning" @click="onUnAudit">反审核</el-button>
      <el-button v-if="d.status === DocStatus.DRAFT" type="danger" @click="onCancel">作废</el-button>
    </template>

    <el-card shadow="never">

      <el-descriptions :column="3" border>
        <el-descriptions-item label="转应收单号">{{ d.code || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="DocStatusTag[d.status] || 'info'" size="small">{{ DocStatusLabel[d.status] || d.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源应付单号">{{ d.payableBillNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="转出日期">{{ d.transferDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ d.supplierName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="主体类型">
          <el-tag v-if="d.supplierType" :type="TYPE_TAG[d.supplierType] || 'info'" size="small">{{ typeLabel(d.supplierType) }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="转出金额"><strong>{{ fmt(d.amount) }}</strong></el-descriptions-item>
        <!-- 制单人（2026-09-23 用户口径：单据详情显示「制单人 + 审核人」） -->
        <el-descriptions-item label="制单人">{{ d.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ d.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ d.auditTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ d.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ d.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .p / .card-header / .title 已删除 */
</style>
