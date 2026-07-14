<script setup lang="ts">
import { ref, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

interface Brand { id?: number; brandName: string; status: number }
const tableData = ref<Brand[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<Brand>({ brandName: '', status: 1 })
const editId = ref<number>()

async function loadData() {
  loading.value = true
  try { const res = await request.get<any, any>('/brand/page', { params: { pageSize: 100 } }); tableData.value = res?.records || [] } catch (e: any) { console.warn('加载品牌失败', e?.message || e) } finally { loading.value = false }
}

function handleAdd() { isEdit.value = false; editId.value = undefined; form.value = { brandName: '', status: 1 }; dialogVisible.value = true }
function handleEdit(row: Brand) { isEdit.value = true; editId.value = row.id; form.value = { brandName: row.brandName, status: row.status }; dialogVisible.value = true }

async function handleSubmit() {
  if (!form.value.brandName.trim()) { ElMessage.warning('请输入品牌名称'); return }
  try {
    if (isEdit.value && editId.value) { await request.put('/brand', { id: editId.value, ...form.value }); ElMessage.success('已更新') }
    else { await request.post('/brand', form.value); ElMessage.success('已添加') }
    dialogVisible.value = false; loadData()
  } catch (e: any) { ElMessage.error('操作失败: ' + (e?.message || '未知错误')) }
}

async function handleDelete(row: Brand) {
  try { await ElMessageBox.confirm(`确定删除品牌「${row.brandName}」吗？`, '提示', { type: 'warning' }); await request.delete(`/brand/${row.id}`); ElMessage.success('已删除'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

onMounted(() => loadData())
onActivated(() => loadData())
</script>

<template>
  <el-card shadow="never">
    <div style="margin-bottom:12px"><el-button type="primary" @click="handleAdd">新增品牌</el-button></div>
    <el-table :data="tableData" border stripe v-loading="loading">
      <el-table-column prop="brandName" label="品牌名称" min-width="200" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{row.status===1?'启用':'禁用'}}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{row}"><el-button type="primary" link @click="handleEdit(row)">编辑</el-button><el-button type="danger" link @click="handleDelete(row)">删除</el-button></template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="isEdit?'编辑品牌':'新增品牌'" width="400px">
    <el-form :model="form" label-width="80px">
      <el-form-item label="品牌名称"><el-input v-model="form.brandName" placeholder="请输入品牌名称" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option label="启用" :value="1"/><el-option label="禁用" :value="0"/></el-select></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="handleSubmit">确定</el-button></template>
  </el-dialog>
</template>
