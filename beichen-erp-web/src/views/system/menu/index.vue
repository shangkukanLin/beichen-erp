<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { MenuType, MenuTypeLabel, SYSTEM_MENU_DIRTY_KEY } from '@/api/enums'
import {
  getMenuTree,
  addMenu,
  updateMenu,
  deleteMenu,
  type MenuVO,
  type MenuDTO
} from '@/api/system'

/* ============== 扁平化菜单数据 ============== */

interface FlatMenu extends MenuVO {
  level: number
}

const tableLoading = ref(false)
const userStore = useUserStore()
const tableData = ref<FlatMenu[]>([])
const menuTree = ref<MenuVO[]>([])

function flattenTree(tree: MenuVO[], level = 0): FlatMenu[] {
  const result: FlatMenu[] = []
  for (const item of tree) {
    result.push({ ...item, level })
    if (item.children && item.children.length > 0) {
      result.push(...flattenTree(item.children, level + 1))
    }
  }
  return result
}

async function loadData() {
  tableLoading.value = true
  try {
    const res = await getMenuTree()
    menuTree.value = res || []
    tableData.value = flattenTree(menuTree.value)
  } catch {
    menuTree.value = []
    tableData.value = []
  } finally {
    tableLoading.value = false
  }
}

/* ============== 类型选项 ============== */

const typeOptions = [
  { label: MenuTypeLabel[MenuType.CATALOG], value: MenuType.CATALOG },
  { label: MenuTypeLabel[MenuType.MENU], value: MenuType.MENU }
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

/* ============== 新增/编辑弹窗 ============== */

const dialogVisible = ref(false)
const dialogTitle = ref('新增菜单')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)

const defaultForm = (): MenuDTO => ({
  id: undefined,
  parentId: 0,
  menuName: '',
  menuType: MenuType.MENU,
  routePath: '',
  routeName: '',
  icon: '',
  sortOrder: 1,
  visible: 1,
  status: 1
})

const form = reactive<MenuDTO>(defaultForm())

// 父菜单下拉树数据（只允许选目录类型作为父节点）
// 2026-09-20（F7-187）：新增 excludeId —— 编辑时必须把"正在编辑的节点自身及其整棵子树"排除，
// 否则可把父级选成自己或自己的后代 ⇒ 形成父子环（菜单树渲染异常 / 数据自引用）。
function buildTreeSelectOptions(tree: MenuVO[], excludeId?: number): MenuVO[] {
  const result: MenuVO[] = []
  for (const item of tree) {
    if (excludeId != null && item.id === excludeId) continue   // 自身及子孙都不作为父级候选
    if (item.menuType === MenuType.CATALOG) {
      result.push({ ...item })
    }
    if (item.children && item.children.length > 0) {
      result.push(...buildTreeSelectOptions(item.children, excludeId))
    }
  }
  return result
}

const parentOptions = ref<MenuVO[]>([])

