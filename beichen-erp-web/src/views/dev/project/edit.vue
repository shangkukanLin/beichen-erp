<script setup lang="ts">
import { reactive, ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProject, updateProject, updateProjectStatus,
  getProjectBom, saveProjectBom,
  getProjectBugs, addProjectBug, updateProjectBug, deleteProjectBug,
  getProjectDrawings, addProjectDrawing, deleteProjectDrawing,
  getSupplierPage,
  type ProjectVO, type ProjectDTO, type BomDTO, type BugDTO, type DrawingVO
} from '@/api/system'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)

const STATUS_LIST = ['立项', '排线图纸', '排线打样', 'FOG打样', '显示调试', '触摸调试', '背贴盖板打样', '总成样品', '测试', '小批量', '结项']

const saving = ref(false)
const activeTab = ref((route.query.tab as string) || 'project')

// ===================== 项目基本信息 =====================
const form = reactive<ProjectDTO>({
  name: '', displaySupplierName: '', touchSupplierName: '',
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
    displaySupplierName: p.displaySupplierName, touchSupplierName: p.touchSupplierName,
    adaptModel: p.adaptModel, originalSize: p.originalSize, originalResolution: p.originalResolution,
    sampleFactoryId: p.sampleFactoryId, outsourceFactoryId: p.outsourceFactoryId,
    startDate: p.startDate, expectedEndDate: p.expectedEndDate,
    status: p.status, remark: p.remark
  })
}

async function handleSave() {
  saving.value = true
  try {
    await updateProject(form as any)
    ElMessage.success('保存成功')
    await loadProject()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')); await loadProject() }
  saving.value = false
}

async function handleStatusChange(newStatus: string) {
  await updateProjectStatus(projectId, newStatus)
  form.status = newStatus
  ElMessage.success('阶段已更新')
}

function goCreateOrder(type: 'sample' | 'outsource') {
  const factoryId = type === 'sample' ? form.sampleFactoryId : form.outsourceFactoryId
  if (!factoryId) return
  router.push({ path: '/outsource/order/add', query: { factoryId, projectId } })
}

// ===================== 时间线 =====================
interface TimelineItem { statusName: string; sortOrder: number; plannedEnd?: string; actualEnd?: string; status?: string }
const timelineStatusOptions = ['未完成', '进行中', '已完成']
const timelineList = ref<TimelineItem[]>([])

async function loadTimeline() {
  const res = await request.get<unknown, TimelineItem[]>(`/dev/project/${projectId}/timeline`)
  timelineList.value = res || []
}

async function saveTimeline() {
  await request.put(`/dev/project/${projectId}/timeline`, timelineList.value)
  ElMessage.success('时间线已保存'); loadTimeline()
}

// BOM 平铺列表（父+子混排，子行只读缩进）
const bomList = ref<any[]>([])
const bomTypes = ref<string[]>([])
const allMaterials = ref<any[]>([])
async function loadBomTypes() {
  try { const res = await request.get<any, any>('/dev/bom-type/enabled'); bomTypes.value = (res || []).map((t:any) => t.typeName) } catch (e: any) { console.warn('加载BOM类型失败', e?.message || e) }
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); allMaterials.value = (r?.records || []) } catch (e: any) { console.warn('加载物料数据失败', e?.message || e) }
}
function getMaterialsByType(type: string) { return allMaterials.value.filter((m:any) => m.materialType === type) }

/** 加载BOM + 子物料平铺展示 */
async function loadBom() {
  const items = (await getProjectBom(projectId)) || []
  const result: any[] = []
  for (let i = 0; i < items.length; i++) {
    const b = items[i]
    result.push({ _idx: i, _isChild: false, materialName: b.materialName, supplierId: b.supplierId, spec: b.spec, unit: b.unit, quantityPerSet: b.quantityPerSet, lossRate: b.lossRate, materialType: b.materialType, remark: b.remark })
    result.push(...await fetchChildren(b.materialName))
  }
  bomList.value = result
}

