<script setup lang="ts">
defineOptions({ name: 'OutsourceDelivery' })

import { reactive, ref, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const query = reactive({ code: '', factoryId: undefined as any })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const tableLoading = ref(false)
const factoryOptions = ref<any[]>([])

async function loadOptions() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } }); factoryOptions.value = r?.records || [] } catch (e: any) { console.warn('加载工厂失败', e?.message || e) }
}

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) p.code = query.code
    if (query.factoryId) p.factoryId = query.factoryId
    const r = await request.get<any, any>('/outsource/delivery/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { tableLoading.value = false }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.factoryId = undefined; loadData() }

const deliveryTypeMap: Record<string, string> = { 'IN': '入库', 'OUT': '出库' }
const statusTagMap: Record<string, string> = { '待确认': 'info', '已确认': 'success', '已取消': 'danger' }

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确定取消该收发单吗？', '提示', { type: 'warning' }); await request.put(`/outsource/delivery/${row.id}/cancel`); ElMessage.success('已取消'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确定删除该收发单吗？', '提示', { type: 'warning' }); await request.delete(`/outsource/delivery/${row.id}/attach`); ElMessage.success('已删除'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

function refresh() { loadOptions(); loadData() }
onMounted(refresh)
onActivated(refresh)
</script>

<template>
  <div class="delivery-page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="收发单号" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="加工厂"><el-select v-model="query.factoryId" placeholder="全部" clearable filterable style="width:180px"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" border stripe v-loading="tableLoading" style="width:100%">
        <el-table-column prop="code" label="单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{row}">{{ deliveryTypeMap[row.deliveryType] || row.deliveryType }}</template>
        </el-table-column>
        <el-table-column prop="factoryName" label="加工厂" min-width="120" show-overflow-tooltip />
        <el-table-column prop="deliveryDate" label="收发日期" width="110" />
        <el-table-column prop="logisticsCompany" label="物流公司" width="110" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag :type="statusTagMap[row.status] || 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link @click="ElMessage.info('详情开发中')">详情</el-button>
            <el-button type="danger" link v-if="row.status !== '已取消'" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>
  </div>
</template>

<style scoped>
.delivery-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
</style>
