<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="仓库">
          <RemoteSelect v-model="query.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:180px" />
        </el-form-item>
        <el-form-item label="物料">
          <RemoteSelect v-model="query.materialId" :fetch="fetchMaterials" label-key="materialName" placeholder="全部（可输名称）" style="width:200px" @pick="(rows:any[])=>onMaterialPick(rows[0])" />
        </el-form-item>
        <el-form-item label="变动类型">
          <el-select v-model="query.changeType" placeholder="全部" clearable filterable style="width:180px">
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
        <!-- 2026-09-26 B5b（实测驱动，与成品侧「库存流水」同口径）：时间 160→**96**（该列只显示日期，
             原 160 白占 64px）、变动类型 110→150（中文枚举实测需 192 ⇒ 白名单 + tooltip）、
             关联单号 120→**166**（实测需 161）、仓库 120→**150**、数量列 84/92/92。 -->
        <el-table-column prop="createTime" label="时间" width="96">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="变动类型" width="150" align="center" show-overflow-tooltip>
          <template #default="{ row }"><el-tag :type="logTagType(row.changeType)" size="small">{{ StockChangeTypeLabel[row.changeType] || row.changeType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="关联单号" width="166" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link v-if="billLink(row.relatedBillType, row.relatedBillId)" type="primary" underline="never"
              @click="handleBillClick(row)">
              {{ row.relatedBillNo }}
            </el-link>
            <span v-else>{{ row.relatedBillNo }}</span>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B5b：物料名称做成链接进物料库存分布详情（与成品侧「产品名称」对称） -->
        <el-table-column label="物料名称" min-width="102" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.materialId" type="primary" link @click.stop="router.push(`/outsource/material-stock/detail/${row.materialId}`)">{{ materialName(row) }}</el-button>
            <span v-else>{{ materialName(row) }}</span>
          </template>
        </el-table-column>
        <!-- 仓库 120→**189**（委外仓名实测需 189 ⇒ 完整显示）；点击按仓库类别分流（委外仓 / 自有物料仓）
             2026-09-26 B8：为抵平把「物料名称」min110→71（物料名长度无上界 ⇒ 白名单 + tooltip，净减截断单元格） -->
        <el-table-column label="仓库" width="174" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- 物料可能存放在委外仓（实测占大半），仓库名可点进仓库详情 -->
            <el-button v-if="row.warehouseId" type="primary" link @click.stop="goWarehouse(row.warehouseId)">
              {{ warehouseName(row.warehouseId) }}
            </el-button>
            <span v-else>{{ warehouseName(row.warehouseId) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="changeQuantity" label="变动数量" width="84" align="right">
          <template #default="{ row }">{{ fmt(row.changeQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="beforeQuantity" label="变动前库存" width="92" align="right">
          <template #default="{ row }">{{ fmt(row.beforeQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="afterQuantity" label="变动后库存" width="92" align="right">
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
// 物料库存流水（2026-09-22 用户要求新增，方案 A）：
// 与成品侧「成品库存流水」**共用同一张 warehouse_stock_log**（物料行动是 material_id 非空），
// 后端同一个接口 GET /warehouse/stock/log，用 stockType=MATERIAL 只看物料行。
// 本页与成品页的**三处差异**（其余列/关联单号跳转/导出逻辑完全同构）：
//   ① 筛选「产品」→「物料」（走 materialId；2026-09-22 后端为它新增了 materialId 参数）
//   ② 列「产品名称」→「物料名称」（直接用流水行里已存的 materialName，缺了才查接口兜底）
//   ③ **仓库下拉 = 委外仓 + 自有物料仓** —— 实测物料流水 489/582 行发生在委外仓，
//      若照抄成品页的 warehouseCategory=INVENTORY，页面大部分时间会是空的。
import { localDate } from '@/utils/date'
import { WarehouseCategory, WarehouseType, StockChangeTypeLabel, codeLabelOptions } from '@/api/enums'
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
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
 * 不受列表分页限制；列与页面一致，数量按数值写入。请求失败时退回当前页已加载数据。
 */
async function exportStockLog() {
  let data: any[] = tableData.value || []
  try {
    const params: any = { pageNum: 1, pageSize: 9999, stockType: 'MATERIAL' }
    if (query.warehouseId) params.warehouseId = query.warehouseId
    if (query.materialId) params.materialId = query.materialId
    if (query.changeType) params.changeType = query.changeType
    const res = await request.get<any, any>('/warehouse/stock/log', { params })
    if (Array.isArray(res?.records)) data = res.records
  } catch { /* 拉取失败：退回当前页数据 */ }
  const cols = ['时间', '变动类型', '关联单号', '物料名称', '仓库', '变动数量', '变动前库存', '变动后库存']
  const aoa: (string | number)[][] = [
    [`物料库存流水（导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 行）`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      fmtDateTime(r.createTime),
      StockChangeTypeLabel[r.changeType] || r.changeType || '',
      r.relatedBillNo || '',
      rowMaterialName(r),
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
  XLSX.utils.book_append_sheet(wb, ws, '物料库存流水')
  XLSX.writeFile(wb, `物料库存流水_${localDate()}.xlsx`)
}

// 变动类型选项（前端枚举映射生成；后端只回 code）
const changeTypeOptions = codeLabelOptions(StockChangeTypeLabel)

const query = reactive({ warehouseId: undefined as number | undefined, materialId: undefined as number | undefined, changeType: '' })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const loading = ref(false)
const tableData = ref<any[]>([])

// 仓库下拉选项：**物料仓 = 委外仓（OUTSOURCE）∪ 自有物料仓（INVENTORY + AUXILIARY）**
const warehouseOptions = ref<any[]>([])
const fetchWarehouses = async (kw: string) => {
  const res: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })
  const all: any[] = res?.records || []
  const records = all.filter((w: any) => w.warehouseCategory === WarehouseCategory.OUTSOURCE
    || (w.warehouseCategory === WarehouseCategory.INVENTORY && w.warehouseType === WarehouseType.AUXILIARY))
  return { records, total: records.length }
}
const fetchMaterials = (kw: string) => request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw } })
const materialMap = ref<Record<number, string>>({})

