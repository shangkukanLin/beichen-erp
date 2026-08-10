<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { TimelineStatus, TimelineStatusLabel, ProjectStatus, ProjectStatusLabel, ProjectStatusTag, SeverityType, SeverityTypeLabel, BugTypeEnum, BugTypeEnumLabel, BugStatus, BugStatusLabel, BugStatusTag } from '@/api/enums'
import {
  getProject, updateProject,
  getProjectBom, saveProjectBom,
  getProjectBugs, addProjectBug, updateProjectBug, deleteProjectBug,
  getProjectDrawings, addProjectDrawing, deleteProjectDrawing,
  getSupplierPage,
  type ProjectVO, type ProjectDTO, type BomDTO, type BugDTO, type DrawingVO
} from '@/api/system'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import request from '@/utils/request'
import MaterialFormDialog from '@/components/dev/MaterialFormDialog.vue'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)

const saving = ref(false)
const activeTab = ref((route.query.tab as string) || 'project')

// ===================== 项目基础信息 =====================
const form = reactive<ProjectDTO>({
  name: '', displaySupplierName: '', touchSupplierName: '',
  assemblyName: '',
  adaptModel: '', originalSize: '', originalResolution: '',
  startDate: '', expectedEndDate: '', status: '立项', remark: '',
  sampleFactoryId: undefined, outsourceFactoryId: undefined
})
const solutionSuppliers = ref<{ id: number; name: string }[]>([])
const allSuppliers = ref<any[]>([])
const factoryOptions = ref<{ id: number; name: string }[]>([])

async function loadSolutionSuppliers() {
  try { const res = await getSupplierPage({ supplierType: 'solution', pageSize: 200 }); solutionSuppliers.value = (res?.records || []).map((s: any) => ({ id: s.id, name: s.name })) } catch (e: any) { console.warn('加载方案商失败', e?.message || e) }
}

async function loadAllSuppliers() {
  try { const res = await request.get<any, any>('/supplier/page', { params: { pageSize: 500 } }); allSuppliers.value = res?.records || [] } catch (e: any) { console.warn('加载供应商失败', e?.message || e) }
}
async function loadFactories() {
  try { const res = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } }); factoryOptions.value = (res?.records || []).map((s: any) => ({ id: s.id, name: s.name })) } catch (e: any) { console.warn('加载工厂失败', e?.message || e) }
}

async function loadProject() {
  const p = await getProject(projectId)
  Object.assign(form, {
    id: p.id, name: p.name, code: p.code,
    assemblyName: p.assemblyName,
    displaySupplierName: p.displaySupplierName, touchSupplierName: p.touchSupplierName,
    adaptModel: p.adaptModel, originalSize: p.originalSize, originalResolution: p.originalResolution,
    sampleFactoryId: p.sampleFactoryId, outsourceFactoryId: p.outsourceFactoryId,
    startDate: p.startDate, expectedEndDate: p.expectedEndDate,
    status: p.status, remark: p.remark
  })
}

async function handleSave() {
  if (!form.name.trim()) { ElMessage.warning('请输入项目名称'); return }
  if (!form.assemblyName || !form.assemblyName.trim()) { ElMessage.warning('请输入总成名称'); return }
  saving.value = true
  try {
    await updateProject(form as any)
    ElMessage.success('保存成功')
    await loadProject()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')); await loadProject() }
  saving.value = false
}

function goCreateOrder(type: 'sample' | 'outsource') {
  const factoryId = type === 'sample' ? form.sampleFactoryId : form.outsourceFactoryId
  if (!factoryId) return
  router.push({ path: '/outsource/order/add', query: { factoryId, projectId } })
}

// ===================== 时间线 =====================
interface TimelineItem { id?: number; statusName: string; sortOrder: number; defaultDays?: number; plannedEnd?: string; actualEnd?: string; status?: string; remark?: string }
const timelineStatusOptions = [TimelineStatus.NOT_STARTED, TimelineStatus.IN_PROGRESS, TimelineStatus.FINISHED]
const timelineList = ref<TimelineItem[]>([])
const timelineCompleting = ref<Record<number, boolean>>({})

async function loadTimeline() {
  const res = await request.get<TimelineItem[]>(`/dev/project/${projectId}/timeline`)
  timelineList.value = res || []
}

// 从时间线推导当前项目阶段
const currentPhaseName = computed(() => {
  // 优先找"进行中"的阶段
  const active = timelineList.value.find(t => t.status === TimelineStatus.IN_PROGRESS)
  if (active) return active.statusName
  // 没有进行中的，找最后一个已完成/已跳过的
  for (let i = timelineList.value.length - 1; i >= 0; i--) {
    if (timelineList.value[i].status === TimelineStatus.FINISHED
        || timelineList.value[i].status === TimelineStatus.SKIPPED) {
      return timelineList.value[i].statusName
    }
  }
  // 都没有，返回模板第一项
  return timelineList.value.length > 0 ? timelineList.value[0].statusName : '-'
})

async function saveTimelineRow(row: any) {
  try {
    await request.put(`/dev/project/timeline/${row.id}`, {
      id: row.id,
      projectId: projectId,
      statusName: row.statusName,
      sortOrder: row.sortOrder,
      defaultDays: row.defaultDays,
      plannedEnd: row.plannedEnd || null,
      actualEnd: row.actualEnd || null,
      status: row.status,
      remark: row.remark || null
    })
    // 更新日期或状态后刷新列表（后端可能后推了后续阶段）
    await loadTimeline()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '')) }
}

