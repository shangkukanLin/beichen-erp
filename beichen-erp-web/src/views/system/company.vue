<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCompanyList, createCompany, updateCompany, deleteCompany, type Company } from '@/api/company'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const tableData = ref<Company[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<any>({ companyName: '' })
const editId = ref<number>()

async function loadData() {
  loading.value = true
  try { tableData.value = await getCompanyList() || [] } catch (e: any) { console.warn('加载公司列表失败', e?.message || e) } finally { loading.value = false }
}

function openAdd() {
  isEdit.value = false; editId.value = undefined
  form.value = { companyName: '' }
  dialogVisible.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { companyName: row.companyName }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.companyName.trim()) { ElMessage.warning('请输入公司名称'); return }
  try {
    if (isEdit.value && editId.value) {
      await updateCompany(editId.value, form.value as Company)
      ElMessage.success('修改成功')
    } else {
      await createCompany(form.value as Company)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e: any) { ElMessage.error('操作失败: ' + (e?.message || '未知错误')) }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除公司"${row.companyName}"吗？`, '删除公司', { type: 'warning' })
    await deleteCompany(row.id!)
    ElMessage.success('已删除')
    loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

function goBack() {
  userStore.logout()
  router.push('/login')
}

onMounted(() => loadData())
</script>

<template>
  <div class="company-page">
    <div class="page-header">
      <el-button @click="goBack">← 返回登录</el-button>
      <span class="page-title">公司管理</span>
    </div>

    <el-card shadow="never">
      <div style="margin-bottom:12px"><el-button type="primary" @click="openAdd">新增公司</el-button></div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="companyName" label="公司名称" min-width="200" />
        <el-table-column label="状态" width="80">
          <template #default="{row}"><el-tag :type="row.status===1?'success':'danger'">{{ row.status===1?'启用':'停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{row}">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑公司':'新增公司'" width="420px" :close-on-click-modal="false">
      <el-form :model="form" label-width="80px">
        <el-form-item label="公司名称"><el-input v-model="form.companyName" placeholder="请输入公司名称" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.company-page { max-width: 700px; margin: 40px auto; padding: 0 16px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; }
</style>
