<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const warehouseId = Number(route.params.id)
const warehouse = ref<any>(null)
const loading = ref(false)
const matLoading = ref(false)
const materials = ref<any[]>([])

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

function goBack() { router.push('/outsource/warehouse') }

onMounted(() => { loadWarehouse(); loadMaterials() })
</script>

<template>
  <div class="detail-page">
    <div class="page-header">
      <el-button @click="goBack">返回列表</el-button>
      <span class="page-title">{{ warehouse?.warehouseName || '仓库详情' }}</span>
    </div>

    <!-- 仓库基本信息 -->
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
      <template #header><span style="font-weight:600">库存物料</span></template>
      <el-table :data="materials" border stripe v-loading="matLoading" size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialType" label="物料类型" width="100" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="quantity" label="库存数量" width="100" align="right" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{row}"><el-button type="primary" link size="small" @click="router.push(`/outsource/material-history/${warehouseId}/${row.materialId}`)">详细</el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="materials.length===0" style="text-align:center;color:#909399;padding:24px">暂无关联物料</div>
    </el-card>
  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
</style>
