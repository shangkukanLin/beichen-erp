<script setup lang="ts">
import { localDate } from '@/utils/date'
import { WarehouseCategory, ProductQualityType, WarehouseTypeLabel } from '@/api/enums'
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import * as XLSX from 'xlsx'
import PageShell from '@/components/PageShell.vue'
import StockAmountBar from '@/components/StockAmountBar.vue'

const route = useRoute(); const router = useRouter()
const warehouseId = Number(route.params.id)
const warehouse = ref<any>(null)
const loading = ref(false)
const matLoading = ref(false)
const activeTab = ref('info')
const materials = ref<any[]>([])
const products = ref<any[]>([])

async function loadWarehouse() {
  loading.value = true
  // 2026-09-20（F7-177）：改为按 id 精确查询（照搬 outsource/warehouse-detail.vue:98 的既有修法）。
  // 原先拉 /warehouse/page?pageSize=200 再前端 find：「仓库总数超过 200 时找不到 ⇒ 基础信息区静默空白」，
  // 且为显示一个仓库把整张主数据表拉回来。
  try {
    warehouse.value = await request.get<any, any>(`/warehouse/${warehouseId}`)
  } catch { warehouse.value = null } finally { loading.value = false }
}

// 按物料聚合库存：物料不区分品质，直接累加数量
function groupMaterialStocks(rows: any[]) {
  const map = new Map<number, any>()
  for (const r of rows || []) {
    if (r.materialId == null) continue // 仅统计物料库存
    if (!map.has(r.materialId)) {
      map.set(r.materialId, { materialId: r.materialId, materialName: r.materialName || '', materialTypeName: r.materialTypeName || '', quantity: 0 })
    }
    const row = map.get(r.materialId)
    row.quantity += Number(r.quantity) || 0
  }
  return Array.from(map.values())
}

// 按产品聚合库存：by-warehouse 返回一条品质一条记录，这里归并为每产品一行（A/B/C/不良四列）
function groupProductStocks(rows: any[]) {
  const map = new Map<number, any>()
  for (const r of rows || []) {
    if (r.productId == null) continue // 仅统计成品库存
    if (!map.has(r.productId)) {
      map.set(r.productId, { productId: r.productId, sku: r.sku || '', productName: r.productName || '', qtyA: 0, qtyB: 0, qtyC: 0, qtyDefect: 0, qtyPending: 0 })
    }
    const row = map.get(r.productId)
    const q = Number(r.quantity) || 0
    // 2026-09-14：后端已改回返回品质 code（不再回中文），按 code 归并即可
    const qt = r.qualityType
    if (qt === ProductQualityType.A) row.qtyA += q
    else if (qt === ProductQualityType.B) row.qtyB += q
    else if (qt === ProductQualityType.C) row.qtyC += q
    else if (qt === ProductQualityType.PENDING) row.qtyPending += q
    else if (qt === ProductQualityType.DEFECT) row.qtyDefect += q
    // 其余未知品质不计数：不可用 else 兜底，否则待整理等会被误算成不良品
  }
  return Array.from(map.values())
}

async function loadMaterials() {
  matLoading.value = true
  try {
    const r = await request.get<any,any>(`/warehouse/stock/by-warehouse/${warehouseId}`)
    const rows = Array.isArray(r?.records || r?.data || r) ? (r?.records || r?.data || r) : []
    materials.value = groupMaterialStocks(rows)
    products.value = groupProductStocks(rows)
  } finally { matLoading.value = false }
}

function goLog(row: any) { router.push(`/inventory/warehouse/product-history/${warehouseId}/${row.productId}`) }
function goMaterialLog(row: any) { router.push(`/inventory/warehouse/material-history/${warehouseId}/${row.materialId}`) }
// 数量一律整数（2026-09-16）
function fmt(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }
function totalQty(row: any) {
  return (Number(row.qtyA) || 0) + (Number(row.qtyB) || 0) + (Number(row.qtyC) || 0)
    + (Number(row.qtyDefect) || 0) + (Number(row.qtyPending) || 0)
}



/**
 * 导出"当前页签"为 Excel：列与页面表格一致，数量按数值写入（整数不带小数）。
 * - 物料信息 → 物料清单（物料名称/物料类型/数量）
 * - 产品信息 → 成品清单（SKU/产品名称/A规…待整理/总库存）
 * - 仓库信息 → 仓库档案（项目/内容），避免"点了导出没反应"
 */
function exportExcel() {
  const wname = warehouse.value?.warehouseName || '仓库'
  const now = new Date().toLocaleString('zh-CN')
  let sheetName = ''
  let cols: string[] = []
  let body: (string | number)[][] = []

  if (activeTab.value === 'material') {
    sheetName = '物料库存'
    cols = ['物料名称', '物料类型', '数量']
    body = (materials.value || []).map((r: any) => [r.materialName || '', r.materialTypeName || '-', Number(r.quantity ?? 0)])
  } else if (activeTab.value === 'product') {
    sheetName = '成品库存'
    cols = ['SKU', '产品名称', 'A规', 'B规', 'C规', '不良', '待整理', '总库存']
    body = (products.value || []).map((r: any) => [
      r.sku || '', r.productName || '',
      Number(r.qtyA ?? 0), Number(r.qtyB ?? 0), Number(r.qtyC ?? 0),
      Number(r.qtyDefect ?? 0), Number(r.qtyPending ?? 0), Number(totalQty(r) ?? 0),
    ])
  } else {
    sheetName = '仓库信息'
    cols = ['项目', '内容']
    const w = warehouse.value || {}
    body = [
      ['仓库名称', w.warehouseName || '-'],
      ['编码', w.code || '-'],
      ['类型', WarehouseTypeLabel[w.warehouseType] || w.warehouseType || '-'],
      ['地址', w.address || '-'],
      ['联系人', w.contact || '-'],
      ['电话', w.phone || '-'],
      ['备注', w.remark || '-'],
    ]
  }

  const aoa: (string | number)[][] = [
    [`${wname} - ${sheetName}（导出时间：${now}，共 ${body.length} 行）`],
    [],
    cols,
    ...body,
  ]
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, sheetName)
  XLSX.writeFile(wb, `${wname}_${sheetName}_${localDate()}.xlsx`)
}

