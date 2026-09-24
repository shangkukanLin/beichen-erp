<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCustomerPage,
  updateCustomerStatus,
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

const router = useRouter()

/** 新增 / 编辑统一走独立页面：新增 /inventory/customer/add，详情内直接编辑 /inventory/customer/detail/:id */
function goAdd() { router.push('/inventory/customer/add') }
function goDetail(id?: number) { if (id) router.push(`/inventory/customer/detail/${id}`) }

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
    // 2026-09-24：原「操作成功」语义含糊 ⇒ 按「已X」口径带上具体动作（tip 为 停用/启用）
  ElMessage.success(`已${tip}`)
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

onMounted(() => { loadData() })
// keep-alive 缓存下从详情/新增页返回时刷新列表，保证看到最新数据
onActivated(() => { loadData() })

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="客户编码">
            <el-input v-model="query.code" placeholder="请输入客户编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="客户名称">
            <el-input v-model="query.name" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="请选择状态" clearable style="width: 120px">
              <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="goAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1030px > 内容区 956px ⇒ 横向滚动 74px。
           收窄为合计 882px（编码/名称保持 min-width，宽屏自动吃余量）。 -->
      <el-table v-loading="tableLoading" :data="tableData" border stripe @row-click="(row: any) => goDetail(row.id)">
        <el-table-column prop="code" label="客户编码" min-width="110" show-overflow-tooltip />
        <el-table-column prop="name" label="客户名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="90" />
        <el-table-column prop="phone" label="联系电话" width="110" />
        <el-table-column prop="creditPeriodMonths" label="账期(月)" width="80" align="center" />
        <el-table-column prop="creditPeriod" label="账期(天)" width="80" align="center" />
        <el-table-column prop="creditLimit" label="信用额度" width="110" align="right">
          <template #default="{ row }">{{ fmtMoney(row.creditLimit) }}</template>
        </el-table-column>
        <!-- 2026-09-15 用户要求：删除「应收余额」列（余额在客户详情页仍可查看） -->
        <el-table-column label="状态" width="78" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="104" align="center" fixed="right">
          <template #default="{ row }">
            <!-- 编辑在详情页内完成，列表只保留详情与停启用；行点击已进详情，按钮须阻止冒泡 -->
            <el-button type="primary" link @click.stop="goDetail((row as Customer).id)">详情</el-button>
            <el-button :type="row.status === 1 ? 'warning' : 'success'" link @click.stop="handleToggleStatus(row as Customer)">
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
  </div>
</template>

<style scoped>
/* 根容器/卡片内边距已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body） */

.query-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
</style>
