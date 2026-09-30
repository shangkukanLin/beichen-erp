<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作 -->
  <PageShell title="销售换货单详情" back-fallback="/sale/exchange">
    <template #sub>
      <el-tag :type="statusTagType(head.status)">{{ statusLabel(head.status) }}</el-tag>
    </template>
    <template #actions>
      <template v-if="isDraft">
        <!-- 草稿：保存(主) + 审核 + 作废（2026-09-24 用户口径：草稿态就地编辑，不再跳独立编辑页） -->
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
        <el-button type="success" :loading="acting" @click="doAudit">审核</el-button>
        <el-button type="danger" :loading="acting" @click="doCancel">作废</el-button>
      </template>
      <el-button v-else-if="isAudited" type="warning" :loading="acting" @click="doUnAudit">反审核</el-button>
    </template>

    <el-card shadow="never">
      <!-- ============ 草稿：可编辑（字段/校验/payload 与 add.vue 完全一致） ============ -->
      <el-form v-if="isDraft" :model="form" label-width="var(--app-label-width)">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="换货单号">{{ head.code }}</el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- 客户与来源销售单在已有单据上锁定（与 add.vue 编辑态 :disabled 一致）：
                 换客户/换来源销售单等于换一张单，不是"改内容" -->
            <el-form-item label="客户">{{ customerName }}</el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源销售单">
              <el-button v-if="head.saleOrderId" type="primary" link @click="goSaleOrder(head.saleOrderId)">{{ head.saleOrderCode || '—' }}</el-button>
              <span v-else>{{ head.saleOrderCode || '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换货日期">
              <el-date-picker v-model="form.exchangeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换入仓(售后)" required>
              <RemoteSelect v-model="form.warehouseInId" add-route="/inventory/warehouse" :fetch="fetchAfterSaleWarehouses"
                label-key="warehouseName" placeholder="客户退回的货品入此仓（待整理）" style="width:100%" domain="warehouse" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换出仓(成品)" required>
              <RemoteSelect v-model="form.warehouseOutId" add-route="/inventory/warehouse" :fetch="fetchFinishedWarehouses"
                label-key="warehouseName" placeholder="发给客户的换出货品从此仓扣减" style="width:100%" domain="warehouse" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="制单人">{{ head.createByName || '—' }}</el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-descriptions v-else :column="3" border>
        <el-descriptions-item label="换货单号">{{ head.code }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ customerName }}</el-descriptions-item>
        <el-descriptions-item label="来源销售单">
          <el-button v-if="head.saleOrderId" type="primary" link @click="goSaleOrder(head.saleOrderId)">{{ head.saleOrderCode || '—' }}</el-button>
          <span v-else>{{ head.saleOrderCode || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="换入仓(售后)">
          <el-button v-if="head.warehouseInId" type="primary" link @click="goWarehouse(head.warehouseInId)">{{ warehouseInDisplayName }}</el-button>
          <span v-else>{{ warehouseInDisplayName }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="换出仓(成品)">
          <el-button v-if="head.warehouseOutId" type="primary" link @click="goWarehouse(head.warehouseOutId)">{{ warehouseOutDisplayName }}</el-button>
          <span v-else>{{ warehouseOutDisplayName }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="换货日期">{{ head.exchangeDate }}</el-descriptions-item>
        <!-- 收费（2026-09-21 逐产品）：单据级金额 = Σ明细行收费；chargeType 为空 = 各收款行类型不一致 ⇒ 显示"多类型" -->
        <el-descriptions-item label="收费（逐产品合计）">
          <template v-if="Number(head.chargeFlag) === 1 && Number(head.chargeAmount) > 0">
            <span style="color:#e6a23c;font-weight:600">{{ formatMoney(head.chargeAmount) }}</span>
            <span style="margin-left:6px;color:#909399">
              {{ ExchangeChargeTypeLabel[String(head.chargeType)] || (head.chargeType ? head.chargeType : '多类型') }}
            </span>
            <span style="margin-left:6px;color:#c0c4cc;font-size:var(--app-font-xs)">（逐产品见下表）</span>
          </template>
          <span v-else style="color:#c0c4cc">不收费</span>
        </el-descriptions-item>
        <el-descriptions-item label="收费说明" :span="2">{{ head.chargeReason || '—' }}</el-descriptions-item>
        <!-- 制单人（2026-09-23 用户口径：单据详情显示「制单人 + 审核人」） -->
        <el-descriptions-item label="制单人">{{ head.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ head.auditTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ head.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ head.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">
        换货明细（同品换货）
        <span v-if="isDraft" style="font-weight:normal;color:#909399;margin-left:8px">
          退回数量受「可退数量」约束；换出数量可不等（如退 2 换 1）；收费按行填写，填金额必须选类型
        </span>
      </el-divider>

      <!-- 草稿：可编辑（退回数量/换出数量·品质·单价/逐行收费/备注 可改；产品与退回原单价由来源销售单固定） -->
      <el-table v-if="isDraft" :data="items" border size="small">
        <el-table-column label="退回（客户退回，入成品仓待整理）" align="center">
          <el-table-column label="退回产品" width="138" show-overflow-tooltip>
            <template #default="{ row }">{{ productText(row) }}</template>
          </el-table-column>
          <el-table-column label="可退数量" width="64" align="right">
            <template #default="{ row }">{{ row.maxQuantity ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="退回数量" width="76">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="原单价" width="76" align="right">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="退回金额" width="80" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="换出（发给客户，从成品仓扣减）" align="center">
          <el-table-column label="换出数量" width="76">
            <template #default="{ row }">
              <el-input-number v-model="row.outQuantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="换出品质" width="78">
            <template #default="{ row }">
              <el-select v-model="row.outQualityType" size="small" style="width:100%">
                <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="换出单价" width="76">
            <template #default="{ row }">
              <el-input-number v-model="row.outUnitPrice" :min="0" :precision="2" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="换出金额" width="80" align="right">
            <template #default="{ row }">{{ formatMoney(row.outAmount) }}</template>
          </el-table-column>
        </el-table-column>
        <!-- 逐产品收费（2026-09-21）：本行产品向客户收取的金额 + 类型（金额 0 = 该产品不收费，类型必选） -->
        <el-table-column label="收费" width="146" align="center">
          <template #default="{ row }">
            <div style="display:flex;gap:4px">
              <el-input-number v-model="row.chargeAmount" :min="0" :precision="2" size="small" :controls="false"
                placeholder="金额" style="width:68px" />
              <el-select v-model="row.chargeType" size="small" placeholder="类型" clearable style="width:66px"
                :disabled="!(Number(row.chargeAmount) > 0)">
                <el-option v-for="o in chargeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="86">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
        </el-table-column>
      </el-table>

      <!-- 已审核 / 已作废：只读（原口径原样保留） -->
      <el-table v-else :data="items" border>
        <el-table-column label="退回（客户退回，入成品仓待整理）" align="center">
          <el-table-column label="退回产品" width="146" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button v-if="row.productId" type="primary" link @click="goProduct(row.productId)">{{ productText(row) }}</el-button>
              <span v-else>{{ productText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="quantity" label="退回数量" width="72" align="right" />
          <el-table-column label="原单价" width="80" align="right">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="退回金额" width="88" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="换出（发给客户，从成品仓扣减）" align="center">
          <el-table-column prop="outQuantity" label="换出数量" width="72" align="right" />
          <el-table-column label="换出品质" width="70" align="center">
            <template #default="{ row }">{{ ProductQualityTypeLabel[String(row.outQualityType)] || row.outQualityType || '-' }}</template>
          </el-table-column>
          <el-table-column label="换出单价" width="80" align="right">
            <template #default="{ row }">{{ formatMoney(row.outUnitPrice) }}</template>
          </el-table-column>
          <el-table-column label="换出金额" width="88" align="right">
            <template #default="{ row }">{{ formatMoney(row.outAmount) }}</template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="收费" width="146" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="Number(row.chargeAmount) > 0">
              <span style="color:#e6a23c;font-weight:600">{{ formatMoney(row.chargeAmount) }}</span>
              <span style="margin-left:4px;color:#909399">{{ ExchangeChargeTypeLabel[String(row.chargeType)] || row.chargeType || '' }}</span>
            </template>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" width="86" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>

    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, onActivated, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import {
  DocStatus, DocStatusLabel,
  ExchangeChargeType, ExchangeChargeTypeLabel,
  ProductQualityType, ProductQualityTypeLabel,
  WarehouseType, SALE_EXCHANGE_DIRTY_KEY,
} from '@/api/enums'
import PageShell from '@/components/PageShell.vue'
import {
  getSaleExchange, updateSaleExchange, auditSaleExchange, unAuditSaleExchange, cancelSaleExchange,
} from '@/api/sale'
import { invalidate } from '@/utils/dataFreshness'

/**
 * 销售换货单详情（2026-09-24 用户口径：草稿态就地可编辑，列表不再给「编辑」）
 *
 * 结构对齐其它单据详情：`head` = 只读快照，`form`/`items` = 可编辑副本（草稿态才建）。
 * 草稿分支的字段、校验、payload 与 add.vue **完全一致**（换出仓 + 换入仓 + 退回/换出明细 + 逐产品收费）。
 * 两处刻意锁定（与 add.vue 编辑态一致）：
 *  · 客户 / 来源销售单 只读 —— add.vue 在编辑态对两者 `:disabled`（换客户 = 换一张单）；
 *  · 明细的「退回产品」与「原单价」只读 —— 同品换货下产品由来源销售单固定，单价取原单价格（可改的是换出侧）。
 */
const route = useRoute()
const router = useRouter()
const acting = ref(false)
const saving = ref(false)

const head = reactive({
  code: '',
  saleOrderId: null as number | null,
  saleOrderCode: '',
  customerId: null as number | null,
  warehouseInId: null as number | null,
  warehouseOutId: null as number | null,
  exchangeDate: '',
  status: DocStatus.DRAFT as string,
  totalAmount: 0,
  chargeFlag: 0,
  chargeType: '',
  chargeAmount: 0,
  chargeReason: '',
  createByName: '',
  auditorName: '',
  auditTime: '',
  createTime: '',
  remark: '',
})
const items = ref<any[]>([])
/** 可编辑副本（白名单：单号/客户/来源销售单/状态/金额合计/审核人 不回传，金额由后端按明细重算） */
const form = reactive({
  exchangeDate: '',
  warehouseInId: null as number | null,
  warehouseOutId: null as number | null,
  chargeReason: '',
  remark: '',
})

const isDraft = computed(() => head.status === DocStatus.DRAFT)
const isAudited = computed(() => head.status === DocStatus.AUDITED)

const qualityOptions = computed(() =>
  [ProductQualityType.A, ProductQualityType.B, ProductQualityType.C, ProductQualityType.DEFECT].map((v) => ({
    value: v, label: ProductQualityTypeLabel[v] || v
  }))
)
/** 收费类型（对应后端 sale/common/ExchangeChargeType；方向：向客户收取） */
const chargeTypeOptions = computed(() =>
  Object.values(ExchangeChargeType).map((v) => ({ value: v, label: ExchangeChargeTypeLabel[v] || v }))
)

// ===== 字典：客户 / 仓库（head 为实体，不含冗余名称，本地翻译展示） =====
const customers = ref<{ id: number; name?: string; customerName?: string }[]>([])
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
async function loadCustomers() { try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] } }
const customerName = computed(() => {
  const c = customers.value.find((x) => x.id === head.customerId)
  return c ? (c.name || c.customerName) : '—'
})
// 2026-09-20（F7-177）：详情只回显换入/换出 2 个仓库名 ⇒ 按 id 单取（原为 pageSize=500 全量拉 + 前端 find）
const warehouseInDisplayName = ref('—')
const warehouseOutDisplayName = ref('—')
async function loadWarehouseNames() {
  const din = head.warehouseInId
  const dout = head.warehouseOutId
  if (din) {
    try { const w: any = await request.get(`/warehouse/${din}`); warehouseInDisplayName.value = w?.warehouseName || w?.name || '—' }
    catch { warehouseInDisplayName.value = '—' }
  } else warehouseInDisplayName.value = '—'
  if (dout) {
    try { const w: any = await request.get(`/warehouse/${dout}`); warehouseOutDisplayName.value = w?.warehouseName || w?.name || '—' }
    catch { warehouseOutDisplayName.value = '—' }
  } else warehouseOutDisplayName.value = '—'
}

/** 换入仓(退回品) 与 换出仓(良品) **都只能是自有成品仓**（2026-09-16 方案 A，与 add.vue 完全一致：
 *  同仓内按品质分行 ⇒ 允许两者相同） */
const fetchAfterSaleWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })
const fetchFinishedWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })

function statusLabel(s: string) { return DocStatusLabel[String(s)] ?? '未知' }
function statusTagType(s: string) {
  if (s === DocStatus.AUDITED) return 'success'
  if (s === DocStatus.CANCELLED) return 'info'
  return 'warning'
}
function formatMoney(v: any) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
/** 明细表「退回产品」展示：有 SKU 时用 `SKU | 名称`（2026-09-21：不再单列 SKU，信息不丢） */
function productText(row: any) {
  const name = row?.productName || ''
  return row?.sku ? `${row.sku} | ${name}` : name
}

function resetForm() {
  form.exchangeDate = head.exchangeDate ? String(head.exchangeDate).slice(0, 10) : ''
  form.warehouseInId = head.warehouseInId
  form.warehouseOutId = head.warehouseOutId
  form.chargeReason = head.chargeReason || ''
  form.remark = head.remark || ''
}

async function loadDetail(id: number) {
  const res: any = await getSaleExchange(id)
  const h = res?.head || {}
  Object.assign(head, {
    code: h.code || '',
    saleOrderId: h.saleOrderId ?? null,
    saleOrderCode: h.saleOrderCode || '',
    customerId: h.customerId ?? null,
    warehouseInId: h.warehouseInId ?? null,
    warehouseOutId: h.warehouseOutId ?? null,
    exchangeDate: h.exchangeDate || '',
    status: h.status || DocStatus.DRAFT,
    totalAmount: Number(h.totalAmount || 0),
    chargeFlag: Number(h.chargeFlag || 0),
    chargeType: h.chargeType || '',
    chargeAmount: Number(h.chargeAmount || 0),
    chargeReason: h.chargeReason || '',
    createByName: h.createByName || '',
    auditorName: h.auditorName || '',
    auditTime: h.auditTime || '',
    createTime: h.createTime || '',
    remark: h.remark || '',
  })
  const its = res?.items || []
  if (String(head.status) === DocStatus.DRAFT) {
    resetForm()
    // 明细回填（数值显式转 Number，避免输入框拿到字符串）
    items.value = its.map((it: any) => ({
      ...it,
      quantity: Number(it.quantity),
      unitPrice: Number(it.unitPrice ?? 0),
      outQuantity: Number(it.outQuantity ?? it.quantity),
      outUnitPrice: Number(it.outUnitPrice ?? it.unitPrice ?? 0),
      chargeAmount: Number(it.chargeAmount || 0),
      chargeType: it.chargeType || ''
    }))
    // 2026-09-21（去掉批量类型后）：旧草稿里"靠单据级类型兜底"的行显性化到行上 —— 否则编辑这类旧单
    // 会被"逐行必选类型"校验拦住、存不回去（单据级类型本就是按各明细一致派生的，落回无损）。
    const docChargeType = head.chargeType || ''
    if (docChargeType) items.value.forEach((it: any) => { if (!it.chargeType) it.chargeType = docChargeType })
  } else {
    items.value = its
  }
  await loadWarehouseNames()
}

function goSaleOrder(id?: number | null) { if (id) router.push(`/inventory/sale/detail/${id}`) }
function goWarehouse(id?: number | null) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goProduct(id?: number | null) { if (id) router.push(`/product/detail/${id}`) }

/** 保存（与 add.vue 同一套校验与 payload；后端 update 自带「只有草稿可编辑」守卫） */
async function doSave() {
  if (!form.warehouseInId) { ElMessage.warning('请选择换入仓(成品仓)'); return }
  if (!form.warehouseOutId) { ElMessage.warning('请选择换出仓(成品仓)'); return }
  const its = items.value.filter((i) => Number(i.quantity) > 0)
  if (its.length === 0) { ElMessage.warning('请至少录入一条换货明细'); return }
  for (const it of its) {
    // 可退量只约束退回数量（换出属正常出库，不受限）
    if (it.maxQuantity != null && Number(it.quantity) > it.maxQuantity) {
      ElMessage.warning(`产品「${it.productName}」退回数量(${it.quantity})超过可退数量(${it.maxQuantity})`)
      return
    }
    if (!(Number(it.outQuantity) > 0)) {
      ElMessage.warning(`产品「${it.productName}」换出数量必须大于 0`)
      return
    }
  }
  // 收费（2026-09-21 逐产品口径）：填了金额的行必须**自己**选了类型（后端会再逐行校验一次）
  for (const it of its) {
    if (Number(it.chargeAmount) > 0 && !it.chargeType) {
      ElMessage.warning(`产品「${it.productName || it.productId}」已填收费金额，请选择收费类型`)
      return
    }
  }
  const chargeTotal = its.reduce((s, i) => s + (Number(i.chargeAmount) || 0), 0)
  saving.value = true
  try {
    await updateSaleExchange(Number(route.params.id), {
      saleOrderId: head.saleOrderId, saleOrderCode: head.saleOrderCode, customerId: head.customerId,
      warehouseInId: form.warehouseInId, warehouseOutId: form.warehouseOutId,
      exchangeDate: form.exchangeDate,
      // 收费：单据级金额由后端按 Σ明细 回写（传 0）；单据级类型不再从界面下发（后端按明细派生）
      chargeFlag: chargeTotal > 0 ? 1 : 0,
      chargeType: '',
      chargeAmount: 0,
      chargeReason: chargeTotal > 0 ? (form.chargeReason || '') : '',
      remark: form.remark,
      items: its.map((i) => ({
        // 退回侧
        saleOrderItemId: i.saleOrderItemId, productId: i.productId, productName: i.productName,
        quantity: i.quantity, unitPrice: i.unitPrice,
        // 换出侧（只支持同品：产品即退回产品；数量可不等如退2换1）
        outQuantity: i.outQuantity, outUnitPrice: i.outUnitPrice, outQualityType: i.outQualityType,
        // 逐产品收费：金额 > 0 才收费，且类型必须是**本行自己**选的
        chargeAmount: Number(i.chargeAmount) || 0,
        chargeType: Number(i.chargeAmount) > 0 ? (i.chargeType || '') : '',
        chargeReason: form.chargeReason || '',
        remark: i.remark
      }))
    })
    ElMessage.success('已保存')
    invalidate('saleExchange')
    await loadDetail(Number(route.params.id))
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '保存失败')
  } finally { saving.value = false }
}

async function doAudit() {
  const charged = Number(head.chargeFlag) === 1 && Number(head.chargeAmount) > 0
  const chargeTip = charged
    ? `\n并生成一条向客户收取的费用应收 ${formatMoney(head.chargeAmount)} 元（台账单号 ${head.code}-FEE）。`
    : ''
  try {
    await ElMessageBox.confirm(`确认审核「${head.code}」？审核后退回货品入成品仓(待整理)，换出货品从成品仓扣减。${chargeTip}`, '审核确认', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await auditSaleExchange(Number(route.params.id))
    ElMessage.success('已审核')
    invalidate('saleExchange')
    loadDetail(Number(route.params.id))
  } finally { acting.value = false }
}
async function doUnAudit() {
  try {
    await ElMessageBox.confirm(`确认反审核「${head.code}」？将回滚退回与换出的库存。`, '提示', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await unAuditSaleExchange(Number(route.params.id))
    ElMessage.success('已反审核')
    invalidate('saleExchange')
    loadDetail(Number(route.params.id))
  } finally { acting.value = false }
}
async function doCancel() {
  try {
    await ElMessageBox.confirm(`确认作废换货单「${head.code}」？作废后单据留痕，不可恢复。`, '提示', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await cancelSaleExchange(Number(route.params.id))
    ElMessage.success('已作废')
    invalidate('saleExchange')
    router.push('/sale/exchange')
  } finally { acting.value = false }
}

// 字典类只需加载一次
onMounted(() => { loadCustomers() })
// 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
// ⚠️ 2026-09-20（F7-148）：单据数据**只**挂 onActivated ⇒ 依赖"本路由一定在 keep-alive 内"
// （当前成立）。若将来把本组件加进 layout 的 :exclude 或改用非 keep-alive 布局，
// onActivated 不再触发 ⇒ 页面永久空白且无报错线索，届时须同时补 onMounted。
onActivated(() => { loadDetail(Number(route.params.id)) })
</script>

<style scoped>
/* 页头/操作区已统一到全局骨架（PageShell + styles/page.css） */
:deep(.el-card__body) { padding: 16px; }
</style>
