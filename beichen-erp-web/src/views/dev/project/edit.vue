<script setup lang="ts">
// 2026-09-20（F7-159）：显式声明组件名（便于 DevTools 辨认与将来按名 exclude）
defineOptions({ name: 'DevProjectEdit' })
import { reactive, ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PhaseStatus, PhaseStatusLabel, SeverityType, SeverityTypeLabel, BugTypeEnum, BugTypeEnumLabel, BugStatus, BugStatusLabel, BugStatusTag, OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag, DevMaterialTypeLabel, DevMaterialStatusLabel, DevDrawingDocType, DevDrawingDocTypeLabel, DEV_PROJECT_DIRTY_KEY, ProductSpec, ProductSpecLabel } from '@/api/enums'
import {
  getProject, updateProject,
  getProjectBom, saveProjectBom, getProjectBomSnapshots,
  getProjectBugs, addProjectBug, updateProjectBug, deleteProjectBug,
  getProjectDrawings, addProjectDrawing, deleteProjectDrawing,
  type ProjectVO, type ProjectDTO, type BomDTO, type BugDTO, type DrawingVO
} from '@/api/system'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import request from '@/utils/request'
import MaterialFormDialog from '@/components/dev/MaterialFormDialog.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)

const saving = ref(false)
const activeTab = ref((route.query.tab as string) || 'project')

// ===================== 项目基础信息 =====================
const form = reactive<ProjectDTO>({
  name: '', displaySupplierName: '', touchSupplierName: '',
  // 2026-09-21：assemblyName 更名为 productName（连 DB 列名一起改）
  productName: '',
  // 2026-09-21 新增：规格（原配/改配，与关联产品联动）+ 产品SKU（详情接口回填关联产品的当前 SKU，可改）
  specType: '',
  productSku: '',
  adaptModel: '', originalSize: '', originalResolution: '',
  originalDriveIc: '', originalTouchIc: '',
  glassSize: '', glassResolution: '',
  configDriveIcId: undefined, configTouchIcId: undefined, configCodeIcId: undefined,
  startDate: '', expectedEndDate: '', remark: '',
  sampleFactoryId: undefined, outsourceFactoryId: undefined,
  brandId: undefined
})

/** 关联产品ID（2026-09-21）：未关联产品 ⇒ 没有 SKU 可改（新增态才建产品） */
const productId = ref<number | undefined>(undefined)
/** 进页面时产品的 SKU：用于判断"用户是否改过"，改过才二次确认并提交同步 */
const skuOnLoad = ref('')

/**
 * 进页面时的「规格」：2026-09-21 阶段模板分原配/改配两套后，规格决定"新建项目套哪一套模板"，
 * 但**已有阶段不回改**（阶段进度是历史快照）⇒ 改过时保存后给一句提示，避免用户以为阶段会跟着变。
 */
const specOnLoad = ref('')

/**
 * 需求 3：规格下拉只给「原配 / 改配」（与立项新增页同一口径）；改动会同步到关联产品。
 */
const SPEC_OPTIONS = [
  { value: ProductSpec.MATCHED, label: ProductSpecLabel[ProductSpec.MATCHED] },
  { value: ProductSpec.MODIFIED, label: ProductSpecLabel[ProductSpec.MODIFIED] }
]

/**
 * 需求 4：项目是「原配」时隐藏「显示方案 / 触摸方案 / 改配信息」。
 * <p>判定用 **`=== 原配`**：规格为空（历史项目）时保持显示，避免老项目字段凭空消失。</p>
 */
const isOriginalSpec = computed(() => form.specType === ProductSpec.MATCHED)
// 显示方案/触摸方案：可自由输入，选项取自方案商列表
const solutionSupplierOptions = ref<{ id: number; name: string }[]>([])
const allSuppliers = ref<any[]>([])
const factoryOptions = ref<{ id: number; name: string }[]>([])
// 品牌下拉（/brand/enabled）
const brandOptions = ref<{ id: number; brandName: string }[]>([])
async function loadBrandOptions() {
  try { brandOptions.value = (await request.get('/brand/enabled')) || [] } catch { /* 忽略 */ }
}

const fetchSolutionSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, supplierType: 'solution', name: kw } })
const fetchFactorySuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, supplierType: 'factory', name: kw } })
const fetchAllSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })

async function loadSolutionSuppliers() {
  const r: any = await fetchSolutionSuppliers(''); solutionSupplierOptions.value = (r?.records || []).map((s: any) => ({ id: s.id, name: s.name }))
}
async function loadAllSuppliers() {
  const r: any = await fetchAllSuppliers(''); allSuppliers.value = (r?.records || []) as any[]
}
async function loadFactories() {
  const r: any = await fetchFactorySuppliers(''); factoryOptions.value = (r?.records || []).map((s: any) => ({ id: s.id, name: s.name }))
}

