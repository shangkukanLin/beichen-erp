<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { IoType, IoTypeLabel, WarehouseCategory, INVENTORY_OTHER_IO_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { useDomainRefresh } from '@/utils/dataFreshness'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ warehouseId: '', ioType: '' })
// 仓库下拉选项（Odoo 实时查库，组件本地保存）
const warehouseOptions = ref<any[]>([])
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })

/**
 * 仓库名称映射：列表只返回 warehouseId，需要一次性拉仓库列表做本地映射。
 * 这里不按 warehouseCategory 过滤——下拉筛选仍只给自有仓，但名称映射要能覆盖到任意仓，避免列显示成 ID。
 */
async function loadWarehouses() {
  try {
    const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } })
    warehouseOptions.value = r?.records || []
  } catch { warehouseOptions.value = [] }
}

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.warehouseId) p.warehouseId = query.warehouseId
    if (query.ioType) p.ioType = query.ioType
    const r = await request.get<any,any>('/inventory/other/page', { params: p })
    list.value = r?.records||[]; pagination.total = r?.total||0
  } finally { loading.value = false }
}
function handleAdd() { router.push('/inventory/other-io/add') }
// 编辑入口收拢到详情页（草稿态才有），列表只保留查看详情
function handleDetail(row: any) { router.push(`/inventory/other-io/detail/${row.id}`) }
async function handleAudit(row: any) {
  try { await ElMessageBox.confirm('确认审核？审核后按明细增减库存', '审核确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/inventory/other/${row.id}/audit`); ElMessage.success('已审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}
async function handleUnAudit(row: any) {
  try { await ElMessageBox.confirm('确认反审核？将逆向增减库存并回到草稿', '反审核确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/inventory/other/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}
async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废？作废后不可恢复', '作废确认', { type: 'warning' }) } catch { return }
  try { await request.put(`/inventory/other/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}
function handleQuery() { pagination.pageNum=1; loadData() }

function getWhName(id: number) { return warehouseOptions.value.find((w:any)=>w.id===id)?.warehouseName || '-' }
// 新增/编辑页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
useDomainRefresh('inventoryOtherIo', () => {
    loadData()
}, INVENTORY_OTHER_IO_DIRTY_KEY)
onMounted(async ()=>{ await loadWarehouses(); loadData() })

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="仓库"><RemoteSelect v-model="query.warehouseId" add-route="/inventory/warehouse" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" clearable style="width:180px" placeholder="全部" domain="warehouse" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="query.ioType" clearable style="width:120px"><el-option :label="IoTypeLabel[IoType.IN]" :value="IoType.IN"/><el-option :label="IoTypeLabel[IoType.OUT]" :value="IoType.OUT"/></el-select></el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>
    <el-card shadow="never">
      <el-table :data="list" border stripe v-loading="loading" @row-click="handleDetail">
        <el-table-column label="日期" width="110"><template #default="{row}">{{ $fmtDate(row.ioDate) }}</template></el-table-column>
        <!-- 2026-09-26 B5a：单号做成链接进详情（原先只能点整行/操作列） -->
        <el-table-column label="单号" width="160" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="handleDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column label="仓库" width="140">
          <template #default="{row}">
            <el-button v-if="row.warehouseId" type="primary" link @click.stop="router.push(`/inventory/warehouse/detail/${row.warehouseId}`)">
              {{ getWhName(row.warehouseId) }}
            </el-button>
            <span v-else>{{ getWhName(row.warehouseId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80"><template #default="{row}"><el-tag :type="row.ioType===IoType.IN?'success':'danger'" size="small">{{ IoTypeLabel[row.ioType] || row.ioType }}</el-tag></template></el-table-column>
        <el-table-column label="其他出入库概况" min-width="200" show-overflow-tooltip>
          <template #default="{row}">{{ row.itemSummary || '-' }}</template>
        </el-table-column>
        <!-- 2026-09-24（用户口径）：反审核移入详情页 ⇒ 操作列 220→132（详情/审核/作废 3 个按钮）。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button>
            <el-button v-perm="'stock:other-io'" type="success" link @click.stop="handleAudit(row)" v-if="row.status===DocStatus.DRAFT">审核</el-button>
            <el-button v-perm="'stock:other-io'" type="danger" link @click.stop="handleCancel(row)" v-if="row.status===DocStatus.DRAFT">作废</el-button>
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
  </div>
</template>
