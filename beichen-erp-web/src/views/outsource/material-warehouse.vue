<script setup lang="ts">
import { WarehouseCategory, WarehouseType, WarehouseTypeLabel } from '@/api/enums'
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()

const query = reactive({ warehouseName: '' })
const allData = ref<any[]>([])
const activeTab = ref('active')
const tableLoading = ref(false)
// 自有物料仓：只展示自有仓库中的辅料仓
const WAREHOUSE_TYPE = WarehouseType.AUXILIARY
const activeData = computed(() => allData.value.filter(v => v.status === 1))
const stoppedData = computed(() => allData.value.filter(v => v.status === 0))

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageSize: 500, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WAREHOUSE_TYPE }
    if (query.warehouseName) p.warehouseName = query.warehouseName
    const r = await request.get<any, any>('/warehouse/page', { params: p })
    allData.value = r?.records || []
  } finally { tableLoading.value = false }
}
function handleQuery() { loadData() }
function handleReset() { query.warehouseName = ''; loadData() }

const dialogVisible = ref(false); const dialogTitle = ref(''); const submitLoading = ref(false)
const defForm = () => ({ id: undefined as any, code: '', warehouseName: '', warehouseType: WAREHOUSE_TYPE, address: '', manager: '', phone: '', status: 1, remark: '' })
const form = reactive(defForm()); const isEdit = ref(false)

function handleAdd() { Object.assign(form, defForm()); isEdit.value = false; dialogTitle.value = '新增仓库'; dialogVisible.value = true }
function handleEdit(row: any) { Object.assign(form, defForm(), row); isEdit.value = true; dialogTitle.value = '编辑仓库'; dialogVisible.value = true }

async function handleSubmit() { if (!form.warehouseName) { ElMessage.warning('请输入仓库名称'); return }; submitLoading.value = true
  try {     if (isEdit.value) { await request.put('/warehouse', form); ElMessage.success('已更新') } else { await request.post('/warehouse', form); ElMessage.success('已新增') }
    dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }

// F7-136（2026-09-20）：与同模块 `delivery/index.vue` 的 `goWhDetail` **统一为同一判定**
// （委外仓 → /outsource/warehouse/detail，自有仓 → /inventory/warehouse/detail）。
// 本页只管理"自有物料仓"（INVENTORY+AUXILIARY），故结果与原"一律跳 inventory"完全一致，
// 仅消除"同一语义两套写法"（改一处忘另一处的来源）。
function handleDetail(row: any) {
  if (row.factoryId != null) router.push(`/outsource/warehouse/detail/${row.id}`)
  else router.push(`/inventory/warehouse/detail/${row.id}`)
}

async function handleToggleStatus(row: any) {
  // F7-133（2026-09-20）：与 warehouse.vue 同款修法 —— 去掉乐观更新，先落库成功再刷新。
  const next = row.status === 1 ? 0 : 1
  await request.put('/warehouse', { ...row, status: next })
  ElMessage.success(next === 1 ? '已启用' : '已停用'); loadData()
}

onMounted(() => loadData())

</script>

<template>
  <div class="wh-page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="仓库名称"><el-input v-model="query.warehouseName" placeholder="仓库名称" clearable @keyup.enter="handleQuery" /></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
      </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="启用" name="active" />
        <el-tab-pane label="停用" name="stopped" />
      </el-tabs>

      <el-table v-if="activeTab==='active'" :data="activeData" border stripe v-loading="tableLoading" style="width:100%" @row-click="handleDetail">
        <el-table-column prop="code" label="仓库编码" width="160" />
        <!-- 2026-09-26 B5b：仓库名称做成链接进仓库详情（自有物料仓 ⇒ 固定走 /inventory/warehouse） -->
        <el-table-column prop="warehouseName" label="仓库名称" min-width="140" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">{{ row.warehouseName }}</el-button></template>
        </el-table-column>
        <el-table-column label="仓型" width="90"><template #default="{row}">{{ WarehouseTypeLabel[row.warehouseType] || row.warehouseType }}</template></el-table-column>
        <el-table-column prop="address" label="地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="manager" label="负责人" width="80" />
        <el-table-column prop="phone" label="联系电话" width="120" />
        <el-table-column label="操作" width="155" align="center">
          <template #default="{row}"><el-button v-perm="'outsource:material-warehouse'" type="primary" link @click.stop="handleDetail(row)">详情</el-button><el-button v-perm="'outsource:material-warehouse'" type="primary" link @click.stop="handleEdit(row)">编辑</el-button><el-button v-perm="'outsource:material-warehouse'" type="warning" link @click.stop="handleToggleStatus(row)">停用</el-button></template>
        </el-table-column>
      </el-table>

      <el-table v-if="activeTab==='stopped'" :data="stoppedData" border stripe style="width:100%" @row-click="handleDetail">
        <el-table-column prop="code" label="仓库编码" width="160" />
        <!-- 2026-09-26 B5b：仓库名称做成链接进仓库详情（自有物料仓 ⇒ 固定走 /inventory/warehouse） -->
        <el-table-column prop="warehouseName" label="仓库名称" min-width="140" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">{{ row.warehouseName }}</el-button></template>
        </el-table-column>
        <el-table-column label="仓型" width="90"><template #default="{row}">{{ WarehouseTypeLabel[row.warehouseType] || row.warehouseType }}</template></el-table-column>
        <el-table-column prop="address" label="地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="manager" label="负责人" width="80" />
        <el-table-column prop="phone" label="联系电话" width="120" />
        <el-table-column label="操作" width="155" align="center">
          <template #default="{row}"><el-button v-perm="'outsource:material-warehouse'" type="primary" link @click.stop="handleDetail(row)">详情</el-button><el-button v-perm="'outsource:material-warehouse'" type="primary" link @click.stop="handleEdit(row)">编辑</el-button><el-button v-perm="'outsource:material-warehouse'" type="success" link @click.stop="handleToggleStatus(row)">启用</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="var(--app-dialog-sm)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="仓库名称" required><el-input v-model="form.warehouseName" /></el-form-item>
        <!-- 仓型：本页只管理自有物料仓（辅料仓），值=code、显示=中文 label（原写法把 code 当 label，界面显示 "AUXILIARY"） -->
        <el-form-item label="仓型"><el-select v-model="form.warehouseType" style="width:100%"><el-option :label="WarehouseTypeLabel[WAREHOUSE_TYPE]" :value="WAREHOUSE_TYPE" /></el-select></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.manager" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
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
