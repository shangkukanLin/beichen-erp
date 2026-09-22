<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import * as XLSX from 'xlsx'
import { QualityType, QualityTypeLabel } from '@/api/enums'

const route = useRoute()
const router = useRouter()
const warehouseId = Number(route.params.id)
const warehouse = ref<any>(null)
const loading = ref(false)
const matLoading = ref(false)
const materials = ref<any[]>([])
const projectMap = ref<Record<number, string>>({})

function getProjectNames(projectIds: string): string {
  if (!projectIds || !projectIds.trim()) return '-'
  return projectIds.split(',').filter(Boolean).map(id => {
    return projectMap.value[Number(id)] || `#${id}`
  }).join('、')
}

async function loadProjects() {
  const r = await request.get('/dev/project/page', { params: { pageSize: 9999, name: '' } })
  const map: Record<number, string> = {}
  ;(r?.records || []).forEach((p: any) => { map[p.id] = p.name })
  projectMap.value = map
}

/**
 * F7-132（2026-09-20）：排序优先级原按**中文类型名**硬编码（`PRIORITY_TYPES = ['玻璃','驱动IC']`）
 * ⇒ 类型一旦改名即静默失效（且同名类型在多租户下并不唯一）。现改为按物料类型的 **sortOrder** 判定
 * （现网：玻璃=1、驱动IC=2 ⇒ 行为完全不变），该值由 `/warehouse/stock/by-warehouse/{id}` 随行返回
 * （`materialTypeSortOrder`，本次一并补齐），前端不再依赖可改的展示名、也无需额外请求类型字典。
 */
const PRIORITY_SORT_ORDER_MAX = 2

/**
 * 负库存项（2026-09-17 F3）：委外仓允许"缺料强制出库"（收货领料走 force 口径）会形成负库存，
 * 但必须**显式可见**并说明成因，否则容易被误认为账错。
 */
const negativeItems = computed(() => materials.value.filter((m: any) => Number(m.quantity) < 0))

// 排序：优先类型（sortOrder ≤ PRIORITY_SORT_ORDER_MAX）> 无归属项目 > 有归属项目；同档内按 sortOrder 稳定排
const sortedMaterials = computed(() => {
  const soOf = (m: any) => (m.materialTypeSortOrder != null ? Number(m.materialTypeSortOrder) : 999)
  return [...materials.value].sort((a, b) => {
    const orderOf = (m: any) => {
      const hasProject = !!(m.projectIds && m.projectIds.trim())
      if (soOf(m) <= PRIORITY_SORT_ORDER_MAX) return 0
      if (!hasProject) return 1
      return 2
    }
    const diff = orderOf(a) - orderOf(b)
    return diff !== 0 ? diff : soOf(a) - soOf(b)
  })
})

function exportExcel() {
  const info = warehouse.value
  if (!info) return

  const now = new Date().toLocaleString('zh-CN')
  const cols = ['物料类型', '物料名称', '单位', '质量类型', '库存数量', '归属项目', '备注']
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
    rows.push([m.materialTypeName || '', m.materialName || '', m.unit || '', QualityTypeLabel[m.qualityType] || '良品', m.quantity ?? 0, getProjectNames(m.projectIds), m.remark || ''])
  })

  const ws = XLSX.utils.aoa_to_sheet(rows)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: 6 } }]
  ws['!cols'] = [{ wch: 14 }, { wch: 22 }, { wch: 8 }, { wch: 8 }, { wch: 12 }, { wch: 24 }, { wch: 20 }]

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



onMounted(() => { loadWarehouse(); loadMaterials(); loadProjects() })
</script>

<template>
  <div class="detail-page">
    <!-- 仓库基础信息 -->
    <el-card shadow="never" v-loading="loading">
      <template #header><span style="font-weight:600">仓库信息</span></template>
      <el-descriptions v-if="warehouse" :column="2" border size="small">
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
          <template #default="{row}"><el-button type="primary" link size="small" @click="router.push(`/outsource/material-history/${warehouseId}/${row.materialId}`)">详细</el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="sortedMaterials.length===0" style="text-align:center;color:var(--app-text-secondary);padding:24px">暂无关联物料</div>
    </el-card>
  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:12px; }

</style>
