<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import * as XLSX from 'xlsx'
import { QualityType, QualityTypeLabel } from '@/api/enums'
import PageShell from '@/components/PageShell.vue'
import StockAmountBar from '@/components/StockAmountBar.vue'

/** 库存形态（2026-09-25 P0-3）：与 /warehouse/stock/by-warehouse 返回的 stockForm 对应 */
const StockFormLabel: Record<string, string> = {
  MATERIAL: '物料',
  PRODUCT_DEFECT: '成品（加工退货）',
  PRODUCT_REPAIR: '成品（维修退货）',
  MATERIAL_REPAIR: '物料（送修在厂）',
}

const route = useRoute()
const router = useRouter()
const warehouseId = Number(route.params.id)
const warehouse = ref<any>(null)
const loading = ref(false)
const matLoading = ref(false)
const materials = ref<any[]>([])
// 2026-10-09（§7.26 幽灵字段）：删除 projectMap / getProjectNames / loadProjects —— 它们只服务于
// 「归属项目」，而该字段已随 OutsourceMaterial 下线（接口不再返回）⇒ 留着就是永远走不到的死代码 ✗。
// （顺带少一次无用的 /dev/project/page 全量请求。）

/**
 * F7-132（2026-09-20）：排序优先级原按**中文类型名**硬编码（`PRIORITY_TYPES = ['玻璃','驱动IC']`）
 * ⇒ 类型一旦改名即静默失效（且同名类型在多租户下并不唯一）。现改为按物料类型的 **sortOrder** 判定
 * （现网：玻璃=1、驱动IC=2 ⇒ 行为完全不变），该值由 `/warehouse/stock/by-warehouse/{id}` 随行返回
 * （`materialTypeSortOrder`，本次一并补齐），前端不再依赖可改的展示名、也无需额外请求类型字典。
 */
const PRIORITY_SORT_ORDER_MAX = 2

/**
 * 成品行（productId 非空）= 退回在厂成品，**不进**「库存物料」表。
 * 2026-09-25 修正：`by-warehouse` 返回的是**整仓全行**（物料行 + 成品行），
 * 原实现直接把全行喂给「库存物料」表 ⇒ 成品行在表里显示为"材料类型/名称/单位全空"的空行，看着像脏数据。
 */
const materialRows = computed(() => (materials.value as any[]).filter((m) => m.materialId != null))

/**
 * 退回成品（加工退货/维修退货）在厂库存（2026-09-25 P0-3 新增展示）。
 * 2026-09-25 修正：只列**有数量**的行 —— 审核→反审核成对回滚后库存归零的行仍留在库存表（咽喉扣到 0 不删行），
 * 数量 0 不是"在厂库存"，列出会让人误以为还有货。
 */
const productStocks = computed(() =>
  (materials.value as any[])
    .filter((m) => m.productId != null && m.stockForm && m.stockForm !== 'MATERIAL' && Number(m.quantity) !== 0)
    .sort((a: any, b: any) => Number(b.quantity) - Number(a.quantity))
)

/**
 * 负库存项（2026-09-17 F3）：委外仓允许"缺料强制出库"（收货领料走 force 口径）会形成负库存，
 * 但必须**显式可见**并说明成因，否则容易被误认为账错。仅物料行（提示文案针对"物料缺料"）。
 */
const negativeItems = computed(() => materialRows.value.filter((m: any) => Number(m.quantity) < 0))

// 排序：优先类型（sortOrder ≤ PRIORITY_SORT_ORDER_MAX）置顶，其余按 sortOrder 稳定排。
// 2026-10-09（§7.26 幽灵字段）：原先还有"无归属项目 > 有归属项目"这一档，但 `projectIds` 已随
// OutsourceMaterial 于 2026-09-21 下线、本接口也不再返回 ⇒ 该分支**恒落在同一档**（死逻辑），已删除。
const sortedMaterials = computed(() => {
  const soOf = (m: any) => (m.materialTypeSortOrder != null ? Number(m.materialTypeSortOrder) : 999)
  return [...materialRows.value].sort((a, b) => {
    const tierOf = (m: any) => (soOf(m) <= PRIORITY_SORT_ORDER_MAX ? 0 : 1)
    const diff = tierOf(a) - tierOf(b)
    return diff !== 0 ? diff : soOf(a) - soOf(b)
  })
})

