<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/enums'

const route = useRoute()
const router = useRouter()
const id = route.params.id as string
const detail = ref<any>({})
const loading = ref(false)

async function loadData() {
  loading.value = true
  try { detail.value = (await request.get<any, any>(`/outsource/material-return/${id}`)) || {} } finally { loading.value = false }
}

async function handleAudit() {
  try { await ElMessageBox.confirm('确认审核该退货单？', '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/audit`); ElMessage.success('审核成功'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit() {
  try { await ElMessageBox.confirm('确认反审核？将物料回源仓并冲销应付', '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

onMounted(loadData)
</script>

<template>
  <div class="app-container" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">委外物料退货详情</span>
          <div>
            <el-button type="success" v-if="detail.status===DocStatus.DRAFT" @click="handleAudit">审核</el-button>
            <el-button type="warning" v-if="detail.status===DocStatus.AUDITED" @click="handleUnAudit">反审核</el-button>
            <el-button type="danger" v-if="detail.status===DocStatus.DRAFT" @click="handleCancel">作废</el-button>
            <el-button @click="router.back()">返回</el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="退回对象">{{ detail.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出库源仓">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ $fmtDate(detail.returnDate) }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">退货物料明细</span></template>
      <el-table :data="detail.items || []" border size="small">
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="bomTypeName" label="BOM类型" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right" />
        <el-table-column prop="amount" label="金额" width="110" align="right" />
      </el-table>
    </el-card>
  </div>
</template>
