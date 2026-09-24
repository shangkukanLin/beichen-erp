<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { StockChangeTypeLabel, stockChangeTypeTag } from '@/api/enums'
import PageShell from '@/components/PageShell.vue'

const route = useRoute(); const router = useRouter()
const warehouseId = Number(route.params.wid)
const materialId = Number(route.params.mid)
const records = ref<any[]>([])
const loading = ref(false)
const pagination = ref({ pageNum: 1, pageSize: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/warehouse/stock/material-history', {
      params: { warehouseId: warehouseId || undefined, materialId: materialId || undefined, pageNum: pagination.value.pageNum, pageSize: pagination.value.pageSize }
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
      other_io: '/inventory/other-io/detail/',
      outsource_other_io: '/outsource/other-io/detail/'
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
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回去「该物料的库存分布详情」（本页由那里进入）；只读 -->
  <PageShell :back-fallback="`/outsource/material-stock/detail/${route.params.wid}`">
    <el-card shadow="never">
      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column label="时间" width="110"><template #default="{row}">{{ $fmtDate(row.createTime) }}</template></el-table-column>
        <el-table-column label="关联单号" width="180">
          <template #default="{row}"><el-button v-if="row.relatedOrderCode" type="primary" link @click="handleCodeClick(row.relatedOrderCode)">{{ row.relatedOrderCode }}</el-button><span v-else style="color:var(--app-text-placeholder)">—</span></template>
        </el-table-column>
        <el-table-column label="类型" width="190" align="center">
          <template #default="{row}"><el-tag :type="stockChangeTypeTag(row.changeType)" size="small">{{ StockChangeTypeLabel[row.changeType] || row.changeType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="变更前" width="100" align="right">
          <template #default="{row}"><span style="font-weight:500">{{ row.beforeQuantity }}</span></template>
        </el-table-column>
        <el-table-column label="变更数量" width="100" align="right">
          <template #default="{row}"><span :style="{color: Number(row.changeQuantity)<0?'var(--app-color-danger)':'var(--app-color-success)',fontWeight:600}">{{ Number(row.changeQuantity)>0?'+':'' }}{{ row.changeQuantity }}</span></template>
        </el-table-column>
        <el-table-column label="变更后" width="100" align="right">
          <template #default="{row}"><span :style="{color: Number(row.afterQuantity)<0?'var(--app-color-danger)':'',fontWeight:600}">{{ row.afterQuantity }}</span></template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSizeChange" @current-change="handlePageChange" />
      </div>
    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .history-page 已删除（.pagination 保留） */

/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
</style>