const rules: FormRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  sortOrder: [{ required: true, message: '请输入排序号', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

function handleAdd() {
  Object.assign(form, defaultForm())
  isEdit.value = false
  dialogTitle.value = '新增菜单'
  parentOptions.value = buildTreeSelectOptions(menuTree.value)
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function handleEdit(row: FlatMenu) {
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId,
    menuName: row.menuName,
    menuType: row.menuType,
    routePath: row.routePath,
    routeName: row.routeName,
    icon: row.icon,
    sortOrder: row.sortOrder,
    visible: row.visible,
    status: row.status
  })
  isEdit.value = true
  dialogTitle.value = '编辑菜单'
  // F7-187：排除自身及子孙，防止把父级选成自己/后代形成环
  parentOptions.value = buildTreeSelectOptions(menuTree.value, row.id as number)
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
        await updateMenu(form)
        ElMessage.success('已更新')
      } else {
        await addMenu(form)
        ElMessage.success('已新增')
      }
      dialogVisible.value = false
      // 2026-09-20（F7-184）：置脏标志，配合列表页 onActivated 按需刷新
      sessionStorage.setItem(SYSTEM_MENU_DIRTY_KEY, '1')
      loadData()
      userStore.fetchMenus()
    } catch {
      // 错误已在拦截器中提示
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row: FlatMenu) {
  // 2026-09-20（F7-173 同族 · 顺带修复）：confirm 与请求各自 try/catch ——
  // 原先共用一个 catch ⇒ 删除接口报错也会被当成"用户取消"静默吞掉（删菜单失败却毫无提示）。
  try {
    await ElMessageBox.confirm(`确定要删除菜单「${row.menuName}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch { return }
  try {
    await deleteMenu(row.id as number | string)
    ElMessage.success('已删除')
    sessionStorage.setItem(SYSTEM_MENU_DIRTY_KEY, '1')
    loadData()
    userStore.fetchMenus()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

function typeText(type: string) {
  return MenuTypeLabel[type] || (type === MenuType.CATALOG ? '目录' : '菜单')
}

function statusText(status: number) {
  return status === 1 ? '启用' : '禁用'
}

function statusType(status: number) {
  return status === 1 ? 'success' : 'info'
}

onMounted(() => {
  loadData()
})
// 2026-09-20（F7-184）：本路由在 keep-alive 内 ⇒ 切回 Tab 时 onMounted 不再触发；按需刷新（脏标志由本页写操作置位）
onActivated(() => {
  if (sessionStorage.getItem(SYSTEM_MENU_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(SYSTEM_MENU_DIRTY_KEY)
    loadData()
  }
})

</script>

<template>
  <div class="menu-page">
    <!-- 操作栏 -->
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <div class="toolbar">
          <el-button v-perm="'system:menu'" type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="tableLoading" :data="tableData" border stripe row-key="id">
        <el-table-column label="菜单名称" min-width="185" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ paddingLeft: row.level * 24 + 'px' }">
              <span v-if="row.level > 0" style="color: var(--app-text-placeholder); margin-right: 4px">├</span>
              {{ row.menuName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="76" align="center">
          <template #default="{ row }">
            <el-tag :type="row.menuType === MenuType.CATALOG ? 'warning' : 'info'" size="small">
              {{ typeText(row.menuType) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B7（实测驱动）：路由路径 min140→**249**（实测需 249：/outsource/material-order/detail 这类长路径
             原 177 渲染宽下 12/100 行被截断）；「路由路径」「图标」登记进白名单（长度随新增菜单/图标变化，均带 tooltip） -->
        <el-table-column prop="routePath" label="路由路径" min-width="249" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.routePath || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span>
              <el-icon><component :is="row.icon" /></el-icon>
              {{ row.icon }}
            </span>
          </template>
        </el-table-column>
        <!-- 2026-09-26 B7：类型/排序/状态/操作 按预算收拢（合计 = 190+76+249+150+62+72+136 = 935 ≤ 956） -->
        <el-table-column prop="sortOrder" label="排序" width="62" align="center" />
        <el-table-column label="状态" width="72" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'system:menu'" type="primary" link @click="handleEdit(row as FlatMenu)">编辑</el-button>
            <el-button v-perm="'system:menu'" type="danger" link @click="handleDelete(row as FlatMenu)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="var(--app-dialog-sm)" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="父菜单" prop="parentId">
          <el-select
            v-model="form.parentId"
            placeholder="请选择父菜单（空为顶级）"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="p in parentOptions"
              :key="p.id"
              :label="p.menuName"
              :value="p.id as number"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="form.menuType">
            <el-radio v-for="o in typeOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由路径" prop="routePath">
          <el-input v-model="form.routePath" placeholder="请输入路由路径，如 /dashboard" />
        </el-form-item>
        <el-form-item label="路由名称" prop="routeName">
          <el-input v-model="form.routeName" placeholder="请输入路由名称，如 Dashboard" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="请输入图标名称，如 HomeFilled" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.menu-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.query-card :deep(.el-card__body),
.table-card :deep(.el-card__body) {
  padding: 16px;
}
</style>
