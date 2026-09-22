<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>采购换货单详情</span>
          <el-tag :type="statusTagType(head.status)">{{ statusLabel(head.status) }}</el-tag>
        </div>
      </template>

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
           ③按内容重新配宽 ⇒ 合计 888px < 内容区 948px ⇒ 一行显示完、不左右滑动 -->
      <el-table :data="items" border>
        <!-- ===== 退回侧：退给供货商，从我方仓扣减 ===== -->
        <el-table-column label="退回（退给供货商）" align="center">
          <el-table-column label="退回产品" width="132" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button v-if="row.productId" type="primary" link @click="goProduct(row.productId)">{{ productText(row) }}</el-button>
              <span v-else>{{ productText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="quantity" label="退回数量" width="74" align="right" />
          <el-table-column label="退回品质" width="72" align="center">
            <template #default="{ row }">{{ ProductQualityTypeLabel[String(row.qualityType)] || row.qualityType || '-' }}</template>
          </el-table-column>
          <el-table-column label="退回单价" width="82" align="right">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="退回金额" width="90" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table-column>
        <!-- ===== 换入侧：供货商换回，入我方仓 ===== -->
        <el-table-column label="换入（供货商换回）" align="center">
          <el-table-column prop="inQuantity" label="换入数量" width="74" align="right" />
          <el-table-column label="换入品质" width="72" align="center">
            <template #default="{ row }">{{ ProductQualityTypeLabel[String(row.inQualityType)] || row.inQualityType || '-' }}</template>
          </el-table-column>
          <el-table-column label="换入单价" width="82" align="right">
            <template #default="{ row }">{{ formatMoney(row.inUnitPrice) }}</template>
          </el-table-column>
          <el-table-column label="换入金额" width="90" align="right">
            <template #default="{ row }">{{ formatMoney(row.inAmount) }}</template>
          </el-table-column>
        </el-table-column>
        <!-- 逐产品付费（2026-09-21）：本行产品付给供货商的金额 + 类型（金额 0 = 该产品不付费） -->
        <el-table-column label="付费" width="140" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="Number(row.chargeAmount) > 0">
              <span style="color:#e6a23c;font-weight:600">{{ formatMoney(row.chargeAmount) }}</span>
              <span style="margin-left:4px;color:#909399">{{ PurchaseChargeTypeLabel[String(row.chargeType)] || row.chargeType || '' }}</span>
            </template>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" width="72" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="footer">
        <el-button @click="goBack">返回</el-button>
        <template v-if="head.status === DocStatus.DRAFT">
          <el-button type="primary" @click="goEdit">编辑</el-button>
          <el-button v-perm="'purchase:exchange:audit'" type="success" :loading="acting" @click="doAudit">审核</el-button>
          <el-button v-perm="'purchase:exchange:cancel'" type="danger" :loading="acting" @click="doCancel">作废</el-button>
        </template>
        <el-button v-if="head.status === DocStatus.AUDITED" v-perm="'purchase:exchange:unaudit'" type="warning" :loading="acting" @click="doUnAudit">反审核</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onActivated, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import {
  DocStatus, DocStatusLabel, ProductQualityTypeLabel, PurchaseChargeTypeLabel, PURCHASE_EXCHANGE_DIRTY_KEY,
} from '@/api/enums'
import {
  getPurchaseExchange, auditPurchaseExchange, unAuditPurchaseExchange, cancelPurchaseExchange,
} from '@/api/purchase'

const route = useRoute()
const router = useRouter()
const acting = ref(false)

const head = reactive({
  code: '',
  supplierName: '',
  purchaseOrderId: null as number | null,
  purchaseOrderCode: '',
  warehouseOutId: null as number | null,
  warehouseInId: null as number | null,
  exchangeDate: '',
  status: DocStatus.DRAFT as string,
  totalReturnAmount: 0,
  totalInAmount: 0,
  // 是否付费（2026-09-21）：方向 = 我方付给供货商
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

// ===== 字典：仓库（详情 head 不含仓库名，本地翻译展示） =====
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

async function loadDetail(id: number) {
  const res: any = await getPurchaseExchange(id)
  const h = res?.head || {}
  Object.assign(head, {
    code: h.code || '',
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
  items.value = res?.items || []
}

function goPurchaseOrder(id?: number | null) { if (id) router.push(`/inventory/purchase/detail/${id}`) }
function goWarehouse(id?: number | null) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goProduct(id?: number | null) { if (id) router.push(`/product/detail/${id}`) }
function goBack() { router.push('/inventory/purchase-exchange') }
/** 编辑：跳转独立编辑页（?id= 自动加载单据） */
function goEdit() { router.push(`/inventory/purchase-exchange/add?id=${route.params.id}`) }

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
onMounted(() => { loadWarehouses() })
// 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
onActivated(() => { loadDetail(Number(route.params.id)) })
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.footer { margin-top: 20px; text-align: right; }
</style>
