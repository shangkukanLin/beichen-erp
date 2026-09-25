<script setup lang="ts">
import { WarehouseCategory, WarehouseType } from '@/api/enums'
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const query = reactive({ warehouseName: '', factoryId: undefined as any })
const allData = ref<any[]>([])
const activeTab = ref('active')
const tableLoading = ref(false)
const factoryOptions = ref<any[]>([])
const activeData = computed(() => allData.value.filter(v => v.status === 1))
const stoppedData = computed(() => allData.value.filter(v => v.status === 0))

const fetchFactories = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })

async function loadFactories() {
  const r = await fetchFactories(''); factoryOptions.value = r?.records || []
}

async function loadData() {
  tableLoading.value = true
  try {
    // 委外仓库页面只查询委外仓库（warehouseCategory=OUTSOURCE）
    const p: any = { pageSize: 500, warehouseCategory: WarehouseCategory.OUTSOURCE }
    if (query.warehouseName) p.warehouseName = query.warehouseName
    if (query.factoryId) p.factoryId = query.factoryId
    const r = await request.get<any, any>('/warehouse/page', { params: p })
    allData.value = r?.records || []
  } finally { tableLoading.value = false }
}
function handleQuery() { loadData() }
function handleReset() { query.warehouseName = ''; query.factoryId = undefined; loadData() }

const dialogVisible = ref(false); const dialogTitle = ref(''); const submitLoading = ref(false)
const defForm = () => ({ id: undefined as any, factoryId: undefined as any, warehouseName: '', warehouseCategory: WarehouseCategory.OUTSOURCE, warehouseType: WarehouseType.AUXILIARY, address: '', contact: '', phone: '', status: 1, remark: '' })
const form = reactive(defForm()); const isEdit = ref(false)

function handleAdd() { Object.assign(form, defForm()); isEdit.value = false; dialogTitle.value = '新增委外仓库'; dialogVisible.value = true }
function handleEdit(row: any) { Object.assign(form, defForm(), row); isEdit.value = true; dialogTitle.value = '编辑委外仓库'; dialogVisible.value = true }

async function handleSubmit() { if (!form.warehouseName) { ElMessage.warning('请输入仓库名称'); return }; submitLoading.value = true
  try { if (isEdit.value) { await request.put('/warehouse', form); ElMessage.success('已更新') } else { await request.post('/warehouse', form); ElMessage.success('已新增') }
    dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }

async function handleToggleStatus(row: any) {
  // F7-133（2026-09-20）：原实现是"**先改 row.status 再 PUT**"（乐观更新）⇒ 请求失败时行内状态已翻转、
  // 数据库未变 ⇒ 界面与数据不一致（且无回滚）。改为**先落库成功、再刷新列表**。
  const next = row.status === 1 ? 0 : 1
  await request.put('/warehouse', { ...row, status: next })
  ElMessage.success(next === 1 ? '已启用' : '已停用'); loadData()
}

function handleDetail(row: any) { router.push(`/outsource/warehouse/detail/${row.id}`) }

onMounted(() => { loadFactories(); loadData() })
</script>

<template>
  <div class="wh-page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="仓库名称"><el-input v-model="query.warehouseName" placeholder="仓库名称" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="供应商"><RemoteSelect v-model="query.factoryId" :fetch="fetchFactories" placeholder="全部" clearable style="width:200px" /></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
      </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="启用" name="active" />
        <el-tab-pane label="停用" name="stopped" />
      </el-tabs>

      <el-table v-if="activeTab==='active'" :data="activeData" border stripe v-loading="tableLoading" style="width:100%" @row-click="handleDetail">
        <!-- 2026-09-26 B5b（用户口径「数据显示完整 + 仓库/主体可点」，实测驱动）：
             所属供应商 140→**160**（实测需 218 ⇒ 已登记白名单 + tooltip）并做成链接进供应商详情；
             仓库名称 min140→**180**（委外仓名 = 主体名 +「委外仓」后缀，实测最长 274 ⇒ 白名单 + tooltip）
             并做成链接进委外仓详情；地址 min150→140 抵平（合计 860 ≤ 948）。两张表（启用/停用）同步。 -->
        <el-table-column label="所属供应商" width="160" show-overflow-tooltip>
          <template #default="{row}">
            <el-button v-if="row.factoryId" type="primary" link @click.stop="router.push(`/supplier/detail/${row.factoryId}`)">{{ row.factoryName }}</el-button>
            <span v-else>{{ row.factoryName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="仓库名称" min-width="180" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">{{ row.warehouseName }}</el-button></template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="140" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="80" />
        <el-table-column prop="phone" label="联系电话" width="120" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button><el-button type="success" link @click.stop="handleEdit(row)">编辑</el-button><el-button type="warning" link @click.stop="handleToggleStatus(row)">停用</el-button></template>
        </el-table-column>
      </el-table>

      <el-table v-if="activeTab==='stopped'" :data="stoppedData" border stripe style="width:100%" @row-click="handleDetail">
        <el-table-column label="所属供应商" width="160" show-overflow-tooltip>
          <template #default="{row}">
            <el-button v-if="row.factoryId" type="primary" link @click.stop="router.push(`/supplier/detail/${row.factoryId}`)">{{ row.factoryName }}</el-button>
            <span v-else>{{ row.factoryName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="仓库名称" min-width="180" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">{{ row.warehouseName }}</el-button></template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="140" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="80" />
        <el-table-column prop="phone" label="联系电话" width="120" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button><el-button type="success" link @click.stop="handleEdit(row)">编辑</el-button><el-button type="success" link @click.stop="handleToggleStatus(row)">启用</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="var(--app-dialog-sm)" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="供应商" required><RemoteSelect v-model="form.factoryId" :fetch="fetchFactories" style="width:100%" /></el-form-item>
        <el-form-item required label="仓库名称"><el-input v-model="form.warehouseName" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
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
