<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const route = useRoute(); const router = useRouter()
const warehouseId = route.params.wid
const materialId = route.params.mid
const records = ref<any[]>([])
const loading = ref(false)
const pagination = ref({ pageNum: 1, pageSize: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/inventory/stock/log', {
      params: { warehouseId, materialId, pageNum: pagination.value.pageNum, pageSize: pagination.value.pageSize }
    })
    records.value = r?.records || []
    pagination.value.total = r?.total || 0
  } finally { loading.value = false }
}

async function handleCodeClick(code: string) {
  if (!code) return
  try {
    const r = await request.get<any, any>('/common/resolve-code', { params: { code } })
    if (!r?.type) { ElMessage.info('未找到关联单据'); return }
    const map: Record<string, string> = {
      order: '/outsource/order/detail/',
      material_order: '/outsource/material-order/detail/',
      delivery: '/outsource/delivery/detail/',
      other_io: '/inventory/other-io/add?id=',
      outsource_other_io: '/outsource/other-io/add?id='
    }
    const path = map[r.type]
    if (path) router.push(path + r.id)
    else ElMessage.info('不支持的关联单据类型')
  } catch { ElMessage.error('查询失败') }
}

function handlePageChange() { loadData() }
function handleSizeChange() { pagination.value.pageNum = 1; loadData() }

onMounted(() => loadData())
</script>

<template>
  <div class="history-page">
    <div class="page-header"><span class="page-title">物料库存流水详细</span></div>

    <el-card shadow="never">
      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column label="时间" width="110"><template #default="{row}">{{ $fmtDate(row.createTime) }}</template></el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{row}"><el-tag :type="row.changeType?.includes('出库')||row.changeType?.includes('退')?'danger':row.changeType?.includes('入库')||row.changeType?.includes('发')?'success':'info'" size="small">{{ row.changeType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="变更前" width="100" align="right">
          <template #default="{row}"><span style="font-weight:500">{{ row.beforeQuantity }}</span></template>
        </el-table-column>
        <el-table-column label="变更数量" width="100" align="right">
          <template #default="{row}"><span :style="{color: Number(row.changeQuantity)<0?'#f56c6c':'#67c23a',fontWeight:600}">{{ Number(row.changeQuantity)>0?'+':'' }}{{ row.changeQuantity }}</span></template>
        </el-table-column>
        <el-table-column label="变更后" width="100" align="right">
          <template #default="{row}"><span :style="{color: Number(row.afterQuantity)<0?'#f56c6c':'',fontWeight:600}">{{ row.afterQuantity }}</span></template>
        </el-table-column>
        <el-table-column label="关联单号" width="160" show-overflow-tooltip>
          <template #default="{row}"><el-button v-if="row.relatedBillNo" type="primary" link @click="handleCodeClick(row.relatedBillNo)">{{ row.relatedBillNo }}</el-button><span v-else style="color:#c0c4cc">—</span></template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="handlePageChange" @size-change="handleSizeChange" /></div>
    </el-card>
  </div>
</template>

<style scoped>
.history-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