/**
 * 未保存拦截（2026-09-23 统一模板）：本页各 Tab 可就地改项目信息/阶段/BOM/BUG/图纸 ⇒ 属"能改数据"，接守卫。
 * ⚠️ 必须写在 form 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline } = useUnsavedGuard(() => ({ form }))

async function loadProject() {
  const p = await getProject(projectId)
  Object.assign(form, {
    id: p.id, name: p.name, code: p.code,
    productName: p.productName,
    specType: p.specType,
    productSku: p.productSku || '',
    displaySupplierName: p.displaySupplierName, touchSupplierName: p.touchSupplierName,
    adaptModel: p.adaptModel, originalSize: p.originalSize, originalResolution: p.originalResolution,
    originalDriveIc: p.originalDriveIc, originalTouchIc: p.originalTouchIc,
    glassSize: p.glassSize, glassResolution: p.glassResolution,
    configDriveIcId: p.configDriveIcId, configTouchIcId: p.configTouchIcId, configCodeIcId: p.configCodeIcId,
    sampleFactoryId: p.sampleFactoryId, outsourceFactoryId: p.outsourceFactoryId,
    sampleFactoryName: p.sampleFactoryName, outsourceFactoryName: p.outsourceFactoryName,
    startDate: p.startDate, expectedEndDate: p.expectedEndDate,
    // F7-89：不回填 status —— 它是阶段推导的派生字段，本页不提交（后端已改为白名单字段更新）
    remark: p.remark
  })
  // 2026-09-21（需求 1）：记录"是否已关联产品"与"进页面时的 SKU"，供产品SKU 的可编辑性与二次确认判断
  productId.value = p.productId
  skuOnLoad.value = p.productSku || ''
  specOnLoad.value = p.specType || ''
  await loadConfigNames()
  // 数据加载完成 ⇒ 重建"未保存"基线（保存成功后也会重跑本函数 ⇒ 自动重置，不误报）
  takeBaseline()
}

// 改配信息物料名称回显
const configDriveIcName = ref(''), configTouchIcName = ref(''), configCodeIcName = ref('')
async function loadConfigNames() {
  const ids = [form.configDriveIcId, form.configTouchIcId, form.configCodeIcId].filter((v: any) => v != null)
  if (!ids.length) return
  try {
    const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } })
    const mats = (r?.records || [])
    const find = (id: any) => { const m = mats.find((x: any) => x.id === id); return m ? m.materialName : '' }
    configDriveIcName.value = find(form.configDriveIcId)
    configTouchIcName.value = find(form.configTouchIcId)
    configCodeIcName.value = find(form.configCodeIcId)
  } catch { /* 忽略 */ }
}

