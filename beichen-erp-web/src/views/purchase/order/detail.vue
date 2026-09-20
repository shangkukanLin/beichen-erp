<script setup lang="ts">
defineOptions({ name: 'PurchaseDetail' })
import { ref, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { getPurchaseOrderItems, type PurchaseOrder, type PurchaseOrderItem, PurchaseStatus, PurchaseStatusLabel } from '@/api/purchase'

const route = useRoute(); const router = useRouter()
const orderId = Number(route.params.id)
const order = ref<PurchaseOrder>({})
const items = ref<PurchaseOrderItem[]>([])
const returns = ref<any[]>([])
const loading = ref(false)
const supplierName = ref('')
const warehouseName = ref('')

function statusType(s?: string | number) {
  if (s === PurchaseStatus.DRAFT) return 'info'
  if (s === PurchaseStatus.AUDITED) return 'success'
  if (s === PurchaseStatus.CANCELLED) return 'danger'
  return undefined
}
function statusLabel(s?: number) { return s != null ? (PurchaseStatusLabel[s] || '') : '' }
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

/** 品质等级中文映射 */
function qualityLabel(qt?: string) {
  const map: Record<string, string> = { A: 'A规', B: 'B规', C: 'C规', DEFECT: '不良' }
  return qt ? (map[qt] || qt) : '—'
}

async function loadData() {
  loading.value = true
  try {
    const [res, itemRes] = await Promise.all([
      request.get<any, any>(`/inventory/purchase/${orderId}`),
      getPurchaseOrderItems(orderId)
    ])
    order.value = res || {}
    items.value = itemRes || []

    // 2026-09-20（F7-177）：详情页只需**一个**名称 ⇒ 按 id 单取（原先是 pageSize=500 全量拉回再前端 find，
    // 等于为了显示两个名字把整张主数据表拉一遍）。列表页做"多行名称映射"时才应全量拉（见 inventory/stock-log.vue）。
    if (order.value.supplierId) {
      const s: any = await request.get(`/supplier/${order.value.supplierId}`).catch(() => null)
      supplierName.value = s?.name || ''
    }
    if (order.value.warehouseId) {
      const w: any = await request.get(`/warehouse/${order.value.warehouseId}`).catch(() => null)
      warehouseName.value = w?.warehouseName || ''
    }
    // 该采购单的退货情况（期 2·2026-09-19 读隔离：随详情接口一并返回，
    // 原先跨页读 /inventory/purchase-return/by-order，需 purchase:return ⇒ 只有采购单权限的用户会 403）
    returns.value = (res as any)?.returns || []
  } finally { loading.value = false }
}

function goSupplier(id?: number) { if (id) router.push(`/supplier/detail/${id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function addReturn() { router.push({ path: '/inventory/purchase-return/add', query: { fromOrder: orderId } }) }
function goReturnDetail(id: number) { router.push(`/inventory/purchase-return/detail/${id}`) }

// 本页的供货商名/仓库名是在 loadData 内按需查询填充的（字典与业务耦合），故整体放到 onActivated：
// keep-alive 缓存下再次进入会复用组件、onMounted 不再触发，只靠 onMounted 会停留在上次缓存的状态
onActivated(() => { loadData() })
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">采购单详情 — {{ order.code }}</span>
          <el-button type="primary" :icon="'Plus'" @click="addReturn">发起退货</el-button>
        </div>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="单号">{{ order.code }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(order.status)">{{ statusLabel(order.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="供货商">
          <el-button v-if="order.supplierId" type="primary" link @click="goSupplier(order.supplierId)">{{ supplierName }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="入库仓库">
          <el-button v-if="order.warehouseId" type="primary" link @click="goWarehouse(order.warehouseId)">{{ warehouseName }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单日期">{{ order.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="税额">{{ fmt(order.taxAmount) }}</el-descriptions-item>
        <el-descriptions-item label="总金额">{{ fmt(order.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ order.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">明细</el-divider>
      <el-table :data="items" border stripe size="small">
        <el-table-column prop="sku" label="SKU" width="130" />
        <el-table-column prop="productName" label="成品名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="品质" width="80" align="center">
          <template #default="{ row }">{{ qualityLabel(row.qualityType) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">退货情况</span></template>
      <el-table v-if="returns.length" :data="returns" border stripe size="small">
        <el-table-column prop="code" label="退货单号" width="180" />
        <el-table-column prop="returnDate" label="退货日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退货金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goReturnDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无退货记录" :image-size="60" />
    </el-card>

    <div style="text-align:center;margin-top:20px">
      <el-button @click="$router.back()">返回</el-button>
    </div>
  </div>
</template>

<style scoped>
.detail-page { display: flex; flex-direction: column; gap: 12px; }
</style>