onMounted(() => { loadWarehouse(); loadMaterials() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta)；本页只读（导出留在卡内工具栏） -->
  <PageShell :loading="loading" back-fallback="/inventory/warehouse">
    <el-card shadow="never">
      <!-- 2026-10-09 用户需求：本仓库存金额。口径与两个库存列表页同源，**范围随仓库性质收敛**：
           物料仓(AUXILIARY) 只看物料、成品仓(FINISHED) 只看成品 —— 与页面上的库存内容一致
           （用户口径："成品库存详情的库存金额只算成品的"）。委外仓在 outsource/warehouse-detail 里同时看物料与退回成品。 -->
      <StockAmountBar :warehouse-id="warehouseId" :mode="warehouse?.warehouseType === 'AUXILIARY' ? 'material' : 'product'" />
      <div class="toolbar">
        <el-button :icon="'Download'" @click="exportExcel">导出当前页签</el-button>
      </div>
      <el-tabs v-model="activeTab">
        <!-- 仓库信息 Tab -->
        <el-tab-pane label="仓库信息" name="info">
          <el-descriptions v-if="warehouse" :column="3" border size="small">
            <el-descriptions-item label="仓库名称" :span="2">{{ warehouse.warehouseName }}</el-descriptions-item>
            <el-descriptions-item label="仓库编码">{{ warehouse.code }}</el-descriptions-item>
            <el-descriptions-item label="类型"><el-tag :type="warehouse.warehouseType==='FINISHED'?'success':'info'" size="small">{{ WarehouseTypeLabel[warehouse.warehouseType] || warehouse.warehouseType }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="地址" :span="2">{{ warehouse.address || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ warehouse.contact || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ warehouse.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ warehouse.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- 物料信息 Tab -->
        <el-tab-pane label="物料信息" name="material">
          <el-table :data="materials" border stripe v-loading="matLoading" size="small">
            <!-- 2026-10-10 用户口径：独立「物料类型」列与名称前缀内容重复 ⇒ 删列、只留前缀（空出的 130px 转给名称列） -->
            <el-table-column label="物料名称" min-width="290" show-overflow-tooltip>
              <template #default="{ row }">{{ $mLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="数量" width="120" align="right">
              <template #default="{row}"><span style="font-weight:600">{{ fmt(row.quantity) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="{row}"><el-button type="primary" link size="small" @click="goMaterialLog(row)">库存流水</el-button></template>
            </el-table-column>
          </el-table>
          <div v-if="materials.length===0" style="text-align:center;color:var(--app-text-secondary);padding:24px">暂无库存物料</div>
        </el-tab-pane>

        <!-- 产品信息 Tab -->
        <el-tab-pane label="产品信息" name="product">
          <el-table :data="products" border stripe v-loading="matLoading" size="small">
            <el-table-column prop="sku" label="SKU" width="130" />
            <el-table-column prop="productName" label="产品名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="A规" width="90" align="right">
          <template #default="{row}">
            <el-tag v-if="Number(row.qtyA)>0" type="success" size="small">{{ fmt(row.qtyA) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="B规" width="90" align="right">
          <template #default="{row}">
            <el-tag v-if="Number(row.qtyB)>0" type="warning" size="small">{{ fmt(row.qtyB) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="C规" width="90" align="right">
          <template #default="{row}">
            <el-tag v-if="Number(row.qtyC)>0" type="info" size="small">{{ fmt(row.qtyC) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="不良" width="90" align="right">
          <template #default="{row}">
            <el-tag v-if="Number(row.qtyDefect)>0" type="danger" size="small">{{ fmt(row.qtyDefect) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="待整理" width="90" align="right">
          <template #default="{row}">
            <el-tag v-if="Number(row.qtyPending)>0" type="primary" size="small" title="等待退货整理的库存">{{ fmt(row.qtyPending) }}</el-tag>
            <span v-else style="color:#999">0</span>
          </template>
        </el-table-column>
        <el-table-column label="总库存" width="100" align="right">
          <template #default="{row}"><span style="font-weight:600">{{ fmt(totalQty(row)) }}</span></template>
        </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="{row}"><el-button type="primary" link size="small" @click="goLog(row)">库存流水</el-button></template>
            </el-table-column>
          </el-table>
          <div v-if="products.length===0" style="text-align:center;color:var(--app-text-secondary);padding:24px">暂无库存产品</div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .detail-page 局部样式已删除 */

.toolbar { display:flex; justify-content:flex-end; margin-bottom:8px; }

</style>
