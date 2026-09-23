<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="仓库">
          <RemoteSelect v-model="query.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:160px" />
        </el-form-item>
        <el-form-item label="产品">
          <RemoteSelect v-model="query.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="全部（可输SKU）" style="width:200px" @pick="(rows:any[])=>onProductPick(rows[0])" />
        </el-form-item>
        <el-form-item label="变动类型">
          <el-select v-model="query.changeType" placeholder="全部" clearable filterable style="width:160px">
            <el-option v-for="o in changeTypeOptions" :key="o.code" :label="o.label" :value="o.code" />
          </el-select>
        </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button :icon="'Download'" @click="exportStockLog">导出 Excel</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe max-height="calc(100vh - 260px)">
        <el-table-column prop="createTime" label="时间" width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="changeType" label="变动类型" width="130" align="center">
          <template #default="{ row }"><el-tag :type="logTagType(row.changeType)" size="small">{{ StockChangeTypeLabel[row.changeType] || row.changeType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="关联单号" width="150">
          <template #default="{ row }">
            <el-link v-if="billLink(row.relatedBillType, row.relatedBillId)" type="primary" underline="never"
              @click="handleBillClick(row)">
              {{ row.relatedBillNo }}
            </el-link>
            <span v-else>{{ row.relatedBillNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="产品名称" min-width="140">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column label="仓库" width="120">
          <template #default="{ row }">
            <el-button v-if="row.warehouseId" type="primary" link @click.stop="router.push(`/inventory/warehouse/detail/${row.warehouseId}`)">
              {{ warehouseName(row.warehouseId) }}
            </el-button>
            <span v-else>{{ warehouseName(row.warehouseId) }}</span>
          </template>
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
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="(v:number)=>{pagination.pageSize=v;pagination.pageNum=1;loadData()}" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { WarehouseCategory, StockChangeTypeLabel, codeLabelOptions } from '@/api/enums'
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { productLabel } from '@/api/product'
import * as XLSX from 'xlsx'

/** 时间格式化（脚本内导出用；模板里的 $fmtDate 是全局属性，脚本中取不到） */
function fmtDateTime(v: any) {
  if (!v) return ''
  const d = new Date(String(v).replace(' ', 'T'))
  if (isNaN(d.getTime())) return String(v)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/**
 * 导出 Excel（**全量**）：按当前筛选条件重新请求全部匹配流水（pageNum=1、pageSize=9999），
 * 不受列表分页限制；列与页面一致，数量按数值写入（整数不带小数）。
 * 请求失败时退回当前页已加载数据，保证导出始终可用。
 */
async function exportStockLog() {
  let data: any[] = tableData.value || []
  try {
    const params: any = { pageNum: 1, pageSize: 9999, stockType: 'PRODUCT' }
    if (query.warehouseId) params.warehouseId = query.warehouseId
    if (query.productId) params.productId = query.productId
    if (query.changeType) params.changeType = query.changeType
    const res = await request.get<any, any>('/warehouse/stock/log', { params })
    if (Array.isArray(res?.records)) data = res.records
  } catch { /* 拉取失败：退回当前页数据 */ }
  const cols = ['时间', '变动类型', '关联单号', '产品名称', '仓库', '变动数量', '变动前库存', '变动后库存']
  const aoa: (string | number)[][] = [
    [`库存流水（导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 行）`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      fmtDateTime(r.createTime),
      StockChangeTypeLabel[r.changeType] || r.changeType || '',
      r.relatedBillNo || '',
      productName(r.productId) || '',
      warehouseName(r.warehouseId) || '',
      Number(r.changeQuantity ?? 0),
      Number(r.beforeQuantity ?? 0),
      Number(r.afterQuantity ?? 0),
    ])
  })
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } }]
  ws['!cols'] = [{ wch: 20 }, { wch: 16 }, { wch: 20 }, { wch: 24 }, { wch: 18 }, { wch: 10 }, { wch: 12 }, { wch: 12 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '库存流水')
  XLSX.writeFile(wb, `库存流水_${localDate()}.xlsx`)
}

// 变动类型选项（2026-09-14：改由前端枚举映射生成；后端 /warehouse/stock/change-types 已只回 code）
const changeTypeOptions = codeLabelOptions(StockChangeTypeLabel)

