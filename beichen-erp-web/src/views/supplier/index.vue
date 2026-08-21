<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { useOptionsStore } from '@/stores/options'
import {
  getSupplierPage, addSupplier, updateSupplier, toggleSupplierStatus,
  getSupplierProducts, saveSupplierProducts,
  type SupplierVO, type SupplierDTO, type SupplierProductVO, type SupplierProductDTO
} from '@/api/system'

const route = useRoute()
const router = useRouter()
const optionsStore = useOptionsStore()

// ---------- 根据路由确定供应商类型 ----------
const typeMap: Record<string, { title: string; type: string }> = {
  '/supplier/solution': { title: '方案商', type: 'solution' },
  '/supplier/factory': { title: '委外加工厂', type: 'factory' },
  '/supplier/product': { title: '成品供应商', type: 'product' },
  '/supplier/material-supplier': { title: '辅料商', type: 'material' }
}
const currentType = computed(() => typeMap[route.path]?.type || 'solution')
const pageTitle = computed(() => typeMap[route.path]?.title || '供应商')

// 监听路由变化重新加载
watch(() => route.path, () => { pagination.pageNum = 1; activeTab.value = 'active'; loadData() })

// ---------- 委外加工厂 Tab ----------
const activeTab = ref('active')

function onFactoryTabChange(tab: any) {
  activeTab.value = tab
  pagination.pageNum = 1
  loadData()
}

// ---------- 查询 ----------
const query = reactive({ name: '', phone: '', status: undefined as number | undefined })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableLoading = ref(false)
const tableData = ref<SupplierVO[]>([])

async function loadData() {
  tableLoading.value = true
  try {
    let qStatus = query.status
    // 委外加工厂：用 Tab 控制状态筛选
    if (currentType.value === 'factory') {
      qStatus = activeTab.value === 'active' ? 1 : 0
    }
    const res = await getSupplierPage({
      supplierType: currentType.value,
      name: query.name || undefined,
      phone: query.phone || undefined,
      status: qStatus,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } finally { tableLoading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.name = ''; query.phone = ''; query.status = undefined; activeTab.value = 'active'; pagination.pageNum = 1; loadData() }

// ---------- 表单 ----------
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const defaultForm = (): SupplierDTO => ({
  code: '', name: '', supplierType: '', typeCodes: [currentType.value], status: 1,
  contact: '', phone: '', address: '', remark: '',
  relatedSupplierId: undefined,
  creditPeriodMonths: undefined, creditPeriod: undefined
})
const form = reactive<SupplierDTO>(defaultForm())
const isEdit = ref(false)

const rules: FormRules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}

function handleAdd() {
  Object.assign(form, defaultForm())
  form.typeCodes = [currentType.value]
  isEdit.value = false
  dialogTitle.value = '新增' + pageTitle.value
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function handleDetail(row: SupplierVO) {
  router.push(`/supplier/detail/${row.id}`)
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      form.typeCodes = [currentType.value]
      if (isEdit.value && form.id) {
        await updateSupplier(form)
        ElMessage.success('修改成功')
      } else {
        await addSupplier(form)
        ElMessage.success('新增成功')
      }
      optionsStore.refreshAllSuppliers()
      dialogVisible.value = false
      loadData()
    } catch { /* 拦截器已提示 */ } finally { submitLoading.value = false }
  })
}

async function handleToggleStatus(row: SupplierVO) {
  await toggleSupplierStatus(row.id!)
  optionsStore.refreshAllSuppliers()
  ElMessage.success(row.status === 1 ? '已停用' : '已启用')
  loadData()
}

// ---------- 供应产品 ----------
const productOptions = ref<any[]>([])
async function loadProductOptions(query?: string) {
  try {
    const params: any = { pageSize: 50 }
    if (query) params.keyword = query
    const res = await request.get<any, any>('/product/page', { params })
    productOptions.value = res?.records || []
  } catch { productOptions.value = [] }
}
const productDialogVisible = ref(false)
const productSupplierId = ref<number | string>()
const productList = ref<SupplierProductDTO[]>([])

async function openProducts(row: SupplierVO) {
  productSupplierId.value = row.id
  const list = await getSupplierProducts(row.id!)
  productList.value = (list || []).map(p => ({ productId: p.productId, unitPrice: p.unitPrice, remark: p.remark }))
  productDialogVisible.value = true
}

function addProductRow() {
  productList.value.push({ productId: undefined, unitPrice: undefined, remark: '' })
}

function removeProductRow(index: number) {
  productList.value.splice(index, 1)
}

async function saveProducts() {
  if (!productSupplierId.value) return
  await saveSupplierProducts(productSupplierId.value, productList.value)
  ElMessage.success('供应产品保存成功')
  productDialogVisible.value = false
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
</script>

<template>
  <div class="supplier-page">
    <!-- 查询栏 -->
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="供应商名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="query.phone" placeholder="手机号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item v-if="currentType!=='factory'" label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:100px">
            <el-option label="合作中" :value="1" />
            <el-option label="已停用" :value="0" />
          </el-select>
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
      <!-- 委外加工厂：Tab 切换 -->
      <el-tabs v-if="currentType==='factory'" v-model="activeTab" @tab-change="onFactoryTabChange">
        <el-tab-pane label="合作中" name="active" />
        <el-tab-pane label="已停用" name="disabled" />
      </el-tabs>

      <el-table v-loading="tableLoading" :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="code" label="编码" min-width="130" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status===1?'success':'info'">{{ row.status===1?'合作中':'已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row as SupplierVO)">详细</el-button>
            <el-button type="warning" link @click="openProducts(row as SupplierVO)">产品</el-button>
            <el-button type="success" link @click="handleToggleStatus(row as SupplierVO)">{{ row.status===1?'停用':'启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10,20,50]"
          layout="total,sizes,prev,pager,next"
          background
          @current-change="loadData"
          @size-change="handleQuery"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="编码">
              <el-input v-if="isEdit" v-model="form.code" disabled placeholder="自动生成" />
              <el-input v-else disabled placeholder="保存后自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contact" placeholder="联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width:100%">
                <el-option label="合作中" :value="1" />
                <el-option label="已停用" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="form.address" placeholder="地址" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="账期">
              <div style="display:flex;align-items:center;gap:6px">
                <el-input-number v-model="form.creditPeriodMonths" :min="0" :max="24" placeholder="月" controls-position="right" style="width:90px" /><span>个月</span>
                <el-input-number v-model="form.creditPeriod" :min="0" :max="31" placeholder="天" controls-position="right" style="width:90px" /><span>天</span>
                <span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">（收货/交货后多少天付款，默认当天）</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 供应产品弹窗 -->
    <el-dialog v-model="productDialogVisible" title="供应产品" width="700px">
      <el-button type="primary" size="small" @click="addProductRow" style="margin-bottom:12px">+ 添加产品</el-button>
      <el-table :data="productList" border>
        <el-table-column label="产品" min-width="220">
          <template #default="{ row }">
            <el-select v-model="row.productId" placeholder="搜索选择产品" filterable remote :remote-method="loadProductOptions" style="width:100%" size="small">
              <el-option v-for="p in productOptions" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="110">
          <template #default="{ row }">
            <el-input v-model="row.unitPrice" placeholder="单价" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removeProductRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="productDialogVisible=false">取消</el-button>
        <el-button type="primary" @click="saveProducts">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.supplier-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
