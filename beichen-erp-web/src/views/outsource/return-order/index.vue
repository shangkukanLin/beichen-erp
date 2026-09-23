<script setup lang="ts">
/**
 * 委外加工退货（页面 = 加工退货；两个页签 = 加工退货 / 维修退货）
 *
 * <p>2026-09-21（用户口径）：**「加工退货」页签改成一张台账表** —— 有单（挂加工单、在该单收货详细页
 * 发起）与无单（本页发起）**同表同字段**，只用「关联加工单」列区分（有单显示加工单号、无单显示"未关联"）。
 * 它们本来就是同一条负数收货记录（`delivery_type=DEFECT_RETURN`、`is_reverse=1`），审核/反审核/删除
 * 也走同一套端点，所以合并成一张表不需要任何"按来源分派动作"的分支。</p>
 *
 * <p>两个页签：①**加工退货**＝红冲收货台账（有单+无单，本页可新增"无单"那条）②**维修退货**
 * ＝售后品推给工厂维修（送修/返回/结案）。⚠️ 页签①与页面/菜单同名是有意的（用户口径「文案改成加工退货」）——
 * 本页按「退回加工厂」这一大类组织，两个页签是它的两种情形。</p>
 *
 * <p>2026-09-21（用户口径「文案统一成加工退货」+「历史加工退货单不要了」）：动作名全链一个词（加工单
 * 收货详细页的按钮、后端提示语与备注快照同步改名；沿革 退不良 → 加工退货 → 不良退货 → **定稿加工退货**）；
 * 上一代独立加工退货单（`outsource_return_order` 的 DEFECT，已停止新增）**不再单独列页签** ——
 * 存量单据如需反审核/作废，走详情页 URL 直达（`/outsource/return-order/detail/{id}`）。</p>
 *
 * <p>2026-09-21（用户口径「加工退货页面和物料退货的 UI 需要优化和统一，按 A+B+C+D 做」）：
 * 本页与「物料退货」页（`outsource/material-return`）**对齐成同一套列表页家规** ——
 * ①**一页一张卡片**：页签 → 筛选行 → 表格 → 分页（原先「新增/说明卡片 + 表格卡片」两张卡片）；
 * ②**筛选行统一**：左侧筛选 + [查询][重置]，**新增按钮靠右且随页签切换**（原先新增按钮在另一张卡片里，
 *   而筛选（台账）在表格上方、（维修的返回进度）却在按钮卡片上 —— 两页签位置不一致）；
 * ③**页头说明改用 `el-alert`**（原先写在一段普通文字里，里面的 `**` **会被原样显示出来**，实测确认）；
 * ④**列宽/动作集/详情入口与物料退货页统一**：操作列 174、动作顺序 = 详情 → 编辑 → 审核 → 反审核 →
 *   作废 → 结案 → 撤销结案；「详情」在台账走**抽屉**（该记录没有独立详情页）、在维修退货走**详情页**。</p>
 *
 * <p>📏 列宽预算（家规：合计 ≤ 930；纵向滚动条出现时内容区从 963 缩到约 948，故留余量）：
 * 台账 = 96+100+110+64+84+78+174 = 706 固定 ＋ 产品/备注 min 110+110 = **926** ✓；
 * 维修退货 = 132+100+116+100+96+78+174 = 796 固定 ＋ 内容列 min 136 = **932** ✓
 * （与物料退货页的公共列**同宽**：单号 132、对方 100、内容 min136、日期 96、状态 78、操作 174）。</p>
 */
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_RETURN_ORDER_DIRTY_KEY, OutsourceChargeTypeLabel, OutsourceReturnType, OutsourceReturnTypeLabel } from '@/api/enums'

const router = useRouter()

/** 页签：DEFECT=加工退货（红冲收货台账）/ REPAIR=维修退货（独立退货单） */
type TabKey = 'DEFECT' | 'REPAIR'
const activeTab = ref<TabKey>('DEFECT')
/** 维修退货的返回进度筛选（2026-09-17）：PENDING_RETURN 还有未返回 / CLOSED 已结案 */
const progress = ref<string>('')