async function completePhase(timelineId: number) {
  timelineCompleting.value[timelineId] = true
  try {
    await request.put(`/dev/project/timeline/${timelineId}/complete`)
    ElMessage.success('阶段已完成')
    await loadTimeline()
    await loadProject()
  } catch (e: any) { ElMessage.error('操作失败: ' + (e?.message || '')) }
  finally { timelineCompleting.value[timelineId] = false }
}

async function skipPhase(timelineId: number) {
  timelineCompleting.value[timelineId] = true
  try {
    await request.put(`/dev/project/timeline/${timelineId}/skip`)
    ElMessage.success('阶段已跳过')
    await loadTimeline()
    await loadProject()
  } catch (e: any) { ElMessage.error('操作失败: ' + (e?.message || '')) }
  finally { timelineCompleting.value[timelineId] = false }
}

/** 撤销阶段 */
async function revertPhase(timelineId: number) {
  try {
    await ElMessageBox.confirm('撤销后将恢复该阶段为进行中，后续阶段将全部重置为未开始。确认撤销？', '确认撤销', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await request.put(`/dev/project/timeline/${timelineId}/revert`)
    ElMessage.success('阶段已撤销')
    await loadTimeline()
    await loadProject()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败: ' + (e?.message || ''))
  }
}

/** 级联重算计划日期 */
async function recalcPlannedEnds() {
  try {
    await ElMessageBox.confirm('将以第一个进行中阶段为起点，级联推算后续所有未开始阶段的计划日期。确认重算？', '确认重算', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' })
    await request.put(`/dev/project/timeline/recalc`, null, { params: { projectId } })
    ElMessage.success('计划日期已重算')
    await loadTimeline()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败: ' + (e?.message || ''))
  }
}

// 进度统计（使用枚举比较）
const timelineProgress = computed(() => {
  const total = timelineList.value.length
  const completed = timelineList.value.filter(t => t.status === TimelineStatus.FINISHED || t.status === TimelineStatus.SKIPPED).length
  const inProgress = timelineList.value.filter(t => t.status === TimelineStatus.IN_PROGRESS).length
  return { total, completed, inProgress, pct: total > 0 ? Math.round(completed / total * 100) : 0 }
})

// 行样式（使用枚举比较）
function timelineRowClass({ row }: { row: TimelineItem }) {
  if (row.status === TimelineStatus.FINISHED || row.status === TimelineStatus.SKIPPED) return 'timeline-row-done'
  if (row.status === TimelineStatus.IN_PROGRESS) return 'timeline-row-active'
  return ''
}

// BOM 平铺列表（父+子混排，子行只读缩进）
const bomList = ref<any[]>([])
const bomTypes = ref<any[]>([])
const allMaterials = ref<any[]>([])
async function loadBomTypes() {
  try { const res = await request.get<any, any>('/dev/bom-type/enabled'); bomTypes.value = (res || []) } catch (e: any) { console.warn('加载BOM类型失败', e?.message || e) }
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); allMaterials.value = (r?.records || []) } catch (e: any) { console.warn('加载物料数据失败', e?.message || e) }
}
// BOM类型 id -> 类型名 映射，用于回显
const bomTypeNameMap = computed<Record<number, string>>(() => {
  const m: Record<number, string> = {}
  for (const t of bomTypes.value) m[t.id] = t.typeName
  return m
})
function getMaterialsByType(type: any) { return allMaterials.value.filter((m:any) => m.bomTypeId != null && m.bomTypeId === type) }

