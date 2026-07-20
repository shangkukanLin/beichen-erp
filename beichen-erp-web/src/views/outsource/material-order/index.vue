<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const query = reactive({ code: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const loading = ref(false)
const activeTab = ref('进行中')

// 状态 Tab 定义 - 进行中：待确认 + 收货中
const STATUS_TABS = [
  { key: '进行中', label: '进行中', type: 'warning', statuses: ['待确认', '收货中'] },
  { key: '已完成', label: '已完成', type: 'success', statuses: ['已完成'] },
  { key: '已取消', label: '已取消', type: 'danger', statuses: ['已取消'] }
]
const tabPanes = computed(() => STATUS_TABS.map(t => ({ tab: t.key, name: t.key })))

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.code) p.code = query.code
    const tab = STATUS_TABS.find(t => t.key === activeTab.value)
    if (tab && tab.statuses.length === 1) {
      p.status = tab.statuses[0]
    } else if (tab && tab.statuses.length > 1) {
      p.statuses = tab.statuses.join(',')
    }
    const r = await request.get<any, any>('/outsource/material-order/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { loading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; loadData() }
function onTabChange() { pagination.pageNum = 1; loadData() }

async function handleConfirm(row: any) {
  try { await ElMessageBox.confirm('确认后将进入收货中状态', '确认订单', { type: 'warning' }); await request.put(`/outsource/material-order/${row.id}/confirm`); ElMessage.success('已确认'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确定取消该订单？', '取消订单', { type: 'warning' }); await request.put(`/outsource/material-order/${row.id}/cancel`); ElMessage.success('已取消'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}
onMounted(() => { loadData() })
onActivated(() => {
  if ((window as any).__materialOrderNeedRefresh) { (window as any).__materialOrderNeedRefresh = false; loadData() }
})
</script>

<template>
  <div class="mo-page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="订单号" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button><el-button type="success" @click="router.push('/outsource/material-order/add')">新增</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-tabs v-model="activeTab" class="status-tabs" @tab-change="onTabChange">
      <el-tab-pane v-for="t in STATUS_TABS" :key="t.key" :name="t.key">
        <template #label>
          <el-badge :value="t.key === activeTab ? pagination.total : 0" :hidden="t.key !== activeTab" :max="9999" type="primary">
            <span :class="['tab-label', `tab-${t.type}`]">{{ t.label }}</span>
          </el-badge>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" border stripe v-loading="loading" row-key="id" size="small">
        <el-table-column prop="code" label="订单号" width="170" />
        <el-table-column label="供应商" width="170" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName }}</el-button></template>
        </el-table-column>
        <el-table-column label="下单日期" width="90" align="center"><template #default="{row}">{{ $fmtDate(row.createTime) || '-' }}</template></el-table-column>
        <el-table-column label="物料名称" min-width="50" show-overflow-tooltip>
          <template #default="{row}">
            <el-tooltip placement="top" :show-after="300" raw-content>
              <template #content>
                <div v-for="(it,i) in (row.items||[])" :key="i" style="line-height:1.6">{{ it.materialType || '' }} {{ it.materialName }} ×{{ it.orderQuantity }}{{it.unit}}（已收{{it.receivedQuantity||0}}）</div>
              </template>
              <span>{{ (row.items || []).map((it: any) => it.materialName).filter(Boolean).join('、') || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="下单总数" width="75" align="center">
          <template #default="{row}">{{ (row.items || []).reduce((s: number, it: any) => s + (it.orderQuantity || 0), 0) }}</template>
        </el-table-column>
        <el-table-column label="已收" width="72" align="center">
          <template #default="{row}"><span :style="{color: (row.items || []).reduce((s: number, it: any) => s + (it.receivedQuantity || 0), 0)>0?'#67c23a':''}">{{ (row.items || []).reduce((s: number, it: any) => s + (it.receivedQuantity || 0), 0) }}</span></template>
        </el-table-column>
        <el-table-column label="最近交货" width="85" align="center"><template #default="{row}">{{ $fmtDate(row.lastDeliveryTime) || '-' }}</template></el-table-column>
        <el-table-column label="交期" width="90" align="center"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column label="状态" width="70" align="center"><template #default="{row}"><el-tag :type="row.status==='待确认'?'info':row.status==='收货中'?'warning':row.status==='已完成'?'success':'danger'" size="small">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="145" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link size="small" @click="router.push(`/outsource/material-order/detail/${row.id}`)" style="padding:0 4px">详情</el-button>
            <el-button v-if="row.status==='待确认'" type="success" link size="small" @click="handleConfirm(row)" style="padding:0 4px">确认</el-button>
            <el-button v-if="row.status!=='已完成' && row.status!=='已取消'" type="danger" link size="small" @click="handleCancel(row)" style="padding:0 4px">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>
  </div>
</template>

<style scoped>
.mo-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
.status-tabs { background: #fff; padding: 0 16px; border-radius: 4px; }
.status-tabs :deep(.el-tabs__header) { margin: 0; }
.tab-label { font-weight: 500; }
.tab-label.tab-info { color: #909399; }
.tab-label.tab-warning { color: #e6a23c; }
.tab-label.tab-success { color: #67c23a; }
.tab-label.tab-danger { color: #f56c6c; }
</style>
