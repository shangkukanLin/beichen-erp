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
        <!-- 2026-10-01（第 1 步）：补动作级权限码 sale:exchange:*（此前只有页级码 sale:exchange） -->
        <el-button v-perm="'sale:exchange:audit'" type="success" :loading="acting" @click="doAudit">审核</el-button>
        <el-button v-perm="'sale:exchange:cancel'" type="danger" :loading="acting" @click="doCancel">作废</el-button>
      </template>
      <el-button v-else-if="isAudited" v-perm="'sale:exchange:unaudit'" type="warning" :loading="acting" @click="doUnAudit">反审核</el-button>
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
          退回数量上限 = min(销售数量, 库存数量)，见「库存数量」列；换出数量可不等（如退 2 换 1）；收费按行填写，填金额必须选类型
        </span>
      </el-divider>

      <!-- 草稿：可编辑（退回数量/换出数量·品质·单价/逐行收费/备注 可改）。
           2026-10-01（F8 补充，用户口径「一并放开」）：**无来源换货**（head.saleOrderId 为空）时，
           「退回产品」也可改（下拉远程搜）、并可增删明细行 —— 原设计"产品由来源销售单固定"只对**有来源**成立；
           有来源时仍保持只读（产品来自销售明细，换了就等于换一张单）。 -->
      <div v-if="isDraft && !head.saleOrderId" style="margin-bottom:8px">
        <el-button type="primary" size="small" @click="addItem">添加明细</el-button>
        <span style="margin-left:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
          未关联销售单（无单换货）：可增删明细并更换产品；已经审核过的单据不再可编辑
        </span>
      </div>
      <el-table v-if="isDraft" :data="items" border size="small">
        <el-table-column label="退回（客户退回，入成品仓待整理）" align="center">
          <el-table-column label="退回产品" width="138" show-overflow-tooltip>
            <template #default="{ row }">
              <!-- 无来源换货：可更换产品（远程搜，可输 SKU）；有来源：产品由销售明细固定，只读 -->
              <el-select v-if="!head.saleOrderId" v-model="row.productId" placeholder="选择产品（可输SKU）"
                size="small" filterable remote :remote-method="loadProducts" style="width:100%"
                @change="(v: number) => onProductChange(v, row)">
                <el-option v-for="m in productOptions" :key="m.id" :label="productLabel(m)" :value="m.id" />
              </el-select>
              <span v-else>{{ productText(row) }}</span>
            </template>
          </el-table-column>
          <!-- 2026-10-01（用户口径）：原「可退数量」改为「库存数量」= 该**换出仓** + 该产品 +
               该**换出品质**的库存（stockForm=MATERIAL，与审核换出扣减口径一致）；
               退回数量的 :max = min(来源销售单数量, 库存数量)；无来源（无单换货）⇒ :max = 库存数量。
               与新增页 add.vue 同款（详情页这列此前恒为「-」，故一并接上实时库存）。 -->
          <el-table-column label="库存数量" width="74" align="right">
            <template #default="{ row }">
              <span v-if="row.stock !== undefined" :style="{ color: Number(row.stock) <= 0 ? 'red' : '' }">{{ row.stock }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="退回数量" width="86">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" size="small" :controls="false" :max="row.quantityLimit" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="原单价" width="86" align="right">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="退回金额" width="80" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="换出（发给客户，从成品仓扣减）" align="center">
          <el-table-column label="换出数量" width="86">
            <template #default="{ row }">
              <el-input-number v-model="row.outQuantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="换出品质" width="88">
            <template #default="{ row }">
              <el-select v-model="row.outQualityType" size="small" style="width:100%" @change="() => refreshStock(row)">
                <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="换出单价" width="86">
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
        <!-- 无来源时可删行（有来源的明细来自销售单，删行会让"可退/可换量"对不上，故不提供） -->
        <el-table-column v-if="!head.saleOrderId" label="操作" width="52" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeItem($index)">删除</el-button>
          </template>
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
          <el-table-column prop="quantity" label="退回数量" width="82" align="right" />
          <el-table-column label="原单价" width="90" align="right">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="退回金额" width="88" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="换出（发给客户，从成品仓扣减）" align="center">
          <el-table-column prop="outQuantity" label="换出数量" width="82" align="right" />
          <el-table-column label="换出品质" width="80" align="center">
            <template #default="{ row }">{{ ProductQualityTypeLabel[String(row.outQualityType)] || row.outQualityType || '-' }}</template>
          </el-table-column>
          <el-table-column label="换出单价" width="90" align="right">
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
      </el-table>

    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, onActivated, reactive, ref, computed, watch } from 'vue'
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
  // 2026-10-01（用户口径）：详情页草稿态也要「库存数量」+ 上限 = min(来源销售单数量, 库存)
  getSaleExchangeSaleOrderItems,
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

/**
 * 无来源换货的手工选品（2026-10-01 F8 补充）：有来源时明细由销售单带出，本列表不用。
 * 与 add.vue 同款接口与展示格式（`/product/page`，展示「SKU | 名称」，可输 SKU 远程搜）。
 */
const productOptions = ref<any[]>([])
function productLabel(m: any) {
  return m?.sku ? `${m.sku} | ${m.name || m.productName || ''}` : (m?.name || m?.productName || '')
}
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
    // 2026-10-01（用户口径）：来源「销售数量」不在明细里 ⇒ 按 saleOrderItemId 从来源单反查，
    // 再补齐「库存数量」与上限（依赖换出仓，故必须在 resetForm() 之后、明细回填之后）
    const srcMap = await loadSourceQtyMap()
    if (srcMap.size) {
      items.value.forEach((it: any) => {
        const q = srcMap.get(Number(it.saleOrderItemId))
        if (q != null) it.sourceQty = q
      })
    }
    await Promise.all(items.value.map((it: any) => refreshStock(it)))
  } else {
    items.value = its
  }
  await loadWarehouseNames()
}

