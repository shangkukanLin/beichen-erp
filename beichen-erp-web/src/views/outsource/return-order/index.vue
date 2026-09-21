<script setup lang="ts">
/**
 * 委外加工退货
 *
 * <p>2026-09-21（用户口径）：**「不良退货」页签改成一张台账表** —— 有单（挂加工单、在该单收货详细页
 * 发起）与无单（本页发起）**同表同字段**，只用「关联加工单」列区分（有单显示加工单号、无单显示"未关联"）。
 * 它们本来就是同一条负数收货记录（`delivery_type=DEFECT_RETURN`、`is_reverse=1`），审核/反审核/删除
 * 也走同一套端点，所以合并成一张表不需要任何"按来源分派动作"的分支。</p>
 *
 * <p>三个页签：①**不良退货**＝红冲收货台账（有单+无单，本页可新增"无单"那条）②**历史不良退货单**
 * ＝上一代的独立退货单（2026-09-21 起停止新增，只在存在存量单据时出现，仅供审核/反审核/作废）
 * ③**维修退货**＝售后品推给工厂维修（送修/返回/结案）。</p>
 */
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_RETURN_ORDER_DIRTY_KEY, OutsourceChargeTypeLabel, OutsourceReturnType, OutsourceReturnTypeLabel } from '@/api/enums'

const router = useRouter()

/** 页签：DEFECT=不良退货（红冲收货台账）/ DEFECT_LEGACY=历史不良退货单 / REPAIR=维修退货 */
type TabKey = 'DEFECT' | 'DEFECT_LEGACY' | 'REPAIR'
const activeTab = ref<TabKey>('DEFECT')
/** 独立退货单的类型（历史页签=DEFECT / 维修页签=REPAIR），驱动 /outsource/return-order/page 查询 */
const activeType = ref<string>(OutsourceReturnType.DEFECT)
/** 维修退货的返回进度筛选（2026-09-17）：PENDING_RETURN 还有未返回 / CLOSED 已结案 */
const progress = ref<string>('')

