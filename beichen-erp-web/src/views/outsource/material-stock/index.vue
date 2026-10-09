<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <!-- 2026-09-29（用户口径「物料/物料类型/所在仓库 三个搜索项要显示在一行，不要两行」）：
             与「成品库存详情」同款（两页互为镜像）：查询条是全局 grid「1fr（表单）+ auto（按钮组）」，
             实测视口 1262 时表单列只有 **614px**；原先 180/160/240 ⇒ 项宽 220+228+308（+每 item 32px
             margin-right）= 884 > 614 ⇒ 「所在仓库」折到第二行。
             现按本页 label 宽度（物料 2 字 / 物料类型 4 字 / 所在仓库 4 字）差异化收窄到 **135/120/135**：
             项宽 ≈ 175+188+203 = 566 ＋ 2×12px gap = **590 ≤ 614**（余量 24）⇒ 三项锁死一行。
             ⚠️ 改这三个宽度前请跑 verify-stock-querybar-oneline.ps1。 -->
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="物料">
            <el-input v-model="query.materialName" placeholder="物料名称" clearable style="width:135px" @keyup.enter="doQuery" />
          </el-form-item>
          <el-form-item label="物料类型">
            <RemoteSelect v-model="query.materialTypeId" add-route="/dev/material-type" :fetch="fetchMaterialTypes" label-key="typeName" placeholder="全部" style="width:120px" domain="materialType" />
          </el-form-item>
          <el-form-item label="所在仓库">
            <RemoteSelect v-model="query.warehouseIds" multiple collapse-tags collapse-tags-tooltip
              add-route="/inventory/warehouse" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:135px" domain="warehouse" />
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="doQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="resetQuery">重置</el-button>
          <el-button :icon="'Download'" @click="exportMaterialStock">导出 Excel</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!--
        物料库存详情（2026-09-21 新增，镜像「成品库存详情」）。
        ⚠️ 2026-09-24（用户口径「不需要做聚合数据了」）：**本页不再做跨仓汇总** ——
           行粒度改为「仓库 × 物料」（同一物料在多仓有量就出现多行），数据来自
           /warehouse/stock/material-stock/page（与本页导出同源）。
           原先的汇总口径（每物料一行 + 分布仓库列 + 点行进「物料库存分布详情」）已取消；
           子页面 /outsource/material-stock/detail/:id 按用户要求**保留**（可直达，只是列表不再有入口）。
        与成品页的口径差异（务必保持）：
          ① 品质只有**两档**——物料走 outsource.common.QualityType（GOOD 良品 / DEFECT 不良品），
             没有成品的 A/B/C/待整理；
          ② 物料**没有安全库存字段** ⇒ 不显示安全库存列、也没有"仅看低于安全库存"筛选；
          ③ 物料没有 SKU ⇒ 不显示 SKU 列（物料名称即主标识），多一列「物料类型」。
        所有列统一用 min-width：Element Plus 按比例分摊剩余空间，窄屏刚好放下、宽屏自动铺满。
      -->
      <!-- 2026-10-09 用户需求：物料仓库存金额。列宽够（本页 7 列，加一列仍 ≤ 容器）⇒ 直接加列；
           成品库存主表 12 列余量为 0，那里改用同一个合计条组件（见 StockAmountBar 注释）。 -->
      <StockAmountBar mode="material" />
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="materialTypeName" label="物料类型" min-width="104" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialTypeName || '—' }}</template>
        </el-table-column>
        <!-- 2026-09-26 B5b（用户口径「数据显示完整 + 物料/仓库可点」）：行粒度是「仓库 × 物料」，
             两列都做成链接：物料 → 物料库存分布详情；仓库 → 仓库详情（按 warehouseCategory 分流委外/自有）。 -->
        <el-table-column label="物料名称" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.materialId" type="primary" link @click.stop="router.push(`/outsource/material-stock/detail/${row.materialId}`)">{{ row.materialName }}</el-button>
            <span v-else>{{ row.materialName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" min-width="70" align="center">
          <template #default="{ row }">{{ row.unit || '—' }}</template>
        </el-table-column>
        <!-- 2026-09-24（用户口径：不再做聚合数据）：行粒度改为「仓库 × 物料」⇒ 必须有仓库列 -->
        <el-table-column label="所在仓库" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.warehouseId" type="primary" link @click.stop="goWarehouse(row)">{{ row.warehouseName || '—' }}</el-button>
            <span v-else>{{ row.warehouseName || '—' }}</span>
          </template>
        </el-table-column>
        <!-- 品质数量用紧凑数字（非 tag）：两档 + 汇总列要在一屏内放得下，避免横向滚动 -->
        <el-table-column label="良品" min-width="86" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyGood, 'good')">{{ fmt(row.qtyGood) }}</span></template>
        </el-table-column>
        <el-table-column label="不良" min-width="86" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyDefect, 'defect')">{{ fmt(row.qtyDefect) }}</span></template>
        </el-table-column>
        <el-table-column label="总库存" min-width="86" align="right">
          <template #default="{ row }"><strong>{{ fmt(totalQty(row)) }}</strong></template>
        </el-table-column>
        <el-table-column label="库存金额" min-width="112" align="right">
          <template #default="{ row }"><StockAmountCell :row="row" /></template>
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
import { WarehouseCategory, WarehouseType } from '@/api/enums'
import StockAmountBar from '@/components/StockAmountBar.vue'
import StockAmountCell from '@/components/StockAmountCell.vue'
import request from '@/utils/request'
import * as XLSX from 'xlsx'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
/**
 * 仓库详情分流（2026-09-26 B5b）：本页行粒度是「仓库 × 物料」，仓库可能是委外仓或自有物料仓，
 * 两者详情页不同。接口行里已带 warehouseCategory（后端为前端区分两类仓专门回传）⇒ 零额外请求。
 */
