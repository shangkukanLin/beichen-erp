<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="stockQuery" class="query-form">
        <el-form-item label="仓库">
          <RemoteSelect v-model="stockQuery.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" :option-disabled="(row:any)=>row.warehouseType === WarehouseType.AUXILIARY" placeholder="全部" style="width:160px" />
        </el-form-item>
        <el-form-item label="产品">
          <el-input v-model="stockQuery.productName" placeholder="产品名称" clearable @keyup.enter="stockQuery_" />
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
          <template #default="{ row }">{{ row.warehouseName || warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="160" />
        <el-table-column label="A规" width="90" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyA > 0" type="success" size="small">{{ fmt(row.qtyA) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="B规" width="90" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyB > 0" type="warning" size="small">{{ fmt(row.qtyB) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="C规" width="90" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyC > 0" type="info" size="small">{{ fmt(row.qtyC) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="不良" width="90" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyDefect > 0" type="danger" size="small">{{ fmt(row.qtyDefect) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="总库存" width="100" align="right">
          <template #default="{ row }">{{ fmt(totalQty(row)) }}</template>
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
import { WarehouseCategory, WarehouseType } from '@/api/enums'
import { reactive, ref, onMounted, onActivated } from 'vue'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

// 仓库下拉选项（Odoo 实时查库，组件本地保存）
const warehouseOptions = ref<any[]>([])
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })

function warehouseName(id?: number) {
  const w = warehouseOptions.value.find(x => x.id === id)
  return w ? w.warehouseName : ''
}
function fmt(v?: number) { return v == null ? '0' : parseFloat(Number(v).toFixed(4)).toString() }
function totalQty(row: any) {
  return (Number(row.qtyA) || 0) + (Number(row.qtyB) || 0) + (Number(row.qtyC) || 0) + (Number(row.qtyDefect) || 0)
}

const stockQuery = reactive({ warehouseId: undefined as number | undefined, productName: '' })
const stockPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const stockLoading = ref(false)
const stockData = ref<any[]>([])

async function loadStock() {
  stockLoading.value = true
  try {
    const params: any = { pageNum: stockPage.pageNum, pageSize: stockPage.pageSize, stockType: 'PRODUCT' }
    if (stockQuery.warehouseId) params.warehouseId = stockQuery.warehouseId
    if (stockQuery.productName) params.productName = stockQuery.productName
    const res = await request.get<any, any>('/warehouse/stock/page', { params })
    stockData.value = res?.records || []
    stockPage.total = res?.total || 0
  } catch { stockData.value = []; stockPage.total = 0 } finally { stockLoading.value = false }
}
function stockQuery_() { stockPage.pageNum = 1; loadStock() }
function stockReset() { stockQuery.warehouseId = undefined; stockQuery.productName = ''; stockPage.pageNum = 1; loadStock() }

onMounted(() => { loadStock() })
onActivated(() => { loadStock() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding: 16px; }
.query-form { align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