// ==================== ① 加工退货台账（有单 + 无单一张表，2026-09-21 用户口径） ====================
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
    ElMessage.error('加载加工退货失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { ledgerLoading.value = false }
}
function ledgerSearch() { ledgerPage.pageNum = 1; loadLedger() }
/** 规格显示：DEFECT 显示"不良"，其余显示"A规/B规/C规" */
function specText(q?: string) { return q === 'DEFECT' ? '不良' : (q ? q + '规' : '-') }

async function auditLedger(row: any) {
  const tip = row.orderCode
    ? '确定审核该加工退货吗？审核后将扣减成品库存、把 BOM 料还回工厂委外仓并冲减应付，该加工单的已收数量同步回退。'
    : '确定审核该加工退货吗？审核后将扣减成品库存、把 BOM 料还回所选加工厂的委外仓，并按还回料的 FIFO 价值冲减应付。'
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
  try { await ElMessageBox.confirm('确定删除该加工退货草稿吗？', '删除', { type: 'warning' }) } catch { return }
  try { await request.delete(`/outsource/order-delivery/${row.id}`); ElMessage.success('已删除'); await loadLedger() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

// ---------- 详情抽屉（2026-09-21 用户口径「加工退货页面的列表也应该有详情」）----------
// 列表只留扫读几列，细节进抽屉（与「收货记录」同一家规）：记录全字段 + 落账明细
//（审核后实际扣的成品、按 BOM 还回工厂委外仓的物料、冲减的应付）。
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})

/** 详情改独立页（2026-09-23 用户要求：抽屉改独立界面）：台账行点击 / 行内「详情」都跳详情页 */
function openDetail(row: any) { if (row?.id != null) router.push(`/outsource/defect-return/detail/${row.id}`) }

// ---------- 新增"无单"加工退货（不关联加工单；有单的退回请到该加工单的收货详细页） ----------
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
/**
 * 退货对象（= 还料与应付对象，无单时靠它定位工厂委外仓）。
 * <p>2026-09-21（用户口径）：**加工退货只能退给加工厂或供应商，不能退给供货商** ⇒
 * `excludeSupplierType: 'product'`（供货商=成品商 product；其余 加工厂/辅料商/方案商 都放行）。
 * 后端 `returnDefectNoOrder` 有同一口径的兜底校验 ✓。</p>
 */
const fetchFactories = (kw: string) =>
  request.get('/supplier/page', { params: { pageSize: 500, name: kw, excludeSupplierType: 'product' } })
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
    ElMessage.success('加工退货草稿已保存，请在下方列表审核')
    noOrderVisible.value = false
    activeTab.value = 'DEFECT'
    await loadLedger()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { noOrderSaving.value = false }
}

// ==================== ② 独立退货单（维修退货） ====================
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/return-order/page', {
      params: {
        pageNum: pagination.pageNum, pageSize: pagination.pageSize, returnType: OutsourceReturnType.REPAIR,
        progress: progress.value || undefined
      }
    })
    list.value = r?.records || []; pagination.total = Number(r?.total || 0)
  } finally { loading.value = false }
}

/** 切页签：加工退货看台账、维修退货看独立退货单；分页与进度筛选各自重置 */
function handleTabChange() {
  pagination.pageNum = 1; ledgerPage.pageNum = 1; progress.value = ''
  if (activeTab.value === 'DEFECT') { loadLedger(); return }
  loadData()
}
/** 维修退货页签的「查询/重置」（与物料退货页同一套交互；台账页签有各自的即时筛选） */
function handleSearch() { pagination.pageNum = 1; loadData() }
function handleReset() { progress.value = ''; handleSearch() }

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

/** 新增时带上类型（维修退货），进新增页后表单按类型切换（2026-09-21：加工退货已不在本页新增） */
function handleAdd(type: string) { router.push(`/outsource/return-order/add?returnType=${type || OutsourceReturnType.REPAIR}`) }
/** E4：草稿可编辑（后端 PUT /outsource/return-order/{id}，仅 DRAFT） */
function handleEdit(row: any) { router.push(`/outsource/return-order/edit/${row.id}`) }
/** 加工单号 → 该加工单详情（有单的加工退货由它承载数量回退） */
function goOrder(row: any) { if (row.orderId) router.push(`/outsource/order/detail/${row.orderId}`) }
function goReturnDetail(row: any) { router.push(`/outsource/return-order/detail/${row.id}`) }

