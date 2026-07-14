<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getProjectPage, addProject, updateProject, deleteProject, updateProjectStatus,
  getProjectBom, saveProjectBom,
  getProjectBugs, addProjectBug, updateProjectBug, deleteProjectBug,
  getProjectDrawings, addProjectDrawing, deleteProjectDrawing,
  getSupplierPage,
  type ProjectVO, type ProjectDTO, type BomDTO, type BugDTO, type DrawingVO
} from '@/api/system'
import request from '@/utils/request'

const STATUS_LIST = ['立项', '排线图纸', '排线打样', 'FOG打样', '显示调试', '触摸调试', '背贴盖板打样', '总成样品', '测试', '小批量', '结项']
const today = new Date().toISOString().split('T')[0]
const router = useRouter()

// ===================== 列表 + Tab =====================
const activeTab = ref('active')
const query = reactive({ name: '' })
const tableLoading = ref(false)
const allProjects = ref<ProjectVO[]>([])
const timelineMap = ref<Record<number, TimelineItem[]>>({})

interface TimelineItem { statusName: string; sortOrder: number; plannedEnd?: string; actualEnd?: string; status?: string }
const timelineStatusOptions = ['未完成', '进行中', '已完成']

const activeProjects = ref<ProjectVO[]>([])
const finishedProjects = ref<ProjectVO[]>([])

function filterProjects() {
  activeProjects.value = allProjects.value.filter(p => p.status !== '结项')
  finishedProjects.value = allProjects.value.filter(p => p.status === '结项')
}

function isOverdue(project: ProjectVO) {
  const timelines = timelineMap.value[project.id as number]
  if (!timelines) return false
  const cur = timelines.find(t => t.statusName === project.status)
  return !!(cur && cur.plannedEnd && cur.plannedEnd < today && !cur.actualEnd)
}

function getPlannedEnd(project: ProjectVO) {
  const timelines = timelineMap.value[project.id as number]
  if (!timelines) return ''
  const cur = timelines.find(t => t.statusName === project.status)
  return cur?.plannedEnd || ''
}

async function loadTimelines(projects: ProjectVO[]) {
  const ids = projects.map(p => p.id).filter(Boolean) as number[]
  if (ids.length === 0) return
  try {
    const res = await request.post<unknown, Record<number, TimelineItem[]>>('/dev/project/timelines/batch', { projectIds: ids })
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
function handleEdit(row: ProjectVO) { router.push(`/dev/project/edit/${row.id}`) }

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value && form.id) { await updateProject(form); ElMessage.success('修改成功') }
      else { await addProject(form); ElMessage.success('新增成功') }
      dialogVisible.value = false; loadData()
    } finally { submitLoading.value = false }
  })
}

