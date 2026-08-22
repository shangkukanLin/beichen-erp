<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="仓库">
          <RemoteSelect v-model="query.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:160px" />
        </el-form-item>
        <el-form-item label="产品">
          <RemoteSelect v-model="query.productId" :fetch="fetchProducts" placeholder="全部" style="width:200px" @pick="(rows:any[])=>onProductPick(rows[0])" />
        </el-form-item>
        <el-form-item label="变动类型">
          <el-select v-model="query.changeType" placeholder="全部" clearable style="width:130px">
            <el-option v-for="o in changeTypeOptions" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe max-height="calc(100vh - 260px)">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="changeType" label="变动类型" width="120" align="center">
          <template #default="{ row }"><el-tag :type="logTagType(row.changeType)" size="small">{{ row.changeTypeLabel || row.changeType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="产品名称" min-width="140">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="仓库" width="120">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="changeQuantity" label="变动数量" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.changeQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="beforeQuantity" label="变动前库存" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.beforeQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="afterQuantity" label="变动后库存" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.afterQuantity) }}</template>
        </el-table-column>
        <el-table-column label="关联单号" width="150">
          <template #default="{ row }">
            <el-link v-if="billLink(row.relatedBillType, row.relatedBillId)" type="primary" :underline="false"
              @click="handleBillClick(row.relatedBillType, row.relatedBillId)">
              {{ row.relatedBillNo }}
            </el-link>
            <span v-else>{{ row.relatedBillNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="relatedBillType" label="关联类型" width="120">
          <template #default="{ row }">{{ row.relatedBillTypeLabel || row.relatedBillType }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createTime" label="操作时间" width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="(v:number)=>{pagination.pageSize=v;loadData()}" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { WarehouseCategory } from '@/api/enums'
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

const changeTypeOptions = ['PURCHASE_IN', 'RETURN_OUT', 'SALE_OUT', 'MOVE_OUT', 'MOVE_IN', 'OTHER_IN', 'OTHER_OUT', 'CANCEL_IN', 'CANCEL_OUT', 'INIT']

const query = reactive({ warehouseId: undefined as number | undefined, productId: undefined as number | undefined, changeType: '' })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const loading = ref(false)
const tableData = ref<any[]>([])

// 仓库下拉选项（Odoo 实时查库，组件本地保存）
const warehouseOptions = ref<any[]>([])
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })
const productOptions = ref<any[]>([])
const productMap = ref<Record<number, string>>({})

function fmt(v?: number) { return v == null ? '-' : Number(v).toLocaleString() }
function warehouseName(id?: number) { const w = warehouseOptions.value.find(x => x.id === id); return w ? w.warehouseName : '' }
function productName(id?: number) { return (id && productMap.value[id]) || '' }

function logTagType(ct?: string) {
  if (!ct) return 'info'
  if (ct.includes('_IN')) return 'success'
  if (ct.includes('_OUT') || ct.includes('RETURN')) return 'danger'
  return 'info'
}

async function loadProducts(queryStr?: string) {
  try {
    const params: any = { pageSize: 100 }
    if (queryStr) params.name = queryStr
    const res = await fetchProducts(queryStr || '')
    productOptions.value = res?.records || []
  } catch { productOptions.value = [] }
}

function onProductPick(p: any) {
  if (p) productMap.value[p.id] = p.name || p.productName || ''
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize, stockType: 'PRODUCT' }
    if (query.warehouseId) params.warehouseId = query.warehouseId
    if (query.productId) params.productId = query.productId
    if (query.changeType) params.changeType = query.changeType
    const res = await request.get<any, any>('/warehouse/stock/log', { params })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
    // 收集产品ID并批量加载产品名称
    const ids = [...new Set(tableData.value.map((r: any) => r.productId).filter(Boolean))]
    for (const id of ids as number[]) {
      if (!productMap.value[id]) {
        try {
          const r = await request.get<any, any>(`/product/${id}`)
          if (r) productMap.value[id] = r.name || r.productName || ''
        } catch { productMap.value[id] = '' }
      }
    }
  } catch { tableData.value = [] } finally { loading.value = false }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.warehouseId = undefined; query.productId = undefined; query.changeType = ''; pagination.pageNum = 1; loadData() }

// 关联单号可点击跳转映射(根据 relatedBillType → 单据详情路由)
const router = useRouter()
const billDetailRouteMap: Record<string, string> = {
  PURCHASE_ORDER: '/inventory/purchase/detail', PURCHASE_INBOUND: '/inventory/purchase/detail',
  PURCHASE_RETURN: '/inventory/purchase-return', SALE_ORDER: '/inventory/sale',
  SALE_OUTBOUND: '/inventory/sale', WAREHOUSE_MOVE: '/inventory/warehouse-move',
  WAREHOUSE_MOVE_UN_AUDIT: '/inventory/warehouse-move', OTHER_IO: '/inventory/other-io',
  // 委外模块有独立详情页
  OUTSOURCE_DELIVERY: '/outsource/delivery/detail', MATERIAL_IO: '/outsource/delivery/detail',
  OUTSOURCE_ORDER: '/outsource/order/detail', OUTSOURCE_DEFECT: '/outsource/order/detail',
  OUTSOURCE_RETURN: '/outsource/return-order/detail',
  SUPPLIER_SETTLEMENT: '/supplier/manage',
}
function billLink(billType?: string, billId?: number): boolean {
  return !!(billType && billId && billDetailRouteMap[billType])
}
function handleBillClick(billType?: string, billId?: number) {
  if (!billType || !billId) return
  const route = billDetailRouteMap[billType]
  if (!route) return
  // 委外模块有独立详情页，带上ID
  if (route.includes('/detail')) {
    router.push(`${route}/${billId}`)
  } else {
    // 其他模块跳列表页并传递 billId（可被页面接受后打开详情弹窗）
    router.push({ path: route, query: { billId: String(billId), billType: billType } })
  }
}

onMounted(() => { loadData() })

</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding: 16px; }
.query-form { align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
