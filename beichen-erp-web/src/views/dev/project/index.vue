<script setup lang="ts">
import { localDate } from '@/utils/date'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { PhaseStatus, ProjectStatus, ProjectStatusLabel, ProjectStatusTag, DEV_PROJECT_DIRTY_KEY } from '@/api/enums'
import {
  getProjectPage,
  getSupplierPage,
  type ProjectVO
} from '@/api/system'
import request from '@/utils/request'
import { useDomainRefresh } from '@/utils/dataFreshness'

const today = localDate()
const router = useRouter()

// ===================== 列表 + Tab =====================
const route = useRoute()
const activeTab = ref((route.query.tab as string) || 'active')
const query = reactive({ name: '', brandId: undefined as number | undefined })
const tableLoading = ref(false)
const allProjects = ref<ProjectVO[]>([])
const phaseMap = ref<Record<number, PhaseItem[]>>({})

interface PhaseItem { id?: number; phaseName: string; sortOrder: number; defaultDays?: number; plannedEnd?: string; actualEnd?: string; status?: string }

const activeProjects = ref<ProjectVO[]>([])
const finishedProjects = ref<ProjectVO[]>([])
const cancelledProjects = ref<ProjectVO[]>([])

function filterProjects() {
  activeProjects.value = allProjects.value.filter((p: any) => p.status === ProjectStatus.IN_PROGRESS)
  finishedProjects.value = allProjects.value.filter((p: any) => p.status === ProjectStatus.CLOSED)
  cancelledProjects.value = allProjects.value.filter((p: any) => p.status === ProjectStatus.CANCELLED)
}

function isOverdue(row: any) {
  const phases = phaseMap.value[row.id]
  if (!phases) return false
  const cur = phases.find((t: any) => t.status === PhaseStatus.IN_PROGRESS)
  return !!(cur && cur.plannedEnd && cur.plannedEnd < today && !cur.actualEnd)
}

function getCurrentPhase(row: any) {
  const phases = phaseMap.value[row.id]
  if (!phases || !phases.length) return '-'
  const active = phases.find((t: any) => t.status === PhaseStatus.IN_PROGRESS)
  return active ? active.phaseName : (row.status ? ProjectStatusLabel[row.status] || row.status : '-')
}

function getPlannedEnd(row: any) {
  const phases = phaseMap.value[row.id]
  if (!phases) return ''
  const active = phases.find((t: any) => t.status === PhaseStatus.IN_PROGRESS)
  return active?.plannedEnd || ''
}

async function loadPhases(projects: ProjectVO[]) {
  const ids = projects.map(p => p.id).filter(Boolean) as number[]
  if (ids.length === 0) return
  try {
    const res = await request.post<Record<number, PhaseItem[]>>('/dev/project/batch-phases', ids)
    if (res) phaseMap.value = { ...phaseMap.value, ...res }
  } catch (e: any) { console.warn('加载项目阶段失败', e?.message || e) }
}

async function loadData() {
  tableLoading.value = true
  try {
    const res = await getProjectPage({ name: query.name || undefined, brandId: query.brandId || undefined, pageSize: 100 })
    allProjects.value = res?.records || []
    filterProjects()
    await loadPhases(allProjects.value)
  } finally { tableLoading.value = false }
}

function handleQuery() { loadData() }
function handleReset() { query.name = ''; query.brandId = undefined; loadData() }

// ===================== 品牌下拉 =====================
const brandOptions = ref<{ id: number; brandName: string }[]>([])
async function loadBrandOptions() {
  try { brandOptions.value = (await request.get('/brand/enabled')) || [] } catch { /* 忽略 */ }
}

function handleAdd() { router.push('/dev/project/add') }
function handleEdit(row: any) { if (row.id) router.push(`/dev/project/edit/${row.id}`) }