const query = reactive({ warehouseId: undefined as number | undefined, productId: undefined as number | undefined, changeType: '' })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const loading = ref(false)
const tableData = ref<any[]>([])

// 仓库下拉选项（Odoo 实时查库，组件本地保存）
const warehouseOptions = ref<any[]>([])
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })
const productMap = ref<Record<number, string>>({})

function fmt(v?: number) { return v == null ? '-' : Number(v).toLocaleString() }
function warehouseName(id?: number) { const w = warehouseOptions.value.find(x => x.id === id); return w ? w.warehouseName : '-' }
function productName(id?: number) { return (id && productMap.value[id]) || '' }

function logTagType(ct?: string) {
  if (!ct) return 'info'
  if (ct.includes('_IN')) return 'success'
  if (ct.includes('_OUT') || ct.includes('RETURN')) return 'danger'
  return 'info'
}

/**
 * 仓库名称映射：流水只返回 warehouseId，需要一次性拉仓库列表做本地映射。
 * 这里不按 warehouseCategory 过滤——下拉筛选仍只给自有仓，但名称映射要能覆盖到任意仓，避免列显示为空。
 */
async function loadWarehouses() {
  try {
    const res = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } })
    warehouseOptions.value = res?.records || []
  } catch { warehouseOptions.value = [] }
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

// 关联单号可点击跳转映射：relatedBillType → 详情页路由前缀（/detail 结尾的直接带 ID 跳详情页；
// 无独立详情页的类型跳列表页并传 billId，由对应列表页挂载时定位打开详情弹窗）
const router = useRouter()
const billDetailRouteMap: Record<string, string> = {
  // 采购/销售/出入库 → 独立详情页
  PURCHASE_ORDER: '/inventory/purchase/detail', PURCHASE_INBOUND: '/inventory/purchase/detail',
  PURCHASE_RETURN: '/inventory/purchase-return/detail',
  SALE_ORDER: '/inventory/sale/detail', SALE_OUTBOUND: '/inventory/sale/detail',
  SALE_RETURN: '/sale/return/detail', SALE_EXCHANGE: '/sale/exchange/detail',
  OTHER_IO: '/inventory/other-io/detail',
  // 委外模块独立详情页
  OUTSOURCE_DELIVERY: '/outsource/delivery/detail', MATERIAL_IO: '/outsource/delivery/detail',
  OUTSOURCE_ORDER: '/outsource/order/detail', OUTSOURCE_DEFECT: '/outsource/order/detail',
  OUTSOURCE_RETURN: '/outsource/return-order/detail',
  OUTSOURCE_MATERIAL_RETURN: '/outsource/material-return/detail',
  // 无独立详情页 → 跳列表页定位
  WAREHOUSE_MOVE: '/inventory/warehouse-move', WAREHOUSE_MOVE_UN_AUDIT: '/inventory/warehouse-move',
  PRODUCT_RECLASSIFY: '/inventory/reclassify', RETURN_SORT: '/inventory/return-sort',
  SUPPLIER_SETTLEMENT: '/supplier/manage',
}
/**
 * 详情目标ID：默认取 relatedBillId；
 * 销售出库单（SALE_OUTBOUND）的 relatedBillId 指出库单，后端已映射为关联销售单ID（relatedBillDetailId）
 */
function billTargetId(row: any): number | undefined {
  return row?.relatedBillDetailId ?? row?.relatedBillId
}
function billLink(billType?: string, billId?: number): boolean {
  return !!(billType && billId && billDetailRouteMap[billType])
}
function handleBillClick(row: any) {
  const billType = row?.relatedBillType
  const billId = billTargetId(row)
  if (!billType || !billId) return
  const route = billDetailRouteMap[billType]
  if (!route) return
  if (route.includes('/detail')) {
    router.push(`${route}/${billId}`)
  } else {
    // 无独立详情页：跳列表页并传 billId（页面挂载时定位打开详情弹窗）
    router.push({ path: route, query: { billId: String(billId), billType } })
  }
}

onMounted(async () => { await loadWarehouses(); loadData() })

</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
/* 卡片内边距已统一到全局（styles/page.css 的 .table-card .el-card__body） */
.query-form { align-items: center; }
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
</style>
