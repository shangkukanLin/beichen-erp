<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/enums'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/return-order/page', { params: { pageNum: pagination.pageNum, pageSize: pagination.pageSize } })
    list.value = r?.records || []; pagination.total = r?.total || 0
  } finally { loading.value = false }
}

async function handleAudit(row: any) {
  try { await ElMessageBox.confirm('确认审核该退货单？审核后物料入工厂仓、成品出库并冲减应付', '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/audit`); ElMessage.success('审核成功'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit(row: any) {
  try { await ElMessageBox.confirm('确认反审核？将逆向库存并冲销应付', '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

function handleAdd() { router.push('/outsource/return-order/add') }

onMounted(() => { loadData() })

</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <el-button type="primary" @click="handleAdd">新增委外加工退货</el-button>
    </el-card>
    <el-card shadow="never">
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="code" label="退货单号" width="170" />
        <el-table-column label="加工厂" width="130" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click="router.push(`/supplier/detail/${row.factoryId}`)">{{ row.factoryName }}</el-button></template>
        </el-table-column>
        <el-table-column prop="orderCode" label="关联加工单" width="170" />
        <el-table-column label="退货物料" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemSummary || '-' }}</template>
        </el-table-column>
        <el-table-column label="退货日期" width="110" align="center">
          <template #default="{ row }">{{ $fmtDate(row.returnDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/outsource/return-order/detail/${row.id}`)">详情</el-button>
            <el-button type="success" link v-if="row.status===DocStatus.DRAFT" @click="handleAudit(row)">审核</el-button>
            <el-button type="warning" link v-if="row.status===DocStatus.AUDITED" @click="handleUnAudit(row)">反审核</el-button>
            <el-button type="danger" link v-if="row.status===DocStatus.DRAFT" @click="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="()=>{pagination.pageNum=1;loadData()}" />
      </div>
    </el-card>
  </div>
</template>
