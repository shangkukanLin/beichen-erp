<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProductPage,
  deleteProduct,
  ProductStatus,
  ProductStatusLabel,
  ProductStatusTag,
  type Product,
  type ProductQueryParams
} from '@/api/product'
import request from '@/utils/request'

const router = useRouter()

// 品牌下拉
const brandOptions = ref<{ id: number; brandName: string }[]>([])
async function loadBrands() {
  try { const res = await request.get<any, any>('/brand/enabled'); brandOptions.value = res || []   } catch (e: any) { ElMessage.error('品牌加载失败：' + (e?.msg || e?.message || '未知错误')) }
}

// 查询参数
const query = reactive<ProductQueryParams>({
  keyword: '',
  brandId: undefined
})

// Tab 切换
const activeTab = ref(ProductStatus.NORMAL)

// 分页
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableLoading = ref(false)
const tableData = ref<Product[]>([])

// 2026-09-20（F7-155）：删除 categoryOptions / statusOptions —— 定义后**模板从未使用**
// （状态切换实际由 3 个 el-tab-pane 硬写）。其中 categoryOptions 是 4 个**中文分类字面量**，
// 一旦将来被启用就是"改名静默失效"的隐患（F7-132 同族），删掉比留着更安全。

async function loadData() {
  tableLoading.value = true
  try {
    const params: ProductQueryParams = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (query.keyword) params.keyword = query.keyword
    if (query.brandId) params.brandId = query.brandId
    if (activeTab.value) params.status = activeTab.value

    const res = await getProductPage(params)
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
  query.keyword = ''
  query.brandId = undefined
  pagination.pageNum = 1
  loadData()
}

/** 新增/编辑统一走独立页面（/product/add、/product/detail/:id），列表不再弹框 */
function handleAdd() { router.push('/product/add') }
function handleEdit(row: any) { router.push(`/product/detail/${row.id}`) }

function getBrandName(brandId: number | string | undefined) {
  if (brandId == null) return ''
  const b = brandOptions.value.find(o => o.id === Number(brandId))
  return b ? b.brandName : ''
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要停用物料「${row.name}」吗？停用后仍可在"停售"标签页查看`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteProduct(row.id as number | string)
    ElMessage.success('已停用')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.message || '停用失败')
    }
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

function isLowStock(row: any): boolean {
  const safety = Number(row.safetyStock) || 0
  const current = Number(row.currentStock) || 0
  return safety > 0 && current < safety
}

function rowClass({ row }: any) {
  return isLowStock(row) ? 'low-stock-row' : ''
}

function statusText(status: string) {
  return ProductStatusLabel[status] || status || ProductStatusLabel.NORMAL
}

function statusType(status: string) {
  return ProductStatusTag[status] || 'warning'
}

onMounted(() => {
  loadBrands()
  loadData()
})

</script>

<template>
  <div class="material-page">
    <!-- 查询栏 -->
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="名称/SKU">
          <el-input v-model="query.keyword" placeholder="产品名称或 SKU" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="品牌">
          <el-select v-model="query.brandId" placeholder="全部品牌" clearable style="width:160px" @change="handleQuery">
            <el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" />
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
      <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
        <el-tab-pane :label="ProductStatusLabel.NORMAL" :name="ProductStatus.NORMAL" />
        <el-tab-pane :label="ProductStatusLabel.DISCONTINUED" :name="ProductStatus.DISCONTINUED" />
        <el-tab-pane :label="ProductStatusLabel.DEVELOPING" :name="ProductStatus.DEVELOPING" />
      </el-tabs>

      <el-table v-loading="tableLoading" :data="tableData" border stripe :row-class-name="rowClass">
        <!-- 2026-09-14 按用户要求移除「序号」列 -->
        <el-table-column prop="sku" label="SKU" width="140" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="品牌" min-width="120">
          <template #default="{ row }">{{ getBrandName(row.brandId) }}</template>
        </el-table-column>
        <el-table-column prop="generalModel" label="通用型号" width="120" />
        <el-table-column prop="safetyStock" label="安全库存" width="100" align="right" />
        <!-- 2026-09-15 用户要求：删除「成本价」列（含「手工」标记）与「最近进价」列 —— 成本属内部信息，不在产品列表展示 -->
        <el-table-column label="当前库存" width="130" align="right">
          <template #default="{ row }">
            <span :style="{ color: isLowStock(row) ? 'var(--app-color-danger)' : '', fontWeight: isLowStock(row) ? 'bold' : '' }">
              {{ row.currentStock ?? 0 }}
            </span>
            <el-tag v-if="isLowStock(row)" type="danger" size="small" style="margin-left:4px">预警</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <!-- 详情页即编辑页，列表只需一个「编辑」入口 -->
            <el-button type="primary" link @click="handleEdit(row as Product)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row as Product)">删除</el-button>
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
