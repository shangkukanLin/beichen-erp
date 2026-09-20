<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

/**
 * 阶段模板面板（2026-09-15）：原「阶段模板管理」页面（/dev/phase-template）的主体，
 * 现作为「基础数据 → 模版管理」页面中「阶段模板管理」页签的内嵌内容（见 ../index.vue）。
 * 接口与业务行为保持不变：阶段模板供新建研发项目时一键套用（可选择是否同步产品状态）。
 */
const loading = ref(false)
const list = ref<any[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive({ id: undefined as any, name: '', defaultDays: 0, sortOrder: 0, remark: '' })

async function loadData() {
  loading.value = true
  // 2026-09-20（F7-192）：原先只有 try/finally、无 catch ⇒ 失败无提示且产生未处理 rejection
  try { const r = await request.get<any, any>('/dev/phase-template/list'); list.value = r || [] }
  catch (e: any) { list.value = []; ElMessage.error(e?.message || '加载阶段模板失败') }
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
  <div class="panel-body">
    <div class="panel-toolbar">
      <span class="panel-tip">阶段模板用于新建研发立项时一键套用；「同步产品状态」的阶段完成/跳过后会把关联产品置为「正常」。</span>
      <el-button type="primary" :icon="'Plus'" @click="openAdd">新增阶段</el-button>
    </div>

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

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑阶段':'新增阶段'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item required label="阶段名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="默认天数"><el-input-number v-model="form.defaultDays" :min="0" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="1" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="handleSubmit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-body { display: flex; flex-direction: column; gap: 12px; }
.panel-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.panel-tip { font-size: var(--app-font-xs); color: var(--el-text-color-secondary); }
</style>
