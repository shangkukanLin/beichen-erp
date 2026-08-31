<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="stockQuery" class="query-form">
        <el-form-item label="仓库">
          <!-- 仓库支持多选：同时看多个仓库的成品库存；下拉已过滤掉辅料仓（物料仓） -->
          <RemoteSelect v-model="stockQuery.warehouseIds" multiple collapse-tags collapse-tags-tooltip :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:260px" />
        </el-form-item>
        <el-form-item label="品牌">
          <RemoteSelect v-model="stockQuery.brandId" :fetch="fetchBrands" label-key="brandName" placeholder="全部" style="width:160px" />
        </el-form-item>
        <el-form-item label="产品">
          <el-input v-model="stockQuery.productName" placeholder="产品名称或 SKU" clearable @keyup.enter="stockQuery_" />
        </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="stockQuery_">查询</el-button>
          <el-button :icon="'Refresh'" @click="stockReset">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="stockLoading" :data="stockData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="仓库" min-width="140">
          <template #default="{ row }">{{ row.warehouseName || warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="sku" label="SKU" width="140" />
        <el-table-column prop="brandName" label="品牌" min-width="110">
          <template #default="{ row }">{{ row.brandName || '—' }}</template>
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
        <el-table-column label="待分类" width="90" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.qtyPending > 0" type="primary" size="small" title="压在售后仓、等待退货整理的库存">{{ fmt(row.qtyPending) }}</el-tag>
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
import { reactive, ref, onMounted } from 'vue'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

// 仓库下拉选项（Odoo 实时查库，组件本地保存）
const warehouseOptions = ref<any[]>([])
// 成品库存查询：只列有成品库存的仓库（成品仓/售后仓/不良仓），过滤掉辅料仓（物料仓）
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
    .then((res: any) => {
      res.records = (res.records || []).filter((w: any) => w.warehouseType !== WarehouseType.AUXILIARY)
      return res
    })
// 品牌下拉
const fetchBrands = (kw: string) => request.get('/brand/page', { params: { pageSize: 200, brandName: kw } })

function warehouseName(id?: number) {
  const w = warehouseOptions.value.find(x => x.id === id)
  return w ? w.warehouseName : ''
}
function fmt(v?: number) { return v == null ? '0' : parseFloat(Number(v).toFixed(4)).toString() }
function totalQty(row: any) {
  return (Number(row.qtyA) || 0) + (Number(row.qtyB) || 0) + (Number(row.qtyC) || 0)
    + (Number(row.qtyDefect) || 0) + (Number(row.qtyPending) || 0)
}

const stockQuery = reactive({ warehouseIds: [] as number[], brandId: undefined as number | undefined, productName: '' })
const stockPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const stockLoading = ref(false)
const stockData = ref<any[]>([])

async function loadStock() {
  stockLoading.value = true
  try {
    const params: any = { pageNum: stockPage.pageNum, pageSize: stockPage.pageSize, stockType: 'PRODUCT' }
    // 多选用逗号分隔：axios 默认会把数组序列化成 warehouseIds[]=1，后端 @RequestParam List 收不到
    if (stockQuery.warehouseIds?.length) params.warehouseIds = stockQuery.warehouseIds.join(',')
    if (stockQuery.brandId) params.brandId = stockQuery.brandId
    if (stockQuery.productName) params.productName = stockQuery.productName
    const res = await request.get<any, any>('/warehouse/stock/product-stock/page', { params })
    stockData.value = res?.records || []
    stockPage.total = res?.total || 0
  } catch { stockData.value = []; stockPage.total = 0 } finally { stockLoading.value = false }
}
function stockQuery_() { stockPage.pageNum = 1; loadStock() }
function stockReset() { stockQuery.warehouseIds = []; stockQuery.brandId = undefined; stockQuery.productName = ''; stockPage.pageNum = 1; loadStock() }

onMounted(() => { loadStock() })

</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding: 16px; }
.query-form { align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
