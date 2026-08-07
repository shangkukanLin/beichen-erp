<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="hd">
          <span>收费售后（退回不良品）</span>
          <el-button type="primary" :icon="Plus" @click="goAdd">新增退回</el-button>
        </div>
      </template>
      <el-form :inline="true" class="filters">
        <el-form-item label="产品">
          <el-input v-model="query.productName" placeholder="模糊搜索" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="load(1)">查询</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="rows" border stripe size="small" v-loading="loading">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="产品" prop="productName" min-width="180" />
        <el-table-column label="退回数量" prop="quantity" width="120" align="right" />
        <el-table-column label="退回仓库" prop="warehouseName" min-width="150" />
        <el-table-column label="退回日期" prop="deliveryDate" width="130" />
        <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
        :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end" @current-change="load" @size-change="load(1)" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const warehouses = ref<Record<number, string>>({})

const query = reactive({ pageNum: 1, pageSize: 10, productName: '' })

async function loadWarehouses() {
  const res = await request.get('/inventory/warehouse/page', { params: { pageNum: 1, pageSize: 1000 } })
  const map: Record<number, string> = {}
  ;(res.records || []).forEach((w: any) => { map[w.id] = w.name })
  warehouses.value = map
}

async function load(page = query.pageNum) {
  loading.value = true
  try {
    const res = await request.get('/outsource/after-sale/page', {
      params: { pageNum: page, pageSize: query.pageSize, productName: query.productName || undefined },
    })
    rows.value = (res.records || []).map((r: any) => ({
      ...r,
      warehouseName: r.warehouseId ? warehouses.value[r.warehouseId] || '' : '',
    }))
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function goAdd() {
  router.push('/outsource/after-sale/add')
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除「${row.productName}」的退回记录？将扣回已入库不良品。`, '提示', { type: 'warning' })
  await request.delete(`/outsource/after-sale/${row.id}`)
  ElMessage.success('已删除')
  load(1)
}

onMounted(async () => {
  await loadWarehouses()
  await load(1)
})
</script>

<style scoped>
.hd { display: flex; justify-content: space-between; align-items: center; }
.filters { margin-bottom: 12px; }
</style>
