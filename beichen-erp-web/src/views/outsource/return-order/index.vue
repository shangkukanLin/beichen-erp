<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_RETURN_ORDER_DIRTY_KEY, OutsourceChargeTypeLabel, OutsourceReturnType, OutsourceReturnTypeLabel } from '@/api/enums'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
/** 类型页签：DEFECT 不良退货 / REPAIR 维修退货（2026-09-17） */
const activeType = ref<string>(OutsourceReturnType.DEFECT)
/** 维修退货的返回进度筛选（2026-09-17）：PENDING_RETURN 还有未返回 / CLOSED 已结案 */
const progress = ref<string>('')

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/return-order/page', {
      params: {
        pageNum: pagination.pageNum, pageSize: pagination.pageSize, returnType: activeType.value,
        progress: activeType.value === OutsourceReturnType.REPAIR ? (progress.value || undefined) : undefined
      }
    })
    list.value = r?.records || []; pagination.total = r?.total || 0
  } finally { loading.value = false }
}
/** 切页签：重置分页与进度筛选（进度筛选只对维修退货有意义） */
function handleTabChange() { pagination.pageNum = 1; progress.value = ''; loadData() }

async function handleAudit(row: any) {
  const tip = row.returnType === OutsourceReturnType.REPAIR
    ? '确认审核该维修退货单？审核后成品送修出库（不冲减应付）并生成加工厂向我方收取的维修费应付'
    : '确认审核该退货单？审核后物料入工厂仓、成品出库并冲减应付'
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/audit`); ElMessage.success('审核成功'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit(row: any) {
  try { await ElMessageBox.confirm('确认反审核？将逆向库存并冲销应付', '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

/** 结案（仅维修退货）：工厂把送修成品全部送回（未返回=0）后确认收尾 */
async function handleClose(row: any) {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/撤销维修返回，也不能反审核（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/close`); ElMessage.success('已结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen(row: any) {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/re-open`); ElMessage.success('已撤销结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/return-order/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

/** 新增时带上类型（不良退货 / 维修退货），进新增页后表单按类型切换 */
function handleAdd(type: string) { router.push(`/outsource/return-order/add?returnType=${type || OutsourceReturnType.DEFECT}`) }
/** E4：草稿可编辑（后端 PUT /outsource/return-order/{id}，仅 DRAFT） */
function handleEdit(row: any) { router.push(`/outsource/return-order/edit/${row.id}`) }

onActivated(() => {
  // 详情/新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY)
    loadData()
  }
})
onMounted(() => { loadData() })

