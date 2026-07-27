<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import * as XLSX from 'xlsx'

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
  try {
    const r = await request.get<any, any>('/dev/project/page', { params: { pageSize: 500 } })
    const list = r?.records || []
    list.forEach((p: any) => { projectMap.value[p.id] = p.name })
  } catch { /* ignore */ }
}

const PRIORITY_TYPES = ['玻璃', '驱动IC']

// 排序：玻璃/驱动IC > 无归属项目 > 有归属项目
const sortedMaterials = computed(() => {
  return [...materials.value].sort((a, b) => {
    const getOrder = (m: any) => {
      const type = m.materialType || ''
      const hasProject = !!(m.projectIds && m.projectIds.trim())
      if (PRIORITY_TYPES.includes(type)) return 0
      if (!hasProject) return 1
      return 2
    }
    return getOrder(a) - getOrder(b)
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
    rows.push([m.materialType || '', m.materialName || '', m.unit || '', m.qualityType || '良品', m.quantity ?? 0, getProjectNames(m.projectIds), m.remark || ''])
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
    const r = await request.get<any, any>(`/outsource/warehouse/by-factory/${warehouseId}`)
    // by-factory 返回的是仓库列表，取第一个（通常每个工厂只有一个默认仓库）
    // 但实际上这个接口是基于 factory_id 的，而 route 传的是 warehouse_id
    // 需要调整——从 page 接口获取单个仓库
    const res = await request.get<any, any>('/outsource/warehouse/page', { params: { pageSize: 100 } })
    const list = res?.records || []
    warehouse.value = list.find((w:any) => w.id === warehouseId) || null
  } finally { loading.value = false }
}

async function loadMaterials() {
  matLoading.value = true
  try {
    const r = await request.get<any, any>(`/outsource/stock/by-warehouse/${warehouseId}`)
    materials.value = r || []
  } finally { matLoading.value = false }
}



onMounted(() => { loadWarehouse(); loadMaterials(); loadProjects() })
</script>

<template>
  <div class="detail-page">
    <div class="page-header">
      <span class="page-title">{{ warehouse?.warehouseName || '委外仓库详情' }}</span>
    </div>

    <!-- 仓库基础信息 -->
    <el-card shadow="never" v-loading="loading">
      <template #header><span style="font-weight:600">仓库信息</span></template>
      <el-descriptions v-if="warehouse" :column="2" border size="small">
        <el-descriptions-item label="仓库名称" :span="2">{{ warehouse.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="所属加工厂">{{ warehouse.factoryName }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="warehouse.status===1?'success':'info'" size="small">{{ warehouse.status===1?'启用':'停用' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ warehouse.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ warehouse.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ warehouse.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ warehouse.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 物料列表 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <span style="font-weight:600">库存物料</span>
        <el-button type="primary" size="small" style="margin-left:12px" @click="exportExcel">导出</el-button>
      </template>
      <el-table :data="sortedMaterials" border stripe v-loading="matLoading" size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialType" label="物料类型" width="100" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column label="质量类型" width="90" align="center"><template #default="{row}"><el-tag :type="row.qualityType==='良品'?'success':'danger'" size="small">{{ row.qualityType || '良品' }}</el-tag></template></el-table-column>
        <el-table-column label="库存数量" width="110" align="right"><template #default="{row}"><span :style="{color: Number(row.quantity)<0?'#f56c6c':'',fontWeight:Number(row.quantity)<0?600:400}">{{ row.quantity }}</span></template></el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{row}"><el-button type="primary" link size="small" @click="router.push(`/outsource/material-history/${warehouseId}/${row.materialId}`)">详细</el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="sortedMaterials.length===0" style="text-align:center;color:#909399;padding:24px">暂无关联物料</div>
    </el-card>
  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
</style>
