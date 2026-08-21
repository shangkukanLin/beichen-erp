<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import { useOptionsStore } from '@/stores/options'

const optionsStore = useOptionsStore()
const query = reactive({ materialName: '', projectId: undefined as any })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const allMaterials = ref<any[]>([])  // 全部物料，不受TAB过滤，供子物料下拉框使用
const tableLoading = ref(false)
const projectOptions = ref<any[]>([])
const supplierOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
const MATERIAL_TYPES = ref<any[]>([])

// Tab 切换 - 物料类型（按 bomTypeId 过滤）
const activeTab = ref<number | string>('全部')

async function loadOptions() {
  await optionsStore.ensureBomTypes(); MATERIAL_TYPES.value = optionsStore.bomTypes || []
  await optionsStore.ensureProjects(); projectOptions.value = optionsStore.projects || []
  await optionsStore.ensureSuppliers('all'); supplierOptions.value = optionsStore.suppliers['suppliers:all'] || []
  await optionsStore.ensureWarehouses(); warehouseOptions.value = optionsStore.warehouses || []
}

// 按供应商ID列表(逗号分隔)查出供应商名称并拼接展示，空安全返回 '-'
function supplierNames(ids: string | undefined) {
  if (!ids || !ids.trim()) return '-'
  return ids.split(',').map(id => {
    const s = supplierOptions.value.find(x => String(x.id) === id.trim())
    return s ? s.name : id.trim()
  }).join(', ')
}

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.materialName) p.materialName = query.materialName
    if (query.projectId) p.projectId = query.projectId
    if (activeTab.value !== '全部') p.bomTypeId = activeTab.value
    const r = await request.get<any, any>('/outsource/material/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { tableLoading.value = false }
}
function handleTabChange() { pagination.pageNum = 1; loadData() }
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.materialName = ''; query.projectId = undefined; pagination.pageNum = 1; loadData() }

const dialogVisible = ref(false); const dialogTitle = ref(''); const submitLoading = ref(false)
const defForm = () => ({ id: undefined as any, projectIds: '', projectIdArr: [] as number[], warehouseId: undefined as any, materialName: '', bomTypeId: undefined as any, supplierIdArr: [] as number[], unit: 'PCS', remark: '' })
const form = reactive(defForm()); const isEdit = ref(false)

// 子物料组成
const bomRows = ref<any[]>([])
function addBomRow() { bomRows.value.push({ childMaterialId: undefined, quantity: 1, lossRate: 0, remark: '' }) }
function removeBomRow(idx: number) { bomRows.value.splice(idx, 1) }
async function loadComponents(materialId: number) {
  try { const r = await request.get<any, any>(`/outsource/material/${materialId}/components`); bomRows.value = (r || []).map((c: any) => ({ childMaterialId: c.childMaterialId, quantity: c.quantity ?? 1, lossRate: c.lossRate ?? 0, remark: c.remark || '' })) } catch { bomRows.value = [] }
}
async function saveComponents(materialId: number) {
  const valid = bomRows.value.filter(r => r.childMaterialId)
  await request.put(`/outsource/material/${materialId}/components`, valid)
}

// 加载全部物料（不受TAB过滤），供子物料下拉框使用
async function loadAllMaterials() {
  await optionsStore.ensureMaterials(); allMaterials.value = optionsStore.materials || []
}

function handleAdd() { loadOptions(); Object.assign(form, defForm()); bomRows.value = []; isEdit.value = false; dialogTitle.value = '新增物料'; dialogVisible.value = true; loadAllMaterials() }
function handleEdit(row: any) {
  loadOptions()
  Object.assign(form, defForm(), row)
  form.projectIdArr = (row.projectIds || '').split(',').filter(Boolean).map(Number)
  form.supplierIdArr = (row.supplierIds || '').split(',').filter(Boolean).map(Number)
  form.warehouseId = row.warehouseId || undefined
  isEdit.value = true; dialogTitle.value = '编辑物料'; dialogVisible.value = true
  loadAllMaterials()
  loadComponents(row.id)
}

