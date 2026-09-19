<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="产品">
            <el-input v-model="query.productName" placeholder="产品名称或 SKU" clearable style="width:180px" @keyup.enter="doQuery" />
          </el-form-item>
          <el-form-item label="品牌">
            <RemoteSelect v-model="query.brandId" :fetch="fetchBrands" label-key="brandName" placeholder="全部" style="width:160px" />
          </el-form-item>
          <el-form-item label="所在仓库">
            <RemoteSelect v-model="query.warehouseIds" multiple collapse-tags collapse-tags-tooltip
              :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:240px" />
          </el-form-item>
          <el-form-item label="">
            <el-checkbox v-model="query.onlyLowStock" label="仅看低于安全库存" />
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="doQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="resetQuery">重置</el-button>
          <el-button :icon="'Download'" @click="exportProductStock">导出 Excel</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!--
        每行一个产品：数量为跨仓库汇总值，点击行进入详情看该产品在各仓库的分布。
        所有列统一用 min-width（不用固定 width）：Element Plus 会把剩余空间按 min-width 比例分摊给各列，
        因此窄屏刚好放下（Σmin-width 约 936，容器约 950）、宽屏自动拉伸铺满整页，不会出现留白或横向滚动。
      -->
      <el-table v-loading="loading" :data="rows" border stripe @row-click="goDetail">
        <el-table-column prop="sku" label="SKU" min-width="95" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品名称" min-width="125" show-overflow-tooltip />
        <el-table-column prop="brandName" label="品牌" min-width="78" show-overflow-tooltip>
          <template #default="{ row }">{{ row.brandName || '—' }}</template>
        </el-table-column>
        <!-- 品质数量列用紧凑数字（非 tag）：五档品质 + 汇总列要在一屏内放得下，避免横向滚动 -->
        <el-table-column label="A规" min-width="60" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyA, 'a')">{{ fmt(row.qtyA) }}</span></template>
        </el-table-column>
        <el-table-column label="B规" min-width="60" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyB, 'b')">{{ fmt(row.qtyB) }}</span></template>
        </el-table-column>
        <el-table-column label="C规" min-width="60" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyC, 'c')">{{ fmt(row.qtyC) }}</span></template>
        </el-table-column>
        <el-table-column label="不良" min-width="60" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyDefect, 'defect')">{{ fmt(row.qtyDefect) }}</span></template>
        </el-table-column>
        <el-table-column label="待分类" min-width="74" align="right">
          <template #default="{ row }">
            <span :class="qtyClass(row.qtyPending, 'pending')" title="压在成品仓、等待退货整理的库存（品质待分类 PENDING）">{{ fmt(row.qtyPending) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总库存" min-width="74" align="right">
          <template #default="{ row }"><strong>{{ fmt(totalQty(row)) }}</strong></template>
        </el-table-column>
        <el-table-column label="安全库存" min-width="82" align="right">
          <template #default="{ row }">
            <span v-if="row.safetyStock" :style="{ color: row.lowStock ? '#f56c6c' : '#67c23a' }">
              {{ fmt(row.safetyStock) }}
              <el-tooltip v-if="row.lowStock" content="总库存低于安全库存" placement="top">
                <el-icon style="vertical-align:-2px"><WarningFilled /></el-icon>
              </el-tooltip>
            </span>
            <span v-else style="color:#999">未设置</span>
          </template>
        </el-table-column>
        <el-table-column label="分布仓库" min-width="80" align="center">
          <template #default="{ row }">{{ row.warehouseCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="88" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row)">仓库分布</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="page.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="onSizeChange" @current-change="load" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { WarningFilled } from '@element-plus/icons-vue'
import { WarehouseCategory, WarehouseType } from '@/api/enums'
import request from '@/utils/request'
import * as XLSX from 'xlsx'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()

// 仓库下拉：与成品库存查询一致，只列自有成品仓（排除辅料仓，辅料仓放物料不放成品）
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
    .then((res: any) => {
      res.records = (res.records || []).filter((w: any) => w.warehouseType !== WarehouseType.AUXILIARY)
      return res
    })
const fetchBrands = (kw: string) => request.get('/brand/page', { params: { pageSize: 200, brandName: kw } })

const query = reactive({
  productName: '',
  brandId: undefined as number | undefined,
  warehouseIds: [] as number[],
  onlyLowStock: false
})
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const rows = ref<any[]>([])

/**
 * 导出 Excel（**全量**）：按当前筛选条件重新请求全部匹配产品（pageNum=1、pageSize=9999），
 * 不受列表分页限制；列与页面一致，数量按数值写入（整数不带小数）。
 * 请求失败时退回当前页已加载数据，保证导出始终可用。
 */
async function exportProductStock() {
  let data: any[] = rows.value || []
  try {
    const params: any = { pageNum: 1, pageSize: 9999 }
    // 多选用逗号分隔：与列表查询一致（axios 默认序列化成 warehouseIds[]=1，后端 @RequestParam List 收不到）
    if (query.warehouseIds?.length) params.warehouseIds = query.warehouseIds.join(',')
    if (query.brandId) params.brandId = query.brandId
    if (query.productName) params.productName = query.productName
    if (query.onlyLowStock) params.onlyLowStock = true
    const res = await request.get<any, any>('/warehouse/stock/product-summary/page', { params })
    if (Array.isArray(res?.records)) data = res.records
  } catch { /* 拉取失败：退回当前页数据 */ }
  const cols = ['SKU', '产品名称', '品牌', 'A规', 'B规', 'C规', '不良', '待分类', '总库存', '安全库存', '分布仓库']
  const aoa: (string | number)[][] = [
    [`成品库存汇总（导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 行）`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      r.sku || '',
      r.productName || '',
      r.brandName || '—',
      Number(r.qtyA ?? 0),
      Number(r.qtyB ?? 0),
      Number(r.qtyC ?? 0),
      Number(r.qtyDefect ?? 0),
      Number(r.qtyPending ?? 0),
      Number(totalQty(r) ?? 0),
      r.safetyStock ? Number(r.safetyStock) : '',
      Number(r.warehouseCount ?? 0),
    ])
  })
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } }]
  ws['!cols'] = [{ wch: 16 }, { wch: 24 }, { wch: 14 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '成品库存汇总')
  XLSX.writeFile(wb, `成品库存汇总_${localDate()}.xlsx`)
}

