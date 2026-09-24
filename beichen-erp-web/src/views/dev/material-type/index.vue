<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const form = reactive({ id: undefined as number | undefined, typeName: '', sortOrder: 0, status: 1 })
const isEdit = ref(false)

async function loadData() {
  const res = await request.get<any, any>('/dev/material-type/page', { params: { pageSize: 100 } })
  tableData.value = res?.records || []
}

function handleAdd() { Object.assign(form, { id: undefined, typeName: '', sortOrder: tableData.value.length + 1, status: 1 }); isEdit.value = false; dialogVisible.value = true }
function handleEdit(row: any) { Object.assign(form, row); isEdit.value = true; dialogVisible.value = true }

async function handleSubmit() {
  if (!form.typeName) { ElMessage.warning('请输入类型名称'); return }
  if (isEdit.value) { await request.put('/dev/material-type', form); ElMessage.success('已更新') }
  else { await request.post('/dev/material-type', form); ElMessage.success('已添加') }
  dialogVisible.value = false; loadData()
}

async function handleDelete(row: any) {
  if (row.isDefault === 1) { ElMessage.warning('默认类型不可删除'); return }
  try { await ElMessageBox.confirm(`确定删除「${row.typeName}」吗？`, '提示', { type: 'warning' }); await request.delete(`/dev/material-type/${row.id}`); ElMessage.success('已删除'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

onMounted(() => loadData())

</script>

<template>
  <div class="material-type-page">
    <el-card shadow="never">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px">
        <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        <el-tag type="info">类型数据被 BOM 物料清单共用</el-tag>
      </div>
      <el-table :data="tableData" border stripe>
        <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
        <el-table-column prop="typeName" label="类型名称" min-width="200">
          <template #default="{row}">{{ row.typeName }}<el-tag v-if="row.isDefault===1" type="info" size="small" style="margin-left:6px">默认</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{row.status===1?'启用':'禁用'}}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{row}"><el-button type="primary" link @click="handleEdit(row)">编辑</el-button><el-button type="danger" link :disabled="row.isDefault===1" @click="handleDelete(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑类型':'新增类型'" width="var(--app-dialog-sm)">
      <el-form :model="form" label-width="80px">
        <el-form-item required label="类型名称"><el-input v-model="form.typeName" placeholder="如：偏光片" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option label="启用" :value="1"/><el-option label="禁用" :value="0"/></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>