function goSaleOrder(id?: number | null) { if (id) router.push(`/inventory/sale/detail/${id}`) }
function goWarehouse(id?: number | null) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goProduct(id?: number | null) { if (id) router.push(`/product/detail/${id}`) }

// ===== 无来源换货的明细增删与选品（2026-10-01 F8 补充，字段口径与 add.vue 完全一致）=====

/** 添加一行明细：退回侧 + 换出侧配对；退回品质由后端固定 PENDING，故明细不含退回品质 */
function addItem() {
  const row: any = {
    saleOrderItemId: null,
    productId: null,
    productName: '',
    sku: '',
    quantity: 1,
    // 无来源 ⇒ 没有来源数量，上限 = 换出仓 + 该产品 + 该换出品质的库存（见 refreshStock）
    sourceQty: null,
    unitPrice: 0,
    outQuantity: 1,
    outQualityType: ProductQualityType.A,
    outUnitPrice: 0,
    chargeAmount: 0,
    chargeType: '',
  }
  items.value.push(row)
  refreshStock(row)
}

function removeItem(i: number) { items.value.splice(i, 1) }

/** 产品候选：远程搜（可输 SKU），与 add.vue / 采购换货同款接口 */
async function loadProducts(query?: string) {
  const params: any = { pageSize: 100 }
  if (query) params.keyword = query
  const res: any = await request.get('/product/page', { params })
  productOptions.value = res?.records || []
}

/** 选中产品后带出 SKU/名称与参考单价（销售侧取标准售价 price），同步换出侧默认值，并刷新库存与上限 */
async function onProductChange(val: number, row: any) {
  const m = productOptions.value.find((x: any) => x.id === val)
  if (!m) return
  row.productName = m.name || ''
  row.sku = m.sku || ''
  const price = Number(m.price ?? 0)
  row.unitPrice = price
  if (!row.outUnitPrice) row.outUnitPrice = price
  if (!row.outQuantity) row.outQuantity = row.quantity
  await refreshStock(row)
}

// ===== 2026-10-01（用户口径）：草稿态实时「库存数量」+ 退回数量上限 =====
// 上限 = min(来源销售单数量, 库存数量)；无来源（无单换货）⇒ 上限 = 库存数量（与 add.vue 同款）。
// ⚠️ 库存按 stockForm=MATERIAL 求和：审核换出扣减走的 changeStock 重载缺省形态即 MATERIAL。
/** 来源销售明细的「销售数量」映射（saleOrderItemId → 数量）；无来源或取不到时为空 Map */
async function loadSourceQtyMap(): Promise<Map<number, number>> {
  const map = new Map<number, number>()
  if (!head.saleOrderId) return map
  try {
    const rows: any[] = await getSaleExchangeSaleOrderItems(head.saleOrderId)
    for (const r of rows || []) {
      if (r?.saleOrderItemId != null) map.set(Number(r.saleOrderItemId), Number(r.canExchange ?? 0))
    }
  } catch { /* 取不到来源数量 ⇒ 上限退化为「库存数量」，不阻断页面 */ }
  return map
}

/** 刷新某行的「库存数量」与数量上限（依赖 换出仓 + 产品 + 换出品质） */
async function refreshStock(row: any) {
  row.stock = undefined
  row.quantityLimit = undefined
  if (!row.productId || !form.warehouseOutId) return
  try {
    const p: any = { warehouseId: form.warehouseOutId, productId: row.productId, pageSize: 500 }
    if (row.outQualityType) p.qualityType = row.outQualityType
    const res: any = await request.get('/warehouse/stock/page', { params: p })
    const arr: any[] = res?.records || []
    const stock = arr
      .filter((x: any) => !x.stockForm || x.stockForm === 'MATERIAL')
      .reduce((s: number, x: any) => s + (Number(x.quantity) || 0), 0)
    row.stock = stock
    row.quantityLimit = row.sourceQty != null ? Math.min(Number(row.sourceQty), stock) : stock
  } catch { row.stock = undefined; row.quantityLimit = undefined }
}

/** 切换「换出仓」⇒ 库存按仓取，整表上限失效 ⇒ 全表重算 */
watch(() => form.warehouseOutId, () => { items.value.forEach((it: any) => { refreshStock(it) }) })

/** 保存（与 add.vue 同一套校验与 payload；后端 update 自带「只有草稿可编辑」守卫） */
async function doSave() {
  if (!form.warehouseInId) { ElMessage.warning('请选择换入仓(成品仓)'); return }
  if (!form.warehouseOutId) { ElMessage.warning('请选择换出仓(成品仓)'); return }
  const its = items.value.filter((i) => Number(i.quantity) > 0)
  if (its.length === 0) { ElMessage.warning('请至少录入一条换货明细'); return }
  for (const it of its) {
    // 无来源时产品由用户手选 ⇒ 必填（有来源时产品来自销售明细，必然有值；后端也会兜底拒绝）
    if (!it.productId) { ElMessage.warning('请为每一行选择退回产品'); return }
    // 2026-10-01（用户口径）：上限 = min(来源销售单数量, 换出仓+产品+换出品质的库存数量)；
    // 无来源（无单换货）时上限 = 库存 ⇒ 两种情形都校验（换出数量不受限）。
    const lim = (it as any).quantityLimit
    if (lim != null && Number(it.quantity) > lim) {
      const how = (it as any).sourceQty != null ? '取「销售数量」与「库存数量」中的较小值' : '该仓库该品质的库存数量'
      ElMessage.warning(`产品「${it.productName}」退回数量(${it.quantity})超过上限(${lim}，${how})`)
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