function goWarehouse(row: any) {
  if (!row?.warehouseId) return
  if (row.warehouseCategory === WarehouseCategory.OUTSOURCE) router.push(`/outsource/warehouse/detail/${row.warehouseId}`)
  else router.push(`/inventory/warehouse/detail/${row.warehouseId}`)
}
// 导出的拼装逻辑抽在 ./export.ts（纯函数、无 XLSX/Vue 依赖）⇒ 可被 node 用例直测
import { buildMaterialSheets } from './export'

/**
 * 仓库下拉：只列**物料仓** —— 委外仓（category=OUTSOURCE）+ 自有物料仓（type=AUXILIARY）。
 * 与成品页相反：成品页排除 AUXILIARY（辅料仓放物料不放成品），这里正是要它。
 */
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })
    .then((res: any) => {
      res.records = (res.records || []).filter((w: any) =>
        w.warehouseCategory === WarehouseCategory.OUTSOURCE || w.warehouseType === WarehouseType.AUXILIARY)
      return res
    })
/** 物料类型下拉（物料档案的分类，等价于成品页的「品牌」筛选位） */
const fetchMaterialTypes = (kw: string) => request.get('/dev/material-type/page', { params: { pageSize: 200, typeName: kw } })

const query = reactive({
  materialName: '',
  materialTypeId: undefined as number | undefined,
  warehouseIds: [] as number[]
})
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const rows = ref<any[]>([])

/**
 * 导出 Excel（**全量**，两个视角 / 两个 sheet）：
 * ①「物料库存汇总」——按物料跨仓汇总，与列表页列一致（保持原有形态，不破坏看数习惯）
 * ②「按仓库明细」——按仓库分块：每个仓库下有哪些物料、各多少（用户 2026-09-22 要求）
 *
 * <p>关键点：</p>
 * - 只请求**一次** `/material-stock/page`（本来就是「仓库×物料」粒度、pageSize=9999 全量），
 *   再由纯函数 {@link buildMaterialSheets} 派生出两个 sheet ⇒ 两个视角是**同一快照**，数字必然对得上；
 * - **跟随页面筛选**（所在仓库 / 物料类型 / 物料）：勾了哪些仓就导哪些；
 * - 0 库存隐藏、**负库存保留并逐行备注**（委外仓「缺料强制出库」会产生负库存，是业务事实不是账错）；
 * - 请求失败时退回当前页已加载的汇总数据（此时只有汇总 sheet），保证导出始终可用。
 */
