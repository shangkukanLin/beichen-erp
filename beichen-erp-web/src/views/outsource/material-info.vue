<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const query = reactive({ materialName: '', projectId: undefined as any, materialType: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const tableLoading = ref(false)
const projectOptions = ref<any[]>([])
const supplierOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
const MATERIAL_TYPES = ref<string[]>([])

async function loadOptions() {
  try { const r = await request.get<any, any>('/dev/bom-type/enabled'); MATERIAL_TYPES.value = (r || []).map((t:any)=>t.typeName) } catch (e: any) { console.warn('加载BOM类型失败', e?.message || e) }
  try { const r = await request.get<any, any>('/dev/project/page', { params: { pageSize: 200 } }); projectOptions.value = r?.records || [] } catch (e: any) { console.warn('加载项目失败', e?.message || e) }
  try { const r = await request.get<any, any>('/supplier/page', { params: { pageSize: 500 } }); supplierOptions.value = r?.records || [] } catch (e: any) { console.warn('加载供应商失败', e?.message || e) }
  try { const r = await request.get<any, any>('/outsource/warehouse/page', { params: { pageSize: 500 } }); warehouseOptions.value = r?.records || [] } catch (e: any) { console.warn('加载仓库失败', e?.message || e) }
}

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.materialName) p.materialName = query.materialName
    if (query.projectId) p.projectId = query.projectId
    if (query.materialType) p.materialType = query.materialType
    const r = await request.get<any, any>('/outsource/material/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.materialName = ''; query.projectId = undefined; query.materialType = ''; pagination.pageNum = 1; loadData() }

const dialogVisible = ref(false); const dialogTitle = ref(''); const submitLoading = ref(false)
const defForm = () => ({ id: undefined as any, projectIds: '', projectIdArr: [] as number[], warehouseId: undefined as any, materialName: '', materialType: '', supplierName: '', supplierIdArr: [] as number[], unit: 'PCS', remark: '' })
const form = reactive(defForm()); const isEdit = ref(false)
const childMaterials = ref<any[]>([])   // 子物料列表
const allMaterialOptions = ref<any[]>([]) // 全部物料（供子物料下拉）
const excludedMaterialIds = ref(new Set<number>()) // 不可选的物料ID（防循环引用）

/** 收集当前物料的所有后代ID（防循环引用） */
function collectDescendantIds(materialId: number, all: any[]): Set<number> {
  const result = new Set<number>([materialId])
  const children = all.filter(m => m.parentMaterialId === materialId)
  for (const child of children) {
    const sub = collectDescendantIds(child.id, all)
    sub.forEach(id => result.add(id))
  }
  return result
}

async function loadAllMaterialsForDropdown() {
  try {
    const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } })
    allMaterialOptions.value = r?.records || []
  } catch { allMaterialOptions.value = [] }
}

function handleAdd() {
  Object.assign(form, defForm()); childMaterials.value = []; isEdit.value = false
  excludedMaterialIds.value = new Set()
  dialogTitle.value = '新增物料'; dialogVisible.value = true
}
function handleEdit(row: any) {
  Object.assign(form, defForm(), row)
  form.projectIdArr = (row.projectIds || '').split(',').filter(Boolean).map(Number)
  form.supplierIdArr = (row.supplierIds || '').split(',').filter(Boolean).map(Number)
  form.warehouseId = row.warehouseId || undefined
  isEdit.value = true; dialogTitle.value = '编辑物料'
  // 加载全部物料并计算不可选ID
  loadAllMaterialsForDropdown().then(() => {
    excludedMaterialIds.value = collectDescendantIds(row.id, allMaterialOptions.value)
  })
  loadChildMaterials(row.id)
  dialogVisible.value = true
}

async function loadChildMaterials(parentId: number) {
  try {
    const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } })
    childMaterials.value = (r?.records || []).filter((m: any) => m.parentMaterialId === parentId)
      .map((m: any) => ({ ...m, materialId: m.id, quantity: m.quantityPerSet || 1 }))
  } catch { childMaterials.value = [] }
}

function addChildMaterial() {
  childMaterials.value.push({ tempId: Date.now(), materialId: undefined, materialName: '', unit: '', quantity: 1, supplierIdArr: [], parentMaterialId: form.id || 0 })
}
function onChildMaterialSelect(materialId: number, child: any) {
  const mat = allMaterialOptions.value.find(m => m.id === materialId)
  if (!mat) return
  // 防止循环引用
  if (excludedMaterialIds.value.has(materialId)) {
    ElMessage.warning('该物料已是当前物料的子级或本身，不能重复引用')
    child.materialId = undefined; child.materialName = ''
    return
  }
  child.materialName = mat.materialName
  child.unit = mat.unit || 'PCS'
  child.materialId = materialId
  if (mat.supplierIds) {
    child.supplierIdArr = mat.supplierIds.split(',').filter(Boolean).map(Number)
  }
}
function removeChildMaterial(idx: number) { childMaterials.value.splice(idx, 1) }