/** 加载BOM + 子物料平铺 */
async function loadBom() {
  const items = (await getProjectBom(projectId)) || []
  // 通过 outsourceMaterialId 批量查询物料名称和子物料
  const materialIds = [...new Set(items.map((b:any) => b.outsourceMaterialId).filter(Boolean))]
  const childrenMap: Record<string, any[]> = {}
  const materialNameMap: Record<number, string> = {}
  if (materialIds.length > 0) {
    try {
      const res = await request.post<any, any>('/outsource/material/components-batch-by-ids', materialIds)
      Object.assign(childrenMap, res?.childrenMap || {})
      Object.assign(materialNameMap, res?.nameMap || {})
    } catch { /* ignore */ }
  }
  const result: any[] = []
  for (const b of items) {
    const matId = b.outsourceMaterialId
    const matName = matId ? (materialNameMap[matId] || '') : ''
    result.push({ _isChild: false, materialName: matName, outsourceMaterialId: matId, supplierId: b.supplierId, spec: b.specification, unit: b.unit, quantityPerSet: b.quantity, lossRate: b.lossRate, bomTypeId: b.bomTypeId, bomTypeName: bomTypeNameMap.value[b.bomTypeId ?? 0] || '', remark: '', id: b.id })
    const subs = childrenMap[String(matId)] || []
    for (const s of subs) {
      result.push({ _isChild: true, materialName: s.childName || s.materialName, bomTypeName: s.childType || '', quantityPerSet: s.quantity, lossRate: s.lossRate, remark: s.remark })
    }
  }
  bomList.value = result
}

function addBomRow() { bomList.value.push({ _isChild: false, materialName: '', outsourceMaterialId: undefined, spec: '', unit: '', quantityPerSet: 1, lossRate: 2, bomTypeId: '', remark: '', supplierId: undefined }) }
async function onBomMaterialChange(materialId: number, row: any) {
  if (!materialId) return
  const matched = allMaterials.value.find((m: any) => m.id === materialId)
  if (!matched) return
  row.materialName = matched.materialName
  row.outsourceMaterialId = matched.id
  if (matched.spec) row.spec = matched.spec
  if (matched.unit) row.unit = matched.unit
  if (matched.supplierIds) {
    const ids = String(matched.supplierIds).split(',').filter(Boolean).map(Number)
    if (ids.length > 0) row.supplierId = ids[0]
  }
}
function removeBomRow(i: number) { bomList.value.splice(i, 1) }
async function saveBom() {
  const parents = bomList.value.filter((b: any) => !b._isChild)
  const emptyType = parents.find((b: any) => !b.bomTypeId)
  if (emptyType) { ElMessage.warning('BOM类型不能为空'); return }
  const emptyMatId = parents.find((b: any) => !b.outsourceMaterialId)
  if (emptyMatId) { ElMessage.warning('物料名称不能为空'); return }
  const zeroQty = parents.find((b: any) => !b.quantityPerSet || Number(b.quantityPerSet) <= 0)
  if (zeroQty) { ElMessage.warning('物料用量必须大于0'); return }
  // 转换为后端 Bom 实体格式
  const bomData = parents.map((b: any) => ({
    id: b.id,
    projectId,
    bomTypeId: b.bomTypeId,
    outsourceMaterialId: b.outsourceMaterialId,
    supplierId: b.supplierId,
    quantity: b.quantityPerSet,
    lossRate: b.lossRate,
    specification: b.spec,
    unit: b.unit
  }))
  await saveProjectBom(projectId, bomData)
  ElMessage.success('BOM已保存')
  await loadBom()
}

// ===================== BUG =====================
const bugList = ref<BugDTO[]>([])
const bugTab = ref('active')
const bugListFilter = ref('全部')
const filteredBugs = computed(() => {
  let list = bugList.value
  if (bugListFilter.value !== '全部') list = list.filter(b => b.bugType === bugListFilter.value)
  return { active: list.filter(b => b.status !== BugStatus.CLOSED), closed: list.filter(b => b.status === BugStatus.CLOSED) }
})
const bugDialogVisible = ref(false)
const bugForm = reactive<BugDTO>({ title: '', severity: SeverityType.NORMAL, bugType: BugTypeEnum.DISPLAY, status: BugStatus.OPEN, description: '' })
const isBugEdit = ref(false)
async function loadBugs() {
  try {
    const res: any = await getProjectBugs(projectId)
    bugList.value = res?.records || res || []
  } catch (e: any) {
    ElMessage.error('加载项目缺陷失败：' + (e?.msg || e?.message || '未知错误'))
  }
}
function handleAddBug() { Object.assign(bugForm, { title: '', severity: SeverityType.NORMAL, bugType: BugTypeEnum.DISPLAY, status: BugStatus.OPEN, description: '' }); isBugEdit.value = false; bugDialogVisible.value = true }
function handleEditBug(row: BugDTO) { Object.assign(bugForm, row); isBugEdit.value = true; bugDialogVisible.value = true }
async function handleBugSubmit() {
  if (isBugEdit.value && bugForm.id) { await updateProjectBug(projectId, bugForm); ElMessage.success('已更新') }
  else { await addProjectBug(projectId, bugForm); ElMessage.success('已添加') }
  bugDialogVisible.value = false; loadBugs()
}
async function handleDeleteBug(row: BugDTO) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await deleteProjectBug(projectId, row.id!); ElMessage.success('已删除'); loadBugs() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

