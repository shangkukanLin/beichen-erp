<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, computed, watch } from 'vue'
import { SUPPLIER_DIRTY_KEY } from '@/api/enums'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { productLabel } from '@/api/product'
import { useDomainRefresh } from '@/utils/dataFreshness'
// 2026-09-24：新增/编辑弹框在 2026-09-23 已改为独立页（/supplier/form/add | /edit/:id）⇒ 清掉随之
// 失效的弹框状态与提交逻辑（dialogVisible / dialogTitle / submitLoading / formRef / defaultForm /
// form / isEdit / rules / handleSubmit）以及只被它们用到的导入（addSupplier / updateSupplier /
// SupplierDTO / FormInstance / FormRules / SupplierProductVO）。
// ⚠️ F7-153（编辑时不得用当前页签类型覆盖 typeCodes）的保护与解释都在 form.vue:67-69，未丢失。
import {
  getSupplierPage, toggleSupplierStatus,
  getSupplierProducts, saveSupplierProducts,
  type SupplierVO, type SupplierProductDTO
} from '@/api/system'

const route = useRoute()
const router = useRouter()

// ---------- 根据路由确定供应商类型 ----------
const typeMap: Record<string, { title: string; type: string }> = {
  '/supplier/solution': { title: '方案商', type: 'solution' },
  '/supplier/factory': { title: '委外加工厂', type: 'factory' },
  '/supplier/product': { title: '成品供应商', type: 'product' },
  '/supplier/material-supplier': { title: '辅料商', type: 'material' }
}
const currentType = computed(() => typeMap[route.path]?.type || 'solution')
// 2026-09-24：原 pageTitle 已无任何引用（页面标题由路由 meta.title 渲染）⇒ 删除

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

// ---------- 新增 / 详情（都是跳独立页，本页不再有表单弹框）----------
function handleAdd() {
  // 2026-09-23 用户要求：新增由 700px 弹框改为独立页（类型随 query 带过去，刷新/直链都不丢）
  router.push({ path: '/supplier/form/add', query: { type: currentType.value } })
}

function handleDetail(row: SupplierVO) {
  router.push(`/supplier/detail/${row.id}`)
}

async function handleToggleStatus(row: SupplierVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '停用'
  // 2026-09-20（F7-156）：原先点一下就改状态、无任何确认 ⇒ 补 confirm（与 customer/index.vue 同口径）。
  // 停用会影响采购/委外选单，误点代价高。
  try {
    await ElMessageBox.confirm(`确定要${action}「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await toggleSupplierStatus(row.id!)
    ElMessage.success(next === 1 ? '已启用' : '已停用')
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || `${action}失败`) }
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
  ElMessage.success('供应产品已保存')
  productDialogVisible.value = false
}

// 详情页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
useDomainRefresh('supplier', () => {
    loadData()
}, SUPPLIER_DIRTY_KEY)
onMounted(() => { loadData() })

</script>

<template>
  <div class="page-list">
    <!-- 查询栏 -->
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="供应商名称">
          <el-input v-model="query.name" placeholder="供应商名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="query.phone" placeholder="手机号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item v-if="currentType!=='factory'" label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:100px">
            <el-option label="合作中" :value="1" />
            <el-option label="已停用" :value="0" />
          </el-select>
        </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <!-- 委外加工厂：Tab 切换 -->
      <el-tabs v-if="currentType==='factory'" v-model="activeTab" @tab-change="onFactoryTabChange">
        <el-tab-pane label="合作中" name="active" />
        <el-tab-pane label="已停用" name="disabled" />
      </el-tabs>

      <el-table v-loading="tableLoading" :data="tableData" border stripe @row-click="(row: any) => handleDetail(row)">
        <el-table-column prop="code" label="供应商编码" min-width="130" show-overflow-tooltip />
        <el-table-column prop="name" label="供应商名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="联系电话" width="120" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status===1?'success':'info'">{{ row.status===1?'合作中':'已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleDetail(row as SupplierVO)">详情</el-button>
            <el-button type="warning" link @click.stop="openProducts(row as SupplierVO)">供应产品</el-button>
            <el-button type="success" link @click.stop="handleToggleStatus(row as SupplierVO)">{{ row.status===1?'停用':'启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleQuery" @current-change="loadData" />
      </div>
    </el-card>


    <!-- 供应产品弹窗 -->
    <el-dialog v-model="productDialogVisible" title="供应产品" width="var(--app-dialog-md)">
      <el-button type="primary" size="small" @click="addProductRow" style="margin-bottom:12px">+ 添加产品</el-button>
      <el-table :data="productList" border>
        <el-table-column label="产品" min-width="220">
          <template #default="{ row }">
            <el-select v-model="row.productId" placeholder="搜索选择产品（可输SKU）" filterable remote :remote-method="loadProductOptions" style="width:100%" size="small">
              <el-option v-for="p in productOptions" :key="p.id" :label="productLabel(p)" :value="p.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="110">
          <template #default="{ row }">
            <el-input v-model="row.unitPrice" placeholder="单价" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center" fixed="right">
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
/* 根容器/卡片内边距已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body） */
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
</style>
