<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()
const tableLoading = ref(false)
const searchName = ref('')
const allProjects = ref<any[]>([])
const filteredProjects = ref<any[]>([])

function handleSearch() {
  const kw = searchName.value
  filteredProjects.value = kw ? allProjects.value.filter((p:any) =>
    p.name.includes(kw) || p.code.includes(kw)) : allProjects.value
}

async function loadProjects() {
  tableLoading.value = true
  try {
    const res = await request.get<any, any>('/dev/project/page', { params: { pageSize: 200 } })
    const projects = res?.records || []
    for (const p of projects) {
      const boms = await request.get<any, any>(`/dev/project/${p.id}/bom`)
      p.bomCount = (boms || []).length
    }
    allProjects.value = projects.filter((p:any) => p.bomCount > 0)
    filteredProjects.value = allProjects.value
  } finally { tableLoading.value = false }
}

function goToBom(projectId: number) { router.push(`/dev/project/edit/${projectId}?tab=bom`) }

onMounted(() => loadProjects())
</script>

<template>
  <div class="bom-page">
    <el-card shadow="never" class="table-card">
      <template #header><span style="font-weight:600">项目BOM总览</span></template>

      <div style="margin-bottom:12px">
        <el-input v-model="searchName" placeholder="搜索项目名称或编号" clearable style="width:280px" @input="handleSearch" />
      </div>

      <el-table :data="filteredProjects" border stripe v-loading="tableLoading">
        <el-table-column prop="code" label="项目编号" width="160" />
        <el-table-column prop="name" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="BOM物料数" width="110" align="center">
          <template #default="{row}"><el-tag type="primary" size="small">{{ row.bomCount }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" label="项目阶段" width="110" />
        <el-table-column label="操作" width="130" align="center">
          <template #default="{row}">
            <el-button type="primary" link @click="goToBom(row.id)">查看BOM</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.bom-page { display:flex; flex-direction:column; gap:12px; }
.table-card :deep(.el-card__body) { padding:16px; }
</style>
