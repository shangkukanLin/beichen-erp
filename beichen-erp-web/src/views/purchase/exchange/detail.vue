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
          <span v-else>{{ head.purchaseOrderCode || '—' }}</span>
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
        <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ head.auditTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ head.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ head.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">换货明细（同品换货）</el-divider>
      <el-table :data="items" border>
        <!-- ===== 退回侧：退给供货商，从我方仓扣减 ===== -->
        <el-table-column label="退回（退给供货商，从我方仓扣减）" align="center">
          <el-table-column label="SKU" width="130">
            <template #default="{ row }">
              <el-button v-if="row.productId" type="primary" link @click="goProduct(row.productId)">{{ row.sku || '—' }}</el-button>
              <span v-else>{{ row.sku || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="退回产品" min-width="150" show-overflow-tooltip />
          <el-table-column prop="quantity" label="退回数量" width="100" align="right" />
          <el-table-column label="退回品质" width="100" align="center">
            <template #default="{ row }">{{ ProductQualityTypeLabel[String(row.qualityType)] || row.qualityType || '-' }}</template>
          </el-table-column>
          <el-table-column label="退回单价" width="100" align="right">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="退回金额" width="110" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table-column>
        <!-- ===== 换入侧：供货商换回，入我方仓 ===== -->
        <el-table-column label="换入（供货商换回，入我方仓）" align="center">
          <el-table-column label="换入产品" min-width="180">
            <template #default="{ row }">{{ row.productName }}</template>
          </el-table-column>
          <el-table-column prop="inQuantity" label="换入数量" width="100" align="right" />
          <el-table-column label="换入品质" width="100" align="center">
            <template #default="{ row }">{{ ProductQualityTypeLabel[String(row.inQualityType)] || row.inQualityType || '-' }}</template>
          </el-table-column>
          <el-table-column label="换入单价" width="100" align="right">
            <template #default="{ row }">{{ formatMoney(row.inUnitPrice) }}</template>
          </el-table-column>
          <el-table-column label="换入金额" width="110" align="right">
            <template #default="{ row }">{{ formatMoney(row.inAmount) }}</template>
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
  DocStatus, DocStatusLabel, ProductQualityTypeLabel, PURCHASE_EXCHANGE_DIRTY_KEY,
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
  await ElMessageBox.confirm(
    `确认审核「${head.code}」？退回货品从我方仓扣减（退给供货商）、换入良品入库；`
    + `并生成两条应付台账：退回冲减 ${formatMoney(head.totalReturnAmount)} / 换入新增 ${formatMoney(head.totalInAmount)}，净额 ${formatMoney(net)}。`,
    '审核确认', { type: 'warning' })
  acting.value = true
  try {
    await auditPurchaseExchange(Number(route.params.id))
    ElMessage.success('已审核')
    sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1')
    loadDetail(Number(route.params.id))
  } finally { acting.value = false }
}
async function doUnAudit() {
  await ElMessageBox.confirm(`确认反审核「${head.code}」？将回滚退回与换入的库存，并作废两条应付台账。`, '提示', { type: 'warning' })
  acting.value = true
  try {
    await unAuditPurchaseExchange(Number(route.params.id))
    ElMessage.success('已反审核')
    sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1')
    loadDetail(Number(route.params.id))
  } finally { acting.value = false }
}
async function doCancel() {
  await ElMessageBox.confirm(`确认作废换货单「${head.code}」？作废后单据留痕，不可恢复。`, '提示', { type: 'warning' })
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
