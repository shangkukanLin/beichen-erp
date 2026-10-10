<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getUserPage,
  getUser,
  addUser,
  getDefaultDashboardTabs,
  updateUser,
  deleteUser,
  resetPassword,
  toggleUserStatus,
  getEnabledRoles,
  getMenuTree,
  getUserMenus,
  saveUserMenus,
  DASHBOARD_TABS,
  type UserVO,
  type UserDTO,
  type UserQueryParams,
  type MenuVO,
  type Role
} from '@/api/system'
import { SUPER_ADMIN_ROLE_CODE } from '@/constants/system'
import { SYSTEM_USER_DIRTY_KEY } from '@/api/enums'
import { useUserStore } from '@/stores/user'
import { invalidate, useDomainRefresh } from '@/utils/dataFreshness'

const userStore = useUserStore()

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
  roleIds: [],
  // 新增用户默认首页全部业务 TAB 可见
  dashboardTabs: DASHBOARD_TABS.map((t) => t.key)
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

/* 新增模式下按所选角色动态推导首页 TAB 默认勾选（多角色并集；编辑模式不覆盖已保存配置） */
let tabsDeriveTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => (form.roleIds || []).join(','),
  (val) => {
    if (isEdit.value) return
    if (tabsDeriveTimer) clearTimeout(tabsDeriveTimer)
    tabsDeriveTimer = setTimeout(async () => {
      const roleIds = val ? val.split(',').filter(Boolean) : []
      if (roleIds.length === 0) {
        form.dashboardTabs = DASHBOARD_TABS.map((t) => t.key)
        return
      }
      try {
        const tabs = await getDefaultDashboardTabs(roleIds)
        if (!isEdit.value) form.dashboardTabs = tabs && tabs.length > 0 ? tabs : DASHBOARD_TABS.map((t) => t.key)
      } catch { /* 推导失败保持当前勾选 */ }
    }, 300)
  }
)

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
    roleOptions.value = (res || []).filter((r: Role) => r.roleCode !== SUPER_ADMIN_ROLE_CODE)
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
  // 先同步填行数据并打开弹窗（详情接口仅用于补 TAB 勾选）
  Object.assign(form, defaultForm(), {
    id: row.id,
    username: row.username,
    phone: row.phone ?? '',
    dept: row.dept ?? '',
    status: row.status,
    roleIds: (row.roles || []).map((r) => r.id as number | string),
    dashboardTabs: DASHBOARD_TABS.map((t) => t.key)
  })
  isEdit.value = true
  dialogTitle.value = '编辑用户'
  dialogVisible.value = true
  formRef.value?.clearValidate()
  // 详情接口带回 TAB 勾选（列表接口不携带）；空/缺失视为全部可见
  getUser(row.id as number | string)
    .then((detail) => {
      // 防竞态：请求返回时用户可能已关闭弹窗或切到「新增」，不得回填
      if (!dialogVisible.value || !isEdit.value || form.id !== row.id) return
      const tabs = detail?.dashboardTabs
      form.dashboardTabs = tabs && tabs.length > 0 ? tabs : DASHBOARD_TABS.map((t) => t.key)
    })
    .catch(() => {
      if (!dialogVisible.value || !isEdit.value || form.id !== row.id) return
      form.dashboardTabs = DASHBOARD_TABS.map((t) => t.key)
    })
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
          roleIds: form.roleIds,
          dashboardTabs: form.dashboardTabs
        }
        await updateUser(payload)
        ElMessage.success('已更新')
      } else {
        await addUser(form)
        ElMessage.success('已新增')
      }
      dialogVisible.value = false
      // 2026-09-20（F7-184）：置脏标志，配合列表页 onActivated 按需刷新
      invalidate('user')
      loadData()
    } catch {
      // 错误已在拦截器中提示
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row: UserVO) {
  // 2026-09-20（F7-173 同族 · 顺带修复）：confirm 与请求各自 try/catch ——
  // 原先共用一个 catch ⇒ 删除失败（如用户仍被引用）会被当成"用户取消"静默吞掉。
  try {
    await ElMessageBox.confirm(`确定要删除用户「${row.username}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch { return }
  try {
    await deleteUser(row.id as number | string)
    ElMessage.success('已删除')
    if (tableData.value.length === 1 && pagination.pageNum > 1) {
      pagination.pageNum--
    }
    invalidate('user')
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

async function handleToggleStatus(row: UserVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '禁用'
  // 同上（顺带修复）：删除/启停都属写操作，失败必须提示，不能与"取消"共用一个 catch
  try {
    await ElMessageBox.confirm(`确定要${action}用户「${row.username}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch { return }
  try {
    await toggleUserStatus(row.id as number | string, next)
    ElMessage.success(`${action}成功`)
    invalidate('user')
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || `${action}失败`) }
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
      ElMessage.success('已重置密码')
      resetDialogVisible.value = false
    } catch {
      // 错误已在拦截器中提示
    } finally {
      resetLoading.value = false
    }
  })
}

