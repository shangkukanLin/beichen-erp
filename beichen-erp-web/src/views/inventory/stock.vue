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
          <el-button :icon="'Download'" @click="exportStock">导出 Excel</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="stockLoading" :data="stockData" border stripe>
        <!-- 仓库可点：按 factoryId 分流（有工厂=委外仓，无=自有成品仓） -->
        <el-table-column label="仓库" min-width="140">
          <template #default="{ row }">
            <el-link v-if="row.warehouseId" type="primary" underline="never" @click="goWarehouse(row)">
              {{ row.warehouseName || warehouseName(row.warehouseId) }}
            </el-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <!-- SKU 可点：进该产品在各仓库的库存分布详情 -->
        <el-table-column label="SKU" width="140">
          <template #default="{ row }">
            <el-link v-if="row.productId" type="primary" underline="never" @click="goProduct(row)">{{ row.sku }}</el-link>
            <span v-else>{{ row.sku }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="160" />
        <el-table-column prop="brandName" label="品牌" min-width="110">
          <template #default="{ row }">{{ row.brandName || '—' }}</template>
        </el-table-column>
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
import { localDate } from '@/utils/date'
import { WarehouseCategory, WarehouseType } from '@/api/enums'
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import * as XLSX from 'xlsx'

const router = useRouter()

/**
 * 导出 Excel（**全量**）：按当前筛选条件重新请求全部匹配数据（pageNum=1、pageSize=9999），
 * 不受列表分页限制；列与页面表格一致，数量按数值写入（整数不带小数）。
 * 请求失败时退回当前页已加载数据，保证导出始终可用。
 */
async function exportStock() {
  let data: any[] = stockData.value || []
  try {
    const params: any = { pageNum: 1, pageSize: 9999, stockType: 'PRODUCT' }
    // 多选用逗号分隔：与列表查询一致（axios 默认序列化成 warehouseIds[]=1，后端 @RequestParam List 收不到）
    if (stockQuery.warehouseIds?.length) params.warehouseIds = stockQuery.warehouseIds.join(',')
    if (stockQuery.brandId) params.brandId = stockQuery.brandId
    if (stockQuery.productName) params.productName = stockQuery.productName
    const res = await request.get<any, any>('/warehouse/stock/product-stock/page', { params })
    if (Array.isArray(res?.records)) data = res.records
  } catch { /* 拉取失败：退回当前页数据 */ }
  const cols = ['仓库', 'SKU', '产品名称', '品牌', 'A规', 'B规', 'C规', '不良', '待分类', '总库存']
  const aoa: (string | number)[][] = [
    [`成品库存分布（导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 行）`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      r.warehouseName || warehouseName(r.warehouseId) || '',
      r.sku || '',
      r.productName || '',
      r.brandName || '—',
      Number(r.qtyA ?? 0),
      Number(r.qtyB ?? 0),
      Number(r.qtyC ?? 0),
      Number(r.qtyDefect ?? 0),
      Number(r.qtyPending ?? 0),
      Number(totalQty(r) ?? 0),
    ])
  })
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } }]
  ws['!cols'] = [{ wch: 20 }, { wch: 16 }, { wch: 24 }, { wch: 14 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 10 }, { wch: 10 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '成品库存分布')
  XLSX.writeFile(wb, `成品库存分布_${localDate()}.xlsx`)
}

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

/**
 * 仓库名称映射：库存记录只返回 warehouseId，需要一次性拉仓库列表做本地映射。
 * 这里不按类别过滤——下拉筛选只给成品类仓库，但名称映射要能覆盖到任意仓，避免列显示为空。
 */
async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } })
    warehouseOptions.value = r?.records || []
  } catch { warehouseOptions.value = [] }
}

function warehouseName(id?: number) {
  const w = warehouseOptions.value.find(x => x.id === id)
  return w ? w.warehouseName : '-'
}
/** 仓库名点击：进仓库详情（委外仓走 outsource 详情页，自有成品仓走 inventory 详情页） */
function goWarehouse(row: any) {
  if (!row?.warehouseId) return
  router.push(row.factoryId
    ? `/outsource/warehouse/detail/${row.warehouseId}`
    : `/inventory/warehouse/detail/${row.warehouseId}`)
}
/** SKU 点击：进该产品的库存分布详情（该产品在各仓库的库存） */
function goProduct(row: any) {
  if (row?.productId) router.push(`/inventory/product-stock/detail/${row.productId}`)
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

onMounted(async () => { await loadWarehouses(); loadStock() })

</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding: 16px; }
.query-form { align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
