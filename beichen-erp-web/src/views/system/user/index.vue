<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getUserPage,
  addUser,
  updateUser,
  deleteUser,
  resetPassword,
  toggleUserStatus,
  getEnabledRoles,
  type UserVO,
  type UserDTO,
  type UserQueryParams,
  type Role
} from '@/api/system'

// 查询参数
const query = reactive<UserQueryParams>({
  username: '',
  phone: '',
  status: '',
  roleId: ''
})

// 分页
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableLoading = ref(false)
const tableData = ref<UserVO[]>([])

// 角色下拉数据
const roleOptions = ref<Role[]>([])

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

/* ============== 新增/编辑弹窗 ============== */
const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)

const defaultForm = (): UserDTO => ({
  id: undefined,
  username: '',
  password: '',
  phone: '',
  dept: '',
  status: 1,
  roleIds: []
})

const form = reactive<UserDTO>(defaultForm())

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: isEdit.value
    ? []
    : [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 3, message: '密码长度不少于3位', trigger: 'blur' }
      ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}))

/* ============== 重置密码弹窗 ============== */
const resetDialogVisible = ref(false)
const resetLoading = ref(false)
const resetFormRef = ref<FormInstance>()
const resetForm = reactive({ id: '' as number | string, username: '', password: '' })
const resetRules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 3, message: '密码长度不少于3位', trigger: 'blur' }
  ]
}

async function loadRoles() {
  try {
    const res = await getEnabledRoles()
    // 禁止通过用户管理分配超级管理员角色
    roleOptions.value = (res || []).filter((r: Role) => r.roleCode !== 'super_admin')
  } catch {
    roleOptions.value = []
  }
}

async function loadData() {
  tableLoading.value = true
  try {
    const params: UserQueryParams = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (query.username) params.username = query.username
    if (query.phone) params.phone = query.phone
    if (query.status !== '' && query.status !== undefined && query.status !== null) {
      params.status = query.status
    }
    if (query.roleId) params.roleId = query.roleId

    const res = await getUserPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch {
    tableData.value = []
    pagination.total = 0
  } finally {
    tableLoading.value = false
  }
}

function handleQuery() {
  pagination.pageNum = 1
  loadData()
}

function handleReset() {
  query.username = ''
  query.phone = ''
  query.status = ''
  query.roleId = ''
  pagination.pageNum = 1
  loadData()
}

function handleAdd() {
  Object.assign(form, defaultForm())
  isEdit.value = false
  dialogTitle.value = '新增用户'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function handleEdit(row: UserVO) {
  Object.assign(form, defaultForm(), {
    id: row.id,
    username: row.username,
    phone: row.phone ?? '',
    dept: row.dept ?? '',
    status: row.status,
    roleIds: (row.roles || []).map((r) => r.id as number | string)
  })
  isEdit.value = true
  dialogTitle.value = '编辑用户'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        const payload: UserDTO = {
          id: form.id,
          username: form.username,
          phone: form.phone || null,
          dept: form.dept || null,
          status: form.status,
          roleIds: form.roleIds
        }
        await updateUser(payload)
        ElMessage.success('修改成功')
      } else {
        await addUser(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } catch {
      // 错误已在拦截器中提示
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row: UserVO) {
  try {
    await ElMessageBox.confirm(`确定要删除用户「${row.username}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteUser(row.id as number | string)
    ElMessage.success('删除成功')
    if (tableData.value.length === 1 && pagination.pageNum > 1) {
      pagination.pageNum--
    }
    loadData()
  } catch {
    // 用户取消或错误
  }
}

async function handleToggleStatus(row: UserVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户「${row.username}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await toggleUserStatus(row.id as number | string, next)
    ElMessage.success(`${action}成功`)
    loadData()
  } catch {
    // 用户取消或错误
  }
}

function handleOpenReset(row: UserVO) {
  resetForm.id = row.id as number | string
  resetForm.username = row.username
  resetForm.password = ''
  resetDialogVisible.value = true
  resetFormRef.value?.clearValidate()
}

async function handleResetPassword() {
  if (!resetFormRef.value) return
  await resetFormRef.value.validate(async (valid) => {
    if (!valid) return
    resetLoading.value = true
    try {
      await resetPassword({ id: resetForm.id, password: resetForm.password })
      ElMessage.success('密码重置成功')
      resetDialogVisible.value = false
    } catch {
      // 错误已在拦截器中提示
    } finally {
      resetLoading.value = false
    }
  })
}

function handleSizeChange(val: number) {
  pagination.pageSize = val
  pagination.pageNum = 1
  loadData()
}

function handleCurrentChange(val: number) {
  pagination.pageNum = val
  loadData()
}

function statusText(status: number) {
  return status === 1 ? '启用' : '禁用'
}

function statusType(status: number) {
  return status === 1 ? 'success' : 'info'
}

onMounted(() => {
  loadRoles()
  loadData()
})
</script>

<template>
  <div class="user-page">
    <!-- 查询栏 -->
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="请输入用户名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="query.phone" placeholder="请输入手机号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="请选择状态" clearable style="width: 140px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.roleId" placeholder="请选择角色" clearable style="width: 160px">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id as number | string" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="tableLoading" :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as UserVO).phone || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="dept" label="部门" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as UserVO).dept || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <template v-if="(row as UserVO).roles && (row as UserVO).roles!.length">
              <el-tag
                v-for="r in (row as UserVO).roles"
                :key="r.id"
                size="small"
                class="role-tag"
                type="primary"
              >
                {{ r.roleName }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType((row as UserVO).status)">{{ statusText((row as UserVO).status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row as UserVO)">编辑</el-button>
            <el-button type="warning" link @click="handleOpenReset(row as UserVO)">重置密码</el-button>
            <el-button
              :type="(row as UserVO).status === 1 ? 'info' : 'success'"
              link
              @click="handleToggleStatus(row as UserVO)"
            >
              {{ (row as UserVO).status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button type="danger" link @click="handleDelete(row as UserVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" placeholder="请输入用户名" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col v-if="!isEdit" :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门" prop="dept">
              <el-input v-model="form.dept" placeholder="请输入部门" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="角色" prop="roleIds">
              <el-select
                v-model="form.roleIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="请选择角色"
                style="width: 100%"
              >
                <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id as number | string" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetDialogVisible" title="重置密码" width="440px" :close-on-click-modal="false">
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="90px">
        <el-form-item label="用户名">
          <el-input :model-value="resetForm.username" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetLoading" @click="handleResetPassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.query-card :deep(.el-card__body),
.table-card :deep(.el-card__body) {
  padding: 16px;
}

.query-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.role-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