async function handleDelete(row: ProjectVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '提示', { type: 'warning' }); await deleteProject(row.id!); ElMessage.success('删除成功'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleStatusChange(row: ProjectVO, newStatus: string) {
  await updateProjectStatus(row.id!, newStatus)
  ElMessage.success('状态已更新'); loadData()
}

// ===================== 时间线 =====================
const timelineVisible = ref(false)
const timelinePid = ref<number>()
const timelineList = ref<TimelineItem[]>([])

async function loadTimelineDetail(projectId: number) {
  const res = await request.get<unknown, TimelineItem[]>(`/dev/project/${projectId}/timeline`)
  timelineList.value = res || []
}

async function saveTimeline() {
  if (!timelinePid.value) return
  await request.put(`/dev/project/${timelinePid.value}/timeline`, timelineList.value)
  ElMessage.success('时间线已保存'); timelineVisible.value = false; loadData()
}

function openTimeline(row: ProjectVO) {
  timelinePid.value = row.id as number
  loadTimelineDetail(row.id as number).then(() => { timelineVisible.value = true })
}

// ===================== 详情(BOM/BUG/图纸) =====================
const detailVisible = ref(false)
const detailProject = ref<ProjectVO | null>(null)
const detailTab = ref('bom')
const bomList = ref<BomDTO[]>([])
const bomTypes = ref<string[]>([])
const allMaterials = ref<any[]>([])
async function loadBomTypes() {
  try { const res = await request.get<any, any>('/dev/bom-type/enabled'); bomTypes.value = (res || []).map((t:any) => t.typeName) } catch (e: any) { console.warn('加载BOM类型失败', e?.message || e) }
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); allMaterials.value = (r?.records || []) } catch (e: any) { console.warn('加载物料失败', e?.message || e) }
}
function getMaterialsByType(type: string) { return allMaterials.value.filter((m:any) => m.materialType === type) }
async function loadBom() { if (detailProject.value) bomList.value = (await getProjectBom(detailProject.value.id!))?.map(b => ({ materialName: b.materialName, spec: b.spec, unit: b.unit, quantityPerSet: b.quantityPerSet, lossRate: b.lossRate, materialType: b.materialType, remark: b.remark })) || [] }
function addBomRow() { bomList.value.push({ materialName: '', spec: '', unit: '', quantityPerSet: 1, lossRate: 2, materialType: '', remark: '' }) }
function removeBomRow(i: number) { bomList.value.splice(i, 1) }
async function saveBom() { if (detailProject.value) { const emptyType = bomList.value.find((b: any) => !b.materialType || !b.materialType.trim()); if (emptyType) { ElMessage.warning('物料类型不能为空'); return }; const emptyName = bomList.value.find((b: any) => !b.materialName || !b.materialName.trim()); if (emptyName) { ElMessage.warning('物料名称不能为空'); return }; const zeroQty = bomList.value.find((b: any) => !b.quantityPerSet || Number(b.quantityPerSet) <= 0); if (zeroQty) { ElMessage.warning('物料用量必须大于0'); return }; await saveProjectBom(detailProject.value.id!, bomList.value); ElMessage.success('BOM已保存'); loadBom() } }

const bugList = ref<BugDTO[]>([]); const bugDialogVisible = ref(false)
const bugForm = reactive<BugDTO>({ title: '', severity: '一般', status: '待处理', description: '', assignedTo: undefined }); const isBugEdit = ref(false)
async function loadBugs() { if (detailProject.value) bugList.value = (await getProjectBugs(detailProject.value.id!)) || [] }
function handleAddBug() { Object.assign(bugForm, { title: '', severity: '一般', status: '待处理', description: '', assignedTo: undefined }); isBugEdit.value = false; bugDialogVisible.value = true }
function handleEditBug(row: BugDTO) { Object.assign(bugForm, row); isBugEdit.value = true; bugDialogVisible.value = true }
async function handleBugSubmit() { if (!detailProject.value) return; if (isBugEdit.value && bugForm.id) { await updateProjectBug(detailProject.value.id!, bugForm); ElMessage.success('已更新') } else { await addProjectBug(detailProject.value.id!, bugForm); ElMessage.success('已添加') }; bugDialogVisible.value = false; loadBugs() }
async function handleDeleteBug(row: BugDTO) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await deleteProjectBug(detailProject.value!.id!, row.id!); ElMessage.success('已删除'); loadBugs() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

const drawingList = ref<DrawingVO[]>([])
async function loadDrawings() { if (detailProject.value) drawingList.value = (await getProjectDrawings(detailProject.value.id!)) || [] }
const drawingForm = reactive({ docName: '', docType: '图纸', version: '', fileUrl: '' }); const drawingVisible = ref(false)
function handleAddDrawing() { Object.assign(drawingForm, { docName: '', docType: '图纸', version: '', fileUrl: '' }); drawingVisible.value = true }
async function handleDrawingSubmit() { if (detailProject.value) { await addProjectDrawing(detailProject.value.id!, drawingForm as any); ElMessage.success('已添加'); drawingVisible.value = false; loadDrawings() } }
async function handleDeleteDrawing(row: DrawingVO) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await deleteProjectDrawing(detailProject.value!.id!, row.id!); ElMessage.success('已删除'); loadDrawings() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

