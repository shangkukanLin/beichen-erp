<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const factoryOptions = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/return-order/page', { params: { pageNum: pagination.pageNum, pageSize: pagination.pageSize } })
    list.value = r?.records || []; pagination.total = r?.total || 0
  } finally { loading.value = false }
}

async function loadOptions() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } }); factoryOptions.value = r?.records || [] } catch {}
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认取消？将逆向库存', '确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/cancel`); ElMessage.success('已取消'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

function handleAdd() { router.push('/outsource/return-order/add') }

onMounted(() => { loadOptions(); loadData() })
onActivated(() => { if ((window as any).__returnOrderNeedRefresh) { (window as any).__returnOrderNeedRefresh = false; loadData() } })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <el-button type="primary" @click="handleAdd">新增退货</el-button>
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
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag :type="row.status==='已取消'?'danger':'success'" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/outsource/return-order/detail/${row.id}`)">详情</el-button>
            <el-button type="danger" link v-if="row.status!=='已取消'" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="()=>{pagination.pageNum=1;loadData()}" />
      </div>
    </el-card>
  </div>
</template>
