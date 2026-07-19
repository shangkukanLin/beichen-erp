<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'

const route = useRoute(); const router = useRouter()
const warehouseId = Number(route.params.id)
const warehouse = ref<any>(null)
const loading = ref(false)
const matLoading = ref(false)
const materials = ref<any[]>([])

async function loadWarehouse() {
  loading.value = true
  try {
    const r = await request.get<any,any>('/inventory/warehouse/page', { params: { pageSize: 200 } })
    warehouse.value = (r?.records || []).find((w:any) => w.id === warehouseId) || null
  } finally { loading.value = false }
}

async function loadMaterials() {
  matLoading.value = true
  try {
    const r = await request.get<any,any>('/inventory/stock/page', { params: { pageSize: 500, warehouseId } })
    materials.value = r?.records || []
  } finally { matLoading.value = false }
}

function goLog(row: any) { router.push(`/inventory/warehouse/material-history/${warehouseId}/${row.materialId}`) }



onMounted(() => { loadWarehouse(); loadMaterials() })
</script>

<template>
  <div class="detail-page">
    <div class="page-header">
      <span class="page-title">{{ warehouse?.warehouseName || '仓库详情' }}</span>
    </div>

    <el-card shadow="never" v-loading="loading">
      <template #header><span style="font-weight:600">仓库信息</span></template>
      <el-descriptions v-if="warehouse" :column="2" border size="small">
        <el-descriptions-item label="仓库名称" :span="2">{{ warehouse.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="编码">{{ warehouse.code }}</el-descriptions-item>
        <el-descriptions-item label="类型"><el-tag :type="warehouse.warehouseType==='成品仓'?'success':warehouse.warehouseType==='不良仓'?'danger':'info'" size="small">{{ warehouse.warehouseType }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ warehouse.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ warehouse.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ warehouse.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ warehouse.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">库存物料</span></template>
      <el-table :data="materials" border stripe v-loading="matLoading" size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="productName" label="产品/物料" min-width="160" show-overflow-tooltip />
        <el-table-column label="库存数量" width="120" align="right">
          <template #default="{row}"><span :style="{color: Number(row.quantity)<0?'#f56c6c':'',fontWeight:Number(row.quantity)<0?600:400}">{{ row.quantity }}</span></template>
        </el-table-column>
        <el-table-column label="可用数量" width="100" align="right"><template #default="{row}">{{ row.availableQuantity || 0 }}</template></el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{row}"><el-button type="primary" link size="small" @click="goLog(row)">流水</el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="materials.length===0" style="text-align:center;color:#909399;padding:24px">暂无库存物料</div>
    </el-card>
  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
</style>