async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确定取消项目「${row.name}」吗？`, '提示', { type: 'warning' })
    await request.put(`/dev/project/${row.id}/cancel`)
    ElMessage.success('项目已取消')
    loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleReactivate(row: any) {
  try {
    await ElMessageBox.confirm(`确定重新激活项目「${row.name}」吗？`, '提示', { type: 'info' })
    await request.put(`/dev/project/${row.id}/reactivate`)
    ElMessage.success('项目已重新激活')
    loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

// 新增/编辑页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
useDomainRefresh('devProject', () => {
    loadData()
}, DEV_PROJECT_DIRTY_KEY)
// 2026-09-30: 品牌筛选是本地下拉（/brand/enabled 进页面只拉一次）⇒ 品牌在别处新增/改名后，
// 回到本页要能拿到新数据；之前只在 onMounted 拉，keep-alive 下永远不更新。
useDomainRefresh('brand', loadBrandOptions)
onMounted(() => { loadData() })

</script>

<template>
  <div class="project-page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="项目名称"><el-input v-model="query.name" placeholder="项目名称" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="品牌"><el-select v-model="query.brandId" filterable clearable placeholder="全部" style="width:150px" @change="(v: any) => { if (v === ADD_MARKER) { query.brandId = undefined; $router.push('/inventory/brand'); return } handleQuery() }"><el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
      </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-change="()=>{}">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="进行中" name="active" />
        <el-tab-pane label="已结项" name="finished" />
        <el-tab-pane label="已取消" name="cancelled" />
      </el-tabs>

      <!-- 全部 -->
      <!-- 2026-09-16 用户要求「一行显示完、不要左右滑动」：列宽整体收紧（原合计 ≈1270px → ≈1015px） -->
      <el-table v-if="activeTab==='all'" :data="allProjects" border stripe v-loading="tableLoading" style="width:100%" @row-click="handleEdit">
        <!-- 2026-09-25 B1（实测驱动）：项目编码 120→**140**（需 139）、品牌 72→**108**（需 107）、
             项目名称 min100→132（长名仍可能省略号，已在守卫白名单登记）、显示/触摸方案 75→70、
             原机尺寸 70→64（同步 4 张表）。合计 ≤930，仍一行显示完。 -->
        <el-table-column prop="code" label="项目编码" width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="项目名称" min-width="132" show-overflow-tooltip />
        <el-table-column prop="brandName" label="品牌" width="108" show-overflow-tooltip />
        <el-table-column prop="displaySupplierName" label="显示方案" min-width="70" show-overflow-tooltip />
        <el-table-column prop="touchSupplierName" label="触摸方案" min-width="70" show-overflow-tooltip />
        <el-table-column prop="originalSize" label="原机" width="64" show-overflow-tooltip />
        <!-- 改配尺寸 = 改配信息里的「玻璃尺寸」(Project.glassSize)，2026-09-16 用户要求列表展示 -->
        <el-table-column prop="glassSize" label="改配" width="64" show-overflow-tooltip />
        <el-table-column label="项目阶段" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ getCurrentPhase(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="100" align="center">
          <template #default="{ row }">
            <span v-if="getPlannedEnd(row)" :style="{ color: isOverdue(row) ? 'var(--app-color-danger)' : 'var(--app-text-regular)', fontWeight: isOverdue(row) ? 'bold' : 'normal' }">
              {{ getPlannedEnd(row) }}
            </span>
            <span v-else style="color:var(--app-text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="112" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link size="small" @click.stop="handleEdit(row)">详情</el-button>
            <el-button v-if="row.status===ProjectStatus.IN_PROGRESS" type="danger" link size="small" @click.stop="handleCancel(row)">取消立项</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 进行中 -->
      <el-table v-if="activeTab==='active'" :data="activeProjects" border stripe v-loading="tableLoading" style="width:100%" :height="undefined" @row-click="handleEdit">
        <!-- 2026-09-25 B1（实测驱动）：项目编码 120→**140**（需 139）、品牌 72→**108**（需 107）、
             项目名称 min100→132（长名仍可能省略号，已在守卫白名单登记）、显示/触摸方案 75→70、
             原机尺寸 70→64（同步 4 张表）。合计 ≤930，仍一行显示完。 -->
        <el-table-column prop="code" label="项目编码" width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="项目名称" min-width="132" show-overflow-tooltip />
        <el-table-column prop="brandName" label="品牌" width="108" show-overflow-tooltip />
        <el-table-column prop="displaySupplierName" label="显示方案" min-width="70" show-overflow-tooltip />
        <el-table-column prop="touchSupplierName" label="触摸方案" min-width="70" show-overflow-tooltip />
        <el-table-column prop="originalSize" label="原机" width="64" show-overflow-tooltip />
        <!-- 改配尺寸 = 改配信息里的「玻璃尺寸」(Project.glassSize)，2026-09-16 用户要求列表展示 -->
        <el-table-column prop="glassSize" label="改配" width="64" show-overflow-tooltip />
        <el-table-column label="项目阶段" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ getCurrentPhase(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="100" align="center">
          <template #default="{ row }">
            <span v-if="getPlannedEnd(row)" :style="{ color: isOverdue(row) ? 'var(--app-color-danger)' : 'var(--app-text-regular)', fontWeight: isOverdue(row) ? 'bold' : 'normal' }">
              {{ getPlannedEnd(row) }}
            </span>
            <span v-else style="color:var(--app-text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="112" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link size="small" @click.stop="handleEdit(row)">详情</el-button>
            <el-button v-if="row.status===ProjectStatus.IN_PROGRESS" type="danger" link size="small" @click.stop="handleCancel(row)">取消立项</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已结项 -->
      <el-table v-if="activeTab==='finished'" :data="finishedProjects" border stripe v-loading="tableLoading" style="width:100%" @row-click="handleEdit">
        <!-- 2026-09-25 B1（实测驱动）：项目编码 120→**140**（需 139）、品牌 72→**108**（需 107）、
             项目名称 min100→132（长名仍可能省略号，已在守卫白名单登记）、显示/触摸方案 75→70、
             原机尺寸 70→64（同步 4 张表）。合计 ≤930，仍一行显示完。 -->
        <el-table-column prop="code" label="项目编码" width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="项目名称" min-width="132" show-overflow-tooltip />
        <el-table-column prop="brandName" label="品牌" width="108" show-overflow-tooltip />
        <el-table-column prop="displaySupplierName" label="显示方案" min-width="70" show-overflow-tooltip />
        <el-table-column prop="touchSupplierName" label="触摸方案" min-width="70" show-overflow-tooltip />
        <el-table-column prop="originalSize" label="原机" width="64" show-overflow-tooltip />
        <!-- 改配尺寸 = 改配信息里的「玻璃尺寸」(Project.glassSize)，2026-09-16 用户要求列表展示 -->
        <el-table-column prop="glassSize" label="改配" width="64" show-overflow-tooltip />
        <el-table-column label="状态" width="75" align="center"><template #default="{row}"><el-tag :type="ProjectStatusTag[row.status] || 'success'" size="small">{{ ProjectStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="85" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="success" link size="small" @click.stop="handleEdit(row as ProjectVO)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已取消 -->
      <el-table v-if="activeTab==='cancelled'" :data="cancelledProjects" border stripe v-loading="tableLoading" style="width:100%" @row-click="handleEdit">
        <!-- 2026-09-25 B1（实测驱动）：项目编码 120→**140**（需 139）、品牌 72→**108**（需 107）、
             项目名称 min100→132（长名仍可能省略号，已在守卫白名单登记）、显示/触摸方案 75→70、
             原机尺寸 70→64（同步 4 张表）。合计 ≤930，仍一行显示完。 -->
        <el-table-column prop="code" label="项目编码" width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="项目名称" min-width="132" show-overflow-tooltip />
        <el-table-column prop="brandName" label="品牌" width="108" show-overflow-tooltip />
        <el-table-column prop="displaySupplierName" label="显示方案" min-width="70" show-overflow-tooltip />
        <el-table-column prop="touchSupplierName" label="触摸方案" min-width="70" show-overflow-tooltip />
        <el-table-column prop="originalSize" label="原机" width="64" show-overflow-tooltip />
        <!-- 改配尺寸 = 改配信息里的「玻璃尺寸」(Project.glassSize)，2026-09-16 用户要求列表展示 -->
        <el-table-column prop="glassSize" label="改配" width="64" show-overflow-tooltip />
        <el-table-column label="状态" width="75" align="center"><template #default="{row}"><el-tag :type="ProjectStatusTag[row.status] || 'danger'" size="small">{{ ProjectStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="115" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="success" link size="small" @click.stop="handleEdit(row as ProjectVO)">详情</el-button>
            <el-button type="warning" link size="small" @click.stop="handleReactivate(row as ProjectVO)">重新激活</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

  </div>
</template>

<style scoped>
.project-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
</style>

