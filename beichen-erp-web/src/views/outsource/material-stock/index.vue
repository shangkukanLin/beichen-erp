<template>
  <div class="page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="物料">
            <el-input v-model="query.materialName" placeholder="物料名称" clearable style="width:180px" @keyup.enter="doQuery" />
          </el-form-item>
          <el-form-item label="物料类型">
            <RemoteSelect v-model="query.materialTypeId" :fetch="fetchMaterialTypes" label-key="typeName" placeholder="全部" style="width:160px" />
          </el-form-item>
          <el-form-item label="所在仓库">
            <RemoteSelect v-model="query.warehouseIds" multiple collapse-tags collapse-tags-tooltip
              :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:240px" />
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
        物料库存情况（2026-09-21 新增，镜像「成品库存情况」）。
        与成品页的口径差异（务必保持）：
          ① 品质只有**两档**——物料走 outsource.common.QualityType（GOOD 良品 / DEFECT 不良品），
             没有成品的 A/B/C/待整理；
          ② 物料**没有安全库存字段** ⇒ 不显示安全库存列、也没有"仅看低于安全库存"筛选；
          ③ 物料没有 SKU ⇒ 不显示 SKU 列（物料名称即主标识），多一列「物料类型」。
        每行一个物料：数量为跨仓库汇总值，点击行进入详情看该物料在各仓库的分布。
        所有列统一用 min-width：Element Plus 按比例分摊剩余空间，窄屏刚好放下、宽屏自动铺满。
      -->
      <el-table v-loading="loading" :data="rows" border stripe @row-click="goDetail">
        <el-table-column prop="materialTypeName" label="物料类型" min-width="104" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialTypeName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" min-width="70" align="center">
          <template #default="{ row }">{{ row.unit || '—' }}</template>
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
        <el-table-column label="分布仓库" min-width="88" align="center">
          <template #default="{ row }">{{ row.warehouseCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="96" align="center">
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
import { WarehouseCategory, WarehouseType } from '@/api/enums'
import request from '@/utils/request'
import * as XLSX from 'xlsx'
import RemoteSelect from '@/components/RemoteSelect.vue'
// 导出的拼装逻辑抽在 ./export.ts（纯函数、无 XLSX/Vue 依赖）⇒ 可被 node 用例直测
import { buildMaterialSheets } from './export'

const router = useRouter()

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
    const res = await request.get<any, any>('/warehouse/stock/material-summary/page', { params })
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
function goDetail(row: any) { router.push(`/outsource/material-stock/detail/${row.materialId}`) }

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
.qty-good { color: #67c23a; font-weight: 600; }
.qty-defect { color: #f56c6c; font-weight: 600; }
.qty-zero { color: #c0c4cc; }
/* 列宽全部交给 min-width 按比例分配，不设 nowrap：nowrap 会让超宽表头溢出到相邻列，反而造成「对不齐」 */
</style>