// ===================== 图纸（含排线图纸上传） =====================
const drawingList = ref<DrawingVO[]>([])
async function loadDrawings() { drawingList.value = (await getProjectDrawings(projectId)) || [] }
const drawingVisible = ref(false)
const drawingForm = reactive({ docName: '', docType: '排线图', version: 'v1.0', fileUrl: '' })
const uploadFile = ref<File | null>(null)
const uploading = ref(false)

function handleAddDrawing() { 
  Object.assign(drawingForm, { docName: '', docType: '排线图', version: 'v1.0', fileUrl: '' })
  uploadFile.value = null
  drawingVisible.value = true 
}

function handleDragOver(e: DragEvent) { e.preventDefault() }

function handleDrop(e: DragEvent) {
  e.preventDefault()
  const file = e.dataTransfer?.files?.[0]
  if (file) { uploadFile.value = file; drawingForm.docName = file.name; drawingForm.version = 'v1.0' }
}

function handleFileSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) { uploadFile.value = file; drawingForm.docName = file.name }
}

async function handleDrawingSubmit() {
  if (!drawingForm.docName) { ElMessage.warning('请选择文件'); return }
  uploading.value = true
  try {
    if (uploadFile.value) {
      const fd = new FormData()
      fd.append('file', uploadFile.value)
      const res = await request.post<any, string>('/dev/file/upload', fd)
      drawingForm.fileUrl = res as unknown as string
    }
    await addProjectDrawing(projectId, drawingForm as any)
    ElMessage.success('图纸已上传'); drawingVisible.value = false; loadDrawings()
  } catch (e: any) { ElMessage.error('上传失败: ' + (e?.message || '未知错误')) } finally { uploading.value = false }
}
function downloadFile(url: string) { window.open(url) }
async function handleDeleteDrawing(row: DrawingVO) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await deleteProjectDrawing(projectId, row.id!); ElMessage.success('已删除'); loadDrawings() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

// ===================== 项目物料 =====================
interface DevPurchaseItem {
  id?: number
  projectId?: number
  name: string
  type: string
  quantity: number
  locationDetail: string
  warehouseAddress: string
  purchaseDate: string
  amount: number
  status: string
  remark: string
}
const devMaterialList = ref<DevPurchaseItem[]>([])
const materialDialog = ref<any>(null)

async function loadDevMaterials() {
  try {
    const res = await request.get<DevPurchaseItem[]>(`/dev/purchase-item/project/${projectId}`)
    devMaterialList.value = res || []
  } catch (e: any) { ElMessage.error('加载项目物料失败：' + (e?.msg || e?.message || '未知错误')) }
}

// 打开共用新增/编辑弹窗（自动锁定当前项目）
function handleAddDevMaterial() { materialDialog.value?.open() }
function handleEditDevMaterial(row: any) { materialDialog.value?.open(row) }

