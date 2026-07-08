<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'

const route = useRoute(); const router = useRouter()
const warehouseId = route.params.wid
const materialId = route.params.mid
const records = ref<any[]>([])
const loading = ref(false)
const query = reactive({ startDate: '', endDate: '' })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const p: any = { warehouseId, materialId, pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.startDate) p.startDate = query.startDate
    if (query.endDate) p.endDate = query.endDate
    const r = await request.get<any, any>('/outsource/stock/history', { params: p })
    records.value = r?.records || []; pagination.total = r?.total || 0
  } finally { loading.value = false }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.startDate = ''; query.endDate = ''; pagination.pageNum = 1; loadData() }

onMounted(() => loadData())
</script>

<template>
  <div class="history-page">
    <div class="page-header"><el-button @click="router.back()">返回</el-button><span class="page-title">物料收发历史</span></div>

    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="开始日期"><el-input v-model="query.startDate" type="date" style="width:160px" /></el-form-item>
        <el-form-item label="结束日期"><el-input v-model="query.endDate" type="date" style="width:160px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column prop="deliveryDate" label="日期" width="110" />
        <el-table-column prop="code" label="收发单号" width="170" />
        <el-table-column prop="deliveryType" label="类型" width="70">
          <template #default="{row}"><el-tag :type="row.deliveryType==='发料'?'success':row.deliveryType==='收料'?'warning':'danger'">{{row.deliveryType}}</el-tag></template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialType" label="物料类型" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
        <el-table-column prop="status" label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='已确认'?'success':'info'">{{row.status}}</el-tag></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>
  </div>
</template>

<style scoped>
.history-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
.query-card :deep(.el-card__body) { padding:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
