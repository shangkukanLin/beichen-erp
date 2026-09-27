<script setup lang="ts">
// 加工退货记录详情（2026-09-23 用户要求：原「加工退货详情」580px 抽屉改为独立页面）
// —— 按 id 回源 `/outsource/order-delivery/return-defect/{id}/detail`（含落账明细），
//    台账行点击 / 行内「详情」按钮都跳到这里。
import { computed, ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/enums'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'
import RemoteSelect from '@/components/RemoteSelect.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<any>({})

/** 退货规格 code -> 中文（与列表、无单退货弹窗同一口径） */
function specText(q?: string) {
  if (q === 'A') return 'A规'
  if (q === 'B') return 'B规'
  if (q === 'C') return 'C规'
  if (q === 'DEFECT') return '不良'
  return q || '-'
}
/**
 * 库存形态（与 /outsource/warehouse-detail 同口径）：无单退货的成品以「成品（加工退货）」形态进加工厂委外仓。
 */
const StockFormLabel: Record<string, string> = {
  MATERIAL: '物料',
  PRODUCT_DEFECT: '成品（加工退货）',
  PRODUCT_REPAIR: '成品（维修退货）',
  MATERIAL_REPAIR: '物料（送修在厂）',
}

/**
 * 是否为「无单退货·新口径」记录（2026-09-27 用户口径）——判定**只看实际流水**，不看 linked：
 * - 无单退货（GTW-，2026-09-25 P1-1 起）：不拆 BOM、不冲应付，只扣成品 + 把成品以 PRODUCT_DEFECT 转入委外仓
 *   ⇒ `materials` 为空、`outsourceIn` 有值；
 * - 存量「独立 DEFECT 单」（旧逻辑，已停止新增）：虽也不关联加工单，但会还料 + 负应付、且**没有** PRODUCT_DEFECT 转移腿
 *   ⇒ `materials` 非空 ⇒ 不能被误判成新口径（否则会隐藏它的真实还料明细）。
 */
const isNoOrderNew = computed(() =>
  !detail.value.linked && (detail.value.materials || []).length === 0
)
function goOrder() {
  if (detail.value.orderId != null) router.push(`/outsource/order/detail/${detail.value.orderId}`)
}

async function load() {
  loading.value = true
  try {
    detail.value = (await request.get<any, any>(`/outsource/order-delivery/return-defect/${route.params.id}/detail`)) || {}
  } catch { detail.value = {} } finally { loading.value = false }
}

/**
 * ==================== 加工返回登记（2026-09-27 用户口径） ====================
 * 「加工返回单」不再是独立单据/独立菜单叶子：工厂修好送回时，**就在本页**登记返回，
 * 交互与「成品维修退货」详情页的「登记维修返回」完全一致 —— 登记即生效、可逐条撤销、有返回记录列表。
 * 账务与老流程一字未改：核销在厂成品（PRODUCT_DEFECT）+ 修好成品回我方仓 + 按实际用料扣委外仓料
 * + 料款生成对加工厂的**赔料应收** + FIFO 成本结转。
 */
const returns = ref<any[]>([])
const returnsLoading = ref(false)
/** 已返回量 = Σ 本来源单的返回记录（列表接口只返回本来源单） */
const returnedQty = computed(() => returns.value.reduce((s: number, r: any) => s + Math.abs(Number(r.quantity || 0)), 0))
/** 退货总量（台账记录里数量是负数 ⇒ 取绝对值） */
const sentQty = computed(() => Math.abs(Number(detail.value.quantity || 0)))
/** 未返回量 */
const unreturnedQty = computed(() => Math.max(0, sentQty.value - returnedQty.value))
/**
 * 能否登记返回：仅「无单退货·新口径」（成品已以 PRODUCT_DEFECT 转入委外仓）+ 已审核 + 还有未返回。
 * 存量「独立 DEFECT 单」不进在厂 ⇒ 给它登记会撞「在厂成品不足」⇒ 直接不给入口。
 */
const canReturn = computed(() => isNoOrderNew.value && detail.value.settled === true
  && detail.value.status === DocStatus.AUDITED && unreturnedQty.value > 0)

async function loadReturns() {
  returnsLoading.value = true
  try {
    returns.value = (await request.get<any, any>(`/outsource/order-delivery/${route.params.id}/return-backs`)) || []
  } catch { returns.value = [] } finally { returnsLoading.value = false }
}

/** 回仓品质可选档（与无单退货建单、加工返回弹窗同一口径） */
const specOptions = [
  { value: 'A', label: 'A规' }, { value: 'B', label: 'B规' },
  { value: 'C', label: 'C规' }, { value: 'DEFECT', label: '不良' }
]
const returnDlg = reactive({ visible: false, saving: false })
const returnForm = reactive({
  quantity: '' as any, returnQualityType: 'A', inWarehouseId: undefined as any,
  returnDate: '', remark: '', items: [] as Array<{ materialId: any, quantity: any }>
})
/** 回仓仓库（我方自有成品仓） */
const fetchFinishedWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })
/** 「实际用料」候选：后端按**来源单的 BOM 快照**解析（池空 ⇒ 允许只登记返回、不填用料，不卡流程） */
const candidates = ref<any[]>([])
async function loadCandidates() {
  candidates.value = []
  try {
    candidates.value = (await request.get<any, any>(`/outsource/order-delivery/${route.params.id}/return-back-material-candidates`)) || []
  } catch { candidates.value = [] }
}
/** 选料后默认数量 = 单套用量 × 本次返回数量（可改；数量仍可超 BOM） */
function onPickMaterial(it: any) {
  const c = candidates.value.find((x: any) => String(x.materialId) === String(it.materialId))
  if (!c) return
  const n = Math.round(Number(returnForm.quantity) || 0)
  it.quantity = Math.max(1, Math.round(Number(c.perSetQuantity || 0) * (n > 0 ? n : 1)))
}
function isMaterialPicked(mid: any, cur: any) {
  return returnForm.items.some((it: any) => it !== cur && String(it.materialId) === String(mid))
}
function addItem() { returnForm.items.push({ materialId: undefined, quantity: undefined }) }
function removeItem(i: number) { returnForm.items.splice(i, 1) }