async function handleDeleteDevMaterial(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该记录吗？', '提示', { type: 'warning' })
    await request.delete(`/dev/purchase-item/${row.id}`)
    ElMessage.success('已删除')
    loadDevMaterials()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

// ===================== 关联委外订单 =====================
interface RelatedOrder { id: number; code: string; status: string; createTime: string; productName: string }
const relatedOrders = ref<RelatedOrder[]>([])
async function loadRelatedOrders() {
  try {
    const res = await request.get<RelatedOrder[]>(`/dev/project/${projectId}/related-orders`)
    relatedOrders.value = res || []
  } catch (e: any) { ElMessage.error('加载关联订单失败：' + (e?.msg || e?.message || '未知错误')) }
}

// 切换 Tab 时自动加载 BOM 数据
watch(activeTab, async (tab) => { if (tab === 'bom') await loadBom() })

onMounted(() => { loadProject(); loadSolutionSuppliers(); loadAllSuppliers(); loadFactories(); loadBomTypes(); loadTimeline(); loadBom(); loadBugs(); loadDrawings(); loadDevMaterials(); loadRelatedOrders() })
onActivated(() => { loadSolutionSuppliers(); loadAllSuppliers(); loadFactories(); loadBomTypes() })



function onNameBlur() {
  if (!form.assemblyName || !form.assemblyName.trim()) {
    form.assemblyName = form.name
  }
}
</script>

<template>
  <div class="edit-page">
    <el-tabs v-model="activeTab">
      <!-- 项目信息 Tab -->
      <el-tab-pane label="项目信息" name="project">
        <!-- 基础信息 -->
        <el-card shadow="never">
          <template #header><span style="font-weight:600">基础信息</span></template>
          <el-form :model="form" label-width="100px" size="default">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="项目名称"><el-input v-model="form.name" @blur="onNameBlur" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="总成名称" prop="assemblyName" :rules="[{ required: true, message: '请输入总成名称', trigger: 'blur' }]"><el-input v-model="form.assemblyName" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="项目编码"><el-input :model-value="form.code" disabled /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="当前阶段">
                <el-tag type="warning" size="default">{{ currentPhaseName }}</el-tag>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="适配机型"><el-input v-model="form.adaptModel" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="显示方案"><el-select v-model="form.displaySupplierName" filterable allow-create style="width:100%" @change="(v: string) => { if (v === ADD_MARKER) { form.displaySupplierName = ''; router.push('/supplier/manage'); return } }"><el-option v-for="s in solutionSuppliers" :key="s.id" :label="s.name" :value="s.name" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="触摸方案"><el-select v-model="form.touchSupplierName" filterable allow-create style="width:100%" @change="(v: string) => { if (v === ADD_MARKER) { form.touchSupplierName = ''; router.push('/supplier/manage'); return } }"><el-option v-for="s in solutionSuppliers" :key="s.id" :label="s.name" :value="s.name" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="原机尺寸"><el-input v-model="form.originalSize" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="原分辨率"><el-input v-model="form.originalResolution" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="打样工厂">
                <div style="display:flex;gap:4px">
                  <el-select v-model="form.sampleFactoryId" clearable filterable style="flex:1" placeholder="选择工厂" @change="(v: any) => { if (v === ADD_MARKER) { form.sampleFactoryId = undefined; router.push('/supplier/manage'); return } }"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select>
                  <el-button v-if="form.sampleFactoryId" type="success" size="small" @click="goCreateOrder('sample')">下单</el-button>
                </div>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="委外工厂">
                <div style="display:flex;gap:4px">
                  <el-select v-model="form.outsourceFactoryId" clearable filterable style="flex:1" placeholder="选择工厂" @change="(v: any) => { if (v === ADD_MARKER) { form.outsourceFactoryId = undefined; router.push('/supplier/manage'); return } }"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select>
                  <el-button v-if="form.outsourceFactoryId" type="success" size="small" @click="goCreateOrder('outsource')">下单</el-button>
                </div>
              </el-form-item></el-col>
            </el-row>
          </el-form>
        </el-card>

        <!-- 时间节点 -->
        <el-card shadow="never" style="margin-top:12px">
          <template #header><span style="font-weight:600">时间节点</span></template>
          <el-form :model="form" label-width="100px" size="default">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="立项日期"><el-input v-model="form.startDate" type="date" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="预计完成"><el-input v-model="form.expectedEndDate" type="date" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
            </el-row>
          </el-form>
        </el-card>

        <div style="margin-top:12px"><el-button type="primary" :loading="saving" @click="handleSave">保存基础信息</el-button></div>
      </el-tab-pane>

      <!-- 阶段时间线 Tab -->
      <el-tab-pane label="阶段时间线" name="timeline">
        <el-card shadow="never">
          <!-- 进度概览 + 操作按钮 -->
          <div style="margin-bottom:12px;display:flex;align-items:center;gap:16px;flex-wrap:wrap">
            <span style="font-size:14px;font-weight:600">进度概览</span>
            <div style="flex:1;max-width:360px">
              <el-progress :percentage="timelineProgress.pct" :stroke-width="16" 
                :color="timelineProgress.pct === 100 ? '#67c23a' : '#409eff'">
                <span style="font-size:12px">{{ timelineProgress.completed }} / {{ timelineProgress.total }} 已完成</span>
              </el-progress>
            </div>
            <el-tag v-if="timelineProgress.inProgress > 0" type="warning" size="small">{{ timelineProgress.inProgress }} 个进行中</el-tag>
            <el-button type="primary" size="small" plain @click="recalcPlannedEnds">重算计划日期</el-button>
          </div>

          <el-table :data="timelineList" border size="small" :row-class-name="timelineRowClass">
            <el-table-column label="排序" width="55" align="center"><template #default="{row}">{{ row.sortOrder }}</template></el-table-column>
            <el-table-column prop="statusName" label="阶段名称" width="140" />
            <el-table-column label="默认天数" width="75" align="center"><template #default="{row}">{{ row.defaultDays || '-' }}</template></el-table-column>
            <el-table-column label="计划完成" width="150">
              <template #default="{row}">
                <el-input v-model="row.plannedEnd" type="date" size="small" 
                  :disabled="row.status === TimelineStatus.FINISHED || row.status === TimelineStatus.SKIPPED" @change="saveTimelineRow(row)" />
              </template>
            </el-table-column>
            <el-table-column label="实际完成" width="150">
              <template #default="{row}">
                <el-input v-model="row.actualEnd" type="date" size="small" @change="saveTimelineRow(row)" />
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="140">
              <template #default="{row}">
                <el-input v-model="row.remark" size="small" placeholder="可选" @change="saveTimelineRow(row)" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{row}">
                <el-tag v-if="row.status === TimelineStatus.FINISHED" type="success" size="small">{{ TimelineStatusLabel[row.status] }}</el-tag>
                <el-tag v-else-if="row.status === TimelineStatus.IN_PROGRESS" type="warning" size="small">{{ TimelineStatusLabel[row.status] }}</el-tag>
                <el-tag v-else-if="row.status === TimelineStatus.SKIPPED" type="info" size="small" style="border-style:dashed">{{ TimelineStatusLabel[row.status] }}</el-tag>
                <el-select v-else v-model="row.status" size="small" style="width:90px" @change="saveTimelineRow(row)">
                  <el-option v-for="o in timelineStatusOptions" :key="o" :label="TimelineStatusLabel[o]" :value="o" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center">
              <template #default="{row}">
                <div style="display:flex;justify-content:center;align-items:center;gap:4px">
                <el-button v-if="row.id && row.status === TimelineStatus.IN_PROGRESS" type="success" size="small"
                  :loading="timelineCompleting[row.id]" @click="completePhase(row.id)">
                  完成
                </el-button>
                <el-button v-if="row.id && row.status === TimelineStatus.IN_PROGRESS" type="warning" size="small"
                  plain @click="skipPhase(row.id)">
                  跳过
                </el-button>
                <el-button v-if="row.id && (row.status === TimelineStatus.FINISHED || row.status === TimelineStatus.SKIPPED)" type="danger" size="small"
                  plain @click="revertPhase(row.id)">
                  撤销
                </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- BOM物料清单 Tab -->
      <el-tab-pane label="BOM物料清单" name="bom">
        <el-card shadow="never">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
            <el-button type="primary" size="small" @click="addBomRow">+ 添加物料</el-button>
            <el-button type="success" size="small" @click="saveBom">保存</el-button>
          </div>
          <el-table :data="bomList" border size="small">
            <el-table-column label="类型" width="100">
              <template #default="{row}">
                <span v-if="row._isChild" style="color:#999;font-size:12px">{{ row.bomTypeName }}</span>
                <el-select v-else v-model="row.bomTypeId" size="small" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.bomTypeId = ''; router.push('/dev/bom-type'); return } row.materialName = '' }">
                  <el-option v-for="t in bomTypes" :key="t.id" :label="t.typeName" :value="t.id" />
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="物料名称" min-width="130">
              <template #default="{row}">
                <span v-if="row._isChild" style="color:#409eff;font-size:12px">└ {{ row.materialName }}</span>
                <el-select v-else v-model="row.outsourceMaterialId" size="small" filterable clearable style="width:100%" placeholder="选择" @change="(v: any) => { if (v === ADD_MARKER) { row.outsourceMaterialId = undefined; router.push('/outsource/material-info'); return } onBomMaterialChange(v, row) }">
                  <el-option v-for="m in getMaterialsByType(row.bomTypeId || '')" :key="m.id" :label="m.materialName" :value="m.id" />
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="供应商" width="100">
              <template #default="{row}">
                <span v-if="row._isChild" style="color:#999;font-size:12px">-</span>
                <el-select v-else v-model="row.supplierId" size="small" clearable filterable style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.supplierId = undefined; router.push('/supplier/manage'); return } }">
                  <el-option v-for="s in allSuppliers" :key="s.id" :label="s.name" :value="s.id" />
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="规格" width="90"><template #default="{row}"><span v-if="row._isChild" style="color:#999;font-size:12px">-</span><el-input v-else v-model="row.spec" size="small" /></template></el-table-column>
            <el-table-column label="单位" width="65"><template #default="{row}"><span v-if="row._isChild" style="color:#999;font-size:12px">-</span><el-input v-else v-model="row.unit" size="small" /></template></el-table-column>
            <el-table-column label="用量" width="75"><template #default="{row}"><span :style="{fontSize:'12px'}">{{ row.quantityPerSet }}</span></template></el-table-column>
            <el-table-column label="损耗率%" width="80"><template #default="{row}"><span :style="{fontSize:'12px'}">{{ row.lossRate }}</span></template></el-table-column>
            <el-table-column label="操作" width="60" align="center">
              <template #default="{$index}">
                <el-button type="danger" link @click="removeBomRow($index)">{{ bomList[$index]._isChild ? '' : '删除' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 图纸 Tab（排线图纸上传） -->
      <el-tab-pane label="图纸文档" name="drawing">
        <el-card shadow="never">
          <div style="margin-bottom:12px;display:flex;gap:8px">
            <el-button type="primary" @click="handleAddDrawing">📎 上传排线图纸</el-button>
            <el-tag type="info">支持排线图、结构图、规格书、测试报告</el-tag>
          </div>
          <el-table :data="drawingList" border>
            <el-table-column prop="docName" label="文档名称" min-width="160" />
            <el-table-column prop="docType" label="类型" width="100" />
            <el-table-column label="版本" width="80">
              <template #default="{row}">v{{ row.versionCode || 1 }}</template>
            </el-table-column>
            <el-table-column prop="fileUrl" label="文件" min-width="120" show-overflow-tooltip />
            <el-table-column prop="uploadTime" label="上传时间" width="160" />
            <el-table-column label="操作" width="130" align="center">
              <template #default="{row}">
                <el-button type="primary" link v-if="row.fileUrl" @click="downloadFile(row.fileUrl)">下载</el-button>
                <el-button type="danger" link @click="handleDeleteDrawing(row as DrawingVO)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 项目物料 Tab -->
      <el-tab-pane label="项目物料" name="material">
        <el-card shadow="never">
          <div style="margin-bottom:8px;color:#909399;font-size:12px">
            仅记录项目研发自购用料（如机板、原屏幕），与 BOM 表、委外物料无关
          </div>
          <div style="margin-bottom:8px">
            <el-button type="primary" size="small" @click="handleAddDevMaterial">+ 新增项目用料</el-button>
          </div>
          <el-table :data="devMaterialList" border size="small">
            <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="80" />
            <el-table-column prop="quantity" label="数量" width="70" align="center" />
            <el-table-column label="存放位置" width="120">
              <template #default="{ row }">{{ row.warehouseName || '-' }}</template>
            </el-table-column>
            <el-table-column label="位置详情" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.warehouseAddress || row.locationDetail || '-' }}</template>
            </el-table-column>
            <el-table-column prop="purchaseDate" label="采购日期" width="110" />
            <el-table-column prop="amount" label="金额" width="90" align="right">
              <template #default="{ row }">{{ row.amount ? '¥' + Number(row.amount).toFixed(2) : '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === '完好' ? 'success' : row.status === '已损坏' ? 'danger' : 'warning'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleEditDevMaterial(row)">编辑</el-button>
                <el-button type="danger" link @click="handleDeleteDevMaterial(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 关联订单 Tab -->
      <el-tab-pane label="关联订单" name="relatedOrders">
        <el-card shadow="never">
          <el-table :data="relatedOrders" border size="small" empty-text="暂无关联的委外订单">
            <el-table-column prop="code" label="订单号" width="180" />
            <el-table-column prop="productName" label="产品" min-width="120" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}">
                <el-tag size="small" type="primary">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column label="操作" width="100" align="center">
              <template #default="{row}">
                <el-button type="primary" link @click="router.push(`/outsource/order/detail/${row.id}`)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- BUG Tab -->
      <el-tab-pane label="BUG 列表" name="bug">
        <el-card shadow="never">
          <div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">
            <el-button type="primary" size="small" @click="handleAddBug">+ 新增BUG</el-button>
            <el-select v-model="bugListFilter" size="small" style="width:100px" @change="()=>{}">
              <el-option label="全部" value="全部"/>
              <el-option :label="BugTypeEnumLabel[BugTypeEnum.DISPLAY]" :value="BugTypeEnum.DISPLAY"/>
              <el-option :label="BugTypeEnumLabel[BugTypeEnum.TOUCH]" :value="BugTypeEnum.TOUCH"/>
              <el-option :label="BugTypeEnumLabel[BugTypeEnum.STRUCTURE]" :value="BugTypeEnum.STRUCTURE"/>
            </el-select>
          </div>

          <el-tabs v-model="bugTab" type="card" style="margin-top:4px">
            <el-tab-pane :label="'处理中 ('+filteredBugs.active.length+')'" name="active">
              <el-table :data="filteredBugs.active" border size="small">
                <el-table-column prop="code" label="编号" width="140" />
                <el-table-column prop="title" label="标题" min-width="150" />
                <el-table-column prop="bugType" label="类型" width="70"><template #default="{row}">{{ BugTypeEnumLabel[row.bugType] || row.bugType }}</template></el-table-column>
                <el-table-column prop="severity" label="严重程度" width="90"><template #default="{row}">{{ SeverityTypeLabel[row.severity] || row.severity }}</template></el-table-column>
                <el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small" :type="BugStatusTag[row.status] || 'info'">{{ BugStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
                <el-table-column label="操作" width="120" align="center"><template #default="{row}"><el-button type="primary" link @click="handleEditBug(row as BugDTO)">编辑</el-button><el-button type="danger" link @click="handleDeleteBug(row as BugDTO)">删除</el-button></template></el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="'已关闭 ('+filteredBugs.closed.length+')'" name="closed">
              <el-table :data="filteredBugs.closed" border size="small" v-if="filteredBugs.closed.length>0">
                <el-table-column prop="code" label="编号" width="140" />
                <el-table-column prop="title" label="标题" min-width="150" />
                <el-table-column prop="bugType" label="类型" width="70"><template #default="{row}">{{ BugTypeEnumLabel[row.bugType] || row.bugType }}</template></el-table-column>
                <el-table-column prop="severity" label="严重程度" width="90"><template #default="{row}">{{ SeverityTypeLabel[row.severity] || row.severity }}</template></el-table-column>
                <el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small" type="info">{{ BugStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
                <el-table-column label="操作" width="120" align="center"><template #default="{row}"><el-button type="primary" link @click="handleEditBug(row as BugDTO)">编辑</el-button><el-button type="danger" link @click="handleDeleteBug(row as BugDTO)">删除</el-button></template></el-table-column>
              </el-table>
              <div v-else style="color:#909399;padding:16px;text-align:center">暂无已关闭的BUG</div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- BUG 弹窗 -->
    <el-dialog v-model="bugDialogVisible" :title="isBugEdit?'编辑BUG':'新增BUG'" width="500px">
      <el-form :model="bugForm" label-width="80px">
        <el-form-item label="标题"><el-input v-model="bugForm.title" /></el-form-item>
        <el-form-item label="严重程度"><el-select v-model="bugForm.severity" style="width:100%">
          <el-option v-for="(label, code) in SeverityTypeLabel" :key="code" :label="label" :value="code" />
        </el-select></el-form-item>
        <el-form-item label="类型"><el-select v-model="bugForm.bugType" style="width:100%">
          <el-option v-for="(label, code) in BugTypeEnumLabel" :key="code" :label="label" :value="code" />
        </el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="bugForm.status" style="width:100%">
          <el-option v-for="(label, code) in BugStatusLabel" :key="code" :label="label" :value="code" />
        </el-select></el-form-item>
        <el-form-item label="描述"><el-input v-model="bugForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="bugDialogVisible=false">取消</el-button><el-button type="primary" @click="handleBugSubmit">确定</el-button></template>
    </el-dialog>

    <!-- 图纸上传弹窗 -->
    <el-dialog v-model="drawingVisible" title="上传图纸" width="520px">
      <!-- 拖拽上传区域 -->
      <div class="drop-zone" 
        @dragover="handleDragOver" @drop="handleDrop"
        :style="{ borderColor: uploadFile ? '#67c23a' : '#dcdfe6', background: uploadFile ? '#f0f9eb' : '#fafafa' }">
        <template v-if="uploadFile">
          <p style="color:#67c23a;font-weight:600;margin:0">📎 {{ uploadFile.name }}</p>
          <p style="color:#909399;font-size:12px;margin:4px 0 0">{{ (uploadFile.size/1024).toFixed(1) }} KB</p>
        </template>
        <template v-else>
          <p style="color:#909399;margin:0">拖拽文件到此处，或点击下方按钮选择</p>
        </template>
        <input type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
      <el-form :model="drawingForm" label-width="80px" style="margin-top:12px">
        <el-form-item label="文档名称"><el-input v-model="drawingForm.docName" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="drawingForm.docType" style="width:100%"><el-option label="排线图" value="排线图"/><el-option label="结构图" value="结构图"/><el-option label="规格书" value="规格书"/><el-option label="测试报告" value="测试报告"/><el-option label="其他" value="其他"/></el-select></el-form-item>
        <el-form-item label="版本"><el-input v-model="drawingForm.version" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="drawingVisible=false">取消</el-button><el-button type="primary" :loading="uploading" @click="handleDrawingSubmit">确定</el-button></template>
    </el-dialog>

    <!-- 项目物料弹窗（共用组件，自动锁定当前项目） -->
    <MaterialFormDialog ref="materialDialog" :default-project-id="projectId" @saved="loadDevMaterials" />
  </div>
</template>

<style scoped>
.edit-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }

.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:32px; text-align:center; transition:all .3s; cursor:pointer }
.drop-zone:hover { border-color:#409eff; background:#ecf5ff }

/* 时间线行样式 */
:deep(.timeline-row-done) { background-color: #f0f9eb; }
:deep(.timeline-row-active) { background-color: #fdf6ec; }
</style>

