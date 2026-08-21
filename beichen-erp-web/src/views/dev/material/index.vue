<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DevMaterialTypeLabel } from '@/api/enums'
import MaterialFormDialog from '@/components/dev/MaterialFormDialog.vue'
import { useOptionsStore } from '@/stores/options'

const loading = ref(false)
const router = useRouter()
const optionsStore = useOptionsStore()
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const materialTypeOptions = Object.values(DevMaterialTypeLabel)
const projectOptions = computed(() => optionsStore.projects || [])
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

onMounted(() => { optionsStore.ensureProjects(); loadList() })
onActivated(() => { loadList() })
</script>

<template>
  <div class="material-page">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="物料名称"><el-input v-model="query.name" placeholder="模糊搜索" clearable style="width:160px" @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="关联项目">
          <el-select v-model="query.projectId" placeholder="全部" clearable style="width:160px">
            <el-option label="未关联项目" :value="''" />
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width:140px">
            <el-option v-for="t in materialTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <div style="margin-bottom:12px">
        <el-button type="primary" @click="handleAdd">+ 新增研发物料</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="quantity" label="数量" width="90" />
        <el-table-column prop="amount" label="金额" width="110" />
        <el-table-column label="存放位置" width="140">
          <template #default="{ row }">{{ row.warehouseName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="关联项目" min-width="150">
          <template #default="{ row }">{{ row.projectName || projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