</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <el-button type="primary" :icon="'Plus'" @click="handleAdd(OutsourceReturnType.DEFECT)">新增不良退货</el-button>
      <el-button type="warning" :icon="'Plus'" @click="handleAdd(OutsourceReturnType.REPAIR)">新增维修退货</el-button>
      <!-- 返回进度（2026-09-17，仅维修退货）：跟踪"工厂还有多少没送回来" -->
      <el-select v-if="activeType === OutsourceReturnType.REPAIR" v-model="progress" placeholder="返回进度" clearable
        style="width:150px;margin-left:12px" @change="() => { pagination.pageNum = 1; loadData() }">
        <el-option label="待返回" value="PENDING_RETURN" />
        <el-option label="已结案" value="CLOSED" />
      </el-select>
    </el-card>
    <el-card shadow="never">
      <!-- 类型页签（2026-09-17）：不良退货 = 工厂交货发现不良退回；维修退货 = 售后品推给工厂维修 -->
      <el-tabs v-model="activeType" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane :label="OutsourceReturnTypeLabel[OutsourceReturnType.DEFECT]" :name="OutsourceReturnType.DEFECT" />
        <el-tab-pane :label="OutsourceReturnTypeLabel[OutsourceReturnType.REPAIR]" :name="OutsourceReturnType.REPAIR" />
      </el-tabs>
      <!-- 列宽合计 ≈932px（**留余量**）＜ 内容区，保证「一行显示完、不横向滑动」。
           注意：行数多时出现纵向滚动条会让内容区从 963 缩到约 948，故按 948 兜底。
           2026-09-17：①**去掉「类型」列**——页面已按"不良退货/维修退货"分页签，列是冗余的；
           ②压缩各列（单号 170→140、加工厂 120、关联加工单 150→120、内容 min 150→100、工厂收费 →100、
           日期 110→104、状态 90→84、操作 240→164），解决原先 1252px 宽导致 状态/操作 被挤出屏幕的问题。 -->
      <el-table :data="list" border stripe v-loading="loading" @row-click="(row: any) => router.push(`/outsource/return-order/detail/${row.id}`)">
        <el-table-column prop="code" label="退货单号" width="140" />
        <el-table-column label="加工厂" width="120" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.factoryId}`)">{{ row.factoryName }}</el-button></template>
        </el-table-column>
        <!-- 不良退货：可关联加工单（也可不关联，按 BOM 快照带料）；维修退货不关联加工单，该列换「送修/已返回」 -->
        <el-table-column v-if="activeType === OutsourceReturnType.DEFECT" label="关联加工单" width="120" show-overflow-tooltip>
          <template #default="{ row }"><span v-if="row.orderCode">{{ row.orderCode }}</span><span v-else style="color:var(--app-text-placeholder)">未关联</span></template>
        </el-table-column>
        <!-- 送修 / 已返回（2026-09-17）：橙=工厂还没送完、绿=已全部送回；结案入口见操作列 -->
        <el-table-column v-else label="送修/已返回" width="110" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ color: Number(row.unreturnedQty) > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontWeight: 500 }"
              :title="Number(row.unreturnedQty) > 0 ? ('还有 ' + row.unreturnedQty + ' 件未返回') : '已全部返回'">
              {{ row.sentQty ?? '-' }} / {{ row.repairReturnedQty ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="退货/送修内容" min-width="100" show-overflow-tooltip>
          <!-- 不良退货显示"退货物料"（BOM 快照），维修退货没有物料 → 显示"产品×数量" -->
          <template #default="{ row }">{{ row.itemSummary || row.productSummary || '-' }}</template>
        </el-table-column>
        <!-- 收费方向：加工厂向我方收取（我方付加工厂，审核后生成正向应付） -->
        <el-table-column label="工厂收费" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.chargeFlag) === 1 && Number(row.chargeAmount) > 0" type="warning" size="small"
              :title="'加工厂向我方收取：' + (OutsourceChargeTypeLabel[String(row.chargeType)] || '')">
              {{ Number(row.chargeAmount).toFixed(2) }}
            </el-tag>
            <span v-else style="color:#c0c4cc">不收费</span>
          </template>
        </el-table-column>
        <el-table-column label="退货日期" width="104" align="center">
          <template #default="{ row }">{{ $fmtDate(row.returnDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="84" align="center">
          <!-- 维修退货已结案时直接显示「已结案」（替代"已审核"），未结案按原状态（2026-09-17） -->
          <template #default="{ row }">
            <el-tag v-if="row.returnType === OutsourceReturnType.REPAIR && row.closedFlag === 1" type="success" size="small">已结案</el-tag>
            <el-tag v-else :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="164" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="router.push(`/outsource/return-order/detail/${row.id}`)">详情</el-button>
            <el-button type="warning" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button type="success" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleAudit(row)">审核</el-button>
            <el-button type="warning" link v-if="row.status===DocStatus.AUDITED && row.closedFlag!==1" @click.stop="handleUnAudit(row)">反审核</el-button>
            <el-button type="danger" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleCancel(row)">作废</el-button>
            <!-- 结案（仅维修退货）：未返回=0 才出现 -->
            <el-button type="success" link v-if="row.returnType===OutsourceReturnType.REPAIR && row.status===DocStatus.AUDITED && row.closedFlag!==1 && Number(row.unreturnedQty)===0" @click.stop="handleClose(row)">结案</el-button>
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