/* ============== 页面权限弹窗（2026-09-18：在用户管理里直接编辑该用户的页面可见性） ============== */
const permDialogVisible = ref(false)
const permSaving = ref(false)
const permUserId = ref<number | string>('')
const permUsername = ref('')
const permMode = ref('ROLE')
const permRoleKeys = ref<(number | string)[]>([])
const permCheckedKeys = ref<(number | string)[]>([])
const permMenuTree = ref<MenuVO[]>([])
const permTreeRef = ref()

function isCustomPerm(row: UserVO) {
  return (row.menuMode || 'ROLE') === 'CUSTOM'
}
function isSuperAdminRow(row: UserVO) {
  return (row.roles || []).some((r) => r.roleCode === SUPER_ADMIN_ROLE_CODE)
}
function isSelfRow(row: UserVO) {
  return String(row.id ?? '') === String(userStore.userInfo?.id ?? '')
}
/** 不能改自己（防自锁：把自己「用户管理」去掉就再也进不来）也不能改超管（恒为跟随角色） */
function canEditPerm(row: UserVO) {
  return !isSelfRow(row) && !isSuperAdminRow(row) && row.username !== 'lin'
}
function permDisabledReason(row: UserVO) {
  if (isSelfRow(row)) return '不能修改自己的页面权限（防止把自己锁在门外），请由其他管理员操作'
  if (isSuperAdminRow(row) || row.username === 'lin') return '超级管理员用户恒为「跟随角色」，不可自定义'
  return ''
}

async function handleOpenPerm(row: UserVO) {
  permUserId.value = row.id as number | string
  permUsername.value = row.username
  permMode.value = 'ROLE'
  permRoleKeys.value = []
  permCheckedKeys.value = []
  permDialogVisible.value = true
  // 菜单树：与角色「分配权限」同一接口（管理端全量树）
  try {
    permMenuTree.value = (await getMenuTree()) || []
  } catch {
    permMenuTree.value = []
  }
  try {
    const perm = await getUserMenus(permUserId.value)
    permRoleKeys.value = perm.roleMenuIds || []
    if ((perm.menuMode || 'ROLE') === 'CUSTOM') {
      permMode.value = 'CUSTOM'
      permCheckedKeys.value = perm.menuIds && perm.menuIds.length > 0 ? perm.menuIds : permRoleKeys.value
      await nextTick()
      permTreeRef.value?.setCheckedKeys(permCheckedKeys.value)
    }
  } catch {
    permMode.value = 'ROLE'
  }
}

/* 切到「自定义」时：若尚无勾选，用「角色现有权限」预填（在其基础上微调，而不是从零勾） */
watch(permMode, async (val) => {
  if (val !== 'CUSTOM') return
  const keys = permCheckedKeys.value.length > 0 ? permCheckedKeys.value : permRoleKeys.value
  permCheckedKeys.value = keys
  await nextTick()
  permTreeRef.value?.setCheckedKeys(keys)
})