function reloadCurrent() {
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
onMounted(() => { loadLedger() })

</script>

<template>
  <!-- 一页一张卡片（家规）：页签 → 筛选行（含新增按钮）→ 业务提示 → 表格 → 分页 -->
  <div class="page-list">
    <el-card shadow="never">
      <!-- 页签：加工退货 = 红冲收货台账（有单+无单一张表）；维修退货 = 售后送修（送修/返回/结案）。
           ⚠️ 上一代独立加工退货单不再单独列页签（用户口径「不要了」）⇒ 存量单据走详情页 URL 直达。 -->
      <el-tabs v-model="activeTab" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane :label="OutsourceReturnTypeLabel[OutsourceReturnType.DEFECT]" name="DEFECT" />
        <el-tab-pane :label="OutsourceReturnTypeLabel[OutsourceReturnType.REPAIR]" name="REPAIR" />
      </el-tabs>

      <!-- 筛选行（与物料退货页同一布局）：左侧筛选 + 查询/重置，右侧新增按钮（随页签切换） -->
      <div v-if="activeTab === 'DEFECT'" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <el-select v-model="ledgerQuery.linked" placeholder="关联加工单" clearable style="width:150px">
          <el-option label="已关联加工单" value="WITH_ORDER" />
          <el-option label="未关联（无单）" value="WITHOUT_ORDER" />
        </el-select>
        <el-select v-model="ledgerQuery.status" placeholder="状态" clearable style="width:130px">
          <el-option label="草稿" :value="DocStatus.DRAFT" />
          <el-option label="已审核" :value="DocStatus.AUDITED" />
        </el-select>
        <!-- 2026-09-21：与另外三个页签一致 —— 筛选只改条件，点「查询」才生效（原先台账页签是即时查询，同页两套手感） -->
        <el-button type="primary" @click="ledgerSearch">查询</el-button>
        <el-button @click="ledgerQuery.linked = ''; ledgerQuery.status = ''; ledgerSearch()">重置</el-button>
        <div style="margin-left:auto">
          <el-button type="success" :icon="'Plus'" @click="openNoOrder">新增</el-button>
        </div>
      </div>
      <div v-else style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <!-- 返回进度（仅维修退货）：跟踪"工厂还有多少没送回来" -->
        <el-select v-model="progress" placeholder="返回进度" clearable style="width:150px">
          <el-option label="待返回" value="PENDING_RETURN" />
          <el-option label="已结案" value="CLOSED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div style="margin-left:auto">
          <el-button type="success" :icon="'Plus'" @click="handleAdd(OutsourceReturnType.REPAIR)">新增</el-button>
        </div>
      </div>

      <!-- 台账的业务提示：改用 el-alert 承载（原先写成普通文字，里面的 `**` 会被原样显示出来） -->
      <el-alert v-if="activeTab === 'DEFECT'" type="info" :closable="false" show-icon style="margin-bottom:8px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            退回某批已收成品、但<b>不挂加工单</b>（单据已结 / 无需挂单）时在此登记；
            <b>有关联加工单的退回</b>请到该加工单的收货详细页用「加工退货」——
            两者是同一个动作（红冲收货），都会出现在下方同一张表里，用「关联加工单」列区分。
          </span>
        </template>
      </el-alert>

      <!-- ============ ① 加工退货台账：有单 + 无单一张表（「关联加工单」列区分） ============ -->
      <template v-if="activeTab === 'DEFECT'">
        <!-- 列宽合计 926px（**留余量**）＜ 内容区（行数多时纵向滚动条约吃掉 15px：963→948），一行显示完、不横向滑动。
             扣减仓库不单独占列，挂在「退货数量」的 title 上（该信息主要给查账用）。 -->
        <el-table :data="ledger" border stripe v-loading="ledgerLoading" @row-click="openDetail">
          <el-table-column label="退货日期" width="96"><template #default="{ row }">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
          <el-table-column prop="factoryName" label="加工厂" width="100" show-overflow-tooltip />
          <!-- 「关联加工单」= 本表唯一的"有无单"区分：有单显示可点的加工单号，无单显示"未关联" -->
          <el-table-column label="关联加工单" width="110" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button v-if="row.orderCode" type="primary" link @click.stop="goOrder(row)">{{ row.orderCode }}</el-button>
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
          <el-table-column label="状态" width="78" align="center">
            <template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip />
          <!-- 动作集与顺序统一（与物料退货页一致）：详情 → 审核 → 反审核 → 删除。
               这些是**收货记录**（没有独立详情页）⇒ 详情走抽屉；单据型的维修退货页签则跳详情页。 -->
          <el-table-column label="操作" width="174" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click.stop="openDetail(row)">详情</el-button>
              <el-button type="success" link v-if="row.status === DocStatus.DRAFT" @click.stop="auditLedger(row)">审核</el-button>
              <el-button type="warning" link v-if="row.status === DocStatus.AUDITED" @click.stop="unauditLedger(row)">反审核</el-button>
              <el-button type="danger" link v-if="row.status === DocStatus.DRAFT" @click.stop="deleteLedger(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination">
          <el-pagination v-model:current-page="ledgerPage.pageNum" v-model:page-size="ledgerPage.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="ledgerPage.total"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="ledgerSearch" @current-change="loadLedger" />
        </div>
      </template>

      <!-- ============ ② 独立退货单：维修退货（送修 / 返回 / 结案） ============ -->
      <template v-else>
        <!-- 列宽合计 932px（**留余量**）＜ 内容区，保证「一行显示完、不横向滑动」。
             2026-09-21 与物料退货页对齐：单号 132 / 加工厂 100 / 送修已返回 116 / 内容 min136 /
             工厂收费 100 / 退货日期 96 / 状态 78 / 操作 174（公共列同宽）。 -->
        <el-table :data="list" border stripe v-loading="loading" @row-click="goReturnDetail">
          <el-table-column prop="code" label="退货单号" width="132" />
          <el-table-column label="加工厂" width="100" show-overflow-tooltip>
            <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.factoryId}`)">{{ row.factoryName }}</el-button></template>
          </el-table-column>
          <!-- 送修 / 已返回（2026-09-17）：橙=工厂还没送完、绿=已全部送回；结案入口见操作列 -->
          <el-table-column label="送修/已返回" width="116" align="center" show-overflow-tooltip>
            <template #default="{ row }">
              <span :style="{ color: Number(row.unreturnedQty) > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontWeight: 500 }"
                :title="Number(row.unreturnedQty) > 0 ? ('还有 ' + row.unreturnedQty + ' 件未返回') : '已全部返回'">
                {{ row.sentQty ?? '-' }} / {{ row.repairReturnedQty ?? 0 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="退货/送修内容" min-width="136" show-overflow-tooltip>
            <!-- 维修退货没有物料明细 → 显示"产品×数量"；加工退货显示"退货物料"（BOM 快照） -->
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
          <el-table-column label="退货日期" width="96" align="center">
            <template #default="{ row }">{{ $fmtDate(row.returnDate) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="78" align="center">
            <!-- 维修退货已结案时直接显示「已结案」（替代"已审核"），未结案按原状态（2026-09-17） -->
            <template #default="{ row }">
              <el-tag v-if="row.returnType === OutsourceReturnType.REPAIR && row.closedFlag === 1" type="success" size="small">已结案</el-tag>
              <el-tag v-else :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <!-- 动作集与顺序统一（与物料退货页一致）：详情 → 编辑 → 审核 → 反审核 → 作废 → 结案 → 撤销结案 -->
          <el-table-column label="操作" width="174" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click.stop="goReturnDetail(row)">详情</el-button>
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
        <div class="pagination">
          <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="handleSearch" @current-change="loadData" />
        </div>
      </template>
    </el-card>

    <!-- 新增无单加工退货弹窗：不关联加工单，靠"加工厂 + 产品 + 规格 + 数量"定位（后端按产品 BOM 快照还料） -->
    <el-dialog v-model="noOrderVisible" title="新增无单加工退货" width="560px" :close-on-click-modal="false">
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