async function handleSave() {
  if (!form.name.trim()) { ElMessage.warning('请输入项目名称'); return }
  if (!form.productName || !form.productName.trim()) { ElMessage.warning('请输入产品名称'); return }
  // 2026-09-21 新增：规格必填（原配/改配）；改动会同步到关联产品
  if (!form.specType) { ElMessage.warning('请选择规格（原配/改配）'); return }
  // 2026-09-21（需求 1）：立项详细页也允许改「产品SKU」，但 SKU 是既有编码、历史单据里存的是快照
  // ⇒ 确有变化时才二次确认（未改过则不打扰用户）
  const newSku = form.productSku ? form.productSku.trim() : ''
  if (productId.value && newSku && newSku !== skuOnLoad.value) {
    try {
      await ElMessageBox.confirm(
        `产品SKU 将从「${skuOnLoad.value || '（空）'}」改为「${newSku}」。` +
        '已生成的单据里保存的是当时的 SKU 快照，不会随本次修改回改。确认修改？',
        '修改产品SKU确认',
        { confirmButtonText: '确认修改', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      // 取消 ⇒ 回滚输入框并结束本次保存
      await loadProject()
      return
    }
  }
  // 2026-09-21（阶段模板分原配/改配）：规格改动**不回改已有阶段**（阶段进度是历史快照），
  // 只影响之后新建的项目；保存成功后给一句明确提示，避免用户误以为阶段会跟着重算。
  const specChanged = !!form.specType && form.specType !== specOnLoad.value
  saving.value = true
  try {
    await updateProject(form as any)
    ElMessage.success('已保存'); invalidate('devProject')
    if (specChanged) {
      ElMessage.info('规格已变更：已有阶段不会重算（阶段进度是历史快照），仅之后新建的项目按新规格套用阶段模板')
    }
    await loadProject()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')); await loadProject() }
  saving.value = false
}

function goCreateOrder(type: 'sample' | 'outsource') {
  const factoryId = type === 'sample' ? form.sampleFactoryId : form.outsourceFactoryId
  if (!factoryId) return
  router.push({ path: '/outsource/order/add', query: { factoryId, projectId } })
}

// ===================== 项目阶段 =====================
interface PhaseItem { id?: number; phaseName: string; sortOrder: number; defaultDays?: number; plannedEnd?: string; actualEnd?: string; status?: string; remark?: string }
const phaseStatusOptions = [PhaseStatus.NOT_STARTED, PhaseStatus.IN_PROGRESS, PhaseStatus.FINISHED]
const phaseList = ref<PhaseItem[]>([])
const phaseCompleting = ref<Record<number, boolean>>({})

async function loadPhase() {
  const res = await request.get<PhaseItem[]>(`/dev/project/${projectId}/phase`)
  phaseList.value = res || []
}

// 从项目阶段推导当前项目阶段
const currentPhaseName = computed(() => {
  // 优先找"进行中"的阶段
  const active = phaseList.value.find(t => t.status === PhaseStatus.IN_PROGRESS)
  if (active) return active.phaseName
  // 没有进行中的，找最后一个已完成/已跳过的
  for (let i = phaseList.value.length - 1; i >= 0; i--) {
    if (phaseList.value[i].status === PhaseStatus.FINISHED
        || phaseList.value[i].status === PhaseStatus.SKIPPED) {
      return phaseList.value[i].phaseName
    }
  }
  // 都没有，返回模板第一项
  return phaseList.value.length > 0 ? phaseList.value[0].phaseName : '-'
})

async function savePhaseRow(row: any) {
  try {
    await request.put(`/dev/project/phase/${row.id}`, {
      id: row.id,
      projectId: projectId,
      phaseName: row.phaseName,
      sortOrder: row.sortOrder,
      defaultDays: row.defaultDays,
      plannedEnd: row.plannedEnd || null,
      actualEnd: row.actualEnd || null,
      status: row.status,
      remark: row.remark || null
    })
    // 更新日期或状态后刷新列表（后端可能后推了后续阶段）
    await loadPhase()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '')) }
}

async function completePhase(phaseId: number) {
  phaseCompleting.value[phaseId] = true
  try {
    await request.put(`/dev/project/phase/${phaseId}/complete`)
    ElMessage.success('阶段已完成'); invalidate('devProject')
    await loadPhase()
    await loadProject()
  } catch (e: any) { ElMessage.error('操作失败: ' + (e?.message || '')) }
  finally { phaseCompleting.value[phaseId] = false }
}

async function skipPhase(phaseId: number) {
  phaseCompleting.value[phaseId] = true
  try {
    await request.put(`/dev/project/phase/${phaseId}/skip`)
    ElMessage.success('阶段已跳过'); invalidate('devProject')
    await loadPhase()
    await loadProject()
  } catch (e: any) { ElMessage.error('操作失败: ' + (e?.message || '')) }
  finally { phaseCompleting.value[phaseId] = false }
}

/** 撤销阶段 */
async function revertPhase(phaseId: number) {
  try {
    await ElMessageBox.confirm('撤销后将恢复该阶段为进行中，后续阶段将全部重置为未开始。确认撤销？', '确认撤销', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await request.put(`/dev/project/phase/${phaseId}/revert`)
    ElMessage.success('阶段已撤销'); invalidate('devProject')
    await loadPhase()
    await loadProject()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败: ' + (e?.message || ''))
  }
}

/**
 * 一键完成（2026-09-21 新增）：把本项目所有未完成阶段一次标为完成。
 * <p>后端在同一事务内批量推进，**终态与逐个点「完成」完全一致** —— 全部完成后项目会自动结项，
 * 若关联产品处于"研发中"其状态会同步为"正常"。故此处**必须二次确认**，把这两个后果写清楚。</p>
 */
const phaseCompletingAll = ref(false)
async function completeAllPhases() {
  const pending = phaseList.value.filter(
    t => t.status !== PhaseStatus.FINISHED && t.status !== PhaseStatus.SKIPPED).length
  if (pending === 0) { ElMessage.info('所有阶段均已完成'); return }
  try {
    await ElMessageBox.confirm(
      `将把本项目全部未完成阶段一次性标记为完成（共 ${pending} 个），并自动结项；` +
      '若关联产品处于"研发中"，其状态将同步为"正常"。如需回退，需在下方列表逐个「撤销」。确认继续？',
      '一键完成确认',
      { confirmButtonText: '确认完成', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }
  phaseCompletingAll.value = true
  try {
    const n: any = await request.put(`/dev/project/${projectId}/phase/complete-all`)
    ElMessage.success(n ? `已完成 ${n} 个阶段` : '所有阶段均已完成')
    invalidate('devProject')
    await loadPhase()
    // 必须同时刷新项目：全完成后后端已自动结项，基础信息页签顶部的状态要跟着变
    await loadProject()
  } catch (e: any) {
    ElMessage.error('操作失败: ' + (e?.message || ''))
  } finally { phaseCompletingAll.value = false }
}

/** 级联重算计划日期 */
async function recalcPlannedEnds() {
  try {
    await ElMessageBox.confirm('将以第一个进行中阶段为起点，级联推算后续所有未开始阶段的计划日期。确认重算？', '确认重算', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' })
    await request.put(`/dev/project/phase/recalc`, null, { params: { projectId } })
    ElMessage.success('计划日期已重算'); invalidate('devProject')
    await loadPhase()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败: ' + (e?.message || ''))
  }
}

// 进度统计（使用枚举比较）
const phaseProgress = computed(() => {
  const total = phaseList.value.length
  const completed = phaseList.value.filter(t => t.status === PhaseStatus.FINISHED || t.status === PhaseStatus.SKIPPED).length
  const inProgress = phaseList.value.filter(t => t.status === PhaseStatus.IN_PROGRESS).length
  return { total, completed, inProgress, pct: total > 0 ? Math.round(completed / total * 100) : 0 }
})

// 行样式（使用枚举比较）
function phaseRowClass({ row }: { row: PhaseItem }) {
  if (row.status === PhaseStatus.FINISHED || row.status === PhaseStatus.SKIPPED) return 'phase-row-done'
  if (row.status === PhaseStatus.IN_PROGRESS) return 'phase-row-active'
  return ''
}

// BOM 平铺列表（父+子混排，子行只读缩进）
const bomList = ref<any[]>([])
const materialTypes = ref<any[]>([])
const allMaterials = ref<any[]>([])
const fetchMaterialTypes = (kw: string) => request.get('/dev/material-type/enabled', { params: { kw } })
const fetchMaterials = (kw: string) => request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw } })
// 按 物料类型过滤物料（实时查库，供 BOM 物料名称下拉使用）
function fetchMaterialsByType(kw: string, row: any) {
  return request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw, materialTypeId: row.materialTypeId || undefined } })
}
async function loadMaterialTypes() {
  const bt: any = await fetchMaterialTypes(''); materialTypes.value = bt || []
  const m: any = await fetchMaterials(''); allMaterials.value = (m?.records || []) as any[]
}
// 物料类型名 -> id 映射，用于改配信息物料下拉按类型过滤
const materialTypeIdMap = computed<Record<string, number>>(() => {
  const m: Record<string, number> = {}
  for (const t of materialTypes.value) m[t.typeName] = t.id
  return m
})
function fetchConfigMaterials(kw: string, typeName: string) {
  const typeId = materialTypeIdMap.value[typeName]
  return request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw, materialTypeId: typeId || undefined } })
}
// 跳转到物料信息管理并定位到对应 物料类型 TAB
function goMaterialInfo(typeName: string) {
  router.push({ path: '/outsource/material-info', query: { materialTypeId: materialTypeIdMap.value[typeName] } })
}
// 物料类型 id -> 类型名 映射，用于回显
const materialTypeNameMap = computed<Record<number, string>>(() => {
  const m: Record<number, string> = {}
  for (const t of materialTypes.value) m[t.id] = t.typeName
  return m
})
function getMaterialsByType(type: any) { return allMaterials.value.filter((m:any) => m.materialTypeId != null && m.materialTypeId === type) }

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
    result.push({ _isChild: false, materialName: matName, outsourceMaterialId: matId, supplierId: b.supplierId, spec: b.specification, unit: b.unit, quantityPerSet: b.quantity, lossRate: b.lossRate, materialTypeId: b.materialTypeId, materialTypeName: materialTypeNameMap.value[b.materialTypeId ?? 0] || '', remark: '', id: b.id })
    const subs = childrenMap[String(matId)] || []
    for (const s of subs) {
      result.push({ _isChild: true, materialName: s.childName || s.materialName, materialTypeName: s.childType || '', quantityPerSet: s.quantity, lossRate: s.lossRate, remark: s.remark })
    }
  }
  bomList.value = result
}

