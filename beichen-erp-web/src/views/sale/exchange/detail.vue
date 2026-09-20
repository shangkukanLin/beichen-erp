<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>销售换货单详情</span>
          <el-tag :type="statusTagType(head.status)">{{ statusLabel(head.status) }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border>
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
        <el-descriptions-item label="收费">
          <template v-if="Number(head.chargeFlag) === 1 && Number(head.chargeAmount) > 0">
            <span style="color:#e6a23c;font-weight:600">{{ formatMoney(head.chargeAmount) }}</span>
            <span style="margin-left:6px;color:#909399">{{ ExchangeChargeTypeLabel[String(head.chargeType)] || head.chargeType || '' }}</span>
          </template>
          <span v-else style="color:#c0c4cc">不收费</span>
        </el-descriptions-item>
        <el-descriptions-item label="收费说明" :span="2">{{ head.chargeReason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ head.auditTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ head.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ head.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">换货明细（同品换货）</el-divider>
      <el-table :data="items" border>
        <!-- ===== 退回侧：客户退回，入成品仓（品质待分类 PENDING）待整理 ===== -->
        <el-table-column label="退回（客户退回，入成品仓待分类）" align="center">
          <el-table-column label="SKU" width="130">
            <template #default="{ row }">
              <el-button v-if="row.productId" type="primary" link @click="goProduct(row.productId)">{{ row.sku || '—' }}</el-button>
              <span v-else>{{ row.sku || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="退回产品" min-width="150" show-overflow-tooltip />
          <el-table-column prop="quantity" label="退回数量" width="100" align="right" />
          <el-table-column label="原单价" width="100" align="right">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="退回金额" width="110" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table-column>
        <!-- ===== 换出侧：发给客户，从成品仓扣减 ===== -->
        <el-table-column label="换出（发给客户，从成品仓扣减）" align="center">
          <el-table-column label="换出产品" min-width="180">
            <template #default="{ row }">{{ row.productName }}</template>
          </el-table-column>
          <el-table-column prop="outQuantity" label="换出数量" width="100" align="right" />
          <el-table-column label="换出品质" width="100" align="center">
            <template #default="{ row }">{{ ProductQualityTypeLabel[String(row.outQualityType)] || row.outQualityType || '-' }}</template>
          </el-table-column>
          <el-table-column label="换出单价" width="100" align="right">
            <template #default="{ row }">{{ formatMoney(row.outUnitPrice) }}</template>
          </el-table-column>
          <el-table-column label="换出金额" width="110" align="right">
            <template #default="{ row }">{{ formatMoney(row.outAmount) }}</template>
          </el-table-column>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="footer">
        <el-button @click="goBack">返回</el-button>
        <template v-if="head.status === DocStatus.DRAFT">
          <el-button type="primary" @click="goEdit">编辑</el-button>
          <el-button type="success" :loading="acting" @click="doAudit">审核</el-button>
          <el-button type="danger" :loading="acting" @click="doCancel">作废</el-button>
        </template>
        <el-button v-if="head.status === DocStatus.AUDITED" type="warning" :loading="acting" @click="doUnAudit">反审核</el-button>
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
  DocStatus, DocStatusLabel,
  ExchangeChargeTypeLabel,
  ProductQualityTypeLabel,
} from '@/api/enums'
import { SALE_EXCHANGE_DIRTY_KEY } from '@/api/enums'
import {
  getSaleExchange, auditSaleExchange, unAuditSaleExchange, cancelSaleExchange,
} from '@/api/sale'

const route = useRoute()
const router = useRouter()
const acting = ref(false)

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
  auditorName: '',
  auditTime: '',
  createTime: '',
  remark: '',
})
const items = ref<any[]>([])

// ===== 字典：客户 / 仓库（详情 head 为实体，不含冗余名称，本地翻译展示） =====
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
    auditorName: h.auditorName || '',
    auditTime: h.auditTime || '',
    createTime: h.createTime || '',
    remark: h.remark || '',
  })
  items.value = res?.items || []
  await loadWarehouseNames()
}

function goSaleOrder(id?: number | null) { if (id) router.push(`/inventory/sale/detail/${id}`) }
function goWarehouse(id?: number | null) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goProduct(id?: number | null) { if (id) router.push(`/product/detail/${id}`) }
function goBack() { router.push('/sale/exchange') }
/** 编辑：跳转独立编辑页（?id= 自动加载单据） */
function goEdit() { router.push(`/sale/exchange/add?id=${route.params.id}`) }

async function doAudit() {
  const charged = Number(head.chargeFlag) === 1 && Number(head.chargeAmount) > 0
  const chargeTip = charged
    ? `\n并生成一条向客户收取的费用应收 ${formatMoney(head.chargeAmount)} 元（台账单号 ${head.code}-FEE）。`
    : ''
  await ElMessageBox.confirm(`确认审核「${head.code}」？审核后退回货品入成品仓(待分类)，换出货品从成品仓扣减。${chargeTip}`, '审核确认', { type: 'warning' })
  acting.value = true
  try {
    await auditSaleExchange(Number(route.params.id))
    ElMessage.success('已审核')
    sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1')
    loadDetail(Number(route.params.id))
  } finally { acting.value = false }
}
async function doUnAudit() {
  await ElMessageBox.confirm(`确认反审核「${head.code}」？将回滚退回与换出的库存。`, '提示', { type: 'warning' })
  acting.value = true
  try {
    await unAuditSaleExchange(Number(route.params.id))
    ElMessage.success('已反审核')
    sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1')
    loadDetail(Number(route.params.id))
  } finally { acting.value = false }
}
async function doCancel() {
  await ElMessageBox.confirm(`确认作废换货单「${head.code}」？作废后单据留痕，不可恢复。`, '提示', { type: 'warning' })
  acting.value = true
  try {
    await cancelSaleExchange(Number(route.params.id))
    ElMessage.success('已作废')
    sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1')
    router.push('/sale/exchange')
  } finally { acting.value = false }
}

// 字典类只需加载一次
onMounted(() => { loadCustomers() })
// 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
onActivated(() => { loadDetail(Number(route.params.id)) })
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.footer { margin-top: 20px; text-align: right; }
</style>