async function exportMaterialStock() {
  let data: any[] | null = null
  try {
    const params: any = { pageNum: 1, pageSize: 9999 }
    // 多选用逗号分隔：与列表查询一致（axios 默认序列化成 warehouseIds[]=1，后端 @RequestParam List 收不到）
    if (query.warehouseIds?.length) params.warehouseIds = query.warehouseIds.join(',')
    if (query.materialTypeId) params.materialTypeId = query.materialTypeId
    if (query.materialName) params.materialName = query.materialName
    const res = await request.get<any, any>('/warehouse/stock/material-stock/page', { params })
    if (Array.isArray(res?.records)) data = res.records
  } catch { /* 拉取失败：退回当前页数据 */ }
  const specs = buildMaterialSheets(data ?? rows.value ?? [], { hideZero: true })
  const wb = XLSX.utils.book_new()
  for (const s of specs) {
    const ws = XLSX.utils.aoa_to_sheet(s.aoa)
    ws['!merges'] = s.merges as any
    ws['!cols'] = s.cols as any
    XLSX.utils.book_append_sheet(wb, ws, s.name)
  }
  XLSX.writeFile(wb, `物料库存汇总_${localDate()}.xlsx`)
}

// 数量一律整数（与成品库存情况一致）
function fmt(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }

/** 品质数量配色：有量用品质色加粗，为 0 统一置灰，避免满屏色块干扰阅读 */
function qtyClass(v: number, type: 'good' | 'defect') {
  return (Number(v) || 0) > 0 ? `qty-${type}` : 'qty-zero'
}

/** 总库存 = 良品 + 不良（物料只有这两档） */
function totalQty(row: any) {
  return (Number(row.qtyGood) || 0) + (Number(row.qtyDefect) || 0)
}

async function load() {
  loading.value = true
  try {
    const params: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    // 多选用逗号分隔：axios 默认把数组序列化成 warehouseIds[]=1，后端 @RequestParam List 收不到
    if (query.warehouseIds?.length) params.warehouseIds = query.warehouseIds.join(',')
    if (query.materialTypeId) params.materialTypeId = query.materialTypeId
    if (query.materialName) params.materialName = query.materialName
    // 2026-09-24（用户口径）：不再聚合 ⇒ 直接取「仓库 × 物料」明细（与本页导出同源，数字必然一致）
    const res = await request.get<any, any>('/warehouse/stock/material-stock/page', { params })
    rows.value = res?.records || []
    page.total = res?.total || 0
  } catch {
    rows.value = []; page.total = 0
  } finally { loading.value = false }
}

function doQuery() { page.pageNum = 1; load() }
function resetQuery() {
  query.materialName = ''
  query.materialTypeId = undefined
  query.warehouseIds = []
  page.pageNum = 1
  load()
}
function onSizeChange(v: number) { page.pageSize = v; page.pageNum = 1; load() }

useDomainRefresh('materialStock', load)
</script>

<style scoped>
/* 卡片内边距已统一到全局（styles/page.css 的 .table-card .el-card__body） */
/* 2026-09-29（用户口径「三个搜索项要在一行」）：与「成品库存详情」同款（两页互为镜像）：
   全局 .query-bar 的 grid 只分「表单 1fr / 按钮组 auto」两列，表单内部仍受 el-form--inline 的
   inline-flex + 每 item 32px margin-right 与全局 `flex-wrap: nowrap` 支配 ⇒ 宽度不够就折行。
   ⚠️ 本页选择器必须与全局 `.query-card .query-bar .query-form` 同权重以上（scoped 后 0,4,0）才盖得住 nowrap。 */
.query-card .query-bar .query-form { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
/* flex:0 0 auto ⇒ item 保持声明宽度**不被压缩**（否则会被 flex-shrink 悄悄压窄）；装不下时后面的项自然折行。 */
.query-card .query-bar .query-form :deep(.el-form-item) { margin-right: 0; flex: 0 0 auto; }
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
/* 品质数量紧凑着色（对应 qtyClass）：替代 el-tag，省出横向空间 */
.qty-good { color: #67c23a; font-weight: 600; }
.qty-defect { color: #f56c6c; font-weight: 600; }
.qty-zero { color: #c0c4cc; }
/* 列宽全部交给 min-width 按比例分配，不设 nowrap：nowrap 会让超宽表头溢出到相邻列，反而造成「对不齐」 */
</style>