function addBomRow() { bomList.value.push({ _isChild: false, materialName: '', outsourceMaterialId: undefined, spec: '', unit: '', quantityPerSet: 1, lossRate: 2, materialTypeId: '', remark: '', supplierId: undefined }) }
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
  const emptyType = parents.find((b: any) => !b.materialTypeId)
  if (emptyType) { ElMessage.warning('物料类型不能为空'); return }
  const emptyMatId = parents.find((b: any) => !b.outsourceMaterialId)
  if (emptyMatId) { ElMessage.warning('物料名称不能为空'); return }
  const zeroQty = parents.find((b: any) => !b.quantityPerSet || Number(b.quantityPerSet) <= 0)
  if (zeroQty) { ElMessage.warning('物料用量必须大于0'); return }
  // 转换为后端 Bom 实体格式
  const bomData = parents.map((b: any) => ({
    id: b.id,
    projectId,
    materialTypeId: b.materialTypeId,
    outsourceMaterialId: b.outsourceMaterialId,
    supplierId: b.supplierId,
    quantity: b.quantityPerSet,
    lossRate: b.lossRate,
    specification: b.spec,
    unit: b.unit
  }))
  await saveProjectBom(projectId, bomData)
  ElMessage.success('BOM已保存'); invalidate('devProject')
  await loadBom()
  // BOM 为准：同步刷新改配信息（驱动IC/触摸IC/码片IC）回显
  const p: any = await getProject(projectId)
  form.configDriveIcId = p.configDriveIcId
  form.configTouchIcId = p.configTouchIcId
  form.configCodeIcId = p.configCodeIcId
  await loadConfigNames()
}

// ===================== BOM 历史快照（2026-09-17） =====================
// 下加工单时按「产品 + 研发BOM版本 + 明细内容」生成：研发BOM没变就沿用上一份快照（多张加工单共享），
// 只有"有变化"（研发BOM升版本 或 下单时改了损耗率/供料方）时才新增一份。这里回看历史与流向。
const snapDialog = ref(false)
const snapLoading = ref(false)
const snapList = ref<any[]>([])
async function openSnapshots() {
  // 2026-09-23 用户要求：BOM 历史快照由 900px 弹框改为独立页面（只读回看）
  router.push(`/dev/bom-snapshot/${projectId}`)
}
function snapKindText(kind: string) {
  if (kind === 'BOM') return '与研发BOM一致'
  if (kind === 'ORDER') return '下单时明细有调整'
  if (kind === 'MIGRATED') return '历史订单迁移'
  return kind || '-'
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
function handleAddBug() { Object.assign(bugForm, { id: undefined, title: '', severity: SeverityType.NORMAL, bugType: BugTypeEnum.DISPLAY, status: BugStatus.OPEN, description: '' }); isBugEdit.value = false; bugDialogVisible.value = true }
function handleEditBug(row: BugDTO) { Object.assign(bugForm, row); isBugEdit.value = true; bugDialogVisible.value = true }
async function handleBugSubmit() {
  if (isBugEdit.value && bugForm.id) { await updateProjectBug(projectId, bugForm); ElMessage.success('已更新'); invalidate('devProject') }
  else { await addProjectBug(projectId, bugForm); ElMessage.success('已添加'); invalidate('devProject') }
  bugDialogVisible.value = false; loadBugs()
}
async function handleDeleteBug(row: BugDTO) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await deleteProjectBug(projectId, row.id!); ElMessage.success('已删除'); loadBugs(); invalidate('devProject') } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

// ===================== 图纸（含排线图纸上传） =====================
const drawingList = ref<DrawingVO[]>([])
async function loadDrawings() { drawingList.value = (await getProjectDrawings(projectId)) || [] }
const drawingVisible = ref(false)
const drawingForm = reactive({ docName: '', docType: DevDrawingDocType.DRAWING, version: 'v1.0', fileUrl: '' })
const uploadFile = ref<File | null>(null)
const uploading = ref(false)

function handleAddDrawing() { 
  Object.assign(drawingForm, { id: undefined, docName: '', docType: DevDrawingDocType.DRAWING, version: 'v1.0', fileUrl: '' })
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
    ElMessage.success('图纸已上传'); drawingVisible.value = false; loadDrawings(); invalidate('devProject')
  } catch (e: any) { ElMessage.error('上传失败: ' + (e?.message || '未知错误')) } finally { uploading.value = false }
}
function downloadFile(url: string) { window.open(url) }
async function handleDeleteDrawing(row: DrawingVO) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await deleteProjectDrawing(projectId, row.id!); ElMessage.success('已删除'); loadDrawings(); invalidate('devProject') } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

