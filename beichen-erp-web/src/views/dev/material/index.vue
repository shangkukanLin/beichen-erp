<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DevMaterialTypeLabel, DevMaterialStatusLabel, DEV_MATERIAL_DIRTY_KEY } from '@/api/enums'
import MaterialFormDialog from '@/components/dev/MaterialFormDialog.vue'

const loading = ref(false)
const router = useRouter()
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const materialTypeOptions = Object.entries(DevMaterialTypeLabel).map(([value, label]) => ({ value, label }))
const fetchProjects = (kw: string) => request.get('/dev/project/page', { params: { pageSize: 500, name: kw } })
const projectOptions = ref<any[]>([])
async function loadProjectOptions() { const r: any = await fetchProjects(''); projectOptions.value = (r?.records || []) as any[] }
const materialDialog = ref<any>(null)

const query = reactive<{ name: string; projectId: number | '' ; type: string }>({
  name: '', projectId: '', type: ''
})

function projectName(id: number) { const f = projectOptions.value.find((x: any) => x.id === id); return f ? f.name : '未关联' }

async function loadList() {
  loading.value = true
  try {
    const params: any = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (query.name && query.name.trim()) params.name = query.name.trim()
    if (query.projectId !== '') params.projectId = query.projectId
    if (query.type) params.type = query.type
    const res: any = await request.get('/dev/purchase-item/page', { params })
    list.value = res?.records || []
    total.value = res?.total || 0
  } catch (e: any) { ElMessage.error('加载失败: ' + (e?.message || '未知错误')) } finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; loadList() }
function handleReset() { query.name = ''; query.projectId = ''; query.type = ''; handleSearch() }
function handlePageChange(p: number) { pageNum.value = p; loadList() }
function handleSizeChange(s: number) { pageSize.value = s; pageNum.value = 1; loadList() }

function handleAdd() { materialDialog.value?.open() }
function handleDetail(row: any) { router.push(`/dev/material/detail/${row.id}`) }
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该物料记录吗？', '提示', { type: 'warning' })
    await request.delete(`/dev/purchase-item/${row.id}`)
    ElMessage.success('已删除'); loadList()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

onActivated(() => {
  // 详情页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(DEV_MATERIAL_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(DEV_MATERIAL_DIRTY_KEY)
    loadList()
  }
})
onMounted(() => { loadProjectOptions(); loadList() })

</script>

<template>
  <div class="material-page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="物料名称"><el-input v-model="query.name" placeholder="模糊搜索" clearable style="width:160px" @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="关联项目">
          <RemoteSelect v-model="query.projectId" :fetch="fetchProjects" placeholder="全部" clearable style="width:160px">
            <el-option label="未关联项目" :value="''" />
          </RemoteSelect>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width:140px">
            <el-option v-for="t in materialTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="handleSearch">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="primary" :icon="'Plus'" @click="handleAdd">新增研发物料</el-button>
      </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <el-table :data="list" v-loading="loading" border stripe @row-click="handleDetail">
        <el-table-column label="类型" width="120"><template #default="{ row }">{{ DevMaterialTypeLabel[row.type] || row.type }}</template></el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="quantity" label="数量" width="90" />
        <el-table-column label="存放位置" width="160">
          <template #default="{ row }">{{ row.warehouseName || '-' }}</template>
        </el-table-column>
        <el-table-column label="关联项目" min-width="150">
          <template #default="{ row }">
            <el-link
              v-if="row.projectId"
              type="primary"
              @click.stop="$router.push(`/dev/project/edit/${row.projectId}`)"
            >{{ row.projectName || projectName(row.projectId) }}</el-link>
            <span v-else style="color:var(--app-text-placeholder)">未关联</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }">{{ DevMaterialStatusLabel[row.status] || row.status }}</template></el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleDetail(row)">详情</el-button>
            <el-button link type="danger" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:12px;justify-content:flex-end"
        background layout="total, sizes, prev, pager, next"
        :total="total" :current-page="pageNum" :page-size="pageSize"
        :page-sizes="[10,20,50]" @current-change="handlePageChange" @size-change="handleSizeChange" />
    </el-card>

    <MaterialFormDialog ref="materialDialog" @saved="loadList" />
  </div>
</template>
