<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const list = ref<any[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive({ id: undefined as any, name: '', defaultDays: 0, sortOrder: 0, remark: '' })

async function loadData() {
  loading.value = true
  try { const r = await request.get<any,any>('/dev/phase-template/list'); list.value = r || [] }
  finally { loading.value = false }
}

function openAdd() { isEdit.value = false; resetForm(); dialogVisible.value = true }
function openEdit(row: any) {
  isEdit.value = true
  Object.assign(form, { id: row.id, name: row.name, defaultDays: row.defaultDays, sortOrder: row.sortOrder, remark: row.remark || '' })
  dialogVisible.value = true
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除', { type: 'warning' }) } catch { return }
  try { await request.delete(`/dev/phase-template/${row.id}`); ElMessage.success('已删除'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}
function resetForm() { Object.assign(form, { id: undefined, name: '', defaultDays: 0, sortOrder: 0, remark: '' }) }
async function handleSubmit() {
  if (!form.name) { ElMessage.warning('请输入阶段名称'); return }
  try {
    if (isEdit.value) { await request.put('/dev/phase-template', form); ElMessage.success('已更新') }
    else { await request.post('/dev/phase-template', form); ElMessage.success('已创建') }
    dialogVisible.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

onMounted(loadData)
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span style="font-weight:600;font-size:15px">阶段模板管理</span>
        <el-button type="primary" @click="openAdd">新增阶段</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column label="序号" width="60" align="center"><template #default="{row}">{{ row.sortOrder }}</template></el-table-column>
        <el-table-column prop="name" label="阶段名称" width="140" />
        <el-table-column label="默认天数" width="80" align="center"><template #default="{row}">{{ row.defaultDays }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="130" align="center">
          <template #default="{row}">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑阶段':'新增阶段'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="阶段名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="默认天数"><el-input-number v-model="form.defaultDays" :min="0" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="1" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="handleSubmit">保存</el-button></template>
    </el-dialog>
  </div>
</template>
