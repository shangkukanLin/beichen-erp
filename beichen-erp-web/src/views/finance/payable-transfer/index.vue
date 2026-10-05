<script setup lang="ts">
import { reactive, ref, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, SettlementStatusLabel, sourceBillTypeLabel } from '@/api/enums'
import { TYPE_MAP, TYPE_OPTIONS, TYPE_TAG } from '@/constants/supplier'
import { PAYABLE_TRANSFER_DIRTY_KEY } from '@/api/enums'
import { useDomainRefresh } from '@/utils/dataFreshness'

const router = useRouter()
const query = reactive({
  code: '',
  supplierId: undefined as number | undefined,
  supplierType: '',
  status: '',
  dateRange: null as [string, string] | null
})
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const rows = ref<any[]>([])

const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }

async function load() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.code) p.code = query.code
    if (query.supplierId) p.supplierId = query.supplierId
    if (query.supplierType) p.supplierType = query.supplierType
    if (query.status) p.status = query.status
    if (query.dateRange?.length === 2) { p.startDate = query.dateRange[0]; p.endDate = query.dateRange[1] }
    const res = await request.get<any, any>('/finance/payable-transfer/page', { params: p })
    rows.value = res?.records || []
    page.total = res?.total || 0
  } catch { rows.value = []; page.total = 0 } finally { loading.value = false }
}
function doQuery() { page.pageNum = 1; load() }
function resetQuery() {
  query.code = ''; query.supplierId = undefined; query.supplierType = ''
  query.status = ''; query.dateRange = null; page.pageNum = 1; load()
}
function onSizeChange(v: number) { page.pageSize = v; page.pageNum = 1; load() }

function goAdd() { router.push('/finance/payable-transfer/add') }
function goDetail(row: any) { router.push(`/finance/payable-transfer/detail/${row.id}`) }
/* 2026-09-24（用户口径）：goEdit 已移除 —— 草稿态编辑统一在详情页内联完成，列表不再提供编辑入口。 */

async function onAudit(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认审核转应收单「${row.code}」？审核后将向【${TYPE_MAP[row.supplierType] || '对方'} ${row.supplierName}】生成 ${fmt(row.amount)} 元应收，原应付记录不再参与付款抵扣。`,
      '审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/finance/payable-transfer/${row.id}/audit`)
    ElMessage.success('已审核，已生成应收')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function onUnAudit(row: any) {
  try {
    await ElMessageBox.confirm(`确认反审核「${row.code}」？将冲销已生成的应收，并恢复原应付记录的抵扣资格。`, '反审核确认', { type: 'warning' })
  } catch { return }
  try {
    await request.put(`/finance/payable-transfer/${row.id}/un-audit`)
    ElMessage.success('已反审核')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function onCancel(row: any) {
  try { await ElMessageBox.confirm(`确认作废转应收单「${row.code}」？`, '作废确认', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/finance/payable-transfer/${row.id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

// 2026-10-05 F7-287 修复：原 `onMounted(load)` 与下面的 useDomainRefresh **加载的是同一个函数**
//   ⇒ 首次进入重复请求一遍。useDomainRefresh 自身在 onMounted 调一次 loader，故删掉独立 onMounted。
// 从新增/详情页返回时按需刷新（保留查询与分页现场）
useDomainRefresh('payableTransfer', () => {
    load()
}, PAYABLE_TRANSFER_DIRTY_KEY)
</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="单号">
            <el-input v-model="query.code" placeholder="转应收单号" clearable style="width:160px" @keyup.enter="doQuery" />
          </el-form-item>
          <el-form-item label="往来单位">
            <RemoteSelect v-model="query.supplierId" add-route="/supplier/manage/add" :fetch="fetchSuppliers" placeholder="全部" style="width:160px" domain="supplier" />
          </el-form-item>
          <el-form-item label="主体类型">
            <el-select v-model="query.supplierType" placeholder="全部" clearable style="width:120px">
              <el-option v-for="o in TYPE_OPTIONS" :key="o.name" :label="o.label" :value="o.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
              <el-option v-for="(label, code) in DocStatusLabel" :key="code" :label="label" :value="code" />
            </el-select>
          </el-form-item>
          <el-form-item label="转出日期">
            <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" style="width:240px" />
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="doQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="resetQuery">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="goAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1190px > 内容区 956px
           ⇒ 横向滚动 234px。收窄为合计 928px。
           2026-09-26 B6（用户口径「数据显示完整 + 单号/往来单位可点」）：
           ①转应收单号 min110→**152**（TR-+11 位）并做成链接进转应收单详情；
           ②往来单位做成链接进供应商详情（行内有 supplierId 才可点，缺失时保持纯文本）；
           合计 = 152+110+110+76+96+92+76+84+132 = **928** ✓ -->
      <el-table v-loading="loading" :data="rows" border stripe @row-click="goDetail">
        <el-table-column label="转应收单号" min-width="152" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column label="来源应付单号" min-width="137" show-overflow-tooltip>
          <template #default="{ row }">{{ row.payableBillNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="往来单位" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.supplierId" type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName || '—' }}</el-button>
            <span v-else>{{ row.supplierName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="主体类型" width="76" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.supplierType" :type="TYPE_TAG[row.supplierType] || 'info'" size="small">{{ typeLabel(row.supplierType) }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="转出金额" width="96" align="right" show-overflow-tooltip>
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="transferDate" label="转出日期" width="92" align="center" />
        <el-table-column label="状态" width="76" align="center">
          <template #default="{ row }">
            <el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核人" width="84" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditorName || '—' }}</template>
        </el-table-column>
        <!-- 2026-09-24（用户口径）：草稿态在**详情页**改+存 ⇒ 列表去掉「编辑」入口；操作列 174→132（剩 3 个按钮）。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row)">详情</el-button>
            <!-- 2026-09-29 审核批 B · F7-220：危险动作按权限显示（后端前缀 /api/finance/payable-transfer ⇒ finance:payable-transfer） -->
            <el-button v-if="row.status === DocStatus.DRAFT" link type="success" v-perm="'finance:payable-transfer'" @click.stop="onAudit(row)">审核</el-button>
            <el-button v-if="row.status === DocStatus.DRAFT" link type="danger" v-perm="'finance:payable-transfer'" @click.stop="onCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="page.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="onSizeChange" @current-change="load" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
/* 根容器/卡片内边距已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body） */
.query-form { align-items: center; }
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
:deep(.el-table__row) { cursor: pointer; }
</style>