/** 根据物料名查子物料行（缩进展示） */
async function fetchChildren(materialName: string) {
  const rows: any[] = []
  if (!materialName) return rows
  const mat = allMaterials.value.find((m: any) => m.materialName === materialName)
  if (!mat) return rows
  try {
    const r = await request.get<any, any[]>('/outsource/material-bom/direct', { params: { parentId: mat.id } })
    if (r && r.length > 0) {
      for (let j = 0; j < r.length; j++) {
        rows.push({
          _isChild: true,
          materialName: r[j].childName || r[j].childMaterialId,
          spec: r[j].childSpec || '',
          unit: r[j].childUnit || '',
          quantityPerSet: r[j].quantity ?? 1,
          lossRate: r[j].lossRate ?? 0,
          materialType: r[j].childType || '',
          remark: r[j].remark || '',
          supplierId: undefined
        })
      }
    }
  } catch { /* ignore */ }
  return rows
}

function addBomRow() { bomList.value.push({ _isChild: false, materialName: '', spec: '', unit: '', quantityPerSet: 1, lossRate: 2, materialType: '', remark: '', supplierId: undefined }) }
async function onBomMaterialChange(materialName: string, row: any) {
  if (!materialName || !row.materialType) return
  const matched = getMaterialsByType(row.materialType).find((m: any) => m.materialName === materialName)
  if (!matched) return
  if (matched.spec) row.spec = matched.spec
  if (matched.unit) row.unit = matched.unit
  if (matched.supplierIds) {
    const ids = String(matched.supplierIds).split(',').filter(Boolean).map(Number)
    if (ids.length > 0) row.supplierId = ids[0]
  }
  // 选择物料后立即加载并显示其子物料
  const idx = bomList.value.indexOf(row)
  if (idx >= 0) {
    // 移除该物料行之前的旧子行
    let next = idx + 1
    while (next < bomList.value.length && bomList.value[next]._isChild) {
      bomList.value.splice(next, 1)
    }
    // 插入新的子物料行
    const children = await fetchChildren(materialName)
    bomList.value.splice(idx + 1, 0, ...children)
  }
}
function removeBomRow(i: number) {
  const row = bomList.value[i]
  // 删除该父行及其子行
  spliceOne(i)
  if (row && !row._isChild) {
    while (i < bomList.value.length && bomList.value[i]._isChild) {
      bomList.value.splice(i, 1)
    }
  }
}
function spliceOne(i: number) { bomList.value.splice(i, 1) }
async function saveBom() {
  const parentRows = bomList.value.filter((b: any) => !b._isChild)
  const emptyType = parentRows.find((b: any) => !b.materialType || !b.materialType.trim())
  if (emptyType) { ElMessage.warning('物料类型不能为空'); return }
  const emptyName = parentRows.find((b: any) => !b.materialName || !b.materialName.trim())
  if (emptyName) { ElMessage.warning('物料名称不能为空'); return }
  const zeroQty = parentRows.find((b: any) => !b.quantityPerSet || Number(b.quantityPerSet) <= 0)
  if (zeroQty) { ElMessage.warning('物料用量必须大于0'); return }
  await saveProjectBom(projectId, parentRows)
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
  return { active: list.filter(b => b.status !== '已关闭'), closed: list.filter(b => b.status === '已关闭') }
})
const bugDialogVisible = ref(false)
const bugForm = reactive<BugDTO>({ title: '', severity: '一般', bugType: '显示', status: '待处理', description: '', assignedTo: undefined })
const isBugEdit = ref(false)
async function loadBugs() { bugList.value = (await getProjectBugs(projectId)) || [] }
function handleAddBug() { Object.assign(bugForm, { title: '', severity: '一般', bugType: '显示', status: '待处理', description: '', assignedTo: undefined }); isBugEdit.value = false; bugDialogVisible.value = true }
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

// 切换 Tab 时自动加载 BOM 数据
watch(activeTab, async (tab) => { if (tab === 'bom') await loadBom() })

