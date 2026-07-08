<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getRolePage,
  addRole,
  updateRole,
  deleteRole,
  getMenuTree,
  getRoleMenus,
  saveRoleMenus,
  type Role,
  type RoleDTO,
  type RoleQueryParams,
  type MenuVO
} from '@/api/system'

// 查询参数
const query = reactive<RoleQueryParams>({
  roleName: '',
  status: ''
})

// 分页
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableLoading = ref(false)
const tableData = ref<Role[]>([])

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

/* ============== 权限分配弹窗 ============== */
const permDialogVisible = ref(false)
const permLoading = ref(false)
const permRoleId = ref<number | string>('')
const permRoleName = ref('')
const permTreeRef = ref()
const permMenuTree = ref<MenuVO[]>([])
const permCheckedKeys = ref<number[]>([])

// 弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)

const defaultForm = (): RoleDTO => ({
  id: undefined,
  roleName: '',
  roleCode: '',
  status: 1,
  remark: ''
})

const form = reactive<RoleDTO>(defaultForm())

const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

async function loadData() {
  tableLoading.value = true
  try {
    const params: RoleQueryParams = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (query.roleName) params.roleName = query.roleName
    if (query.status !== '' && query.status !== undefined && query.status !== null) {
      params.status = query.status
    }

    const res = await getRolePage(params)
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
  query.roleName = ''
  query.status = ''
  pagination.pageNum = 1
  loadData()
}

function handleAdd() {
  Object.assign(form, defaultForm())
  isEdit.value = false
  dialogTitle.value = '新增角色'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function handleEdit(row: Role) {
  Object.assign(form, defaultForm(), {
    id: row.id,
    roleName: row.roleName,
    roleCode: row.roleCode,
    status: row.status,
    remark: row.remark || ''
  })
  isEdit.value = true
  dialogTitle.value = '编辑角色'
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
        await updateRole(form)
        ElMessage.success('修改成功')
      } else {
        await addRole(form)
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

async function handleDelete(row: Role) {
  try {
    await ElMessageBox.confirm(`确定要删除角色「${row.roleName}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteRole(row.id as number | string)
    ElMessage.success('删除成功')
    if (tableData.value.length === 1 && pagination.pageNum > 1) {
      pagination.pageNum--
    }
    loadData()
  } catch {
    // 用户取消或错误
  }
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

async function handleOpenPerm(row: Role) {
  permRoleId.value = row.id as number | string
  permRoleName.value = row.roleName
  permDialogVisible.value = true
  // 加载菜单树
  try {
    const res = await getMenuTree()
    permMenuTree.value = res || []
  } catch {
    permMenuTree.value = []
  }
  // 加载已分配权限
  try {
    const ids = await getRoleMenus(permRoleId.value)
    permCheckedKeys.value = ids || []
  } catch {
    permCheckedKeys.value = []
  }
}

async function handleSavePerm() {
  const checkedKeys = permTreeRef.value?.getCheckedKeys() || []
  const halfCheckedKeys = permTreeRef.value?.getHalfCheckedKeys() || []
  const allKeys = [...checkedKeys, ...halfCheckedKeys]
  permLoading.value = true
  try {
    await saveRoleMenus(permRoleId.value, allKeys)
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
  } catch {
    // 错误已在拦截器中提示
  } finally {
    permLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
onActivated(() => { loadData() })
</script>

<template>
  <div class="role-page">
    <!-- 查询栏 -->
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="角色名称">
          <el-input v-model="query.roleName" placeholder="请输入角色名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="请选择状态" clearable style="width: 140px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
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
        <el-table-column prop="roleName" label="角色名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="roleCode" label="角色编码" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType((row as Role).status)">{{ statusText((row as Role).status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as Role).remark || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row as Role)">编辑</el-button>
            <el-button type="warning" link @click="handleOpenPerm(row as Role)">分配权限</el-button>
            <el-button type="danger" link @click="handleDelete(row as Role)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="请输入角色编码" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配弹窗 -->
    <el-dialog v-model="permDialogVisible" :title="`分配权限 - ${permRoleName}`" width="460px" :close-on-click-modal="false">
      <el-tree
        ref="permTreeRef"
        :data="permMenuTree"
        node-key="id"
        show-checkbox
        :default-checked-keys="permCheckedKeys"
        :props="{ label: 'menuName', children: 'children' }"
        default-expand-all
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permLoading" @click="handleSavePerm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.role-page {
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

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
