<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/enums'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ code: '', supplierId: undefined as any, status: '' })

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/material-return/page', { params: { pageNum: pagination.pageNum, pageSize: pagination.pageSize, code: query.code || undefined, supplierId: query.supplierId || undefined, status: query.status || undefined } })
    list.value = r?.records || []; pagination.total = r?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.supplierId = undefined; query.status = ''; handleSearch() }

async function handleAudit(row: any) {
  try { await ElMessageBox.confirm('确认审核该退货单？审核后物料出源仓并冲减应付', '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/audit`); ElMessage.success('审核成功'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit(row: any) {
  try { await ElMessageBox.confirm('确认取消审核？将物料回源仓并冲销应付', '确认取消审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/un-audit`); ElMessage.success('已取消审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '取消审核失败') }
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

function handleAdd() { router.push('/outsource/material-return/add') }

onMounted(loadData)
onActivated(() => { loadData() })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap">
        <el-input v-model="query.code" placeholder="退货单号" clearable style="width:200px" @keyup.enter="handleSearch" />
        <el-input v-model="query.supplierId" placeholder="供应商ID" clearable style="width:150px" @keyup.enter="handleSearch" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
          <el-option label="草稿" :value="DocStatus.DRAFT" />
          <el-option label="已审核" :value="DocStatus.AUDITED" />
          <el-option label="已作废" :value="DocStatus.CANCELLED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="success" @click="handleAdd">新增物料退货</el-button>
      </div>
    </el-card>
    <el-card shadow="never">
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="code" label="退货单号" width="180" />
        <el-table-column label="供应商" width="130" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName }}</el-button></template>
        </el-table-column>
        <el-table-column prop="warehouseName" label="出库源仓" width="130" show-overflow-tooltip />
        <el-table-column label="退货物料" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemSummary || '-' }}</template>
        </el-table-column>
        <el-table-column label="退货金额" width="110" align="right">
          <template #default="{ row }">{{ row.totalAmount != null ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column label="退货日期" width="110" align="center">
          <template #default="{ row }">{{ $fmtDate(row.returnDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/outsource/material-return/detail/${row.id}`)">详情</el-button>
            <el-button type="success" link v-if="row.status===DocStatus.DRAFT" @click="handleAudit(row)">审核</el-button>
            <el-button type="warning" link v-if="row.status===DocStatus.AUDITED" @click="handleUnAudit(row)">取消审核</el-button>
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