async function openDetail(row: ProjectVO) { detailProject.value = row; detailTab.value = 'bom'; detailVisible.value = true; loadBom(); loadBugs(); loadDrawings() }

onMounted(() => { loadData(); loadSolutionSuppliers(); loadFactories(); loadBomTypes() })
onActivated(() => { loadData(); loadSolutionSuppliers(); loadFactories(); loadBomTypes() })
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
        <el-table-column label="项目阶段" min-width="120" align="center">
          <template #default="{ row }">
            <el-select :model-value="row.status" size="small" style="width:100%" @change="(v:string)=>handleStatusChange(row as ProjectVO, v)">
              <el-option v-for="s in STATUS_LIST" :key="s" :label="s" :value="s" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="120" align="center">
          <template #default="{ row }">
            <span v-if="getPlannedEnd(row as ProjectVO)" :style="{ color: isOverdue(row as ProjectVO) ? '#f56c6c' : '#606266', fontWeight: isOverdue(row as ProjectVO) ? 'bold' : 'normal' }">
              {{ getPlannedEnd(row as ProjectVO) }}
            </span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link size="small" @click="handleEdit(row as ProjectVO)">详细</el-button>
            <el-button type="warning" link size="small" @click="openTimeline(row as ProjectVO)">时间线</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row as ProjectVO)">删除</el-button>
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
        <el-table-column label="状态" width="80" align="center"><template #default="{row}"><el-tag type="success" size="small">{{row.status}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link size="small" @click="openDetail(row as ProjectVO)">详情</el-button>
            <el-button type="success" link size="small" @click="handleEdit(row as ProjectVO)">详细</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row as ProjectVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 弹窗略（新增/编辑/时间线/详情/BUG/图纸保持不变） -->
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
            <el-select v-model="form.sampleFactoryId" clearable filterable style="width:100%" placeholder="选择工厂"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="委外工厂">
            <el-select v-model="form.outsourceFactoryId" clearable filterable style="width:100%" placeholder="选择工厂"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="立项日期"><el-input v-model="form.startDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="预计完成"><el-input v-model="form.expectedEndDate" type="date" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>

    <el-dialog v-model="timelineVisible" title="状态时间线" width="600px">
      <el-table :data="timelineList" border size="small">
        <el-table-column prop="statusName" label="状态" width="140" />
        <el-table-column label="计划完成" width="160"><template #default="{row}"><el-input v-model="row.plannedEnd" type="date" size="small" /></template></el-table-column>
        <el-table-column label="实际完成" width="160"><template #default="{row}"><el-input v-model="row.actualEnd" type="date" size="small" /></template></el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{row}">
            <el-select v-model="row.status" size="small" style="width:100%">
              <el-option v-for="o in timelineStatusOptions" :key="o" :label="o" :value="o" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="timelineVisible=false">取消</el-button><el-button type="primary" @click="saveTimeline">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="'项目详情: '+(detailProject?.name||'')" width="900px" :close-on-click-modal="false">
      <el-tabs v-model="detailTab">
        <el-tab-pane label="BOM物料清单" name="bom">
          <el-button type="primary" size="small" @click="addBomRow" style="margin-bottom:8px">+ 添加物料</el-button>
          <el-button type="success" size="small" @click="saveBom" style="margin-left:8px">保存</el-button>
          <el-table :data="bomList" border size="small">
            <el-table-column label="类型" width="100">
              <template #default="{row}">
                <el-select v-model="row.materialType" size="small" style="width:100%" @change="row.materialName = ''">
                  <el-option v-for="t in bomTypes" :key="t" :label="t" :value="t" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="物料名称" min-width="130"><template #default="{row}"><el-select v-model="row.materialName" size="small" filterable allow-create clearable style="width:100%" placeholder="选择"><el-option v-for="m in getMaterialsByType(row.materialType || '')" :key="m.id" :label="m.materialName" :value="m.materialName" /></el-select></template></el-table-column>
            <el-table-column label="规格" width="100"><template #default="{row}"><el-input v-model="row.spec" size="small" /></template></el-table-column>
            <el-table-column label="单位" width="70"><template #default="{row}"><el-input v-model="row.unit" size="small" /></template></el-table-column>
            <el-table-column label="用量" width="80"><template #default="{row}"><el-input v-model="row.quantityPerSet" size="small" /></template></el-table-column>
            <el-table-column label="损耗率%" width="85"><template #default="{row}"><el-input v-model="row.lossRate" size="small" /></template></el-table-column>
            <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeBomRow($index)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="BUG 列表" name="bug">
          <el-button type="primary" size="small" @click="handleAddBug" style="margin-bottom:8px">+ 新增BUG</el-button>
          <el-table :data="bugList" border size="small">
            <el-table-column prop="code" label="编号" width="140" />
            <el-table-column prop="title" label="标题" min-width="150" />
            <el-table-column prop="severity" label="严重程度" width="90" />
            <el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small">{{row.status}}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="120" align="center"><template #default="{row}"><el-button type="primary" link @click="handleEditBug(row as BugDTO)">编辑</el-button><el-button type="danger" link @click="handleDeleteBug(row as BugDTO)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="图纸文档" name="drawing">
          <el-button type="primary" size="small" @click="handleAddDrawing" style="margin-bottom:8px">+ 添加图纸</el-button>
          <el-table :data="drawingList" border size="small">
            <el-table-column prop="docName" label="名称" min-width="150" />
            <el-table-column prop="docType" label="类型" width="90" />
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="createTime" label="上传时间" width="160" />
            <el-table-column label="操作" width="80" align="center"><template #default="{row}"><el-button type="danger" link @click="handleDeleteDrawing(row as DrawingVO)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <el-dialog v-model="bugDialogVisible" :title="isBugEdit?'编辑BUG':'新增BUG'" width="500px">
      <el-form :model="bugForm" label-width="80px">
        <el-form-item label="标题"><el-input v-model="bugForm.title" /></el-form-item>
        <el-form-item label="严重程度"><el-select v-model="bugForm.severity" style="width:100%"><el-option label="致命" value="致命"/><el-option label="严重" value="严重"/><el-option label="一般" value="一般"/><el-option label="轻微" value="轻微"/></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="bugForm.status" style="width:100%"><el-option label="待处理" value="待处理"/><el-option label="处理中" value="处理中"/><el-option label="已修复" value="已修复"/><el-option label="已验证" value="已验证"/><el-option label="已关闭" value="已关闭"/></el-select></el-form-item>
        <el-form-item label="描述"><el-input v-model="bugForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="bugDialogVisible=false">取消</el-button><el-button type="primary" @click="handleBugSubmit">确定</el-button></template>
    </el-dialog>

    <el-dialog v-model="drawingVisible" title="添加图纸" width="450px">
      <el-form :model="drawingForm" label-width="80px">
        <el-form-item label="名称"><el-input v-model="drawingForm.docName" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="drawingForm.docType" style="width:100%"><el-option label="排线图" value="排线图"/><el-option label="结构图" value="结构图"/><el-option label="规格书" value="规格书"/><el-option label="测试报告" value="测试报告"/><el-option label="其他" value="其他"/></el-select></el-form-item>
        <el-form-item label="版本"><el-input v-model="drawingForm.version" /></el-form-item>
        <el-form-item label="文件URL"><el-input v-model="drawingForm.fileUrl" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="drawingVisible=false">取消</el-button><el-button type="primary" @click="handleDrawingSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.project-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
</style>
