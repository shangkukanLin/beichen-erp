<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { TimelineStatus, ProjectStatus, ProjectStatusLabel, ProjectStatusTag } from '@/api/enums'
import {
  getProjectPage, addProject, updateProject,
  getSupplierPage,
  type ProjectVO, type ProjectDTO
} from '@/api/system'
import request from '@/utils/request'

const STATUS_LIST = ['立项', '排线图纸', '排线打样', 'FOG打样', '显示调试', '触摸调试', '背贴盖板打样', '总成样品', '测试', '小批量', '结项']
const today = new Date().toISOString().split('T')[0]
const router = useRouter()

// ===================== 列表 + Tab =====================
const route = useRoute()
const activeTab = ref((route.query.tab as string) || 'active')
const query = reactive({ name: '' })
const tableLoading = ref(false)
const allProjects = ref<ProjectVO[]>([])
const timelineMap = ref<Record<number, TimelineItem[]>>({})

interface TimelineItem { id?: number; statusName: string; sortOrder: number; defaultDays?: number; plannedEnd?: string; actualEnd?: string; status?: string }

const activeProjects = ref<ProjectVO[]>([])
const finishedProjects = ref<ProjectVO[]>([])
const cancelledProjects = ref<ProjectVO[]>([])

function filterProjects() {
  activeProjects.value = allProjects.value.filter((p: any) => p.status === ProjectStatus.IN_PROGRESS)
  finishedProjects.value = allProjects.value.filter((p: any) => p.status === ProjectStatus.CLOSED)
  cancelledProjects.value = allProjects.value.filter((p: any) => p.status === ProjectStatus.CANCELLED)
}

function isOverdue(row: any) {
  const timelines = timelineMap.value[row.id]
  if (!timelines) return false
  const cur = timelines.find((t: any) => t.status === TimelineStatus.IN_PROGRESS)
  return !!(cur && cur.plannedEnd && cur.plannedEnd < today && !cur.actualEnd)
}

function getCurrentPhase(row: any) {
  const timelines = timelineMap.value[row.id]
  if (!timelines || !timelines.length) return '-'
  const active = timelines.find((t: any) => t.status === TimelineStatus.IN_PROGRESS)
  return active ? active.statusName : (row.status ? ProjectStatusLabel[row.status] || row.status : '-')
}

function getPlannedEnd(row: any) {
  const timelines = timelineMap.value[row.id]
  if (!timelines) return ''
  const active = timelines.find((t: any) => t.status === TimelineStatus.IN_PROGRESS)
  return active?.plannedEnd || ''
}

async function loadTimelines(projects: ProjectVO[]) {
  const ids = projects.map(p => p.id).filter(Boolean) as number[]
  if (ids.length === 0) return
  try {
    const res = await request.post<Record<number, TimelineItem[]>>('/dev/project/batch-timelines', ids)
    if (res) timelineMap.value = { ...timelineMap.value, ...res }
  } catch (e: any) { console.warn('加载时间线失败', e?.message || e) }
}

async function loadData() {
  tableLoading.value = true
  try {
    const res = await getProjectPage({ name: query.name || undefined, pageSize: 100 })
    allProjects.value = res?.records || []
    filterProjects()
    await loadTimelines(allProjects.value)
  } finally { tableLoading.value = false }
}

function handleQuery() { loadData() }
function handleReset() { query.name = ''; loadData() }

// ===================== 方案公司下拉 =====================
const solutionSuppliers = ref<{ id: number; name: string }[]>([])
const factoryOptions = ref<{ id: number; name: string }[]>([])
const fetchFactorySuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, supplierType: 'factory', name: kw } })
async function loadSolutionSuppliers() {
  try {
    const res = await getSupplierPage({ supplierType: 'solution', pageSize: 200 })
    solutionSuppliers.value = (res?.records || []).map((s: any) => ({ id: s.id, name: s.name }))
  } catch (e: any) { console.warn('加载方案商失败', e?.message || e) }
}
async function loadFactories() {
  try {
    const res = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } })
    factoryOptions.value = (res?.records || []).map((s: any) => ({ id: s.id, name: s.name }))
  } catch (e: any) { console.warn('加载工厂失败', e?.message || e) }
}

// ===================== 新增/编辑 =====================
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const defForm = (): ProjectDTO => ({
  name: '', displaySupplierName: '', touchSupplierName: '',
  assemblyName: '',
  adaptModel: '',
  originalSize: '', originalResolution: '', startDate: '', expectedEndDate: '', status: '立项', remark: '',
  sampleFactoryId: undefined, outsourceFactoryId: undefined
})
const form = reactive<ProjectDTO>(defForm())
const isEdit = ref(false)
const rules: FormRules = { name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }], assemblyName: [{ required: true, message: '请输入总成名称', trigger: 'blur' }] }