// ==================== ① 不良退货台账（有单 + 无单一张表，2026-09-21 用户口径） ====================
const ledger = ref<any[]>([])
const ledgerLoading = ref(false)
/** 关联加工单筛选：'' 全部 / WITH_ORDER 已关联 / WITHOUT_ORDER 未关联 */
const ledgerQuery = reactive({ linked: '', status: '' })
const ledgerPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function loadLedger() {
  ledgerLoading.value = true
  try {
    const r = await request.get<any, any>('/outsource/order-delivery/return-defect/page', {
      params: {
        page: ledgerPage.pageNum, size: ledgerPage.pageSize,
        linked: ledgerQuery.linked || undefined, status: ledgerQuery.status || undefined
      }
    })
    ledger.value = r?.records || []
    ledgerPage.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载不良退货失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { ledgerLoading.value = false }
}
function ledgerSearch() { ledgerPage.pageNum = 1; loadLedger() }
/** 规格显示：DEFECT 显示"不良"，其余显示"A规/B规/C规" */
function specText(q?: string) { return q === 'DEFECT' ? '不良' : (q ? q + '规' : '-') }

async function auditLedger(row: any) {
  const tip = row.orderCode
    ? '确定审核该不良退货吗？审核后将扣减成品库存、把 BOM 料还回工厂委外仓并冲减应付，该加工单的已收数量同步回退。'
    : '确定审核该不良退货吗？审核后将扣减成品库存、把 BOM 料还回所选加工厂的委外仓，并按还回料的 FIFO 价值冲减应付。'
  try { await ElMessageBox.confirm(tip, '审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/order-delivery/${row.id}/audit`); ElMessage.success('已审核'); await loadLedger() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function unauditLedger(row: any) {
  try { await ElMessageBox.confirm('确定反审核吗？将回滚成品库存、扣回已还的料并冲回应付，回到草稿。', '反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/order-delivery/${row.id}/un-audit`); ElMessage.success('已反审核'); await loadLedger() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function deleteLedger(row: any) {
  try { await ElMessageBox.confirm('确定删除该不良退货草稿吗？', '删除', { type: 'warning' }) } catch { return }
  try { await request.delete(`/outsource/order-delivery/${row.id}`); ElMessage.success('已删除'); await loadLedger() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

// ---------- 新增"无单"不良退货（不关联加工单；有单的退回请到该加工单的收货详细页） ----------
const noOrderVisible = ref(false)
const noOrderSaving = ref(false)
/** 退货规格：与加工单收货详细页的退货弹窗同一口径（A/B/C/不良） */
const NO_ORDER_SPECS = [
  { value: 'A', label: 'A规' }, { value: 'B', label: 'B规' },
  { value: 'C', label: 'C规' }, { value: 'DEFECT', label: '不良' }
]
const noOrderForm = reactive({
  factoryId: undefined as any, warehouseId: undefined as any, productMasterId: undefined as any,
  qualityType: 'A' as string, quantity: '' as any, remark: ''
})
/** 加工厂（= 还料与应付对象，无单时靠它定位工厂委外仓） */
const fetchFactories = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
/** 扣减的成品仓（我方自有成品仓） */
const fetchFinishedWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })
/** 产品主数据（无单时没有加工单产品行可选） */
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })

function openNoOrder() {
  Object.assign(noOrderForm, {
    factoryId: undefined, warehouseId: undefined, productMasterId: undefined,
    qualityType: 'A', quantity: '', remark: ''
  })
  noOrderVisible.value = true
}

async function submitNoOrder() {
  if (!noOrderForm.factoryId) { ElMessage.warning('请选择加工厂'); return }
  if (!noOrderForm.warehouseId) { ElMessage.warning('请选择扣减的成品仓库'); return }
  if (!noOrderForm.productMasterId) { ElMessage.warning('请选择产品'); return }
  const qty = Math.round(Number(noOrderForm.quantity) || 0)
  if (!(qty > 0)) { ElMessage.warning('请输入退货数量'); return }
  noOrderSaving.value = true
  try {
    await request.post('/outsource/order-delivery/return-defect-no-order', {
      factoryId: noOrderForm.factoryId, warehouseId: noOrderForm.warehouseId,
      productMasterId: noOrderForm.productMasterId, qualityType: noOrderForm.qualityType,
      quantity: qty, remark: noOrderForm.remark
    })
    ElMessage.success('不良退货草稿已保存，请在下方列表审核')
    noOrderVisible.value = false
    activeTab.value = 'DEFECT'
    await loadLedger()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { noOrderSaving.value = false }
}

// ==================== ②③ 独立退货单（历史不良退货单 / 维修退货） ====================
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
/** 历史不良退货单存量数：>0 才显示该页签（2026-09-21 起不再新增，存量清完页签自动消失） */
const legacyTotal = ref(0)

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
/** 拉一次存量数，决定"历史不良退货单"页签是否出现 */
async function loadLegacyTotal() {
  try {
    const r = await request.get<any, any>('/outsource/return-order/page', {
      params: { pageNum: 1, pageSize: 1, returnType: OutsourceReturnType.DEFECT }
    })
    legacyTotal.value = Number(r?.total || 0)
    if (legacyTotal.value === 0 && activeTab.value === 'DEFECT_LEGACY') activeTab.value = 'DEFECT'
  } catch { legacyTotal.value = 0 }
}

/** 切页签：不良退货看台账、历史/维修看独立退货单；分页与进度筛选各自重置 */
function handleTabChange() {
  pagination.pageNum = 1; ledgerPage.pageNum = 1; progress.value = ''
  if (activeTab.value === 'DEFECT') { loadLedger(); return }
  activeType.value = activeTab.value === 'REPAIR' ? OutsourceReturnType.REPAIR : OutsourceReturnType.DEFECT
  loadData()
}

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

/** 新增时带上类型（维修退货），进新增页后表单按类型切换（2026-09-21：不良退货已不在本页新增） */
function handleAdd(type: string) { router.push(`/outsource/return-order/add?returnType=${type || OutsourceReturnType.REPAIR}`) }
/** E4：草稿可编辑（后端 PUT /outsource/return-order/{id}，仅 DRAFT） */
function handleEdit(row: any) { router.push(`/outsource/return-order/edit/${row.id}`) }
/** 加工单号 → 该加工单详情（有单的不良退货由它承载数量回退） */
function goOrder(row: any) { if (row.orderId) router.push(`/outsource/order/detail/${row.orderId}`) }

function reloadCurrent() {
  loadLegacyTotal()
  if (activeTab.value === 'DEFECT') loadLedger()
  else loadData()
}

onActivated(() => {
  // 详情/新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY)
    reloadCurrent()
  }
})
onMounted(() => { loadLegacyTotal(); loadLedger() })

</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <!-- 新增入口按页签切换（2026-09-21 用户口径）：不良退货页只新增"无单"那种（有单的请到加工单收货详细页），
           维修退货页新增独立维修退货单；历史页签只读（上一代单据，只保留审核/反审核/作废）。 -->
      <template v-if="activeTab === 'DEFECT'">
        <el-button type="danger" :icon="'Plus'" @click="openNoOrder">新增无单不良退货</el-button>
        <span style="margin-left:12px;color:var(--app-text-secondary);font-size:var(--app-font-xs);line-height:1.6">
          退回某批已收成品、但**不挂加工单**（单据已结／无需挂单）时在此登记；<b>有关联加工单的退回</b>请到该加工单的
          收货详细页用「加工退货」—— 两者是同一个动作（红冲收货），都会出现在下方同一张表里，用「关联加工单」列区分。
        </span>
      </template>
      <template v-else-if="activeTab === 'REPAIR'">
        <el-button type="warning" :icon="'Plus'" @click="handleAdd(OutsourceReturnType.REPAIR)">新增维修退货</el-button>
        <!-- 返回进度（2026-09-17，仅维修退货）：跟踪"工厂还有多少没送回来" -->
        <el-select v-model="progress" placeholder="返回进度" clearable
          style="width:150px;margin-left:12px" @change="() => { pagination.pageNum = 1; loadData() }">
          <el-option label="待返回" value="PENDING_RETURN" />
          <el-option label="已结案" value="CLOSED" />
        </el-select>
      </template>
    </el-card>
    <el-card shadow="never">
      <!-- 页签（2026-09-21）：不良退货 = 红冲收货台账（有单+无单一张表）；历史不良退货单 = 上一代独立单据
           （仅在存有历史单据时出现）；维修退货 = 售后送修（送修/返回/结案）。 -->
      <el-tabs v-model="activeTab" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane :label="OutsourceReturnTypeLabel[OutsourceReturnType.DEFECT]" name="DEFECT" />
        <el-tab-pane v-if="legacyTotal > 0" label="历史不良退货单" name="DEFECT_LEGACY" />
        <el-tab-pane :label="OutsourceReturnTypeLabel[OutsourceReturnType.REPAIR]" name="REPAIR" />
      </el-tabs>

      <!-- ============ ① 不良退货台账：有单 + 无单一张表（「关联加工单」列区分） ============ -->
      <template v-if="activeTab === 'DEFECT'">
        <el-form :inline="true" style="margin-bottom:8px">
          <el-form-item label="关联加工单">
            <el-select v-model="ledgerQuery.linked" placeholder="全部" clearable style="width:130px" @change="ledgerSearch">
              <el-option label="已关联加工单" value="WITH_ORDER" />
              <el-option label="未关联（无单）" value="WITHOUT_ORDER" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="ledgerQuery.status" placeholder="全部" clearable style="width:120px" @change="ledgerSearch">
              <el-option label="草稿" :value="DocStatus.DRAFT" />
              <el-option label="已审核" :value="DocStatus.AUDITED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="ledgerQuery.linked = ''; ledgerQuery.status = ''; ledgerSearch()">重置</el-button>
          </el-form-item>
        </el-form>
        <!-- 列宽合计 ≈920px（**留余量**）＜ 内容区（行数多时纵向滚动条约吃掉 15px：963→948），一行显示完、不横向滑动。
             扣减仓库不单独占列，挂在「退货数量」的 title 上（该信息主要给查账用）。 -->
        <el-table :data="ledger" border stripe v-loading="ledgerLoading">
          <el-table-column label="退货日期" width="96"><template #default="{ row }">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
          <el-table-column prop="factoryName" label="加工厂" width="110" show-overflow-tooltip />
          <!-- 「关联加工单」= 本表唯一的"有无单"区分：有单显示可点的加工单号，无单显示"未关联" -->
          <el-table-column label="关联加工单" width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button v-if="row.orderCode" type="primary" link @click="goOrder(row)">{{ row.orderCode }}</el-button>
              <span v-else style="color:var(--app-text-placeholder)">未关联</span>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="产品" min-width="110" show-overflow-tooltip />
          <el-table-column label="规格" width="64" align="center"><template #default="{ row }">{{ specText(row.qualityType) }}</template></el-table-column>
          <el-table-column label="退货数量" width="84" align="right">
            <template #default="{ row }">
              <span :title="row.warehouseName ? ('扣减仓库：' + row.warehouseName) : ''" style="color:var(--app-color-danger);font-weight:500">{{ Math.abs(Number(row.quantity || 0)) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="76" align="center">
            <template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="140" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="success" link v-if="row.status === DocStatus.DRAFT" @click="auditLedger(row)">审核</el-button>
              <el-button type="warning" link v-if="row.status === DocStatus.AUDITED" @click="unauditLedger(row)">反审核</el-button>
              <el-button type="danger" link v-if="row.status === DocStatus.DRAFT" @click="deleteLedger(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top:16px;display:flex;justify-content:flex-end">
          <el-pagination v-model:current-page="ledgerPage.pageNum" v-model:page-size="ledgerPage.pageSize" :total="ledgerPage.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadLedger" @size-change="ledgerSearch" />
        </div>
      </template>

      <!-- ============ ②③ 独立退货单：历史不良退货单（只读存量）/ 维修退货 ============ -->
      <template v-else>
        <!-- 列宽合计 ≈932px（**留余量**）＜ 内容区，保证「一行显示完、不横向滑动」。
             注意：行数多时出现纵向滚动条会让内容区从 963 缩到约 948，故按 948 兜底。
             2026-09-17：①**去掉「类型」列**——页面已按类型分页签；②压缩各列解决原先 1252px 宽导致状态/操作被挤出屏幕的问题。 -->
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
      </template>
    </el-card>

    <!-- 新增无单不良退货弹窗：不关联加工单，靠"加工厂 + 产品 + 规格 + 数量"定位（后端按产品 BOM 快照还料） -->
    <el-dialog v-model="noOrderVisible" title="新增无单不良退货" width="560px" :close-on-click-modal="false">
      <el-form :model="noOrderForm" label-width="120px" size="small">
        <el-form-item required label="加工厂">
          <RemoteSelect v-model="noOrderForm.factoryId" :fetch="fetchFactories" :label-key="(row:any)=>row.name" style="width:100%" placeholder="加工厂（还料/应付对象）" />
        </el-form-item>
        <el-form-item required label="扣减成品仓">
          <RemoteSelect v-model="noOrderForm.warehouseId" :fetch="fetchFinishedWarehouses" :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" style="width:100%" placeholder="选择扣减的成品仓库" />
        </el-form-item>
        <el-form-item required label="产品">
          <RemoteSelect v-model="noOrderForm.productMasterId" :fetch="fetchProducts" :label-key="(row:any)=>row.name" style="width:100%" placeholder="选择产品" />
        </el-form-item>
        <el-form-item required label="退货规格">
          <el-select v-model="noOrderForm.qualityType" style="width:100%">
            <el-option v-for="o in NO_ORDER_SPECS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item required label="退货数量">
          <el-input v-model="noOrderForm.quantity" type="number" placeholder="整数" @change="noOrderForm.quantity = Math.round(Number(noOrderForm.quantity) || 0)" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="noOrderForm.remark" placeholder="选填，如退回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noOrderVisible = false">取消</el-button>
        <el-button type="primary" :loading="noOrderSaving" @click="submitNoOrder">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>
