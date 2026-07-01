<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const query = reactive({ materialName: '', projectId: undefined as any, materialType: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const tableLoading = ref(false)
const projectOptions = ref<any[]>([])
const supplierOptions = ref<any[]>([])
const MATERIAL_TYPES = ref<string[]>([])

async function loadOptions() {
  try { const r = await request.get<any, any>('/dev/bom-type/enabled'); MATERIAL_TYPES.value = (r || []).map((t:any)=>t.typeName) } catch { }
  try { const r = await request.get<any, any>('/dev/project/page', { params: { pageSize: 200 } }); projectOptions.value = r?.records || [] } catch { }
  try { const r = await request.get<any, any>('/supplier/page', { params: { pageSize: 500 } }); supplierOptions.value = r?.records || [] } catch { }
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
const defForm = () => ({ id: undefined as any, projectIds: '', projectIdArr: [] as number[], materialName: '', materialType: '', supplierName: '', supplierIdArr: [] as number[], unit: 'PCS', remark: '' })
const form = reactive(defForm()); const isEdit = ref(false)

function handleAdd() { Object.assign(form, defForm()); isEdit.value = false; dialogTitle.value = '新增物料'; dialogVisible.value = true }
function handleEdit(row: any) {
  Object.assign(form, defForm(), row)
  form.projectIdArr = (row.projectIds || '').split(',').filter(Boolean).map(Number)
  form.supplierIdArr = (row.supplierIds || '').split(',').filter(Boolean).map(Number)
  isEdit.value = true; dialogTitle.value = '编辑物料'; dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.materialName) { ElMessage.warning('请输入物料名称'); return }
  // 构建提交数据
  const ids = form.projectIdArr.join(',')
  const names = form.projectIdArr.map((id:number)=>projectOptions.value.find((p:any)=>p.id===id)?.name||'').filter(Boolean).join(', ')
  const sIds = form.supplierIdArr.join(',')
  const sNames = form.supplierIdArr.map((id:number)=>supplierOptions.value.find((s:any)=>s.id===id)?.name||'').filter(Boolean).join(', ')
  const body = { ...form, projectIds: ids, projectName: names, supplierIds: sIds, supplierName: sNames }

  submitLoading.value = true
  try { if (isEdit.value) { await request.put('/outsource/material', body); ElMessage.success('修改成功') } else { await request.post('/outsource/material', body); ElMessage.success('新增成功') }
    dialogVisible.value = false; loadData() } finally { submitLoading.value = false }
}
async function handleDelete(row: any) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await request.delete(`/outsource/material/${row.id}`); ElMessage.success('已删除'); loadData() } catch { } }

onMounted(() => { loadOptions(); loadData() })
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
        <el-table-column prop="supplierName" label="供应商" width="160" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{row}"><el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button><el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="所属项目"><el-select v-model="form.projectIdArr" multiple filterable placeholder="可多选" style="width:100%"><el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="物料类型"><el-select v-model="form.materialType" style="width:100%"><el-option v-for="t in MATERIAL_TYPES" :key="t" :label="t" :value="t" /></el-select></el-form-item>
        <el-form-item label="物料名称" required><el-input v-model="form.materialName" /></el-form-item>
        <el-form-item label="供应商"><el-select v-model="form.supplierIdArr" multiple filterable placeholder="可多选" style="width:100%"><el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mat-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