function openReturn() {
  Object.assign(returnForm, {
    quantity: unreturnedQty.value,                       // 默认=未返回量（可改；后端按单防超返）
    returnQualityType: detail.value.qualityType || 'A',  // 默认沿用退货规格
    inWarehouseId: undefined, returnDate: '', remark: '', items: []
  })
  returnDlg.visible = true
  loadCandidates()
}

async function submitReturn() {
  const qty = Math.round(Number(returnForm.quantity) || 0)
  if (!(qty > 0)) { ElMessage.warning('请输入返回数量'); return }
  if (qty > unreturnedQty.value) { ElMessage.warning(`返回数量不能超过未返回量（${unreturnedQty.value}）`); return }
  if (!returnForm.inWarehouseId) { ElMessage.warning('请选择回仓仓库'); return }
  const items = returnForm.items
    .map((it: any) => ({ materialId: it.materialId, quantity: Math.round(Number(it.quantity) || 0) }))
    .filter((it: any) => it.materialId && it.quantity > 0)
  // 池非空时必须至少一行有效用料；池空（来源单没绑 BOM 快照/该产品无 BOM）允许只登记返回（料款应收 0）
  if (!items.length && candidates.value.length > 0) { ElMessage.warning('请至少填写一行有效用料（物料+数量）'); return }
  returnDlg.saving = true
  try {
    await request.post(`/outsource/order-delivery/${route.params.id}/return-back`, {
      factoryId: detail.value.factoryId, productId: detail.value.productMasterId,
      quantity: qty, defectQualityType: detail.value.qualityType,
      returnQualityType: returnForm.returnQualityType, inWarehouseId: returnForm.inWarehouseId,
      returnDate: returnForm.returnDate || undefined, remark: returnForm.remark, items
    })
    ElMessage.success('已登记返回（已生效）')
    returnDlg.visible = false
    await Promise.all([load(), loadReturns()])
  } catch (e: any) { ElMessage.error(e?.message || '登记返回失败') } finally { returnDlg.saving = false }
}