function exportExcel() {
  const info = warehouse.value
  if (!info) return

  const now = new Date().toLocaleString('zh-CN')
  // 2026-10-09（§7.26）：去掉「归属项目」列 —— 该字段已下线（接口不再返回，取值恒为 '-'）
  const cols = ['物料类型', '物料名称', '单位', '质量类型', '库存数量', '备注']
  const rows: any[][] = [
    [`库存物料清单 - ${info.warehouseName || ''}`],
    [`仓库名称：${info.warehouseName || '-'}`],
    [`所属加工厂：${info.factoryName || '-'}`],
    [`地址：${info.address || '-'}`],
    [`联系人：${info.contact || '-'}    电话：${info.phone || '-'}`],
    [`导出时间：${now}`],
    [],
    cols,
  ]
  sortedMaterials.value.forEach(m => {
    rows.push([m.materialTypeName || '', m.materialName || '', m.unit || '', QualityTypeLabel[m.qualityType] || '良品', m.quantity ?? 0, m.remark || ''])
  })

  const ws = XLSX.utils.aoa_to_sheet(rows)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: 5 } }]
  ws['!cols'] = [{ wch: 14 }, { wch: 22 }, { wch: 8 }, { wch: 8 }, { wch: 12 }, { wch: 20 }]

  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '库存物料')
  XLSX.writeFile(wb, `${info.warehouseName || '仓库'}_库存物料.xlsx`)
}

async function loadWarehouse() {
  loading.value = true
  try {
    // F7-131（2026-09-20）：原实现有三处问题 ——
    //  ① 先请求 `/warehouse/by-factory/${warehouseId}`，但该接口按 **factoryId** 过滤（这里传的却是
    //     warehouseId）⇒ 参数语义错，且返回值 `r` 被直接丢弃、从未使用（白白多一次查询）；
    //  ② 改拉 `/warehouse/page?pageSize=100` 再在前端 find ⇒ **仓库总数超过 100 时找不到 ⇒ 基础信息区静默空白**；
    //  ③ 原注释自己写着"需要调整——从 page 接口获取单个仓库"。
    // 现改为后端新增的按 id 精确查询（返回结构与列表每一行完全一致）。
    warehouse.value = await request.get<any, any>(`/warehouse/${warehouseId}`)
  } finally { loading.value = false }
}

async function loadMaterials() {
  matLoading.value = true
  try {
    const r = await request.get<any, any>(`/warehouse/stock/by-warehouse/${warehouseId}`)
    materials.value = r?.records || r?.data || r || []
  } finally { matLoading.value = false }
}