async function handleSubmit() {
  if (!form.materialName) { ElMessage.warning('请输入物料名称'); return }
  if (!form.bomTypeId) { ElMessage.warning('请选择物料类型'); return }
  const ids = form.projectIdArr.join(',')
  const names = form.projectIdArr.map((id:number)=>projectOptions.value.find((p:any)=>p.id===id)?.name||'').filter(Boolean).join(', ')
  const sIds = form.supplierIdArr.join(',')
  const body = { ...form, projectIds: ids, projectName: names, supplierIds: sIds }
  submitLoading.value = true
  try {
    if (isEdit.value) { await request.put('/outsource/material', body); ElMessage.success('修改成功') }
    else { const res = await request.post('/outsource/material', body) as any; form.id = res }
    if (form.id) await saveComponents(form.id)
    optionsStore.refreshMaterials(); dialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}
async function handleDelete(row: any) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await request.delete(`/outsource/material/${row.id}`); optionsStore.refreshMaterials(); ElMessage.success('已删除'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

const router = useRouter()

// 三个下拉「+ 新增」项：识别到标记后移除占位并跳转到对应列表页
function onProjectChange(val: any[]) {
  if (val.includes(ADD_MARKER)) {
    form.projectIdArr = val.filter(v => v !== ADD_MARKER)
    router.push('/dev/project')
  }
}
function onWarehouseChange(val: any) {
  if (val === ADD_MARKER) {
    form.warehouseId = undefined
    router.push('/outsource/warehouse')
  }
}
function onSupplierChange(val: any[]) {
  if (val.includes(ADD_MARKER)) {
    form.supplierIdArr = val.filter(v => v !== ADD_MARKER)
    // 跳转到供应商管理列表页（与菜单 routePath /supplier/manage 保持一致）
    router.push('/supplier/manage')
  }
}

onMounted(() => { loadOptions(); loadData() })
onActivated(() => { loadOptions(); loadData() })
</script>

<template>
  <div class="mat-page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="物料名称"><el-input v-model="query.materialName" placeholder="物料名称" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="所属项目"><el-select v-model="query.projectId" placeholder="全部" clearable filterable style="width:180px"><el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button><el-button type="success" @click="handleAdd">新增</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="全部" />
        <el-tab-pane v-for="t in MATERIAL_TYPES" :key="t.id" :label="t.typeName" :name="t.id" />
      </el-tabs>

      <el-table :data="tableData" border stripe v-loading="tableLoading">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="projectName" label="所属项目" width="150" show-overflow-tooltip />
        <el-table-column prop="bomTypeName" label="物料类型" width="100" />
        <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
        <el-table-column label="供应商" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ supplierNames(row.supplierIds) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{row}"><el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button><el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="所属项目"><el-select v-model="form.projectIdArr" multiple filterable placeholder="可多选" style="width:100%" @change="onProjectChange"><el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item>
        <el-form-item label="物料类型"><el-select v-model="form.bomTypeId" style="width:100%"><el-option v-for="t in MATERIAL_TYPES" :key="t.id" :label="t.typeName" :value="t.id" /></el-select></el-form-item>
        <el-form-item label="物料名称" required><el-input v-model="form.materialName" /></el-form-item>
        <el-form-item label="委外仓库"><el-select v-model="form.warehouseId" clearable filterable placeholder="可选" style="width:100%" @change="onWarehouseChange"><el-option v-for="w in warehouseOptions" :key="w.id" :label="`${w.factoryName} - ${w.warehouseName}`" :value="w.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item>
        <el-form-item label="供应商"><el-select v-model="form.supplierIdArr" multiple filterable placeholder="可多选" style="width:100%" @change="onSupplierChange"><el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>

      <el-divider content-position="left"><span style="font-weight:600;font-size:var(--app-font-base)">子物料组成</span></el-divider>
      <div style="margin-bottom:8px">
        <el-button type="primary" size="small" @click="addBomRow">+ 添加子物料</el-button>
        <span style="color:var(--app-text-secondary);font-size:var(--app-font-sm);margin-left:8px">共 {{ bomRows.length }} 项</span>
      </div>
      <el-table :data="bomRows" border stripe empty-text="暂无子物料" max-height="280" size="small">
        <el-table-column label="子物料" min-width="220">
          <template #default="{ row }">
            <el-select v-model="row.childMaterialId" filterable placeholder="选择已有物料" style="width:100%" size="small">
              <el-option v-for="m in allMaterials" :key="m.id" :label="`${m.materialName} (${m.bomTypeName || ''})`" :value="m.id" :disabled="m.id === form.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="用量" width="90">
          <template #default="{ row }"><el-input v-model="row.quantity" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="损耗率%" width="100">
          <template #default="{ row }"><el-input v-model="row.lossRate" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }"><el-input v-model="row.remark" placeholder="备注" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }"><el-button type="danger" link size="small" @click="removeBomRow($index)">删除</el-button></template>
        </el-table-column>
      </el-table>

      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mat-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
