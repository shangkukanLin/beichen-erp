<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const activeTab = ref('发料')
const query = reactive({ code: '', factoryId: undefined as any })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const tableLoading = ref(false)
const factoryOptions = ref<any[]>([])
const allWarehouses = ref<any[]>([])

async function loadOptions() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType:'factory', pageSize:200 } }); factoryOptions.value = r?.records || [] } catch (e: any) { console.warn('加载工厂选项失败', e?.message || e) }
  try {
    const [r1, r2] = await Promise.all([
      request.get<any,any>('/outsource/warehouse/page',{params:{pageSize:300}}),
      request.get<any,any>('/inventory/warehouse/page',{params:{pageSize:300}})
    ]);
    allWarehouses.value = [...(r1?.records||[]), ...(r2?.records||[])]
  } catch {}
}

function goWhDetail(warehouseId: number) {
  const w = allWarehouses.value.find((x:any)=>x.id===warehouseId)
  if (!w) return
  if (w.factoryId != null) router.push(`/outsource/warehouse/detail/${warehouseId}`)
  else router.push(`/inventory/warehouse/detail/${warehouseId}`)
}

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { deliveryType: activeTab.value, pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) p.code = query.code
    if (query.factoryId) p.factoryId = query.factoryId
    const r = await request.get<any, any>('/outsource/delivery/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { tableLoading.value = false }
}
function onTabChange() { pagination.pageNum = 1; loadData() }
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.factoryId = undefined; loadData() }

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确定取消该收发单吗？取消后库存将自动恢复。', '提示', { type: 'warning' }); await request.put(`/outsource/delivery/${row.id}/cancel`); ElMessage.success('已取消'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

onMounted(() => { loadOptions(); loadData() })
onActivated(() => {
  if ((window as any).__deliveryNeedRefresh) { (window as any).__deliveryNeedRefresh = false; loadData() }
})
onActivated(() => { loadOptions() })
</script>

<template>
  <div class="delivery-page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="收发单号" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="加工厂"><el-select v-model="query.factoryId" placeholder="全部" clearable filterable style="width:180px"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button><el-button type="success" @click="router.push('/outsource/delivery/add')">新增</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="发料" name="发料" /><el-tab-pane label="收料" name="收料" /><el-tab-pane label="退料" name="退料" />
      </el-tabs>

      <el-table :data="tableData" border stripe v-loading="tableLoading" style="width:100%" size="small">
        <el-table-column prop="code" label="单号" width="170" />
        <el-table-column label="发出仓库" width="130" show-overflow-tooltip>
          <template #default="{row}"><span v-if="row.supplierDirect" style="color:#409eff">{{row.supplierName||'供应商直发'}}</span><el-button v-else type="primary" link @click="goWhDetail(row.fromWarehouseId)">{{row.fromWarehouseName||'-'}}</el-button></template>
        </el-table-column>
        <el-table-column label="目标仓库" width="130" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click="goWhDetail(row.toWarehouseId)">{{row.toWarehouseName||'-'}}</el-button></template>
        </el-table-column>
        <el-table-column label="物料" min-width="180" show-overflow-tooltip>
          <template #default="{row}"><span v-if="row.itemSummary">{{row.itemSummary}}</span><span v-else style="color:#c0c4cc">{{row.itemCount||0}}项</span></template>
        </el-table-column>
        <el-table-column label="日期" width="100"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="75"><template #default="{row}"><el-tag :type="row.status==='已确认'?'success':'info'">{{row.status}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link @click="router.push(`/outsource/delivery/detail/${row.id}`)">详情</el-button>
            <el-button type="danger" link v-if="row.status==='已确认'" @click="handleCancel(row)">取消</el-button>
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
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
