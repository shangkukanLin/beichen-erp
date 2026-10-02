<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { localDate } from '@/utils/date'
import { DevMaterialTypeLabel, DevMaterialStatusLabel, DEV_MATERIAL_DIRTY_KEY } from '@/api/enums'
import MaterialFormDialog from '@/components/dev/MaterialFormDialog.vue'
import { useDomainRefresh } from '@/utils/dataFreshness'
// 资金账户（支出账户下拉）：属共享主数据（GET 放行），研发支出登记用
import { getAccountPage, type FinanceAccount } from '@/api/finance'

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

// ==================== 列表行操作：为已有研发物料**补登记**研发支出（2026-09-28 用户口径） ====================
// 场景：新增物料时没勾选（或当时不确定要记多少），事后在列表里补登记。
// 与「新增弹窗内勾选」共用同一个后端端点 `POST /api/dev/purchase-item/{id}/rd-expense`，同样只落**草稿**
// （资金在费用管理审核时才动）；同一物料重复登记由后端幂等回原单，消息里给出原单号。
const rdDialog = ref(false)
const rdSubmitting = ref(false)
const rdRow = ref<any>(null)
const rdRowForm = reactive({ amount: undefined as any, accountId: undefined as any, expenseDate: localDate(), remark: '' })
const rdAccounts = ref<FinanceAccount[]>([])
async function loadRdAccounts() {
  try {
    const r: any = await getAccountPage({ pageSize: 200 })
    rdAccounts.value = (r?.records || []).filter((a: any) => a.status === 1)
  } catch { rdAccounts.value = [] }
}
function handleRdExpense(row: any) {
  rdRow.value = row
  // 金额默认带出研发物料金额（可改）；账户每次重置，避免沿用上一次的选择
  Object.assign(rdRowForm, { amount: row?.amount || undefined, accountId: undefined, expenseDate: localDate(), remark: '' })
  loadRdAccounts()
  rdDialog.value = true
}
async function submitRdExpense() {
  if (!rdRowForm.amount || Number(rdRowForm.amount) <= 0) { ElMessage.warning('研发支出金额必须大于 0'); return }
  if (!rdRowForm.accountId) { ElMessage.warning('研发支出必须选择支出账户'); return }
  rdSubmitting.value = true
  try {
    const r: any = await request.post(`/dev/purchase-item/${rdRow.value.id}/rd-expense`, { ...rdRowForm })
    const no = r?.expenseNo ? `（单号 ${r.expenseNo}）` : ''
    ElMessage.success(`${r?.existing ? '该研发物料已登记过研发支出' : '研发支出已存为草稿'}${no}，请在「财务管理 → 费用管理」审核后才扣款`)
    rdDialog.value = false
  } finally { rdSubmitting.value = false }
}

// 详情页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
useDomainRefresh('devMaterial', () => {
    loadList()
}, DEV_MATERIAL_DIRTY_KEY)
onMounted(() => { loadProjectOptions(); loadList() })

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="物料名称"><el-input v-model="query.name" placeholder="模糊搜索" clearable style="width:160px" @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="关联项目">
          <RemoteSelect v-model="query.projectId" add-route="/dev/project/add" :fetch="fetchProjects" placeholder="全部" clearable style="width:160px" domain="devProject" >
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
        <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
      </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe @row-click="handleDetail">
        <el-table-column label="类型" width="120"><template #default="{ row }">{{ DevMaterialTypeLabel[row.type] || row.type }}</template></el-table-column>
        <!-- 2026-09-25 B1：研发物料名称 → /dev/material/detail/:id -->
        <el-table-column label="物料名称" min-width="140">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="router.push(`/dev/material/detail/${row.id}`)">{{ row.name }}</el-button>
          </template>
        </el-table-column>
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
        <!-- 2026-09-28（用户口径「研发支出属研发物料」）：操作列加「研发支出」（140→190）——
             给"新增时没勾选、事后补登记"的场景（落草稿，财务在费用管理审核后才扣款） -->
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleDetail(row)">详情</el-button>
            <el-button link type="primary" @click.stop="handleRdExpense(row)">研发支出</el-button>
            <el-button link type="danger" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSizeChange" @current-change="handlePageChange" />
      </div>
    </el-card>

    <MaterialFormDialog ref="materialDialog" @saved="loadList" />

    <!-- 列表行操作：为已有研发物料补登记研发支出（2026-09-28 用户口径）。落草稿、审核才扣款；
         重复登记由后端按来源幂等回原单。 -->
    <el-dialog v-model="rdDialog" title="登记研发支出" width="var(--app-dialog-sm)" :close-on-click-modal="false">
      <el-form :model="rdRowForm" label-width="90px">
        <el-form-item label="研发物料"><span style="font-weight:600">{{ rdRow?.name }}</span></el-form-item>
        <el-form-item required label="支出金额"><el-input-number v-model="rdRowForm.amount" :precision="2" :min="0.01" controls-position="right" style="width:100%" placeholder="默认取物料金额" /></el-form-item>
        <el-form-item required label="支出账户">
          <el-select v-model="rdRowForm.accountId" placeholder="请选择" style="width:100%">
            <el-option v-for="a in rdAccounts" :key="a.id" :label="`${a.accountName}（余额 ${Number((a as any).balance ?? 0).toFixed(2)}）`" :value="a.id ?? ''" />
          
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/finance/account')">+ 新增</div></template>
              </el-select>
        </el-form-item>
        <el-form-item label="费用日期"><el-date-picker v-model="rdRowForm.expenseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="费用备注"><el-input v-model="rdRowForm.remark" placeholder="留空自动填「研发支出：物料名」" /></el-form-item>
      </el-form>
      <div style="color:var(--app-text-secondary);font-size:var(--app-font-xs);line-height:1.5">保存为草稿费用单，需在「财务管理 → 费用管理」审核后才扣款；同一研发物料只会保留一张研发支出。</div>
      <template #footer><el-button @click="rdDialog=false">取消</el-button><el-button type="primary" :loading="rdSubmitting" @click="submitRdExpense">确定</el-button></template>
    </el-dialog>
  </div>
</template>
