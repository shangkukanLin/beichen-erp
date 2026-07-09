<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getCustomerPage,
  updateCustomerStatus,
  createCustomer,
  updateCustomer,
  type Customer,
  type PageResult
} from '@/api/customer'

const query = reactive({
  code: '',
  name: '',
  status: '' as string | number
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableLoading = ref(false)
const tableData = ref<Customer[]>([])

const statusOptions = [
  { label: '合作中', value: 1 },
  { label: '已停用', value: 0 }
]

const dialogVisible = ref(false)
const dialogTitle = ref('新增客户')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const defaultForm = (): Customer => ({
  code: '',
  name: '',
  contact: '',
  phone: '',
  address: '',
  creditPeriod: 0,
  creditLimit: 0,
  receivableBalance: 0,
  prepaidBalance: 0,
  status: 1,
  remark: ''
})

const form = reactive<Customer>(defaultForm())

const rules: FormRules = {
  name: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

async function loadData() {
  tableLoading.value = true
  try {
    const params: any = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (query.code) params.code = query.code
    if (query.name) params.name = query.name
    if (query.status !== '' && query.status !== null) params.status = query.status

    const res = await getCustomerPage(params)
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
  query.code = ''
  query.name = ''
  query.status = ''
  pagination.pageNum = 1
  loadData()
}

function handleAdd() {
  Object.assign(form, defaultForm())
  dialogTitle.value = '新增客户'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function handleEdit(row: Customer) {
  Object.assign(form, defaultForm(), row)
  dialogTitle.value = '编辑客户'
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
        await updateCustomer(form)
        ElMessage.success('修改成功')
      } else {
        await createCustomer(form)
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

async function handleToggleStatus(row: Customer) {
  const target = row.status === 1 ? 0 : 1
  const tip = target === 0 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${tip}客户「${row.name}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await updateCustomerStatus(row.id as number, target)
    ElMessage.success('操作成功')
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
  return status === 1 ? '合作中' : '已停用'
}

function statusType(status: number) {
  return status === 1 ? 'success' : 'info'
}

function fmtMoney(v?: number) {
  if (v === undefined || v === null) return '0.00'
  return Number(v).toFixed(2)
}

onMounted(() => {
  loadData()
})
onActivated(() => {
  loadData()
})
</script>

<template>
  <div class="customer-page">
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="编码">
          <el-input v-model="query.code" placeholder="请输入客户编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="请选择状态" clearable style="width: 120px">
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

    <el-card shadow="never" class="table-card">
      <el-table v-loading="tableLoading" :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="code" label="编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="creditPeriod" label="账期(天)" width="90" align="center" />
        <el-table-column prop="creditLimit" label="信用额度" width="120" align="right">
          <template #default="{ row }">{{ fmtMoney(row.creditLimit) }}</template>
        </el-table-column>
        <el-table-column prop="receivableBalance" label="应收余额" width="120" align="right">
          <template #default="{ row }"><span style="color:#f56c6c">{{ fmtMoney(row.receivableBalance) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row as Customer)">编辑</el-button>
            <el-button :type="row.status === 1 ? 'warning' : 'success'" link @click="handleToggleStatus(row as Customer)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="编码" prop="code">
              <el-input v-model="form.code" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入客户名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contact" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话">
              <el-input v-model="form.phone" placeholder="请输入电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="账期(天)">
              <el-input-number v-model="form.creditPeriod" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="信用额度">
              <el-input-number v-model="form.creditLimit" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="form.address" placeholder="请输入地址" />
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
            <el-form-item label="备注">
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
.customer-page {
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
