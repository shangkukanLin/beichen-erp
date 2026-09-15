<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { SettlementStatus, SettlementStatusLabel, sourceBillTypeLabel, SourceBillTypeLabel, codeLabelOptions } from '@/api/enums'
import { getPayablePage, type FinancePayable } from '@/api/finance'
import { TYPE_MAP, TYPE_TABS, TYPE_TAG } from '@/constants/supplier'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
/** 来源单据类型下拉（取后端枚举，避免前端硬编码中文） */
const sourceBillTypeOptions = ref<{ code: string; label: string }[]>([])
const query = reactive({ supplierId: '' as string|number, sourceBillType: '', status: '', billNo: '' })
/** 页签：按主体类型查看应付（与供应商管理页一致），all=全部 */
const activeType = ref('all')
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinancePayable[]>([])
const suppliersOptions = ref<any[]>([])
const detailVisible = ref(false)
const detail = ref<FinancePayable>({})

const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadSuppliersOptions() {
  try { const r: any = await fetchSuppliers(''); suppliersOptions.value = r?.records || [] } catch { suppliersOptions.value = [] }
}

async function load() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.supplierId) p.supplierId = query.supplierId
    if (activeType.value !== 'all') p.supplierType = activeType.value
    if (query.sourceBillType) p.sourceBillType = query.sourceBillType
    if (query.status) p.status = query.status
    if (query.billNo) p.billNo = query.billNo
    const res = await getPayablePage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}
// 切页签：回到第一页重新查询
watch(activeType, () => { page.pageNum = 1; load() })

/** 来源单据类型选项（2026-09-14：改由前端枚举映射生成；后端接口已只回 code） */
function loadSourceBillTypes() { sourceBillTypeOptions.value = codeLabelOptions(SourceBillTypeLabel) }
onMounted(() => { loadSuppliersOptions(); loadSourceBillTypes(); load() })

function query_() { page.pageNum = 1; load() }
function reset_() {
  query.supplierId = ''; query.sourceBillType = ''
  query.status = ''; query.billNo = ''; page.pageNum = 1; load()
}
function sName(id?: number) { return suppliersOptions.value.find(x => x.id === id)?.name || '' }
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

/** 主体类型 code -> 中文（供货商/加工厂/辅料商/方案商） */
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }
/** 负数为退货/扣款类冲减项 */
function isDeduction(row: any) { return Number(row.amount) < 0 }
/** 只有未结清的冲减项才可以转应收向对方收款 */
function canTransfer(row: any) {
  return isDeduction(row) && row.status === SettlementStatus.UNSETTLED && !row.transferredToReceivable
}
function goTransfer(row: any) {
  router.push(`/finance/payable-transfer/add?payableId=${row.id}`)
}
// 结算状态 code -> 中文
const STATUS_LABEL: Record<string, string> = SettlementStatusLabel
function statusLabel(code?: string) { return code ? (STATUS_LABEL[code] || code) : '' }
function stType(s?: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined { if (s === SettlementStatus.UNSETTLED) return 'danger'; if (s === SettlementStatus.PARTIAL) return 'warning'; if (s === SettlementStatus.SETTLED) return 'success'; if (s === SettlementStatus.CANCELLED) return 'info'; return undefined }
</script>
<template>
  <div class="p">
    <el-card shadow="never" class="query-card">
      <!-- 页签：按主体类型查看应付（全部/供货商/加工厂/辅料商/方案商），与供应商管理页交互一致 -->
      <el-tabs v-model="activeType">
        <el-tab-pane v-for="t in TYPE_TABS" :key="t.name" :label="t.label" :name="t.name" />
      </el-tabs>
      <div class="query-bar">
      <el-form :inline="true" :model="query" class="query-form">
      <el-form-item label="供应商"><RemoteSelect v-model="query.supplierId" :fetch="fetchSuppliers" placeholder="全部" style="width:160px" /></el-form-item>
      <el-form-item label="业务场景">
        <el-select v-model="query.sourceBillType" placeholder="全部" clearable style="width:150px">
          <el-option v-for="o in sourceBillTypeOptions" :key="o.code" :label="o.label" :value="o.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:120px"><el-option v-for="s in [{l:SettlementStatusLabel[SettlementStatus.UNSETTLED],v:SettlementStatus.UNSETTLED},{l:SettlementStatusLabel[SettlementStatus.PARTIAL],v:SettlementStatus.PARTIAL},{l:SettlementStatusLabel[SettlementStatus.SETTLED],v:SettlementStatus.SETTLED}]" :key="s.v" :label="s.l" :value="s.v"/></el-select></el-form-item>
      <el-form-item label="单号"><el-input v-model="query.billNo" placeholder="单据号" clearable @keyup.enter="query_"/></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="query_">查询</el-button>
        <el-button :icon="'Refresh'" @click="reset_">重置</el-button>
      </div>
      </div>
    </el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="data" border stripe @row-click="(row: any) => { detail = row; detailVisible = true }">
        <el-table-column prop="billNo" label="单据号" min-width="150"/>
        <el-table-column label="供应商" min-width="140"><template #default="{row}">{{ row.supplierName || sName(row.supplierId) || '—' }}</template></el-table-column>
        <el-table-column label="主体类型" width="100" align="center">
          <template #default="{row}">
            <el-tag v-if="row.supplierType" :type="TYPE_TAG[row.supplierType] || 'info'" size="small">{{ typeLabel(row.supplierType) }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="业务场景" width="130">
          <template #default="{row}">
            {{ sourceBillTypeLabel(row.sourceBillType) }}
            <el-tag v-if="row.transferredToReceivable" type="warning" size="small" style="margin-left:4px">已转应收</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应付金额" width="120" align="right">
          <!-- 负数=退货/扣款冲减项，标红区分于正常货款 -->
          <template #default="{row}"><span :style="{ color: isDeduction(row) ? 'var(--app-color-danger)' : undefined }">{{ fmt(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column prop="paidAmount" label="已付" width="120" align="right"><template #default="{row}">{{ fmt(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="unpaidAmount" label="未付" width="120" align="right"><template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="120" align="center"/>
        <el-table-column label="状态" width="90" align="center"><template #default="{row}"><el-tag :type="stType(row.status)">{{statusLabel(row.status)}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{row}">
            <el-button type="primary" link @click.stop="detail=row;detailVisible=true">详情</el-button>
            <!-- 无货款可抵时，把这笔扣款/退货转为向对方收款 -->
            <el-button v-if="canTransfer(row)" type="warning" link @click.stop="goTransfer(row)">转应收</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pg"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="load" @current-change="load"/></div>
    </el-card>
    <el-drawer v-model="detailVisible" title="应付详情" size="50%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单据号">{{ detail.billNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ sName(detail.supplierId) }}</el-descriptions-item>
        <el-descriptions-item label="来源类型">{{ sourceBillTypeLabel(detail.sourceBillType) }}</el-descriptions-item>
        <el-descriptions-item label="来源单号">{{ detail.sourceBillNo }}</el-descriptions-item>
        <el-descriptions-item label="到期日">{{ detail.dueDate }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">{{ fmt(detail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="已付金额">{{ fmt(detail.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="未付金额"><span style="color:var(--app-color-danger)">{{ fmt(detail.unpaidAmount) }}</span></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
