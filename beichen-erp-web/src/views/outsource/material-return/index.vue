<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel } from '@/api/enums'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ code: '', supplierId: undefined as any, status: '', progress: '' })
/** 类型页签（2026-09-17）：REFUND 退货退款（冲应付）/ REPAIR 维修返还（送修，修好登记返回入库） */
const activeType = ref<string>(MaterialReturnType.REFUND)

// Odoo 风格：退货对象（物料商）实时查库
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })

async function loadData() {
  loading.value = true
  try {
    // progress（维修返还）：PENDING_RETURN 还有未返回 / CLOSED 已结案（2026-09-17）
    const r = await request.get<any, any>('/outsource/material-return/page', { params: { pageNum: pagination.pageNum, pageSize: pagination.pageSize, code: query.code || undefined, supplierId: query.supplierId || undefined, status: query.status || undefined, returnType: activeType.value, progress: activeType.value === MaterialReturnType.REPAIR ? (query.progress || undefined) : undefined } })
    list.value = r?.records || []; pagination.total = r?.total || 0
  } finally { loading.value = false }
}
/** 切类型页签：重置到第 1 页再查（返回进度筛选只在维修页签有效，切换时清空） */
function handleTabChange() { pagination.pageNum = 1; query.progress = ''; loadData() }

function handleSearch() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.supplierId = undefined; query.status = ''; query.progress = ''; handleSearch() }

/** 结案（仅维修返还）：全部送修数量已返回（未返回=0）后确认收尾 */
async function handleClose(row: any) {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/撤销维修返回，也不能反审核（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/close`); ElMessage.success('已结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen(row: any) {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/re-open`); ElMessage.success('已撤销结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

/** 审核提示按类型区分：退货退款冲减应付；维修返还只出库送修（不冲应付） */
async function handleAudit(row: any) {
  const repair = row.returnType === MaterialReturnType.REPAIR
  // 维修返还：关联订单未完成时审核会同时扣减该订单收料数（修好返回自动回补，2026-09-17）
  const tip = repair
    ? ('确认审核该维修返还单？审核后物料出源仓送供应商维修（不冲减应付）' + (row.materialOrderCode ? `；关联订单 ${row.materialOrderCode} 若未完成，将同时扣减其收料数` : ''))
    : '确认审核该退货单？审核后物料出源仓并冲减应付'
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/audit`); ElMessage.success('审核成功'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit(row: any) {
  const repair = row.returnType === MaterialReturnType.REPAIR
  const tip = repair
    ? ('确认反审核？将送修物料回源仓（若有维修返回记录需先撤销；关联订单已扣减的收料数会一并回滚）')
    : '确认反审核？将物料回源仓并冲销应付'
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

/** 新增时带上类型（退货退款 / 维修返还），进新增页后表单按类型切换（2026-09-17） */
function handleAdd(type?: string) { router.push(`/outsource/material-return/add?returnType=${type || MaterialReturnType.REFUND}`) }

onActivated(() => {
  // 详情/新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY)
    loadData()
  }
})
onMounted(loadData)

</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap">
        <el-input v-model="query.code" placeholder="退货单号" clearable style="width:200px" @keyup.enter="handleSearch" />
        <RemoteSelect v-model="query.supplierId" :fetch="fetchSuppliers" placeholder="供应商" style="width:180px" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
          <el-option label="草稿" :value="DocStatus.DRAFT" />
          <el-option label="已审核" :value="DocStatus.AUDITED" />
          <el-option label="已作废" :value="DocStatus.CANCELLED" />
        </el-select>
        <!-- 返回进度（2026-09-17，仅维修返还）：跟踪"还有多少货在供应商处没回来" -->
        <el-select v-if="activeType === MaterialReturnType.REPAIR" v-model="query.progress" placeholder="返回进度" clearable style="width:140px">
          <el-option label="待返回" value="PENDING_RETURN" />
          <el-option label="已结案" value="CLOSED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" :icon="'Plus'" @click="handleAdd(MaterialReturnType.REFUND)">新增退货退款</el-button>
        <el-button type="warning" :icon="'Plus'" @click="handleAdd(MaterialReturnType.REPAIR)">新增维修返还</el-button>
      </div>
    </el-card>
    <el-card shadow="never">
      <!-- 类型页签（2026-09-17）：退货退款 = 退回并退款（冲应付）；维修返还 = 退回维修（修好登记返回入库） -->
      <el-tabs v-model="activeType" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane :label="MaterialReturnTypeLabel[MaterialReturnType.REFUND]" :name="MaterialReturnType.REFUND" />
        <el-tab-pane :label="MaterialReturnTypeLabel[MaterialReturnType.REPAIR]" :name="MaterialReturnType.REPAIR" />
      </el-tabs>
      <el-table :data="list" border stripe v-loading="loading" @row-click="(row: any) => router.push(`/outsource/material-return/detail/${row.id}`)">
        <el-table-column prop="code" label="退货单号" width="170" />
        <el-table-column label="供应商" width="110" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName }}</el-button></template>
        </el-table-column>
        <el-table-column prop="warehouseName" label="出库源仓" width="110" show-overflow-tooltip />
        <el-table-column :label="activeType === MaterialReturnType.REPAIR ? '送修物料' : '退货物料'" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemSummary || '-' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">{{ row.totalAmount != null ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column label="退货日期" width="105" align="center">
          <template #default="{ row }">{{ $fmtDate(row.returnDate) }}</template>
        </el-table-column>
        <!-- 送修/已返回跟踪（2026-09-17，仅维修返还）：场景②订单已完成 / 场景③未关联订单 靠它跟踪 -->
        <el-table-column v-if="activeType === MaterialReturnType.REPAIR" label="送修/已返回" width="110" align="center">
          <template #default="{ row }">
            <span>{{ row.sentQty ?? '-' }} / <span :style="{ color: Number(row.returnedQty) > 0 ? 'var(--app-color-success)' : '' }">{{ row.returnedQty ?? 0 }}</span></span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeType === MaterialReturnType.REPAIR" label="返回进度" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.closedFlag === 1" type="success" size="small">已结案</el-tag>
            <el-tag v-else-if="row.status === DocStatus.AUDITED && Number(row.unreturnedQty) > 0" type="warning" size="small">待返回 {{ row.unreturnedQty }}</el-tag>
            <el-tag v-else-if="row.status === DocStatus.AUDITED" type="info" size="small">已返回完</el-tag>
            <span v-else style="color:var(--app-text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="router.push(`/outsource/material-return/detail/${row.id}`)">详情</el-button>
            <el-button type="success" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleAudit(row)">审核</el-button>
            <el-button type="warning" link v-if="row.status===DocStatus.AUDITED && row.closedFlag!==1" @click.stop="handleUnAudit(row)">反审核</el-button>
            <el-button type="danger" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleCancel(row)">作废</el-button>
            <!-- 结案（仅维修返还）：未返回=0 时才出现，代表场景②/③的跟踪终点 -->
            <el-button type="success" link v-if="row.status===DocStatus.AUDITED && row.closedFlag!==1 && Number(row.unreturnedQty)===0" @click.stop="handleClose(row)">结案</el-button>
            <el-button type="warning" link v-if="row.closedFlag===1" @click.stop="handleReOpen(row)">撤销结案</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="()=>{pagination.pageNum=1;loadData()}" />
      </div>
    </el-card>
  </div>
</template>
