<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ warehouseId: '', ioType: '' })
const warehouses = ref<any[]>([])

async function loadWarehouses() {
  try { const r = await request.get<any,any>('/outsource/warehouse/page',{params:{pageSize:200}}); warehouses.value = r?.records||[] } catch {}
}
async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.warehouseId) p.warehouseId = query.warehouseId
    if (query.ioType) p.ioType = query.ioType
    const r = await request.get<any,any>('/outsource/other-io/page', { params: p })
    list.value = r?.records||[]; pagination.total = r?.total||0
  } finally { loading.value = false }
}
function handleAdd() { router.push('/outsource/other-io/add') }
function handleEdit(row: any) { router.push(`/outsource/other-io/add?id=${row.id}`) }
async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认取消？取消后将逆向库存', '确认',{type:'warning'}) } catch { return }
  try { await request.put(`/outsource/other-io/${row.id}/cancel`); ElMessage.success('已取消'); loadData() } catch (e: any) { ElMessage.error(e?.message||'失败') }
}
function handleQuery() { pagination.pageNum=1; loadData() }
function getWhName(id: number) { return warehouses.value.find((w:any)=>w.id===id)?.warehouseName || id }
onMounted(()=>{ loadWarehouses(); loadData() })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="仓库"><el-select v-model="query.warehouseId" clearable filterable style="width:200px" placeholder="全部"><el-option v-for="w in warehouses" :key="w.id" :label="`${w.warehouseName}（${w.factoryName||''}）`" :value="w.id"/></el-select></el-form-item>
        <el-form-item label="类型"><el-select v-model="query.ioType" clearable style="width:120px"><el-option label="入库" value="入库"/><el-option label="出库" value="出库"/></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button type="success" @click="handleAdd">新增</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="code" label="单号" width="160"/>
        <el-table-column label="仓库" width="180"><template #default="{row}">{{ getWhName(row.warehouseId) }}</template></el-table-column>
        <el-table-column label="类型" width="80"><template #default="{row}"><el-tag :type="row.ioType==='入库'?'success':'danger'" size="small">{{ row.ioType }}</el-tag></template></el-table-column>
        <el-table-column label="日期" width="110"><template #default="{row}">{{ $fmtDate(row.ioDate) }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip/>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{row}"><el-button type="primary" link @click="handleEdit(row)" :disabled="row.status==='已取消'">编辑</el-button><el-button type="danger" link @click="handleCancel(row)" :disabled="row.status==='已取消'">取消</el-button></template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery"/></div>
    </el-card>
  </div>
</template>
