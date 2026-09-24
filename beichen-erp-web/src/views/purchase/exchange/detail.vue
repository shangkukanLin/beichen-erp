<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作 -->
  <PageShell title="采购换货单详情" :loading="loading" back-fallback="/inventory/purchase-exchange">
    <template #sub>
      <el-tag :type="statusTagType(head.status)">{{ statusLabel(head.status) }}</el-tag>
    </template>
    <template #actions>
      <template v-if="isDraft">
        <!-- 草稿：保存(主) + 审核 + 作废（2026-09-24 用户口径：草稿态就地编辑，不再跳独立编辑页） -->
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
        <el-button v-perm="'purchase:exchange:audit'" type="success" :loading="acting" @click="doAudit">审核</el-button>
        <el-button v-perm="'purchase:exchange:cancel'" type="danger" :loading="acting" @click="doCancel">作废</el-button>
      </template>
      <el-button v-else-if="isAudited" v-perm="'purchase:exchange:unaudit'" type="warning" :loading="acting" @click="doUnAudit">反审核</el-button>
    </template>

    <el-card shadow="never">
      <!-- ============ 草稿：可编辑（字段/校验/payload 与 add.vue 完全一致） ============ -->
      <template v-if="isDraft">
        <el-form :model="form" label-width="var(--app-label-width)">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="换货单号">{{ head.code }}</el-form-item>
            </el-col>
            <el-col :span="8">
              <!-- 供货商与来源采购单在已有单据上锁定（与 add.vue 编辑态 :disabled 一致）：
                   换供货商/换来源采购单等于换一张单，不是"改内容" -->
              <el-form-item label="供货商">
                <span>{{ head.supplierName || '—' }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="来源采购单">
                <el-button v-if="head.purchaseOrderId" type="primary" link @click="goPurchaseOrder(head.purchaseOrderId)">{{ head.purchaseOrderCode || '—' }}</el-button>
                <span v-else>未关联（无单换货，明细手工录入）</span>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="换货日期" required>
                <el-date-picker v-model="form.exchangeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="退回出库仓" required>
                <RemoteSelect v-model="form.warehouseOutId" :fetch="fetchFinishedWarehouses"
                  label-key="warehouseName" placeholder="退给供货商，从我方仓扣减" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="换入入库仓" required>
                <RemoteSelect v-model="form.warehouseInId" :fetch="fetchFinishedWarehouses"
                  label-key="warehouseName" placeholder="换回良品入我方仓（可与退回仓相同）" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="制单人">{{ head.createByName || '—' }}</el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-divider content-position="left">
          换货明细
          <span style="font-weight:normal;color:#909399;margin-left:8px">
            同品换货：退回默认「不良品」出库、换入默认「A规」入库；换入单价高于退回单价即为加价换新（差额进应付）
          </span>
        </el-divider>
        <div style="margin-bottom:8px">
          <!-- 无单换货可手工加行；关联采购单时明细已按来源带出，可增删行 -->
          <el-button v-if="!head.purchaseOrderId" type="primary" size="small" @click="addItem">添加明细</el-button>
          <span style="margin-left:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
            {{ head.purchaseOrderId
              ? '明细已按来源采购单带出，可增删行；退回数量受「可换数量」约束'
              : '未关联采购单（无单换货）：请手工添加并选择产品；能否出库以审核时的库存校验为准' }}
          </span>
        </div>
        <el-table :data="items" border size="small" max-height="380">
          <!-- ===== 退回侧：退回供货商，从我方仓出库 ===== -->
          <el-table-column label="退回（退给供货商）" align="center">
            <el-table-column label="退回产品" width="128" show-overflow-tooltip>
              <template #default="{ row }">
                <!-- 无单换货：手工选产品（可输 SKU 远程搜）；关联采购单：产品由采购明细带出，只读。
                     已有单据**不提供"换产品"**：换产品等于换明细行，请删除后重新添加（后端也不支持改 productId） -->
                <el-select v-if="!head.purchaseOrderId" v-model="row.productId" placeholder="选择产品（可输SKU）"
                  size="small" filterable remote :remote-method="loadProducts" style="width:100%"
                  @change="(v: number) => onProductChange(v, row)">
                  <el-option v-for="m in productOptions" :key="m.id" :label="productLabel(m)" :value="m.id" />
                </el-select>
                <span v-else>{{ productText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可换数量" width="64" align="right">
              <template #default="{ row }">{{ row.maxQuantity ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="退回数量" width="78">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="退回品质" width="78">
              <template #default="{ row }">
                <el-select v-model="row.qualityType" size="small" style="width:100%">
                  <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="退回单价" width="78">
              <template #default="{ row }">
                <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" :controls="false" style="width:100%" />
              </template>
            </el-table-column>
          </el-table-column>

          <!-- ===== 换入侧：供货商换回，入我方仓 ===== -->
          <el-table-column label="换入（供货商换回）" align="center">
            <el-table-column label="换入数量" width="78">
              <template #default="{ row }">
                <el-input-number v-model="row.inQuantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="换入品质" width="78">
              <template #default="{ row }">
                <el-select v-model="row.inQualityType" size="small" style="width:100%">
                  <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="换入单价" width="78">
              <template #default="{ row }">
                <el-input-number v-model="row.inUnitPrice" :min="0" :precision="2" size="small" :controls="false" style="width:100%" />
              </template>
            </el-table-column>
          </el-table-column>

          <!-- 逐产品付费（2026-09-21）：本行产品付给供货商的金额 + 类型（金额 0 = 该产品不付费，类型必选） -->
          <el-table-column label="付费" width="146" align="center">
            <template #default="{ row }">
              <div style="display:flex;gap:4px">
                <el-input-number v-model="row.chargeAmount" :min="0" :precision="2" size="small" :controls="false"
                  placeholder="金额" style="width:68px" />
                <el-select v-model="row.chargeType" size="small" placeholder="类型" clearable style="width:66px"
                  :disabled="!(Number(row.chargeAmount) > 0)">
                  <el-option v-for="o in payTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="88">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
          </el-table-column>
          <el-table-column label="操作" width="52" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="items.length" style="margin-top:8px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
          合计：退回 {{ totalReturnAmount.toFixed(2) }} ｜ 换入 {{ totalInAmount.toFixed(2) }} ｜
          应付净额 <b style="color:var(--app-text-primary)">{{ (totalInAmount - totalReturnAmount).toFixed(2) }}</b>
        </div>

        <el-row :gutter="16" style="margin-top:8px">
          <el-col :span="6">
            <el-form-item label="付费合计（自动）">
              <span style="font-weight:600;color:#e6a23c">{{ chargeTotal.toFixed(2) }}</span>
              <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">= Σ 明细行付费</span>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="付费说明（整单）">
              <el-input v-model="form.chargeReason" placeholder="选填，如：换货服务费 / 补差价（落到该单付费台账备注）" />
            </el-form-item>
          </el-col>
        </el-row>
        <div v-if="chargeTotal > 0" style="margin:0 0 10px 110px;font-size:var(--app-font-xs);color:var(--app-color-warning)">
          付费方向：<b>我方付给供货商</b> ⇒ 审核后额外生成一条正向应付（我方欠供货商 +{{ chargeTotal.toFixed(2) }}）
        </div>
      </template>

      <!-- ============ 已审核 / 已作废：只读（原口径原样保留） ============ -->
      <template v-else>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="换货单号">{{ head.code }}</el-descriptions-item>
          <el-descriptions-item label="供货商">{{ head.supplierName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="来源采购单">
            <el-button v-if="head.purchaseOrderId" type="primary" link @click="goPurchaseOrder(head.purchaseOrderId)">{{ head.purchaseOrderCode || '—' }}</el-button>
            <!-- 无单换货（2026-09-21 起允许不关联采购单）：明细为手工录入，无可换量上限 -->
            <span v-else>未关联（无单换货，明细手工录入）</span>
          </el-descriptions-item>
          <el-descriptions-item label="退回出库仓">
            <el-button v-if="head.warehouseOutId" type="primary" link @click="goWarehouse(head.warehouseOutId)">{{ warehouseOutName }}</el-button>
            <span v-else>{{ warehouseOutName }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="换入入库仓">
            <el-button v-if="head.warehouseInId" type="primary" link @click="goWarehouse(head.warehouseInId)">{{ warehouseInName }}</el-button>
            <span v-else>{{ warehouseInName }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="换货日期">{{ head.exchangeDate }}</el-descriptions-item>
          <el-descriptions-item label="退回金额（冲减应付）">
            <span style="color:#f56c6c;font-weight:600">-{{ formatMoney(head.totalReturnAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="换入金额（新增应付）">
            <span style="color:#67c23a;font-weight:600">{{ formatMoney(head.totalInAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="应付净额（差价）">
            <span style="font-weight:600">{{ formatMoney(Number(head.totalInAmount || 0) - Number(head.totalReturnAmount || 0)) }}</span>
          </el-descriptions-item>
          <!-- 付费（2026-09-21 逐产品）：单据级金额 = Σ明细行付费；chargeType 为空 = 各付费行类型不一致 ⇒ 显示"多类型" -->
          <el-descriptions-item label="付费（逐产品合计，我方付给供货商）">
            <template v-if="Number(head.chargeFlag) === 1 && Number(head.chargeAmount) > 0">
              <span style="color:#e6a23c;font-weight:600">{{ formatMoney(head.chargeAmount) }}</span>
              <span style="margin-left:6px;color:#909399">
                {{ PurchaseChargeTypeLabel[String(head.chargeType)] || (head.chargeType ? head.chargeType : '多类型') }}
              </span>
              <span style="margin-left:6px;color:#c0c4cc;font-size:var(--app-font-xs)">（逐产品见下表「付费」列）</span>
            </template>
            <span v-else>不付费</span>
          </el-descriptions-item>
          <el-descriptions-item label="付费说明（整单）">{{ head.chargeReason || '—' }}</el-descriptions-item>
          <!-- 制单人（2026-09-23 用户口径：单据详情显示「制单人 + 审核人」） -->
          <el-descriptions-item label="制单人">{{ head.createByName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ head.auditTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ head.createTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ head.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">换货明细（同品换货）</el-divider>
        <!-- 2026-09-21（UI 优化）：原来 12 列、合计 1420px ⇒ 横向滚动 472px。现：①SKU 不单列，产品格显示
             「SKU | 名称」（SKU 仍可点击进产品详情）②「换入产品」列去掉 —— **同品换货**下它与退回产品必然相同
             （异型号是后端预留能力、当前 UI 不开放；真出现时换入侧的数量/品质/单价仍在表内）
             2026-09-24（用户要求「宽度要充满页面」）：Element 用 table-layout:fixed + width:100%
             ⇒ **列宽之和大于容器时不是横向滚动，而是把右侧列裁掉**（实测列和 1056 / 容器 948 ⇒ 末列超出 108px）。
             故"撑满"必须同时满足两条：
               ① 列宽之和 ≤ 容器：叶子表头去掉与分组表头重复的「退回/换入」前缀（分组头已写明，
                  4 字标题在 72px 内被截断成「退回…」），数字列收到 数量 62 / 品质 60 / 单价 72 / 金额 76、
                  付费 140→128 ⇒ 合计 ≈ 888px（回到原设计目标，窄屏不再被裁）
               ② 至少一列用 min-width：Element 只有存在 min-width 列时才会把容器余量分给它 ⇒
                  「产品」132 与「备注」88 用 min-width，宽屏下自动吃满余量（原先全固定宽 ⇒ 右侧留白）
             数字列仍用固定 width，列宽稳定、不随窗口抖动。 -->
        <el-table :data="items" border>
          <!-- ===== 退回侧：退给供货商，从我方仓扣减 ===== -->
          <el-table-column label="退回（退给供货商）" align="center">
            <el-table-column label="产品" min-width="132" show-overflow-tooltip>
              <template #default="{ row }">
                <el-button v-if="row.productId" type="primary" link @click="goProduct(row.productId)">{{ productText(row) }}</el-button>
                <span v-else>{{ productText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="62" align="right" />
            <el-table-column label="品质" width="60" align="center">
              <template #default="{ row }">{{ ProductQualityTypeLabel[String(row.qualityType)] || row.qualityType || '-' }}</template>
            </el-table-column>
            <el-table-column label="单价" width="72" align="right">
              <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="76" align="right">
              <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
            </el-table-column>
          </el-table-column>
          <!-- ===== 换入侧：供货商换回，入我方仓 ===== -->
          <el-table-column label="换入（供货商换回）" align="center">
            <el-table-column prop="inQuantity" label="数量" width="62" align="right" />
            <el-table-column label="品质" width="60" align="center">
              <template #default="{ row }">{{ ProductQualityTypeLabel[String(row.inQualityType)] || row.inQualityType || '-' }}</template>
            </el-table-column>
            <el-table-column label="单价" width="72" align="right">
              <template #default="{ row }">{{ formatMoney(row.inUnitPrice) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="76" align="right">
              <template #default="{ row }">{{ formatMoney(row.inAmount) }}</template>
            </el-table-column>
          </el-table-column>
          <!-- 逐产品付费（2026-09-21）：本行产品付给供货商的金额 + 类型（金额 0 = 该产品不付费） -->
          <el-table-column label="付费" width="128" align="center" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="Number(row.chargeAmount) > 0">
                <span style="color:#e6a23c;font-weight:600">{{ formatMoney(row.chargeAmount) }}</span>
                <span style="margin-left:4px;color:#909399">{{ PurchaseChargeTypeLabel[String(row.chargeType)] || row.chargeType || '' }}</span>
              </template>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="88" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
      </template>

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
  DocStatus, DocStatusLabel, ProductQualityType, ProductQualityTypeLabel,
  WarehouseType, WarehouseCategory, PurchaseChargeType, PurchaseChargeTypeLabel,
  PURCHASE_EXCHANGE_DIRTY_KEY,
} from '@/api/enums'
import { productLabel } from '@/api/product'
import {
  getPurchaseExchange, updatePurchaseExchange,
  auditPurchaseExchange, unAuditPurchaseExchange, cancelPurchaseExchange,
} from '@/api/purchase'

import PageShell from '@/components/PageShell.vue'

/**
 * 采购换货单详情（2026-09-24 用户口径：草稿态就地可编辑，列表不再给「编辑」）
 *
 * 结构对齐采购单/销售单/报损单详情：`head` = 只读快照，`form`/`items` = 可编辑副本（草稿态才建）。
 * 草稿分支的字段、校验、payload 与 add.vue **完全一致**（双向仓库 + 退回/换入明细 + 逐产品付费）；
 * 已审核/已作废分支保留原只读 el-descriptions + 明细表。
 *
 * 两处刻意锁定（与 add.vue 编辑态一致，避免"改一张单等于换一张单"）：
 *  · 供货商 / 来源采购单 只读 —— add.vue 在编辑态对两者 `:disabled`；
 *  · 明细行的「退回产品」只读 —— 换产品等于换明细行，请删除后重新添加（后端也不支持改 productId）。
 *    仅"无单换货"（未关联采购单）允许「添加明细」并现场选产品，与 add.vue 一致。
 */
const route = useRoute()
const router = useRouter()
const acting = ref(false)
const loading = ref(false)
const saving = ref(false)

const head = reactive({
  code: '',
  supplierId: null as number | null,
  supplierName: '',
  purchaseOrderId: null as number | null,
  purchaseOrderCode: '',
  warehouseOutId: null as number | null,
  warehouseInId: null as number | null,
  exchangeDate: '',
  status: DocStatus.DRAFT as string,
  totalReturnAmount: 0,
  totalInAmount: 0,
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
/** 可编辑副本（白名单：提交体不带 code/status/审核人/金额合计，金额由后端按明细重算） */
const form = reactive({
  exchangeDate: '',
  warehouseOutId: null as number | null,
  warehouseInId: null as number | null,
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
/** 付费类型（对应后端 purchase/common/PurchaseChargeType；方向：我们向供货商付费） */
const payTypeOptions = computed(() =>
  Object.values(PurchaseChargeType).map((v) => ({ value: v, label: PurchaseChargeTypeLabel[v] || v }))
)
/** 付费合计 = Σ 明细行付费（单据级金额由后端按 Σ明细 回写 ⇒ 前端不填总数） */
const chargeTotal = computed(() =>
  items.value.reduce((s: number, it: any) => s + (Number(it.chargeAmount) || 0), 0))
const totalReturnAmount = computed(() =>
  items.value.reduce((s, i) => s + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0))
const totalInAmount = computed(() =>
  items.value.reduce((s, i) => s + (Number(i.inQuantity) || 0) * (Number(i.inUnitPrice) || 0), 0))

/** 退回出库仓 / 换入入库仓都只能是**自有成品仓**（同仓允许，仓内按品质分行） */
const fetchFinishedWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WarehouseType.FINISHED } })

// ===== 字典：仓库（只读分支的 head 不含仓库名，本地翻译展示） =====
const warehouses = ref<{ id: number; warehouseName?: string; name?: string }[]>([])
async function loadWarehouses() {
  try {
    const r: any = await request.get('/warehouse/page', { params: { pageSize: 500 } })
    warehouses.value = r?.records || []
  } catch { warehouses.value = [] }
}
const warehouseOutName = computed(() => {
  const w = warehouses.value.find((x) => x.id === head.warehouseOutId)
  return w ? (w.warehouseName || w.name) : '—'
})
const warehouseInName = computed(() => {
  const w = warehouses.value.find((x) => x.id === head.warehouseInId)
  return w ? (w.warehouseName || w.name) : '—'
})

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
/** 明细表「退回产品」展示：有 SKU 时 `SKU | 名称`（不再单列 SKU，信息不丢） */
function productText(row: any) {
  const name = row?.productName || ''
  return row?.sku ? `${row.sku} | ${name}` : name
}

// ===== 无单换货手工加行时的产品候选（远程搜） =====
const productOptions = ref<any[]>([])
async function loadProducts(query?: string) {
  const params: any = { pageSize: 100 }
  if (query) params.keyword = query
  const res: any = await request.get('/product/page', { params })
  productOptions.value = res?.records || []
}
/** 选中产品后带出 SKU/名称与默认单价（最近进价 lastInPrice，其次标准价 price） */
function onProductChange(val: number, row: any) {
  const m = productOptions.value.find((x: any) => x.id === val)
  if (!m) return
  row.productName = m.name || ''
  row.sku = m.sku || ''
  const price = Number(m.lastInPrice ?? m.price ?? 0)
  row.unitPrice = price
  if (!row.inUnitPrice) row.inUnitPrice = price
  if (!row.inQuantity) row.inQuantity = row.quantity
}
/** 添加明细（无单换货用）：一行 = 退回侧 + 换入侧配对（退回默认不良品、换入默认 A 规） */
function addItem() {
  items.value.push({
    purchaseOrderItemId: null, productId: null, productName: '', sku: '',
    chargeAmount: 0, chargeType: '',
    quantity: 1, maxQuantity: null, qualityType: ProductQualityType.DEFECT, unitPrice: 0,
    inQuantity: 1, inQualityType: ProductQualityType.A, inUnitPrice: 0,
    remark: ''
  })
}
function removeItem(i: number) { items.value.splice(i, 1) }

function resetForm() {
  form.exchangeDate = head.exchangeDate ? String(head.exchangeDate).slice(0, 10) : ''
  form.warehouseOutId = head.warehouseOutId
  form.warehouseInId = head.warehouseInId
  form.chargeReason = head.chargeReason || ''
  form.remark = head.remark || ''
}

async function loadDetail(id: number) {
  loading.value = true
  try {
    const res: any = await getPurchaseExchange(id)
    const h = res?.head || {}
    Object.assign(head, {
      code: h.code || '',
      supplierId: h.supplierId ?? null,
      supplierName: h.supplierName || '',
      purchaseOrderId: h.purchaseOrderId ?? null,
      purchaseOrderCode: h.purchaseOrderCode || '',
      warehouseOutId: h.warehouseOutId ?? null,
      warehouseInId: h.warehouseInId ?? null,
      exchangeDate: h.exchangeDate || '',
      status: h.status || DocStatus.DRAFT,
      totalReturnAmount: Number(h.totalReturnAmount || 0),
      totalInAmount: Number(h.totalInAmount || 0),
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
    if (String(head.status) === DocStatus.DRAFT) {
      resetForm()
      // 明细回填（退回侧 + 换入侧字段均已落库；数值显式转 Number，避免输入框拿到字符串）
      items.value = (res?.items || []).map((it: any) => ({
        ...it,
        quantity: Number(it.quantity),
        unitPrice: Number(it.unitPrice ?? 0),
        inQuantity: Number(it.inQuantity ?? it.quantity),
        inUnitPrice: Number(it.inUnitPrice ?? it.unitPrice ?? 0),
        chargeAmount: Number(it.chargeAmount || 0),
        chargeType: it.chargeType || '',
      }))
    } else {
      items.value = res?.items || []
    }
  } finally { loading.value = false }
}

function goPurchaseOrder(id?: number | null) { if (id) router.push(`/inventory/purchase/detail/${id}`) }
function goWarehouse(id?: number | null) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goProduct(id?: number | null) { if (id) router.push(`/product/detail/${id}`) }

/** 保存（与 add.vue 同一套校验与 payload；后端 update 自带「只有草稿可编辑」守卫） */
async function onSave() {
  if (!form.warehouseOutId) { ElMessage.warning('请选择退回出库仓'); return }
  if (!form.warehouseInId) { ElMessage.warning('请选择换入入库仓'); return }
  const its = items.value.filter((i) => Number(i.quantity) > 0)
  if (its.length === 0) { ElMessage.warning('请至少录入一条换货明细'); return }
  if (its.some((i) => !i.productId)) { ElMessage.warning('请为每条明细选择退回产品'); return }
  for (const it of its) {
    if (it.maxQuantity != null && Number(it.quantity) > it.maxQuantity) {
      ElMessage.warning(`产品「${it.productName}」退回数量(${it.quantity})超过可换数量(${it.maxQuantity})`)
      return
    }
    if (!(Number(it.inQuantity) > 0)) {
      ElMessage.warning(`产品「${it.productName}」换入数量必须大于 0`)
      return
    }
  }
  for (const it of its) {
    if (Number(it.chargeAmount) > 0 && !it.chargeType) {
      ElMessage.warning(`产品「${it.productName || it.productId}」已填付费金额，请选择付费类型`)
      return
    }
  }
  const charged = chargeTotal.value > 0
  saving.value = true
  try {
    await updatePurchaseExchange(Number(route.params.id), {
      supplierId: head.supplierId,
      purchaseOrderId: head.purchaseOrderId || null,
      purchaseOrderCode: head.purchaseOrderId ? head.purchaseOrderCode : '',
      warehouseOutId: form.warehouseOutId,
      warehouseInId: form.warehouseInId,
      exchangeDate: form.exchangeDate,
      chargeFlag: charged ? 1 : 0,
      chargeType: '',
      chargeAmount: 0,
      chargeReason: charged ? (form.chargeReason || '') : '',
      remark: form.remark,
      items: its.map((i) => ({
        purchaseOrderItemId: i.purchaseOrderItemId ?? null, productId: i.productId, productName: i.productName,
        qualityType: i.qualityType, quantity: i.quantity, unitPrice: i.unitPrice,
        inQuantity: i.inQuantity, inQualityType: i.inQualityType, inUnitPrice: i.inUnitPrice,
        chargeAmount: Number(i.chargeAmount) || 0,
        chargeType: Number(i.chargeAmount) > 0 ? (i.chargeType || '') : '',
        chargeReason: form.chargeReason || '',
        remark: i.remark
      }))
    })
    ElMessage.success('已保存')
    sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1')
    await loadDetail(Number(route.params.id))
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '保存失败')
  } finally { saving.value = false }
}

async function doAudit() {
  const net = Number(head.totalInAmount || 0) - Number(head.totalReturnAmount || 0)
  // 是否付费：方向是"我方付给供货商" ⇒ 审核额外生成一条正向应付
  const feeText = Number(head.chargeFlag) === 1 && Number(head.chargeAmount || 0) > 0
    ? `；另生成付费应付 ${formatMoney(head.chargeAmount)}（我方付给供货商）` : ''
  // 2026-09-20（F7-154）：confirm 单独 try/catch（点「取消」会 reject，原先 confirm 在 try 之外 ⇒ 未处理 rejection）
  try {
    await ElMessageBox.confirm(
      `确认审核「${head.code}」？退回货品从我方仓扣减（退给供货商）、换入良品入库；`
      + `并生成两条应付台账：退回冲减 ${formatMoney(head.totalReturnAmount)} / 换入新增 ${formatMoney(head.totalInAmount)}，净额 ${formatMoney(net)}${feeText}。`,
      '审核确认', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await auditPurchaseExchange(Number(route.params.id))
    ElMessage.success('已审核')
    sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1')
    loadDetail(Number(route.params.id))
  } finally { acting.value = false }
}
async function doUnAudit() {
  const feeText = Number(head.chargeFlag) === 1 ? '（含付费台账）' : ''
  try {
    await ElMessageBox.confirm(`确认反审核「${head.code}」？将回滚退回与换入的库存，并作废应付台账${feeText}。`, '提示', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await unAuditPurchaseExchange(Number(route.params.id))
    ElMessage.success('已反审核')
    sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1')
    loadDetail(Number(route.params.id))
  } finally { acting.value = false }
}
async function doCancel() {
  try {
    await ElMessageBox.confirm(`确认作废换货单「${head.code}」？作废后单据留痕，不可恢复。`, '提示', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await cancelPurchaseExchange(Number(route.params.id))
    ElMessage.success('已作废')
    sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1')
    router.push('/inventory/purchase-exchange')
  } finally { acting.value = false }
}

// 字典类只需加载一次
onMounted(() => { loadWarehouses(); if (isDraft.value) loadProducts() })
// 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
onActivated(() => { loadDetail(Number(route.params.id)) })
</script>

<style scoped>
/* 页头/操作区已统一到全局骨架（PageShell + styles/page.css） */
</style>