onMounted(() => { loadProject(); loadSolutionSuppliers(); loadAllSuppliers(); loadFactories(); loadBomTypes(); loadTimeline(); loadBom(); loadBugs(); loadDrawings() })
function goBack() { router.push('/dev/project') }
</script>

<template>
  <div class="edit-page">
    <!-- 顶栏 -->
    <div class="page-header">
      <el-button @click="goBack" :icon="'ArrowLeft'">返回列表</el-button>
      <span class="page-title">{{ form.name || '项目编辑' }} <el-tag size="small" style="margin-left:8px">{{ form.code }}</el-tag></span>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 项目信息 Tab -->
      <el-tab-pane label="项目信息" name="project">
        <!-- 基础信息 -->
        <el-card shadow="never">
          <template #header><span style="font-weight:600">基础信息</span></template>
          <el-form :model="form" label-width="100px" size="default">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="项目名称"><el-input v-model="form.name" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="项目阶段">
                <el-select :model-value="form.status" @change="(v:string)=>handleStatusChange(v)" style="width:100%">
                  <el-option v-for="s in STATUS_LIST" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="适配机型"><el-input v-model="form.adaptModel" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="显示方案"><el-select v-model="form.displaySupplierName" filterable allow-create style="width:100%"><el-option v-for="s in solutionSuppliers" :key="s.id" :label="s.name" :value="s.name" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="触摸方案"><el-select v-model="form.touchSupplierName" filterable allow-create style="width:100%"><el-option v-for="s in solutionSuppliers" :key="s.id" :label="s.name" :value="s.name" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="原机尺寸"><el-input v-model="form.originalSize" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="原分辨率"><el-input v-model="form.originalResolution" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="打样工厂">
                <div style="display:flex;gap:4px">
                  <el-select v-model="form.sampleFactoryId" clearable filterable style="flex:1" placeholder="选择工厂"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select>
                  <el-button v-if="form.sampleFactoryId" type="success" size="small" @click="goCreateOrder('sample')">下单</el-button>
                </div>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="委外工厂">
                <div style="display:flex;gap:4px">
                  <el-select v-model="form.outsourceFactoryId" clearable filterable style="flex:1" placeholder="选择工厂"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select>
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

        <div style="margin-top:12px"><el-button type="primary" :loading="saving" @click="handleSave">保存基本信息</el-button></div>
      </el-tab-pane>

      <!-- 阶段时间线 Tab -->
      <el-tab-pane label="阶段时间线" name="timeline">
        <el-card shadow="never">
          <el-table :data="timelineList" border size="small">
            <el-table-column prop="statusName" label="阶段" width="140" />
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
          <el-button type="primary" size="small" @click="saveTimeline" style="margin-top:8px">保存时间线</el-button>
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
                <span v-if="row._isChild" style="color:#909399;font-size:12px">{{ row.materialType }}</span>
                <el-select v-else v-model="row.materialType" size="small" style="width:100%" @change="row.materialName = ''">
                  <el-option v-for="t in bomTypes" :key="t" :label="t" :value="t" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="物料名称" min-width="130">
              <template #default="{row}">
                <span v-if="row._isChild" style="color:#409EFF;font-size:12px">└ {{ row.materialName }}</span>
                <el-select v-else v-model="row.materialName" size="small" filterable allow-create clearable style="width:100%" placeholder="选择" @change="(v: string) => onBomMaterialChange(v, row)"><el-option v-for="m in getMaterialsByType(row.materialType || '')" :key="m.id" :label="m.materialName" :value="m.materialName" /></el-select>
              </template>
            </el-table-column>
            <el-table-column label="供应商" width="100">
              <template #default="{row}">
                <span v-if="row._isChild" style="color:#909399;font-size:12px">{{ row.supplierId || '-' }}</span>
                <el-select v-else v-model="row.supplierId" size="small" clearable filterable style="width:100%">
                  <el-option v-for="s in allSuppliers" :key="s.id" :label="s.name" :value="s.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="规格" width="90"><template #default="{row}"><span v-if="row._isChild" style="color:#909399;font-size:12px">{{ row.spec }}</span><el-input v-else v-model="row.spec" size="small" /></template></el-table-column>
            <el-table-column label="单位" width="65"><template #default="{row}"><span v-if="row._isChild" style="color:#909399;font-size:12px">{{ row.unit }}</span><el-input v-else v-model="row.unit" size="small" /></template></el-table-column>
            <el-table-column label="用量" width="75"><template #default="{row}"><span v-if="row._isChild" style="font-size:12px">{{ row.quantityPerSet }}</span><el-input v-else v-model="row.quantityPerSet" size="small" /></template></el-table-column>
            <el-table-column label="损耗率%" width="80"><template #default="{row}"><span v-if="row._isChild" style="font-size:12px">{{ row.lossRate }}</span><el-input v-else v-model="row.lossRate" size="small" /></template></el-table-column>
            <el-table-column label="操作" width="60" align="center">
              <template #default="{$index}">
                <el-button v-if="!bomList[$index]._isChild" type="danger" link @click="removeBomRow($index)">删除</el-button>
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
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="fileUrl" label="文件" min-width="120" show-overflow-tooltip />
            <el-table-column prop="createTime" label="上传时间" width="160" />
            <el-table-column label="操作" width="130" align="center">
              <template #default="{row}">
                <el-button type="primary" link v-if="row.fileUrl" @click="downloadFile(row.fileUrl)">下载</el-button>
                <el-button type="danger" link @click="handleDeleteDrawing(row as DrawingVO)">删除</el-button>
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
              <el-option label="全部" value="全部"/><el-option label="显示" value="显示"/><el-option label="触摸" value="触摸"/><el-option label="结构" value="结构"/>
            </el-select>
          </div>

          <el-tabs v-model="bugTab" type="card" style="margin-top:4px">
            <el-tab-pane :label="'处理中 ('+filteredBugs.active.length+')'" name="active">
              <el-table :data="filteredBugs.active" border size="small">
                <el-table-column prop="code" label="编号" width="140" />
                <el-table-column prop="title" label="标题" min-width="150" />
                <el-table-column prop="bugType" label="类型" width="70" />
                <el-table-column prop="severity" label="严重程度" width="90" />
                <el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small" :type="row.status==='待处理'?'danger':row.status==='处理中'?'warning':'success'">{{row.status}}</el-tag></template></el-table-column>
                <el-table-column label="操作" width="120" align="center"><template #default="{row}"><el-button type="primary" link @click="handleEditBug(row as BugDTO)">编辑</el-button><el-button type="danger" link @click="handleDeleteBug(row as BugDTO)">删除</el-button></template></el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="'已关闭 ('+filteredBugs.closed.length+')'" name="closed">
              <el-table :data="filteredBugs.closed" border size="small" v-if="filteredBugs.closed.length>0">
                <el-table-column prop="code" label="编号" width="140" />
                <el-table-column prop="title" label="标题" min-width="150" />
                <el-table-column prop="bugType" label="类型" width="70" />
                <el-table-column prop="severity" label="严重程度" width="90" />
                <el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small" type="info">{{row.status}}</el-tag></template></el-table-column>
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
        <el-form-item label="严重程度"><el-select v-model="bugForm.severity" style="width:100%"><el-option label="致命" value="致命"/><el-option label="严重" value="严重"/><el-option label="一般" value="一般"/><el-option label="轻微" value="轻微"/></el-select></el-form-item>
        <el-form-item label="类型"><el-select v-model="bugForm.bugType" style="width:100%"><el-option label="显示" value="显示"/><el-option label="触摸" value="触摸"/><el-option label="结构" value="结构"/></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="bugForm.status" style="width:100%"><el-option label="待处理" value="待处理"/><el-option label="处理中" value="处理中"/><el-option label="已修复" value="已修复"/><el-option label="已验证" value="已验证"/><el-option label="已关闭" value="已关闭"/></el-select></el-form-item>
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
  </div>
</template>

<style scoped>
.edit-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:32px; text-align:center; transition:all .3s; cursor:pointer }
.drop-zone:hover { border-color:#409eff; background:#ecf5ff }
</style>
