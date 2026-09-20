<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>销售退单详情</span>
          <el-tag :type="statusTagType(header.status)">{{ statusLabel(header.status) }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="退单号">{{ header.code }}</el-descriptions-item>
        <el-descriptions-item label="客户">
          <el-button v-if="head.customerId" type="primary" link @click="goCustomer(head.customerId)">{{ header.customerName || '—' }}</el-button>
          <span v-else>{{ header.customerName || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="退货仓库">
          <el-button v-if="head.warehouseId" type="primary" link @click="goWarehouse(head.warehouseId)">{{ warehouseDisplayName }}</el-button>
          <span v-else>{{ warehouseDisplayName }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="关联销售单">
          <el-button v-if="head.saleOrderId" type="primary" link @click="goSaleOrder(head.saleOrderId)">{{ head.saleOrderCode || '—' }}</el-button>
          <span v-else>{{ head.saleOrderCode || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ head.returnDate }}</el-descriptions-item>
        <el-descriptions-item label="退货金额">{{ formatMoney(head.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="收费" :span="2">
          <template v-if="Number(head.chargeFlag) === 1 && Number(head.chargeAmount) > 0">
            <span style="color:#e6a23c;font-weight:600">{{ formatMoney(head.chargeAmount) }}</span>
            <span style="margin-left:6px;color:#909399">{{ ExchangeChargeTypeLabel[String(head.chargeType)] || head.chargeType || '' }}</span>
          </template>
          <span v-else style="color:#c0c4cc">不收费</span>
        </el-descriptions-item>
        <el-descriptions-item label="收费说明" :span="3">{{ head.chargeReason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ head.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">退货明细</el-divider>
      <el-table :data="items" border>
        <el-table-column label="SKU" width="130">
          <template #default="{ row }">
            <el-button v-if="row.productId" type="primary" link @click="goProduct(row.productId)">{{ row.sku || '—' }}</el-button>
            <span v-else>{{ row.sku || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品" min-width="200" />
        <el-table-column label="品质等级" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="ProductQualityTypeTag[row.qualityType] || 'info'">{{ ProductQualityTypeLabel[row.qualityType] || '待分类' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="退货数量" width="130" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />
      </el-table>

      <div class="footer">
        <el-button @click="goBack">返回</el-button>
        <template v-if="head.status === SaleReturnStatus.DRAFT">
          <el-button type="primary" @click="goEdit">编辑</el-button>
          <el-button v-perm="'sale:return:audit'" type="success" :loading="acting" @click="doAudit">审核</el-button>
          <el-button v-perm="'sale:return:cancel'" type="danger" :loading="acting" @click="doCancel">作废</el-button>
        </template>
        <el-button v-if="String(head.status) === String(SaleReturnStatus.AUDITED)" v-perm="'sale:return:unaudit'" type="warning" :loading="acting" @click="doUnAudit">反审核</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onActivated, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { ProductQualityTypeLabel, ProductQualityTypeTag, ExchangeChargeTypeLabel } from '@/api/enums'
import {
  getSaleReturn,
  getSaleReturnItems,
  auditSaleReturn,
  unAuditSaleReturn,
  cancelSaleReturn,
  SaleReturnStatus,
  SaleReturnStatusLabel,
} from '@/api/sale'

const route = useRoute()
const router = useRouter()
const acting = ref(false)
// 仓库显示用的本地轻量列表（组件内维护，不再依赖全局 optionsStore）
// 2026-09-20（F7-142）：不再按 warehouseType 过滤 —— 详情页只做「名称回显」，需能显示任意历史仓库；
// 原写死的 'AFTER_SALE' 已随 2026-09-16「方案 A」取消（实测现网无此类型仓）⇒ 该过滤恒为空，仓库名永远显示 —。
// 「只能退到自有成品仓」是【新增时】的可选范围规则（见 return/add.vue:161），与详情展示无关。
// 2026-09-20（F7-177）：详情只回显 1 个仓库名 ⇒ 按 id 单取（原为 pageSize=500 全量拉 + 前端 find；
// 照搬 outsource/warehouse-detail.vue:98 的既有修法，仓库总数超过 pageSize 时全量方案会静默找不到）
const warehouseDisplayName = ref('—')
async function loadWarehouseName() {
  const wid = head.warehouseId
  if (!wid) { warehouseDisplayName.value = '—'; return }
  try { const w: any = await request.get(`/warehouse/${wid}`); warehouseDisplayName.value = w?.warehouseName || w?.name || '—' }
  catch { warehouseDisplayName.value = '—' }
}
const items = ref<any[]>([])

const head = reactive({
  code: '',
  customerId: undefined as number | undefined,
  customerName: '',
  warehouseId: undefined as number | undefined,
  saleOrderId: undefined as number | undefined,
  saleOrderCode: '',
  returnDate: '',
  totalAmount: 0,
  chargeFlag: 0,
  chargeType: '',
  chargeAmount: 0,
  chargeReason: '',
  auditorName: '',
  remark: '',
  status: SaleReturnStatus.DRAFT,
})
const header = head



/** 单据状态为字符串编码（DRAFT/AUDITED/CANCELLED），与后端 status 字段(varchar)一致 */
function statusLabel(s: string) {
  return SaleReturnStatusLabel[s] ?? '未知'
}
function statusTagType(s: string) {
  if (s === SaleReturnStatus.AUDITED) return 'success'
  if (s === SaleReturnStatus.CANCELLED) return 'info'
  return 'warning'
}
function formatMoney(v: any) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadDetail(id: number) {
  const h = await getSaleReturn(id)
  Object.assign(head, {
    code: h.code,
    customerId: h.customerId,
    customerName: h.customerName,
    warehouseId: h.warehouseId,
    saleOrderId: h.saleOrderId,
    saleOrderCode: h.saleOrderCode,
    returnDate: h.returnDate,
    totalAmount: h.totalAmount,
    chargeFlag: Number(h.chargeFlag || 0),
    chargeType: h.chargeType || '',
    chargeAmount: Number(h.chargeAmount || 0),
    chargeReason: h.chargeReason || '',
    auditorName: h.auditorName,
    remark: h.remark,
    status: h.status,
  })
  items.value = await getSaleReturnItems(id)
  await loadWarehouseName()
}

function goBack() {
  router.push('/sale/return')
}
function goEdit() {
  router.push(`/sale/return/add?id=${route.params.id}`)
}
function goCustomer(id?: number) { if (id) router.push(`/inventory/customer/detail/${id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goSaleOrder(id?: number) { if (id) router.push(`/inventory/sale/detail/${id}`) }
function goProduct(id?: number) { if (id) router.push(`/product/detail/${id}`) }

async function doAudit() {
  await ElMessageBox.confirm('确认审核？审核后客户退回的待分类品将入库成品仓增加库存。', '提示', { type: 'warning' })
  acting.value = true
  try {
    await auditSaleReturn(Number(route.params.id))
    ElMessage.success('审核成功')
    loadDetail(Number(route.params.id))
  } finally {
    acting.value = false
  }
}
async function doUnAudit() {
  await ElMessageBox.confirm('确认反审核？将扣减已入库的待分类品库存。', '提示', { type: 'warning' })
  acting.value = true
  try {
    await unAuditSaleReturn(Number(route.params.id))
    ElMessage.success('反审核成功')
    loadDetail(Number(route.params.id))
  } finally {
    acting.value = false
  }
}
async function doCancel() {
  await ElMessageBox.confirm('确认作废该销售退单？', '提示', { type: 'warning' })
  acting.value = true
  try {
    await cancelSaleReturn(Number(route.params.id))
    ElMessage.success('作废成功')
    loadDetail(Number(route.params.id))
  } finally {
    acting.value = false
  }
}

// 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
// （仓库名已随 loadDetail 按 id 单取，不再需要额外的字典预载）
onActivated(() => { loadDetail(Number(route.params.id)) })
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.footer { margin-top: 20px; text-align: right; }
</style>