// ===================== 项目物料 =====================
interface DevPurchaseItem {
  id?: number
  projectId?: number
  name: string
  type: string
  quantity: number
  locationDetail: string
  warehouseName: string
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

// 打开共用新增弹窗（自动锁定当前项目）
function handleAddDevMaterial() { materialDialog.value?.open() }
// 进入物料独立详情页
function handleDetailDevMaterial(row: any) {
  router.push({ path: `/dev/material/detail/${row.id}`, query: { projectId } })
}

async function handleDeleteDevMaterial(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该记录吗？', '提示', { type: 'warning' })
    await request.delete(`/dev/purchase-item/${row.id}`)
    ElMessage.success('已删除'); invalidate('devProject')
    loadDevMaterials()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

// 切换 Tab 时自动加载 BOM 数据
watch(activeTab, async (tab) => { if (tab === 'bom') await loadBom() })

// 顶栏"刷新数据"：重新加载方案供应商下拉
async function handleRefreshData() { await loadSolutionSuppliers() }
onMounted(() => { loadProject(); loadSolutionSuppliers(); loadAllSuppliers(); loadFactories(); loadBrandOptions(); loadMaterialTypes(); loadPhase(); loadBom(); loadBugs(); loadDrawings(); loadDevMaterials(); window.addEventListener('refresh:dropdown-data', handleRefreshData) })
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))