async function handleSavePerm() {
  permSaving.value = true
  try {
    if (permMode.value === 'ROLE') {
      await saveUserMenus(permUserId.value, { menuMode: 'ROLE' })
      ElMessage.success('已设为「跟随角色」')
    } else {
      // 与角色「分配权限」一致：提交"全选 + 半选"
      const checkedKeys = permTreeRef.value?.getCheckedKeys() || []
      const halfCheckedKeys = permTreeRef.value?.getHalfCheckedKeys() || []
      const allKeys = [...checkedKeys, ...halfCheckedKeys]
      if (allKeys.length === 0) {
        ElMessage.warning('自定义页面权限至少要勾选一个页面')
        return
      }
      await saveUserMenus(permUserId.value, { menuMode: 'CUSTOM', menuIds: allKeys })
      ElMessage.success('自定义页面权限已保存')
    }
    // 2026-09-20（F7-184）：权限变化置脏，切回本页时刷新一次
    invalidate('user')
    permDialogVisible.value = false
    loadData()
  } catch {
    // 错误已在拦截器中提示
  } finally {
    permSaving.value = false
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

onMounted(() => {
  loadRoles()
  loadData()
})
// 2026-09-20（F7-184）：本路由在 keep-alive 内 ⇒ 切回 Tab 时 onMounted 不再触发；按需刷新（脏标志由本页写操作置位）
useDomainRefresh('user', () => {
    loadData()
}, SYSTEM_USER_DIRTY_KEY)

</script>

<template>
  <div class="page-list">
    <!-- 查询栏 -->
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
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
          
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/system/role')">+ 新增</div></template>
              </el-select>
        </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button v-perm="'system:user'" type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1030px > 内容区 971px
           ⇒ 横向滚动 59px。收窄为合计 888px；操作列按 5 个按钮的实际需要给 236px（权限/编辑/重置密码/禁用/删除
           ＝12 个汉字 + 4 个 12px 间距 + 单元格内边距）。 -->
      <el-table v-loading="tableLoading" :data="tableData" border stripe>
        <el-table-column prop="username" label="用户名" min-width="110" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as UserVO).phone || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="dept" label="部门" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as UserVO).dept || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="160">
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
        <el-table-column label="页面权限" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="isCustomPerm(row as UserVO)" type="warning" size="small">自定义</el-tag>
            <el-tag v-else type="info" size="small">跟随角色</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290" align="center">
          <template #default="{ row }">
            <el-tooltip
              :content="permDisabledReason(row as UserVO)"
              :disabled="canEditPerm(row as UserVO)"
              placement="top"
            >
              <span>
                <el-button v-perm="'system:user'"
                  type="primary"
                  link
                  :disabled="!canEditPerm(row as UserVO)"
                  @click="handleOpenPerm(row as UserVO)"
                >
                  权限
                </el-button>
              </span>
            </el-tooltip>
            <el-button v-perm="'system:user'" type="primary" link @click="handleEdit(row as UserVO)">编辑</el-button>
            <el-button v-perm="'system:user'" type="warning" link @click="handleOpenReset(row as UserVO)">重置密码</el-button>
            <el-button v-perm="'system:user'"
              :type="(row as UserVO).status === 1 ? 'info' : 'success'"
              link
              @click="handleToggleStatus(row as UserVO)"
            >
              {{ (row as UserVO).status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button v-perm="'system:user'" type="danger" link @click="handleDelete(row as UserVO)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="var(--app-dialog-sm)" :close-on-click-modal="false">
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
              
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/system/role')">+ 新增</div></template>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="首页TAB">
              <el-checkbox-group v-model="form.dashboardTabs">
                <el-checkbox v-for="t in DASHBOARD_TABS" :key="t.key" :value="t.key">{{ t.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 页面权限弹窗（2026-09-18：直接编辑该用户可见的页面） -->
    <el-dialog
      v-model="permDialogVisible"
      :title="`页面权限 - ${permUsername}`"
      width="var(--app-dialog-sm)"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="权限模式">
          <el-radio-group v-model="permMode">
            <el-radio value="ROLE">跟随角色（默认）</el-radio>
            <el-radio value="CUSTOM">自定义</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="permMode === 'ROLE'" label="可见页面">
          <span class="perm-hint">
            该用户可见页面 = 所属角色的权限（当前共 {{ permRoleKeys.length }} 项）；角色权限变化时自动跟随。
          </span>
        </el-form-item>
        <template v-else>
          <el-form-item label="可见页面">
            <div class="perm-tree">
              <el-tree
                ref="permTreeRef"
                :data="permMenuTree"
                node-key="id"
                show-checkbox
                :default-checked-keys="permCheckedKeys"
                :props="{ label: 'menuName', children: 'children' }"
                default-expand-all
              />
            </div>
          </el-form-item>
          <el-form-item label=" ">
            <span class="perm-hint">
              自定义后，角色权限变化<strong>不会</strong>自动同步给该用户；保存时会自动保留「首页」并补齐上级目录。
              仅控制页面可见性（后端接口鉴权仍按角色）。
            </span>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="handleSavePerm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetDialogVisible" title="重置密码" width="var(--app-dialog-sm)" :close-on-click-modal="false">
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
/* 根容器/卡片内边距已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body） */

.query-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.role-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

/* 分页样式已统一到全局（styles/page.css 的 .pagination） */

/* 页面权限弹窗 */
.perm-tree {
  width: 100%;
  max-height: 320px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
}

.perm-hint {
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