/** 子物料下拉选项（排除自身，但允许已有子物料） */
function childMaterialOptions() {
  const childIds = new Set(childMaterials.value.map(c => c.materialId).filter(Boolean))
  return allMaterialOptions.value.filter(m => m.id !== form.id && (!excludedMaterialIds.value.has(m.id) || childIds.has(m.id)))
}

async function handleSubmit() {
  if (!form.materialName) { ElMessage.warning('请输入物料名称'); return }
  const ids = form.projectIdArr.join(',')
  const names = form.projectIdArr.map((id:number)=>projectOptions.value.find((p:any)=>p.id===id)?.name||'').filter(Boolean).join(', ')
  const sIds = form.supplierIdArr.join(',')
  const sNames = form.supplierIdArr.map((id:number)=>supplierOptions.value.find((s:any)=>s.id===id)?.name||'').filter(Boolean).join(', ')
  const body = { ...form, projectIds: ids, projectName: names, supplierIds: sIds, supplierName: sNames }

  submitLoading.value = true
  try {
    let parentId = form.id
    if (isEdit.value) {
      await request.put('/outsource/material', body)
    } else {
      await request.post('/outsource/material', body)
      // 新增后需要知道 ID：重新查
      const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500, materialName: form.materialName } })
      const found = (r?.records || []).find((m: any) => m.materialName === form.materialName)
      parentId = found?.id || parentId
    }
    // 保存子物料
    for (const child of childMaterials.value) {
      if (!child.materialName) continue
      // 子物料通过 parentMaterialId 关联父物料，不再重复创建物料记录
      if (!child.id && child.materialId) {
        // 更新已有物料的 parentMaterialId
        await request.put('/outsource/material', {
          id: child.materialId,
          materialName: child.materialName,
          materialType: form.materialType,
          unit: child.unit,
          parentMaterialId: parentId,
          projectIds: ids,
          supplierIds: (child.supplierIdArr || []).join(','),
          supplierName: (child.supplierIdArr || []).map((sid: number) => supplierOptions.value.find((s: any) => s.id === sid)?.name || '').filter(Boolean).join(', ')
        })
      }
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}
async function handleDelete(row: any) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await request.delete(`/outsource/material/${row.id}`); ElMessage.success('已删除'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

onMounted(() => { loadOptions(); loadData() })
onActivated(() => { loadOptions(); loadData() })
</script>

<template>
  <div class="mat-page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="物料名称"><el-input v-model="query.materialName" placeholder="物料名称" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="所属项目"><el-select v-model="query.projectId" placeholder="全部" clearable filterable style="width:180px"><el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="物料类型"><el-select v-model="query.materialType" placeholder="全部" clearable style="width:120px"><el-option v-for="t in MATERIAL_TYPES" :key="t" :label="t" :value="t" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button><el-button type="success" @click="handleAdd">新增</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" border stripe v-loading="tableLoading">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="projectName" label="所属项目" width="150" show-overflow-tooltip />
        <el-table-column prop="materialType" label="物料类型" width="100" />
        <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" width="180" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{row}"><el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button><el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="所属项目"><el-select v-model="form.projectIdArr" multiple filterable placeholder="可多选" style="width:100%"><el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="物料类型"><el-select v-model="form.materialType" style="width:100%"><el-option v-for="t in MATERIAL_TYPES" :key="t" :label="t" :value="t" /></el-select></el-form-item>
        <el-form-item label="物料名称" required><el-input v-model="form.materialName" /></el-form-item>
        <el-form-item label="委外仓库"><el-select v-model="form.warehouseId" clearable filterable placeholder="可选" style="width:100%"><el-option v-for="w in warehouseOptions" :key="w.id" :label="`${w.factoryName} - ${w.warehouseName}`" :value="w.id" /></el-select></el-form-item>
        <el-form-item label="供应商"><el-select v-model="form.supplierIdArr" multiple filterable placeholder="可多选" style="width:100%"><el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>

      <!-- 子物料清单（仅编辑时显示） -->
      <div v-if="isEdit" style="margin-top:12px;border-top:1px solid #ebeef5;padding-top:12px">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
          <span style="font-weight:600;font-size:14px">📦 子物料清单（BOM）</span>
          <el-button type="primary" size="small" @click="addChildMaterial">+ 添加子物料</el-button>
        </div>
        <el-table :data="childMaterials" border size="small" max-height="250">
          <el-table-column label="物料名称" min-width="160">
            <template #default="{row}">
              <el-select v-model="row.materialId" filterable size="small" style="width:100%" placeholder="选择物料" @change="(v:number) => onChildMaterialSelect(v, row)">
                <el-option v-for="m in childMaterialOptions()" :key="m.id" :label="`${m.materialName} (${m.materialType||''})`" :value="m.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="70">
            <template #default="{row}"><el-input v-model="row.unit" size="small" disabled /></template>
          </el-table-column>
          <el-table-column label="数量" width="90">
            <template #default="{row}"><el-input-number v-model="row.quantity" size="small" :min="0" :precision="0" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="操作" width="50" align="center">
            <template #default="{$index}"><el-button type="danger" link size="small" @click="removeChildMaterial($index)">×</el-button></template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mat-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
