<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <!-- 2026-09-29（用户口径「产品/品牌/所在仓库 三个搜索项要显示在一行，不要两行」）：
             查询条是全局 grid「1fr（表单）+ auto（按钮组）」，实测视口 1262 时表单列只有 **614px**；
             原先 180/160/240 ⇒ 项宽 220+200+308（+每个 item 32px margin-right）= 826 > 614 ⇒ 「所在仓库」折行。
             现按实测收窄到 **145/125/145**（项宽 ≈ 185+165+213 = 563）＋ 2×12px gap = **587 ≤ 614**（余量 27）⇒
             三项锁死一行；「仅看低于安全库存」勾选仍留在第二行（口径只要求三个搜索项一行）。
             ⚠️ 改这三个宽度前请跑 verify-stock-querybar-oneline.ps1（它断言三项同一行且不溢出）。 -->
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="产品">
            <el-input v-model="query.productName" placeholder="产品名称或 SKU" clearable style="width:145px" @keyup.enter="doQuery" />
          </el-form-item>
          <el-form-item label="品牌">
            <RemoteSelect v-model="query.brandId" add-route="/inventory/brand" :fetch="fetchBrands" label-key="brandName" placeholder="全部" style="width:125px" domain="brand" />
          </el-form-item>
          <el-form-item label="所在仓库">
            <RemoteSelect v-model="query.warehouseIds" multiple collapse-tags collapse-tags-tooltip
              add-route="/inventory/warehouse" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:145px" domain="warehouse" />
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
        因此窄屏刚好放下、宽屏自动拉伸铺满整页，不会出现留白或横向滚动。
        2026-09-26 B5a（用户口径「数据显示完整 + 产品可点」，实测驱动）：
        ①品牌 min78→**108**（实测需 107，原先全部行被截断）；
        ②产品名称做成链接进产品详情（原先只能点整行/操作列）；
        ③为抵平把 SKU 95→88、产品名称 min125→116、待整理 74→72、总库存 74→72、安全库存 82→80、分布仓库 80→74
          ⇒ 声明合计 **938** ✓（容器约 948；全部 min-width ⇒ 宽屏自动铺满）。
        ⚠️ 上列三处数值已被 2026-09-26 B6 记录更新（现值：SKU min98、品牌 min100、分布仓库 min82、声明合计 948 ≤ 容器 956）。
      -->
      <el-table v-loading="loading" :data="rows" border stripe @row-click="goDetail">
        <!-- 2026-09-26 B6（实测）：SKU 是最长 10 位的业务编码（最长样本「SKU-000012」正文需 93px），
             min88 会把 10 行 SKU 全部省略 ⇒ min88→**98**（93 + 内边距 16 + 边框 1 的最省值再留 2px 余量）。 -->
        <el-table-column prop="sku" label="SKU" min-width="98" show-overflow-tooltip />
        <el-table-column label="产品名称" min-width="116" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goProduct(row)">{{ row.productName }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="brandName" label="品牌" min-width="100" show-overflow-tooltip>
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
        <el-table-column label="待整理" min-width="72" align="right">
          <template #default="{ row }">
            <span :class="qtyClass(row.qtyPending, 'pending')" title="压在成品仓、等待退货整理的库存（品质待整理 PENDING）">{{ fmt(row.qtyPending) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总库存" min-width="72" align="right">
          <template #default="{ row }"><strong>{{ fmt(totalQty(row)) }}</strong></template>
        </el-table-column>
        <el-table-column label="安全库存" min-width="80" align="right">
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
        <!-- 2026-09-26 B6：4 字表头需 56+16+1+2 = 75；74（或再分配后的 74）会让余量只剩 1px ⇒ min74→**82**，
             宽度由「品牌」108→100 让出（品牌正文最长 73px，100 仍完整）。 -->
        <el-table-column label="分布仓库" min-width="82" align="center">
          <template #default="{ row }">{{ row.warehouseCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="88" align="center" fixed="right">
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
import { useDomainRefresh } from '@/utils/dataFreshness'
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
  const cols = ['SKU', '产品名称', '品牌', 'A规', 'B规', 'C规', '不良', '待整理', '总库存', '安全库存', '分布仓库']
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
/** 产品名称 → 产品主数据详情（2026-09-26 B5a：本页「产品名称」列由纯文本改为可点） */
function goProduct(row: any) { if (row.productId) router.push(`/product/detail/${row.productId}`) }

useDomainRefresh('productStock', load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
/* 卡片内边距已统一到全局（styles/page.css 的 .table-card .el-card__body） */
/* 2026-09-29（用户口径「三个搜索项要在一行」）：全局 .query-bar 的 grid 只把「表单 1fr / 按钮组 auto」分两列，
   表单内部仍是 el-form--inline 的 **inline-flex + 每个 item 32px margin-right** ⇒ 宽度一不够就折行
   （实测：三项 563 + 3×32 = 659 > 表单列 614）。
   ⚠️ 全局还写着 `.query-card .query-bar .query-form { flex-wrap: nowrap }`（0,3,0）⇒ 本页选择器**必须同权重以上**
      （scoped 后是 0,4,0）才盖得住它；否则 display:flex 一旦生效、又不许换行 ⇒ 4 个 item 挤成一行**溢出** 119px。 */
.query-card .query-bar .query-form { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
/* flex:0 0 auto ⇒ item 保持声明宽度**不被压缩**（否则会被 flex-shrink 悄悄压成 131/111/103 这种"看不出来
   但已经变窄"的状态）；装不下时让后面的勾选项自然折行。 */
.query-card .query-bar .query-form :deep(.el-form-item) { margin-right: 0; flex: 0 0 auto; }
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
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