function fmt(v?: number) { return v == null ? '-' : Number(v).toLocaleString() }
function warehouseName(id?: number) { const w = warehouseOptions.value.find(x => x.id === id); return w ? w.warehouseName : '-' }
/** 物料名：优先取流水行自带（写入时就落库了），缺失才用本地映射 / ID 兜底 */
function rowMaterialName(row: any) {
  if (row?.materialName) return row.materialName
  const id = row?.materialId
  if (!id) return ''
  return materialMap.value[id] || `物料#${id}`
}
function materialName(row: any) { return rowMaterialName(row) || '-' }

function logTagType(ct?: string) {
  if (!ct) return 'info'
  if (ct.includes('_IN') || ct.includes('RECEIVE') || ct.includes('RETURN_IN')) return 'success'
  if (ct.includes('_OUT') || ct.includes('RETURN') || ct.includes('CONSUME')) return 'danger'
  return 'info'
}

/**
 * 仓库名称映射：流水只返回 warehouseId，需一次性拉仓库列表做本地映射。
 * 这里**不按类别过滤** —— 下拉只给物料仓，但名称映射要覆盖任意仓（含委外仓），避免列显示成 '-'。
 */
async function loadWarehouses() {
  try {
    const res = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } })
    warehouseOptions.value = res?.records || []
  } catch { warehouseOptions.value = [] }
}

/** 物料名兜底映射（个别历史行 material_name 为空时用） */
async function loadMaterialMap() {
  try {
    const res = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } })
    const map: Record<number, string> = {}
    ;(res?.records || []).forEach((m: any) => { if (m?.id) map[m.id] = m.materialName || '' })
    materialMap.value = map
  } catch { materialMap.value = {} }
}

function onMaterialPick(m: any) {
  if (m?.id) materialMap.value[m.id] = m.materialName || ''
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize, stockType: 'MATERIAL' }
    if (query.warehouseId) params.warehouseId = query.warehouseId
    if (query.materialId) params.materialId = query.materialId
    if (query.changeType) params.changeType = query.changeType
    const res = await request.get<any, any>('/warehouse/stock/log', { params })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { tableData.value = [] } finally { loading.value = false }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.warehouseId = undefined; query.materialId = undefined; query.changeType = ''; pagination.pageNum = 1; loadData() }

// 关联单号可点击跳转映射：relatedBillType → 详情页路由。
// ⚠️ 与成品页的差异：同名 code 在这里必须指向**物料侧**页面 ——
//    OTHER_IO（物料其他出入库与成品其他出入库共用同一 code）→ /outsource/other-io/detail
const router = useRouter()
const billDetailRouteMap: Record<string, string> = {
  // 委外物料类独立详情页
  OUTSOURCE_DELIVERY: '/outsource/delivery/detail',
  MATERIAL_IO: '/outsource/delivery/detail',
  // 物料移仓单（2026-09-24 新增）：审核与反审核两套 related_bill_type 都指向同一详情页
  MATERIAL_MOVE: '/inventory/material-move/detail',
  MATERIAL_MOVE_UN_AUDIT: '/inventory/material-move/detail',
  OUTSOURCE_ORDER: '/outsource/order/detail',
  OUTSOURCE_DEFECT: '/outsource/order/detail',
  OUTSOURCE_RETURN: '/outsource/return-order/detail',
  OUTSOURCE_REPAIR: '/outsource/return-order/detail',
  OUTSOURCE_MATERIAL_RETURN: '/outsource/material-return/detail',
  OUTSOURCE_MATERIAL_REPAIR: '/outsource/material-return/detail',
  OUTSOURCE_STOCK_LOSS: '/outsource/stock-loss/detail',
  OTHER_IO: '/outsource/other-io/detail',
  // 无独立详情页 → 跳列表页定位
  STOCK_TAKE: '/outsource/material-stock-take',
  SUPPLIER_SETTLEMENT: '/supplier/manage',
}
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
    router.push({ path: route, query: { billId: String(billId), billType } })
  }
}
/**
 * 仓库详情分流（2026-09-26 B5b 修复）：物料流水**大半发生在委外仓**，原实现一律跳 /inventory/warehouse/detail
 * ⇒ 委外仓会落到"仓库不存在/空页"。改为按仓库类别分流（仓库列表本页已全量加载 => 零额外请求）。
 */
function goWarehouse(id?: number) {
  if (id == null) return
  const w = warehouseOptions.value.find((x: any) => x.id === id)
  if (w?.warehouseCategory === WarehouseCategory.OUTSOURCE) router.push(`/outsource/warehouse/detail/${id}`)
  else router.push(`/inventory/warehouse/detail/${id}`)
}

onMounted(async () => { await Promise.all([loadWarehouses(), loadMaterialMap()]); loadData() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
/* 卡片内边距已统一到全局（styles/page.css 的 .table-card .el-card__body） */
.query-form { align-items: center; }
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
</style>
