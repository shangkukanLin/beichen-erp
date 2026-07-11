<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getMaterialPage,
  addMaterial,
  updateMaterial,
  deleteMaterial,
  type Material,
  type MaterialQueryParams
} from '@/api/material'
import request from '@/utils/request'

// 品牌下拉
const brandOptions = ref<{ id: number; brandName: string }[]>([])
async function loadBrands() {
  try { const res = await request.get<any, any>('/brand/enabled'); brandOptions.value = res || [] } catch (e: any) { console.warn('加载品牌失败', e?.message || e) }
}

// 查询参数
const query = reactive<MaterialQueryParams>({
  name: ''
})

// Tab 切换
const activeTab = ref('正常')

// 分页
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableLoading = ref(false)
const tableData = ref<Material[]>([])

// 分类下拉选项
const categoryOptions = [
  { label: '原材料', value: '原材料' },
  { label: '半成品', value: '半成品' },
  { label: '成品', value: '成品' },
  { label: '辅料', value: '辅料' }
]

const statusOptions = [
  { label: '正常', value: '正常' },
  { label: '停售', value: '停售' },
  { label: '研发中', value: '研发中' }
]

// 弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('新增物料')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const defaultForm = (): Material => ({
  name: '',
  brandId: undefined,
  safetyStock: 0,
  currentStock: 0,
  status: '正常',
  remark: '',
  code: '',
  category: '',
  spec: '',
  unit: ''
} as Material)

const form = reactive<Material>(defaultForm())

const rules: FormRules = {
  name: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

async function loadData() {
  tableLoading.value = true
  try {
    const params: MaterialQueryParams = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (query.name) params.name = query.name
    params.status = activeTab.value

    const res = await getMaterialPage(params)
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch {
    tableData.value = []
    pagination.total = 0
  } finally {
    tableLoading.value = false
  }
}

function handleTabChange() {
  pagination.pageNum = 1
  loadData()
}

function handleQuery() {
  pagination.pageNum = 1
  loadData()
}

function handleReset() {
  query.name = ''
  pagination.pageNum = 1
  loadData()
}

function handleAdd() {
  Object.assign(form, defaultForm())
  dialogTitle.value = '新增物料'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function getBrandName(brandId: number | string | undefined) {
  if (brandId == null) return ''
  const b = brandOptions.value.find(o => o.id === Number(brandId))
  return b ? b.brandName : ''
}

async function handleEdit(row: Material) {
  Object.assign(form, defaultForm(), row)
  dialogTitle.value = '编辑物料'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateMaterial(form)
        ElMessage.success('修改成功')
      } else {
        await addMaterial(form)
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

async function handleDelete(row: Material) {
  try {
    await ElMessageBox.confirm(`确定要删除物料「${row.name}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteMaterial(row.id as number | string)
    ElMessage.success('删除成功')
    // 删除后若当前页空了，回退一页
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

function isLowStock(row: Material): boolean {
  const safety = Number(row.safetyStock) || 0
  const current = Number(row.currentStock) || 0
  return safety > 0 && current < safety
}

function rowClass({ row }: { row: Material }) {
  return isLowStock(row) ? 'low-stock-row' : ''
}

function statusText(status: string) {
  return status || '正常'
}

function statusType(status: string) {
  if (status === '正常') return 'success'
  if (status === '停售') return 'danger'
  return 'warning'
}

onMounted(() => {
  loadBrands()
  loadData()
})
onActivated(() => { loadBrands(); loadData() })
</script>

<template>
  <div class="material-page">
    <!-- 查询栏 -->
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="正常" name="正常" />
        <el-tab-pane label="停售" name="停售" />
        <el-tab-pane label="研发中" name="研发中" />
      </el-tabs>

      <el-table v-loading="tableLoading" :data="tableData" border stripe :row-class-name="rowClass">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="品牌" min-width="120">
          <template #default="{ row }">{{ getBrandName(row.brandId) }}</template>
        </el-table-column>
        <el-table-column prop="safetyStock" label="安全库存" width="100" align="right" />
        <el-table-column label="当前库存" width="130" align="right">
          <template #default="{ row }">
            <span :style="{ color: isLowStock(row) ? '#f56c6c' : '', fontWeight: isLowStock(row) ? 'bold' : '' }">
              {{ row.currentStock ?? 0 }}
            </span>
            <el-tag v-if="isLowStock(row)" type="danger" size="small" style="margin-left:4px">预警</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row as Material)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row as Material)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-select v-model="form.brandId" placeholder="请选择品牌" clearable style="width:100%">
                <el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安全库存">
              <el-input-number v-model="form.safetyStock" :min="0" :precision="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.material-page {
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

:deep(.low-stock-row) {
  background-color: #fef0f0 !important;
}
</style>
