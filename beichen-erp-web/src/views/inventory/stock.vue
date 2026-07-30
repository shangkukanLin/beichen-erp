<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="stockQuery" class="query-form">
        <el-form-item label="仓库">
          <el-select v-model="stockQuery.warehouseId" placeholder="全部" clearable filterable style="width:160px">
            <el-option v-for="w in stockWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品">
          <el-input v-model="stockQuery.productName" placeholder="产品名称" clearable @keyup.enter="stockQuery_" />
        </el-form-item>
        <el-form-item label="品质">
          <el-select v-model="stockQuery.qualityType" placeholder="全部" clearable style="width:120px">
            <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="stockQuery_">查询</el-button>
          <el-button :icon="'Refresh'" @click="stockReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="stockLoading" :data="stockData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="仓库" min-width="140">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="180" />
        <el-table-column label="品质" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="qualityTag(row.qualityType)" size="small">{{ qualityLabel(row.qualityType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="库存数量" min-width="140" align="right">
          <template #default="{ row }">{{ fmt(row.quantity) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="stockPage.pageNum" v-model:page-size="stockPage.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="stockPage.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="(v:number)=>{stockPage.pageSize=v;loadStock()}" @current-change="loadStock" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import request from '@/utils/request'
import { getQualityTypes, type QualityOption } from '@/api/product'

const warehouses = ref<{ id: number; warehouseName: string; warehouseType?: string }[]>([])
const stockWarehouses = computed(() => warehouses.value.filter(w => w.warehouseType !== '辅料仓'))

function warehouseName(id?: number) {
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : ''
}
function fmt(v?: number) { return v == null ? '0' : parseFloat(Number(v).toFixed(4)).toString() }

const qualityOptions = ref<QualityOption[]>([])
const stockQuery = reactive({ warehouseId: undefined as number | undefined, productName: '', qualityType: '' })
const stockPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const stockLoading = ref(false)
const stockData = ref<any[]>([])

async function loadStock() {
  stockLoading.value = true
  try {
    const params: any = { pageNum: stockPage.pageNum, pageSize: stockPage.pageSize }
    if (stockQuery.warehouseId) params.warehouseId = stockQuery.warehouseId
    if (stockQuery.productName) params.productName = stockQuery.productName
    if (stockQuery.qualityType) params.qualityType = stockQuery.qualityType
    const res = await request.get<any, any>('/inventory/stock/page', { params })
    const fclWhIds = new Set(stockWarehouses.value.map(w => w.id))
    const allRecords = res?.records || []
    stockData.value = allRecords.filter((r: any) => fclWhIds.has(r.warehouseId))
    stockPage.total = stockData.value.length
  } catch { stockData.value = [] } finally { stockLoading.value = false }
}
function stockQuery_() { stockPage.pageNum = 1; loadStock() }
function stockReset() { stockQuery.warehouseId = undefined; stockQuery.productName = ''; stockQuery.qualityType = ''; stockPage.pageNum = 1; loadStock() }

async function loadWarehouses() {
  try {
    const res = await request.get('/inventory/warehouse/page', { params: { pageSize: 200 } })
    warehouses.value = res?.records || []
  } catch { warehouses.value = [] }
}

function qualityTag(type?: string) {
  const m: Record<string, string> = { A: 'success', B: 'warning', C: 'info', DEFECT: 'danger' }
  return m[type || ''] || ''
}
function qualityLabel(type?: string) {
  const m: Record<string, string> = { A: 'A规', B: 'B规', C: 'C规', DEFECT: '不良' }
  return m[type || ''] || type || ''
}
async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }

onMounted(() => { loadWarehouses(); loadQualityTypes(); loadStock() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding: 16px; }
.query-form { align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
