<script setup lang="ts">
// 成品收货「收货记录」详情（2026-09-23 用户要求：原 60% 抽屉改为独立页面）
// —— 按 id 回源 `/outsource/order-delivery/{id}`（后端本轮回补的单条查询），
//    再用该记录所属加工单的产品行 + 成品仓列表解析名称（与列表页同一套匹配口径）。
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { DocStatusLabel, DocStatusTag } from '@/api/enums'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'

const route = useRoute()
const loading = ref(false)
const row = ref<any>({})
const products = ref<any[]>([])
const warehouses = ref<any[]>([])

function orderProductOf(r: any) {
  return products.value.find((p: any) => p.productMasterId && p.productMasterId === r?.productMasterId)
    || products.value.find((p: any) => p.id === r?.productId)
}
function productNameOf(r: any) { return orderProductOf(r)?.productName || '-' }
function skuOf(r: any) { return orderProductOf(r)?.sku || '-' }
function warehouseNameOf(r: any) {
  if (!r?.warehouseId) return '-'
  return warehouses.value.find((w: any) => w.id === r.warehouseId)?.warehouseName || r.warehouseId
}
/** 类型文案（与成品收货页口径一致；枚举一律按 code 比对） */
function typeTextOf(r: any) {
  if (!r?.deliveryType) return '普通收货'
  if (r.deliveryType === 'DELIVERY') return '收货'
  if (r.deliveryType === 'DEFECT_RETURN') return '加工退货'
  return r.deliveryType
}
/** 加工退货规格：A/B/C → A规/B规/C规；DEFECT → 不良（普通收货为空） */
function qualityTextOf(r: any) {
  const q = r?.qualityType
  if (!q) return '-'
  return q === 'DEFECT' ? '不良' : q + '规'
}
function openAttach(url: string) { window.open(url + '?inline=true') }

async function load() {
  loading.value = true
  try {
    const rec: any = await request.get('/outsource/order-delivery/' + route.params.id)
    row.value = rec || {}
    if (rec?.orderId != null) {
      const [prods, whs] = await Promise.all([
        request.get<any, any>(`/outsource/order/${rec.orderId}/products`).catch(() => []),
        request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } }).catch(() => null)
      ])
      products.value = prods || []
      warehouses.value = (whs as any)?.records || []
    }
  } catch { row.value = {} } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架，本页只读无操作按钮 -->
  <PageShell :loading="loading" back-fallback="/outsource/order/delivery">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">收货记录详情 — #{{ row.id ?? '' }}</span>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="记录ID">{{ row.id }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="收货日期">{{ row.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="登记时间">{{ row.createTime ? String(row.createTime).replace('T', ' ').slice(0, 19) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ productNameOf(row) }}</el-descriptions-item>
        <el-descriptions-item label="SKU">{{ skuOf(row) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeTextOf(row) }}</el-descriptions-item>
        <el-descriptions-item label="加工退货规格">{{ qualityTextOf(row) }}</el-descriptions-item>
        <el-descriptions-item label="收货仓库">{{ warehouseNameOf(row) }}</el-descriptions-item>
        <el-descriptions-item label="物流单号">{{ row.trackingNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="A规数量">{{ row.aQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="B规数量">{{ row.bQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="C规数量">{{ row.cQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="不良数量">{{ row.defectQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="总数量">
          <span :style="{ color: Number(row.quantity) < 0 ? 'var(--app-color-danger)' : '', fontWeight: '600' }">{{ row.quantity }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="收货图片">
          <el-button v-if="row.attachUrl" type="primary" link size="small" @click="openAttach(row.attachUrl)">查看图片</el-button>
          <span v-else style="color:var(--app-text-placeholder)">—</span>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项） -->
        <el-descriptions-item label="制单人">{{ row.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ row.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ row.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </PageShell>
</template>

<style scoped>/* 页头已统一到全局骨架（PageShell）；原 .p 局部样式已删除 */</style>
