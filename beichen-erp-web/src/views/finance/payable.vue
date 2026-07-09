<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import request from '@/utils/request'
import { getPayablePage, type FinancePayable } from '@/api/finance'

const query = reactive({ supplierId: '' as string|number, status: '', billNo: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinancePayable[]>([])
const suppliers = ref<{id:number;name:string}[]>([])
const detailVisible = ref(false)
const detail = ref<FinancePayable>({})

async function load() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.supplierId) p.supplierId = query.supplierId
    if (query.status) p.status = query.status
    if (query.billNo) p.billNo = query.billNo
    const res = await getPayablePage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
onMounted(async () => { try { const r = await request.get('/supplier/page',{params:{pageSize:200}}); suppliers.value = r?.records || [] } catch {}; load() })
function query_() { page.pageNum = 1; load() }
function reset_() { query.supplierId = ''; query.status = ''; query.billNo = ''; page.pageNum = 1; load() }
function sName(id?: number) { return suppliers.value.find(x => x.id === id)?.name || '' }
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function stType(s?: string) { if (s === '未结清') return 'danger'; if (s === '部分结清') return 'warning'; if (s === '已结清') return 'success'; return '' }
</script>
<template>
  <div class="p">
    <el-card shadow="never"><el-form :inline="true" :model="query" class="qf">
      <el-form-item label="供应商"><el-select v-model="query.supplierId" placeholder="全部" clearable filterable style="width:160px"><el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:120px"><el-option v-for="s in [{l:'未结清',v:'未结清'},{l:'部分结清',v:'部分结清'},{l:'已结清',v:'已结清'}]" :key="s.v" :label="s.l" :value="s.v"/></el-select></el-form-item>
      <el-form-item label="单号"><el-input v-model="query.billNo" placeholder="单据号" clearable @keyup.enter="query_"/></el-form-item>
      <el-form-item><el-button type="primary" @click="query_">查询</el-button><el-button @click="reset_">重置</el-button></el-form-item>
    </el-form></el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column type="index" width="55" align="center"/>
        <el-table-column prop="billNo" label="单据号" min-width="150"/>
        <el-table-column label="供应商" min-width="140"><template #default="{row}">{{ sName(row.supplierId) }}</template></el-table-column>
        <el-table-column prop="sourceBillType" label="来源" width="100"/>
        <el-table-column prop="amount" label="应付金额" width="120" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <el-table-column prop="paidAmount" label="已付" width="120" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" label="未付" width="120" align="right"><template #default="{row}"><span style="color:#f56c6c">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="120" align="center"/>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="stType(row.status)">{{row.status}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="80" align="center"><template #default="{row}"><el-button type="primary" link @click="detail=row;detailVisible=true">详情</el-button></template></el-table-column>
      </el-table>
      <div class="pg"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="load" @current-change="load"/></div>
    </el-card>
    <el-drawer v-model="detailVisible" title="应付详情" size="50%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单据号">{{ detail.billNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ sName(detail.supplierId) }}</el-descriptions-item>
        <el-descriptions-item label="来源类型">{{ detail.sourceBillType }}</el-descriptions-item>
        <el-descriptions-item label="来源单号">{{ detail.sourceBillNo }}</el-descriptions-item>
        <el-descriptions-item label="到期日">{{ detail.dueDate }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">{{ fmt(detail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="已付金额">{{ fmt(detail.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="未付金额"><span style="color:#f56c6c">{{ fmt(detail.unpaidAmount) }}</span></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
