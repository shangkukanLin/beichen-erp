<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { PhaseStatus, ProjectStatusLabel } from '@/api/enums'

const router = useRouter()
const tableLoading = ref(false)
const searchName = ref('')
const allProjects = ref<any[]>([])
const filteredProjects = ref<any[]>([])
const phaseMap = ref<Record<number, any[]>>({})

function handleSearch() {
  const kw = searchName.value
  filteredProjects.value = kw ? allProjects.value.filter((p:any) =>
    p.name.includes(kw) || p.code.includes(kw)) : allProjects.value
}

// 与项目列表页一致：显示当前进行中的阶段名；无进行中阶段时回退项目状态中文
function getCurrentPhase(row: any) {
  const phases = phaseMap.value[row.id]
  if (!phases || !phases.length) return row.status ? (ProjectStatusLabel[row.status] || row.status) : '-'
  const active = phases.find((t: any) => t.status === PhaseStatus.IN_PROGRESS)
  return active ? active.phaseName : (row.status ? (ProjectStatusLabel[row.status] || row.status) : '-')
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
    // 批量拉取项目阶段，推导当前阶段
    if (allProjects.value.length > 0) {
      try {
        const ids = allProjects.value.map((p:any) => p.id)
        const tlRes = await request.post<Record<number, any[]>>('/dev/project/batch-phases', ids)
        if (tlRes) phaseMap.value = tlRes
      } catch { /* ignore */ }
    }
  } catch (e: any) {
    ElMessage.error('项目BOM概览加载失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { tableLoading.value = false }
}

function goToBom(projectId: number) { router.push(`/dev/project/edit/${projectId}?tab=bom`) }
function handleRowClick(row: any) { goToBom(row.id) }

onMounted(() => loadProjects())

</script>

<template>
  <div class="bom-page">
    <el-card shadow="never" class="table-card">
      <template #header><span style="font-weight:600">项目BOM总览</span></template>

      <div style="margin-bottom:12px">
        <el-input v-model="searchName" placeholder="搜索项目名称或编号" clearable style="width:280px" @input="handleSearch" />
      </div>

      <el-table :data="filteredProjects" border stripe v-loading="tableLoading" @row-click="handleRowClick">
        <el-table-column label="项目编号" width="160">
          <template #default="{row}"><el-link type="primary" @click.stop="$router.push(`/dev/project/edit/${row.id}`)">{{ row.code }}</el-link></template>
        </el-table-column>
        <el-table-column prop="name" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="BOM物料数" width="110" align="center">
          <template #default="{row}"><el-tag type="primary" size="small">{{ row.bomCount }}</el-tag></template>
        </el-table-column>
        <el-table-column label="项目阶段" width="140" align="center">
          <template #default="{row}">
            <el-tag type="warning" size="small">{{ getCurrentPhase(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{row}">
            <el-button type="primary" link @click.stop="goToBom(row.id)">查看BOM</el-button>
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
