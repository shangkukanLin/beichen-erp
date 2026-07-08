<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const query = reactive({ warehouseName: '', factoryId: undefined as any })
const allData = ref<any[]>([])
const activeTab = ref('active')
const tableLoading = ref(false)
const factoryOptions = ref<any[]>([])
const activeData = computed(() => allData.value.filter(v => v.status === 1))
const stoppedData = computed(() => allData.value.filter(v => v.status === 0))

async function loadFactories() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } }); factoryOptions.value = r?.records || [] } catch (e: any) { console.warn('加载工厂选项失败', e?.message || e) }
}

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageSize: 500 }
    if (query.warehouseName) p.warehouseName = query.warehouseName
    if (query.factoryId) p.factoryId = query.factoryId
    const r = await request.get<any, any>('/outsource/warehouse/page', { params: p })
    allData.value = r?.records || []
  } finally { tableLoading.value = false }
}
function handleQuery() { loadData() }
function handleReset() { query.warehouseName = ''; query.factoryId = undefined; loadData() }

const dialogVisible = ref(false); const dialogTitle = ref(''); const submitLoading = ref(false)
const defForm = () => ({ id: undefined as any, factoryId: undefined as any, warehouseName: '', address: '', contact: '', phone: '', status: 1, remark: '' })
const form = reactive(defForm()); const isEdit = ref(false)

function handleAdd() { Object.assign(form, defForm()); isEdit.value = false; dialogTitle.value = '新增仓库'; dialogVisible.value = true }
function handleEdit(row: any) { Object.assign(form, defForm(), row); isEdit.value = true; dialogTitle.value = '编辑仓库'; dialogVisible.value = true }

async function handleSubmit() { if (!form.warehouseName) { ElMessage.warning('请输入仓库名称'); return }; submitLoading.value = true
  try { if (isEdit.value) { await request.put('/outsource/warehouse', form); ElMessage.success('修改成功') } else { await request.post('/outsource/warehouse', form); ElMessage.success('新增成功') }
    dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }

async function handleToggleStatus(row: any) {
  row.status = row.status === 1 ? 0 : 1
  await request.put('/outsource/warehouse', row)
  ElMessage.success(row.status === 1 ? '已启用' : '已停用'); loadData()
}

function handleDetail(row: any) { router.push(`/outsource/warehouse/detail/${row.id}`) }

onMounted(() => { loadFactories(); loadData() })
onActivated(() => loadData())
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
      <el-tabs v-model="activeTab">
        <el-tab-pane label="启用" name="active" />
        <el-tab-pane label="停用" name="stopped" />
      </el-tabs>

      <el-table v-if="activeTab==='active'" :data="activeData" border stripe v-loading="tableLoading" style="width:100%">
        <el-table-column prop="factoryName" label="所属加工厂" width="140" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库名称" min-width="140" />
        <el-table-column prop="address" label="地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="80" />
        <el-table-column prop="phone" label="电话" width="120" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{row}"><el-button type="primary" link @click="handleDetail(row)">详情</el-button><el-button type="success" link @click="handleEdit(row)">编辑</el-button><el-button type="warning" link @click="handleToggleStatus(row)">停用</el-button></template>
        </el-table-column>
      </el-table>

      <el-table v-if="activeTab==='stopped'" :data="stoppedData" border stripe style="width:100%">
        <el-table-column prop="factoryName" label="所属加工厂" width="140" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库名称" min-width="140" />
        <el-table-column prop="address" label="地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="80" />
        <el-table-column prop="phone" label="电话" width="120" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{row}"><el-button type="primary" link @click="handleDetail(row)">详情</el-button><el-button type="success" link @click="handleEdit(row)">编辑</el-button><el-button type="success" link @click="handleToggleStatus(row)">启用</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="加工厂" required><el-select v-model="form.factoryId" filterable style="width:100%"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item>
        <el-form-item label="仓库名称"><el-input v-model="form.warehouseName" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wh-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
</style>