onMounted(() => { loadWarehouse(); loadMaterials() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta)；本页只读（导出按钮留在卡内） -->
  <PageShell :loading="loading" back-fallback="/outsource/warehouse">
    <!-- 2026-10-09 用户需求：本仓库存金额（委外仓同样要；口径与物料/成品库存列表页同源） -->
    <StockAmountBar :warehouse-id="warehouseId" />
    <!-- 仓库基础信息 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">仓库信息</span></template>
      <el-descriptions v-if="warehouse" :column="3" border size="small">
        <el-descriptions-item label="仓库名称" :span="2">{{ warehouse.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="所属加工厂">{{ warehouse.factoryName }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="warehouse.status===1?'success':'info'" size="small">{{ warehouse.status===1?'启用':'停用' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ warehouse.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ warehouse.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ warehouse.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ warehouse.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 物料列表 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <span style="font-weight:600">库存物料</span>
        <el-button type="primary" size="small" style="margin-left:12px" @click="exportExcel">导出</el-button>
      </template>
      <!-- 负库存提示（2026-09-17 F3）：委外仓可因"缺料强制出库"出现负数，需显式说明成因 -->
      <el-alert v-if="negativeItems.length" type="warning" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs)">
            本仓有 {{ negativeItems.length }} 项物料为负库存（{{ negativeItems.map((m: any) => m.materialName).slice(0, 5).join('、') }}{{ negativeItems.length > 5 ? ' 等' : '' }}）——
            由收货领料时确认「继续出库（物料将变为负数）」形成，属缺料未补、非账错，请尽快补料。
          </span>
        </template>
      </el-alert>
      <el-table :data="sortedMaterials" border stripe v-loading="matLoading" size="small">
        <el-table-column prop="materialTypeName" label="物料类型" width="100" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <!-- 2026-09-25 物料形态化：送修在厂行（MATERIAL_REPAIR）与常规物料行并存，tag 区分 -->
        <el-table-column label="形态" width="110" align="center">
          <template #default="{row}">
            <el-tag v-if="row.stockForm && row.stockForm !== 'MATERIAL'" type="warning" size="small">{{ StockFormLabel[row.stockForm] || row.stockForm }}</el-tag>
            <span v-else style="color:var(--app-text-placeholder)">常规</span>
          </template>
        </el-table-column>
        <el-table-column label="质量类型" width="90" align="center"><template #default="{row}"><el-tag :type="row.qualityType===QualityType.DEFECT?'danger':'success'" size="small">{{ QualityTypeLabel[row.qualityType] || '良品' }}</el-tag></template></el-table-column>
        <el-table-column label="库存数量" width="130" align="right">
          <template #default="{row}">
            <span :style="{color: Number(row.quantity)<0?'var(--app-color-danger)':'',fontWeight:Number(row.quantity)<0?600:400}">{{ row.quantity }}</span>
            <!-- 负库存显式标注（2026-09-17 F3）：本仓允许"缺料强制出库"，负数是缺料未补，不是账错 -->
            <el-tooltip v-if="Number(row.quantity)<0" content="负库存＝收货领料时确认「继续出库（物料将变为负数）」形成，待补料后转正；建议尽快补料" placement="top">
              <el-tag type="danger" size="small" style="margin-left:4px">缺料</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{row}"><el-button type="primary" link size="small" @click="router.push(`/outsource/material-history/${warehouseId}/${row.materialId}`)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="sortedMaterials.length===0" style="text-align:center;color:var(--app-text-secondary);padding:24px">暂无关联物料</div>
    </el-card>

    <!-- 2026-09-25 P0-3：退回成品在厂库存（加工退货 / 维修退货 同表以「形态」列区分；两者责任方不同不可混账）。
         行来源 = 本仓 productId 非空且形态非 MATERIAL 的库存行；数量为 0 的行（成对回滚残留）不展示。 -->
    <el-card v-if="productStocks.length" shadow="never" class="table-card" style="margin-top:12px">
      <template #header><span style="font-weight:600">退回成品（在厂）</span></template>
      <el-table :data="productStocks" border stripe size="small">
        <el-table-column label="形态" width="150" align="center">
          <template #default="{row}"><el-tag :type="row.stockForm==='PRODUCT_DEFECT'?'danger':'warning'" size="small">{{ StockFormLabel[row.stockForm] || row.stockForm }}</el-tag></template>
        </el-table-column>
        <el-table-column label="产品" min-width="160"><template #default="{row}">{{ row.productName || ('#' + row.productId) }}</template></el-table-column>
        <el-table-column label="规格" width="90" align="center"><template #default="{row}">{{ row.qualityType || '-' }}</template></el-table-column>
        <el-table-column label="数量" width="110" align="right"><template #default="{row}">{{ row.quantity }}</template></el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .detail-page 局部样式已删除 */

</style>
