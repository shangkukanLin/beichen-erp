<script setup lang="ts">
defineOptions({ name: 'OutsourceOrderIndex' })

import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const activeTab = ref('进行中')
const query = reactive({ code: '', factoryId: undefined as any })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const tableLoading = ref(false)
const factoryOptions = ref<any[]>([])

async function loadOptions() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType:'factory', pageSize:200 } }); factoryOptions.value = r?.records || [] } catch (e: any) { console.warn('加载工厂选项失败', e?.message || e) }
}

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (activeTab.value === '进行中') { p.status = '待确认,生产中' }
    else { p.status = activeTab.value }
    if (query.code) p.code = query.code
    if (query.factoryId) p.factoryId = query.factoryId
    const r = await request.get<any, any>('/outsource/order/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { tableLoading.value = false }
}
function onTabChange() { pagination.pageNum = 1; loadData() }
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.factoryId = undefined; loadData() }

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确定取消该加工单吗？', '提示', { type: 'warning' }); await request.put(`/outsource/order/${row.id}/cancel`); ElMessage.success('已取消'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

function refresh() { loadOptions(); loadData() }
onMounted(refresh)
onActivated(refresh)
</script>

<template>
  <div class="order-page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="加工单号" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="加工厂"><el-select v-model="query.factoryId" placeholder="全部" clearable filterable style="width:180px"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button><el-button type="success" @click="router.push('/outsource/order/add')">新增加工单</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="进行中" name="进行中" />
        <el-tab-pane label="已完成" name="已完成" />
        <el-tab-pane label="已取消" name="已取消" />
      </el-tabs>

      <el-table :data="tableData" border stripe v-loading="tableLoading" style="width:100%">
        <el-table-column prop="code" label="单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="factoryName" label="加工厂" min-width="120" show-overflow-tooltip />
        <el-table-column label="产品" min-width="200" show-overflow-tooltip>
          <template #default="{row}">{{ row.productNames || (row.productCount || 0) + '项' }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="110" align="right">
          <template #default="{row}">{{ row.totalAmount ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="planStartDate" label="计划开始" width="120" />
        <el-table-column prop="planEndDate" label="计划完成" width="120">
          <template #default="{row}">
            <span :style="{ color: row.planEndDate && new Date(row.planEndDate) < new Date() && row.status !== '已完成' && row.status !== '已取消' ? 'red' : '' }">{{ row.planEndDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{row}">
            <el-tag :type="row.status==='待确认'?'info':row.status==='生产中'?'primary':row.status==='已完成'?'success':'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link @click="router.push(`/outsource/order/detail/${row.id}`)">详情</el-button>
            <el-button type="danger" link v-if="row.status!=='已取消'" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>
  </div>
</template>

<style scoped>
.order-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
</style>