function onNameBlur() {
  if (!form.productName || !form.productName.trim()) {
    form.productName = form.name
  }
}
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta)；
       本页是多 Tab 页，各 Tab 内的保存/完成按钮作用域是该 Tab ⇒ 保留原位置 -->
  <PageShell back-fallback="/dev/project">
    <el-tabs v-model="activeTab">
      <!-- 项目信息 Tab -->
      <el-tab-pane label="项目信息" name="project">
        <!-- 基础信息 -->
        <el-card shadow="never">
          <template #header><span style="font-weight:600">基础信息</span></template>
          <el-form :model="form" label-width="var(--app-label-width)" size="default">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="项目编码"><el-input :model-value="form.code" disabled /></el-form-item></el-col>
              <el-col :span="8"><el-form-item required label="项目名称"><el-input v-model="form.name" @blur="onNameBlur" /></el-form-item></el-col>
              <!-- 需求 1：产品SKU（关联产品后才可改；改动需二次确认）／需求 2：原「总成名称」更名为「产品名称」 -->
              <el-col :span="8"><el-form-item label="产品SKU" prop="productSku" :rules="[{ required: !!productId, message: '请输入产品SKU', trigger: 'blur' }]">
                <el-input v-model="form.productSku" maxlength="64" :disabled="!productId" placeholder="自动生成，可修改" />
                <div v-if="!productId" style="font-size:var(--app-font-xs);color:var(--app-text-secondary);line-height:1.4">
                  该项目未关联产品，无 SKU 可改
                </div>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="产品名称" prop="productName" :rules="[{ required: true, message: '请输入产品名称', trigger: 'blur' }]"><el-input v-model="form.productName" /></el-form-item></el-col>
              <!-- 需求 3：规格（原配/改配）——改动会同步到关联产品规格，但**不回改已有阶段** -->
              <el-col :span="8"><el-form-item label="规格" prop="specType" :rules="[{ required: true, message: '请选择规格', trigger: 'change' }]">
                <el-select v-model="form.specType" placeholder="请选择（原配/改配）" style="width:100%">
                  <el-option v-for="o in SPEC_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
                <div style="font-size:var(--app-font-xs);color:var(--app-text-secondary);line-height:1.4">
                  规格决定新建项目套用哪一套阶段模板（原配 7 个阶段 / 改配 14 个）；改规格不会重算已有阶段
                </div>
              </el-form-item></el-col>

              <el-col :span="8"><el-form-item label="立项日期"><el-input v-model="form.startDate" type="date" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="预计完成"><el-input v-model="form.expectedEndDate" type="date" /></el-form-item></el-col>
              <!-- 制单人（2026-09-23 用户口径：研发立项也显示制单人；**无审核流程 ⇒ 不显示审核人**） -->
              <el-col :span="8"><el-form-item label="制单人"><el-input :model-value="form.createByName || '—'" readonly /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="当前阶段">
                <el-tag type="warning" size="default">{{ currentPhaseName }}</el-tag>
              </el-form-item></el-col>

              <el-col :span="8"><el-form-item label="适配机型"><el-input v-model="form.adaptModel" /></el-form-item></el-col>
              <!-- 需求 4：规格=原配 ⇒ 隐藏「显示方案 / 触摸方案」 -->
              <el-col v-if="!isOriginalSpec" :span="8"><el-form-item label="显示方案"><el-select v-model="form.displaySupplierName" filterable allow-create style="width:100%" @change="(v: string) => { if (v === ADD_MARKER) { form.displaySupplierName = ''; router.push('/supplier/manage'); return } }"><el-option v-for="s in solutionSupplierOptions" :key="s.id" :label="s.name" :value="s.name" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item></el-col>
              <el-col v-if="!isOriginalSpec" :span="8"><el-form-item label="触摸方案"><el-select v-model="form.touchSupplierName" filterable allow-create style="width:100%" @change="(v: string) => { if (v === ADD_MARKER) { form.touchSupplierName = ''; router.push('/supplier/manage'); return } }"><el-option v-for="s in solutionSupplierOptions" :key="s.id" :label="s.name" :value="s.name" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item></el-col>

              <el-col :span="8"><el-form-item label="打样工厂">
                <div style="display:flex;gap:4px;align-items:center">
                  <RemoteSelect v-model="form.sampleFactoryId" :fetch="fetchFactorySuppliers" clearable placeholder="选择工厂" :preset="form.sampleFactoryId ? { id: form.sampleFactoryId, name: form.sampleFactoryName } : null" @change="(v: any) => { if (v === ADD_MARKER) { form.sampleFactoryId = undefined; router.push('/supplier/manage'); return } }" domain="vendor" ><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
                  <el-button v-if="form.sampleFactoryId" type="success" @click="goCreateOrder('sample')">下单</el-button>
                </div>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="委外工厂">
                <div style="display:flex;gap:4px;align-items:center">
                  <RemoteSelect v-model="form.outsourceFactoryId" :fetch="fetchFactorySuppliers" clearable placeholder="选择工厂" :preset="form.outsourceFactoryId ? { id: form.outsourceFactoryId, name: form.outsourceFactoryName } : null" @change="(v: any) => { if (v === ADD_MARKER) { form.outsourceFactoryId = undefined; router.push('/supplier/manage'); return } }" domain="vendor" ><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
                  <el-button v-if="form.outsourceFactoryId" type="success" @click="goCreateOrder('outsource')">下单</el-button>
                </div>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="品牌"><el-select v-model="form.brandId" filterable clearable placeholder="选择品牌" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.brandId = undefined; router.push('/inventory/brand'); return } }"><el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item></el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
            </el-row>

            <!-- 原机配置 -->
            <el-divider content-position="left">原机配置</el-divider>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="原机尺寸"><el-input v-model="form.originalSize" placeholder="如 6.1寸" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="原分辨率"><el-input v-model="form.originalResolution" placeholder="如 1080×2400" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="驱动IC"><el-input v-model="form.originalDriveIc" placeholder="原机驱动IC型号" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="触摸IC"><el-input v-model="form.originalTouchIc" placeholder="原机触摸IC型号" /></el-form-item></el-col>
            </el-row>

            <!-- 改配信息（需求 4：规格=原配 ⇒ 整块隐藏） -->
            <el-divider v-if="!isOriginalSpec" content-position="left">改配信息</el-divider>
            <el-row v-if="!isOriginalSpec" :gutter="16">
              <el-col :span="8"><el-form-item label="玻璃尺寸"><el-input v-model="form.glassSize" placeholder="如 6.1寸" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="玻璃分辨率"><el-input v-model="form.glassResolution" placeholder="如 1080×2400" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="驱动IC">
                <RemoteSelect v-model="form.configDriveIcId" domain="material" :fetch="(kw: string) => fetchConfigMaterials(kw, '驱动IC')" :label-key="$mLabel" clearable filterable :preset="form.configDriveIcId ? { id: form.configDriveIcId, materialName: configDriveIcName } : null" style="width:100%" placeholder="选择驱动IC物料" @change="(v: any) => { if (v === ADD_MARKER) { form.configDriveIcId = undefined; goMaterialInfo('驱动IC'); return } }"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="触摸IC">
                <RemoteSelect v-model="form.configTouchIcId" domain="material" :fetch="(kw: string) => fetchConfigMaterials(kw, '触摸IC')" :label-key="$mLabel" clearable filterable :preset="form.configTouchIcId ? { id: form.configTouchIcId, materialName: configTouchIcName } : null" style="width:100%" placeholder="选择触摸IC物料" @change="(v: any) => { if (v === ADD_MARKER) { form.configTouchIcId = undefined; goMaterialInfo('触摸IC'); return } }"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="码片IC">
                <RemoteSelect v-model="form.configCodeIcId" domain="material" :fetch="(kw: string) => fetchConfigMaterials(kw, '码片IC')" label-key="materialName" clearable filterable :preset="form.configCodeIcId ? { id: form.configCodeIcId, materialName: configCodeIcName } : null" style="width:100%" placeholder="选择码片IC物料" @change="(v: any) => { if (v === ADD_MARKER) { form.configCodeIcId = undefined; goMaterialInfo('码片IC'); return } }"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
              </el-form-item></el-col>
            </el-row>
          </el-form>
        </el-card>

        <div style="margin-top:12px"><el-button type="primary" :loading="saving" @click="handleSave">保存基础信息</el-button></div>
      </el-tab-pane>

      <!-- 项目阶段 Tab -->
      <el-tab-pane label="项目阶段" name="phase">
        <el-card shadow="never">
          <!-- 进度概览 + 操作按钮 -->
          <div style="margin-bottom:12px;display:flex;align-items:center;gap:16px;flex-wrap:wrap">
            <span style="font-size:var(--app-font-base);font-weight:600">进度概览</span>
            <div style="flex:1;max-width:360px">
              <el-progress :percentage="phaseProgress.pct" :stroke-width="16" 
                :color="phaseProgress.pct === 100 ? 'var(--app-color-success)' : 'var(--app-color-primary)'">
                <span style="font-size:var(--app-font-xs)">{{ phaseProgress.completed }} / {{ phaseProgress.total }} 已完成</span>
              </el-progress>
            </div>
            <el-tag v-if="phaseProgress.inProgress > 0" type="warning" size="small">{{ phaseProgress.inProgress }} 个进行中</el-tag>
            <!-- 2026-09-21：一键完成（主操作靠前）；会连带自动结项，故点击后二次确认 -->
            <el-button type="success" size="small" :loading="phaseCompletingAll"
              :disabled="phaseProgress.total === 0" @click="completeAllPhases">一键完成</el-button>
            <el-button type="primary" size="small" plain @click="recalcPlannedEnds">重算计划日期</el-button>
          </div>

          <el-table :data="phaseList" border size="small" :row-class-name="phaseRowClass">
            <el-table-column label="排序" width="55" align="center"><template #default="{row}">{{ row.sortOrder }}</template></el-table-column>
            <el-table-column prop="phaseName" label="阶段名称" width="140" />
            <el-table-column label="默认天数" width="75" align="center"><template #default="{row}">{{ row.defaultDays || '-' }}</template></el-table-column>
            <el-table-column label="计划完成" width="150">
              <template #default="{row}">
                <el-input v-model="row.plannedEnd" type="date" size="small" 
                  :disabled="row.status === PhaseStatus.FINISHED || row.status === PhaseStatus.SKIPPED" @change="savePhaseRow(row)" />
              </template>
            </el-table-column>
            <el-table-column label="实际完成" width="150">
              <template #default="{row}">
                <el-input v-model="row.actualEnd" type="date" size="small" @change="savePhaseRow(row)" />
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="140">
              <template #default="{row}">
                <el-input v-model="row.remark" size="small" placeholder="可选" @change="savePhaseRow(row)" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{row}">
                <el-tag v-if="row.status === PhaseStatus.FINISHED" type="success" size="small">{{ PhaseStatusLabel[row.status] }}</el-tag>
                <el-tag v-else-if="row.status === PhaseStatus.IN_PROGRESS" type="warning" size="small">{{ PhaseStatusLabel[row.status] }}</el-tag>
                <el-tag v-else-if="row.status === PhaseStatus.SKIPPED" type="info" size="small" style="border-style:dashed">{{ PhaseStatusLabel[row.status] }}</el-tag>
                <el-select v-else v-model="row.status" size="small" style="width:90px" @change="savePhaseRow(row)">
                  <el-option v-for="o in phaseStatusOptions" :key="o" :label="PhaseStatusLabel[o]" :value="o" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center">
              <template #default="{row}">
                <div style="display:flex;justify-content:center;align-items:center;gap:4px">
                <el-button v-if="row.id && row.status === PhaseStatus.IN_PROGRESS" type="success" size="small"
                  :loading="phaseCompleting[row.id]" @click="completePhase(row.id)">
                  完成
                </el-button>
                <el-button v-if="row.id && row.status === PhaseStatus.IN_PROGRESS" type="warning" size="small"
                  plain @click="skipPhase(row.id)">
                  跳过
                </el-button>
                <el-button v-if="row.id && (row.status === PhaseStatus.FINISHED || row.status === PhaseStatus.SKIPPED)" type="danger" size="small"
                  plain @click="revertPhase(row.id)">
                  撤销
                </el-button>
                </div>
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
            <el-radio-group v-model="bugTab" size="small" style="margin-left:8px">
              <el-radio-button value="active">处理中 ({{ filteredBugs.active.length }})</el-radio-button>
              <el-radio-button value="closed">已关闭 ({{ filteredBugs.closed.length }})</el-radio-button>
            </el-radio-group>
          </div>

          <!-- 处理中 -->
          <el-table v-show="bugTab === 'active'" :data="filteredBugs.active" border size="small">
            <el-table-column prop="code" label="编号" width="240" />
            <el-table-column prop="title" label="标题" min-width="150" />
            <el-table-column prop="bugType" label="类型" width="70"><template #default="{row}">{{ BugTypeEnumLabel[row.bugType] || row.bugType }}</template></el-table-column>
            <el-table-column prop="severity" label="严重程度" width="90"><template #default="{row}">{{ SeverityTypeLabel[row.severity] || row.severity }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small" :type="BugStatusTag[row.status] || 'info'">{{ BugStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="120" align="center"><template #default="{row}"><el-button type="primary" link @click="handleEditBug(row as BugDTO)">编辑</el-button><el-button type="danger" link @click="handleDeleteBug(row as BugDTO)">删除</el-button></template></el-table-column>
            <template #empty><div style="color:var(--app-text-secondary);padding:16px;text-align:center">暂无处理中的BUG</div></template>
          </el-table>

          <!-- 已关闭 -->
          <el-table v-show="bugTab === 'closed'" :data="filteredBugs.closed" border size="small">
            <el-table-column prop="code" label="编号" width="240" />
            <el-table-column prop="title" label="标题" min-width="150" />
            <el-table-column prop="bugType" label="类型" width="70"><template #default="{row}">{{ BugTypeEnumLabel[row.bugType] || row.bugType }}</template></el-table-column>
            <el-table-column prop="severity" label="严重程度" width="90"><template #default="{row}">{{ SeverityTypeLabel[row.severity] || row.severity }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small" type="info">{{ BugStatusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="120" align="center"><template #default="{row}"><el-button type="primary" link @click="handleEditBug(row as BugDTO)">编辑</el-button><el-button type="danger" link @click="handleDeleteBug(row as BugDTO)">删除</el-button></template></el-table-column>
            <template #empty><div style="color:var(--app-text-secondary);padding:16px;text-align:center">暂无已关闭的BUG</div></template>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- BOM信息 Tab -->
      <el-tab-pane label="BOM信息" name="bom">
        <el-card shadow="never">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
            <el-button type="primary" size="small" @click="addBomRow">+ 添加物料</el-button>
            <el-button type="success" size="small" @click="saveBom">保存</el-button>
            <!-- 历史快照：下加工单时按「研发BOM版本 + 明细内容」生成/共享（2026-09-17） -->
            <el-button size="small" @click="openSnapshots">历史快照</el-button>
            <!-- 2026-10-09（用户需求）：损耗率% 已放开为可就地修改（0~100，点「保存」生效）；用量仍为只读 -->
            <span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">用量在研发侧为只读（可在「加工单下单」时调整）；<b>损耗率% 可直接修改</b>，点「保存」生效</span>
          </div>
          <el-table :data="bomList" border size="small">
            <el-table-column label="类型" width="100">
              <template #default="{row}">
                <span v-if="row._isChild" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ row.materialTypeName }}</span>
                <RemoteSelect v-else v-model="row.materialTypeId" :fetch="fetchMaterialTypes" label-key="typeName" size="small" clearable style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.materialTypeId = ''; router.push('/dev/material-type'); return } row.outsourceMaterialId = undefined; row.materialName = '' }" domain="materialType" >
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </RemoteSelect>
              </template>
            </el-table-column>
            <el-table-column label="物料名称" min-width="130">
              <template #default="{row}">
                <span v-if="row._isChild" style="color:var(--app-color-primary);font-size:var(--app-font-xs)">└ {{ row.materialName }}</span>
                <RemoteSelect v-else v-model="row.outsourceMaterialId" :fetch="(kw: string) => fetchMaterialsByType(kw, row)" label-key="materialName" size="small" filterable clearable disable-cache style="width:100%" placeholder="选择" :preset="row.outsourceMaterialId ? { id: row.outsourceMaterialId, materialName: row.materialName } : null" @change="(v: any) => { if (v === ADD_MARKER) { row.outsourceMaterialId = undefined; router.push('/outsource/material-info'); return } onBomMaterialChange(v, row) }">
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </RemoteSelect>
              </template>
            </el-table-column>
            <el-table-column label="供应商" width="100">
              <template #default="{row}">
                <span v-if="row._isChild" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">-</span>
                <RemoteSelect v-else v-model="row.supplierId" :fetch="fetchAllSuppliers" size="small" clearable filterable style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.supplierId = undefined; router.push('/supplier/manage'); return } }" domain="supplier" >
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </RemoteSelect>
              </template>
            </el-table-column>
            <el-table-column label="规格" width="90"><template #default="{row}"><span v-if="row._isChild" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">-</span><el-input v-else v-model="row.spec" size="small" /></template></el-table-column>
            <el-table-column label="单位" width="65"><template #default="{row}"><span v-if="row._isChild" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">-</span><el-input v-else v-model="row.unit" size="small" /></template></el-table-column>
            <el-table-column label="用量" width="75"><template #header><span title="只读展示：用量在「加工单下单」时可调">用量</span></template><template #default="{row}"><span :style="{fontSize:'12px'}">{{ row.quantityPerSet }}</span></template></el-table-column>
            <!-- 2026-10-09（用户需求「研发立项详情的BOM信息的损耗率%需要可以修改」）：
                 损耗率% 改为**父行可就地编辑**（子行是物料信息带出的只读展示，不落库）。
                 口径 = 百分数 0~100（与「加工单下单」页同口径）；走同一条「保存」整表提交（saveBom 本就提交 lossRate）。
                 用量仍保持只读 —— 本次只放开损耗率。 -->
            <el-table-column label="损耗率%" width="90">
              <template #header><span title="研发侧可直接修改；下加工单时仍可按单调整">损耗率%</span></template>
              <template #default="{row}">
                <span v-if="row._isChild" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">{{ row.lossRate }}</span>
                <el-input-number v-else v-model="row.lossRate" size="small" :min="0" :max="100" :precision="2" :controls="false" style="width:100%" />
              </template>
            </el-table-column>
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
            <el-table-column label="类型" width="100"><template #default="{row}">{{ DevDrawingDocTypeLabel[row.docType] || row.docType }}</template></el-table-column>
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
          <div style="margin-bottom:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
            仅记录项目研发自购用料（如机板、原屏幕），与 BOM 表、委外物料无关
          </div>
          <div style="margin-bottom:8px">
            <el-button type="primary" size="small" @click="handleAddDevMaterial">+ 新增项目用料</el-button>
          </div>
          <el-table :data="devMaterialList" border stripe size="small">
            <el-table-column prop="name" label="物料名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="类型" width="120"><template #default="{ row }">{{ DevMaterialTypeLabel[row.type] || row.type }}</template></el-table-column>
            <el-table-column prop="quantity" label="数量" width="90" align="center" />
            <el-table-column prop="amount" label="金额" width="110" align="right">
              <template #default="{ row }">{{ row.amount ? '¥' + Number(row.amount).toFixed(2) : '-' }}</template>
            </el-table-column>
            <el-table-column label="存放位置" width="140">
              <template #default="{ row }">{{ row.warehouseName || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'GOOD' ? 'success' : row.status === 'DAMAGED' ? 'danger' : 'warning'">{{ DevMaterialStatusLabel[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleDetailDevMaterial(row)">详情</el-button>
                <el-button type="danger" link @click="handleDeleteDevMaterial(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

    </el-tabs>


    <!-- BUG 弹窗 -->
    <el-dialog v-model="bugDialogVisible" :title="isBugEdit?'编辑BUG':'新增BUG'" width="var(--app-dialog-sm)">
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
    <el-dialog v-model="drawingVisible" title="上传图纸" width="var(--app-dialog-sm)">
      <!-- 拖拽上传区域 -->
      <div class="drop-zone" 
        @dragover="handleDragOver" @drop="handleDrop"
        :style="{ borderColor: uploadFile ? 'var(--app-color-success)' : 'var(--app-border-color)', background: uploadFile ? '#f0f9eb' : '#fafafa' }">
        <template v-if="uploadFile">
          <p style="color:var(--app-color-success);font-weight:600;margin:0">📎 {{ uploadFile.name }}</p>
          <p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:4px 0 0">{{ (uploadFile.size/1024).toFixed(1) }} KB</p>
        </template>
        <template v-else>
          <p style="color:var(--app-text-secondary);margin:0">拖拽文件到此处，或点击下方按钮选择</p>
        </template>
        <input type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
      <el-form :model="drawingForm" label-width="80px" style="margin-top:12px">
        <el-form-item label="文档名称"><el-input v-model="drawingForm.docName" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="drawingForm.docType" style="width:100%"><el-option v-for="(lb, code) in DevDrawingDocTypeLabel" :key="code" :label="lb" :value="code"/></el-select></el-form-item>
        <el-form-item label="版本"><el-input v-model="drawingForm.version" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="drawingVisible=false">取消</el-button><el-button type="primary" :loading="uploading" @click="handleDrawingSubmit">确定</el-button></template>
    </el-dialog>

    <!-- 项目物料弹窗（共用组件，自动锁定当前项目） -->
    <MaterialFormDialog ref="materialDialog" :default-project-id="projectId" @saved="loadDevMaterials" />
  </PageShell>
</template>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .edit-page / .page-header 已删除
   （.page-header 与全局类同名，留着会双重生效；模板里也早已没有该 class 的节点） */

.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:32px; text-align:center; transition:all .3s; cursor:pointer }
.drop-zone:hover { border-color:var(--app-color-primary); background:#ecf5ff }

/* 项目阶段行样式 */
:deep(.phase-row-done) { background-color: #f0f9eb; }
:deep(.phase-row-active) { background-color: #fdf6ec; }
</style>

