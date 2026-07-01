<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const query = reactive({ warehouseName: '', factoryId: undefined as any })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const tableLoading = ref(false)
const factoryOptions = ref<any[]>([])

async function loadFactories() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } }); factoryOptions.value = r?.records || [] } catch { }
}

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.warehouseName) p.warehouseName = query.warehouseName
    if (query.factoryId) p.factoryId = query.factoryId
    const r = await request.get<any, any>('/outsource/warehouse/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.warehouseName = ''; query.factoryId = undefined; loadData() }

const dialogVisible = ref(false); const dialogTitle = ref(''); const submitLoading = ref(false)
const defForm = () => ({ id: undefined as any, factoryId: undefined as any, warehouseName: '', address: '', contact: '', phone: '', status: 1, remark: '' })
const form = reactive(defForm()); const isEdit = ref(false)

function handleAdd() { Object.assign(form, defForm()); isEdit.value = false; dialogTitle.value = '新增仓库'; dialogVisible.value = true }
function handleEdit(row: any) { Object.assign(form, defForm(), row); isEdit.value = true; dialogTitle.value = '编辑仓库'; dialogVisible.value = true }

async function handleSubmit() {
  if (!form.warehouseName) { ElMessage.warning('请输入仓库名称'); return }
  submitLoading.value = true
  try { if (isEdit.value) { await request.put('/outsource/warehouse', form); ElMessage.success('修改成功') } else { await request.post('/outsource/warehouse', form); ElMessage.success('新增成功') }
    dialogVisible.value = false; loadData() } finally { submitLoading.value = false }
}
async function handleDelete(row: any) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await request.delete(`/outsource/warehouse/${row.id}`); ElMessage.success('已删除'); loadData() } catch { } }

onMounted(() => { loadFactories(); loadData() })
</script>

<template>
  <div class="wh-page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="仓库名称"><el-input v-model="query.warehouseName" placeholder="仓库名称" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="加工厂"><el-select v-model="query.factoryId" placeholder="全部" clearable filterable style="width:200px"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button><el-button type="success" @click="handleAdd">新增</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" border stripe v-loading="tableLoading">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="factoryName" label="所属加工厂" width="140" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库名称" min-width="140" />
        <el-table-column prop="address" label="地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="80" />
        <el-table-column prop="phone" label="电话" width="120" />
        <el-table-column label="状态" width="70" align="center"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'" size="small">{{row.status===1?'启用':'停用'}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{row}"><el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button><el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="加工厂" required><el-select v-model="form.factoryId" filterable style="width:100%"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item>
        <el-form-item label="仓库名称"><el-input v-model="form.warehouseName" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option label="启用" :value="1"/><el-option label="停用" :value="0"/></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wh-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