// 数量一律整数（2026-09-16）
function fmt(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }

/** 品质数量配色：有量用品质色加粗，为 0 统一置灰，避免满屏色块干扰阅读 */
function qtyClass(v: number, type: 'a' | 'b' | 'c' | 'defect' | 'pending') {
  return (Number(v) || 0) > 0 ? `qty-${type}` : 'qty-zero'
}

function totalQty(row: any) {
  return (Number(row.qtyA) || 0) + (Number(row.qtyB) || 0) + (Number(row.qtyC) || 0)
    + (Number(row.qtyDefect) || 0) + (Number(row.qtyPending) || 0)
}

async function load() {
  loading.value = true
  try {
    const params: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    // 多选用逗号分隔：axios 默认把数组序列化成 warehouseIds[]=1，后端 @RequestParam List 收不到
    if (query.warehouseIds?.length) params.warehouseIds = query.warehouseIds.join(',')
    if (query.brandId) params.brandId = query.brandId
    if (query.productName) params.productName = query.productName
    if (query.onlyLowStock) params.onlyLowStock = true
    const res = await request.get<any, any>('/warehouse/stock/product-summary/page', { params })
    rows.value = res?.records || []
    page.total = res?.total || 0
  } catch {
    rows.value = []; page.total = 0
  } finally { loading.value = false }
}

function doQuery() { page.pageNum = 1; load() }
function resetQuery() {
  query.productName = ''
  query.brandId = undefined
  query.warehouseIds = []
  query.onlyLowStock = false
  page.pageNum = 1
  load()
}
function onSizeChange(v: number) { page.pageSize = v; page.pageNum = 1; load() }
function goDetail(row: any) { router.push(`/inventory/product-stock/detail/${row.productId}`) }

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding: 16px; }
.query-form { align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
/* 整行可点：给出手型光标，操作列按钮不再额外高亮 */
:deep(.el-table__row) { cursor: pointer; }
/* 品质数量紧凑着色（对应 qtyClass）：替代 el-tag，省出横向空间 */
.qty-a { color: #67c23a; font-weight: 600; }
.qty-b { color: #409eff; font-weight: 600; }
.qty-c { color: #e6a23c; font-weight: 600; }
.qty-defect { color: #f56c6c; font-weight: 600; }
.qty-pending { color: #909399; font-weight: 600; }
.qty-zero { color: #c0c4cc; }
/* 列宽全部交给 min-width 按比例分配，不设 nowrap：nowrap 会让超宽表头溢出到相邻列，反而造成「对不齐」 */
</style>
