<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { IoType, IoTypeLabel } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ warehouseId: '' })
const activeTab = ref(IoType.IN)
const warehouses = ref<any[]>([])

// Odoo 风格：仓库实时查库
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })
async function loadWarehouses() {
  try { const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500 } }); warehouses.value = r?.records || [] } catch { warehouses.value = [] }
}

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.warehouseId) p.warehouseId = query.warehouseId
    p.ioType = activeTab.value
    const r = await request.get<any,any>('/outsource/other-io/page', { params: p })
    list.value = r?.records||[]; pagination.total = r?.total||0
  } finally { loading.value = false }
}
function handleAdd() { router.push('/outsource/other-io/add') }
function handleEdit(row: any) {
  // 草稿跳编辑页，已审核/已取消跳详情页
  if (row.status === DocStatus.DRAFT) router.push(`/outsource/other-io/edit/${row.id}`)
  else router.push(`/outsource/other-io/detail/${row.id}`)
}
async function handleApprove(row: any) {
  try { await ElMessageBox.confirm('确认审核？审核后库存生效', '确认',{type:'warning'}) } catch { return }
  try { await request.put(`/outsource/other-io/${row.id}/approve`); ElMessage.success('已审核'); loadData() } catch (e: any) { ElMessage.error(e?.message||'失败') }
}
async function handleUnapprove(row: any) {
  try { await ElMessageBox.confirm('确认反审核？将回滚库存', '确认',{type:'warning'}) } catch { return }
  try { await request.put(`/outsource/other-io/${row.id}/unapprove`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message||'失败') }
}
async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废？', '作废确认',{type:'warning'}) } catch { return }
  try { await request.put(`/outsource/other-io/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message||'失败') }
}
function handleTabChange() { pagination.pageNum=1; loadData() }
function handleQuery() { pagination.pageNum=1; loadData() }
function getWhName(id: number) { return warehouses.value.find((w:any)=>w.id===id)?.warehouseName || id }
function goWarehouseDetail(warehousId: number) {
  const wh = warehouses.value.find((w:any)=>w.id===warehousId)
  // 有 factoryId 的是委外仓，否则是我方仓
  if (wh?.factoryId != null) router.push(`/outsource/warehouse/detail/${warehousId}`)
  else router.push(`/inventory/warehouse/detail/${warehousId}`)
}
onMounted(()=>{ loadWarehouses(); loadData() })

</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="仓库"><RemoteSelect v-model="query.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>`${row.warehouseName}（${row.factoryName||''}）`" clearable style="width:200px" placeholder="全部" /></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button type="success" @click="handleAdd">新增</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane :label="IoTypeLabel[IoType.IN]" :name="IoType.IN"/>
      <el-tab-pane :label="IoTypeLabel[IoType.OUT]" :name="IoType.OUT"/>
    </el-tabs>
    <el-card shadow="never" style="margin-top:-12px">
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="code" label="单号" width="160"/>
        <el-table-column label="仓库" width="180"><template #default="{row}"><el-button type="primary" link @click="goWarehouseDetail(row.warehouseId)">{{ getWhName(row.warehouseId) }}</el-button></template></el-table-column>
        <el-table-column label="日期" width="110"><template #default="{row}">{{ $fmtDate(row.ioDate) }}</template></el-table-column>
        <el-table-column label="物料明细" min-width="160" show-overflow-tooltip>
          <template #default="{row}"><span v-if="row.itemSummary">{{row.itemSummary}}</span><span v-else style="color:var(--app-text-placeholder)">-</span></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip/>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{row}">
            <el-tag v-if="row.status===DocStatus.DRAFT" :type="DocStatusTag[row.status]" size="small">{{ DocStatusLabel[row.status] }}</el-tag>
            <el-tag v-else-if="row.status===DocStatus.AUDITED" :type="DocStatusTag[row.status]" size="small">{{ DocStatusLabel[row.status] }}</el-tag>
            <el-tag v-else-if="row.status===DocStatus.CANCELLED" :type="DocStatusTag[row.status]" size="small">{{ DocStatusLabel[row.status] }}</el-tag>
            <el-tag v-else type="warning" size="small">{{row.status}}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{row}">
            <el-button v-if="row.status===DocStatus.DRAFT" type="success" link @click="handleApprove(row)">审核</el-button>
            <el-button v-if="row.status===DocStatus.AUDITED" type="warning" link @click="handleUnapprove(row)">反审核</el-button>
            <el-button type="primary" link @click="handleEdit(row)">{{ row.status===DocStatus.DRAFT ? '编辑' : '详细' }}</el-button>
            <el-button type="danger" link @click="handleCancel(row)" :disabled="row.status===DocStatus.CANCELLED || row.status===DocStatus.AUDITED">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery"/></div>
    </el-card>
  </div>
</template>