/** 撤销返回：库存/应收/成本对称逆回后删除该记录（与「撤销维修返回」同口径） */
async function revokeReturn(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认撤销返回「${row.code}」吗？将对称逆回：恢复在厂成品、扣回已回仓成品、回补实际用料、冲销赔料应收并反结转成本。`,
      '撤销返回', { type: 'warning' })
  } catch { return }
  try {
    await request.delete(`/outsource/order-delivery/return-back/${row.id}`)
    ElMessage.success('已撤销')
    await Promise.all([load(), loadReturns()])
  } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

onMounted(async () => { await load(); await loadReturns() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原「返回」按钮已删）；本页只读无操作 -->
  <PageShell :loading="loading" back-fallback="/outsource/return-order">
    <!-- 2026-09-27 用户口径：工厂修好送回时**就在本页登记返回**（不再去「加工返回单」叶子开单） -->
    <template #actions>
      <el-button v-if="canReturn" type="primary" @click="openReturn">登记返回</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">加工退货详情 — {{ detail.code || detail.legacyNo || ('记录 #' + (detail.id ?? '')) }}</span>
        </div>
      </template>

      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code || ('加工退货#' + (detail.id ?? '-')) }}</el-descriptions-item>
        <el-descriptions-item label="记录ID">{{ detail.id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ detail.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="加工厂">{{ detail.factoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联加工单">
          <el-button v-if="detail.orderCode && detail.orderId" type="primary" link @click="goOrder()">{{ detail.orderCode }}</el-button>
          <span v-else-if="detail.orderCode">{{ detail.orderCode }}</span>
          <span v-else style="color:var(--app-text-placeholder)">未关联（无单退回）</span>
        </el-descriptions-item>
        <el-descriptions-item label="产品">{{ (detail.productName || '-') + (detail.sku ? '（' + detail.sku + '）' : '') }}</el-descriptions-item>
        <el-descriptions-item label="退货规格">{{ specText(detail.qualityType) }}</el-descriptions-item>
        <el-descriptions-item label="退货数量">
          <span style="color:var(--app-color-danger);font-weight:500">{{ Math.abs(Number(detail.quantity || 0)) }}</span>
        </el-descriptions-item>
        <!-- 返回进度（2026-09-27）：口径同台账「退货/已返回」列 —— 无单退货才有（有单红冲不进在厂，没有返回一说） -->
        <el-descriptions-item v-if="isNoOrderNew && detail.settled" label="返回进度">
          <span :style="{ color: unreturnedQty > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontWeight: 500 }">
            {{ sentQty }} / {{ returnedQty }}
          </span>
          <span style="margin-left:6px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
            {{ unreturnedQty > 0 ? ('未返回 ' + unreturnedQty) : '已全部返回' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="扣减仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <!-- 2026-09-27（用户口径）：无单退货建单时解析/选定的 BOM 快照 —— 工厂修好送回时
             「加工返回单」按它限定可选的「实际用料」（无单红冲本身仍不拆料还料，见 P1-1） -->
        <el-descriptions-item label="BOM 快照">
          <span v-if="detail.bomSnapshotId">v{{ detail.bomVersion ?? '?' }}</span>
          <span v-else style="color:var(--app-text-placeholder)">未绑定（返回单里将没有可选的用料）</span>
        </el-descriptions-item>
        <el-descriptions-item label="建单时间">{{ detail.createTime ? String(detail.createTime).replace('T', ' ').slice(0, 19) : '-' }}</el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">落账明细</span></template>
      <!-- 2026-09-27 用户口径（详情页文案按真实流水分支）：无单退货（GTW-）与有单红冲（GTH-）落账口径不同，
           原先一律写「按 BOM 还料 + 冲减应付」⇒ 无单退货会显示根本不存在的动作（用户问到的困惑点）。
           判定见 isNoOrderNew（只看流水，兼容存量的「独立 DEFECT 单」）。 -->
      <el-alert v-if="detail.id && !detail.settled" type="info" :closable="false" show-icon
        :title="isNoOrderNew
          ? '尚未落账（草稿 / 已反审核）：审核后才会扣减成品，并把成品以「成品（加工退货）」形态转入加工厂委外仓（本口径不还料、不冲应付）。'
          : '尚未落账（草稿 / 已反审核）：审核后才会扣减成品、把 BOM 料还回工厂委外仓并冲减应付。'" />
      <template v-else-if="detail.settled">
        <p style="margin:0 0 8px;line-height:1.6;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
          ① 成品：已从「{{ detail.warehouseName || '-' }}」扣减
          <b style="color:var(--app-color-danger)">{{ Math.abs(Number(detail.quantity || 0)) }}</b> 件（{{ specText(detail.qualityType) }}）；
          <template v-if="!isNoOrderNew">
            ② 还料：按 BOM 还回工厂委外仓的物料如下<template v-if="detail.orderCode">，并回退该加工单的已收数量</template>。
          </template>
          <template v-else>
            ② 还料：<b>本单不还料</b>（无单退货不拆 BOM —— 拆料与退货时点无关，BOM 改过即拆错）。
            退回成品以「成品（加工退货）」形态挂在下方委外仓；工厂修好送回时在<b>本页「登记返回」</b>按
            <b>实际用料</b>扣料，并生成对加工厂的赔料应收。
          </template>
        </p>
        <el-table v-if="(detail.materials || []).length" :data="detail.materials" border stripe size="small">
          <el-table-column prop="materialName" label="还回物料" min-width="130" show-overflow-tooltip />
          <el-table-column label="品质" width="70" align="center">
            <template #default="{ row }">{{ row.qualityType === 'DEFECT' ? '不良' : '良品' }}</template>
          </el-table-column>
          <el-table-column label="数量" width="80" align="right"><template #default="{ row }">{{ row.quantity }}</template></el-table-column>
          <el-table-column prop="warehouseName" label="还入的委外仓" min-width="120" show-overflow-tooltip />
        </el-table>
        <p v-if="!isNoOrderNew && !(detail.materials || []).length" style="margin:6px 0 0;color:var(--app-text-placeholder);font-size:var(--app-font-xs)">
          无还料记录（包工包料产品 / 该产品无 BOM 快照 ⇒ 只扣成品、不还料）
        </p>
        <!-- 无单退货独有：成品落在哪个委外仓 + 什么形态（数据来自 OUTSOURCE_DEFECT_IN 流水） -->
        <el-table v-if="detail.outsourceIn" :data="[detail.outsourceIn]" border stripe size="small" style="margin-top:8px">
          <el-table-column prop="warehouseName" label="转入的委外仓" min-width="150" show-overflow-tooltip />
          <el-table-column label="形态" width="150" align="center">
            <template #default="{ row }"><el-tag type="danger" size="small">{{ StockFormLabel[row.stockForm] || row.stockForm || '-' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="数量" width="90" align="right">
            <template #default="{ row }">{{ Number(row.quantity || 0) > 0 ? '+' : '' }}{{ row.quantity }}</template>
          </el-table-column>
        </el-table>
        <p v-else-if="isNoOrderNew" style="margin:6px 0 0;color:var(--app-text-placeholder);font-size:var(--app-font-xs)">
          未查到委外仓入库流水（异常：无单退货审核后应有 PRODUCT_DEFECT 转移腿，请核对库存流水）
        </p>
        <p style="margin:12px 0 0;line-height:1.6;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
          <template v-if="!isNoOrderNew">
            ③ 应付冲减：
            <b :style="{ color: Number(detail.payableAmount) < 0 ? 'var(--app-color-success)' : 'var(--app-text-regular)' }">
              {{ Number(detail.payableAmount || 0).toFixed(2) }}
            </b>
            <span v-if="detail.payableStatus">（{{ detail.payableStatus === 'UNSETTLED' ? '未付款' : detail.payableStatus === 'SETTLED' ? '已付款' : detail.payableStatus }}）</span>
            <span style="color:var(--app-text-placeholder)"> —— 负数表示冲减已生成的加工应付。</span>
          </template>
          <template v-else>
            ③ 应付：<b>本次退货不产生应付</b>（无单口径不动应付）—— 料的账在本页「登记返回」时按实际用料结转。
          </template>
        </p>
      </template>
    </el-card>

    <!-- ============ 返回记录（2026-09-27 用户口径：加工返回不再单独开单 —— 就在本页登记/撤销） ============
         与「成品维修退货」详情页的「维修返回」记录同范式：登记即生效 + 逐条撤销（表内撤销走对称逆回后删除）。 -->
    <el-card v-if="isNoOrderNew && detail.settled" shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">返回记录</span>
          <span style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">
            工厂修好送回时点右上角「登记返回」；登记即生效（核销在厂 + 成品回仓 + 按实际用料扣料 + 赔料应收），错了可逐条撤销。
          </span>
        </div>
      </template>
      <el-table :data="returns" border stripe size="small" v-loading="returnsLoading">
        <el-table-column label="返回单号" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.code }}</template>
        </el-table-column>
        <el-table-column label="返回数量" width="90" align="right">
          <template #default="{ row }">{{ row.quantity }}</template>
        </el-table-column>
        <el-table-column label="回仓品质" width="90" align="center">
          <template #default="{ row }">{{ specText(row.returnQualityType) }}</template>
        </el-table-column>
        <el-table-column prop="inWarehouseName" label="回仓仓库" min-width="140" show-overflow-tooltip />
        <el-table-column label="实际用料料款" width="130" align="right">
          <template #default="{ row }">
            <span :title="'FIFO 结转的料款 = 对加工厂的赔料应收'">{{ Number(row.materialAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="returnDate" label="返回日期" width="110" />
        <el-table-column prop="createByName" label="登记人" width="90" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }"><el-button type="danger" link @click="revokeReturn(row)">撤销</el-button></template>
        </el-table-column>
        <template #empty>
          <span style="color:var(--app-text-placeholder)">尚未登记返回（工厂把货送回时点右上角「登记返回」）</span>
        </template>
      </el-table>
    </el-card>

    <!-- 登记返回弹窗：加工厂/产品/在厂规格由本记录带入（不可改，后端还会按来源单复核同厂同产品同规格） -->
    <el-dialog v-model="returnDlg.visible" title="登记返回（登记即生效）" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            登记即生效：<b>核销在厂成品</b>（加工厂委外仓的「成品（加工退货）」）→ <b>修好成品回我方仓</b> →
            按<b>实际用料</b>从委外仓扣料 → 料款生成对加工厂的<b>赔料应收</b>。填错可回本页逐条撤销。
          </span>
        </template>
      </el-alert>
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="来源退货单">{{ detail.code || ('加工退货#' + (detail.id ?? '-')) }}</el-descriptions-item>
        <el-descriptions-item label="加工厂">{{ detail.factoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ detail.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="在厂规格 / 未返回量">{{ specText(detail.qualityType) }} / {{ unreturnedQty }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="returnForm" label-width="120px" size="small">
        <el-form-item required label="返回数量">
          <el-input v-model="returnForm.quantity" type="number" placeholder="整数"
            @change="returnForm.quantity = Math.round(Number(returnForm.quantity) || 0)" />
        </el-form-item>
        <el-form-item required label="回仓仓库">
          <RemoteSelect v-model="returnForm.inWarehouseId" :fetch="fetchFinishedWarehouses"
            :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" placeholder="修好成品回仓仓库" style="width:100%" />
        </el-form-item>
        <el-form-item label="回仓品质">
          <el-select v-model="returnForm.returnQualityType" style="width:100%">
            <el-option v-for="o in specOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <!-- 实际用料只能从**本张加工退货单的 BOM 快照**里选（数量仍可超 BOM）；池空 ⇒ 允许只登记返回、不填料 -->
        <el-form-item :required="candidates.length > 0" label="实际用料明细">
          <div style="width:100%">
            <el-alert v-if="candidates.length === 0" type="warning" :closable="false" show-icon style="margin-bottom:8px">
              <template #title>
                <span style="font-size:var(--app-font-xs);line-height:1.5">
                  本退货单未绑定 BOM 快照、或该产品没有可用 BOM ⇒ 没有可选的用料。
                  本次可以<b>只登记返回、不填用料</b>（料款应收按 0）。
                </span>
              </template>
            </el-alert>
            <div v-else style="margin-bottom:8px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
              只能从<b>本加工退货单的 BOM 快照</b>里选；选料后自动带出默认用量（可改），数量可超 BOM。
            </div>
            <div v-for="(it, i) in returnForm.items" :key="i" style="display:flex;gap:8px;margin-bottom:8px">
              <el-select v-model="it.materialId" filterable clearable style="flex:1" placeholder="从 BOM 快照里选物料"
                :disabled="candidates.length === 0" @change="onPickMaterial(it)">
                <el-option v-for="c in candidates" :key="c.materialId" :value="c.materialId"
                  :disabled="isMaterialPicked(c.materialId, it)"
                  :label="(c.materialName || ('#' + c.materialId)) + (c.unit ? ('（' + c.unit + '）') : '') + ' · 单套用量 ' + c.perSetQuantity" />
              </el-select>
              <el-input v-model="it.quantity" type="number" placeholder="用量(可超BOM)" style="width:150px"
                @change="it.quantity = Math.round(Number(it.quantity) || 0)" />
              <el-button type="danger" link @click="removeItem(i)">删除</el-button>
            </div>
            <el-button type="primary" link :icon="'Plus'" :disabled="candidates.length === 0" @click="addItem">添加用料行</el-button>
          </div>
        </el-form-item>
        <el-form-item label="返回日期">
          <el-date-picker v-model="returnForm.returnDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="returnForm.remark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="returnDlg.saving" @click="submitReturn">登记返回</el-button>
      </template>
    </el-dialog>
  </PageShell>
</template>

<style scoped>/* 页头已统一到全局骨架（PageShell）；原 .p 局部样式已删除 */</style>