function handleAdd() { router.push('/dev/project/add') }
function handleEdit(row: any) { if (row.id) router.push(`/dev/project/edit/${row.id}`) }

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value && form.id) { await updateProject(form); ElMessage.success('修改成功') }
      else { await addProject(form); ElMessage.success('新增成功') }
      dialogVisible.value = false; loadData()
    } finally { submitLoading.value = false }
  })
}

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

onMounted(() => { loadData(); loadSolutionSuppliers(); loadFactories() })

</script>

<template>
  <div class="project-page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="名称"><el-input v-model="query.name" placeholder="项目名称" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button><el-button type="success" @click="handleAdd">新增</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-change="()=>{}">
        <el-tab-pane label="进行中" name="active" />
        <el-tab-pane label="已结项" name="finished" />
        <el-tab-pane label="已取消" name="cancelled" />
      </el-tabs>

      <!-- 进行中 -->
      <el-table v-if="activeTab==='active'" :data="activeProjects" border stripe v-loading="tableLoading" style="width:100%" size="default" :height="undefined">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="code" label="编号" width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="项目名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="assemblyName" label="总成名称" width="110" show-overflow-tooltip />
        <el-table-column prop="displaySupplierName" label="显示方案" min-width="90" show-overflow-tooltip />
        <el-table-column prop="touchSupplierName" label="触摸方案" min-width="90" show-overflow-tooltip />
        <el-table-column prop="originalSize" label="原机尺寸" width="90" show-overflow-tooltip />
        <el-table-column label="项目阶段" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ getCurrentPhase(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="120" align="center">
          <template #default="{ row }">
            <span v-if="getPlannedEnd(row)" :style="{ color: isOverdue(row) ? 'var(--app-color-danger)' : 'var(--app-text-regular)', fontWeight: isOverdue(row) ? 'bold' : 'normal' }">
              {{ getPlannedEnd(row) }}
            </span>
            <span v-else style="color:var(--app-text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link size="small" @click="handleEdit(row)">详细</el-button>
            <el-button v-if="row.status===ProjectStatus.IN_PROGRESS" type="danger" link size="small" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已结项 -->
      <el-table v-if="activeTab==='finished'" :data="finishedProjects" border stripe v-loading="tableLoading" style="width:100%" size="default">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="code" label="编号" width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="displaySupplierName" label="显示方案" min-width="90" show-overflow-tooltip />
        <el-table-column prop="touchSupplierName" label="触摸方案" min-width="90" show-overflow-tooltip />
        <el-table-column prop="originalSize" label="原机尺寸" width="90" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center"><template #default="{row}"><el-tag :type="ProjectStatusTag[row.status] || 'success'" size="small">{{ ProjectStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="success" link size="small" @click="handleEdit(row as ProjectVO)">详细</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已取消 -->
      <el-table v-if="activeTab==='cancelled'" :data="cancelledProjects" border stripe v-loading="tableLoading" style="width:100%" size="default">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="code" label="编号" width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="displaySupplierName" label="显示方案" min-width="90" show-overflow-tooltip />
        <el-table-column prop="touchSupplierName" label="触摸方案" min-width="90" show-overflow-tooltip />
        <el-table-column prop="originalSize" label="原机尺寸" width="90" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center"><template #default="{row}"><el-tag :type="ProjectStatusTag[row.status] || 'danger'" size="small">{{ ProjectStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="success" link size="small" @click="handleEdit(row as ProjectVO)">详细</el-button>
            <el-button type="warning" link size="small" @click="handleReactivate(row as ProjectVO)">重新激活</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="660px" :close-on-click-modal="false" top="3vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="14"><el-form-item label="项目名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="10"><el-form-item label="总成名称"><el-input v-model="form.assemblyName" /></el-form-item></el-col>
          <el-col :span="14"><el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option v-for="s in STATUS_LIST" :key="s" :label="s" :value="s" /></el-select></el-form-item></el-col>
          <el-col :span="10"></el-col>
          <el-col :span="12"><el-form-item label="显示方案"><el-select v-model="form.displaySupplierName" filterable allow-create style="width:100%"><el-option v-for="s in solutionSuppliers" :key="s.id" :label="s.name" :value="s.name" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="触摸方案"><el-select v-model="form.touchSupplierName" filterable allow-create style="width:100%"><el-option v-for="s in solutionSuppliers" :key="s.id" :label="s.name" :value="s.name" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="适配机型"><el-input v-model="form.adaptModel" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="原机尺寸"><el-input v-model="form.originalSize" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="原分辨率"><el-input v-model="form.originalResolution" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="打样工厂">
            <RemoteSelect v-model="form.sampleFactoryId" :fetch="fetchFactorySuppliers" clearable placeholder="选择工厂" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="委外工厂">
            <RemoteSelect v-model="form.outsourceFactoryId" :fetch="fetchFactorySuppliers" clearable placeholder="选择工厂" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="立项日期"><el-input v-model="form.startDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="预计完成"><el-input v-model="form.expectedEndDate" type="date" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>

  </div>
</template>

<style scoped>
.project-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
</style>

