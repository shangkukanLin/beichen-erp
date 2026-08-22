<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { IoType, IoTypeLabel, WarehouseCategory } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ warehouseId: '', ioType: '' })
// 仓库下拉选项（Odoo 实时查库，组件本地保存）
const warehouseOptions = ref<any[]>([])
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })

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
function handleEdit(row: any) { router.push(`/inventory/other-io/add?id=${row.id}`) }
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

function getWhName(id: number) { return warehouseOptions.value.find((w:any)=>w.id===id)?.warehouseName || id }
onMounted(()=>{ loadData() })

</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="仓库"><RemoteSelect v-model="query.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" clearable style="width:180px" placeholder="全部" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="query.ioType" clearable style="width:120px"><el-option :label="IoTypeLabel[IoType.IN]" :value="IoType.IN"/><el-option :label="IoTypeLabel[IoType.OUT]" :value="IoType.OUT"/></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button type="success" @click="handleAdd">新增</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="code" label="单号" width="160"/>
        <el-table-column label="仓库" width="140"><template #default="{row}">{{ getWhName(row.warehouseId) }}</template></el-table-column>
        <el-table-column label="类型" width="80"><template #default="{row}"><el-tag :type="row.ioType===IoType.IN?'success':'danger'" size="small">{{ IoTypeLabel[row.ioType] || row.ioType }}</el-tag></template></el-table-column>
        <el-table-column label="日期" width="110"><template #default="{row}">{{ $fmtDate(row.ioDate) }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip/>
        <el-table-column label="操作" width="260" align="center">
          <template #default="{row}">
            <el-button type="primary" link @click="handleEdit(row)" v-if="row.status===DocStatus.DRAFT">编辑</el-button>
            <el-button type="success" link @click="handleAudit(row)" v-if="row.status===DocStatus.DRAFT">审核</el-button>
            <el-button type="warning" link @click="handleUnAudit(row)" v-if="row.status===DocStatus.AUDITED">反审核</el-button>
            <el-button type="danger" link @click="handleCancel(row)" v-if="row.status===DocStatus.DRAFT">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery"/></div>
    </el-card>
  </div>
</template>
